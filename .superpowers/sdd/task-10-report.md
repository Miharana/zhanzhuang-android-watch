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

## Actual limitations

- These are local emulator runs, not physical-device or Google Play/Internal-test evidence.
- No Pixel Watch 4 was attached. The 15-, 30-, and 180-minute screen-off sessions, haptics, live heart rate, Health Connect, paired Data Layer transfer, disconnect/reconnect, process death, reboot, and battery-saver scenarios were not executed and remain blocked/not run in the QA documents.
- Android API 28 and API 34 phone cases and Wear OS 5/6 cases were not run; the matrix records this explicitly.
- `shellcheck` was not installed, so it could not be run. `bash` contract checks and `git diff --check` were run instead.
- The approved design/spec/plan requires Wear standalone `true`; the initial task handoff said `false`. The gate intentionally enforces the approved `true` value.
