# Google Play release evidence

Last updated: 2026-08-31 (UTC)

## Android Developer account identity

The only authorized Android Developer / Google Play Console account for this
app is managed under `Urticad Tech Ltd` (developer ID
`7258731265136907307`). Do not use the Play test account as the developer
account.

## 0.1.11 mobile + 0.1.12 Wear broken-functionality crash resubmission

Google Play rejected Wear version code `2000012` on 2026-08-30 under
**Broken Functionality policy**. The Play issue details identify **Crashes for
users** and say that the app opens but keeps crashing; the attached review
capture shows the system-level “Zhan Zhuang keeps stopping” dialog. The Gmail
inbox has not yet received a separate 2000012 message, so the Play Console
issue record and its attached capture are the authoritative evidence.

Code review found that a fresh Wear launch unconditionally started
`ACTION_STATUS` through `startForegroundService()` even when no session
snapshot existed. That unnecessarily entered the Room/Health Services/
foreground-notification path before the setup screen. The fix adds one shared
recovery policy: a fresh install skips status recovery, an active snapshot is
allowed to use the foreground service, and a terminal snapshot is republished
without foreground promotion. Boot recovery and the service entry point apply
the same fail-closed guard.

The compliance review also found that mobile Health Connect writes could be
scheduled by `HealthSyncWorker` after runtime permission had been granted
without a separately persisted in-app disclosure acceptance. The mobile fix
adds a prominent Settings disclosure naming completed sessions and optional
heart-rate samples, requires explicit acceptance immediately before the system
permission request, persists that acceptance, and blocks both gateway writes and
background reconciliation until the acceptance and runtime permission are
available.

The follow-up Play Policy Insights audit on 2026-08-31 found no Critical or
Important code-compliance findings. The Health Connect data-flow findings were
pruned after verifying the affirmative disclosure gate and both write-boundary
checks. The only remaining item is the routine Play Console confirmation that
the mobile `specialUse` and Wear `health|specialUse` foreground-service
declarations describe the timer and include the required reviewer evidence.

| Surface | Version code | Version name | Candidate | Bytes | SHA-256 |
| --- | ---: | --- | --- | ---: | --- |
| Phones/tablets | `1000011` | `0.1.11` | `mobile/build/outputs/bundle/release/mobile-release.aab` | `10728302` | `87c504798e77ed0ee4007f280ce14a9b8811ca284ce36e1145b79e2518ba85c1` |
| Wear OS | `2000013` | `0.1.12` | `wear/build/outputs/bundle/release/wear-release.aab` | `10280590` | `4ab1218bd894ad5949f25cd5004448404d4c0db740c8fa08cb2f55141f248545` |

Verification completed before upload:

- Wear recovery-policy regression tests cover fresh, active, terminal, and
  malformed snapshots.
- Mobile Health Connect consent tests cover default denial, persisted
  acceptance, ViewModel permission gating, and background-worker no-write
  behavior before acceptance.
- Full Android unit tests, Wear unit tests, debug lint/builds, release lint,
  signed AAB verification, manifest/resource/permission contracts, and release
  contract tests passed.
- API 36 Wear cold launch after data clear succeeded with the signed `2000013`
  APK; the process remained alive, no `WearSessionService` was started, and no
  fatal exception, security exception, or system crash dialog was observed.
- API 33 mobile cold launch after data clear succeeded with `1000011` and
  the process remained alive without fatal, security, or system crash output.
- The andship lanes uploaded mobile `1000011` to `internal`, promoted it to
  `production`, and retained Wear `2000013` on both Wear tracks with
  `changes_not_sent_for_review=true`.

Android Publisher readback after the upload and promotion returned:

- `internal|completed|1000011`
- `production|completed|1000011`
- `wear:internal|completed|2000013`
- `wear:production|completed|2000013`

On 2026-08-31, Codex Browser reconfirmed the authorized
authorized owner session under `Urticad Tech Ltd`. Publishing overview
prepared **15 changes for review**, including phone `0.1.11`, Wear `0.1.12`,
and the existing listing/App content changes. After the owner's explicit
confirmation, the final **Send changes for review** action completed and the
page now shows **Changes in review** for both production surfaces. Play's
automated quick checks continue in the background and will forward the changes
to review when they complete successfully.

## 0.1.10 / 0.1.11 Wear splash-screen policy resubmission

Google Play Support rejected Wear version code `2000011` on 2026-08-25 for
**Wear App Quality Guidelines: Missing app icon in splash screen**. The notice
specified that app startup must show a 48x48dp app icon on a black background.

The Wear launch path now uses AndroidX Core Splashscreen `1.2.0`, a black
starting theme, a centered 48dp layer-list icon that reuses the launcher
identity, and `installSplashScreen()` before the activity is created. The
mobile candidate was advanced alongside the Wear fix so the Android release
uses one coordinated review submission.

| Surface | Version code | Version name | Candidate | Bytes | SHA-256 |
| --- | ---: | --- | --- | ---: | --- |
| Phones/tablets | `1000010` | `0.1.10` | `mobile/build/outputs/bundle/release/mobile-release.aab` | `10723057` | `1b2169b06041f4f71033f16af3a1af8734f7a0839c10fe1cf91856d31aa7b0a5` |
| Wear OS | `2000012` | `0.1.11` | `wear/build/outputs/bundle/release/wear-release.aab` | `10279695` | `7e23f3976502d703f5d97351761be3c839104d3d8dd40653073b60c58cc4dc75` |

The signed release verifier, JVM tests, release lint, AAB manifest/resource
checks, signing-certificate check, and release contract tests passed. The
andship lanes uploaded the mobile AAB to `internal`, the Wear AAB to
`wear:internal`, and promoted both candidates to their corresponding
production tracks with `changesNotSentForReview=true`.

Android Publisher readback after the upload and promotion returned:

- `internal|completed|1000010`
- `production|completed|1000010`
- `wear:internal|completed|2000012`
- `wear:production|completed|2000012`

On 2026-08-25, Codex Browser confirmed the authorized owner session
session under `Urticad Tech Ltd`. Play Console Submission `15` was submitted
at `Aug 25, 2026, 4:52 pm` and is **In review**, containing both phone
Production `0.1.10` and Wear Production `0.1.11` plus the associated listing,
App content, advanced distribution, and store-setting changes.

Post-submission checks show Policy status **Update in review**. App content
has no declarations under **Need attention**; all 11 declarations, including
Data safety, Health apps, foreground-service permissions, Content ratings,
Ads, Privacy policy, and Target audience, are actioned and currently **In
review**. The visible “Missing app icon in splash screen” entry is the prior
rejected submission record, not a new issue.

## 0.1.10 Wear default-font clipping correction

Google Play Support rejected Wear version `2000010` on 2026-08-18 under
**Wear app functionality not working as described**: the reviewer found text
cut off at the default font size. The earlier reviewer captures
`IN_APP_EXPERIENCE-8439.png` (setup) and `IN_APP_EXPERIENCE-3089.png`
(end-confirmation) show the affected round-screen edge layout.

The correction applies round-safe horizontal insets, `transformedHeight`, and
`SurfaceTransformation` to every user-visible Wear text path; adds inner
horizontal room and centered wrapping to custom actions; reduces the setup
heading padding so the primary action remains fully visible; and adds top/bottom
scroll safety space so first and last controls can be brought fully into view.
The Wear UI tests now scroll to action items before interacting with them rather
than assuming uncomposed lazy-list items are present.

Verification completed on the task-created 454px Wear OS 4 round emulator
(Android 13/API 33): all 5 Wear instrumentation tests passed, including setup,
end confirmation, cancellation, and the real foreground-service pause/resume/
finish/persistence flow. The no-clean Android checks, release lint, signed AAB
integrity, upload-certificate, version, form-factor, and forbidden-permission
contracts also passed.

| Surface | Version code | Candidate | Bytes | SHA-256 |
| --- | ---: | --- | ---: | --- |
| Phones/tablets (unchanged) | `1000009` | `mobile/build/outputs/bundle/release/mobile-release.aab` | `10722636` | `7047ae52147e1132f3e5c8c17523614bdc24aef18cf1bbc26c1cbadb50ccbc7d` |
| Wear OS | `2000011` | `wear/build/outputs/bundle/release/wear-release.aab` | `10221990` | `4a76d55793ab44463e29971d8f03ac71d247440dfb2493965bbda88a2445ed57` |

The andship release lane (Fastlane supply backend) successfully uploaded Wear
`2000011` to `wear:internal` and promoted it to `wear:production` with
completed release status. Sequential Publisher readback confirmed
`wear:production` contains version code `2000011`; promotion
superseded/deactivated the internal origin release. A later local andship
release verification rebuilt both signed candidates and passed all checks; it
was not uploaded again because version code `2000011` is already immutable on
the production track.

On 2026-08-18, Codex Browser confirmed the authorized Play Console session as
the owner account under `Urticad Tech Ltd`. Publishing overview showed
`15` changes, including phone Production `0.1.9` and Wear Production `0.1.10`.
After explicit confirmation, **Submit 15 changes for review** was completed;
the page now shows **Changes in review** and confirms that the changes are now
in review. The submit button is no longer present.

The automated Play Policy Insights scan found no critical or important code
violation. It identified administrative checks for mobile `specialUse` and
Wear `health|specialUse` foreground-service declarations, and confirmed the
Health Connect/heart-rate path is permission-gated and disclosed. These App
content declarations must remain aligned in Play Console.

## 0.1.9 broken-functionality fix release

The previous `0.1.8` / `1000008` phone and `0.1.8` / `2000009` Wear releases
were already present on all four Play tracks. Because Google Play version
codes are immutable, the reliability fixes use the next release identity:
`0.1.9`, phone/tablet code `1000009`, and Wear OS code `2000010`.

The phone flow now prevents duplicate session actions, surfaces start and
control failures, treats failed watch delivery as a real error, and falls back
to a local phone-owned session when the watch transport is unavailable. The
release was built from branch `codex/zhanzhuang-0.1.3-taiji-icon` at baseline
`7a7971d`, plus the verified working-tree fixes.

| Surface | Version code | Candidate | Bytes | SHA-256 |
| --- | ---: | --- | ---: | --- |
| Phones/tablets | `1000009` | `mobile/build/outputs/bundle/release/mobile-release.aab` | `10722636` | `7047ae52147e1132f3e5c8c17523614bdc24aef18cf1bbc26c1cbadb50ccbc7d` |
| Wear OS | `2000010` | `wear/build/outputs/bundle/release/wear-release.aab` | `10218555` | `c07cdeceac9dea9f0323ee3f2b7bf8492a9a983d4cea85572442014309ebf8af` |

The signed release verifier passed tests, release lint, metadata and asset
contracts, strict AAB integrity checks, the approved upload certificate, and
bundle identity checks. The mobile binary-only lane and Wear lane skipped
listing metadata, images, screenshots, and changelogs during upload. Android
Publisher readback after upload and promotion returned:

- `internal|0.1.9|completed|1000009`
- `production|0.1.9|completed|1000009`
- `wear:internal|0.1.9|completed|2000010`
- `wear:production|0.1.9|completed|2000010`

On 2026-08-14, Play Console Publishing overview showed 15 changes not yet
submitted for review, including both `0.1.9` full-rollout changes. The
submission was confirmed in the UI; the overview now shows **Changes in
review** for `Production` and `Production (Wear OS)`. Play's pre-review checks
were still running at the time of submission.

On 2026-08-16, Codex Browser reconfirmed the same account and the Publishing
overview still showed **Changes in review** for both `0.1.9` production changes.

No physical phone or Pixel Watch target was connected during this release;
Play delivery does not substitute for the outstanding physical runtime matrix.

## 0.1.8 E interval timer release candidate

This candidate replaces the queued `0.1.4` phone and `0.1.7` Wear production
changes after they were confirmed in Play Console as **Changes in review**, not
as a formally published release. It uses version name `0.1.8`, version code
`1000008` for phones/tablets, and version code `2000009` for Wear OS.

The candidate unifies the launcher and running visual around the approved E
mark: a gold taiji above two gold rings inside a retained thin gold rim. The
active timer uses that same rim as a one-reminder-interval progress ring. It
also fixes the recovery gap where a phone opened after a watch-originated start
could not query and adopt the already-running Wear-owned session.

Before signing, the no-clean Android gate passed the core/domain and both app
unit suites, mobile and Wear debug lint, debug APK builds, release-manifest
checks, version identity checks, resource checks, and forbidden-permission
checks. The Play icon was regenerated from `fastlane/assets-source/icon.svg`
for `en-GB`, `en-US`, and `zh-CN`. Signed AAB hashes, upload timestamp, and
post-upload Android Publisher track readback are intentionally recorded only
after the signed artifacts and remote commits exist.

## 0.1.7 Wear round-screen policy correction

Google Play's 2026-08-07 policy notice for Wear version `2000006` identified
the **Watch shapes** requirement. Its reviewer screenshots showed a clipped
setup control and a clipped end-confirmation action on a round display.

Wear `0.1.7` uses version code `2000008` and replaces the affected round-screen
paths with Material 3 `AppScaffold` / `ScreenScaffold` and
`TransformingLazyColumn`: setup, optional heart-rate permission, active
session, end confirmation, and completion. All custom actions use the shared
round-safe horizontal inset plus Material 3 surface transformation and minimum
vertical list padding. The old rectangular `ScreenColumn` implementation was
removed so no user-reachable Wear screen can fall back to it.

On the 454px round Wear OS 4 emulator (API 33), final screenshots showed the
setup controls, active-session controls, and all three end-confirmation actions
fully visible. The four direct Wear UI instrumentation interactions passed.
This is emulator evidence, not a claim of physical Pixel Watch 4 completion.

The phone binary remains `0.1.6` / `1000006`; only `wear:internal` and
`wear:production` are to be replaced by `0.1.7` / `2000008`.

| Surface | Version code | Candidate | Bytes | SHA-256 |
| --- | ---: | --- | ---: | --- |
| Wear OS | `2000008` | `wear/build/outputs/bundle/release/wear-release.aab` | `10214542` | `30ea8dad5e0cc6558195391f944c269ad187fc49433bb47928aa247f10ddf77b` |

The signed candidate passed the local release verifier: JVM tests, release
lint, manifest identity and form-factor checks, forbidden-permission checks,
strict archive integrity verification, and the approved upload-certificate
fingerprint check.

Android Publisher readback after upload and promotion returned
`wear:internal|0.1.7|completed|2000008` and
`wear:production|0.1.7|completed|2000008`. On 2026-08-07, Play Console then
confirmed **15 changes sent for review**, including the production Wear `0.1.7`
full rollout. Its automatic quick checks remained in progress at submission;
Google sends the changes to review as soon as those checks complete
successfully.

## 0.1.6 two-circle launcher pre-change Android audit

Before the approved icon-only change, the current Android router guidance and
project source were reviewed. The project remains a two-module native Android
app using Java 17 with `compileSdk`/`targetSdk` 36; no dependency, AGP, or
migration change is required for this release. Phone and Wear manifests both
resolve `android:icon` and `android:roundIcon` through `@mipmap/ic_launcher`.
The existing no-clean Android gate passed on 2026-08-06, covering JVM tests,
debug lint, debug APK builds, release manifests, localized resources, launcher
resource presence, special-use/health foreground-service declarations, and the
no-Internet/no-location contract.

The audit found one release-isolation concern: `upload_mobile_internal` also
uploads listing metadata and images. The 0.1.6 test release will add and use a
binary-only phone internal lane, matching the existing metadata-skipping Wear
lane, so testing the launcher does not alter the current production-review
listing or its remote icon.

## 0.1.6 two-circle launcher internal release

Source commit: `a20416c66307a61a9915e58ddc4a49b790ba8fba`

| Surface | Version code | Candidate | Bytes | SHA-256 |
| --- | ---: | --- | ---: | --- |
| Phones/tablets | `1000006` | `mobile/build/outputs/bundle/release/mobile-release.aab` | `10715443` | `f50ba7964199bed2e5d2f2d8fa3e6151a38eb6e536fa2eede3b69cd72b4724d0` |
| Wear OS | `2000007` | `wear/build/outputs/bundle/release/wear-release.aab` | `10208949` | `9a14c438921cf2f29090709f436e1e4369101ffc60687ab3dc532d94fb05f0ea` |

The launcher now keeps the gold outer rim and uses only a hollow gold head
circle, one larger gold-and-soil taiji circle, and two parallel legs. Phone,
Wear, monochrome, the canonical SVG, and all local listing icons share that
geometry; there are no arms, torso line, sparkles, or split stance.

The no-clean Android quality gate and the signed release verifier passed on
2026-08-06. They cover both app test suites, debug and release lint, debug APK
builds, signed AAB builds, strict JAR integrity, the approved upload
certificate, bundle manifests, SDK/package/version identity, localized assets,
and the no-Internet/no-location contract. The verifier also confirms the exact
candidate hashes above.

After the upload, a local Android 17/API 37 phone emulator ran all six mobile
instrumentation tests successfully against the debug variant. That is
supplementary phone-flow evidence only; it neither installs the Play-signed AAB
nor substitutes for a physical Pixel Watch 4 check.

Both signed AABs were uploaded only to their existing Internal testing tracks.
The phone lane skips store metadata, images, screenshots, and changelogs; the
Wear lane skips the same listing data. Sequential Android Publisher readback
after upload returned `0.1.6|completed|1000006` for `internal` and
`0.1.6|completed|2000007` for `wear:internal`. `production` remained
`0.1.4|completed|1000005`; `wear:production` remained
`0.1.5|completed|2000006`. The production-review listing and remote icon were
not modified by this release.

## Current production review identity

- Developer: **Urticad Tech Ltd** (organisation account)
- Developer ID: `7258731265136907307`
- Play app ID: `4973495334373549508`
- Package: `app.zhanzhuang.timer`
- Version names: phone/tablet `0.1.4`; Wear OS `0.1.5`
- Upload certificate SHA-256:
  `83:FC:DF:02:8E:8B:E3:82:6C:AD:A6:86:76:08:A6:95:9C:77:5F:CA:D6:98:E7:13:FE:02:7A:07:0E:12:7C:81`

| Surface | Version code | Uploaded artifact path | SHA-256 at Play commit |
| --- | ---: | --- | --- |
| Phones/tablets | `1000005` | `mobile/build/outputs/bundle/release/mobile-release.aab` | `c424765c60c9382803d3503bcfffb49f14aaf3cc14194b9eab7f52ce6ed885cd` |
| Wear OS | `2000006` | `wear/build/outputs/bundle/release/wear-release.aab` | `56f8b10faceb3bddeea9ecb5ebb6cf396abaebcd5f91640ce4b91f1788e9f1b2` |

The uploaded candidates passed unit tests, mobile and Wear release lint,
release bundle builds, JAR signature verification, upload-certificate matching,
bundle manifest inspection, package/version checks, target SDK 36 checks, and
the no-Internet/no-location release contract. Android Publisher reports both
phone tracks as `0.1.4` / `completed` and both Wear tracks as `0.1.5` /
`completed`. Play Console groups the corrected Wear release and the retained
first-release changes under **Changes in review**.

## 0.1.1 internal release

Source commit: `a241073e035057c50be52d8588c359d661dcdf45`

| Surface | Version code | Candidate | Bytes | SHA-256 |
| --- | ---: | --- | ---: | --- |
| Phones/tablets | `1000002` | `mobile/build/outputs/bundle/release/mobile-release.aab` | `10715490` | `3dacf0b628287f20a59689f9f7f95e85ac793f6d2d0d7cbccee7463aa2f71204` |
| Wear OS | `2000002` | `wear/build/outputs/bundle/release/wear-release.aab` | `10205881` | `49ebc700f814b6693749de0a71e864b0551614e1a83e8a8b7378be824591c53d` |

Both candidates were rebuilt from the committed `0.1.1` identity without a
Gradle clean task. Bundletool readback confirms package
`app.zhanzhuang.timer`, version name `0.1.1`, target SDK 36, and version codes
`1000002` / `2000002`. `jarsigner` reports both jars verified, and both upload
certificates match the established SHA-256 above. The full debug gate reran
188 Gradle tasks and passed both app test suites, both lint gates, both debug
APKs, release manifests, permission contracts, and bilingual resources. The
signed verifier then passed all JVM tests, both release lint gates, store
metadata/assets, both AAB builds, strict JAR integrity, certificate matching,
and bundle manifest inspection.

The connected Pixel 10 Pro (Android 17/API 37) passed six mobile instrumentation
tests, including a durable completion followed by a distinct second session,
large-font reachability, and light-theme status-bar contrast. A fresh
notification-permission state was also exercised manually: Start produced the
system prompt; allowing it produced notification ID `1001` and a running
special-use foreground service; the test session then completed cleanly.

Both files were uploaded to their existing internal-testing tracks on
2026-08-03 UTC. Android Publisher readback after both commits returned
`0.1.1|completed|1000002` for `internal` and
`0.1.1|completed|2000002` for `wear:internal`. The Pixel Watch 4 physical
matrix remains required because the attached ADB target during this candidate
run was the phone, not the watch. The active `0.1.0` production review remains
untouched.

## 0.1.2 internal release

Source commit: `59a60f9422f7e6641c19200cd618a6a638573c24`

| Surface | Version code | Candidate | Bytes | SHA-256 |
| --- | ---: | --- | ---: | --- |
| Phones/tablets | `1000003` | `mobile/build/outputs/bundle/release/mobile-release.aab` | `10715493` | `da3b87b725bfb27fc59aafeed93519c5ef92ae0a6d389b67ba8efe0fb0f9b505` |
| Wear OS | `2000003` | `wear/build/outputs/bundle/release/wear-release.aab` | `10206080` | `e4110a5d27793139c5dac7b1adf83a9a1219081e4ee7c138f9cdf9bdeff8a173` |

The 0.1.2 Wear foreground service now publishes the durable Wear-owned session
state before its ephemeral countdown updates at forced synchronization points.
This closes the ordering gap where a watch-local start could send runtime data
that the phone correctly rejected because it had not yet learned the session
identity and ownership. A regression contract failed on the old ordering and
passed after the state-before-runtime change.

The clean-free debug gate forcibly reran 188 Gradle tasks and passed all JVM
tests, both debug lint gates, both debug APK builds, release-manifest checks,
version/package checks, permission contracts, and localized resources. The
connected Pixel 10 Pro (Android 17/API 37) passed all six mobile instrumentation
tests. The signed verifier then passed 206 Gradle tasks plus release lint,
store assets, strict JAR integrity, upload-certificate matching, and bundletool
identity checks for both AABs.

Both candidates were uploaded on 2026-08-03 UTC. Android Publisher readback
returned `0.1.2|completed|1000003` for `internal` and
`0.1.2|completed|2000003` for `wear:internal`. Production remained
`0.1.0|completed|1000001` and `0.1.0|completed|2000001`. Pixel Watch 4
physical runtime evidence remains separate and is not inferred from the Play
upload or the phone-only instrumentation run.

## 0.1.3 internal release

Source commit: `902ab6c2e1fc60667cd8d5e6441d49a300bf634a`

| Surface | Version code | Candidate | Bytes | SHA-256 |
| --- | ---: | --- | ---: | --- |
| Phones/tablets | `1000004` | `mobile/build/outputs/bundle/release/mobile-release.aab` | `10715950` | `3fd59f8b094e311eadfa44c248a557dee89bdc4a79f701cd3f371b467879739f` |
| Wear OS | `2000004` | `wear/build/outputs/bundle/release/wear-release.aab` | `10206537` | `8adeeec7ea1293fd2e616b29f1c83fe231673cad237903639fec2ed9ccfa02dc` |

The 0.1.3 phone and Wear launchers now use the same adaptive foreground: a
standing gold figure holds a gold-and-soil taiji with two contrasting dots.
The matching monochrome assets preserve the taiji contour, and the canonical
SVG generated identical 512 px RGBA Play icons for `en-GB`, `en-US`, and
`zh-CN`. Resource contract tests first failed on the missing taiji identifiers
and passed after all phone, Wear, monochrome, and store assets were aligned.

The clean-free debug gate forcibly reran 188 Gradle tasks and passed all JVM
tests, both debug lint gates, both debug APK builds, release-manifest checks,
version/package checks, permission contracts, and localized resources. The
signed verifier passed 206 Gradle tasks plus release lint, store-image
contracts, strict JAR integrity, upload-certificate matching, and bundletool
identity checks for both AABs.

Both candidates were uploaded on 2026-08-03 UTC. Android Publisher readback
returned `0.1.3|completed|1000004` for `internal` and
`0.1.3|completed|2000004` for `wear:internal`. Production remained
`0.1.0|completed|1000001` and `0.1.0|completed|2000001`. The Play listing icon
was uploaded with the phone internal release. An Android Publisher
`edits.images.list` readback returned one icon for each of `en-GB`, `en-US`,
and `zh-CN`; all three remote objects report SHA-256
`82502746c4ff9bd2e967711aa1e0e8e58aba180958a4f6b2a45b9af12c58beb2`,
exactly matching the three repository PNGs. The public unreviewed detail page
still rendered Google's generic Android placeholder, so that cached surface is
not used as icon-upload evidence. Physical launcher and paired runtime evidence
remains separate because the Mac did not enumerate either device through ADB
after the upload.

## 0.1.4 internal and production review release

Source commit: `12f42ec`

| Surface | Version code | Candidate | Bytes | SHA-256 |
| --- | ---: | --- | ---: | --- |
| Phones/tablets | `1000005` | `mobile/build/outputs/bundle/release/mobile-release.aab` | `10716027` | `c424765c60c9382803d3503bcfffb49f14aaf3cc14194b9eab7f52ce6ed885cd` |
| Wear OS | `2000005` | `wear/build/outputs/bundle/release/wear-release.aab` | `10206604` | `80ed4c351acd54e00c3d82f69c0001cc1629773a47567e7ad7f684c00a7e5c7d` |

The launcher was simplified so the hands meet one borderless gold-and-soil
taiji at its sides and stop above the lower edge. The arm paths no longer form
an outer ball ring. Phone, Wear, monochrome, canonical SVG, and all three Play
listing icons share this rule. The rendered 512 px RGBA listing icon has
SHA-256 `f98751a274283950cf8724bab47c37d65c8eddf871161224e17625f4711829a3`;
Android Publisher returned that exact hash for `en-GB`, `en-US`, and `zh-CN`.

The clean-free debug gate forcibly reran 188 Gradle tasks and passed all JVM
tests, mobile and Wear debug lint, both APK builds, release manifests, package
and version identity, permissions, and localized resources. The signed verifier
then passed 206 Gradle tasks, release lint, store-image contracts, both AAB
builds, strict JAR integrity, the approved upload certificate, and bundletool
manifest checks. A concurrent-service test initially failed because a finishing
ticker can safely repeat `stopSelfResult` for the old start ID; its assertion
now verifies the real invariant that every stop request targets only the old ID
and never the newly started session.

Both AABs were uploaded to internal testing and read back as
`0.1.4|completed|1000005` and `0.1.4|completed|2000005`. The verified versions
were then promoted to `production` and `wear:production` with full-rollout
`completed` status. A fresh Android Publisher readback returned the same
version codes on both production tracks. Play Console Publishing overview
lists **0.1.4 — Start full rollout** under both **Production** and
**Production (Wear OS)** inside **Changes in review**. At the evidence time,
Google's quick automated checks were running with an estimate of up to 13
minutes; the page states that the changes will proceed to review automatically
after those checks pass. There was no remaining send-for-review action.

## 0.1.5 Wear rejection correction and resubmission

Source commit: `549783c`

| Surface | Version code | Uploaded artifact path | Bytes at Play commit | SHA-256 at Play commit |
| --- | ---: | --- | ---: | --- |
| Wear OS | `2000006` | `wear/build/outputs/bundle/release/wear-release.aab` | `10209517` | `56f8b10faceb3bddeea9ecb5ebb6cf396abaebcd5f91640ce4b91f1788e9f1b2` |

Google Play rejected Wear version code `2000005` on 2026-08-04 for two
directly evidenced quality issues: scrollable views lacked a scrollbar, and
duration shortcut controls/text were cut by the round display edge. The
rejection affected Wear only; the phone bundle was not named. The response did
not appeal the correct finding.

The setup and heart-rate permission pages now use Wear Compose's
shape-adaptive `ScalingLazyColumn`, snap scrolling for touch and rotary input,
round-safe horizontal action insets, and a platform `ScrollIndicator`. Other
legacy `ScreenColumn` users also receive the platform scroll indicator. New
source contracts were observed failing before implementation and passing after
the fix. Visual verification on a 456 px round Wear OS 4/API 33 emulator showed
the active shortcut fully inside the circle, adjacent items scaling/fading at
the edges, and the scroll indicator visible. The connected Wear instrumentation
suite then passed all five tests, including the real service session flow.

The clean-free gate passed 188 Gradle tasks across unit tests, debug lint,
debug builds, release manifests, permissions, locales, and distinct phone/Wear
version identities. The signed verifier passed the release test/lint/bundle
pipeline, strict JAR integrity, upload-certificate matching, package and SDK
inspection, and the no-Internet/no-location contracts. The approved upload
certificate remained unchanged.

The Wear AAB was uploaded to `wear:internal` and promoted to
`wear:production` with `deactivate_on_promote=true`. Sequential Android
Publisher readback returned `0.1.5|completed|2000006` on both Wear tracks;
`2000005` was no longer active. Phone tracks remained unchanged at
`0.1.4|completed|1000005`. Play Console then showed Wear `0.1.5` under the 15
pending first-release changes. The final confirmation returned **15 changes
sent for review**, and the Publishing overview moved to **Changes in review**
with one visible `0.1.5` entry.

The hashes above identify the exact bytes committed to Google Play. A later
post-submission verification rebuild included newer archive/VCS metadata and
therefore produced different local file hashes (`c4f21c4c…e3e7` for mobile and
`4fe35bb2…ef51` for Wear), while all manifest, version, certificate, signature,
SDK, permission, test, and lint checks passed again. Those rebuilt files were
not uploaded and do not replace the Play-commit hashes above.

## Google Play remote state

The established Miharana Fastlane service account was granted only the app-
scoped permissions needed for testing, store presence, policy declarations,
and production releases. It has no account administration or finance access.

| Surface | Track | Version code | Android Publisher readback after submission |
| --- | --- | ---: | --- |
| Phones/tablets | `internal` | `1000006` | `0.1.6` / `completed` / Active |
| Wear OS | `wear:internal` | `2000007` | `0.1.6` / `completed` / Active |
| Phones/tablets | `production` | `1000005` | `0.1.4` / `completed`; listed under Changes in review |
| Wear OS | `wear:production` | `2000006` | `0.1.5` / `completed`; listed under Changes in review |

The first production promotions were deliberately created as `draft`, because
Google Play rejects a non-draft production release while the app itself is
still a draft app. Both production releases were then previewed in Console and
had zero blocking release errors. The only automated-check blocker was the
required Advertising ID declaration; it was completed as **No**, consistent
with both manifests. All 15 first-release changes were submitted. Publishing
overview confirmed **15 changes sent for review** and now groups them under
**Changes in review**. A later Publishing overview readback no longer showed
the automated quick-check progress or any issue, while the submission remained
under **Changes in review**. Version `0.1.4` subsequently replaced both
production candidates and was submitted as described above.

Internal tester list: `Internal Testers` (2 users)

- Play owner account
- Play test account

Phone/tablet internal opt-in URL:
<https://play.google.com/apps/internaltest/4701473602939953272>

Wear OS internal opt-in URL:
<https://play.google.com/apps/internaltest/4698135762362798560>

The tester list is selected on both internal tracks. Google Play accepted the
invite for the Play test account, and its install picker recognizes the paired
Google Pixel Watch 4 and Google Pixel 10 Pro. Remote installation still requires
the account holder's Google Passkey confirmation. The 0.1.3 Pixel Watch 4
request subsequently passed that verification and Play confirmed it would be
installed soon. The separate Pixel 10 Pro request also passed its Passkey
verification and received the same install-soon confirmation. Neither message
is treated as installed-version, launcher-rendering, or runtime proof.

## Store and policy setup

- Default and localized listings: `en-GB`, `en-US`, `zh-CN`
- Per locale: title, descriptions, changelogs, icon, feature graphic, four
  phone screenshots, and two Wear screenshots
- Category: Health & fitness
- Distribution: all supported production countries/regions; Wear summary
  reported 177 countries/regions
- Wear OS form factor opted in
- No ads, no account/sign-in requirement, audience 18+, content rating
  completed, government and financial-feature declarations set to no
- Advertising ID declaration completed as **No**; neither candidate requests
  `com.google.android.gms.permission.AD_ID`
- Data safety: no off-device collection or sharing; the app has no Internet
  permission and user-authorized Health Connect writes remain on device
- Health declaration: Activity & fitness and Stress/relaxation/mental acuity;
  explanations supplied for heart-rate and background health permissions
- Foreground-service declaration completed for health tracking and the
  user-started standing timer's special-use timing requirement

Public privacy URL: <https://zhanzhuang-privacy.artlinx.workers.dev>

Public foreground-service review evidence:

- Wear health service: <https://zhanzhuang-privacy.artlinx.workers.dev/review/wear-health-foreground-service.mp4>
- Phone timer service: <https://zhanzhuang-privacy.artlinx.workers.dev/review/mobile-special-use-foreground-service.mp4>

Both videos return HTTP 200 as `video/mp4`; remote bytes were downloaded and
matched their local SHA-256 hashes. The privacy Worker deployment containing
them is version `928f59ce-1c62-4193-8fa6-72d2247c1541`, sourced from commit
`420e71e`.

## Known non-blocking diagnostics

Play shows two warnings on each production bundle: no R8 deobfuscation file and
no native debug-symbol archive. These are recommendations, not release errors.
The current release does not enable R8 obfuscation; the native code comes from
packaged dependencies. They do not block the first review.

## Remaining external evidence

Android Publisher reports both production releases as `completed`, which means
full rollout rather than draft/staged rollout; it does not mean that this new
app has passed first review or is publicly discoverable. Public availability
cannot be claimed until Google completes the first app review. Physical Pixel
Watch 4 testing (screen-off, disconnect/reconnect,
permission denial, process death, reboot, and 15/30/180-minute sessions) also
remains separate from the emulator and Play-delivered internal-track evidence.
