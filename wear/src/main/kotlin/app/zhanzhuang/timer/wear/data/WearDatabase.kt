package app.zhanzhuang.timer.wear.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [WearSessionEntity::class, WearHeartRateEntity::class, OutboxEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class WearDatabase : RoomDatabase() {
    abstract fun wearSessionDao(): WearSessionDao
}
