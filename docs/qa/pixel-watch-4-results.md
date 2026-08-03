# Pixel Watch 4 physical QA results

Date: 2026-08-03

Physical Pixel Watch 4 evidence for `0.1.1` is **not run**: `adb devices -l`
returned no attached devices during this build. Nothing below is a pass
inferred from emulator, unit tests, or local build output.

| Scenario | Expected observation | Observed result | Status |
| --- | --- | --- | --- |
| Unified launcher identity | Phone and watch show the standing figure holding a gold taiji | Source, APK resources, and Play asset pass automated checks; physical launchers not observed | Not run |
| Cold launch before heart-rate permission | App opens setup without a foreground-service crash | Not run | Not run |
| Continue without heart rate | Duration-only timer starts and remains controllable | Not run | Not run |
| Grant heart-rate permission | New session records available heart-rate samples without changing timer ownership | Not run | Not run |
| Notification tap | Active-session notification reopens the Wear activity | Not run | Not run |
| Phone-originated start / pause / resume / finish | One Wear-owned session responds once to each command | Not run | Not run |
| Second phone session | Completed summary and setup coexist; next start gets a different ID and retains history | Not run | Not run |
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

Play delivery is **ready for physical testing**. The phone and Wear OS internal
tracks are active with version `0.1.3` (`1000004` / `2000004`); the shared tester list is enabled on
both tracks, and `scorpiocn@gmail.com` accepted the invitation. Google Play's
install picker recognizes both the Google Pixel Watch 4 and Google Pixel 10
Pro. After the 0.1.3 upload, the install picker selected Pixel Watch 4 and
accepted the Install action. The account holder completed Google Passkey
verification, and Play returned the explicit confirmation that the app would be
installed on the device soon. A second request selected Pixel 10 Pro so the
phone launcher can receive the same 0.1.3 identity. The account holder also
completed that Google Passkey challenge, and Play returned the same explicit
install-soon confirmation for the phone. No installed version, launcher
appearance, runtime, Health Connect, Data Layer, timing, haptic, or recovery
result is inferred from either remote request, so the physical-device rows
above remain `Not run` or `Blocked` until observed.

## Current physical connectivity boundary

Earlier on 2026-08-03, Android's companion-device service on the attached phone
reported an approved Pixel Watch 4 association and an active secure companion
transport. This proves pairing only. After the 0.1.3 upload, `adb devices -l`
returned no attached target; ADB mDNS advertised only the phone but could not
establish a connection, and macOS USB inventory did not enumerate a Pixel or
Android device. Therefore neither installed launcher, watch application,
foreground service, database, permissions, nor logs could be inspected. No
watch serial, address, or health value is retained in this record.

After both remote-install confirmations, the phone continued to advertise an
ADB TLS-connect service over mDNS, but `adb connect` failed and a fresh
`adb devices -l` still returned no target. The advertisement alone is not an
authorized debug connection and is not used as physical-device evidence.

The Play-installed phone package read back as production `0.1.0` / `1000001`;
it had not yet updated to internal `0.1.2`. A fresh installed-version readback
for `0.1.3` was not possible without ADB. This is consistent with the pending
Google Passkey confirmation for remote installation and is not a failure of the
active internal track.

## Operator evidence template

For each physical row, append:

- Anonymized collector file path and UTC start/end timestamps.
- Watch OS/API and `physical-device` classification (never a serial or health samples).
- App version, permission state, foreground-service/notification state.
- Target/actual duration, timer error, expected/observed haptic count, duplicate count, and phone-sync result.
- A precise result: `Pass`, `Fail`, or `Not run`, plus reproducible steps.
