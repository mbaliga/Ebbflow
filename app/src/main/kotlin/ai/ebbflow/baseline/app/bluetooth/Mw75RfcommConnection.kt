package ai.ebbflow.baseline.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import ai.ebbflow.eeg.Mw75Constants
import java.io.IOException
import java.util.UUID

/**
 * Bluetooth Classic RFCOMM link to the MW75, the Android analogue of the macOS
 * `RFCOMMManager` (mw75_streamer/device/rfcomm_manager.py).
 *
 * The MW75 streams on a fixed RFCOMM channel ([Mw75Constants.RFCOMM_CHANNEL] = 25)
 * rather than advertising an SDP service record, and the public Android API only
 * exposes `createRfcommSocketToServiceRecord(UUID)` (SDP-based). We therefore use
 * the long-standing hidden `createRfcommSocket(int channel)` via reflection to open
 * channel 25 directly, falling back to the SPP UUID (secure then insecure) if the
 * hidden method is unavailable on a given OEM build.
 *
 * The device must already be bonded (paired in system Bluetooth settings); this
 * class does not initiate pairing.
 *
 * Caller must hold BLUETOOTH_CONNECT (API 31+).
 */
@SuppressLint("MissingPermission")
class Mw75RfcommConnection(private val device: BluetoothDevice) {

    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var shouldStop = false

    val deviceAddress: String? get() = device.address

    /** Open the RFCOMM socket; returns true on success. */
    fun connect(): Boolean {
        shouldStop = false
        val sock = openSocket()
        if (sock == null) {
            Log.e(TAG, "All RFCOMM socket strategies failed")
            return false
        }
        return try {
            sock.connect() // blocking
            socket = sock
            Log.i(TAG, "RFCOMM connected on channel ${Mw75Constants.RFCOMM_CHANNEL}")
            true
        } catch (e: IOException) {
            Log.e(TAG, "RFCOMM connect failed: ${e.message}")
            runCatching { sock.close() }
            false
        }
    }

    /**
     * Blocking read loop. Delivers raw bytes to [onData] as they arrive (framing /
     * parsing happens downstream in `PacketParser`). Returns when [stop] is called
     * or the socket errors.
     */
    fun readLoop(onData: (ByteArray) -> Unit) {
        val input = try {
            socket?.inputStream
        } catch (e: IOException) {
            Log.e(TAG, "Failed to get input stream: ${e.message}")
            null
        } ?: return

        val buffer = ByteArray(READ_BUFFER_SIZE)
        while (!shouldStop) {
            val n = try {
                input.read(buffer)
            } catch (e: IOException) {
                if (!shouldStop) Log.e(TAG, "RFCOMM read error: ${e.message}")
                break
            }
            if (n <= 0) break
            onData(buffer.copyOf(n))
        }
        Log.i(TAG, "RFCOMM read loop ended")
    }

    fun stop() {
        shouldStop = true
    }

    fun close() {
        shouldStop = true
        runCatching { socket?.close() }
        socket = null
    }

    /** Try channel-25 reflection first, then SPP UUID (secure, then insecure). */
    private fun openSocket(): BluetoothSocket? {
        rfcommViaReflection()?.let { return it }
        Log.w(TAG, "Reflection channel socket unavailable; falling back to SPP UUID")
        rfcommViaUuid(secure = true)?.let { return it }
        rfcommViaUuid(secure = false)?.let { return it }
        return null
    }

    private fun rfcommViaReflection(): BluetoothSocket? = try {
        val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
        method.invoke(device, Mw75Constants.RFCOMM_CHANNEL) as? BluetoothSocket
    } catch (e: Exception) {
        Log.w(TAG, "createRfcommSocket(${Mw75Constants.RFCOMM_CHANNEL}) reflection failed: ${e.message}")
        null
    }

    private fun rfcommViaUuid(secure: Boolean): BluetoothSocket? = try {
        val uuid = UUID.fromString(SPP_UUID)
        if (secure) device.createRfcommSocketToServiceRecord(uuid)
        else device.createInsecureRfcommSocketToServiceRecord(uuid)
    } catch (e: IOException) {
        Log.w(TAG, "SPP RFCOMM socket (secure=$secure) failed: ${e.message}")
        null
    }

    private companion object {
        const val TAG = "Mw75Rfcomm"
        const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"
        const val READ_BUFFER_SIZE = 1024
    }
}
