# Zhan Zhuang 0.1.1 Stability Design

Status: approved

Date: 2026-08-02

## Baseline

Version `0.1.0` is active on the phone and Wear OS internal tracks. The phone
build installs and runs on a Pixel phone, but a completed session leaves the
training surface on a terminal summary with no way to configure and start the
next session. The Play-delivered Wear build installs on Pixel Watch 4 but does
not open reliably, and the phone and watch do not complete the expected session
handshake. The `0.1.0` production submission remains in Google review and must
not be replaced or edited by this work.

The next candidate is version `0.1.1`, with mobile version code `1000002` and
Wear version code `2000002`. It will go to internal testing first.

## Goals

1. Make the Play-delivered Wear app open without crashing before health
   permission has been granted.
2. Preserve a fully functional duration-only timer when heart-rate permission
   is denied, revoked, or unavailable.
3. Restore phone-to-watch session ownership, runtime updates, remote controls,
   terminal delivery, and acknowledgement on a paired device.
4. Let the phone start a second session immediately after the first session
   ends without deleting or hiding its history record.
5. Replace the current filled pillar figure with one consistent, deterministic
   gold line-art icon across phone, Wear, adaptive, monochrome, and Play assets.

## Non-goals

- No production upload, review replacement, or rollout is authorized by this
  design.
- No account system, cloud backend, advertising, payment, Internet permission,
  location access, or health-data read access is added.
- No broad navigation rewrite, database replacement, protocol-version reset,
  haptic editor, tile, complication, coaching content, or medical claim is
  included in `0.1.1`.
- The internal capability wire name `zhan_zhang_sync` is centralized but not
  renamed, so `0.1.0` and `0.1.1` devices remain discoverable during an
  incremental update.

## Root-cause findings

### Wear launch

`MainActivity.onCreate()` currently sends `ACTION_STATUS` through
`startForegroundService()`. `WearSessionService.onStartCommand()` immediately
calls `startForeground()` for a service declared only as type `health`. On
Android 14 and later, a health foreground service must satisfy a health runtime
permission prerequisite at promotion time. A first-launch user has not granted
`BODY_SENSORS` or `READ_HEART_RATE`, so the platform can throw
`SecurityException` before the app reaches its permission UI.

The existing Wear instrumentation flow runs on API 33 and therefore does not
exercise this API 34+ enforcement. It also launches the activity before its
test method revokes sensor permission. A new test must cover the launch-policy
decision directly.

The ongoing notification currently uses a service `PendingIntent` targeting
`ACTION_STATUS`. Tapping it refreshes the service instead of opening the Wear
activity.

### Phone terminal screen

`TrainingScreen` uses mutually exclusive active, terminal, and setup branches.
The terminal branch renders completion copy only. `MainViewModel` correctly
retains the terminal record from Room, so clearing transient Compose state
would not solve the problem: the durable record would be selected again.

### Pairing and sync

Phone and Wear use the same package, capability string, paths, and wire codec.
The main observed failure boundary is Wear command execution: remote commands
enter the same foreground service path that can fail before permission. The
coordinator's timeout and phone-ownership fallback are already covered by unit
tests and remain the safety mechanism when a watch is genuinely unreachable.

## Architecture

### Permission-aware Wear foreground service

The Wear service will declare both `health` and `specialUse` foreground-service
types and their required manifest permissions. The existing user-started
standing-timer explanation will be attached to the Wear `specialUse` service.

Foreground promotion will use an explicit type mask:

- When the Wear heart-rate runtime permission is granted, promote as
  `specialUse | health` before invoking `ExerciseClient`.
- When the permission is missing or revoked, promote as `specialUse` only and
  do not invoke `ExerciseClient`; return the existing duration-only health
  result instead.

This policy applies to local start, phone-originated start, status recovery,
process restart, and boot recovery. A permission change may reduce a recovered
session to duration-only operation, but it must not terminate the timer or
invent heart-rate samples.

`onStartCommand()` must still promote the service within the foreground-service
deadline. The type decision is synchronous and occurs before controller
recovery. All Health Services entry points remain behind the same permission
gate so the declared foreground type and actual sensor work cannot diverge.

### Wear navigation and recovery

The launcher activity remains the user-facing entry point. Its initial status
request uses the permission-aware service launch policy. The ongoing
notification will use `PendingIntent.getActivity()` to reopen `MainActivity`.
Opening the app with no session shows setup; opening it with an active durable
session shows the recovered timer; opening it with a terminal record shows the
existing completion screen and Done action.

### Phone and Wear synchronization

The existing wire protocol, durable ownership rules, ten-second fallback, and
completed-session outbox remain authoritative. `zhan_zhang_sync` will move to a
single shared Kotlin constant consumed by both transports and the phone
connection observer. The two XML capability resources remain declarative, with
a contract test requiring both values to equal the shared constant.

When a peer becomes reachable, Wear retries completed outbox delivery and
publishes its current active state when one exists. Phone connection continues
to be based on reachable capability discovery; a watch-owned session is shown
only after the phone has received authoritative Wear state. If that state does
not arrive within the existing timeout, the phone safely becomes owner and
runs the timer locally.

No connection indicator may claim that a session is running on the watch until
the state/ownership response has been received. Physical QA must verify local
watch start, phone-originated watch start, pause, resume, finish, completion
delivery, duplicate prevention, disconnect, and reconnect.

### Consecutive phone sessions

For a terminal phone state, the training surface will render a compact terminal
summary followed immediately by the normal setup controls. The history record
remains durable and visible. Starting again creates a fresh UUID and replaces
the presented terminal state with the new active state; no dismissal flag,
timer, delayed navigation, or database mutation is needed.

This approach keeps the completion feedback, survives activity recreation, and
uses the existing setup/start path rather than introducing a second start
implementation.

### Icon system

The icon remains deterministic vector artwork because the project already has
an editable SVG source and native Android vector resources.

The new mark uses:

- deep soil `#15110B` outside and soil `#241C12` inside the circular field;
- a simplified front-facing standing figure drawn with thick, rounded gold
  lines;
- a round head, grounded legs, and two curved forearms forming a chest-height
  circular negative space that reads as “holding a ball” without drawing a
  literal filled ball;
- ordered gold accents `#E7D5A6`, `#C6A867`, `#A9863F`, and `#D8C18C` to retain
  the reflective-metal identity at large sizes;
- one restrained four-point sparkle in `#FFF4D6` at the upper right;
- no text, facial detail, medical symbol, gradient bitmap, fine hairline, or
  extra ornament.

The main silhouette must remain legible at 48 px and inside the Android
adaptive-icon safe zone. The same geometry will drive
`fastlane/assets-source/icon.svg`, phone and Wear foreground vectors,
monochrome vectors, and the rendered 512 px Play icons. Store alt text will
describe the new holding-ball standing figure.

## Error handling

- Missing or revoked heart-rate permission selects duration-only mode and is
  visible in session UI; it is not an exceptional app-termination path.
- Health Services `SecurityException` and availability failures remain bounded
  inside the health adapter and cannot stop the timer actor.
- Foreground promotion failures are recorded in test/log evidence and must not
  be swallowed by a blanket exception in the service entry point.
- Failed Data Layer sends retain the existing phone fallback or Wear outbox
  retry behavior; they do not create a second owner.
- A stale or duplicate remote command cannot revive a terminal session or stop
  another session.

## Testing

Implementation follows red-green-refactor. Required automated evidence:

1. A failing launch-policy test showing that missing heart-rate permission must
   select `specialUse` and skip Health Services.
2. A permission-granted test showing that the explicit type mask includes
   `specialUse | health` before Health Services starts.
3. A revoked-permission recovery test proving an active timer becomes
   duration-only instead of crashing or losing elapsed time.
4. A notification contract test proving the content intent opens
   `MainActivity`.
5. A phone UI test proving a terminal summary and setup/start controls coexist.
6. An installed-activity flow that starts, ends, and starts a second phone
   session with a different ID.
7. Sync tests proving both modules consume the shared capability constant and
   that failed watch acknowledgement retains the phone fallback.
8. Existing domain, mobile, Wear, repository, health, sync, resource identity,
   and release-contract suites.
9. Debug APK and release AAB builds for both modules, without running a Gradle
   clean task.

Physical Pixel Watch 4 evidence remains mandatory because target-API-36
foreground-service behavior, Play signing, haptics, screen-off timing, and the
paired Data Layer path cannot be proven by API 33 Wear emulator tests. The first
physical pass is: cold launch before permission, continue without heart rate,
grant heart rate, phone-originated start, pause/resume, finish, second session,
disconnect/reconnect, and completion delivery.

## Release boundaries

After local verification, both `0.1.1` AABs may be prepared as immutable
internal-test candidates. Uploading them to internal testing, modifying Play
foreground-service declarations, replacing the production review, or rolling
out production are separate gates. The current `0.1.0` production review stays
untouched until `0.1.1` passes physical phone and Pixel Watch 4 testing and the
owner explicitly authorizes the next Play action.
