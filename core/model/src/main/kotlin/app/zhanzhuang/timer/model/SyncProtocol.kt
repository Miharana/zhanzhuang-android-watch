package app.zhanzhuang.timer.model

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val PROTOCOL_VERSION = 1
const val PATH_COMMAND = "/zhan-zhuang/v1/command"
const val PATH_STATE = "/zhan-zhuang/v1/state"
const val PATH_COMPLETED = "/zhan-zhuang/v1/completed"
const val PATH_ACK = "/zhan-zhuang/v1/ack"

@Serializable
data class SyncEnvelope(
    val protocolVersion: Int = PROTOCOL_VERSION,
    val eventId: String,
    val sessionId: String,
    val revision: Long,
    val sentAtEpochMillis: Long,
    val payload: SyncPayload,
)

@Serializable
sealed interface SyncPayload {
    @Serializable data class Start(val config: SessionConfig, val expiresAtEpochMillis: Long) : SyncPayload
    @Serializable data object QueryState : SyncPayload
    @Serializable data object Pause : SyncPayload
    @Serializable data object Resume : SyncPayload
    @Serializable data class Finish(val cancelled: Boolean) : SyncPayload
    @Serializable data class CancelIfUnowned(val ownerRevision: Long) : SyncPayload
    @Serializable data class State(val record: SessionRecord) : SyncPayload
    @Serializable data class Completed(val record: SessionRecord) : SyncPayload
    @Serializable data class Ack(val acceptedRevision: Long) : SyncPayload
}

/** The small wire surface shared by the Android Data Layer adapters and coordinator tests. */
interface SyncTransport {
    suspend fun sendMessage(envelope: SyncEnvelope): Boolean
    suspend fun putCompleted(envelope: SyncEnvelope): Boolean
}

object SyncWireCodec {
    const val DATA_KEY_ENVELOPE = "sync_envelope_gzip"

    private val json = Json {
        classDiscriminator = "type"
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    fun encodeMessage(envelope: SyncEnvelope): ByteArray = json.encodeToString(envelope).encodeToByteArray()

    fun decodeMessage(bytes: ByteArray): SyncEnvelope = json.decodeFromString(bytes.decodeToString())

    fun encodeCompleted(envelope: SyncEnvelope): ByteArray {
        require(envelope.payload is SyncPayload.Completed) { "Only completion records are DataItems" }
        val encoded = encodeMessage(envelope)
        return ByteArrayOutputStream().use { output ->
            GZIPOutputStream(output).use { it.write(encoded) }
            output.toByteArray()
        }
    }

    fun decodeCompleted(bytes: ByteArray): SyncEnvelope =
        GZIPInputStream(ByteArrayInputStream(bytes)).use { input ->
            decodeMessage(input.readBytes())
        }.also { require(it.payload is SyncPayload.Completed) { "Completion DataItem carried a non-completion payload" } }

    fun encodeCompletedForOutbox(envelope: SyncEnvelope): String =
        Base64.getEncoder().encodeToString(encodeCompleted(envelope))

    fun decodeCompletedFromOutbox(payload: String): SyncEnvelope =
        decodeCompleted(Base64.getDecoder().decode(payload))
}
