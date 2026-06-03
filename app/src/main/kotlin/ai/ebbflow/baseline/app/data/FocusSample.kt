package ai.ebbflow.baseline.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One persisted focus reading. We store the derived [focus] index and its inputs
 * rather than every 500 Hz raw sample — raw-corpus storage is a Phase 2 concern
 * (build_plan.md §2); this table is just enough to show history in the app and to
 * confirm the pipeline persists end-to-end during bring-up.
 */
@Entity(tableName = "focus_samples")
data class FocusSample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val focus: Double,
    val thetaBetaRatio: Double?,
    val meanUv: Double,
)
