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
/** Capability announced only by the phone app and queried by the Wear app. */
const val CAPABILITY_ZHAN_ZHUANG_PHONE = "zhan_zhang_phone_sync"

/** Capability announced only by the Wear app and queried by the phone app. */
const val CAPABILITY_ZHAN_ZHUANG_WEAR = "zhan_zhang_wear_sync"
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
    /** Ephemeral, service-authoritative Wear timer presentation; it never replaces Room history. */
    @Serializable data class Runtime(val runtime: SessionRuntime) : SyncPayload
    @Serializable data class Completed(val record: SessionRecord) : SyncPayload
    @Serializable data class Ack(val acceptedRevision: Long) : SyncPayload
}

@Serializable
data class SessionRuntime(
    val sessionId: String,
    val revision: Long,
    val status: SessionStatus,
    val activeDurationMs: Long,
    val remainingMs: Long,
    val reportedAtEpochMillis: Long,
) {
    init {
        require(revision >= 0)
        require(activeDurationMs >= 0)
        require(remainingMs >= 0)
        require(status in setOf(SessionStatus.STARTING, SessionStatus.RUNNING, SessionStatus.PAUSED, SessionStatus.COMPLETING))
    }
}

/** The small wire surface shared by the Android Data Layer adapters and coordinator tests. */
interface SyncTransport {
    suspend fun sendMessage(envelope: SyncEnvelope): Boolean
    suspend fun sendMessageTo(envelope: SyncEnvelope, nodeId: String): Boolean = sendMessage(envelope)
    suspend fun putCompleted(envelope: SyncEnvelope): Boolean
}

object SyncWireCodec {
    const val DATA_KEY_ENVELOPE = "sync_envelope_gzip"
    const val MAX_MESSAGE_BYTES = 32 * 1024
    const val MAX_COMPLETED_COMPRESSED_BYTES = 64 * 1024
    const val MAX_COMPLETED_DECOMPRESSED_BYTES = 512 * 1024
    const val MAX_COMPLETED_SAMPLES = 2_048

    private val json = Json {
        classDiscriminator = "type"
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    fun encodeMessage(envelope: SyncEnvelope): ByteArray = json.encodeToString(envelope).encodeToByteArray().also {
        require(it.size <= MAX_MESSAGE_BYTES) { "Sync message exceeds $MAX_MESSAGE_BYTES bytes" }
    }

    fun decodeMessage(bytes: ByteArray): SyncEnvelope {
        require(bytes.size <= MAX_MESSAGE_BYTES) { "Sync message exceeds $MAX_MESSAGE_BYTES bytes" }
        return json.decodeFromString(bytes.decodeToString())
    }

    fun encodeCompleted(envelope: SyncEnvelope): ByteArray {
        require(envelope.payload is SyncPayload.Completed) { "Only completion records are DataItems" }
        val completed = envelope.payload as SyncPayload.Completed
        val encoded = json.encodeToString(
            envelope.copy(payload = SyncPayload.Completed(completed.record.copy(
                heartRateSamples = SyncHeartRateSampler.forTransfer(completed.record.heartRateSamples),
            ))),
        ).encodeToByteArray()
        require(encoded.size <= MAX_COMPLETED_DECOMPRESSED_BYTES) { "Completion JSON exceeds $MAX_COMPLETED_DECOMPRESSED_BYTES bytes" }
        return ByteArrayOutputStream().use { output ->
            GZIPOutputStream(output).use { it.write(encoded) }
            output.toByteArray().also {
                require(it.size <= MAX_COMPLETED_COMPRESSED_BYTES) { "Completion payload exceeds $MAX_COMPLETED_COMPRESSED_BYTES bytes" }
            }
        }
    }

    fun decodeCompleted(bytes: ByteArray): SyncEnvelope {
        require(bytes.size <= MAX_COMPLETED_COMPRESSED_BYTES) { "Completion payload exceeds $MAX_COMPLETED_COMPRESSED_BYTES bytes" }
        val decoded = try {
            GZIPInputStream(ByteArrayInputStream(bytes)).use { input ->
                val output = ByteArrayOutputStream()
                val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(chunk)
                    if (read == -1) break
                    require(output.size() + read <= MAX_COMPLETED_DECOMPRESSED_BYTES) { "Completion payload expands beyond $MAX_COMPLETED_DECOMPRESSED_BYTES bytes" }
                    output.write(chunk, 0, read)
                }
                output.toByteArray()
            }
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: Exception) {
            throw IllegalArgumentException("Invalid compressed completion payload", error)
        }
        return try {
            json.decodeFromString<SyncEnvelope>(decoded.decodeToString()).also {
                require(it.payload is SyncPayload.Completed) { "Completion DataItem carried a non-completion payload" }
            }
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: Exception) {
            throw IllegalArgumentException("Invalid completion payload", error)
        }
    }

    fun encodeCompletedForOutbox(envelope: SyncEnvelope): String =
        Base64.getEncoder().encodeToString(encodeCompleted(envelope))

    fun decodeCompletedFromOutbox(payload: String): SyncEnvelope =
        decodeCompleted(Base64.getDecoder().decode(payload))
}

/** Sync-only representation; Wear Room retains every original measurement. */
object SyncHeartRateSampler {
    fun forTransfer(samples: List<HeartRateSample>, maximum: Int = SyncWireCodec.MAX_COMPLETED_SAMPLES): List<HeartRateSample> {
        require(maximum >= 2)
        val ordered = samples.sortedBy(HeartRateSample::epochMillis).distinctBy(HeartRateSample::epochMillis)
        if (ordered.size <= maximum) return ordered
        return List(maximum) { index ->
            ordered[(index.toLong() * (ordered.size - 1) / (maximum - 1)).toInt()]
        }
    }
}
