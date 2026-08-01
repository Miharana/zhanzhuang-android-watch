# Wear device QA matrix

Status key: `Pass` requires directly observed evidence on the stated device class. `Not run` and `Blocked` are not passes, and emulator evidence never satisfies a physical-device row.

| Device / OS | Form factor | Evidence | Automated UI | Long session / screen off | Health / haptics / Data Layer | Status |
| --- | --- | --- | --- | --- | --- | --- |
| Android 9 / API 28 | Phone | No task AVD/image run | Not run | Not run | Not run | Blocked — no local API 28 image |
| Android 14 / API 34 | Phone | No task AVD/image run | Not run | Not run | Not run | Blocked — no local API 34 phone run |
| Android 16 / API 36 | Phone emulator | Task-created local Pixel 8 AVD; anonymized record in `local-phone-device-evidence-2026-08-01.md` | 4 tests passed; real activity → ViewModel → FGS → Room completion/recreate path | Not run | No paired Wear or Health Connect claim | Partial emulator evidence only |
| Task 10 Wear OS 4 / Android 13 API 33 | Round Wear emulator | Task-created local AVD; anonymized record in `local-device-evidence-2026-08-01.md` | 5 tests passed; real activity → FGS pause/resume/finish → Room completion/recreate path | Not run | Permission-denied/duration-only surface was not observed after runtime revoke; no sensor/paired-phone claim | Partial emulator evidence only |
| Wear OS 5 / API 34 | Round Wear emulator | No AVD/image run | Not run | Not run | Not run | Blocked — no run in this task |
| Wear OS 6 / API 36 | Round Wear emulator | No AVD/image run | Not run | Not run | Not run | Blocked — no run in this task |
| Pixel Watch 4 | Physical round watch | No physical device attached | Not run | Not run | Not run | Blocked — requires physical device |

## Local environment probe — 2026-08-01

- No ADB device was attached before the task-created emulator was started.
- Existing user AVDs were observed but not started or stopped: `Miharana_Android_17_Resizable` (API 37), `Miharana_Pixel_8_API_36` (API 36), `Miharana_Pixel_9_API_37` (API 37), and `Miharana_Pixel_Tablet_API_37` (API 37).
- No pre-existing Wear AVD existed. Task 10 installed the Wear OS 4 API 33 system image into the external SDK and created a unique round Wear AVD. It is emulator evidence only and is shut down after collection.
- Task 10 also created a unique API 36 phone AVD using the already-installed Google APIs image. It ran the mobile instrumentation suite, is emulator evidence only, and is shut down after collection.
- The debug Wear APK was installed solely to inspect package metadata. No foreground session, sensor reading, or notification lifecycle was asserted from that install.

## Required physical-device operator run

1. Install the signed/internal Wear build on a Pixel Watch 4 and record only an anonymized `connected-device-N` label using `scripts/collect_device_evidence.sh --output docs/qa/<date>-pixel-watch.md`.
2. Run separate 15-, 30-, and 180-minute screen-off sessions. Record expected versus observed elapsed time, haptic count, duplicate count, and sync state; do not record heart-rate samples.
3. Repeat the 30-minute case with phone disconnect/reconnect, denied heart-rate permission, denied Health Connect permission, process death, reboot, and battery saver. Mark each exact case `Pass`, `Fail`, or `Not run`.
4. Verify a completed watch-owned record reaches the paired phone once, then verify retry/ack does not duplicate it. This requires a real paired Data Layer path; an isolated emulator cannot prove it.
5. Keep Pixel Watch 4 rows blocked until all observed results are linked here. Do not promote emulator, local build, or test-only results to a physical or Play-internal pass.
