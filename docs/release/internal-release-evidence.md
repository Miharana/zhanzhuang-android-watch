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

## Remote read-only probe

1. The specified repository-external service-account path
   `/Users/pema/.config/zhanzhuang/google-play/fastlane-supply.json` was not
   present.
2. A different repository-external candidate credential authenticated to the
   Android Publisher API without exposing credential contents. A transient,
   uncommitted edit probe for `app.zhanzhuang.timer` returned
   `package-not-found-or-not-authorized`; no AAB, metadata, image, or release
   was committed. The edit was discarded in the probe cleanup path.
3. A Play Console session then reached `https://play.google.com/console/signup`
   with the title **Creating a developer account**, rather than an existing
   verified developer dashboard. No Console form was submitted.

This proves neither package registration nor app-specific service-account
access. It also provides no exact Wear internal track identifier. Uploading a
mobile binary, guessing a Wear track, or asserting Play App Signing/Health/Data
Safety/tester state would therefore be unsafe and was not done.

## External blocker and exact continuation

An account owner must complete the Google Play developer-account bootstrap
(including any identity, terms, and payment steps the Console requires), then
grant a repository-external service account app access or provide an
appropriately authorized external credential. Once an authenticated developer
dashboard is available, the continuation is:

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

## Current remote truth

| Surface | Track | Version code | State |
| --- | --- | ---: | --- |
| Mobile | `internal` | — | Not read: app is not accessible to the available credential/session. |
| Wear OS | dedicated internal track | — | Not read: exact track is unknown; no upload attempted. |
| Production | `production` | — | Untouched; no mutation attempted. |
