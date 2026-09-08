# Zhan Zhuang 0.1.1 Stability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a locally verified `0.1.1` internal-test candidate that opens safely on Pixel Watch 4 without heart-rate permission, restores phone/watch control and consecutive phone sessions, and uses the approved gold holding-ball icon.

**Architecture:** Keep the existing Room-backed timer actors and Data Layer protocol. Add a synchronous Wear foreground-service policy and a permission-gated health adapter, reuse the existing setup path after terminal phone states, centralize the existing capability string, and regenerate all launcher/store icon assets from one deterministic SVG.

**Tech Stack:** Kotlin 2.1, Android/Wear OS target SDK 36, Jetpack Compose, Room, Health Services, Health Connect, Google Play Services Wearable Data Layer, Robolectric, Android instrumentation tests, Gradle 8/AGP 8.13, SVG plus macOS `sips`.

## Global Constraints

- Version name is `0.1.1`; mobile version code is `1000002`; Wear version code is `2000002`.
- Keep application ID `app.zhanzhuang.timer` and capability wire name `zhan_zhang_sync` unchanged.
- Preserve protocol version `1`, the existing Data Layer paths, ten-second phone fallback, durable ownership rules, and completed-session outbox.
- Missing, denied, or revoked heart-rate permission must retain a duration-only timer and must not invoke Health Services.
- The Wear foreground service declares `specialUse`; it adds `health` to the runtime type mask only while heart-rate permission is granted.
- No account, cloud backend, ads, payments, Internet permission, location permission, health-data read scope, medical claims, tile, or complication.
- Do not run any Gradle `clean` task.
- Do not modify, replace, or upload the active `0.1.0` production review. `0.1.1` artifacts are internal-test candidates only until physical QA passes and the owner authorizes Play changes.
- Physical Pixel Watch 4 QA is mandatory after automated verification.

---

### Task 1: Permission-aware Wear service and health gate

**Files:**
- Create: `wear/src/main/kotlin/app/zhanzhuang/timer/wear/session/WearForegroundPolicy.kt`
- Create: `wear/src/main/kotlin/app/zhanzhuang/timer/wear/health/PermissionAwareWearHealthClient.kt`
- Modify: `wear/src/main/kotlin/app/zhanzhuang/timer/wear/session/WearSessionService.kt`
- Modify: `wear/src/main/AndroidManifest.xml`
- Test: `wear/src/test/kotlin/app/zhanzhuang/timer/wear/session/WearForegroundPolicyTest.kt`
- Test: `wear/src/test/kotlin/app/zhanzhuang/timer/wear/health/PermissionAwareWearHealthClientTest.kt`
- Test: `wear/src/test/kotlin/app/zhanzhuang/timer/wear/WearUiServiceContractTest.kt`

**Interfaces:**
- Consumes: `WearHealthClient`, `WearHealthStartResult`, `DurationOnlyReason.PERMISSION_DENIED`, and the current Wear service command actor.
- Produces: `WearForegroundPolicy.hasHeartRatePermission(Context): Boolean`, `WearForegroundPolicy.serviceTypeMask(Boolean): Int`, and `PermissionAwareWearHealthClient(delegate, permissionGranted)`.

- [ ] **Step 1: Write the failing policy and health-gate tests**

```kotlin
@Test fun missingPermissionUsesSpecialUseOnly() {
    assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE, WearForegroundPolicy.serviceTypeMask(false))
}

@Test fun grantedPermissionAddsHealthType() {
    assertEquals(
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH,
        WearForegroundPolicy.serviceTypeMask(true),
    )
}

@Test fun deniedPermissionNeverTouchesDelegate() = runTest {
    val delegate = RecordingHealthClient()
    val client = PermissionAwareWearHealthClient(delegate) { false }
    assertEquals(WearHealthStartResult.DurationOnly(DurationOnlyReason.PERMISSION_DENIED), client.start())
    assertEquals(WearHealthStartResult.DurationOnly(DurationOnlyReason.PERMISSION_DENIED), client.reattach())
    client.pause(); client.resume(); client.end()
    assertTrue(delegate.calls.isEmpty())
}
```

Add manifest assertions for `FOREGROUND_SERVICE_SPECIAL_USE`, `health|specialUse`, and the `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` value `standing_meditation_timer`.

- [ ] **Step 2: Run the focused tests and verify red**

Run:

```bash
./gradlew :wear:testDebugUnitTest --tests '*WearForegroundPolicyTest' --tests '*PermissionAwareWearHealthClientTest' --tests '*WearUiServiceContractTest'
```

Expected: FAIL because the two production types and special-use manifest declaration do not exist.

- [ ] **Step 3: Implement the synchronous policy and permission-gated adapter**

```kotlin
object WearForegroundPolicy {
    fun hasHeartRatePermission(context: Context): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 36) "android.permission.health.READ_HEART_RATE"
        else Manifest.permission.BODY_SENSORS
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun serviceTypeMask(hasHeartRatePermission: Boolean): Int =
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
            if (hasHeartRatePermission) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0
}

class PermissionAwareWearHealthClient(
    private val delegate: WearHealthClient,
    private val permissionGranted: () -> Boolean,
) : WearHealthClient {
    override val updates = delegate.updates
    override suspend fun start() = if (permissionGranted()) delegate.start() else denied()
    override suspend fun reattach() = if (permissionGranted()) delegate.reattach() else denied()
    override suspend fun pause() { if (permissionGranted()) delegate.pause() }
    override suspend fun resume() { if (permissionGranted()) delegate.resume() }
    override suspend fun end() { if (permissionGranted()) delegate.end() }
    private fun denied() = WearHealthStartResult.DurationOnly(DurationOnlyReason.PERMISSION_DENIED)
}
```

Wrap `AndroidWearHealthClient` before injecting it into the controller. In `onStartCommand`, synchronously calculate permission and call:

```kotlin
ServiceCompat.startForeground(
    this,
    OngoingSessionNotification.NOTIFICATION_ID,
    notification.build(controller.state.value, notification.openActivityIntent()),
    WearForegroundPolicy.serviceTypeMask(WearForegroundPolicy.hasHeartRatePermission(this)),
)
```

Declare `android.permission.FOREGROUND_SERVICE_SPECIAL_USE`, set `android:foregroundServiceType="health|specialUse"`, and add:

```xml
<property
    android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
    android:value="standing_meditation_timer" />
```

- [ ] **Step 4: Run focused and controller regression tests**

Run:

```bash
./gradlew :wear:testDebugUnitTest --tests '*WearForegroundPolicyTest' --tests '*PermissionAwareWearHealthClientTest' --tests '*WearUiServiceContractTest' --tests '*WearSessionControllerTest' --tests '*WearHealthClientTest'
```

Expected: PASS; the denied adapter records zero delegate calls, and existing duration/recovery tests remain green.

- [ ] **Step 5: Commit**

```bash
git add wear/src/main/AndroidManifest.xml wear/src/main/kotlin/app/zhanzhuang/timer/wear/session wear/src/main/kotlin/app/zhanzhuang/timer/wear/health wear/src/test/kotlin/app/zhanzhuang/timer/wear
git commit -m "fix: make Wear launch permission safe"
```

### Task 2: Wear notification navigation and restart recovery

**Files:**
- Modify: `wear/src/main/kotlin/app/zhanzhuang/timer/wear/session/OngoingSessionNotification.kt`
- Modify: `wear/src/main/kotlin/app/zhanzhuang/timer/wear/session/WearSessionService.kt`
- Test: `wear/src/test/kotlin/app/zhanzhuang/timer/wear/session/OngoingSessionNotificationTest.kt`
- Test: `wear/src/test/kotlin/app/zhanzhuang/timer/wear/WearUiServiceContractTest.kt`

**Interfaces:**
- Consumes: `MainActivity`, notification ID `41`, and Task 1 foreground type policy.
- Produces: `OngoingSessionNotification.openActivityIntent(): PendingIntent`; all recovery notifications reopen the launcher activity.

- [ ] **Step 1: Write a failing Robolectric notification-intent test**

```kotlin
@Test fun contentIntentReopensMainActivity() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val pending = OngoingSessionNotification(context).openActivityIntent()
    assertTrue(pending.isActivity)
    val shadow = Shadows.shadowOf(pending)
    assertEquals(MainActivity::class.java.name, shadow.savedIntent.component?.className)
}
```

- [ ] **Step 2: Run it and verify red**

Run: `./gradlew :wear:testDebugUnitTest --tests '*OngoingSessionNotificationTest'`

Expected: FAIL because `openActivityIntent()` does not exist and the service currently supplies a service `PendingIntent`.

- [ ] **Step 3: Implement activity navigation and remove the service status intent**

```kotlin
fun openActivityIntent(): PendingIntent = PendingIntent.getActivity(
    context,
    0,
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
)
```

Have both initial promotion and `publishForeground` build with `notification.openActivityIntent()`. Delete `statusIntent()` from `WearSessionService`; `MainActivity.onCreate()` remains the status recovery trigger.

- [ ] **Step 4: Run the Wear unit suite**

Run: `./gradlew :wear:testDebugUnitTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add wear/src/main/kotlin/app/zhanzhuang/timer/wear/session wear/src/test/kotlin/app/zhanzhuang/timer/wear
git commit -m "fix: reopen Wear session from notification"
```

### Task 3: Shared capability identity and reconnect state publication

**Files:**
- Modify: `core/model/src/main/kotlin/app/zhanzhuang/timer/model/SyncProtocol.kt`
- Modify: `mobile/src/main/kotlin/app/zhanzhuang/timer/mobile/MainActivity.kt`
- Modify: `mobile/src/main/kotlin/app/zhanzhuang/timer/mobile/sync/MobileDataLayerService.kt`
- Modify: `wear/src/main/kotlin/app/zhanzhuang/timer/wear/sync/WearDataLayerService.kt`
- Modify: `wear/src/main/kotlin/app/zhanzhuang/timer/wear/sync/WearSyncCoordinator.kt`
- Test: `core/model/src/test/kotlin/app/zhanzhuang/timer/model/SyncProtocolTest.kt`
- Test: `mobile/src/test/kotlin/app/zhanzhuang/timer/mobile/ui/ResourceIdentityContractTest.kt`
- Test: `wear/src/test/kotlin/app/zhanzhuang/timer/wear/ResourceIdentityContractTest.kt`
- Test: `wear/src/test/kotlin/app/zhanzhuang/timer/wear/sync/WearSyncCoordinatorTest.kt`

**Interfaces:**
- Consumes: current XML capability resources and `WearSyncController.current()`.
- Produces: shared `const val CAPABILITY_ZHAN_ZHUANG_SYNC = "zhan_zhang_sync"` and `WearSyncCoordinator.publishCurrentState(nodeId: String): Boolean`.

- [ ] **Step 1: Write failing identity and reconnect tests**

```kotlin
@Test fun capabilityNameRemainsBackwardCompatible() =
    assertEquals("zhan_zhang_sync", CAPABILITY_ZHAN_ZHUANG_SYNC)

@Test fun activeCurrentStateIsPublishedToReconnectedPeer() = runTest {
    val controller = CurrentController(runningRecord())
    val transport = FakeTransport()
    assertTrue(WearSyncCoordinator(FakeOutbox(), transport, controller).publishCurrentState("phone-node"))
    assertEquals("phone-node", transport.targetNodeIds.single())
    assertIs<SyncPayload.State>(transport.messages.single().payload)
}
```

In each resource identity test, parse `src/main/res/xml/wearable_capabilities.xml` and assert its sole capability name equals `CAPABILITY_ZHAN_ZHUANG_SYNC`.

- [ ] **Step 2: Run focused tests and verify red**

Run:

```bash
./gradlew :core:model:test :mobile:testDebugUnitTest --tests '*ResourceIdentityContractTest' :wear:testDebugUnitTest --tests '*ResourceIdentityContractTest' --tests '*WearSyncCoordinatorTest'
```

Expected: FAIL because the constant and reconnect publication API do not exist.

- [ ] **Step 3: Centralize the constant and publish only active Wear-owned state**

```kotlin
const val CAPABILITY_ZHAN_ZHUANG_SYNC = "zhan_zhang_sync"

suspend fun publishCurrentState(nodeId: String): Boolean = mutex.withLock {
    val record = controller?.current()?.takeIf {
        it.owner == SessionOwner.WEAR && it.status in ACTIVE_STATUSES
    } ?: return@withLock false
    sendState(record, nodeId)
}
```

Replace the three private string literals in mobile transport, mobile connection observer, and Wear transport with the shared constant. In `WearDataLayerService.onPeerConnected`, enqueue outbox retry and launch both `resendCompleted()` and `publishCurrentState(peer.id)` inside `runCatching`.

- [ ] **Step 4: Run model/mobile/Wear sync regressions**

Run:

```bash
./gradlew :core:model:test :mobile:testDebugUnitTest --tests '*Sync*' --tests '*ResourceIdentityContractTest' :wear:testDebugUnitTest --tests '*Sync*' --tests '*ResourceIdentityContractTest'
```

Expected: PASS; a terminal or missing current record returns false and sends no state.

- [ ] **Step 5: Commit**

```bash
git add core/model mobile/src/main mobile/src/test wear/src/main wear/src/test
git commit -m "fix: restore Wear reconnect state sync"
```

### Task 4: Consecutive phone sessions

**Files:**
- Modify: `mobile/src/main/kotlin/app/zhanzhuang/timer/mobile/ui/screens/TrainingScreen.kt`
- Modify: `mobile/src/androidTest/kotlin/app/zhanzhuang/timer/mobile/ui/MobileScreensTest.kt`
- Modify: `mobile/src/androidTest/kotlin/app/zhanzhuang/timer/mobile/ui/MobileSessionFlowTest.kt`

**Interfaces:**
- Consumes: existing `TerminalTraining`, `SetupTraining`, `onStart`, and Room-backed session creation.
- Produces: terminal summary plus the normal setup controls in one scroll surface; second start creates a different durable UUID.

- [ ] **Step 1: Add failing terminal/setup coexistence and installed second-start assertions**

```kotlin
@Test fun completedSummaryKeepsNewSessionControlsReachable() {
    rule.setContent { TrainingScreen(state = completedState(), /* existing callbacks */) }
    rule.onNodeWithText("Completed").assertIsDisplayed()
    rule.onNodeWithText("Start standing").assertIsDisplayed()
}
```

Extend `MobileSessionFlowTest` to capture the first completed ID from Room, press `Start standing`, wait for `Standing`, and assert the active Room record has an ID different from the completed ID while the completed history record remains present.

- [ ] **Step 2: Run the installed UI tests and verify red**

Run:

```bash
./gradlew :mobile:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.zhanzhuang.timer.mobile.ui.MobileScreensTest,app.zhanzhuang.timer.mobile.ui.MobileSessionFlowTest
```

Expected: the coexistence/second-start assertion FAILS on the existing mutually exclusive terminal branch. If no emulator is connected, first record `adb devices -l` evidence, then run Task 4 unit/build verification and defer only this device test to the physical checklist.

- [ ] **Step 3: Render terminal feedback followed by setup**

```kotlin
when {
    active -> ActiveTraining(state, onPause, onResume, onFinish, onCancel)
    state.session?.status in TERMINAL_STATUSES -> {
        TerminalTraining(state)
        HorizontalDivider()
        SetupTraining(state, onDurationChange, onIntervalChange, onStart)
    }
    else -> SetupTraining(state, onDurationChange, onIntervalChange, onStart)
}
```

- [ ] **Step 4: Run mobile unit tests, debug build, and connected tests when available**

Run:

```bash
./gradlew :mobile:testDebugUnitTest :mobile:assembleDebug
./gradlew :mobile:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.zhanzhuang.timer.mobile.ui.MobileScreensTest,app.zhanzhuang.timer.mobile.ui.MobileSessionFlowTest
```

Expected: PASS when a device is available; otherwise the first command must pass and the exact connected-test gap is carried into physical QA.

- [ ] **Step 5: Commit**

```bash
git add mobile/src/main/kotlin/app/zhanzhuang/timer/mobile/ui/screens/TrainingScreen.kt mobile/src/androidTest
git commit -m "fix: allow consecutive phone sessions"
```

### Task 5: Gold holding-ball icon system

**Files:**
- Modify: `fastlane/assets-source/icon.svg`
- Modify: `fastlane/assets-source/ALT_TEXT.md`
- Modify: `mobile/src/main/res/drawable/ic_launcher_foreground.xml`
- Modify: `mobile/src/main/res/drawable/ic_launcher_monochrome.xml`
- Modify: `wear/src/main/res/drawable/ic_launcher_foreground.xml`
- Modify: `wear/src/main/res/drawable/ic_launcher_monochrome.xml`
- Modify: `fastlane/metadata/android/en-GB/images/icon.png`
- Modify: `fastlane/metadata/android/en-US/images/icon.png`
- Modify: `fastlane/metadata/android/zh-CN/images/icon.png`
- Modify: `mobile/src/test/kotlin/app/zhanzhuang/timer/mobile/ui/ResourceIdentityContractTest.kt`
- Modify: `wear/src/test/kotlin/app/zhanzhuang/timer/wear/ResourceIdentityContractTest.kt`

**Interfaces:**
- Consumes: deep soil `#15110B`/`#241C12`, four ordered gold accents, and one `#FFF4D6` sparkle.
- Produces: one front-facing thick rounded line figure whose arms enclose a chest-height circular negative space, shared across SVG, adaptive foreground, monochrome, Wear, and 512 px Play icons.

- [ ] **Step 1: Tighten the failing resource contract**

Add assertions that foreground vectors contain `strokeLineCap="round"`, `strokeLineJoin="round"`, at least one transparent-fill gold stroke path, and no old filled-pillar path prefix `M256 105`. Assert SVG contains `stroke-linecap="round"`, `stroke-linejoin="round"`, the label `holding-ball-figure`, and no `<text` or `<linearGradient`.

- [ ] **Step 2: Run resource tests and verify red**

Run:

```bash
./gradlew :mobile:testDebugUnitTest --tests '*ResourceIdentityContractTest' :wear:testDebugUnitTest --tests '*ResourceIdentityContractTest'
```

Expected: FAIL because the current figure is a filled pillar without rounded line geometry.

- [ ] **Step 3: Replace source and Android vectors with deterministic line art**

Use one circular head, one shoulder/torso/grounded-leg path, mirrored curved arm paths around an empty chest circle, two short reflective accent strokes, and one four-point sparkle. Keep all figure strokes at least 18 SVG units (approximately 3.5 px at 100 px and legible at 48 px), rounded caps/joins, and geometry inside the adaptive safe zone.

The SVG group must use:

```xml
<g id="holding-ball-figure" fill="none" stroke-linecap="round" stroke-linejoin="round">
  <circle cx="256" cy="150" r="35" stroke="#E7D5A6" stroke-width="22"/>
  <path d="M222 220 Q256 202 290 220 M256 214 L256 333 M256 279 L205 376 M256 279 L307 376" stroke="#C6A867" stroke-width="26"/>
  <path d="M222 224 Q180 254 197 307 Q214 339 238 302" stroke="#A9863F" stroke-width="22"/>
  <path d="M290 224 Q332 254 315 307 Q298 339 274 302" stroke="#D8C18C" stroke-width="22"/>
</g>
```

Translate the same geometry to the 108×108 Android viewport; monochrome uses identical paths with `#FFFFFFFF`.

- [ ] **Step 4: Render and validate all Play icons**

Run:

```bash
sips -s format png fastlane/assets-source/icon.svg --out /tmp/zhanzhuang-icon.png
sips -z 512 512 /tmp/zhanzhuang-icon.png --out fastlane/metadata/android/en-GB/images/icon.png
cp fastlane/metadata/android/en-GB/images/icon.png fastlane/metadata/android/en-US/images/icon.png
cp fastlane/metadata/android/en-GB/images/icon.png fastlane/metadata/android/zh-CN/images/icon.png
sips -g pixelWidth -g pixelHeight fastlane/metadata/android/en-GB/images/icon.png
shasum -a 256 fastlane/metadata/android/{en-GB,en-US,zh-CN}/images/icon.png
./gradlew :mobile:testDebugUnitTest --tests '*ResourceIdentityContractTest' :wear:testDebugUnitTest --tests '*ResourceIdentityContractTest' :mobile:assembleDebug :wear:assembleDebug
```

Expected: all PNGs report `512 x 512`, all three hashes match, resource tests pass, and both debug APKs build.

- [ ] **Step 5: Commit**

```bash
git add fastlane/assets-source fastlane/metadata/android/*/images/icon.png mobile/src/main/res/drawable mobile/src/test wear/src/main/res/drawable wear/src/test
git commit -m "feat: add gold holding-ball launcher icon"
```

### Task 6: Version identity, release evidence, and immutable internal candidates

**Files:**
- Modify: `mobile/build.gradle.kts`
- Modify: `wear/build.gradle.kts`
- Create: `fastlane/metadata/android/en-GB/changelogs/1000002.txt`
- Create: `fastlane/metadata/android/en-US/changelogs/1000002.txt`
- Create: `fastlane/metadata/android/zh-CN/changelogs/1000002.txt`
- Create: `fastlane/metadata/android/en-GB/changelogs/2000002.txt`
- Create: `fastlane/metadata/android/en-US/changelogs/2000002.txt`
- Create: `fastlane/metadata/android/zh-CN/changelogs/2000002.txt`
- Modify: `docs/qa/pixel-watch-4-results.md`
- Modify: `docs/release/internal-release-evidence.md`

**Interfaces:**
- Consumes: Tasks 1–5 and existing release signing environment variables.
- Produces: versioned mobile/Wear `0.1.1` release AABs and SHA-256 evidence without a Play upload.

- [ ] **Step 1: Add `0.1.1` version identity and localized changelogs**

Set mobile `versionCode = 1_000_002`, Wear `versionCode = 2_000_002`, and both `versionName = "0.1.1"`. Changelog content must mention consecutive sessions, safer Wear launch/duration-only operation, reconnect synchronization, and the new icon; Chinese copy must be equivalent and must not make medical claims.

- [ ] **Step 2: Run all JVM tests serially**

Run:

```bash
./gradlew :core:model:test :core:domain:test :mobile:testDebugUnitTest :wear:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL with zero failing tests.

- [ ] **Step 3: Build debug APKs and signed release AABs without clean**

Run:

```bash
./gradlew :mobile:assembleDebug :wear:assembleDebug :mobile:bundleRelease :wear:bundleRelease
```

Expected: BUILD SUCCESSFUL and these files exist:

```text
mobile/build/outputs/bundle/release/mobile-release.aab
wear/build/outputs/bundle/release/wear-release.aab
```

- [ ] **Step 4: Verify artifact identity and record immutable hashes**

Run:

```bash
apkanalyzer manifest application-id mobile/build/outputs/apk/debug/mobile-debug.apk
apkanalyzer manifest version-name mobile/build/outputs/apk/debug/mobile-debug.apk
apkanalyzer manifest version-code mobile/build/outputs/apk/debug/mobile-debug.apk
apkanalyzer manifest version-name wear/build/outputs/apk/debug/wear-debug.apk
apkanalyzer manifest version-code wear/build/outputs/apk/debug/wear-debug.apk
shasum -a 256 mobile/build/outputs/bundle/release/mobile-release.aab wear/build/outputs/bundle/release/wear-release.aab
```

Expected: debug application ID is `app.zhanzhuang.timer.debug`, both names are `0.1.1`, codes are `1000002` and `2000002`, and both AAB hashes are copied exactly into `docs/release/internal-release-evidence.md` with the build timestamp and commit SHA.

- [ ] **Step 5: Run physical phone and Pixel Watch 4 gate**

Install the debug builds and record pass/fail for cold launch before permission, continue without heart rate, grant heart rate, watch-local start, phone-originated start, pause, resume, finish, second session, notification reopen, screen-off timing, disconnect/reconnect, completion delivery, and duplicate prevention in `docs/qa/pixel-watch-4-results.md`. Do not mark release-ready while any row is untested or failing.

- [ ] **Step 6: Commit candidate metadata and evidence**

```bash
git add mobile/build.gradle.kts wear/build.gradle.kts fastlane/metadata/android docs/qa/pixel-watch-4-results.md docs/release/internal-release-evidence.md
git commit -m "chore: prepare Zhan Zhuang 0.1.1 internal candidate"
```

No Play Console upload is part of this plan. Stop after reporting artifact paths, hashes, automated results, and any remaining physical-device gate.
