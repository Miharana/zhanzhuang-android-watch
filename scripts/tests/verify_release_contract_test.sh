#!/usr/bin/env bash
# Contract tests for the fail-closed Google Play release verifier.
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
verifier="$repo_root/scripts/verify_release.sh"

fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
require_file() { [[ -f "$1" ]] || fail "missing $1"; }
require_text() { grep -Fq -- "$2" "$1" || fail "$1 must contain: $2"; }

require_file "$verifier"
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
require_text "$verifier" '1000001'
require_text "$verifier" '2000001'
require_text "$verifier" 'app.zhanzhuang.timer'
require_text "$verifier" '"$height" -le $((2 * width))'
require_text "$verifier" 'packaged_res/release/packageReleaseResources/values-zh-rCN/values-zh-rCN.xml'

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
