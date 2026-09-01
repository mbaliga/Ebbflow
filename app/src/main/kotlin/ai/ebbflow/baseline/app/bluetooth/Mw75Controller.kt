package ai.ebbflow.baseline.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import ai.ebbflow.baseline.app.data.AppDatabase
import ai.ebbflow.baseline.app.data.SignalQualitySample
import ai.ebbflow.baseline.app.model.StreamHub
import ai.ebbflow.baseline.app.model.StreamPhase
import ai.ebbflow.eeg.EegPacket
import ai.ebbflow.eeg.Mw75Constants
import ai.ebbflow.eeg.PacketParser
import ai.ebbflow.eeg.SignalQualityEstimator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/**
 * High-level MW75 session coordinator — the Android analogue of the Python
 * `MW75Device` (mw75_streamer/device/mw75_device.py). Runs the lifecycle:
 *
 *   1. find the device (bonded, else BLE scan)
 *   2. BLE activation handshake ([Mw75BleActivator])
 *   3. settle, then open RFCOMM ([Mw75RfcommConnection])
 *   4. stream: raw bytes → [PacketParser] → [SignalQualityEstimator] → [StreamHub] + Room
 *   5. on stop/error: close RFCOMM, best-effort BLE disable, publish terminal state
 *
 * Owned by [ai.ebbflow.baseline.app.service.Mw75StreamingService]. [start] blocks
 * (in the read loop) until [stop] is called or the link drops.
 */
@SuppressLint("MissingPermission")
class Mw75Controller(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val dao = AppDatabase.get(context).signalQualitySampleDao()
    private val parser = PacketParser()
    private val quality = SignalQualityEstimator()
    private val persisted = AtomicLong(0)

    @Volatile private var rfcomm: Mw75RfcommConnection? = null
    @Volatile private var activator: Mw75BleActivator? = null
    @Volatile private var running = false

    suspend fun start() {
        running = true
        persisted.set(0)
        quality.reset()

        try {
            // --- 1. Find device ---
            StreamHub.update { it.copy(phase = StreamPhase.SCANNING, message = "Looking for MW75…") }
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = manager?.adapter
            if (adapter == null || !adapter.isEnabled) {
                fail("Bluetooth is off")
                return
            }
            val device = Mw75DeviceFinder(adapter).find()
            if (device == null) {
                fail("MW75 not found — pair it in Bluetooth settings first")
                return
            }
            val name = device.name ?: "MW75"
            StreamHub.update { it.copy(deviceName = name) }

            // --- 2. BLE activation ---
            StreamHub.update { it.copy(phase = StreamPhase.ACTIVATING, message = "Activating EEG mode…") }
            val act = Mw75BleActivator(context, device).also { activator = it }
            val result = act.activate()
            if (!result.success) {
                fail(result.message ?: "BLE activation failed")
                return
            }
            StreamHub.update { it.copy(batteryPercent = result.batteryPercent) }

            // --- 3. Settle, then RFCOMM ---
            delay(Mw75Constants.RFCOMM_SETTLE_DELAY_MS)
            StreamHub.update { it.copy(phase = StreamPhase.CONNECTING_RFCOMM, message = "Opening RFCOMM…") }
            val rf = Mw75RfcommConnection(device).also { rfcomm = it }
            val connected = withContext(Dispatchers.IO) { rf.connect() }
            if (!connected) {
                fail("RFCOMM connection failed")
                return
            }

            // --- 4. Stream ---
            StreamHub.update { it.copy(phase = StreamPhase.STREAMING, message = null) }
            withContext(Dispatchers.IO) {
                rf.readLoop { chunk ->
                    for (packet in parser.feed(chunk)) handlePacket(packet)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Session error", e)
            StreamHub.update { it.copy(phase = StreamPhase.ERROR, message = e.message) }
        } finally {
            cleanup()
        }
    }

    private fun handlePacket(packet: EegPacket) {
        // Quality summaries are emitted at a low cadence to avoid UI/DB churn.
        val summary = quality.add(packet) ?: return
        val stats = parser.stats
        val mean = packet.channels.average()

        StreamHub.update {
            it.copy(
                phase = StreamPhase.STREAMING,
                usableChannelsFraction = summary.usableChannelsFraction,
                clippedSamplesFraction = summary.clippedSamplesFraction,
                flatlineChannelsFraction = summary.flatlineChannelsFraction,
                qualityLabel = summary.label.name,
                validPackets = stats.validPackets,
                invalidPackets = stats.invalidPackets,
                errorRatePercent = stats.errorRate,
                latestChannelsUv = packet.channels,
                samplesPersisted = persisted.get(),
            )
        }

        scope.launch {
            runCatching {
                dao.insert(
                    SignalQualitySample(
                        timestampMs = System.currentTimeMillis(),
                        usableChannelsFraction = summary.usableChannelsFraction,
                        clippedSamplesFraction = summary.clippedSamplesFraction,
                        flatlineChannelsFraction = summary.flatlineChannelsFraction,
                        meanUv = mean,
                    ),
                )
                persisted.incrementAndGet()
            }
        }
    }

    private fun fail(message: String) {
        Log.w(TAG, "Session failed: $message")
        StreamHub.update { it.copy(phase = StreamPhase.ERROR, message = message) }
    }

    fun stop() {
        running = false
        rfcomm?.stop()
        rfcomm?.close()
    }

    private suspend fun cleanup() {
        runCatching { rfcomm?.close() }
        rfcomm = null
        delay(Mw75Constants.RFCOMM_SETTLE_DELAY_MS)
        runCatching { activator?.sendDisableBestEffort() }
        activator = null
        StreamHub.update {
            val terminal = if (it.phase == StreamPhase.ERROR) StreamPhase.ERROR else StreamPhase.STOPPED
            it.copy(phase = terminal)
        }
        Log.i(TAG, "Session cleanup complete")
    }

    private companion object {
        const val TAG = "Mw75Controller"
    }
}
