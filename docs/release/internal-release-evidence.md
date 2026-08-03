# Google Play release evidence

Last updated: 2026-08-03 (UTC)

## Production release identity (unchanged)

- Developer: **Urticad Tech Ltd** (organisation account)
- Developer ID: `7258731265136907307`
- Play app ID: `4973495334373549508`
- Package: `app.zhanzhuang.timer`
- Version name: `0.1.0`
- Upload certificate SHA-256:
  `83:FC:DF:02:8E:8B:E3:82:6C:AD:A6:86:76:08:A6:95:9C:77:5F:CA:D6:98:E7:13:FE:02:7A:07:0E:12:7C:81`

| Surface | Version code | Candidate | SHA-256 |
| --- | ---: | --- | --- |
| Phones/tablets | `1000001` | `mobile/build/outputs/bundle/release/mobile-release.aab` | `a4c0a96f1994fb09af77f9598ef6e67087ca40070d6c6595f82fff0832eb8d5d` |
| Wear OS | `2000001` | `wear/build/outputs/bundle/release/wear-release.aab` | `bc158ad05801f07f34dc8c506694cfab1991d9e93d1f3b40b4fb844d7bef6199` |

The immutable candidates passed unit tests, mobile and Wear release lint,
release bundle builds, JAR signature verification, upload-certificate matching,
bundle manifest inspection, package/version checks, target SDK 36 checks, and
the no-Internet/no-location release contract.

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

## Google Play remote state

The established Miharana Fastlane service account was granted only the app-
scoped permissions needed for testing, store presence, policy declarations,
and production releases. It has no account administration or finance access.

| Surface | Track | Version code | Android Publisher readback after submission |
| --- | --- | ---: | --- |
| Phones/tablets | `internal` | `1000004` | `0.1.3` / `completed` / Active |
| Wear OS | `wear:internal` | `2000004` | `0.1.3` / `completed` / Active |
| Phones/tablets | `production` | `1000001` | `completed`; first release is still in Play review |
| Wear OS | `wear:production` | `2000001` | `completed`; first release is still in Play review |

The first production promotions were deliberately created as `draft`, because
Google Play rejects a non-draft production release while the app itself is
still a draft app. Both production releases were then previewed in Console and
had zero blocking release errors. The only automated-check blocker was the
required Advertising ID declaration; it was completed as **No**, consistent
with both manifests. All 15 first-release changes were submitted. Publishing
overview confirmed **15 changes sent for review** and now groups them under
**Changes in review**. A later Publishing overview readback no longer showed
the automated quick-check progress or any issue, while the submission remained
under **Changes in review**.

Internal tester list: `Internal Testers` (2 users)

- `wangyal@urticad.com`
- `scorpiocn@gmail.com`

Phone/tablet internal opt-in URL:
<https://play.google.com/apps/internaltest/4701473602939953272>

Wear OS internal opt-in URL:
<https://play.google.com/apps/internaltest/4698135762362798560>

The tester list is selected on both internal tracks. Google Play accepted the
invite for `scorpiocn@gmail.com`, and its install picker recognizes the paired
Google Pixel Watch 4 and Google Pixel 10 Pro. Remote installation still requires
the account holder's Google Passkey confirmation. The 0.1.3 Pixel Watch 4
request subsequently passed that verification and Play confirmed it would be
installed soon. A separate Pixel 10 Pro request is waiting for its own Passkey
confirmation; neither message is treated as installed-version or runtime proof.

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
