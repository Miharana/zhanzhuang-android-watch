# Google Play release evidence

Last updated: 2026-08-02 (UTC)

## Release identity

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

## Google Play remote state

The established Miharana Fastlane service account was granted only the app-
scoped permissions needed for testing, store presence, policy declarations,
and production releases. It has no account administration or finance access.

| Surface | Track | Version code | Android Publisher readback after submission |
| --- | --- | ---: | --- |
| Phones/tablets | `internal` | `1000001` | `completed` / Active |
| Wear OS | `wear:internal` | `2000001` | `completed` / Active |
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

- Play owner account
- Play test account

Phone/tablet internal opt-in URL:
<https://play.google.com/apps/internaltest/4701473602939953272>

Wear OS internal opt-in URL:
<https://play.google.com/apps/internaltest/4698135762362798560>

The tester list is selected on both internal tracks. Google Play accepted the
invite for the Play test account, and its install picker recognizes the paired
Google Pixel Watch 4 and Google Pixel 10 Pro. Remote installation still requires
the account holder's Google Passkey confirmation.

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
