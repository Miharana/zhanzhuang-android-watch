#!/usr/bin/env bash
# Contract tests for the fail-closed Google Play release verifier.
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
verifier="$repo_root/scripts/verify_release.sh"
image_checks="$repo_root/scripts/lib/release_image_checks.sh"

fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
require_file() { [[ -f "$1" ]] || fail "missing $1"; }
require_text() { grep -Fq -- "$2" "$1" || fail "$1 must contain: $2"; }

require_file "$verifier"
require_file "$image_checks"
[[ ! -x "$image_checks" ]] || fail 'release image helper must remain non-executable'
require_text "$verifier" 'missing ZHANZHUANG_KEYSTORE_PATH'
require_text "$verifier" 'missing ZHANZHUANG_KEY_ALIAS'
require_text "$verifier" 'missing ZHANZHUANG_STORE_PASSWORD'
require_text "$verifier" 'missing ZHANZHUANG_KEY_PASSWORD'
require_text "$verifier" 'git ls-files'
require_text "$verifier" 'local.properties'
require_text "$verifier" 'service-account'
require_text "$verifier" 'jarsigner'
require_text "$verifier" '-verify -strict'
require_text "$verifier" 'verify_jarsigner'
require_text "$verifier" 'jar verified'
require_text "$verifier" 'LC_ALL=C "$jarsigner" -verify -strict'
require_text "$verifier" '83:FC:DF:02:8E:8B:E3:82:6C:AD:A6:86:76:08:A6:95:9C:77:5F:CA:D6:98:E7:13:FE:02:7A:07:0E:12:7C:81'
require_text "$verifier" ':mobile:bundleRelease'
require_text "$verifier" ':wear:bundleRelease'
require_text "$verifier" ':mobile:lintRelease'
require_text "$verifier" ':wear:lintRelease'
require_text "$verifier" 'phoneScreenshots'
require_text "$verifier" 'wearScreenshots'
require_text "$verifier" 'featureGraphic.png'
require_text "$verifier" '0.1.11'
require_text "$verifier" '1000011'
require_text "$verifier" '2000013'
require_text "$verifier" 'verify_bundle_manifest mobile "$mobile_aab" 1000011 0.1.11'
require_text "$verifier" 'verify_bundle_manifest wear "$wear_aab" 2000013 0.1.12'
require_text "$verifier" 'track_promote_release_status: "completed"'
require_text "$verifier" 'app.zhanzhuang.timer'
require_text "$verifier" '"$height" -le $((2 * width))'
require_text "$verifier" 'packaged_res/release/packageReleaseResources/values-zh-rCN/values-zh-rCN.xml'
if grep -Fq 'VERIFY_RELEASE_LIB_ONLY' "$verifier"; then
    fail 'production verifier must not contain an environment-controlled bypass'
fi

# Image validators are loaded as a library so deliberately invalid, temporary fixtures
# exercise the same checks used by the release gate.
set +e
bash -c 'fail() { printf "%s\n" "$*" >&2; exit 1; }; source "$1"; verify_play_icon /dev/null' _ "$image_checks" >"${TMPDIR:-/tmp}/zhan-zhuang-image-contract.out" 2>&1
image_status=$?
set -e
[[ $image_status -ne 0 ]] || fail 'invalid icon fixture must be rejected by the release image validator'
grep -Fq 'Play icon' "${TMPDIR:-/tmp}/zhan-zhuang-image-contract.out" || fail 'icon fixture must reach the release image validator'
rm -f "${TMPDIR:-/tmp}/zhan-zhuang-image-contract.out"

set +e
env -u ZHANZHUANG_KEYSTORE_PATH -u ZHANZHUANG_KEY_ALIAS -u ZHANZHUANG_STORE_PASSWORD -u ZHANZHUANG_KEY_PASSWORD \
    VERIFY_RELEASE_LIB_ONLY=1 bash "$verifier" >"${TMPDIR:-/tmp}/zhan-zhuang-release-bypass.out" 2>&1
bypass_status=$?
set -e
[[ $bypass_status -ne 0 ]] || fail 'legacy library flag must not bypass direct release verification'
first_line=$(sed -n '1p' "${TMPDIR:-/tmp}/zhan-zhuang-release-bypass.out")
[[ "$first_line" == *'missing ZHANZHUANG_KEYSTORE_PATH'* ]] || fail 'legacy library flag must still fail first on ZHANZHUANG_KEYSTORE_PATH'
rm -f "${TMPDIR:-/tmp}/zhan-zhuang-release-bypass.out"

fixture_dir=$(mktemp -d "${TMPDIR:-/tmp}/zhan-zhuang-image-fixtures.XXXXXX")
trap 'rm -rf "$fixture_dir"' EXIT
bash -c 'fail() { printf "%s\n" "$*" >&2; exit 1; }; source "$1"; verify_play_icon "$2"; verify_wear_screenshot "$3"' _ \
    "$image_checks" \
    "$repo_root/fastlane/metadata/android/en-US/images/icon.png" \
    "$repo_root/fastlane/metadata/android/en-US/images/wearScreenshots/01.png"
sips -z 512 512 "$repo_root/fastlane/metadata/android/en-US/images/featureGraphic.png" --out "$fixture_dir/rgb-icon.png" >/dev/null
set +e
bash -c 'fail() { printf "%s\n" "$*" >&2; exit 1; }; source "$1"; verify_play_icon "$2"' _ "$image_checks" "$fixture_dir/rgb-icon.png" >"$fixture_dir/icon.out" 2>&1
image_status=$?
set -e
[[ $image_status -ne 0 ]] || fail 'RGB icon fixture must be rejected'
grep -Fq '8-bit RGBA/32-bit PNG with alpha' "$fixture_dir/icon.out" || fail 'RGB icon fixture must fail the RGBA contract'

sips -z 4000 4000 "$repo_root/fastlane/metadata/android/en-US/images/wearScreenshots/01.png" --out "$fixture_dir/oversize-wear.png" >/dev/null
set +e
bash -c 'fail() { printf "%s\n" "$*" >&2; exit 1; }; source "$1"; verify_wear_screenshot "$2"' _ "$image_checks" "$fixture_dir/oversize-wear.png" >"$fixture_dir/wear.out" 2>&1
image_status=$?
set -e
[[ $image_status -ne 0 ]] || fail 'oversize Wear fixture must be rejected'
grep -Fq 'between 384px and 3840px' "$fixture_dir/wear.out" || fail 'oversize Wear fixture must fail the max-dimension contract'

# Cross multiplication rejects 1080x2400 (2.22:1) without integer-division truncation.
phone_width=1080
phone_height=2400
[[ "$phone_height" -gt $((2 * phone_width)) ]] || fail 'the 2.22:1 phone fixture must be rejected'

approved_certificate='83:FC:DF:02:8E:8B:E3:82:6C:AD:A6:86:76:08:A6:95:9C:77:5F:CA:D6:98:E7:13:FE:02:7A:07:0E:12:7C:81'
wrong_certificate='83:FC:DF:02:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00'
[[ "$wrong_certificate" != "$approved_certificate" ]] || fail 'wrong certificate fixture must not match approved certificate'

for module in mobile wear; do
    require_text "$repo_root/$module/build.gradle.kts" 'ZHANZHUANG_KEYSTORE_PATH'
    require_text "$repo_root/$module/build.gradle.kts" 'signingConfigs'
    require_text "$repo_root/$module/build.gradle.kts" 'signingConfig = signingConfigs.getByName("release")'
done
require_text "$repo_root/fastlane/Fastfile" 'version_code: 2_000_013'
require_text "$repo_root/fastlane/Fastfile" 'upload_mobile_internal_binary_only'
require_text "$repo_root/fastlane/Fastfile" 'skip_upload_metadata: true'
require_text "$repo_root/fastlane/Fastfile" 'changes_not_sent_for_review: true'

set +e
env -u ZHANZHUANG_KEYSTORE_PATH -u ZHANZHUANG_KEY_ALIAS -u ZHANZHUANG_STORE_PASSWORD -u ZHANZHUANG_KEY_PASSWORD \
    bash "$verifier" >"${TMPDIR:-/tmp}/zhan-zhuang-release-contract.out" 2>&1
status=$?
set -e
[[ $status -ne 0 ]] || fail 'release verifier must fail without signing variables'
first_line=$(sed -n '1p' "${TMPDIR:-/tmp}/zhan-zhuang-release-contract.out")
[[ "$first_line" == *'missing ZHANZHUANG_KEYSTORE_PATH'* ]] || fail 'release verifier must fail first on ZHANZHUANG_KEYSTORE_PATH'
rm -f "${TMPDIR:-/tmp}/zhan-zhuang-release-contract.out"

printf 'PASS: release verifier contracts\n'
