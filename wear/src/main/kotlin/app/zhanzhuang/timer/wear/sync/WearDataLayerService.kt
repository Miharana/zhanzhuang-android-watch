package app.zhanzhuang.timer.wear.sync

import android.content.Context
import androidx.room.Room
import app.zhanzhuang.timer.wear.data.WearDatabase
import app.zhanzhuang.timer.wear.data.WearSessionRepository
import app.zhanzhuang.timer.model.PATH_ACK
import app.zhanzhuang.timer.model.PATH_COMMAND
import app.zhanzhuang.timer.model.PATH_COMPLETED
import app.zhanzhuang.timer.model.PATH_STATE
import app.zhanzhuang.timer.model.SyncEnvelope
import app.zhanzhuang.timer.model.SyncPayload
import app.zhanzhuang.timer.model.SyncTransport
import app.zhanzhuang.timer.model.SyncWireCodec
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object WearSyncRuntime {
    @Volatile private var coordinator: WearSyncCoordinator? = null
    fun get(context: Context): WearSyncCoordinator = coordinator ?: synchronized(this) {
        coordinator ?: run {
            val repository = WearSessionRepository(
                Room.databaseBuilder(context.applicationContext, WearDatabase::class.java, "wear-sessions.db").build(),
            )
            WearSyncCoordinator(
                outbox = WearRepositoryCompletionOutbox(repository),
                transport = WearAndroidDataLayerTransport(context.applicationContext),
                controller = WearServiceSyncController(context.applicationContext, repository),
            ).also { coordinator = it }
        }
    }
}

class WearDataLayerService : com.google.android.gms.wearable.WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val receiveMutex = Mutex()

    override fun onCreate() {
        super.onCreate()
        WearCompletionSyncWorker.enqueue(applicationContext)
        scope.launch { runCatching { WearSyncRuntime.get(applicationContext).resendCompleted() } }
    }

    override fun onPeerConnected(peer: com.google.android.gms.wearable.Node) {
        super.onPeerConnected(peer)
        WearCompletionSyncWorker.enqueue(applicationContext)
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path !in setOf(PATH_COMMAND, PATH_STATE, PATH_ACK)) return
        scope.launch {
            val envelope = runCatching { SyncWireCodec.decodeMessage(event.data) }.getOrNull() ?: return@launch
            receiveMutex.withLock { runCatching { WearSyncRuntime.get(applicationContext).receiveCommand(envelope, event.sourceNodeId) } }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path?.startsWith("$PATH_COMPLETED/") == true }
            .forEach { event ->
                val payload = runCatching { DataMapItem.fromDataItem(event.dataItem).dataMap.getByteArray(SyncWireCodec.DATA_KEY_ENVELOPE) }
                    .getOrNull() ?: return@forEach
                scope.launch {
                    val envelope = runCatching { SyncWireCodec.decodeCompleted(payload) }.getOrNull() ?: return@launch
                    receiveMutex.withLock { runCatching { WearSyncRuntime.get(applicationContext).receiveCommand(envelope, event.dataItem.uri.host) } }
                }
            }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

class WearAndroidDataLayerTransport(context: Context) : SyncTransport {
    private val appContext = context.applicationContext

    override suspend fun sendMessage(envelope: SyncEnvelope): Boolean {
        val path = when (envelope.payload) {
            is SyncPayload.Ack -> PATH_ACK
            is SyncPayload.State,
            is SyncPayload.Runtime,
            -> PATH_STATE
            else -> PATH_COMMAND
        }
        val nodes = withTimeoutOrNull(TRANSPORT_TIMEOUT_MS) { Wearable.getCapabilityClient(appContext)
            .getCapability(CAPABILITY_NAME, CapabilityClient.FILTER_REACHABLE)
            .await()
            .nodes } ?: return false
        return nodes.map { node ->
            runCatching { withTimeoutOrNull(TRANSPORT_TIMEOUT_MS) { Wearable.getMessageClient(appContext).sendMessage(node.id, path, SyncWireCodec.encodeMessage(envelope)).await() } != null }.getOrDefault(false)
        }.any { it }
    }

    override suspend fun sendMessageTo(envelope: SyncEnvelope, nodeId: String): Boolean {
        val path = when (envelope.payload) {
            is SyncPayload.Ack -> PATH_ACK
            is SyncPayload.State,
            is SyncPayload.Runtime,
            -> PATH_STATE
            else -> PATH_COMMAND
        }
        return runCatching { withTimeoutOrNull(TRANSPORT_TIMEOUT_MS) {
            Wearable.getMessageClient(appContext).sendMessage(nodeId, path, SyncWireCodec.encodeMessage(envelope)).await()
        } != null }.getOrDefault(false)
    }

    override suspend fun putCompleted(envelope: SyncEnvelope): Boolean {
        val request = PutDataMapRequest.create("$PATH_COMPLETED/${envelope.eventId}").apply {
            dataMap.putByteArray(SyncWireCodec.DATA_KEY_ENVELOPE, SyncWireCodec.encodeCompleted(envelope))
            dataMap.putLong("delivery_attempt", System.nanoTime())
        }.asPutDataRequest().setUrgent()
        return runCatching { withTimeoutOrNull(TRANSPORT_TIMEOUT_MS) { Wearable.getDataClient(appContext).putDataItem(request).await() } != null }.getOrDefault(false)
    }

    private companion object { const val CAPABILITY_NAME = "zhan_zhang_sync"; const val TRANSPORT_TIMEOUT_MS = 4_000L }
}
