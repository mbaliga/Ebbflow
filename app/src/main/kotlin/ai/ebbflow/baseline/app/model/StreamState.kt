package ai.ebbflow.baseline.app.model

/** Where we are in the MW75 connection/streaming lifecycle. */
enum class StreamPhase {
    IDLE,
    SCANNING,
    ACTIVATING,
    CONNECTING_RFCOMM,
    STREAMING,
    STOPPED,
    ERROR,
}

/**
 * Immutable snapshot of the streaming session, observed by the UI. Produced by
 * [ai.ebbflow.baseline.app.bluetooth.Mw75Controller] and published through
 * [StreamHub]. All EEG values are in microvolts. Quality values describe acquisition
 * integrity only; they are not cognitive or clinical state estimates.
 */
data class StreamState(
    val phase: StreamPhase = StreamPhase.IDLE,
    val deviceName: String? = null,
    val batteryPercent: Int? = null,
    val usableChannelsFraction: Double? = null,
    val clippedSamplesFraction: Double? = null,
    val flatlineChannelsFraction: Double? = null,
    val qualityLabel: String? = null,
    val validPackets: Long = 0,
    val invalidPackets: Long = 0,
    val errorRatePercent: Double = 0.0,
    val latestChannelsUv: List<Double> = emptyList(),
    val samplesPersisted: Long = 0,
    val message: String? = null,
    val lastUpdateMs: Long = 0,
    /** Wall-clock time this session's start() began; 0 before the first session. */
    val sessionStartedMs: Long = 0,
    /** When [batteryPercent] was read from the headset (at activation); 0 when not read. */
    val batteryReadMs: Long = 0,
    /**
     * When the quality fields last came from a fresh summary. Fields carry over between
     * sessions, so a value older than [sessionStartedMs] belongs to the previous session.
     */
    val qualityUpdatedMs: Long = 0,
)
