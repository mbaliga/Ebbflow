package ai.ebbflow.baseline.app.widget

import ai.ebbflow.baseline.app.data.QualityRow
import ai.ebbflow.baseline.app.model.StreamPhase
import ai.ebbflow.baseline.app.model.StreamState
import ai.ebbflow.eeg.SignalQualityLabel

internal object Fixtures {
    const val T0 = 1_762_250_000_000L // 2025-11-04, mid-morning UTC

    fun streaming(
        at: Long = T0 + 600_000,
        sessionStarted: Long = T0,
        battery: Int? = 72,
        label: SignalQualityLabel = SignalQualityLabel.GOOD,
        qualityAt: Long = at,
    ) = StreamState(
        phase = StreamPhase.STREAMING,
        deviceName = "Alice's MW75",
        batteryPercent = battery,
        usableChannelsFraction = 0.93,
        clippedSamplesFraction = 0.004,
        flatlineChannelsFraction = 0.0833,
        qualityLabel = label.name,
        validPackets = 12_000,
        invalidPackets = 12,
        errorRatePercent = 0.1,
        latestChannelsUv = listOf(12.5, -3.25),
        samplesPersisted = 40,
        message = "Opening RFCOMM…",
        lastUpdateMs = at,
        sessionStartedMs = sessionStarted,
        batteryReadMs = if (battery != null) sessionStarted + 2_000 else 0,
        qualityUpdatedMs = qualityAt,
    )

    fun snapshot(state: StreamState): AcquisitionSnapshot = AcquisitionSnapshot.of(state)!!

    fun live(state: StreamState = streaming()) = Acquisition(snapshot(state), Liveness.LIVE)

    fun row(at: Long, usable: Double, clipped: Double = 0.0, flatline: Double = 1.0 - usable - clipped) =
        QualityRow(at, usable, clipped, flatline.coerceAtLeast(0.0))

    /** Rows every 500 ms from [start], oldest first. */
    fun run(start: Long, usable: List<Double>, clipped: Double = 0.0): List<QualityRow> =
        usable.mapIndexed { i, u -> row(start + i * 500L, u, clipped) }
}
