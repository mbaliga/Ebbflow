package ai.ebbflow.baseline.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusSampleDao {

    @Insert
    suspend fun insert(sample: FocusSample)

    @Query("SELECT COUNT(*) FROM focus_samples")
    suspend fun count(): Long

    @Query("SELECT * FROM focus_samples ORDER BY timestampMs DESC LIMIT :limit")
    fun recent(limit: Int = 120): Flow<List<FocusSample>>

    @Query("DELETE FROM focus_samples")
    suspend fun clear()
}
