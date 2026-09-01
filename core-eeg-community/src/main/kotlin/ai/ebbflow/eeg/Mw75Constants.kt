package ai.ebbflow.eeg

/** Documented acquisition constants ported from the reference MW75 streamer. */
object Mw75Constants {
    const val SERVICE_UUID = "00001100-d102-11e1-9b23-00025b00a5a5"
    const val COMMAND_CHAR_UUID = "00001101-d102-11e1-9b23-00025b00a5a5"
    const val STATUS_CHAR_UUID = "00001102-d102-11e1-9b23-00025b00a5a5"
    val ENABLE_EEG = byteArrayOf(0x09, 0x9A.toByte(), 0x03, 0x60, 0x01)
    val DISABLE_EEG = byteArrayOf(0x09, 0x9A.toByte(), 0x03, 0x60, 0x00)
    val ENABLE_RAW_MODE = byteArrayOf(0x09, 0x9A.toByte(), 0x03, 0x41, 0x01)
    val DISABLE_RAW_MODE = byteArrayOf(0x09, 0x9A.toByte(), 0x03, 0x41, 0x00)
    val BATTERY = byteArrayOf(0x09, 0x9A.toByte(), 0x03, 0x14, 0xFF.toByte())
    const val BLE_SUCCESS_CODE = 0xF1
    const val BLE_EEG_COMMAND = 0x60
    const val BLE_RAW_MODE_COMMAND = 0x41
    const val BLE_BATTERY_COMMAND = 0x14
    const val BLE_ACTIVATION_DELAY_MS = 100L
    const val BLE_COMMAND_DELAY_MS = 500L
    const val RFCOMM_SETTLE_DELAY_MS = 500L
    const val EEG_EVENT_ID = 239
    const val PACKET_SIZE = 63
    const val SYNC_BYTE = 0xAA
    const val EEG_SCALING_FACTOR = 0.023842
    const val NUM_EEG_CHANNELS = 12
    const val RFCOMM_CHANNEL = 25
    const val DEVICE_NAME_PATTERN = "MW75"
}
