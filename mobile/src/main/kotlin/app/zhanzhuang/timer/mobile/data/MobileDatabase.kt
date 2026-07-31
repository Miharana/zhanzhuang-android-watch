package app.zhanzhuang.timer.mobile.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SessionEntity::class, HeartRateEntity::class, MobileRuntimeEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class MobileDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS mobile_runtime (singletonId INTEGER NOT NULL, sessionId TEXT NOT NULL, startedAtElapsedMs INTEGER NOT NULL, checkpointElapsedMs INTEGER NOT NULL, checkpointWallEpochMs INTEGER NOT NULL, pausedAtElapsedMs INTEGER, acknowledgedReminderIndex INTEGER NOT NULL, PRIMARY KEY(singletonId), FOREIGN KEY(sessionId) REFERENCES sessions(id) ON DELETE CASCADE)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_mobile_runtime_sessionId ON mobile_runtime(sessionId)",
                )
            }
        }
    }
}
