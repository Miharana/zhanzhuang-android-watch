# Task 10 report — Android and Wear release evidence gates

Date: 2026-08-01

## RED

- The new shell contract test initially failed because `scripts/run_android_checks.sh` and `scripts/collect_device_evidence.sh` did not exist.
- The first full gate run exposed missing JDK/SDK exports; the gate now resolves an installed Java 17 and Android SDK or fails with an actionable message.
- The fresh artifact gate then exposed the missing debug application-ID suffix. Both modules now produce `app.zhanzhuang.timer.debug` debug APKs while release identity remains `app.zhanzhuang.timer`.
- The first Wear emulator run found an ambiguous existing `30 min` test assertion. The test now selects one matching node; no production logic changed.
- The first mobile lint run caught state created without `remember` in the new instrumentation flow test. The test was corrected before the final green gate.

## GREEN

- `scripts/tests/release_evidence_contract_test.sh` passes.
- `scripts/run_android_checks.sh` passes: unit tests, debug lint, and fresh debug builds completed without `gradlew clean`; both APKs were inspected with `apkanalyzer`/`aapt` for ID, version, min/target SDK, launcher icons, Chinese resource configuration, form factor/standalone metadata, foreground/health permissions, and absent INTERNET/location permissions.
- `:wear:connectedDebugAndroidTest` passed 5 tests on a task-created round Wear OS 4 / Android 13 API 33 emulator.
- `:mobile:connectedDebugAndroidTest` passed 4 tests on a task-created Android 16 / API 36 phone emulator.
- The collector generated anonymized evidence only: `connected-device-1`, OS/API, broad form factor, installed version, permissions, foreground-service/notification state, and no health samples.

## Review follow-up — 2026-08-01

- Collector discovery now streams `adb devices -l` through process substitution; raw serials exist only in shell memory and are never written to a temporary file, stdout, or evidence markdown.
- The release gate now checks fresh debug APKs plus fresh release merged manifests. It requires artifact `compileSdkVersion=36`, debug identity `app.zhanzhuang.timer.debug`, release identity `app.zhanzhuang.timer`, exact watch feature/standalone metadata, complete mobile/Wear health and foreground permission sets, API constraints, launcher icons, locales, and no Internet or location permissions.
- The mobile and Wear instrumentation flows now launch production `MainActivity` and drive real foreground-service paths. Mobile starts a disconnected phone-owned session, finishes it, recreates the activity, and checks the durable Room row. Wear starts the real service, pauses, resumes, confirms finish, recreates the activity, and checks the durable Wear Room row.
- Fresh reruns: `:mobile:connectedDebugAndroidTest` passed 4 tests on the task-created Android 16/API 36 emulator; `:wear:connectedDebugAndroidTest` passed 5 tests on the task-created Wear OS 4/Android 13/API 33 round emulator. Both emulators were stopped afterward.
- On the API 33 emulator, the test revokes `BODY_SENSORS` before launch but no duration-only permission surface appears; the exact test therefore proceeds through the real service path and does not claim the denied-permission UI was observed. This remains an explicit Wear permission-flow limitation, not a pass.
- Privacy rejection now covers every `uses-permission*` manifest node, including `uses-permission-sdk-23`. The shell contract fixture exercises SDK-qualified INTERNET, fine/coarse location, and background-location declarations so none can bypass the release gate.

## Actual limitations

- These are local emulator runs, not physical-device or Google Play/Internal-test evidence.
- No Pixel Watch 4 was attached. The 15-, 30-, and 180-minute screen-off sessions, haptics, live heart rate, Health Connect, paired Data Layer transfer, disconnect/reconnect, process death, reboot, and battery-saver scenarios were not executed and remain blocked/not run in the QA documents.
- Android API 28 and API 34 phone cases and Wear OS 5/6 cases were not run; the matrix records this explicitly.
- `shellcheck` was not installed, so it could not be run. `bash` contract checks and `git diff --check` were run instead.
- The approved design/spec/plan requires Wear standalone `true`; the initial task handoff said `false`. The gate intentionally enforces the approved `true` value.
