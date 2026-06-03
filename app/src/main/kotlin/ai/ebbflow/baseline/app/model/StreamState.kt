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
 * [StreamHub]. All EEG values are in microvolts; [focus] is the 0..1 placeholder
 * focus index from the pure-JVM core (`FocusEstimator`).
 */
data class StreamState(
    val phase: StreamPhase = StreamPhase.IDLE,
    val deviceName: String? = null,
    val batteryPercent: Int? = null,
    val focus: Double? = null,
    val thetaBetaRatio: Double? = null,
    val validPackets: Long = 0,
    val invalidPackets: Long = 0,
    val errorRatePercent: Double = 0.0,
    val latestChannelsUv: List<Double> = emptyList(),
    val samplesPersisted: Long = 0,
    val message: String? = null,
    val lastUpdateMs: Long = 0,
)
