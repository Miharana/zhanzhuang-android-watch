package app.zhanzhuang.timer.wear.sync

import android.content.Context
import app.zhanzhuang.timer.model.PATH_ACK
import app.zhanzhuang.timer.model.PATH_COMMAND
import app.zhanzhuang.timer.model.PATH_COMPLETED
import app.zhanzhuang.timer.model.PATH_STATE
import app.zhanzhuang.timer.model.SyncEnvelope
import app.zhanzhuang.timer.model.SyncPayload
import app.zhanzhuang.timer.model.SyncTransport
import app.zhanzhuang.timer.model.SyncWireCodec
import com.google.android.gms.wearable.DataEvent
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

object WearSyncRuntime {
    @Volatile var coordinator: WearSyncCoordinator? = null
}

class WearDataLayerService : com.google.android.gms.wearable.WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path !in setOf(PATH_COMMAND, PATH_STATE, PATH_ACK)) return
        scope.launch {
            val envelope = runCatching { SyncWireCodec.decodeMessage(event.data) }.getOrNull() ?: return@launch
            WearSyncRuntime.coordinator?.receiveCommand(envelope)
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path?.startsWith(PATH_COMPLETED) == true }
            .forEach { event ->
                val payload = runCatching { DataMapItem.fromDataItem(event.dataItem).dataMap.getByteArray(SyncWireCodec.DATA_KEY_ENVELOPE) }
                    .getOrNull() ?: return@forEach
                scope.launch {
                    val envelope = runCatching { SyncWireCodec.decodeCompleted(payload) }.getOrNull() ?: return@launch
                    WearSyncRuntime.coordinator?.receiveCommand(envelope)
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
            is SyncPayload.State -> PATH_STATE
            else -> PATH_COMMAND
        }
        val nodes = Wearable.getNodeClient(appContext).connectedNodes.await()
        return nodes.map { node ->
            runCatching { Wearable.getMessageClient(appContext).sendMessage(node.id, path, SyncWireCodec.encodeMessage(envelope)).await() }.isSuccess
        }.any { it }
    }

    override suspend fun putCompleted(envelope: SyncEnvelope): Boolean {
        val request = PutDataMapRequest.create("$PATH_COMPLETED/${envelope.eventId}").apply {
            dataMap.putByteArray(SyncWireCodec.DATA_KEY_ENVELOPE, SyncWireCodec.encodeCompleted(envelope))
        }.asPutDataRequest().setUrgent()
        return runCatching { Wearable.getDataClient(appContext).putDataItem(request).await() }.isSuccess
    }
}
