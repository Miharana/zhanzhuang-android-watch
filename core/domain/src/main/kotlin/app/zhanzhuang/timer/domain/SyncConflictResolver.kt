package app.zhanzhuang.timer.domain

import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus

/**
 * Conservatively chooses a durable session record.  A terminal record is never
 * regressed by an active record, even if a delayed peer accidentally presents a
 * higher revision; legitimate terminal updates still follow their revision.
 */
class SyncConflictResolver {
    fun merge(current: SessionRecord?, incoming: SessionRecord): SessionRecord = when {
        current == null -> incoming
        current.id != incoming.id -> current
        current.status.isTerminal && !incoming.status.isTerminal -> current
        incoming.revision > current.revision -> incoming
        incoming.revision < current.revision -> current
        incoming.status.isTerminal && !current.status.isTerminal -> incoming
        else -> current
    }

    private val SessionStatus.isTerminal: Boolean
        get() = this == SessionStatus.COMPLETED || this == SessionStatus.CANCELLED || this == SessionStatus.INTERRUPTED
}
