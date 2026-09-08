# Pixel 10 Pro physical QA — 0.1.1

- Collected at (UTC): 2026-08-02T23:59:14Z
- Device class: physical phone/tablet (model name omitted from automated evidence)
- OS / API: Android 17 / API 37
- Package tested: `app.zhanzhuang.timer.debug`
- Installed version: `0.1.1`
- Privacy: raw ADB serials and health samples were neither recorded nor persisted.

## Results

| Scenario | Result | Evidence |
| --- | --- | --- |
| Cold launch | Pass | Activity cold-launched successfully; no crash or ANR was present in filtered logcat. |
| API 37 test compatibility | Pass | AndroidX Test 1.7.0 / Espresso 3.7.0 removed the obsolete reflective `InputManager.getInstance()` failure. |
| Installed UI and service flow | Pass | Six Compose instrumentation tests passed on the physical device. |
| Consecutive phone sessions | Pass | A completed record survived Activity recreation; the pinned start action was visible; a distinct second active record was created without replacing history. |
| Large-font primary action | Pass | The primary start action remained displayed in the instrumentation accessibility case. |
| Light-theme system bars | Pass | The warm-paper screen used dark status-bar icons, including while the phone system theme was dark. |
| Notification permission | Pass | A fresh Android 13+ permission state produced the system notification prompt only after the user pressed Start. |
| Granted notification | Pass | `POST_NOTIFICATIONS` became granted and Notification Manager contained the ongoing, non-clearable foreground notification with ID `1001`. |
| Foreground timer | Pass | `MobileSessionService` reported `isForeground=true`, ID `1001`, and type `specialUse (0x40000000)` while the UI reported `Standing`. |
| Completion cleanup | Pass | The physical test session reached `Completed`; the foreground service was no longer observed afterward. |

## Boundaries

This evidence does not substitute for Pixel Watch 4 ADB testing, physical
haptics, heart-rate samples, paired Data Layer commands, screen-off timing,
process-death recovery, reboot, or the 15/30/180-minute duration matrix. The
connected ADB target in this run was a phone, not a watch.
