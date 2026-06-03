package ai.ebbflow.baseline.eeg

/**
 * Port of the MW75 protocol constants from the reference Python streamer
 * (mw75_streamer/config.py). Single source of truth for both the EEG core and
 * the Android Bluetooth layer.
 */
object Mw75Constants {

    // --- BLE GATT ---
    const val SERVICE_UUID = "00001100-d102-11e1-9b23-00025b00a5a5"
    const val COMMAND_CHAR_UUID = "00001101-d102-11e1-9b23-00025b00a5a5"
    const val STATUS_CHAR_UUID = "00001102-d102-11e1-9b23-00025b00a5a5"

    // BLE command sequences (written to the command characteristic).
    val ENABLE_EEG = byteArrayOf(0x09, 0x9A.toByte(), 0x03, 0x60, 0x01)
    val DISABLE_EEG = byteArrayOf(0x09, 0x9A.toByte(), 0x03, 0x60, 0x00)
    val ENABLE_RAW_MODE = byteArrayOf(0x09, 0x9A.toByte(), 0x03, 0x41, 0x01)
    val DISABLE_RAW_MODE = byteArrayOf(0x09, 0x9A.toByte(), 0x03, 0x41, 0x00)
    val BATTERY = byteArrayOf(0x09, 0x9A.toByte(), 0x03, 0x14, 0xFF.toByte())

    // BLE response decoding (status-characteristic notifications): data[3]=command, data[4]=status.
    const val BLE_SUCCESS_CODE = 0xF1
    const val BLE_EEG_COMMAND = 0x60
    const val BLE_RAW_MODE_COMMAND = 0x41
    const val BLE_BATTERY_COMMAND = 0x14

    // Activation timing (milliseconds).
    const val BLE_ACTIVATION_DELAY_MS = 100L
    const val BLE_COMMAND_DELAY_MS = 500L
    const val RFCOMM_SETTLE_DELAY_MS = 500L

    // --- Protocol / packet framing ---
    const val EEG_EVENT_ID = 239
    const val PACKET_SIZE = 63
    const val SYNC_BYTE = 0xAA
    const val EEG_SCALING_FACTOR = 0.023842 // raw ADC -> microvolts
    const val SENTINEL_VALUE = 8388607 // 2^23 - 1, marks a saturated/invalid sample
    const val NUM_EEG_CHANNELS = 12

    // --- RFCOMM ---
    const val RFCOMM_CHANNEL = 25

    /**
     * Nominal EEG sample rate. NOT transmitted in-band; the reference hardcodes this
     * (mw75_streamer/data/streamers.py). Confirm empirically via [SampleRateEstimator]
     * before any frequency-domain analysis.
     */
    const val NOMINAL_SAMPLE_RATE_HZ = 500.0

    // --- Device discovery ---
    const val DEVICE_NAME_PATTERN = "MW75"
}
