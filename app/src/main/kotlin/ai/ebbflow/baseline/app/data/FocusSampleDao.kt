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

    /** Newest-first integrity columns only (no µV), for the home-screen widgets. */
    @Query(
        "SELECT timestampMs, usableChannelsFraction, clippedSamplesFraction, flatlineChannelsFraction " +
            "FROM signal_quality_samples ORDER BY timestampMs DESC LIMIT :limit",
    )
    suspend fun latestQuality(limit: Int): List<QualityRow>

    @Query("SELECT MIN(timestampMs) FROM signal_quality_samples")
    suspend fun oldestTimestampMs(): Long?

    @Query("SELECT MAX(timestampMs) FROM signal_quality_samples")
    suspend fun newestTimestampMs(): Long?
}
