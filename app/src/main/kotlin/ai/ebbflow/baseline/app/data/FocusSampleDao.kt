package ai.ebbflow.baseline.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SignalQualitySampleDao {

    @Insert
    suspend fun insert(sample: SignalQualitySample)

    @Query("SELECT COUNT(*) FROM signal_quality_samples")
    suspend fun count(): Long

    @Query("SELECT * FROM signal_quality_samples ORDER BY timestampMs DESC LIMIT :limit")
    fun recent(limit: Int = 120): Flow<List<SignalQualitySample>>

    @Query("DELETE FROM signal_quality_samples")
    suspend fun clear()
}
