# Task 4 — Wear persistence, Health Services, and haptics

## Result

Implemented the Wear OS foundation in `:wear` (application ID `app.zhanzhuang.timer`, SDK 33/36/36, version code `2000001`) without adding the Task 5 foreground service/UI, Task 6 Data Layer transport, or any Health Connect writes. The phone remains the only prospective Health Connect writer.

## Files changed

- `settings.gradle.kts` — includes `:wear`.
- `gradle/libs.versions.toml` — adds Health Services client `1.0.0`.
- `wear/build.gradle.kts` — new Wear app module, JDK 17 toolchain, Room and Health Services dependencies.
- `wear/src/main/AndroidManifest.xml` — watch-only feature, standalone metadata, health FGS/notification/sensor permissions and API caps; no location or network permission.
- `wear/src/main/kotlin/app/zhanzhuang/timer/wear/data/*` — Room database, Wear-owned session persistence, heart-rate samples, and a revision-safe outbox.
- `wear/src/main/kotlin/app/zhanzhuang/timer/wear/health/*` — meditation/heart-rate-only Health Services adapter with honest duration-only fallbacks.
- `wear/src/main/kotlin/app/zhanzhuang/timer/wear/session/HapticCuePlayer.kt` — fixed one-shot vibration patterns.
- `wear/src/test/kotlin/app/zhanzhuang/timer/wear/{data,health,session}/*` — regression coverage for configuration, fallbacks, no-takeover lifecycle behavior, outbox revisions/ACKs, and haptics.

## TDD evidence

All commands used JDK 17 and the supplied Android SDK path.

### RED

1. Initial feature tests (Health config/fallback, haptics, and persistence/outbox) were added before production classes.

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew :wear:testDebugUnitTest --tests 'app.zhanzhuang.timer.wear.health.WearHealthClientTest' --tests 'app.zhanzhuang.timer.wear.session.HapticCuePlayerTest' --tests 'app.zhanzhuang.timer.wear.data.WearSessionRepositoryTest'
```

Result: failed as expected at test compilation with unresolved Task 4 symbols (`createExerciseConfig`, `Cue`, `WearDatabase`, `WearSessionRepository`, and the Health result types).

2. Lifecycle-safety regression was added before changing the adapter.

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew :wear:testDebugUnitTest --tests 'app.zhanzhuang.timer.wear.health.WearHealthClientTest.lifecycleIsNoOpAfterAnotherAppExerciseFallback'
```

Result: failed as expected: `AssertionError` at `WearHealthClientTest.kt:47`; `pause/resume/end` were reaching an existing third-party exercise after the duration-only fallback.

3. Future-revision outbox regression was added before changing persistence.

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew :wear:testDebugUnitTest --tests 'app.zhanzhuang.timer.wear.data.WearSessionRepositoryTest.outboxCannotClaimAnUnpersistedFutureRevision'
```

Result: failed as expected: `AssertionError` at `WearSessionRepositoryTest.kt:56`; an outbox event could initially claim an unpersisted future revision.

### GREEN

After the minimal implementation and each corrective change:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew :wear:testDebugUnitTest :wear:lintDebug
```

Result: `BUILD SUCCESSFUL` (9 Wear unit tests; lint clean of errors).

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew test
```

Result: `BUILD SUCCESSFUL` (all repository test tasks).

## Manifest audit

Inspected `wear/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml` with:

```sh
rg -n -i 'location|access_(fine|coarse)_location|gps|internet' \
  wear/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml
```

Result: no matches. The merged manifest has the required watch feature, standalone metadata, health FGS and notification permission, legacy `BODY_SENSORS`/`BODY_SENSORS_BACKGROUND` capped at 35, and API-36 health read permissions. It contains no GPS, location, or `INTERNET` permission.

## Self-review

- Health Services config is explicitly `ExerciseType.MEDITATION`, requests only `HEART_RATE_BPM`, and disables GPS and auto-pause.
- `OTHER_APP_IN_PROGRESS`, unsupported heart rate, unavailable Health Services, and permission denial return an explicit duration-only result. No start call is made over another app’s exercise.
- `pause`, `resume`, and `end` are no-ops unless this adapter has successfully obtained an app-owned exercise; cancellation is always rethrown.
- The outbox requires an exactly matching persisted revision, refuses stale replacements, retains payloads until matching-or-newer ACK, and records retry metadata for Task 6.
- Haptic patterns are fixed and use Android’s `-1` repeat index, so alerts never replay.

## Risks / deliberate boundaries

- Health Services 1.0.0 marks its exercise-status constants `RestrictedApi`; a narrowly scoped suppression is required to consume the documented status values and is protected by the fallback/no-takeover test.
- Lint retains non-blocking warnings for the intentionally UI-less foundation application (missing icon) and `allowBackup=false`; Task 7 owns app visual resources, while future product policy can add data-extraction XML if needed.
- Task 5 must own foreground-service declaration/runtime recovery. Task 6 must own actual Data Layer sending, retries, and ACK transport. Neither is implemented here.

## Commit

`feat: add Wear health and haptic foundation`
