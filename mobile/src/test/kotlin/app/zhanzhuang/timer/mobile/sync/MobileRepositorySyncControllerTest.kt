package app.zhanzhuang.timer.mobile.sync

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.zhanzhuang.timer.mobile.data.MobileRuntimeSnapshot
import app.zhanzhuang.timer.mobile.data.SessionRepository
import app.zhanzhuang.timer.mobile.session.MobileSessionService
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionOwner
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MobileRepositorySyncControllerTest {
    @Test
    fun remoteCancelledFinishStartsServiceWithExplicitCancellationFlag() = runTest {
        val repository = RecordingRepository(record(SessionStatus.RUNNING))
        val context = RecordingContext(ApplicationProvider.getApplicationContext())
        val controller = MobileRepositorySyncController(repository, nowEpochMillis = { 2_000 }, appContext = context)

        val result = controller.finishFromRemote("session-1", cancelled = true)

        assertEquals(SessionStatus.CANCELLED, result?.status)
        val intent = context.started.single()
        assertEquals(MobileSessionService.ACTION_FINISH, intent.action)
        assertTrue(intent.getBooleanExtra(MobileSessionService.EXTRA_CANCELLED, false))
    }

    private fun record(status: SessionStatus) = SessionRecord(
        id = "session-1",
        revision = 1,
        config = SessionConfig(),
        status = status,
        owner = SessionOwner.MOBILE,
        startEpochMillis = 1_000,
    )

    private class RecordingRepository(initial: SessionRecord) : SessionRepository {
        private var record = initial
        override fun observeSessions(): Flow<List<SessionRecord>> = emptyFlow()
        override suspend fun get(id: String): SessionRecord? = record.takeIf { it.id == id }
        override suspend fun activeSession(): SessionRecord? = record.takeIf { it.status in setOf(SessionStatus.STARTING, SessionStatus.RUNNING, SessionStatus.PAUSED, SessionStatus.COMPLETING) }
        override suspend fun runtimeSnapshot(): MobileRuntimeSnapshot? = null
        override suspend fun upsert(record: SessionRecord, runtimeSnapshot: MobileRuntimeSnapshot?) { this.record = record }
    }

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        val started = mutableListOf<Intent>()
        override fun startService(service: Intent): ComponentName? {
            started += Intent(service)
            return ComponentName(packageName, "MobileRepositorySyncControllerTest")
        }
    }
}
