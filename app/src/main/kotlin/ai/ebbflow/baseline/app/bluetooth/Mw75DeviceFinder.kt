package ai.ebbflow.baseline.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.util.Log
import ai.ebbflow.eeg.Mw75Constants
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * Locates the MW75. RFCOMM (Bluetooth Classic) requires the headphones to already
 * be bonded for audio, so a bonded device whose name contains "MW75" is the fast
 * path and is preferred. If none is bonded yet we fall back to a BLE scan (which
 * mirrors the reference Python `BleakScanner.discover`), but the user still has to
 * pair the headset in system settings before RFCOMM will succeed.
 *
 * Caller must hold BLUETOOTH_CONNECT/BLUETOOTH_SCAN (API 31+) or the legacy
 * location permission (API <= 30); see [BluetoothPermissions].
 */
@SuppressLint("MissingPermission")
class Mw75DeviceFinder(private val adapter: BluetoothAdapter) {

    /** A bonded MW75, or null if none is paired. */
    fun bondedMw75(): BluetoothDevice? = adapter.bondedDevices?.firstOrNull { it.matchesMw75() }

    /** Scan for an advertising MW75 for up to [timeoutMs]; null if not found. */
    suspend fun scanForMw75(timeoutMs: Long = SCAN_TIMEOUT_MS): BluetoothDevice? {
        val scanner = adapter.bluetoothLeScanner ?: return null
        return try {
            withTimeout(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    val callback = object : ScanCallback() {
                        override fun onScanResult(callbackType: Int, result: ScanResult) {
                            val device = result.device ?: return
                            if (device.matchesMw75() && cont.isActive) {
                                scanner.stopScan(this)
                                cont.resume(device)
                            }
                        }

                        override fun onScanFailed(errorCode: Int) {
                            Log.w(TAG, "BLE scan failed: $errorCode")
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                    val settings = ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build()
                    // No ScanFilter by name (unreliable across firmware); match in callback.
                    scanner.startScan(emptyList(), settings, callback)
                    cont.invokeOnCancellation { runCatching { scanner.stopScan(callback) } }
                }
            }
        } catch (_: TimeoutCancellationException) {
            null
        }
    }

    /** Bonded device if available, otherwise a scan result. */
    suspend fun find(): BluetoothDevice? = bondedMw75() ?: scanForMw75()

    private fun BluetoothDevice.matchesMw75(): Boolean =
        name?.uppercase()?.contains(Mw75Constants.DEVICE_NAME_PATTERN) == true

    private companion object {
        const val TAG = "Mw75DeviceFinder"
        const val SCAN_TIMEOUT_MS = 15_000L
    }
}
