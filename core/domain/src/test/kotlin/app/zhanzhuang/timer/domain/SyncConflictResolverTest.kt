package app.zhanzhuang.timer.domain

import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionOwner
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncConflictResolverTest {
    private val resolver = SyncConflictResolver()

    @Test
    fun terminalRevisionWins() {
        val completedV8 = record(revision = 8, status = SessionStatus.COMPLETED)
        val runningV7 = record(revision = 7, status = SessionStatus.RUNNING)

        assertEquals(completedV8, resolver.merge(completedV8, runningV7))
    }

    @Test
    fun terminalStateWinsTieWithoutChangingOwnership() {
        val completed = record(revision = 8, status = SessionStatus.COMPLETED, owner = SessionOwner.WEAR)
        val running = record(revision = 8, status = SessionStatus.RUNNING, owner = SessionOwner.MOBILE)

        assertEquals(completed, resolver.merge(completed, running))
    }

    @Test
    fun wearStateConfirmsEqualRevisionMobileProvisional() {
        val provisional = record(revision = 1, status = SessionStatus.STARTING, owner = SessionOwner.MOBILE)
        val acceptedOnWear = record(revision = 1, status = SessionStatus.RUNNING, owner = SessionOwner.WEAR)

        assertEquals(acceptedOnWear, resolver.merge(provisional, acceptedOnWear))
    }

    private fun record(
        revision: Long,
        status: SessionStatus,
        owner: SessionOwner = SessionOwner.MOBILE,
    ) = SessionRecord(
        id = "session-1",
        revision = revision,
        config = SessionConfig(),
        status = status,
        owner = owner,
        startEpochMillis = 1_000,
        activeDurationMs = 1_000,
    )
}
