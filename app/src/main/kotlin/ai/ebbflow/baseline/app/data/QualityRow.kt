package ai.ebbflow.baseline.app.data

/**
 * The integrity columns of one [SignalQualitySample], without its µV mean. Read by the
 * home-screen widgets, which never show signal values.
 */
data class QualityRow(
    val timestampMs: Long,
    val usableChannelsFraction: Double,
    val clippedSamplesFraction: Double,
    val flatlineChannelsFraction: Double,
)
