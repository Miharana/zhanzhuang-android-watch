# Google Play internal-release verification

Date (UTC): 2026-08-01T08:16:32Z

## Authorized scope and local immutable gates

- Authorized distribution scope: **Google Play internal testing only**. No
  closed, open, or production mutation was attempted.
- Package: `app.zhanzhuang.timer`; version name: `0.1.0`.
- Mobile candidate: `1000001`,
  `mobile/build/outputs/bundle/release/mobile-release.aab`, SHA-256
  `a4c0a96f1994fb09af77f9598ef6e67087ca40070d6c6595f82fff0832eb8d5d`,
  10,713,312 bytes.
- Wear candidate: `2000001`,
  `wear/build/outputs/bundle/release/wear-release.aab`, SHA-256
  `bc158ad05801f07f34dc8c506694cfab1991d9e93d1f3b40b4fb844d7bef6199`,
  10,200,763 bytes.
- Both candidates retain the Task 11 verified upload-certificate SHA-256:
  `83:FC:DF:02:8E:8B:E3:82:6C:AD:A6:86:76:08:A6:95:9C:77:5F:CA:D6:98:E7:13:FE:02:7A:07:0E:12:7C:81`.
- Bundle manifests confirm the expected package/version codes, mobile
  min/target SDK `28/36`, and Wear min/target SDK `33/36` with the required
  `android.hardware.type.watch` feature.
- No candidate was rebuilt or replaced during this verification. The initial
  Git gate found one non-secret, untracked `Gemfile.lock` created by the
  already-approved Fastlane setup; it is committed with this evidence so the
  release tooling is reproducible.

## Remote transient-edit probes (uncommitted)

1. The specified repository-external service-account path
   `/Users/pema/.config/zhanzhuang/google-play/fastlane-supply.json` was not
   present.
2. A different repository-external candidate credential authenticated to the
   Android Publisher API without exposing credential contents. Its target-track
   query created a **transient remote Android Publisher edit** for
   `app.zhanzhuang.timer`, because the Android Publisher tracks endpoint is
   edit-scoped. It returned `package-not-found-or-not-authorized`. No AAB,
   metadata, image, or release payload was uploaded; `edit.commit` was never
   called, and the probe cleanup path invoked `delete_edit`.
3. The candidate credential was then tested against the known existing
   Miharana Android package `com.urticad.miharana`. It read its `internal` and
   `production` track releases successfully (both report code `11`,
   `completed`) through a second **transient remote Android Publisher edit**.
   Again, no payload was uploaded, `edit.commit` was never called, and the
   probe cleanup path invoked `delete_edit`. The credential is therefore
   connected to Google Play and has access to that known app; it does **not**
   establish access to `app.zhanzhuang.timer`.
4. Browser-session audit: following the Browser skill's target-URL selection
   path, this task initialized the browser runtime and called
   `agent.browsers.getForUrl("https://play.google.com/console/")`; it selected
   **Chrome**. The task then used `agent.browsers.list()` for a non-interacting
   availability discovery, which returned exactly one surface of type
   `extension` named Chrome and no surface of type `iab`. At that time there
   was therefore no alternate browser surface available to try. Chrome's Play
   Console route reached `https://play.google.com/console/signup` with the
   title **Creating a developer account**, rather than an existing verified
   developer dashboard. No Console form was submitted.

The two Publisher API probes are deliberately recorded as temporary remote
writes, not as read-only operations. Neither reached an edit commit, and there
was no committed or public release/track/metadata/binary mutation. The target
API response intentionally conflates a package that has not been
registered with an app to which this service account has not been granted
access. The known-package probe rules out a broken/unlinked credential but
cannot distinguish those two target-side conditions. It also provides no exact
Wear internal track identifier. Uploading a mobile binary, guessing a Wear
track, or asserting Play App Signing/Health/Data Safety/tester state would
therefore be unsafe and was not done.

## External blocker and exact continuation

An account owner must complete the Google Play developer-account bootstrap as
an **organisation account** (including D-U-N-S, matching legal/contact details,
identity, terms, and payment steps), then grant a repository-external service
account app access or provide an appropriately authorized external credential.
Google's current account-type guidance says organisation accounts should be
used for health apps and that D-U-N-S verification is mandatory:
<https://support.google.com/googleplay/android-developer/answer/13634885?hl=en-GB>.
Once an authenticated developer dashboard is available, the continuation is:

1. Create or confirm the `app.zhanzhuang.timer` record; enroll Play App
   Signing; complete truthful Health apps, Data safety, content rating, public
   privacy URL/contact, testers, and Wear distribution setup.
2. Read the exact dedicated Wear internal track from Console/API.
3. Upload the recorded mobile candidate once to `internal` with `completed`
   status, read it back, then upload the recorded Wear candidate only to the
   confirmed dedicated internal track and read it back.
4. Record the remote track names, version codes, processing states, and
   Play-delivered paired-device QA. Physical Pixel Watch 4 evidence remains
   blocked until actually observed.

## Public privacy-policy publication

- URL: <https://zhanzhuang-privacy.artlinx.workers.dev>
- Published: 2026-08-01 UTC.
- Cloudflare Worker version:
  `5c67d0f4-9644-4e8e-8b05-8620351dc62f` (source commit `aee4218`).
- Remote verification returned HTTP 200 and confirmed the English and Chinese
  policy title, package `app.zhanzhuang.timer`, optional Health Connect writing
  disclosure, and removal of the starter/development preview marker.
- The policy site contains no account flow, form, analytics, advertising,
  database, or user-data storage. Its source is in `privacy-site/` and its
  Urticad-inspired palette is covered by a rendered-site test. A fresh
  `npm ci` and the complete `npm audit --audit-level=high` report zero known
  vulnerabilities. Plain HTTP redirects permanently to HTTPS; HTTPS responses
  include HSTS plus restrictive content, framing, referrer, content-type, and
  browser capability security headers.
- `.openai/hosting.json` records the non-secret Cloudflare account/project
  identifier, Worker name, production URL, deployed version, successful state,
  and source commit so later updates target the same public site.
- The URL is ready to enter in Play Console. Console entry and the required
  verified public developer contact route remain blocked by the developer
  account bootstrap described above.

## Current remote truth

| Surface | Track | Version code | State |
| --- | --- | ---: | --- |
| Mobile | `internal` | — | Not read: app is not accessible to the available credential/session. |
| Wear OS | dedicated internal track | — | Not read: exact track is unknown; no upload attempted. |
| Production | `production` | — | No committed production release, track, metadata, or binary mutation; only the uncommitted transient edit probes above occurred. |

## Continuation read-only access audit — 2026-08-01

The persistent release goal rechecked every locally available owner and service
credential without opening a Publisher edit:

- Google Application Default Credentials are present, can mint an access token,
  and the token has the Android Publisher OAuth scope. The read-only
  `reviews.list` endpoint returned `403 PERMISSION_DENIED` for both
  `app.zhanzhuang.timer` and the known Miharana control package.
- The currently active gcloud user token returned the same `403` result for
  both packages. These two user credential paths therefore have no usable app
  access in the current Play developer account.
- The repository-external Miharana service-account credential returned HTTP 200
  for the known Miharana control package and HTTP 404 for
  `app.zhanzhuang.timer` through the same read-only `reviews.list` request. This
  reconfirms that the credential itself is functional while the target package
  remains absent from or unavailable to that Play account.
- A fresh, read-only Chrome snapshot still shows **Creating a Play Console
  developer account** at `https://play.google.com/console/signup`. The page is
  waiting for an organisation-versus-personal account decision before identity,
  terms, and payment setup. No choice was clicked, no form was filled, and no
  account or billing mutation was made. The live tab was left open as a handoff
  for the account owner.
- The optimal account-type choice is now resolved from current official Play
  guidance: use an organisation account because this timer handles wellness and
  heart-rate information and is a Health app. This decision does not eliminate
  the owner-only D-U-N-S, legal identity, terms, and payment verification steps.

Unlike the earlier track checks, these continuation probes created no Android
Publisher edit and performed no remote write. Until the owner completes the
developer-account registration and grants target-app access, there is no safe
API or Console path to create/read the target app, discover the dedicated Wear
track, upload either AAB, or perform Play-delivered Pixel Watch 4 QA.
