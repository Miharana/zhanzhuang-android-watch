package app.zhanzhuang.timer.wear.sync

import app.zhanzhuang.timer.model.SessionRecord
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** In-process confirmation between Data Layer adapter and the serialized FGS actor. */
object WearServiceCommandGateway {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<SessionRecord?>>()

    suspend fun dispatch(
        timeoutMillis: Long = TIMEOUT_MS,
        send: (String) -> Unit,
    ): SessionRecord? {
        require(timeoutMillis > 0) { "timeoutMillis must be positive" }
        val requestId = UUID.randomUUID().toString()
        val result = CompletableDeferred<SessionRecord?>()
        pending[requestId] = result
        try {
            send(requestId)
            return withTimeoutOrNull(timeoutMillis) { result.await() }
        } finally {
            pending.remove(requestId)
        }
    }

    fun complete(requestId: String?, record: SessionRecord?) {
        if (requestId != null) pending.remove(requestId)?.complete(record)
    }

    private const val TIMEOUT_MS = 10_000L
}
