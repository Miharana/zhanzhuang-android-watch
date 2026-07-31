package app.zhanzhuang.timer.mobile.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SessionEntity::class, HeartRateEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class MobileDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
}
