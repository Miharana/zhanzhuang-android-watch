package app.zhanzhuang.timer.mobile.sync

import android.content.Context
import androidx.room.Room
import app.zhanzhuang.timer.mobile.data.MobileDatabase
import app.zhanzhuang.timer.mobile.data.MobileSessionRepository
import app.zhanzhuang.timer.model.PATH_ACK
import app.zhanzhuang.timer.model.PATH_COMMAND
import app.zhanzhuang.timer.model.PATH_COMPLETED
import app.zhanzhuang.timer.model.PATH_STATE
import app.zhanzhuang.timer.model.SyncEnvelope
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

object MobileSyncRuntime {
    @Volatile private var coordinator: MobileSyncCoordinator? = null
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun get(context: Context): MobileSyncCoordinator = coordinator ?: synchronized(this) {
        coordinator ?: MobileSyncCoordinator(
            transport = MobileAndroidDataLayerTransport(context.applicationContext),
            controller = MobileRepositorySyncController(
                repository = MobileSessionRepository(
                    Room.databaseBuilder(context.applicationContext, MobileDatabase::class.java, "zhan_zhuang.db")
                        .addMigrations(MobileDatabase.MIGRATION_1_2)
                        .build(),
                ),
                nowEpochMillis = { System.currentTimeMillis() },
                appContext = context.applicationContext,
            ),
            scope = applicationScope,
            nowEpochMillis = { System.currentTimeMillis() },
        ).also { coordinator = it }
    }
}

class MobileDataLayerService : com.google.android.gms.wearable.WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val receiveMutex = Mutex()

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path !in setOf(PATH_COMMAND, PATH_STATE, PATH_ACK)) return
        scope.launch {
            val envelope = runCatching { SyncWireCodec.decodeMessage(event.data) }.getOrNull() ?: return@launch
            receiveMutex.withLock {
                runCatching { MobileSyncRuntime.get(applicationContext).receiveCommand(envelope, event.sourceNodeId) }
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path?.startsWith("$PATH_COMPLETED/") == true }
            .forEach { event ->
                val payload = runCatching { DataMapItem.fromDataItem(event.dataItem).dataMap.getByteArray(SyncWireCodec.DATA_KEY_ENVELOPE) }
                    .getOrNull() ?: return@forEach
                val uri = event.dataItem.uri
                scope.launch {
                    val envelope = runCatching { SyncWireCodec.decodeCompleted(payload) }.getOrNull() ?: return@launch
                    val acknowledged = receiveMutex.withLock {
                        runCatching { MobileSyncRuntime.get(applicationContext).receiveCompleted(envelope, uri.host) }.getOrDefault(false)
                    }
                    if (acknowledged) {
                        runCatching { Wearable.getDataClient(applicationContext).deleteDataItems(uri).await() }
                    }
                }
            }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

class MobileAndroidDataLayerTransport(context: Context) : SyncTransport {
    private val appContext = context.applicationContext

    override suspend fun sendMessage(envelope: SyncEnvelope): Boolean {
        val path = when (envelope.payload) {
            is app.zhanzhuang.timer.model.SyncPayload.Ack -> PATH_ACK
            is app.zhanzhuang.timer.model.SyncPayload.State,
            is app.zhanzhuang.timer.model.SyncPayload.Runtime,
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
            is app.zhanzhuang.timer.model.SyncPayload.Ack -> PATH_ACK
            is app.zhanzhuang.timer.model.SyncPayload.State,
            is app.zhanzhuang.timer.model.SyncPayload.Runtime,
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
