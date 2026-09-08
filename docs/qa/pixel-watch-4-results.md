# Pixel Watch 4 physical QA results

Last updated: 2026-08-31

## Current 0.1.11 mobile + 0.1.12 Wear broken-functionality fix candidate

Google Play rejected Wear `2000012` / `0.1.11` on 2026-08-30 after its review
device showed the system “Zhan Zhuang keeps stopping” dialog immediately after
launch. The review record does not expose a stack trace. The corrected Wear
candidate is `2000013` / `0.1.12`.

The startup fix prevents a fresh install from starting the status-only health
foreground service when no durable session exists. Active-session recovery
still uses the foreground service, while terminal recovery uses a normal
activity-bound service. The same guard covers boot recovery and stale service
callers.

The coordinated mobile candidate adds a prominent Health Connect export
disclosure. The user must explicitly accept it before the app requests write
permissions; the persisted acceptance and runtime permission are both checked
before any background Health Connect write.

The follow-up Play Policy Insights audit found no Critical or Important code
compliance findings; its Health Connect findings were pruned after verifying
the explicit acceptance gate. Play Console still shows only the existing
foreground-service declaration as a routine manual-review reminder.

Automated and installed verification passed: recovery-policy unit tests,
Android/Wear unit tests, lint, debug and signed release builds, release
manifest/resource/permission contracts, and signed AAB checks. On the API 36
Wear emulator, clearing app data and launching signed `2000013` returned
success, kept the process alive, started no `WearSessionService`, and produced
no fatal/security exception or crash dialog. On the API 33 mobile emulator,
clearing app data and launching debug `1000011` also returned success with
the process alive and no crash output. This is emulator evidence only; physical
Pixel Watch 4 launch and long-running health-service behavior remain not run.

The uploaded phone AAB is `mobile/build/outputs/bundle/release/mobile-release.aab`
(10,728,302 bytes; SHA-256
`87c504798e77ed0ee4007f280ce14a9b8811ca284ce36e1145b79e2518ba85c1`).
The uploaded Wear AAB is `wear/build/outputs/bundle/release/wear-release.aab`
(10,280,590 bytes; SHA-256
`4ab1218bd894ad5949f25cd5004448404d4c0db740c8fa08cb2f55141f248545`).
Android Publisher readback confirms `1000011` on both phone `internal` and
`production`, and `2000013` on both `wear:internal` and `wear:production`.

Codex Browser is on the authorized owner account. Play
Publishing overview submitted the 15 prepared changes after explicit owner
confirmation, including phone `0.1.11` and Wear `0.1.12`; the page now shows
**Changes in review**. Play's automated quick checks continue in the background.

## Current 0.1.11 Wear policy resubmission candidate

The current candidate is phone/tablet `0.1.10` / `1000010` and Wear OS
`0.1.11` / `2000012`. Wear `2000011` was rejected on 2026-08-25 because the
reviewer found no 48x48dp launcher icon on a black Wear startup screen. The
code correction adds the AndroidX branded splash theme and a centered 48dp
launcher icon matching the app identity.

The signed Wear artifact used for the Play upload was
`wear/build/outputs/bundle/release/wear-release.aab` (10,279,695 bytes;
SHA-256 `7e23f3976502d703f5d97351761be3c839104d3d8dd40653073b60c58cc4dc75`).
The signed mobile artifact is `mobile/build/outputs/bundle/release/mobile-release.aab`
(10,723,057 bytes; SHA-256
`1b2169b06041f4f71033f16af3a1af8734f7a0839c10fe1cf91856d31aa7b0a5`).
The andship release lanes uploaded both candidates to their internal tracks
and promoted them to production; sequential Publisher readback returned
`1000010` for phone `internal`/`production` and `2000012` for Wear
`wear:internal`/`wear:production`.

On 2026-08-25, Codex Browser confirmed the authorized owner account
session under `Urticad Tech Ltd`. Submission `15` contains phone Production
`0.1.10`, Wear Production `0.1.11`, and the related store/App content
changes. It was manually confirmed in Play Console and is now **In review**.
Policy status shows **Update in review**; no App content declaration needs
attention. The old splash-icon rejection remains visible only as the prior
policy record while Google reviews the new submission.

The current candidate passed the signed release verifier, release lint, AAB
manifest/resource checks, and Wear resource identity tests. This verifies the
branded splash resource and launch theme in the signed bundle; it is not a
claim of physical Pixel Watch 4 launch or runtime QA.

## 0.1.9 candidate boundary

The current Play candidate is `0.1.9` / `1000009` (phone) and `0.1.9` /
`2000010` (Wear). Automated checks cover its E timer resource,
reminder-interval progress math, round-safe Wear layout build, and phone-side
discovery of a watch session that began before the phone app opened. Physical
Pixel Watch 4 and paired Data Layer verification remain **not run** until an
authorized phone/watch ADB connection is available; no emulator or source
check is treated as a physical pass.

Physical Pixel Watch 4 evidence for the current `0.1.6` internal build is
**not run**. On 2026-08-06, `adb devices -l` returned only the local Android
emulator and no authorized phone or watch. Nothing below is a pass inferred
from emulator, unit tests, or local build output.

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

On 2026-08-07, the 454px round Wear OS 4 emulator (Android 13/API 33) ran the
four `WearScreensTest` UI interactions directly through its installed test
APK: setup defaults, the end-confirmation requirement, confirmed cancellation,
and cancelled-session semantics all passed. Separate final screenshots of the
setup, active-session, and end-confirmation surfaces show every visible action
fully inside the round screen: duration/interval controls and Start session;
Pause and End session; and End session, Cancel session, and Keep standing.
This validates the Wear `0.1.7` / `2000008` source layout correction only; it
does not substitute for a physical Pixel Watch 4 test.

A task-created round Wear OS 4 emulator (Android 13/API 33) executed the Wear instrumentation suite successfully: 5 tests passed. The check covers Compose setup/active/end-confirmation contracts and is recorded in `wear-device-matrix.md`. It does **not** prove physical haptics, heart rate, foreground longevity, screen-off timing, reboot/process recovery, paired Data Layer delivery, or Pixel Watch 4 behavior.

On 2026-08-06, the available phone emulator (Android 17/API 37) ran all six
mobile instrumentation tests for the 0.1.6 source successfully. This checks
the phone debug variant only and does not prove the Play-signed build, physical
launcher icon, companion delivery, or any watch behaviour.

## Historical 0.1.9 Play-delivered build status

Play delivery is **complete for the four requested tracks and submitted for
review**. The phone `internal` and `production` tracks both read
back `0.1.9` (`1000009`), and the Wear OS `wear:internal` and
`wear:production` tracks both read back `0.1.9` (`2000010`); the shared tester list is enabled
on both tracks, and the invited Play test account accepted the invitation. Google Play's
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
above remain `Not run` or `Blocked` until observed. Phone `0.1.4` and Wear
`0.1.5` are historical production releases. The current phone and Wear `0.1.9`
full-rollout changes are submitted for review; this review state does not
substitute for physical launcher or runtime evidence.

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
