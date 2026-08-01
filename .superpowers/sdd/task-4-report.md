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
- The outbox requires an exactly matching persisted revision, refuses stale replacements, retains payloads until an exactly matching ACK, and records retry metadata for Task 6.
- Haptic patterns are fixed and use Android’s `-1` repeat index, so alerts never replay.

## Risks / deliberate boundaries

- Health Services 1.0.0 marks its exercise-status constants `RestrictedApi`; a narrowly scoped suppression is required to consume the documented status values and is protected by the fallback/no-takeover test.
- Lint retains non-blocking warnings for the intentionally UI-less foundation application (missing icon) and `allowBackup=false`; Task 7 owns app visual resources, while future product policy can add data-extraction XML if needed.
- Task 5 must own foreground-service declaration/runtime recovery. Task 6 must own actual Data Layer sending, retries, and ACK transport. Neither is implemented here.

## Commit

`feat: add Wear health and haptic foundation`

---

## Review-fix follow-up

### Files changed

- `wear/src/main/kotlin/app/zhanzhuang/timer/wear/data/WearSessionRepository.kt` — replaced parent-row `REPLACE` persistence with non-destructive insert/update operations.
- `wear/src/main/kotlin/app/zhanzhuang/timer/wear/health/AndroidWearHealthClient.kt` — marks a newly started exercise as owned before callback registration, cleans it up on registration failure, and preserves recoverable ownership when cleanup cannot complete.
- `wear/src/main/kotlin/app/zhanzhuang/timer/wear/health/WearHealthClient.kt` — adds the explicit `StartedWithoutUpdates` result for an active app-owned exercise with unavailable callback updates.
- `wear/src/test/kotlin/app/zhanzhuang/timer/wear/data/WearSessionRepositoryTest.kt` — proves revision 3 persistence retains an unacknowledged revision 2 outbox event.
- `wear/src/test/kotlin/app/zhanzhuang/timer/wear/health/WearHealthClientTest.kt` — verifies callback-registration cleanup, recoverable ownership when cleanup fails, and cancellation rethrow.
- `wear/src/test/kotlin/app/zhanzhuang/timer/wear/session/HapticCuePlayerTest.kt` — verifies every specified cue pattern and `-1` no-repeat setting.

### RED

After adding the new persistence and callback-registration regressions, before production changes:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew :wear:testDebugUnitTest \
  --tests 'app.zhanzhuang.timer.wear.data.WearSessionRepositoryTest.newerSessionRevisionDoesNotDeleteAnUnacknowledgedOutboxEvent' \
  --tests 'app.zhanzhuang.timer.wear.health.WearHealthClientTest.callbackRegistrationSecurityFailureAfterStartingCleansUpOurExercise'
```

Result: failed as expected: the persistence assertion failed because `OnConflictStrategy.REPLACE` deleted the `wear_sessions` parent and cascaded the revision-2 outbox row; the Health Services assertion failed because `endExerciseAsync` was never invoked after a successful `startExerciseAsync` followed by callback-registration `SecurityException`.

The initial fake attempt to throw checked `HealthServicesException` directly from a Java dynamic proxy surfaced as `UndeclaredThrowableException`, so the registration regression uses the equally required `SecurityException` path. The cleanup-failure regression injects `HealthServicesException` through the async `endExerciseAsync` future, which is caught by the same cleanup path.

### GREEN

After the non-destructive Room save path and explicit ownership recovery implementation:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew :wear:testDebugUnitTest \
  --tests 'app.zhanzhuang.timer.wear.data.WearSessionRepositoryTest' \
  --tests 'app.zhanzhuang.timer.wear.health.WearHealthClientTest' \
  --tests 'app.zhanzhuang.timer.wear.session.HapticCuePlayerTest'
```

Result: `BUILD SUCCESSFUL`. The full focused data, health, and haptics suites passed.

The cancellation and full haptic coverage tests were then added against already-correct behavior and verified with:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew :wear:testDebugUnitTest \
  --tests 'app.zhanzhuang.timer.wear.health.WearHealthClientTest' \
  --tests 'app.zhanzhuang.timer.wear.session.HapticCuePlayerTest'
```

Result: `BUILD SUCCESSFUL`.

The app-owned reattach regression was also written before its production change:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew :wear:testDebugUnitTest \
  --tests 'app.zhanzhuang.timer.wear.health.WearHealthClientTest.reattachCallbackFailureRetainsOurExerciseForRecovery'
```

Result: failed as expected at `WearHealthClientTest.kt:109`: an `OWNED_EXERCISE_IN_PROGRESS` reattach followed by callback-registration failure returned `DurationOnly` and `end()` could not recover the active exercise. After marking a reported app-owned exercise as owned before callback registration, the same full focused data/health/haptics command passed (`BUILD SUCCESSFUL`).

### Review-fix self-review

- A newer session revision now uses SQL `UPDATE`, never SQLite `REPLACE`; the parent row is not deleted, so foreign-key-cascaded samples/outbox events survive the transaction. Samples are still atomically replaced within the DAO transaction.
- Callback registration is only immediately cleaned up when this invocation observed no exercise and successfully invoked `startExercise`. A reported `OWNED_EXERCISE_IN_PROGRESS` is retained for explicit later recovery rather than discarded; the adapter never ends an existing third-party exercise.
- If immediate cleanup succeeds, `start()` honestly returns `DurationOnly`: no app-owned exercise remains. If cleanup encounters Health Services/security failure, it returns `StartedWithoutUpdates` and retains ownership so a later `end()` can recover it.
- `CancellationException` is rethrown without converting it into a fallback; ownership remains so caller cancellation handling can subsequently call `end()`.
- Haptic tests assert all five exact patterns and that every enum case carries Android's no-repeat index `-1`.

### Deviations / residual risks

- No Task 5/6/UI implementation was added. The explicit `StartedWithoutUpdates` branch is a Task 4 contract state only; a future controller must surface it and retry `end()` appropriately.
- The dynamic-proxy test seam cannot throw checked `HealthServicesException` directly from `setUpdateCallback` without Java wrapping it. Production catches both `SecurityException` and `HealthServicesException`; the asynchronous cleanup failure test exercises the latter directly.
- On an app-owned reattach callback failure, the deliberately conservative behavior is to retain ownership and let the caller retry `end()` rather than immediately ending an exercise that predated this `start()` invocation.

### Final verification

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew --console=plain :wear:testDebugUnitTest :wear:lintDebug
```

Result: `BUILD SUCCESSFUL`. Wear debug unit test XML reports 14 tests, 0 failures, and 0 errors. Lint has 0 errors (the pre-existing informational `MissingApplicationIcon` and `DataExtractionRules` warnings remain).

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew --console=plain test
```

Result: `BUILD SUCCESSFUL` (132 actionable tasks, all up-to-date after the fresh prior execution).

```sh
MANIFEST_PATH=wear/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml
if rg -n -i 'location|access_(fine|coarse)_location|gps|internet' "$MANIFEST_PATH"; then exit 1; else echo 'no matches'; fi
```

Result: no forbidden manifest matches.

---

## Capability lookup ownership follow-up

### RED

Added focused `OWNED_EXERCISE_IN_PROGRESS` regressions before changing production code:

- `SecurityException` during `getCapabilities()` returns recoverable ownership and `end()` can finish the reported app-owned exercise.
- `HealthServicesException` during `getCapabilities()` has the same recoverable behavior.
- Unsupported heart rate returns `StartedWithoutUpdates(HEART_RATE_UNAVAILABLE)`, rather than the dishonest `DurationOnly` state, and leaves `end()` available.
- `CancellationException` during `getCapabilities()` is rethrown while retaining ownership for a later `end()`.

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew --console=plain :wear:testDebugUnitTest \
  --tests 'app.zhanzhuang.timer.wear.health.WearHealthClientTest.ownedExerciseCapabilityPermissionFailureRetainsOwnershipForRecovery' \
  --tests 'app.zhanzhuang.timer.wear.health.WearHealthClientTest.ownedExerciseCapabilityHealthServicesFailureRetainsOwnershipForRecovery' \
  --tests 'app.zhanzhuang.timer.wear.health.WearHealthClientTest.ownedExerciseWithoutHeartRateRetainsOwnershipForRecovery' \
  --tests 'app.zhanzhuang.timer.wear.health.WearHealthClientTest.ownedExerciseCapabilityCancellationIsRethrownAndRetainsOwnershipForRecovery'
```

Result: failed as expected (four `AssertionError`s). Before the fix, ownership was recorded only after `getCapabilities()` and successful capability selection, so every regression left `end()` as a no-op or reported the non-recoverable `DurationOnly` state.

### GREEN

The adapter now records an observed `OWNED_EXERCISE_IN_PROGRESS` before querying capabilities. Any capability-driven duration-only result for that known app-owned exercise is converted to `StartedWithoutUpdates` with the original reason. Existing no-exercise and third-party-exercise fallbacks remain duration-only; only an app-owned exercise enables lifecycle recovery.

The focused four-test command then passed (`BUILD SUCCESSFUL`).

### Final verification

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew --console=plain :wear:testDebugUnitTest \
  --tests 'app.zhanzhuang.timer.wear.health.WearHealthClientTest'

JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew --console=plain :wear:testDebugUnitTest :wear:lintDebug

JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew --console=plain test
```

Result: all commands were `BUILD SUCCESSFUL`.

```sh
MANIFEST_PATH=wear/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml
if rg -n -i 'location|access_(fine|coarse)_location|gps|internet' "$MANIFEST_PATH"; then exit 1; else echo 'no matches'; fi
```

Result: no forbidden manifest matches.

### Self-review

- An existing third-party exercise never sets `ownsExercise`, so this change cannot end it; the existing duration-only/no-takeover branch is unchanged.
- An app-owned existing exercise becomes recoverable immediately after status observation. This covers permission and Health Services failures plus cancellation before callback registration.
- `StartedWithoutUpdates` honestly signals that an app-owned exercise remains active without usable heart-rate updates; callers can call `end()` to recover it.
- A fresh no-exercise path still owns an exercise only after `startExercise()` succeeds. Unsupported heart rate or capability failure before that remains `DurationOnly`.

## Final commits and review gate

- `6f93e1f feat: add Wear health and haptic foundation`
- `5078ac2 fix: preserve Wear outbox and exercise ownership`
- `19d2246 fix: retain Wear ownership through capability lookup`

Independent review was run after the implementation and each corrective pass. The final review approved spec compliance and code quality with no remaining Critical, Important, or Minor findings.

---

## Exact ACK revision follow-up

### RED

`acknowledgmentOnlyRemovesExactlyMatchingRevision` was extended before changing the DAO to acknowledge the revision-2 event with revision 3 and assert that the outbox still contains the event.

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew :wear:testDebugUnitTest --tests 'app.zhanzhuang.timer.wear.data.WearSessionRepositoryTest'
```

Result: failed as expected at the higher-revision ACK assertion. The prior `revision <= :acknowledgedRevision` predicate incorrectly deleted revision 2 for ACK revision 3.

### GREEN

The outbox delete predicate is now `revision = :acknowledgedRevision`. The focused test proves lower and higher ACKs retain the revision-2 event, while the equal ACK removes it.

### Tests

- Focused `WearSessionRepositoryTest`: `BUILD SUCCESSFUL`.
- `:wear:testDebugUnitTest :wear:lintDebug`: `BUILD SUCCESSFUL`.
- `./gradlew test`: `BUILD SUCCESSFUL`.
- Merged Wear debug manifest scan for `location`, GPS, and coarse/fine location: no matches.

### Self-review

- The change is limited to exact event/revision ACK matching; it does not add Data Layer transport, retry processing, or UI/service scope.
- A future transport ACK carrying a later revision can no longer erase a durable earlier-revision event.
- Commit: `ac92e89 fix: require exact Wear outbox ACK revision`.

---

## Root-review fixes: repeated-start ownership and atomic session read

### Scope

Implemented only the requested root-review fixes. No Task 5 service/UI behavior and no Task 6 Data Layer behavior changed.

### RED — repeated start after a previously owned exercise

Added three regressions that first start an adapter-owned exercise, then make the next `getCurrentExerciseInfo()` fail. Each test asserts that cancellation is rethrown, ordinary failures return the honest `StartedWithoutUpdates` result, and a following `end()` still invokes Health Services:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew --console=plain :wear:testDebugUnitTest --rerun-tasks \
  --tests 'app.zhanzhuang.timer.wear.health.WearHealthClientTest.repeatedStartStatusLookupCancellationIsRethrownAndKeepsOurExerciseEndable' \
  --tests 'app.zhanzhuang.timer.wear.health.WearHealthClientTest.repeatedStartStatusLookupPermissionFailureKeepsOurExerciseEndable' \
  --tests 'app.zhanzhuang.timer.wear.health.WearHealthClientTest.repeatedStartStatusLookupHealthServicesFailureKeepsOurExerciseEndable'
```

Result: failed as expected with three `AssertionError`s at `WearHealthClientTest.kt:234`, `:256`, and `:281`. The old first line of `start()` cleared `ownsExercise` before status lookup; all three follow-up `end()` calls became no-ops.

### GREEN — repeated-start ownership

`start()` no longer clears ownership before status lookup. A successful status read clears it only for a definitive `NONE` or `OTHER_APP` result, records it for a definitive `OWNED` result, and leaves the prior value intact for `UNKNOWN`. Thus a failed lookup cannot lose a known app-owned exercise, while a known third-party exercise remains non-endable. The same focused `--rerun-tasks` command passed with `BUILD SUCCESSFUL`.

### RED — atomic repository read

Added `getUsesOneAtomicAggregateDaoRead`, a deterministic repository contract test. Its DAO wrapper fails immediately if repository `get()` calls the legacy `session()` or `samples()` APIs, and records exactly one aggregate-read call. Before production code was added, the test intentionally failed at compilation because `WearSessionWithSamples`, `sessionWithSamples`, and the DAO-injected repository constructor did not exist:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew --console=plain :wear:testDebugUnitTest --rerun-tasks \
  --tests 'app.zhanzhuang.timer.wear.data.WearSessionRepositoryTest.getUsesOneAtomicAggregateDaoRead'
```

Result: `:wear:compileDebugUnitTestKotlin FAILED` with the expected missing aggregate API and constructor errors.

### GREEN — atomic repository read

Added Room's `WearSessionWithSamples` relation projection and a single-session `@Transaction` DAO query, `sessionWithSamples(id)`. `WearSessionRepository.get()` now consumes that aggregate directly. The contract test proves a single aggregate DAO call returns both saved samples and never reaches the legacy split-read methods; Room performs the one-parent relation read in the DAO transaction, so it has no N+1 behavior beyond the one session being read. The focused aggregate command passed with `BUILD SUCCESSFUL`.

### RED/GREEN — aggregate heart-rate ordering

The follow-up contract test `getOrdersAggregateHeartRateSamplesChronologically` wraps the real DAO but deliberately returns its aggregate samples in descending timestamp order. Before the mapping change it failed as expected at `WearSessionRepositoryTest.kt:96`:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew --console=plain :wear:testDebugUnitTest --rerun-tasks \
  --tests 'app.zhanzhuang.timer.wear.data.WearSessionRepositoryTest.getOrdersAggregateHeartRateSamplesChronologically'
```

`get()` now sorts aggregate samples by `epochMillis` before constructing `SessionRecord`. The same command then passed with `BUILD SUCCESSFUL`.

### Focused verification

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew --console=plain :wear:testDebugUnitTest --rerun-tasks \
  --tests 'app.zhanzhuang.timer.wear.health.WearHealthClientTest' \
  --tests 'app.zhanzhuang.timer.wear.data.WearSessionRepositoryTest'

JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew --console=plain :wear:testDebugUnitTest :wear:lintDebug

JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew --console=plain test
```

Result: all commands were `BUILD SUCCESSFUL`; the focused health/persistence run covered 15 health and 6 persistence tests, all with zero failures/errors. Full repository test ran 132 actionable tasks.

```sh
MANIFEST_PATH=wear/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml
if rg -n -i 'location|access_(fine|coarse)_location|gps|internet' "$MANIFEST_PATH"; then exit 1; else echo 'no forbidden manifest matches'; fi
git diff --check
```

Result: `no forbidden manifest matches`; diff check clean.

### Residual concern

On an `UNKNOWN` status response, the adapter deliberately preserves prior ownership per the review requirement; it never establishes ownership from `UNKNOWN`. A definitive `OTHER_APP` response always clears ownership, preventing takeover of a third-party exercise.
