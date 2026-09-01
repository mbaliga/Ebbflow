package ai.ebbflow.eeg

import kotlin.math.abs

enum class SignalQualityLabel { GOOD, FAIR, POOR }

data class SignalQualitySummary(
    val usableChannelsFraction: Double,
    val clippedSamplesFraction: Double,
    val flatlineChannelsFraction: Double,
    val label: SignalQualityLabel,
)

/**
 * Device-agnostic acquisition checks over a short window. This deliberately makes no
 * claim about attention, emotion, diagnosis, or any other person state.
 */
class SignalQualityEstimator(
    private val windowPackets: Int = 250,
    private val emitEveryPackets: Int = 125,
    private val clipThresholdUv: Double = 500.0,
    private val flatlineDeltaUv: Double = 0.05,
) {
    private val window = ArrayDeque<EegPacket>()
    private var sinceEmission = 0

    fun reset() {
        window.clear()
        sinceEmission = 0
    }

    fun add(packet: EegPacket): SignalQualitySummary? {
        window.addLast(packet)
        while (window.size > windowPackets) window.removeFirst()
        sinceEmission++
        if (window.size < windowPackets || sinceEmission < emitEveryPackets) return null
        sinceEmission = 0

        val channelCount = packet.channels.size
        val totalValues = window.size * channelCount
        val clipped = window.sumOf { sample -> sample.channels.count { !it.isFinite() || abs(it) >= clipThresholdUv } }
        val flatlineChannels = (0 until channelCount).count { channel ->
            window.zipWithNext().all { (left, right) ->
                abs(left.channels[channel] - right.channels[channel]) <= flatlineDeltaUv
            }
        }
        val clippedFraction = clipped.toDouble() / totalValues
        val flatlineFraction = flatlineChannels.toDouble() / channelCount
        val usableFraction = (1.0 - flatlineFraction - clippedFraction).coerceIn(0.0, 1.0)
        val label = when {
            usableFraction >= 0.9 && clippedFraction < 0.01 -> SignalQualityLabel.GOOD
            usableFraction >= 0.65 && clippedFraction < 0.05 -> SignalQualityLabel.FAIR
            else -> SignalQualityLabel.POOR
        }
        return SignalQualitySummary(usableFraction, clippedFraction, flatlineFraction, label)
    }
}
