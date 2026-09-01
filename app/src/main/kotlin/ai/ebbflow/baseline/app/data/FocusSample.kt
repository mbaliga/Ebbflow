package ai.ebbflow.baseline.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One persisted acquisition-quality summary. Contains no mental-state inference. */
@Entity(tableName = "signal_quality_samples")
data class SignalQualitySample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val usableChannelsFraction: Double,
    val clippedSamplesFraction: Double,
    val flatlineChannelsFraction: Double,
    val meanUv: Double,
)
