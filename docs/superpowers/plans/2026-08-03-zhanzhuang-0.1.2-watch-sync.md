# Zhan Zhuang 0.1.2 Watch Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make a Pixel Watch 4 session started on the watch appear immediately on the paired phone, then deliver the verified fix through both Google Play internal-testing tracks.

**Architecture:** The Wear foreground service remains the only timer authority. At every forced sync point it publishes the durable Wear-owned `SessionRecord` before its ephemeral five-second `SessionRuntime`, allowing the phone to establish ownership before accepting runtime updates. Existing reconnect, completion-outbox, Health Connect, and offline timer behaviour remain unchanged.

**Tech Stack:** Kotlin, Android foreground services, Wear OS Data Layer, Room, Kotlin coroutines, Compose Material 3, Gradle, Fastlane Supply, Google Play Android Publisher API.

## Global Constraints

- Package remains `app.zhanzhuang.timer`; debug package remains `app.zhanzhuang.timer.debug`.
- Phone and Wear share the approved upload certificate and use version `0.1.2` with codes `1000003` and `2000003`.
- Minimum/target SDK remain phone `28/36` and Wear `33/36`.
- No Internet, location, Advertising ID, account, analytics, or cloud permission is added.
- Production tracks remain at `0.1.0`; only `internal` and `wear:internal` may change.
- English and Simplified Chinese release copy must describe the same behaviour without medical claims.

---

### Task 1: Publish authoritative Wear state before runtime

**Files:**
- Modify: `wear/src/main/kotlin/app/zhanzhuang/timer/wear/session/WearSessionService.kt`
- Test: `wear/src/test/kotlin/app/zhanzhuang/timer/wear/WearUiServiceContractTest.kt`

**Interfaces:**
- Consumes: `WearSyncCoordinator.publishState(SessionRecord)` and `publishRuntime(SessionRuntime)`.
- Produces: a forced sync sequence of durable `SyncPayload.State` followed by ephemeral `SyncPayload.Runtime`.

- [x] **Step 1: Write the failing service contract test**

```kotlin
@Test fun activeServicePublishesDurableStateBeforeRuntimeUpdates() {
    val source = File("src/main/kotlin/app/zhanzhuang/timer/wear/session/WearSessionService.kt").readText()
    val runtimePublisher = source
        .substringAfter("private fun scheduleRuntimePublish")
        .substringBefore("private suspend fun publishTerminalState")
    val statePublish = runtimePublisher.indexOf("publishState(record)")
    val runtimePublish = runtimePublisher.indexOf("publishRuntime(runtime)")
    assertTrue(statePublish >= 0)
    assertTrue(runtimePublish > statePublish)
}
```

- [x] **Step 2: Run the test and observe the expected failure**

Run:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew :wear:testDebugUnitTest \
  --tests app.zhanzhuang.timer.wear.WearUiServiceContractTest.activeServicePublishesDurableStateBeforeRuntimeUpdates
```

Expected: assertion failure because `publishState(record)` is absent.

- [x] **Step 3: Publish state before runtime at forced checkpoints**

```kotlin
val sync = WearSyncRuntime.get(applicationContext)
if (force) sync.publishState(record)
sync.publishRuntime(runtime)
```

- [x] **Step 4: Re-run the focused test**

Expected: `BUILD SUCCESSFUL`.

### Task 2: Prepare immutable 0.1.2 release identity

**Files:**
- Modify: `mobile/build.gradle.kts`
- Modify: `wear/build.gradle.kts`
- Modify: `scripts/run_android_checks.sh`
- Modify: `scripts/verify_release.sh`
- Create: `fastlane/metadata/android/en-GB/changelogs/1000003.txt`
- Create: `fastlane/metadata/android/en-US/changelogs/1000003.txt`
- Create: `fastlane/metadata/android/zh-CN/changelogs/1000003.txt`
- Create: `fastlane/metadata/android/en-GB/changelogs/2000003.txt`
- Create: `fastlane/metadata/android/en-US/changelogs/2000003.txt`
- Create: `fastlane/metadata/android/zh-CN/changelogs/2000003.txt`

**Interfaces:**
- Consumes: the existing split phone/Wear version-code convention.
- Produces: version name `0.1.2`, mobile code `1000003`, Wear code `2000003`.

- [x] **Step 1: Change both Gradle identities and every verifier expectation**

Set phone/Wear version name to `0.1.2`, codes to `1_000_003` and `2_000_003`, and replace the corresponding `0.1.1`, `1000002`, and `2000002` release expectations in both scripts.

- [x] **Step 2: Add equivalent localized changelogs**

English:

```text
Watch-started sessions now appear on the paired phone immediately, with timer ownership established before live countdown updates. Includes the 0.1.1 launch, notification, consecutive-session, and visual refinements.
```

Chinese:

```text
从手表开始站桩后，配对手机现在会立即显示同一次练习；系统会先确认计时归属，再同步实时倒计时。并包含 0.1.1 的启动、通知、连续练习和视觉优化。
```

- [x] **Step 3: Run script contract tests and `git diff --check`**

Run:

```bash
bash scripts/tests/release_evidence_contract_test.sh
bash scripts/tests/verify_release_contract_test.sh
git diff --check
```

Expected: all script contract tests pass with no whitespace errors.

### Task 3: Run automated, signed, and phone-device gates

**Files:**
- Modify: `docs/release/internal-release-evidence.md`
- Modify: `docs/qa/local-phone-device-evidence-2026-08-02.md` only if a new phone run adds evidence.

**Interfaces:**
- Consumes: the approved external keystore through 1Password environment references.
- Produces: fresh APKs and signed AABs whose manifests, certificate, hashes, and tests match 0.1.2.

- [x] **Step 1: Run the full clean-free Android gate**

```bash
scripts/run_android_checks.sh
```

Expected: all JVM tests, debug lint, debug APKs, release manifests, permissions, locales, versions, and package contracts pass.

- [x] **Step 2: Run connected phone instrumentation**

```bash
./gradlew :mobile:connectedDebugAndroidTest
```

Expected: all mobile instrumentation tests pass on the attached Pixel phone.

- [x] **Step 3: Build and verify signed release candidates**

Run `scripts/verify_release.sh` through `op run` with the established `Zhan Zhuang Android Upload` references. Expected: both AABs pass release lint, strict JAR integrity, upload-certificate matching, bundle identity, SDK, permissions, and localized-store gates.

- [x] **Step 4: Record exact AAB SHA-256 and byte counts**

```bash
shasum -a 256 mobile/build/outputs/bundle/release/mobile-release.aab \
  wear/build/outputs/bundle/release/wear-release.aab
wc -c mobile/build/outputs/bundle/release/mobile-release.aab \
  wear/build/outputs/bundle/release/wear-release.aab
```

Expected: evidence records exact, non-placeholder values from the fresh signed artifacts.

### Task 4: Deliver to internal testing and close physical QA

**Files:**
- Modify: `docs/release/internal-release-evidence.md`
- Modify: `docs/qa/pixel-watch-4-results.md`

**Interfaces:**
- Consumes: signed 0.1.2 AABs and the repo-external Google Play service account.
- Produces: Android Publisher readback for `internal=1000003` and `wear:internal=2000003`, plus Pixel Watch 4 runtime evidence.

- [x] **Step 1: Read back current remote tracks before mutation**

Expected baseline: internal tracks contain 0.1.1 and production tracks remain 0.1.0.

- [x] **Step 2: Upload phone and Wear AABs sequentially**

```bash
SUPPLY_JSON_KEY="$HOME/.config/miharana/google-play/miharana-fastlane-supply.json" \
  bundle exec fastlane android upload_mobile_internal
SUPPLY_JSON_KEY="$HOME/.config/miharana/google-play/miharana-fastlane-supply.json" \
  bundle exec fastlane android upload_wear_internal
```

Expected: both uploads finish successfully; no production lane runs.

- [x] **Step 3: Read back all four tracks**

Expected: `internal=0.1.2|completed|1000003`, `wear:internal=0.1.2|completed|2000003`, and both production tracks remain `0.1.0|completed`.

- [ ] **Step 4: Complete Pixel Watch 4 matrix**

Install 0.1.2, connect the watch to ADB, and record cold launch, duration-only, heart rate, notification reopen, watch-local start, phone-originated start, pause/resume/finish, completion delivery, duplicate prevention, screen-off timing, disconnect/reconnect, permission denial, process recovery, reboot, and battery saver as `Pass`, `Fail`, or `Not run` with anonymized evidence.

- [ ] **Step 5: Commit the verified release state**

```bash
git add mobile wear scripts fastlane/metadata/android docs
git commit -m "fix: sync watch-started sessions in 0.1.2"
```

Expected: the commit contains source, tests, version metadata, and evidence only; no key, service-account JSON, `local.properties`, or generated Fastlane report.
