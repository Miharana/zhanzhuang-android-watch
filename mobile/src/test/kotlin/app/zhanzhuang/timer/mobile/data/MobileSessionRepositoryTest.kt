package app.zhanzhuang.timer.mobile.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionOwner
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MobileSessionRepositoryTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        MobileDatabase::class.java,
    ).allowMainThreadQueries().build()
    private val repository = MobileSessionRepository(database)

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun staleRevisionCannotReplaceTerminalRecord() = runTest {
        repository.upsert(record("s1", 2, SessionStatus.COMPLETED))
        repository.upsert(record("s1", 1, SessionStatus.RUNNING))

        assertEquals(SessionStatus.COMPLETED, repository.get("s1")!!.status)
    }

    private fun record(id: String, revision: Long, status: SessionStatus) = SessionRecord(
        id = id,
        revision = revision,
        config = SessionConfig(15, 5),
        status = status,
        owner = SessionOwner.MOBILE,
        startEpochMillis = 1_000,
    )
}
