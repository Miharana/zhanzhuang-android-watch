# Google Play Console checklist — first public release

Release: `app.zhanzhuang.timer` version `0.1.0`

## Completed

- [x] Reused the existing **Urticad Tech Ltd** organisation developer account.
- [x] Created the Play app record and enrolled the app in Play App Signing.
- [x] Granted the existing external Fastlane service account least-privilege,
  app-scoped access, including production release permission.
- [x] Verified the signed phone (`1000001`) and Wear (`2000001`) AABs and their
  upload certificate outside the repository.
- [x] Published and entered the bilingual HTTPS privacy policy.
- [x] Uploaded `en-GB`, `en-US`, and `zh-CN` listings, icons, graphics, phone
  screenshots, Wear screenshots, and changelogs.
- [x] Completed app access, ads, content rating, target audience, Data safety,
  Advertising ID (No), government, finance, Health apps, regional, and
  foreground-service forms.
- [x] Set Health & fitness category and public contact details.
- [x] Opted into Wear OS and selected all supported production countries.
- [x] Published phone `internal` code `1000001` and Wear `wear:internal` code
  `2000001` with completed status; activated the tester list.
- [x] Enabled the shared two-user tester list on both internal tracks, accepted
  the invite as the Play test account, and confirmed that Google Play recognizes
  the paired Pixel Watch 4 and Pixel 10 Pro as install targets.
- [x] Promoted both immutable internal candidates to draft production tracks.
- [x] Previewed and saved the phone and Wear full-rollout changes with zero
  blocking release errors.

## Current first-review state

- [x] Cleared the sole automated-check blocker by completing Advertising ID as
  No, consistent with both signed bundle manifests.
- [x] Submitted all 15 staged changes for Google review; Publishing overview
  shows **Changes in review** and **15 changes sent for review**.
- [x] Read back Android Publisher tracks: `internal` `completed:1000001`,
  `wear:internal` `completed:2000001`, `production` `completed:1000001`, and
  `wear:production` `completed:2000001`.
- [x] Confirmed the automated quick-check progress and issue panel disappeared
  while all changes remained under **Changes in review**.
- [ ] Wait for Google first-app review to complete. Play says reviews are
  typically completed within seven days but may take longer.
- [ ] After Google approval, verify whether managed-publishing-off caused
  automatic publication; if not, explicitly publish the approved changes.
- [ ] Verify the public Play listing and production install on a phone and a
  paired Pixel Watch 4.

Phone/tablet internal opt-in URL:
<https://play.google.com/apps/internaltest/4701473602939953272>

Wear OS internal opt-in URL:
<https://play.google.com/apps/internaltest/4698135762362798560>

The two release-page warnings (missing R8 mapping and native symbols) are
non-blocking diagnostics. They are recorded in `internal-release-evidence.md`.
