package ai.ebbflow.eeg

/**
 * A producer of [EegPacket]s that a head (Android service, desktop window) drives with
 * [start] and [stop]. Events arrive on a [Listener]; the core takes no coroutine
 * dependency, so each head adapts the callbacks to its own concurrency model.
 *
 * Implementations connect only after [start] is called and release the device on [stop].
 * They never reconnect on their own after [stop].
 */
interface EegSource {
    fun start(listener: Listener)
    fun stop()

    interface Listener {
        fun onStatus(status: EegSourceStatus)
        fun onPacket(packet: EegPacket)

        /** Optional; sources that cannot count packets never call it. */
        fun onStatistics(statistics: EegStreamStatistics) {}
    }
}

/** Coarse connection phase every source can report; finer sub-phases stay in the head. */
enum class EegSourcePhase { IDLE, CONNECTING, STREAMING, RECONNECTING, STOPPED, ERROR }

data class EegSourceStatus(
    val phase: EegSourcePhase,
    val message: String? = null,
    /** Null when the source does not report a device name. */
    val deviceName: String? = null,
)

/** A null count means the source does not report it, which is not the same as zero. */
data class EegStreamStatistics(
    val validPackets: Long?,
    val invalidPackets: Long?,
)
