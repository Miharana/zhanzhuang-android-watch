# Pixel Watch 4 physical QA results

Date: 2026-08-01

Physical Pixel Watch 4 evidence is **blocked**: no physical watch was attached during this task. Nothing below is a pass inferred from emulator or local build output.

| Scenario | Expected observation | Observed result | Status |
| --- | --- | --- | --- |
| 15-minute screen-off session | Timer error within release threshold; each expected cue once | Not run | Blocked |
| 30-minute screen-off session | Timer remains active with distinct start/interval/end haptics | Not run | Blocked |
| 180-minute screen-off session | Timer error ≤2 seconds; no duplicate cue or duplicate record | Not run | Blocked |
| Phone disconnect / reconnect | Watch-owned session continues; one eventual sync | Not run | Blocked |
| Denied heart-rate permission | Duration-only timer continues; no invented samples | Not run | Blocked |
| Denied Health Connect permission | Local record persists and retry guidance is available | Not run | Blocked |
| Process death / recovery | Snapshot restores or records an honest interruption | Not run | Blocked |
| Reboot | Session is marked interrupted with only reliable pre-reboot data | Not run | Blocked |
| Battery saver | Timer behavior is observed and documented without a reliability claim | Not run | Blocked |

## Non-substituting emulator evidence

A task-created round Wear OS 4 emulator (Android 13/API 33) executed the Wear instrumentation suite successfully: 5 tests passed. The check covers Compose setup/active/end-confirmation contracts and is recorded in `wear-device-matrix.md`. It does **not** prove physical haptics, heart rate, foreground longevity, screen-off timing, reboot/process recovery, paired Data Layer delivery, or Pixel Watch 4 behavior.

## Play-delivered build status

Play-delivered testing is **blocked**. On 2026-08-01 the available Play Console
session reached developer-account signup rather than a verified developer
dashboard, so neither the mobile nor Wear AAB was uploaded to internal testing.
There is no Play installer, Play App Signing, paired-device, Health Connect, or
Data Layer result to report. This does not alter any physical-device row above.

## Operator evidence template

For each physical row, append:

- Anonymized collector file path and UTC start/end timestamps.
- Watch OS/API and `physical-device` classification (never a serial or health samples).
- App version, permission state, foreground-service/notification state.
- Target/actual duration, timer error, expected/observed haptic count, duplicate count, and phone-sync result.
- A precise result: `Pass`, `Fail`, or `Not run`, plus reproducible steps.
