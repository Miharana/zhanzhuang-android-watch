# Google Play Console checklist — first internal release

Release identity:

- Package: `app.zhanzhuang.timer`
- Version name: `0.1.0`
- Mobile version code: `1000001`
- Wear OS version code: `2000001`
- Intended initial mobile track: `internal`, completed (not staged)

## Before any upload

- [ ] Confirm the package is available or the correct Play app record is registered under the verified developer account.
- [ ] Enroll in Play App Signing and record the Play signing certificate separately from the local upload-certificate fingerprint.
- [ ] Confirm the external upload key path and certificate, then run `scripts/verify_release.sh` with credentials only in the verification shell.
- [ ] Complete developer identity/verification requirements in Play Console.
- [ ] Complete the Health apps declaration: optional informational heart-rate collection on Wear and optional write-only Health Connect exercise/heart-rate output; no Health Connect reads.
- [ ] Complete Data safety truthfully: no account, ads, analytics, cloud storage, or sale; local device/watch storage and optional Health Connect sharing only when the user grants it.
- [ ] Complete the content rating questionnaire truthfully.
- [ ] Publish `docs/privacy-policy.html` at a public HTTPS URL and add that URL in Play Console. Also set a verified public developer contact route; this repository intentionally does not invent an email address.
- [ ] Upload both bilingual listing locales, icon, feature graphic, phone screenshots, Wear screenshots, and changelogs.
- [ ] Add internal testers and verify their access before uploading a binary.
- [ ] Opt the app into Wear OS distribution and confirm the exact dedicated Wear form-factor track identifier in Play Console/API. Do not infer or guess that track.

## After upload / before a completion claim

- [ ] Use `fastlane android publish_metadata` only with a repository-external `SUPPLY_JSON_KEY` file.
- [ ] Use `fastlane android upload_mobile_internal` once for the signed mobile AAB; it uploads no metadata or images and targets `internal` with `completed` status.
- [ ] Do not upload the Wear AAB until Task 12 records the exact dedicated Wear track identifier from Play Console/API.
- [ ] Read back the remote mobile and Wear release state, version codes, processing/review state, and package/form-factor assignment. A successful Fastlane process is not proof of a completed Play release.
- [ ] Install from internal testing on a paired Pixel Watch and phone. Physical Pixel Watch screen-off, disconnect/reconnect, permissions-denied, process-death, reboot, and 15/30/180-minute evidence remain blocked until actually observed; emulator evidence cannot close those rows.

## First-binary constraint

Google Play may require a manual Play Console app/bootstrap before the first binary can be accepted. The Fastlane lanes prepare uploads but cannot create or verify that console setup on their own.
