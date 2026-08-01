# Task 11 — Google Play release handoff

## Evidence-driven delivery

The release verifier was first specified by a failing shell contract because it did not exist. Contract coverage then established fail-closed signing variables, exact certificate pinning, release lint/bundle checks, screenshot geometry/alpha checks, locale-resource evidence, and strict `jarsigner` verification under `LC_ALL=C`.

UI changes also followed red/green contract tests:

- mobile safe drawing insets and settings scrolling;
- Wear setup action ordering; and
- round Wear permission safe-top padding (`36.dp`) and `4.dp` button spacing.

The final signed command used shell-local 1Password values only; no secret value was printed or written:

```sh
op whoami >/dev/null
# resolve store/key passwords from the ClaudeAgent vault into shell-local variables
# export the four ZHANZHUANG signing variables in this shell only
bash scripts/verify_release.sh
```

Final result: `PASS: signed Google Play release candidates verified.`

| Artifact | SHA-256 | Bytes | Upload certificate SHA-256 |
| --- | --- | ---: | --- |
| `mobile-release.aab` | `80288a300c6b8cd0d628e51f3e7cb52de9e180a879fd6b570fe6fb59095c0b70` | 10,713,313 | `83:FC:DF:02:8E:8B:E3:82:6C:AD:A6:86:76:08:A6:95:9C:77:5F:CA:D6:98:E7:13:FE:02:7A:07:0E:12:7C:81` |
| `wear-release.aab` | `8ca66e2888622edaefccff786275e9548765babbef5774a4568cf3e3e0303adb` | 10,200,762 | `83:FC:DF:02:8E:8B:E3:82:6C:AD:A6:86:76:08:A6:95:9C:77:5F:CA:D6:98:E7:13:FE:02:7A:07:0E:12:7C:81` |

## Listing-asset provenance

- Phone: actual `zhan_zhuang_task10_phone_api36` Pixel 8 / API 36 debug app. Assets are native emulator captures (1080×1920 or 1080×2160), rendered under each app locale and flattened through JPEG before final PNG output so no alpha channel remains. The final zh-CN settings capture was asserted to contain `设置`, `Health Connect`, and `隐私`, and used the emulator demo clock `8:42` to keep the complete clock glyph visible.
- Wear: actual `zhan_zhuang_task10_wear_api33`, `wearos_large_round`, Wear OS 4 / API 33, 454×454. Setup and permission state labels were asserted through UI Automator before capture. Final PNGs are flattened and contain no alpha.
- Both task-specific AVDs were stopped after capture.

## Review follow-up: round actions and asset format gate

- Wear full-width primary/secondary actions now add a 32dp horizontal safe inset and a 64dp trailing scroll-safe space. The setup screen uses its limited round canvas for duration, interval, and the start action rather than repeating branding. A pure unit test calculates all four action-rectangle corners against the 454px circle: it rejects the prior `36..418 × 351..447` full-width rectangle and accepts the release-capture safe band. UI Automator then recorded the real bounds before each new EN/zh setup and permission capture.
- The Play icon validator now requires 512×512, PNG, 8 bits per sample, four samples per pixel, and alpha. Wear screenshots must be square, 384–3840px inclusive, and alpha-free. The contract creates temporary actual fixtures: a 512px RGB/no-alpha icon and a 4000px no-alpha Wear image; both are rejected, while current legal icon/Wear files pass. The fixture directory is cleaned by a trap.

## Known delivery blockers / next console actions

- No Google Play app bootstrap or service-account JSON path was provided, so no Play Console upload was performed. Fastlane lanes are prepared; the Wear upload lane intentionally fails closed until its dedicated upload task authorizes a track.
- Complete Play Console first-app setup, Play App Signing enrollment, Health/Data Safety declarations, privacy-policy URL, tester configuration, and Wear opt-in before publishing.
- Physical Pixel Watch validation remains outside this emulator-only handoff.
