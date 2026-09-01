package ai.ebbflow.baseline.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import ai.ebbflow.eeg.Mw75Constants
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.util.UUID

/**
 * MW75 BLE activation handshake — a port of the reference Python `BLEManager`
 * (mw75_streamer/device/ble_manager.py).
 *
 * The headphones do not stream over BLE; BLE is only used to put the device into
 * EEG + raw mode, after which the actual 12-channel data flows over Bluetooth
 * Classic RFCOMM (see [Mw75RfcommConnection]). The sequence is:
 *   1. connect GATT, discover services
 *   2. enable notifications on the status characteristic
 *   3. write ENABLE_EEG       → wait 100 ms
 *   4. write ENABLE_RAW_MODE  → wait 500 ms
 *   5. write BATTERY          → wait 500 ms
 *   6. confirm EEG + raw mode were acknowledged (status byte == success)
 *
 * Android's GATT API permits only one outstanding operation at a time, so each
 * write awaits its completion callback before the next is issued.
 *
 * Caller must hold BLUETOOTH_CONNECT (API 31+).
 */
@SuppressLint("MissingPermission")
class Mw75BleActivator(
    private val context: Context,
    private val device: BluetoothDevice,
) {
    data class ActivationResult(
        val success: Boolean,
        val batteryPercent: Int? = null,
        val message: String? = null,
    )

    private val serviceUuid = UUID.fromString(Mw75Constants.SERVICE_UUID)
    private val commandUuid = UUID.fromString(Mw75Constants.COMMAND_CHAR_UUID)
    private val statusUuid = UUID.fromString(Mw75Constants.STATUS_CHAR_UUID)
    private val cccdUuid = UUID.fromString(CCCD_UUID)

    @Volatile private var eegEnabled = false
    @Volatile private var rawEnabled = false
    @Volatile private var batteryPercent: Int? = null

    // Completion signals fulfilled by the GATT callback (one op in flight at a time).
    private var connected: CompletableDeferred<Boolean>? = null
    private var servicesDiscovered: CompletableDeferred<Boolean>? = null
    private var opComplete: CompletableDeferred<Boolean>? = null

    private val callback = object : android.bluetooth.BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connected?.complete(status == BluetoothGatt.GATT_SUCCESS)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connected?.complete(false)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            servicesDiscovered?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            opComplete?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) {
            opComplete?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        // API 33+ delivers the value directly.
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            ch: BluetoothGattCharacteristic,
            value: ByteArray,
        ) = handleNotification(value)

        // Pre-33 path: read the value from the characteristic.
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            handleNotification(ch.value ?: return)
        }
    }

    /**
     * Decode a status-characteristic notification. Layout mirrors the Python
     * handler: data[3] = command id, data[4] = status; success == [BLE_SUCCESS_CODE].
     */
    private fun handleNotification(data: ByteArray) {
        if (data.size < 5) return
        val cmd = data[3].toInt() and 0xFF
        val status = data[4].toInt() and 0xFF
        Log.d(TAG, "BLE response cmd=0x${cmd.toString(16)} status=0x${status.toString(16)}")
        when {
            cmd == Mw75Constants.BLE_EEG_COMMAND && status == Mw75Constants.BLE_SUCCESS_CODE ->
                eegEnabled = true
            cmd == Mw75Constants.BLE_RAW_MODE_COMMAND && status == Mw75Constants.BLE_SUCCESS_CODE ->
                rawEnabled = true
            cmd == Mw75Constants.BLE_BATTERY_COMMAND && status == Mw75Constants.BLE_SUCCESS_CODE ->
                if (data.size >= 6) batteryPercent = data[5].toInt() and 0xFF
            // Alt battery framing: [09 9A 03 F1 <level>] — success code in data[3].
            cmd == Mw75Constants.BLE_SUCCESS_CODE &&
                data[0].toInt() and 0xFF == 0x09 &&
                data[1].toInt() and 0xFF == 0x9A &&
                data[2].toInt() and 0xFF == 0x03 ->
                batteryPercent = status
        }
    }

    /** Run the full activation handshake, leaving BLE disconnected afterwards. */
    suspend fun activate(): ActivationResult {
        var gatt: BluetoothGatt? = null
        return try {
            gatt = connectAndDiscover() ?: return ActivationResult(false, message = "GATT connect failed")

            val service = gatt.getService(serviceUuid)
                ?: return ActivationResult(false, message = "MW75 GATT service not found")
            val command = service.getCharacteristic(commandUuid)
                ?: return ActivationResult(false, message = "command characteristic not found")
            val statusCh = service.getCharacteristic(statusUuid)
                ?: return ActivationResult(false, message = "status characteristic not found")

            if (!enableNotifications(gatt, statusCh)) {
                return ActivationResult(false, message = "could not enable notifications")
            }

            // Activation sequence with the reference timing.
            writeCommand(gatt, command, Mw75Constants.ENABLE_EEG)
            delay(Mw75Constants.BLE_ACTIVATION_DELAY_MS)
            writeCommand(gatt, command, Mw75Constants.ENABLE_RAW_MODE)
            delay(Mw75Constants.BLE_COMMAND_DELAY_MS)
            writeCommand(gatt, command, Mw75Constants.BATTERY)
            delay(Mw75Constants.BLE_COMMAND_DELAY_MS)

            Log.i(TAG, "Activation results: eeg=$eegEnabled raw=$rawEnabled battery=$batteryPercent")
            if (eegEnabled && rawEnabled) {
                ActivationResult(true, batteryPercent)
            } else {
                ActivationResult(false, batteryPercent, "EEG/raw mode not confirmed")
            }
        } catch (e: TimeoutCancellationException) {
            ActivationResult(false, message = "BLE activation timed out")
        } catch (e: Exception) {
            Log.e(TAG, "BLE activation error", e)
            ActivationResult(false, message = e.message)
        } finally {
            // Mirror disconnect_after_activation: RFCOMM is more reliable with BLE
            // released, and some stacks block Classic callbacks while GATT is open.
            runCatching {
                gatt?.disconnect()
                gatt?.close()
            }
        }
    }

    /**
     * Best-effort reset of device state on stop: reconnect, send DISABLE_RAW_MODE
     * then DISABLE_EEG, disconnect. Failures are swallowed (the device powers its
     * EEG down on its own when the RFCOMM link drops; this is just tidy-up).
     */
    suspend fun sendDisableBestEffort() {
        var gatt: BluetoothGatt? = null
        try {
            gatt = connectAndDiscover() ?: return
            val command = gatt.getService(serviceUuid)?.getCharacteristic(commandUuid) ?: return
            writeCommand(gatt, command, Mw75Constants.DISABLE_RAW_MODE)
            delay(Mw75Constants.BLE_ACTIVATION_DELAY_MS)
            writeCommand(gatt, command, Mw75Constants.DISABLE_EEG)
            delay(Mw75Constants.BLE_COMMAND_DELAY_MS)
        } catch (e: Exception) {
            Log.w(TAG, "BLE disable (best-effort) failed: ${e.message}")
        } finally {
            runCatching {
                gatt?.disconnect()
                gatt?.close()
            }
        }
    }

    private suspend fun connectAndDiscover(): BluetoothGatt? {
        connected = CompletableDeferred()
        servicesDiscovered = CompletableDeferred()
        val gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        val ok = withTimeout(CONNECT_TIMEOUT_MS) { connected!!.await() }
        if (!ok) {
            runCatching { gatt.close() }
            return null
        }
        gatt.discoverServices()
        val discovered = withTimeout(CONNECT_TIMEOUT_MS) { servicesDiscovered!!.await() }
        if (!discovered) {
            runCatching { gatt.disconnect(); gatt.close() }
            return null
        }
        return gatt
    }

    @Suppress("DEPRECATION")
    private suspend fun enableNotifications(gatt: BluetoothGatt, ch: BluetoothGattCharacteristic): Boolean {
        gatt.setCharacteristicNotification(ch, true)
        val cccd = ch.getDescriptor(cccdUuid) ?: return false
        opComplete = CompletableDeferred()
        val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(cccd, value)
        } else {
            cccd.value = value
            gatt.writeDescriptor(cccd)
        }
        return withTimeout(OP_TIMEOUT_MS) { opComplete!!.await() }
    }

    @Suppress("DEPRECATION")
    private suspend fun writeCommand(gatt: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray): Boolean {
        opComplete = CompletableDeferred()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(ch, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            ch.value = value
            ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            gatt.writeCharacteristic(ch)
        }
        return withTimeout(OP_TIMEOUT_MS) { opComplete!!.await() }
    }

    private companion object {
        const val TAG = "Mw75BleActivator"
        const val CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb"
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val OP_TIMEOUT_MS = 5_000L
    }
}
