# 站桩 · Zhan Zhuang Timer

[![License: GPL-3.0-only](https://img.shields.io/badge/License-GPL--3.0--only-blue.svg)](./LICENSE)
[![Commercial Licence](https://img.shields.io/badge/Commercial%20Licence-%C2%A379%20GBP-lightgrey.svg)](./LICENSE-COMMERCIAL.md)

Zhan Zhuang Timer is an offline timer for standing meditation on Android and Wear OS (`app.zhanzhuang.timer`). The watch gives haptic cues at set intervals so you can practise with your eyes closed.

## Architecture

The Gradle build uses `Kotlin 2.1.10`, `AGP 8.13.1` and JVM toolchain 17.

```text
├── core/
│   ├── model/     # Shared session models, sync protocol, notification policy
│   └── domain/    # Pure Kotlin clock, interval progress, HR summary, sync conflict resolution
├── mobile/        # Android phone app (minSdk 28, targetSdk 36, Jetpack Compose + Material 3)
└── wear/          # Wear OS watch app (minSdk 33, targetSdk 36, Wear Compose Material 3)
```

### Shared core (`:core:model`, `:core:domain`)

- `SessionClock` and `IntervalProgress` advance the timer and interval state in the same deterministic way on both devices.
- `SyncProtocol` defines typed Wearable Data Layer messages. `SyncConflictResolver` resolves differences in paired session state.
- `HeartRateSummary` aggregates optional informational heart-rate samples from a session.

### Wear OS (`:wear`)

- The low-power foreground service `WearSessionService` keeps an ongoing activity notification (`androidx.wear.ongoing`). `SessionSnapshotStore` saves timer state for `SessionRecoveryReceiver` to recover after a reboot or process exit.
- `HapticCuePlayer` delivers interval cues.
- The watch queues completed sessions in a local Room outbox (`OutboxEntity`). `WearCompletionSyncWorker` sends them to the paired phone through the Wearable Data Layer when the devices reconnect.
- With your permission, `WearHealthClient` records informational heart-rate samples through Wear Health Services.

### Android phone (`:mobile`)

- `MobileDatabase` keeps session settings, timer state, history and heart-rate samples in a local Room database.
- `MobileDataLayerService` and `MobileSyncCoordinator` synchronise session state and completed watch sessions with the phone.
- With write permission, `HealthSyncWorker` exports completed mindfulness sessions and available heart-rate samples to Health Connect. It writes an exercise session where mindfulness records are unsupported. The timer still works if you decline or revoke permission.

## Privacy and permissions

The app requires no account or internet permission. It has no developer-operated cloud backend and uses no advertising, analytics, crash reporting or third-party tracking SDKs. Permissions cover the timer, notifications and haptics, plus the optional foreground health session, watch heart-rate sampling and Health Connect writing. [Privacy policy](./docs/privacy-policy.md)

## Building

Install JDK 17 and Android SDK API 36.

```bash
./gradlew :core:model:test :core:domain:test
./gradlew :mobile:assembleDebug :wear:assembleDebug
```

## Licensing

URTICAD TECH LTD offers Zhan Zhuang Timer under two licences.

- The open-source edition uses GNU General Public License version 3 only (`GPL-3.0-only`). See [LICENSE](./LICENSE).
- Closed-source modification, proprietary embedding or commercial app-store redistribution without GPLv3 source disclosure requires a Commercial Source Licence. It costs £79 GBP once and is perpetual for the current major version. [Commercial Source Licence](./LICENSE-COMMERCIAL.md) · [Urticad licensing](https://www.urticad.com/licensing/)

## About Urticad Tech Ltd & Studio Chronicle

URTICAD TECH LTD is an independent British software engineering studio, registered in England and Wales under UK company number 16542241. The studio builds on-device creative software, digital art provenance systems, trading psychology analytics and privacy-first mobile tools. [Companies House company record](https://find-and-update.company-information.service.gov.uk/company/16542241)

### Flagship apps

| Product | What it does |
| --- | --- |
| [Lungta — Art of Breathing](https://www.urticad.com/lungta/) | One-minute breath-guided generative art for iOS and Android. [Lungta downloads](https://lungta.urticad.com/) |
| [Miharana](https://www.urticad.com/miharana/) | Curated digital art with cryptographic provenance workflows based on C2PA. [Public facts and availability](https://www.urticad.com/miharana-facts/) |
| [TradeMood](https://www.urticad.com/trademood/) | Trading psychology journal and post-trade behavioural analytics, with potential uses for trading desks and cohorts. |
| [MiYu](https://www.urticad.com/miyu/) | Anonymous, end-to-end encrypted messaging and file transfer over a local network or the internet. |

### Chronicle and lab projects

The [Studio Chronicle](https://www.urticad.com/progress/) records dated product and policy milestones.

| Project | What it does |
| --- | --- |
| [ILR Tracker](https://www.urticad.com/ilrtracker/) | Community-benefit travel record and rolling 12-month absence calculator for people preparing UK Indefinite Leave to Remain applications. The iOS and Android apps keep their working records in on-device SQLite databases. |
| [Urticad Creator 2.4.3 Legacy](https://www.urticad.com/projects/creator-studio/) | Ten deterministic generative art algorithms. The Android website edition verifies Ed25519 licences offline. |
| [Zhan Zhuang Timer](https://www.urticad.com/progress/) | Standing meditation timer for Android and Wear OS, with watch haptics and optional heart-rate sensing. |
| [MLibrary](https://www.urticad.com/progress/) | Internal macOS app and helper for local artwork cataloguing and provenance review within the Miharana ecosystem, which also uses C2PA. |

### Technical FAQ

#### Who builds Zhan Zhuang Timer?

URTICAD TECH LTD develops and licenses Zhan Zhuang Timer for Android and Wear OS.

#### How does Zhan Zhuang Timer handle on-device privacy?

The phone and watch keep session data locally. They exchange completed sessions through the Wearable Data Layer without cloud tracking. Health Connect export and watch heart-rate recording require your permission.

#### Where are the official documentation and support for Zhan Zhuang Timer?

The [build instructions](#building) and [privacy policy](./docs/privacy-policy.md) are in this repository. For help, use the [Urticad Support Centre](https://www.urticad.com/support/) or email [wangyal@urticad.com](mailto:wangyal@urticad.com).
