# Zhan Zhuang 0.1.3 Taiji Icon Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship phone and Wear internal builds whose launcher and Play listing consistently show the approved gold taiji held by the standing figure.

**Architecture:** Keep the icon deterministic and native: Android adaptive foreground/monochrome VectorDrawables for both modules, one canonical SVG for Play assets, and generated 512 px RGBA locale copies. Preserve one package ID with distinct phone and Wear version-code ranges, and upload only to their existing internal tracks.

**Tech Stack:** Android VectorDrawable, adaptive icons, SVG/PNG, Kotlin/JVM resource contract tests, Gradle, Fastlane Supply, Android Publisher readback.

## Global Constraints

- Application ID remains `app.zhanzhuang.timer`.
- Version is `0.1.3`; phone code is `1000004`; Wear code is `2000004`.
- Palette remains deep soil `#15110B` / `#241C12`, reflective gold `#C6A867`, deep gold `#A9863F`, gold highlight `#E7D5A6`, gold sheen `#D8C18C`, and sparkle `#FFF4D6`.
- Phone and Wear foreground assets must be structurally identical.
- The held mark is a gold-and-soil taiji with two contrasting dots, not an open circle.
- Publish only to `internal` and `wear:internal`; production stays unchanged.

---

### Task 1: Unify the launcher identity

**Files:**
- Modify: `mobile/src/main/res/drawable/ic_launcher_foreground.xml`
- Modify: `mobile/src/main/res/drawable/ic_launcher_monochrome.xml`
- Modify: `wear/src/main/res/drawable/ic_launcher_foreground.xml`
- Modify: `wear/src/main/res/drawable/ic_launcher_monochrome.xml`
- Modify: `fastlane/assets-source/icon.svg`
- Modify: `fastlane/metadata/android/{en-GB,en-US,zh-CN}/images/icon.png`
- Test: `mobile/src/test/kotlin/app/zhanzhuang/timer/mobile/ui/ResourceIdentityContractTest.kt`
- Test: `wear/src/test/kotlin/app/zhanzhuang/timer/wear/ResourceIdentityContractTest.kt`

**Interfaces:**
- Consumes: approved Urticad earth/gold palette and current holding-ball figure geometry.
- Produces: named `gold_taiji`, `taiji_soil_half`, `taiji_gold_dot`, and `taiji_soil_dot` vector parts plus canonical SVG equivalents.

- [x] **Step 1: Add failing resource assertions for the taiji parts in both modules.**
- [x] **Step 2: Run both resource contract tests and observe failure at the new assertions.**
- [x] **Step 3: Add the gold taiji to phone, Wear, monochrome, and SVG assets.**
- [x] **Step 4: Regenerate the three 512 px RGBA Play icons and visually inspect the canonical preview.**
- [x] **Step 5: Re-run resource and release-image contract tests and observe success.**

### Task 2: Assign immutable 0.1.3 release identity

**Files:**
- Modify: `mobile/build.gradle.kts`
- Modify: `wear/build.gradle.kts`
- Modify: `scripts/run_android_checks.sh`
- Modify: `scripts/verify_release.sh`
- Modify: `scripts/tests/release_evidence_contract_test.sh`
- Modify: `scripts/tests/verify_release_contract_test.sh`
- Create: `fastlane/metadata/android/{en-GB,en-US,zh-CN}/changelogs/1000004.txt`
- Create: `fastlane/metadata/android/{en-GB,en-US,zh-CN}/changelogs/2000004.txt`

**Interfaces:**
- Consumes: completed 0.1.2 internal tracks at codes `1000003` and `2000003`.
- Produces: version `0.1.3`, phone code `1000004`, Wear code `2000004`.

- [x] **Step 1: Change release-contract expectations to 0.1.3 / 1000004 / 2000004 and verify RED.**
- [x] **Step 2: Update Gradle and verifier identities.**
- [x] **Step 3: Add concise localized changelogs describing the unified gold taiji icon.**
- [x] **Step 4: Run both release-contract scripts and verify GREEN.**

### Task 3: Verify and sign both release bundles

**Files:**
- Verify: `mobile/build/outputs/bundle/release/mobile-release.aab`
- Verify: `wear/build/outputs/bundle/release/wear-release.aab`
- Modify: `docs/release/internal-release-evidence.md`

**Interfaces:**
- Consumes: repo-external keystore values from 1Password and versioned source.
- Produces: signed phone and Wear AABs with verified manifests, certificate fingerprint, byte sizes, and SHA-256 hashes.

- [x] **Step 1: Run `scripts/run_android_checks.sh` with explicit Java 17 and Android SDK paths.**
- [x] **Step 2: Build signed release bundles with repo-external signing secrets.**
- [x] **Step 3: Run `scripts/verify_release.sh` against the signed AABs.**
- [x] **Step 4: Record artifact hashes and automated test evidence.**

### Task 4: Upload and read back internal tracks

**Files:**
- Modify: `docs/release/internal-release-evidence.md`
- Modify: `docs/qa/pixel-watch-4-results.md`

**Interfaces:**
- Consumes: signed 0.1.3 AABs and repo-external Google Play service-account JSON.
- Produces: completed `internal=1000004` and `wear:internal=2000004` Play releases, with production unchanged.

- [x] **Step 1: Read current phone, Wear, and production track state.**
- [x] **Step 2: Upload phone AAB to `internal` with release status `completed`.**
- [x] **Step 3: Upload Wear AAB to `wear:internal` with release status `completed`.**
- [x] **Step 4: Read back both tracks and confirm production remains 0.1.0.**

### Task 5: Complete physical-device proof when ADB is available

**Files:**
- Modify: `docs/qa/pixel-watch-4-results.md`

**Interfaces:**
- Consumes: Pixel phone and Pixel Watch 4 with 0.1.3 installed.
- Produces: launcher-icon, cold-launch, watch-start, phone reflection, pause/resume, completion, reconnect, and duplicate-prevention results.

- [x] **Step 1: Check whether Mac enumerates the phone or watch with `adb devices -l`.**
- [ ] **Step 2: Install the matching build and verify the gold taiji launcher on phone and watch.**
- [ ] **Step 3: Run the paired session matrix and collect anonymized evidence.**
- [ ] **Step 4: Record Pass/Fail/Not run without treating missing ADB as a product pass.**
