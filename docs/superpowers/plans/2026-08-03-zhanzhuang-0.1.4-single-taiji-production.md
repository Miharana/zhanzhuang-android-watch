# Zhan Zhuang 0.1.4 single-taiji production plan

## Objective

Ship phone version `1000005` and Wear version `2000005` as Zhan Zhuang `0.1.4`, with one visually unambiguous gold taiji held at chest height and no arm-created second ring.

## Release gates

1. Resource contract tests require the same shortened holding-arm geometry on phone and Wear launchers, a borderless gold taiji disc, and matching store SVG identifiers.
2. The canonical 512 px Play icon is rendered from `fastlane/assets-source/icon.svg` and copied identically to all supported listings.
3. Debug tests, lint, APK identity checks, release tests, release lint, signed AAB construction, certificate verification, and bundle manifest inspection must all pass.
4. Both artifacts are uploaded to their internal tracks and read back through Android Publisher before production promotion.
5. Both verified version codes are promoted with `completed` status and the Play Console publishing overview must confirm that the changes were sent for review.

## Evidence

Record artifact hashes, upload certificate, internal and production track readbacks, listing icon hash readback, and the final Play review state in the release evidence documents.
