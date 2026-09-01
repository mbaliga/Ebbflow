package ai.ebbflow.baseline.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SignalQualitySample::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun signalQualitySampleDao(): SignalQualitySampleDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "ebbflow.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }

        /** Preserve bring-up history while retiring the unvalidated focus columns. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `signal_quality_samples` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestampMs` INTEGER NOT NULL,
                        `usableChannelsFraction` REAL NOT NULL,
                        `clippedSamplesFraction` REAL NOT NULL,
                        `flatlineChannelsFraction` REAL NOT NULL,
                        `meanUv` REAL NOT NULL)""".trimIndent(),
                )
                db.execSQL(
                    """INSERT INTO signal_quality_samples
                        (id, timestampMs, usableChannelsFraction, clippedSamplesFraction,
                         flatlineChannelsFraction, meanUv)
                        SELECT id, timestampMs, 0.0, 0.0, 0.0, meanUv FROM focus_samples""".trimIndent(),
                )
                db.execSQL("DROP TABLE focus_samples")
            }
        }
    }
}
