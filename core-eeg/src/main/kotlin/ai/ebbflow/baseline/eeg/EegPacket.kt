package ai.ebbflow.baseline.eeg

/**
 * A parsed, checksum-valid EEG packet. Mirrors the Python `EEGPacket`
 * (mw75_streamer/data/packet_processor.py). [channels] are in microvolts
 * (raw ADC already multiplied by [Mw75Constants.EEG_SCALING_FACTOR]).
 */
data class EegPacket(
    val timestampMs: Long,
    val eventId: Int,
    val counter: Int,
    val ref: Float,
    val drl: Float,
    val channels: List<Double>,
    val featureStatus: Int,
)
