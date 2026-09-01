package ai.ebbflow.eeg

/** A checksum-valid MW75 EEG packet. Channel values are microvolts. */
data class EegPacket(
    val timestampMs: Long,
    val eventId: Int,
    val counter: Int,
    val ref: Float,
    val drl: Float,
    val channels: List<Double>,
    val featureStatus: Int,
)
