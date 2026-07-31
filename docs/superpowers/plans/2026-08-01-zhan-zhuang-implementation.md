# 站桩 · Zhan Zhuang Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build, verify, sign, and prepare for Google Play internal release a bilingual Android phone + Wear OS app that reliably times 15–180 minute standing-meditation sessions, provides fixed haptic interval cues, records watch heart rate, persists local history, and writes eligible records to Health Connect.

**Architecture:** Two application modules (`:mobile`, `:wear`) share pure model and domain modules. The device that accepts a session becomes its owner; local persistence and an idempotent Data Layer outbox prevent loss and duplication. Wear Health Services owns watch exercise capture, while the phone is the only Health Connect writer.

**Tech Stack:** Kotlin 2.1.10, Gradle 8.13, AGP 8.13.1, JDK 17, API 36, Compose BOM 2026.06.00, Wear Compose 1.6.2, Room 2.8.2, WorkManager 2.11.0, Health Connect 1.1.0, Health Services 1.0.0, Play Services Wearable 20.0.1, JUnit, coroutines-test, fastlane supply.

## Global Constraints

- Name `站桩 · Zhan Zhuang`; application ID `app.zhanzhuang.timer`.
- Mobile min/target/compile SDK `28/36/36`, version `0.1.0` / `1000001`.
- Wear min/target/compile SDK `33/36/36`, version `0.1.0` / `2000001`, standalone `true`.
- Duration: 15–180 minutes in 5-minute steps; shortcuts 15/30/45/60/90/120/180; default 30.
- Interval: exactly 5/10/15/20/30 minutes; default 10; haptic only.
- One active session; elapsed duration excludes pauses and uses a monotonic clock.
- No account, backend, cloud, ads, IAP, analytics, location, calorie estimate, or medical claim.
- Locales: default English and `zh-rCN`.
- Palette: paper `#FBF8F1`, soil `#5B4F3B`, gold `#C6A867`, deep gold `#A9863F`, dark soil `#15110B`, sparkle `#FFF4D6`.
- Sparkle runs once for 280–420 ms at start/completion; never ambient, battery-saver, or reduced-motion.
- Health permissions are contextual and optional; denial keeps duration-only sessions usable.
- Signing material and service-account JSON remain outside Git; initial Play mutation is `internal` only.
- Upload key path is `/Users/pema/.config/zhanzhuang/signing/zhanzhuang-upload.jks`; Play service-account path is `/Users/pema/.config/zhanzhuang/google-play/fastlane-supply.json`.
- Local toolchain: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`; SDK `/opt/homebrew/share/android-commandlinetools`.

---

## File Map

- `core/model` — serializable session and sync contracts.
- `core/domain` — timing, reminder, heart-rate summary, merge rules.
- `mobile` — Room source of truth, phone timer, Health Connect, Data Layer, Material 3 UI.
- `wear` — Room recovery/outbox, Health Services, foreground session, haptics, Data Layer, Wear Compose UI.
- `fastlane`, `scripts`, `docs/privacy-policy.*`, `docs/qa`, `docs/release` — reproducible andship gates and evidence.

### Task 1: Bootstrap Gradle and core contracts

**Files:**
- Create: `.gitignore`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`
- Create: `core/model/build.gradle.kts`, `core/model/src/main/kotlin/app/zhanzhuang/timer/model/SessionModels.kt`
- Create: `core/domain/build.gradle.kts`, `core/domain/src/main/kotlin/app/zhanzhuang/timer/domain/SessionClock.kt`
- Create: `core/domain/src/main/kotlin/app/zhanzhuang/timer/domain/HeartRateSummary.kt`
- Test: `core/domain/src/test/kotlin/app/zhanzhuang/timer/domain/SessionClockTest.kt`
- Test: `core/domain/src/test/kotlin/app/zhanzhuang/timer/domain/HeartRateSummaryTest.kt`

**Interfaces:** Produces `SessionConfig`, `SessionRecord`, `HeartRateSample`, enums, `SessionClock.snapshot()`, and `HeartRateSummary.from()`.

- [ ] **Step 1: Write failing boundary tests**

```kotlin
@Test fun invalidConfigFails() {
    assertFailsWith<IllegalArgumentException> { SessionConfig(14, 10) }
    assertFailsWith<IllegalArgumentException> { SessionConfig(30, 7) }
}

@Test fun pauseIsExcludedAndLateWakeDoesNotBackfill() {
    val clock = SessionClock(SessionConfig(30, 10), 1_000)
    clock.pause(301_000); clock.resume(361_000)
    val snapshot = clock.snapshot(1_261_000)
    assertEquals(1_200_000, snapshot.activeElapsedMs)
    assertEquals(2, snapshot.dueReminderIndex)
}
```

- [ ] **Step 2: Create build files and verify RED**

Use the versions in the plan header; initially include only `:core:model` and `:core:domain`, then add each application module in the task that creates it. Generate the wrapper with the known local Gradle wrapper:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  /Users/pema/Documents/projects/apps/ILRTracker/android/gradlew -p "$PWD" wrapper --gradle-version 8.13
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew :core:domain:test
```

Expected: FAIL because production types do not exist.

- [ ] **Step 3: Implement exact shared shapes**

```kotlin
@Serializable
data class SessionConfig(val durationMinutes: Int = 30, val intervalMinutes: Int = 10) {
    init {
        require(durationMinutes in 15..180 && durationMinutes % 5 == 0)
        require(intervalMinutes in setOf(5, 10, 15, 20, 30))
    }
}

@Serializable enum class SessionStatus { IDLE, STARTING, RUNNING, PAUSED, COMPLETING, COMPLETED, CANCELLED, INTERRUPTED }
@Serializable enum class SessionOwner { MOBILE, WEAR }
@Serializable enum class SampleAccuracy { LOW, MEDIUM, HIGH }
@Serializable enum class SyncState { LOCAL_ONLY, PENDING, SYNCED, FAILED }
@Serializable data class HeartRateSample(val epochMillis: Long, val bpm: Double, val accuracy: SampleAccuracy)
```

`SessionRecord` includes UUID, revision, config, status, owner, start/end epoch milliseconds, active/paused duration, heart-rate samples, and Health Connect state. `ClockSnapshot` includes active elapsed, remaining, at most one due reminder index, and completion flag.

- [ ] **Step 4: Verify GREEN and commit**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew :core:domain:test
git add .gitignore settings.gradle.kts build.gradle.kts gradle.properties gradle core
git commit -m "build: bootstrap shared session core"
```

Expected: all core tests pass; no `local.properties`, key, or password is staged.

### Task 2: Mobile persistence and phone-owned runtime

**Files:**
- Create: `mobile/build.gradle.kts`, `mobile/src/main/AndroidManifest.xml`
- Modify: `settings.gradle.kts` to include `:mobile`
- Create: `mobile/src/main/kotlin/app/zhanzhuang/timer/mobile/data/{SessionEntity,HeartRateEntity,SessionDao,MobileDatabase,MobileSessionRepository}.kt`
- Create: `mobile/src/main/kotlin/app/zhanzhuang/timer/mobile/session/{MobileSessionController,MobileSessionService}.kt`
- Test: `mobile/src/test/kotlin/app/zhanzhuang/timer/mobile/data/MobileSessionRepositoryTest.kt`
- Test: `mobile/src/test/kotlin/app/zhanzhuang/timer/mobile/session/MobileSessionControllerTest.kt`

**Interfaces:** Consumes shared models/clock; produces `observeSessions()`, revision-aware `upsert()`, `activeSession()`, and start/pause/resume/finish commands.

- [ ] **Step 1: Write failing revision and completion tests**

```kotlin
@Test fun staleRevisionCannotReplaceTerminalRecord() = runTest {
    repository.upsert(record("s1", 2, SessionStatus.COMPLETED))
    repository.upsert(record("s1", 1, SessionStatus.RUNNING))
    assertEquals(SessionStatus.COMPLETED, repository.get("s1")!!.status)
}

@Test fun phoneCompletesAndVibratesOnce() = runTest {
    controller.start(SessionConfig(15, 5)); elapsed.advanceBy(900_000)
    controller.tick(); controller.tick()
    assertEquals(1, haptics.completionCount)
}
```

The test file defines `record(id, revision, status)`, `FakeElapsedClock`, `FakeWallClock`, `FakeHaptics`, and an in-memory repository; these fakes implement the same interfaces injected into the controller.

- [ ] **Step 2: Verify RED, implement, and verify GREEN**

Room uses normalized `sessions` and `heart_rate_samples` tables. Repository upsert is transactional and accepts only a higher revision. `MobileSessionService` is user-started, posts an immediate ongoing notification, owns phone vibration, and persists at each transition plus every 60 seconds.

```kotlin
@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY startedAtEpochMillis DESC")
    fun observeAll(): Flow<List<SessionEntity>>
    @Query("SELECT * FROM sessions WHERE id=:id")
    suspend fun get(id: String): SessionEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringConflict(entity: SessionEntity): Long
    @Transaction suspend fun upsertIfNewer(entity: SessionEntity, samples: List<HeartRateEntity>)
}

interface MobileSessionController {
    val state: StateFlow<MobileSessionUiState>
    suspend fun start(config: SessionConfig)
    suspend fun pause()
    suspend fun resume()
    suspend fun finish(cancelled: Boolean = false)
    suspend fun tick()
}
```

The mobile foreground service declares `specialUse` and property `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE=standing_meditation_timer`; it does not claim media/location behavior.

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew :mobile:testDebugUnitTest :mobile:lintDebug
git add mobile && git commit -m "feat: add mobile session persistence and timing"
```

Expected: tests pass; merged manifest has no location or internet permission.

### Task 3: Optional idempotent Health Connect export

**Files:**
- Create: `mobile/src/main/kotlin/app/zhanzhuang/timer/mobile/health/{HealthConnectGateway,AndroidHealthConnectGateway,HealthPermissionState,HealthSyncWorker}.kt`
- Modify: `mobile/src/main/AndroidManifest.xml`
- Test: `mobile/src/test/kotlin/app/zhanzhuang/timer/mobile/health/HealthSyncWorkerTest.kt`

**Interfaces:** Produces availability, write-only permission set, and `write(SessionRecord): HealthWriteResult`.

- [ ] **Step 1: Write failing fallback tests**

```kotlin
@Test fun mindfulnessPreferredAndHeartRateAssociated() = runTest {
    worker(FakeHealthGateway(mindfulnessSupported = true)).sync(completedWithSamples())
    assertEquals(listOf(WriteKind.MINDFULNESS, WriteKind.HEART_RATE), gateway.writes)
}

@Test fun shortSessionStaysLocal() = runTest {
    worker(gateway).sync(completed(activeDurationMillis = 59_999))
    assertTrue(gateway.writes.isEmpty())
}
```

The test file owns `FakeHealthGateway`, `completedWithSamples()`, `completed(activeDurationMillis)`, and the gateway write log; no fixture is imported from another task.

- [ ] **Step 2: Implement feature/permission handling**

```kotlin
sealed interface HealthWriteResult {
    data class Success(val sessionWritten: Boolean, val heartRateWritten: Boolean) : HealthWriteResult
    data class PermissionMissing(val permissions: Set<String>) : HealthWriteResult
    data class Unavailable(val reason: String) : HealthWriteResult
    data class Retryable(val reason: String) : HealthWriteResult
    data class PermanentFailure(val reason: String) : HealthWriteResult
}

interface HealthConnectGateway {
    suspend fun availability(): HealthAvailability
    fun requiredWritePermissions(includeHeartRate: Boolean): Set<String>
    suspend fun write(record: SessionRecord): HealthWriteResult
}
```

Prefer `MindfulnessSessionRecord.MEDITATION`; feature-check and fall back to other-workout `ExerciseSessionRecord`. Use client record IDs `zz:<sessionId>:session` and `zz:<sessionId>:heart-rate`, with revision as client version. Filter samples to interval, BPM 1–300, medium/high accuracy. Request only write permissions. Unique WorkManager work is `health-sync:<sessionId>` with exponential retry only for transient failure.

- [ ] **Step 3: Verify and commit**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew :mobile:testDebugUnitTest :mobile:lintDebug
git add mobile && git commit -m "feat: add optional Health Connect export"
```

Expected: feature fallback, permission denial, partial write, and idempotency tests pass.

### Task 4: Wear persistence, Health Services, and haptics

**Files:**
- Create: `wear/build.gradle.kts`, `wear/src/main/AndroidManifest.xml`
- Modify: `settings.gradle.kts` to include `:wear`
- Create: `wear/src/main/kotlin/app/zhanzhuang/timer/wear/data/{WearDatabase,WearSessionRepository,OutboxEntity}.kt`
- Create: `wear/src/main/kotlin/app/zhanzhuang/timer/wear/health/{WearHealthClient,AndroidWearHealthClient}.kt`
- Create: `wear/src/main/kotlin/app/zhanzhuang/timer/wear/session/HapticCuePlayer.kt`
- Test: `wear/src/test/kotlin/app/zhanzhuang/timer/wear/health/WearHealthClientTest.kt`
- Test: `wear/src/test/kotlin/app/zhanzhuang/timer/wear/session/HapticCuePlayerTest.kt`

**Interfaces:** Produces exercise start/pause/resume/end, heart-rate updates, durable session/outbox, and `play(Cue)`.

- [ ] **Step 1: Write failing config and pattern tests**

```kotlin
@Test fun meditationRequestsHeartRateOnly() {
    val config = createExerciseConfig()
    assertEquals(ExerciseType.MEDITATION, config.exerciseType)
    assertEquals(setOf(DataType.HEART_RATE_BPM), config.dataTypes)
}

@Test fun completionCueIsDistinct() {
    assertEquals(longArrayOf(0, 220, 100, 65, 90, 65).toList(), pattern(Cue.COMPLETE).toList())
}
```

The test file defines a fake capability response and a pure `createExerciseConfig()` factory so it runs without a watch.

- [ ] **Step 2: Implement manifest, adapter, persistence, and haptics**

Manifest declares watch feature, standalone true, health foreground service, notification, legacy `BODY_SENSORS*` capped at SDK 35, and API 36+ `READ_HEART_RATE`/`READ_HEALTH_DATA_IN_BACKGROUND`; no location. Existing third-party exercise produces a duration-only fallback.

```kotlin
interface WearHealthClient {
    val updates: Flow<WearHealthUpdate>
    suspend fun start(): WearHealthStartResult
    suspend fun pause()
    suspend fun resume()
    suspend fun end()
}

enum class Cue { START, INTERVAL, COMPLETE, PAUSE, RESUME }
fun pattern(cue: Cue): LongArray = when (cue) {
    Cue.START -> longArrayOf(0, 70)
    Cue.INTERVAL -> longArrayOf(0, 65, 90, 65)
    Cue.COMPLETE -> longArrayOf(0, 220, 100, 65, 90, 65)
    Cue.PAUSE -> longArrayOf(0, 45)
    Cue.RESUME -> longArrayOf(0, 55)
}
```

- [ ] **Step 3: Verify and commit**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew :wear:testDebugUnitTest :wear:lintDebug
git add wear && git commit -m "feat: add Wear health and haptic foundation"
```

Expected: tests pass; merged Wear manifest has no GPS/location.

### Task 5: Recoverable Wear foreground session

**Files:**
- Create: `wear/src/main/kotlin/app/zhanzhuang/timer/wear/session/{WearSessionService,WearSessionController,SessionSnapshotStore,SessionRecoveryReceiver,OngoingSessionNotification}.kt`
- Modify: `wear/src/main/AndroidManifest.xml`
- Test: `wear/src/test/kotlin/app/zhanzhuang/timer/wear/session/WearSessionControllerTest.kt`

**Interfaces:** Consumes Wear health/repository/clock/haptics; produces `StateFlow<WearSessionUiState>` and session commands.

- [ ] **Step 1: Write failing lifecycle/recovery tests**

```kotlin
@Test fun completionPersistsBeforeExternalEnd() = runTest {
    controller.start(SessionConfig(15, 5), "s1"); elapsed.advanceBy(900_000); controller.tick()
    assertEquals(listOf("persist:COMPLETING", "health:end", "persist:COMPLETED", "outbox:s1"), events)
}

@Test fun changedBootMarksInterruptedWithoutInventingSamples() = runTest {
    snapshots.save(snapshot(bootId = "old", sampleCount = 2)); controller.recover(currentBootId = "new")
    assertEquals(SessionStatus.INTERRUPTED, repository.latest()!!.status)
    assertEquals(2, repository.latest()!!.samples.size)
}
```

The test file defines event-recording fake health/repository/snapshot/haptic dependencies and an adjustable monotonic clock.

- [ ] **Step 2: Implement service and UI state**

```kotlin
data class WearSessionUiState(
    val record: SessionRecord?,
    val remainingMs: Long,
    val currentHeartRateBpm: Double?,
    val nextReminderAtActiveMs: Long?,
    val heartRateUnavailableReason: String? = null,
)

interface WearSessionController {
    val state: StateFlow<WearSessionUiState>
    suspend fun start(config: SessionConfig, sessionId: String = UUID.randomUUID().toString())
    suspend fun pause()
    suspend fun resume()
    suspend fun finish(cancelled: Boolean = false)
    suspend fun tick()
    suspend fun recover(currentBootId: String)
}
```

Service type is `health`, starts from explicit user action, posts notification immediately, and attaches Ongoing Activity. Persist before external calls; batch samples every 30 seconds or 20 samples; snapshot every transition and 60 seconds. A delayed wake emits at most one interval cue.

- [ ] **Step 3: Verify and commit**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew :wear:testDebugUnitTest :wear:lintDebug
git add wear && git commit -m "feat: add recoverable Wear training service"
```

Expected: lifecycle, process recovery, reboot interruption, pause, and reminder tests pass.

### Task 6: Versioned Data Layer and durable outbox

**Files:**
- Create: `core/model/src/main/kotlin/app/zhanzhuang/timer/model/SyncProtocol.kt`
- Create: `core/domain/src/main/kotlin/app/zhanzhuang/timer/domain/SyncConflictResolver.kt`
- Create: `mobile/src/main/kotlin/app/zhanzhuang/timer/mobile/sync/{MobileDataLayerService,MobileSyncCoordinator}.kt`
- Create: `wear/src/main/kotlin/app/zhanzhuang/timer/wear/sync/{WearDataLayerService,WearSyncCoordinator}.kt`
- Modify: both manifests with listener services/capabilities
- Test: conflict/timeout/outbox tests in all three modules

**Interfaces:** Produces protocol v1 paths/envelopes, 5+5 second mobile fallback, durable completion ACK.

- [ ] **Step 1: Write failing ordering and timeout tests**

```kotlin
@Test fun terminalRevisionWins() {
    assertEquals(completedV8, resolver.merge(completedV8, runningV7))
}

@Test fun fallbackOccursAfterAckThenQueryTimeout() = runTest {
    coordinator.startFromMobile(SessionConfig()); advanceTimeBy(5_000)
    assertEquals(listOf(Command.START, Command.QUERY_STATE), transport.sent)
    advanceTimeBy(5_000); assertEquals(SessionOwner.MOBILE, controller.owner())
}
```

Each coordinator test defines its own fake transport, repository, scheduler, controller, and record factory. The conflict resolver test creates explicit `SessionRecord` values rather than depending on production database fixtures.

- [ ] **Step 2: Implement protocol and transport**

```kotlin
const val PROTOCOL_VERSION = 1
const val PATH_COMMAND = "/zhan-zhuang/v1/command"
const val PATH_STATE = "/zhan-zhuang/v1/state"
const val PATH_COMPLETED = "/zhan-zhuang/v1/completed"
const val PATH_ACK = "/zhan-zhuang/v1/ack"

@Serializable data class SyncEnvelope(
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
    @Serializable data class Completed(val record: SessionRecord) : SyncPayload
    @Serializable data class Ack(val acceptedRevision: Long) : SyncPayload
}
```

`SyncPayload` includes Start with expiry, QueryState, Pause, Resume, Finish, CancelIfUnowned, Completed, and Ack. MessageClient carries commands/status; gzip JSON DataItems carry durable completed records. Unknown version, expired start, duplicate event, and lower revision are rejected safely. Outbox clears only after matching revision ACK.

- [ ] **Step 3: Verify and commit**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew :core:domain:test :mobile:testDebugUnitTest :wear:testDebugUnitTest
git add core mobile wear && git commit -m "feat: add idempotent phone watch synchronization"
```

Expected: ordering, duplicate, timeout, disconnect, resend, and ACK tests pass.

### Task 7: Urticad-derived Wear UI

**Files:**
- Create: `wear/src/main/kotlin/app/zhanzhuang/timer/wear/ui/theme/{Color,Theme}.kt`
- Create: `wear/src/main/kotlin/app/zhanzhuang/timer/wear/ui/components/GoldSparkle.kt`
- Create: `wear/src/main/kotlin/app/zhanzhuang/timer/wear/ui/screens/{SetupScreen,ActiveSessionScreen,CompletionScreen,PermissionScreen}.kt`
- Create: `wear/src/main/kotlin/app/zhanzhuang/timer/wear/MainActivity.kt`
- Test: `wear/src/androidTest/kotlin/app/zhanzhuang/timer/wear/ui/WearScreensTest.kt`

**Interfaces:** Consumes Wear UI state/controller; produces round-screen, rotary, ambient, permission flows.

- [ ] **Step 1: Write failing Compose semantics tests**

```kotlin
@Test fun setupShowsDefaults() {
    rule.setContent { SetupScreen(SessionConfig(), {}, {}) }
    rule.onNodeWithText("30 min").assertIsDisplayed()
    rule.onNodeWithText("Every 10 min").assertIsDisplayed()
}

@Test fun endRequiresConfirmation() {
    rule.setContent { ActiveSessionScreen(runningState(), {}, {}, {}) }
    rule.onNodeWithContentDescription("End session").performClick()
    rule.onNodeWithText("End this session?").assertIsDisplayed()
}
```

The UI test file defines `runningState()` and no-op callbacks locally, then runs the same assertions under English and `zh-CN` locales.

- [ ] **Step 2: Implement palette and screens**

```kotlin
val DeepSoil = Color(0xFF15110B)
val RaisedSoil = Color(0xFF241C12)
val WarmPaper = Color(0xFFFBF8F1)
val ReflectiveGold = Color(0xFFC6A867)
val DeepGold = Color(0xFFA9863F)
val Sparkle = Color(0xFFFFF4D6)
```

Use Wear Material 3, rotary-aware duration control, 48 dp targets, edge-safe padding, tabular timer digits, and end confirmation. Sparkle accepts `enabled`; disable in ambient, battery saver, and zero animator-scale. Ambient UI updates only at minute boundaries.

- [ ] **Step 3: Verify and commit**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew :wear:testDebugUnitTest :wear:lintDebug :wear:assembleDebug
git add wear && git commit -m "feat: build earth and gold Wear experience"
```

Expected: no clipping on 41 mm/45 mm round profiles; semantics and build pass.

### Task 8: Mobile training, history, detail, and settings UI

**Files:**
- Create: `mobile/src/main/kotlin/app/zhanzhuang/timer/mobile/ui/theme/{Color,Theme}.kt`
- Create: `mobile/src/main/kotlin/app/zhanzhuang/timer/mobile/ui/components/GoldSparkle.kt`
- Create: `mobile/src/main/kotlin/app/zhanzhuang/timer/mobile/ui/screens/{TrainingScreen,HistoryScreen,SessionDetailScreen,SettingsScreen}.kt`
- Create: `mobile/src/main/kotlin/app/zhanzhuang/timer/mobile/ui/{MainViewModel,HistoryStats}.kt`
- Create: `mobile/src/main/kotlin/app/zhanzhuang/timer/mobile/MainActivity.kt`
- Test: `mobile/src/androidTest/kotlin/app/zhanzhuang/timer/mobile/ui/MobileScreensTest.kt`
- Test: `mobile/src/test/kotlin/app/zhanzhuang/timer/mobile/ui/HistoryStatsTest.kt`

**Interfaces:** Consumes repository/controller/watch/Health states; produces training/history/settings navigation.

- [ ] **Step 1: Write failing statistics and disconnected-watch tests**

```kotlin
@Test fun totalsUseActiveDurationAndLocalZone() {
    val stats = HistoryStats.calculate(records(), instant("2026-08-01T12:00:00Z"), ZoneId.of("Europe/London"))
    assertEquals(30.minutes, stats.today); assertEquals(90.minutes, stats.week); assertEquals(180.minutes, stats.month)
}

@Test fun disconnectedWatchExplainsMissingHeartRate() {
    rule.setContent { TrainingScreen(state(watchConnected = false), {}) }
    rule.onNodeWithText("This session won't record heart rate").assertIsDisplayed()
}
```

The statistics test defines fixed records and `instant(String)` locally. The UI test defines `state(watchConnected)` and callbacks locally.

- [ ] **Step 2: Implement mobile theme and flows**

```kotlin
val Paper = Color(0xFFFBF8F1); val Surface = Color(0xFFFFFDF8)
val SunkenEarth = Color(0xFFEBE3D2); val SoilInk = Color(0xFF5B4F3B)
val SoftSoil = Color(0xFF998A72); val Gold = Color(0xFFC6A867)
val DeepGold = Color(0xFFA9863F); val PaleGold = Color(0xFFF0E4C4)
```

Training exposes all duration shortcuts/steps and fixed intervals. History sorts descending and totals active time. Detail draws a custom Canvas heart-rate polyline, avoiding a chart SDK. Settings exposes defaults, Health status/permission, retry, privacy, and licenses. Permission launches only from explicit user action.

- [ ] **Step 3: Verify and commit**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew :mobile:testDebugUnitTest :mobile:lintDebug :mobile:assembleDebug
git add mobile && git commit -m "feat: build mobile training and history experience"
```

Expected: statistics, permissions, large-font semantics, and debug build pass.

### Task 9: Localization, accessibility, and identity

**Files:**
- Create: English and `values-zh-rCN/strings.xml` in both apps
- Create: adaptive, round, and monochrome icon resources in both apps
- Create: `docs/design-system.md`
- Test: resource parity tests in mobile/wear unit tests

**Interfaces:** Produces complete bilingual copy and Play-ready visual identity.

- [ ] **Step 1: Write failing parity/claim tests**

```kotlin
@Test fun localeKeysMatch() = assertEquals(keys("values"), keys("values-zh-rCN"))
@Test fun forbiddenClaimsAreAbsent() {
    listOf("治愈", "治疗", "诊断", "burn calories", "weight loss", "premium", "advertisement")
        .forEach { assertFalse(allCopy.lowercase().contains(it)) }
}
```

- [ ] **Step 2: Implement resources and icon**

Use “站桩”/“Zhan Zhuang” and describe heart rate as informational. Icon: centered gold standing pillar/figure inside a dark-soil circle with one restrained four-point sparkle, no text, plus monochrome layer. `docs/design-system.md` records color roles/proportions, sparkle suppression, 6/10/16/24 dp radii, 48 dp touch minimum, and round safe zones.

Minimum resource contract in both locales:

```xml
<resources>
    <string name="app_name">Zhan Zhuang</string>
    <string name="start_session">Start session</string>
    <string name="pause_session">Pause</string>
    <string name="resume_session">Resume</string>
    <string name="end_session">End session</string>
    <string name="heart_rate_unavailable">Heart rate not recorded</string>
    <string name="health_connect">Health Connect</string>
    <string name="local_only_privacy">Your data stays on your devices unless you choose Health Connect export.</string>
</resources>
```

The Chinese file contains the same names with human Chinese translations; additional screen strings follow the same parity rule.

- [ ] **Step 3: Verify and commit**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew test lintDebug assembleDebug
git add mobile wear docs/design-system.md && git commit -m "feat: complete bilingual app identity"
```

Expected: locale parity, icon contracts, tests, lint, and debug builds pass.

### Task 10: Integration and Pixel Watch evidence gates

**Files:**
- Create: `scripts/run_android_checks.sh`, `scripts/collect_device_evidence.sh`
- Create: `docs/qa/wear-device-matrix.md`, `docs/qa/pixel-watch-4-results.md`
- Create: end-to-end instrumentation tests in both apps
- Modify: module test configuration

**Interfaces:** Produces repeatable clean verification and secret-safe physical QA evidence.

- [ ] **Step 1: Write a failing release-contract gate**

The script must run clean tests/lint/debug builds, inspect both manifests/artifacts, and reject wrong ID/version/SDK/standalone/permissions/locales. Required core:

```bash
./gradlew clean test lintDebug assembleDebug
"$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer" manifest application-id mobile/build/outputs/apk/debug/mobile-debug.apk
"$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer" manifest application-id wear/build/outputs/apk/debug/wear-debug.apk
```

Expected ID for both debug apps: `app.zhanzhuang.timer.debug`.

- [ ] **Step 2: Implement tests and evidence collector**

Collector records anonymized labels (`connected-device-1`), OS/API, installed version, permissions, service/notification state, and timestamps. It never prints raw ADB serials or health samples.

- [ ] **Step 3: Run emulator and Pixel Watch 4 matrix**

Use Wear OS 4/5/6 round profiles and Android 9/14/16 where images exist. Physical Watch tests: 15, 30, and 180-minute screen-off sessions; disconnect/reconnect; denied heart rate/Health Connect; process death; reboot; battery saver. Record timer error, expected/observed haptic count, duplicate count, and sync state. Never mark physical rows passed from emulator evidence.

- [ ] **Step 4: Commit gates/evidence**

```bash
git add scripts docs/qa mobile wear && git commit -m "test: add phone and Wear release evidence gates"
```

Expected: automated gate passes; physical rows truthfully reflect executed evidence.

### Task 11: Signing, privacy, fastlane, metadata, and assets

**Files:**
- Create: `Gemfile`, `fastlane/{Appfile,Fastfile}`
- Create: `fastlane/metadata/android/{en-US,zh-CN}` descriptions/changelogs/images
- Create: `docs/privacy-policy.md`, `docs/privacy-policy.html`, `docs/release/play-console-checklist.md`
- Create: `scripts/verify_release.sh`
- Modify: both module Gradle files for env-only release signing

**Interfaces:** Produces two signed AABs, metadata lane, mobile internal lane, and truthful Play declarations.

- [ ] **Step 1: Add a fail-closed release gate**

```bash
: "${ZHANZHUANG_KEYSTORE_PATH:?missing ZHANZHUANG_KEYSTORE_PATH}"
: "${ZHANZHUANG_KEY_ALIAS:?missing ZHANZHUANG_KEY_ALIAS}"
: "${ZHANZHUANG_STORE_PASSWORD:?missing ZHANZHUANG_STORE_PASSWORD}"
: "${ZHANZHUANG_KEY_PASSWORD:?missing ZHANZHUANG_KEY_PASSWORD}"
test ! -e fastlane/service-account.json
./gradlew clean test lintRelease :mobile:bundleRelease :wear:bundleRelease
"$JAVA_HOME/bin/jarsigner" -verify -strict mobile/build/outputs/bundle/release/mobile-release.aab
"$JAVA_HOME/bin/jarsigner" -verify -strict wear/build/outputs/bundle/release/wear-release.aab
```

Expected without external signing values: fail on the first missing variable.

- [ ] **Step 2: Implement signing and fastlane**

Both modules require the same external upload key; no debug-signing fallback. `publish_metadata` uses `SUPPLY_JSON_KEY`, package `app.zhanzhuang.timer`, metadata path, and skips binaries. `upload_mobile_internal` uploads only the mobile AAB to `internal`, `release_status: completed`, and skips metadata/assets. Wear upload remains disabled until Play Console/API provides the exact dedicated-track identifier; do not guess it.

```kotlin
val releaseKeyPath = providers.environmentVariable("ZHANZHUANG_KEYSTORE_PATH")
android.signingConfigs.create("release") {
    storeFile = releaseKeyPath.orNull?.let(::file)
    storePassword = providers.environmentVariable("ZHANZHUANG_STORE_PASSWORD").orNull
    keyAlias = providers.environmentVariable("ZHANZHUANG_KEY_ALIAS").orNull
    keyPassword = providers.environmentVariable("ZHANZHUANG_KEY_PASSWORD").orNull
}
android.buildTypes.named("release") {
    signingConfig = android.signingConfigs.getByName("release")
    isMinifyEnabled = true
}
```

```ruby
default_platform(:android)
platform :android do
  lane :publish_metadata do
    upload_to_play_store(
      json_key: ENV.fetch("SUPPLY_JSON_KEY"), package_name: "app.zhanzhuang.timer",
      metadata_path: "fastlane/metadata/android", track: "internal",
      skip_upload_aab: true, skip_upload_apk: true
    )
  end
  lane :upload_mobile_internal do
    upload_to_play_store(
      json_key: ENV.fetch("SUPPLY_JSON_KEY"), package_name: "app.zhanzhuang.timer",
      aab: "mobile/build/outputs/bundle/release/mobile-release.aab",
      track: "internal", release_status: "completed",
      skip_upload_metadata: true, skip_upload_images: true, skip_upload_screenshots: true
    )
  end
end
```

- [ ] **Step 3: Create truthful listing/assets**

English short description: `Quiet Zhan Zhuang timers, haptic cues and optional heart-rate history.`

Chinese short description: `安静的站桩计时、间隔震动提醒与可选心率记录。`

Full copy states local-only storage, optional Health Connect, no account/ads/cloud, 15 minutes–3 hours, and no medical advice. Prepare phone/Wear screenshots and 1024×500 feature graphic for: duration, quiet cues, live heart rate, local history, Health Connect/privacy.

- [ ] **Step 4: Verify and commit non-secret material**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ANDROID_HOME=/opt/homebrew/share/android-commandlinetools bash scripts/verify_release.sh
git add Gemfile fastlane docs scripts/verify_release.sh mobile wear
git commit -m "release: prepare signed Google Play package"
```

Expected: both AABs verify and Git contains no key, password, JSON credential, or `local.properties`.

### Task 12: Google Play internal upload and readback

**Files:**
- Create: `docs/release/internal-release-evidence.md`
- Modify: `docs/release/play-console-checklist.md`, `docs/qa/pixel-watch-4-results.md`

**Interfaces:** Consumes signed hashes, external service account, Play app record, assets, and physical QA; produces read-backed mobile/Wear internal state.

- [ ] **Step 1: Confirm immutable gates before mutation**

Record package `app.zhanzhuang.timer`, version `0.1.0`, mobile `1000001`, Wear `2000001`, track `internal`, rollout `completed/not staged`. Verify package availability, developer identity/package registration, Play App Signing, Health declaration, Data safety, privacy URL, tester list, Wear opt-in, and service-account access.

- [ ] **Step 2: Upload metadata/assets and mobile AAB once**

```bash
SUPPLY_JSON_KEY=/Users/pema/.config/zhanzhuang/google-play/fastlane-supply.json bundle exec fastlane android publish_metadata
SUPPLY_JSON_KEY=/Users/pema/.config/zhanzhuang/google-play/fastlane-supply.json bundle exec fastlane android upload_mobile_internal
```

Expected: package `app.zhanzhuang.timer`, track `internal`, code `1000001`. After ambiguous HTTP 5xx, read remote state; never blindly repeat upload.

- [ ] **Step 3: Upload Wear AAB to confirmed dedicated internal track**

Use only the exact Wear track identifier read from Play Console/API, package `app.zhanzhuang.timer`, code `2000001`, and verified Wear AAB hash. Do not promote either form to closed or production.

- [ ] **Step 4: Read back and record remote truth**

Evidence includes artifact paths/SHA-256, signing fingerprint, locales, image counts, release status, codes, exact tracks, processing/review state. Fastlane success alone is insufficient; remote readback must show both releases.

- [ ] **Step 5: Test Play-delivered builds and commit evidence**

Install through internal tester links on paired phone and Pixel Watch 4. Confirm Google Play installer, identity, Data Layer under Play App Signing, exactly one Health Connect session plus heart-rate series, and 180-minute error ≤2 seconds.

```bash
git add docs/release docs/qa/pixel-watch-4-results.md
git commit -m "release: record Google Play internal verification"
```

Expected: both internal form-factor releases are completed; production is untouched.

## Final Completion Audit

1. Clean `test`, `lintRelease`, and both `bundleRelease` tasks pass.
2. AAB identity proves package, SDK, form factor, unique codes, and same upload certificate.
3. Tests cover boundaries, pause, late reminder, dedupe, outbox ACK, Health fallback/denial, and statistics.
4. Pixel Watch 4 evidence covers 15/30/180-minute screen-off, haptics, heart rate, disconnect, death, and denial.
5. Health Connect has one correct session/heart-rate series; denial retains local function.
6. Runtime/store copy is bilingual and assets match the approved paper/soil/gold/sparkle system.
7. Privacy, Health declaration, Data safety, content rating, developer verification, Play Signing, and Wear opt-in are complete.
8. Remote readback proves both internal releases; no production rollout occurred.
9. Git is clean and contains no secrets or signing material.
