# Two-Circle Standing Icon Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship `0.1.6` to phone and Wear internal testing with a two-circle standing launcher icon that retains the gold rim and uses parallel legs.

**Architecture:** A canonical SVG produces the three 512 px Play PNG source assets; equivalent Android VectorDrawables drive the phone and Wear adaptive and monochrome launcher icons. New binary-only Fastlane lanes ensure the test upload does not change production-review listing metadata.

**Tech Stack:** Android VectorDrawable, adaptive icons, SVG/PNG via `sips`, Kotlin/JVM resource tests, Gradle, Fastlane Supply, Android Publisher edits API.

## Global Constraints

- Package remains `app.zhanzhuang.timer`, SDK stays `36`, Java toolchain stays `17`.
- Phone is `0.1.6` / `1000006`; Wear is `0.1.6` / `2000007`.
- Palette remains `#15110B`, `#241C12`, rim `#A9863F`, gold `#C6A867`, and highlight `#E7D5A6`.
- Keep the thin gold outer rim. Remove sparkle, separate arms, shoulder/chest/torso lines, and diagonal legs.
- Primary figure geometry is exactly a hollow head ring, a large gold-and-soil taiji torso, and two equal vertical rounded legs. Taiji dots are internal detail, not extra primary circles.
- Phone and Wear icon geometry must match. Internal tracks only; production and global listing metadata stay untouched.
- Do not run Gradle `clean`; signing and Google credentials remain outside the repository.

---

### Task 1: Audit the Android baseline before mutation

**Files:**
- Verify: `settings.gradle.kts`, `mobile/build.gradle.kts`, `wear/build.gradle.kts`
- Verify: `mobile/src/main/AndroidManifest.xml`, `wear/src/main/AndroidManifest.xml`
- Verify: `scripts/run_android_checks.sh`, `scripts/verify_release.sh`, `fastlane/Fastfile`
- Modify: `docs/release/internal-release-evidence.md`

**Interfaces:**
- Consumes: latest Android guidance and the approved icon specification.
- Produces: a dated audit result covering SDK, adaptive-icon manifest wiring, permissions, signing, build checks, and track isolation.

- [ ] **Step 1: Read Android guidance and project identity.**

```bash
sed -n '1,180p' /Users/pema/.codex/skills/android/SKILL.md
sed -n '1,110p' mobile/build.gradle.kts
sed -n '1,110p' wear/build.gradle.kts
```

Expected: two application modules, SDK 36, Java 17, and no dependency or migration change required for this resource-only release.

- [ ] **Step 2: Inspect manifests and the current upload lanes.**

```bash
rg -n "android:icon|android:roundIcon|uses-permission|foregroundServiceType" mobile/src/main/AndroidManifest.xml wear/src/main/AndroidManifest.xml
sed -n '1,120p' fastlane/Fastfile
```

Expected: both modules resolve `@mipmap/ic_launcher`; the current mobile internal lane uploads metadata and must not be used unchanged.

- [ ] **Step 3: Run the existing no-clean Android gate.**

```bash
scripts/run_android_checks.sh
```

Expected: baseline pass before icon edits. Record failures before proceeding; repair only a release-relevant cause.

- [ ] **Step 4: Add the dated audit paragraph and commit it.**

Record observed SDK, launcher wiring, permission contract, and the need for a binary-only mobile lane in `docs/release/internal-release-evidence.md`.

```bash
git add docs/release/internal-release-evidence.md
git commit -m "docs: audit Android 0.1.6 icon baseline"
```

### Task 2: Establish failing icon resource contracts

**Files:**
- Modify: `mobile/src/test/kotlin/app/zhanzhuang/timer/mobile/ui/ResourceIdentityContractTest.kt`
- Modify: `wear/src/test/kotlin/app/zhanzhuang/timer/wear/ResourceIdentityContractTest.kt`

**Interfaces:**
- Consumes: current `adaptiveIconContractIsPresent()` tests.
- Produces: assertions that reject the old arms, sparkle, torso stroke, and splayed-leg geometry.

- [ ] **Step 1: Replace arm-based assertions in both resource tests.**

Insert these exact foreground assertions and remove all assertions for holding arms, sparkle, or diagonal legs:

```kotlin
assertTrue(foreground.contains("android:name=\"head_ring\""))
assertTrue(foreground.contains("android:name=\"standing_taiji\""))
assertTrue(foreground.contains("android:name=\"left_parallel_leg\""))
assertTrue(foreground.contains("android:name=\"right_parallel_leg\""))
assertTrue(foreground.contains("M46.5,66L46.5,82"))
assertTrue(foreground.contains("M61.5,66L61.5,82"))
assertFalse(foreground.contains("left_holding_arm"))
assertFalse(foreground.contains("right_holding_arm"))
assertFalse(foreground.contains("80.79,23.84"))
assertFalse(foreground.contains("M54,61.59L43.24,81.21"))
```

Require SVG ids `two-circle-standing-figure`, `head-ring`, `standing-taiji`, `left-parallel-leg`, and `right-parallel-leg`; reject `left-holding-arm`, `right-holding-arm`, and `id="sparkle"`.

- [ ] **Step 2: Verify the assertions fail on the old asset.**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools ./gradlew :mobile:testDebugUnitTest --tests app.zhanzhuang.timer.mobile.ui.ResourceIdentityContractTest
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools ./gradlew :wear:testDebugUnitTest --tests app.zhanzhuang.timer.wear.ResourceIdentityContractTest
```

Expected: both fail because old resources still contain arms, sparkle, and splayed legs.

- [ ] **Step 3: Commit the failing contracts.**

```bash
git add mobile/src/test/kotlin/app/zhanzhuang/timer/mobile/ui/ResourceIdentityContractTest.kt wear/src/test/kotlin/app/zhanzhuang/timer/wear/ResourceIdentityContractTest.kt
git commit -m "test: define two-circle launcher icon contract"
```

### Task 3: Implement deterministic SVG and Android icon resources

**Files:**
- Modify: `fastlane/assets-source/icon.svg`, `fastlane/assets-source/ALT_TEXT.md`
- Modify: `mobile/src/main/res/drawable/ic_launcher_foreground.xml`, `mobile/src/main/res/drawable/ic_launcher_monochrome.xml`
- Modify: `wear/src/main/res/drawable/ic_launcher_foreground.xml`, `wear/src/main/res/drawable/ic_launcher_monochrome.xml`
- Modify: `fastlane/metadata/android/{en-GB,en-US,zh-CN}/images/icon.png`

**Interfaces:**
- Consumes: Task 2 contract markers.
- Produces: one canonical 512 px Play icon and matching 108 dp adaptive/monochrome assets.

- [ ] **Step 1: Replace the SVG figure body with the approved two-circle geometry.**

Keep the existing deep-soil background and `<circle cx="256" cy="256" r="224" fill="#241C12" stroke="#A9863F" stroke-width="5"/>` rim. Replace the old figure with:

```svg
<g id="two-circle-standing-figure" fill="none" stroke-linecap="round" stroke-linejoin="round">
  <circle id="head-ring" cx="256" cy="146" r="28" stroke="#E7D5A6" stroke-width="18"/>
  <g id="standing-taiji" stroke="none">
    <circle id="taiji-disc" cx="256" cy="266" r="66" fill="#C6A867"/>
    <path id="taiji-soil-half" d="M256 200A66 66 0 0 1 256 332A33 33 0 0 1 256 266A33 33 0 0 0 256 200Z" fill="#241C12"/>
    <circle id="taiji-soil-dot" cx="256" cy="233" r="8.5" fill="#241C12"/>
    <circle id="taiji-gold-dot" cx="256" cy="299" r="8.5" fill="#E7D5A6"/>
  </g>
  <path id="left-parallel-leg" d="M232 322L232 392" stroke="#C6A867" stroke-width="24"/>
  <path id="right-parallel-leg" d="M280 322L280 392" stroke="#C6A867" stroke-width="24"/>
</g>
```

- [ ] **Step 2: Translate the same geometry to each foreground VectorDrawable.**

After the existing soil disc/rim, use this exact 108 dp content in both foreground files:

```xml
<path android:name="head_ring" android:fillColor="#00000000" android:strokeColor="#E7D5A6" android:strokeWidth="3.8" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M54,24.05A5.91,5.91 0,1 1,54,35.87A5.91,5.91 0,1 1,54,24.05"/>
<group android:name="standing_taiji"><path android:name="taiji_gold_disc" android:fillColor="#C6A867" android:pathData="M54,42.19A13.92,13.92 0,1 1,54,70.03A13.92,13.92 0,1 1,54,42.19"/><path android:name="taiji_soil_half" android:fillColor="#241C12" android:pathData="M54,42.19A13.92,13.92 0,0 1,54,70.03A6.96,6.96 0,0 1,54,56.11A6.96,6.96 0,0 0,54,42.19Z"/><path android:name="taiji_soil_dot" android:fillColor="#241C12" android:pathData="M54,47.23A1.79,1.79 0,1 1,54,50.81A1.79,1.79 0,1 1,54,47.23"/><path android:name="taiji_gold_dot" android:fillColor="#E7D5A6" android:pathData="M54,61.41A1.79,1.79 0,1 1,54,64.99A1.79,1.79 0,1 1,54,61.41"/></group>
<path android:name="left_parallel_leg" android:fillColor="#00000000" android:strokeColor="#C6A867" android:strokeWidth="5.06" android:strokeLineCap="round" android:pathData="M46.5,66L46.5,82"/>
<path android:name="right_parallel_leg" android:fillColor="#00000000" android:strokeColor="#C6A867" android:strokeWidth="5.06" android:strokeLineCap="round" android:pathData="M61.5,66L61.5,82"/>
```

In monochrome vectors, keep the same named head, taiji, and parallel-leg topology in white; omit rim colour, arms, and sparkle.

- [ ] **Step 3: Render and copy the canonical PNG.**

```bash
sips -s format png fastlane/assets-source/icon.svg --out /tmp/zhanzhuang-two-circle-icon.png
sips -z 512 512 /tmp/zhanzhuang-two-circle-icon.png --out fastlane/metadata/android/en-GB/images/icon.png
cp fastlane/metadata/android/en-GB/images/icon.png fastlane/metadata/android/en-US/images/icon.png
cp fastlane/metadata/android/en-GB/images/icon.png fastlane/metadata/android/zh-CN/images/icon.png
shasum -a 256 fastlane/metadata/android/en-GB/images/icon.png fastlane/metadata/android/en-US/images/icon.png fastlane/metadata/android/zh-CN/images/icon.png
```

Expected: three identical 512×512 RGBA files. Replace the icon line in `ALT_TEXT.md` with: `gold-rimmed soil disc, hollow gold head ring, large gold-and-soil taiji torso, and two parallel gold legs; no arms or sparkle.`

- [ ] **Step 4: Verify GREEN and commit.**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools ./gradlew :mobile:testDebugUnitTest --tests app.zhanzhuang.timer.mobile.ui.ResourceIdentityContractTest
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools ./gradlew :wear:testDebugUnitTest --tests app.zhanzhuang.timer.wear.ResourceIdentityContractTest
bash scripts/tests/verify_release_contract_test.sh
git add fastlane/assets-source mobile/src/main/res/drawable wear/src/main/res/drawable fastlane/metadata/android/en-GB/images/icon.png fastlane/metadata/android/en-US/images/icon.png fastlane/metadata/android/zh-CN/images/icon.png
git commit -m "feat: simplify launcher to two-circle standing icon"
```

Expected: tests pass and no old arm/sparkle path remains.

### Task 4: Assign 0.1.6 identity and add metadata-safe internal upload

**Files:**
- Modify: `mobile/build.gradle.kts`, `wear/build.gradle.kts`
- Modify: `scripts/run_android_checks.sh`, `scripts/verify_release.sh`
- Modify: `scripts/tests/release_evidence_contract_test.sh`, `scripts/tests/verify_release_contract_test.sh`
- Modify: `fastlane/Fastfile`
- Create: `fastlane/metadata/android/{en-GB,en-US,zh-CN}/changelogs/{1000006,2000007}.txt`

**Interfaces:**
- Consumes: completed internal codes `1000005` and `2000006`.
- Produces: immutable phone `0.1.6/1000006` and Wear `0.1.6/2000007` bundles with binary-only internal lanes.

- [ ] **Step 1: Update Gradle and verifier version constants.**

```kotlin
// mobile/build.gradle.kts
versionCode = 1_000_006
versionName = "0.1.6"
// wear/build.gradle.kts
versionCode = 2_000_007
versionName = "0.1.6"
```

Use `inspect_apk mobile "$mobile_apk" 28 1000006 0.1.6` and `inspect_apk wear "$wear_apk" 33 2000007 0.1.6` in `scripts/run_android_checks.sh`. Require `changelogs/1000006.txt` and `changelogs/2000007.txt` in `scripts/verify_release.sh`; update the matching shell-test fixtures.

- [ ] **Step 2: Add each locale's changelogs.**

```text
en-GB/en-US phone: Simplified launcher icon with a two-circle standing figure and parallel stance.
en-GB/en-US Wear: Simplified watch launcher icon with a two-circle standing figure and parallel stance.
zh-CN phone: 启动图标更新为双圆站桩形象与平行站姿。
zh-CN Wear: 手表启动图标更新为双圆站桩形象与平行站姿。
```

- [ ] **Step 3: Add the safe phone upload lane.**

Append this lane to `fastlane/Fastfile` and assert its name in the release verifier and shell test:

```ruby
desc "Upload only the verified mobile AAB to internal testing"
lane :upload_mobile_internal_binary_only do
  upload_to_play_store(
    json_key: supply_json_key!, package_name: "app.zhanzhuang.timer",
    aab: "mobile/build/outputs/bundle/release/mobile-release.aab",
    track: "internal", release_status: "completed",
    skip_upload_metadata: true, skip_upload_images: true,
    skip_upload_screenshots: true, skip_upload_changelogs: true
  )
end
```

- [ ] **Step 4: Run release contracts and commit.**

```bash
bash scripts/tests/release_evidence_contract_test.sh
bash scripts/tests/verify_release_contract_test.sh
git add mobile/build.gradle.kts wear/build.gradle.kts scripts fastlane/Fastfile fastlane/metadata/android
git commit -m "chore: prepare 0.1.6 two-circle internal release"
```

Expected: both contract scripts pass and the new lane cannot upload listing metadata.

### Task 5: Build, sign, upload, and read back only internal tracks

**Files:**
- Verify: `mobile/build/outputs/bundle/release/mobile-release.aab`
- Verify: `wear/build/outputs/bundle/release/wear-release.aab`
- Modify: `docs/release/internal-release-evidence.md`, `docs/qa/pixel-watch-4-results.md`

**Interfaces:**
- Consumes: Tasks 1–4, external signing credentials, and external `SUPPLY_JSON_KEY`.
- Produces: completed internal releases `1000006` and `2000007` without production change.

- [ ] **Step 1: Run complete local gates.**

```bash
scripts/run_android_checks.sh
scripts/verify_release.sh
shasum -a 256 mobile/build/outputs/bundle/release/mobile-release.aab wear/build/outputs/bundle/release/wear-release.aab
```

Expected: tests, lint, debug APKs, signed AABs, manifests, permissions, launcher resources, certificates, and PNG contract pass.

- [ ] **Step 2: Read current tracks sequentially.**

Use one Android Publisher edits readback over `internal`, `wear:internal`, `production`, and `wear:production`. Expected pre-upload codes: `1000005`, `2000006`, `1000005`, and `2000006`.

- [ ] **Step 3: Upload only the two internal binaries.**

```bash
SUPPLY_JSON_KEY=/Users/pema/.config/miharana/google-play/miharana-fastlane-supply.json bundle exec fastlane android upload_mobile_internal_binary_only
SUPPLY_JSON_KEY=/Users/pema/.config/miharana/google-play/miharana-fastlane-supply.json bundle exec fastlane android upload_wear_internal
```

Expected: no metadata, image, screenshot, changelog, production, or review mutation.

- [ ] **Step 4: Read back exact post-upload state.**

Expected:

```text
internal|0.1.6|completed|1000006
wear:internal|0.1.6|completed|2000007
production|0.1.4|completed|1000005
wear:production|0.1.5|completed|2000006
```

- [ ] **Step 5: Attempt device validation truthfully and commit evidence.**

```bash
adb devices -l
```

If authorized devices exist, install through the matching Play internal links or ADB and verify launcher rendering and version. Otherwise record `Not run — no authorized ADB target`; never report a device pass without a connected device.

Record source commit, artifact hashes, build gates, exact remote readback, production non-mutation, and device result in both evidence documents.

```bash
git add docs/release/internal-release-evidence.md docs/qa/pixel-watch-4-results.md
git commit -m "docs: record 0.1.6 internal release"
git status --short
```

Expected: clean worktree after evidence commit.
