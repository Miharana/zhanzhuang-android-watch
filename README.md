# 站桩 · Zhan Zhuang

[![License: GPL-3.0-only](https://img.shields.io/badge/License-GPL--3.0--only-blue.svg)](./LICENSE)
[![Commercial Licence](https://img.shields.io/badge/Commercial%20Licence-%C2%A379%20GBP-lightgrey.svg)](./LICENSE-COMMERCIAL.md)

A quiet, offline-first timer for Zhan Zhuang (standing meditation) practice on Android and Wear OS (`app.zhanzhuang.timer`).

---

## Architecture

The project is structured as a multi-module Gradle build (`Kotlin 2.1.10`, `AGP 8.13.1`, JVM toolchain 17):

```text
├── core/
│   ├── model/     # Shared session models, sync protocol, notification policy
│   └── domain/    # Pure Kotlin clock, interval progress, HR summary, sync conflict resolution
├── mobile/        # Android phone app (minSdk 28, targetSdk 36, Jetpack Compose + Material 3)
└── wear/          # Wear OS watch app (minSdk 33, targetSdk 36, Wear Compose Material 3)
```

### Shared Core (`:core:model`, `:core:domain`)
- **`SessionClock` & `IntervalProgress`:** Deterministic timer and interval state progression shared identically between phone and watch.
- **`SyncProtocol` & `SyncConflictResolver`:** Typed Wearable Data Layer message contracts and conflict resolution for paired phone–watch state.
- **`HeartRateSummary`:** Aggregates optional informational heart-rate samples recorded during a session.

### Wear OS (`:wear`)
- **Low-power foreground session (`WearSessionService`):** Maintains an ongoing activity notification (`androidx.wear.ongoing`) with snapshot persistence (`SessionSnapshotStore`) and reboot/process recovery (`SessionRecoveryReceiver`).
- **Tactile pacing (`HapticCuePlayer`):** Non-intrusive haptic interval cues designed for eyes-closed practice.
- **Offline-resilient sync:** Completed sessions are queued in a local Room outbox (`OutboxEntity`) and flushed to the paired phone via `WearCompletionSyncWorker` and the Wearable Data Layer when connectivity resumes.
- **Optional heart rate:** When explicitly permitted, `WearHealthClient` records informational heart-rate samples via Wear Health Services.

### Android Phone (`:mobile`)
- **Local persistence:** Session configuration, runtime state, history, and heart-rate samples are stored locally in Room (`MobileDatabase`).
- **Paired watch coordination:** `MobileDataLayerService` and `MobileSyncCoordinator` synchronise session state and completed watch sessions with the phone.
- **Optional Health Connect export:** With explicit user write permission, `HealthSyncWorker` writes completed mindfulness sessions (with exercise-session fallback where unsupported) and available heart-rate samples to Health Connect. Refusing or revoking Health Connect access leaves the core timer unaffected.

---

## Offline Privacy Design

- **100% offline:** No account system, no developer-operated cloud backend, and no internet permission requested.
- **Zero tracking:** No advertising, analytics, crash-reporting, or third-party tracking SDKs.
- **Minimal permissions:** Requests only permissions required for the timer, notifications/haptics, optional foreground health session, optional watch heart-rate sampling, and optional Health Connect writing. See [`docs/privacy-policy.md`](./docs/privacy-policy.md).

---

## Building

Requires JDK 17 and an Android SDK with API 36 installed:

```bash
./gradlew :core:model:test :core:domain:test
./gradlew :mobile:assembleDebug :wear:assembleDebug
```

---

## Licensing

**站桩 · Zhan Zhuang** is dual-licensed by **URTICAD TECH LTD** (Company No. `16542241`, England and Wales):

- **Open Source (`GPL-3.0-only`):** Available under the GNU General Public License Version 3. See [`LICENSE`](./LICENSE).
- **Commercial Source Licence:** Closed-source modification, proprietary embedding, or commercial app-store redistribution without GPLv3 source disclosure requires a **£79 GBP** one-off perpetual Commercial Source Licence for the current major version. See [`LICENSE-COMMERCIAL.md`](./LICENSE-COMMERCIAL.md) or visit <https://www.urticad.com/licensing/>.
