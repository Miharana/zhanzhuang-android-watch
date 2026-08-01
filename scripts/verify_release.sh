#!/usr/bin/env bash
# Fail-closed local verification for the signed Google Play candidates.
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_root"

fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
need_command() { command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"; }
require_file() { [[ -s "$1" ]] || fail "missing required file: ${1#$repo_root/}"; }
require_text() { grep -Fq -- "$2" "$1" || fail "${1#$repo_root/} must contain required release text"; }
. "$repo_root/scripts/lib/release_image_checks.sh"

# Keep these checks first: do not perform Gradle or credential work with an incomplete key.
: "${ZHANZHUANG_KEYSTORE_PATH:?missing ZHANZHUANG_KEYSTORE_PATH}"
: "${ZHANZHUANG_KEY_ALIAS:?missing ZHANZHUANG_KEY_ALIAS}"
: "${ZHANZHUANG_STORE_PASSWORD:?missing ZHANZHUANG_STORE_PASSWORD}"
: "${ZHANZHUANG_KEY_PASSWORD:?missing ZHANZHUANG_KEY_PASSWORD}"

expected_keystore=/Users/pema/.config/zhanzhuang/signing/zhanzhuang-upload.jks
expected_alias=zhanzhuang-upload
expected_fingerprint='83:FC:DF:02:8E:8B:E3:82:6C:AD:A6:86:76:08:A6:95:9C:77:5F:CA:D6:98:E7:13:FE:02:7A:07:0E:12:7C:81'
[[ "$ZHANZHUANG_KEYSTORE_PATH" == "$expected_keystore" ]] || fail 'release keystore path is not the approved external upload key'
[[ "$ZHANZHUANG_KEY_ALIAS" == "$expected_alias" ]] || fail 'release key alias is not the approved upload alias'
[[ -f "$ZHANZHUANG_KEYSTORE_PATH" ]] || fail 'approved external keystore is unavailable'
case "$ZHANZHUANG_KEYSTORE_PATH" in "$repo_root"/*) fail 'release keystore must remain outside the repository' ;; esac

[[ ! -e fastlane/service-account.json ]] || fail 'repo-local service-account.json is forbidden'
[[ ! -e local.properties ]] || fail 'repo-local local.properties is forbidden'

for forbidden in \
    '(^|/)(local\.properties|.*\.(jks|keystore)|keystore\.properties|service-account[^/]*\.json|.*service.*account.*\.json)$'; do
    tracked=$(git ls-files | grep -E "$forbidden" || true)
    [[ -z "$tracked" ]] || fail 'tracked signing material, service-account JSON, or local.properties is forbidden'
    history=$(git log --all --format= --name-only | grep -E "$forbidden" || true)
    [[ -z "$history" ]] || fail 'Git history contains a signing-material or credential-path risk'
done
worktree_risk=$(find . -path './.git' -prune -o \( -name '*.jks' -o -name '*.keystore' -o -name 'keystore.properties' -o -name 'local.properties' -o -iname '*service*account*.json' \) -print -quit)
[[ -z "$worktree_risk" ]] || fail 'worktree contains signing material, service-account JSON, or local.properties'
raw_signing=$(git grep -nE 'ZHANZHUANG_(STORE|KEY)_PASSWORD[[:space:]]*=[^[:space:]]+' -- ':!scripts/verify_release.sh' ':!scripts/tests/verify_release_contract_test.sh' || true)
[[ -z "$raw_signing" ]] || fail 'tracked files contain a raw signing-password assignment'

for locale in en-GB en-US zh-CN; do
    locale_dir="fastlane/metadata/android/$locale"
    for metadata in title.txt short_description.txt full_description.txt changelogs/1000001.txt changelogs/2000001.txt; do
        require_file "$locale_dir/$metadata"
    done
    [[ $(wc -m < "$locale_dir/title.txt") -le 30 ]] || fail "$locale title exceeds 30 characters"
    [[ $(wc -m < "$locale_dir/short_description.txt") -le 80 ]] || fail "$locale short description exceeds 80 characters"
    [[ $(wc -m < "$locale_dir/full_description.txt") -le 4000 ]] || fail "$locale full description exceeds 4000 characters"

    icon="$locale_dir/images/icon.png"
    feature="$locale_dir/images/featureGraphic.png"
    require_file "$icon"; require_file "$feature"
    verify_play_icon "$icon"
    [[ $(image_size "$feature") == '1024x500' ]] || fail "$locale feature graphic must be 1024x500"
    [[ $(image_has_alpha "$feature") == 'no' ]] || fail "$locale feature graphic must not have alpha"

    phone_dir="$locale_dir/images/phoneScreenshots"
    wear_dir="$locale_dir/images/wearScreenshots"
    phone_count=$(find "$phone_dir" -maxdepth 1 -type f -iname '*.png' | wc -l | tr -d ' ')
    wear_count=$(find "$wear_dir" -maxdepth 1 -type f -iname '*.png' | wc -l | tr -d ' ')
    [[ "$phone_count" -ge 4 ]] || fail "$locale needs at least four phone screenshots"
    [[ "$wear_count" -ge 2 ]] || fail "$locale needs at least two Wear screenshots"
    while IFS= read -r screenshot; do
        dimensions=$(image_size "$screenshot"); width=${dimensions%x*}; height=${dimensions#*x}
        [[ "$width" -ge 320 && "$width" -le 3840 && "$height" -ge 320 && "$height" -le 3840 ]] || fail "$locale phone screenshot dimensions are outside Play limits"
        [[ "$width" -lt "$height" ]] || fail "$locale phone screenshot is not portrait"
        [[ "$height" -le $((2 * width)) ]] || fail "$locale phone screenshot exceeds Play's 2:1 aspect-ratio limit"
        [[ $(image_has_alpha "$screenshot") == 'no' ]] || fail "$locale phone screenshot must not have alpha"
    done < <(find "$phone_dir" -maxdepth 1 -type f -iname '*.png' | sort)
    while IFS= read -r screenshot; do
        verify_wear_screenshot "$screenshot"
    done < <(find "$wear_dir" -maxdepth 1 -type f -iname '*.png' | sort)
done

require_text fastlane/Fastfile 'upload_mobile_internal'
require_text fastlane/Fastfile 'publish_metadata'
require_text fastlane/Fastfile 'app.zhanzhuang.timer'
require_text fastlane/Fastfile 'upload_wear_internal'
require_text fastlane/Fastfile 'wear:internal'
require_text fastlane/Fastfile 'promote_mobile_production'
require_text fastlane/Fastfile 'track_promote_to: "production"'
require_text fastlane/Fastfile 'promote_wear_production'
require_text fastlane/Fastfile 'track_promote_to: "wear:production"'
require_text fastlane/Fastfile 'track_promote_release_status: "draft"'

./gradlew test :mobile:lintRelease :wear:lintRelease :mobile:bundleRelease :wear:bundleRelease

java_home=${JAVA_HOME:-}
if [[ -z "$java_home" || ! -x "$java_home/bin/jarsigner" ]]; then
    java_home=$(dirname "$(dirname "$(command -v jarsigner)")")
fi
jarsigner="$java_home/bin/jarsigner"
keytool="$java_home/bin/keytool"
[[ -x "$jarsigner" && -x "$keytool" ]] || fail 'a JDK with jarsigner and keytool is required'

verify_jarsigner() {
    local aab=$1 output status
    # Android upload certificates are intentionally self-signed, so JDK jarsigner's
    # strict trust-chain exit is expected. Still require its strict integrity pass.
    set +e
    output=$(LC_ALL=C "$jarsigner" -verify -strict "$aab" 2>&1)
    status=$?
    set -e
    [[ "$output" == *'jar verified'* ]] || fail "jarsigner did not verify ${aab#$repo_root/}"
    if [[ $status -ne 0 ]]; then
        [[ $status -eq 4 && "$output" == *'certificate chain is invalid'* && "$output" == *'self-signed'* ]] || fail "jarsigner strict verification failed for ${aab#$repo_root/}"
    fi
}

mobile_aab=mobile/build/outputs/bundle/release/mobile-release.aab
wear_aab=wear/build/outputs/bundle/release/wear-release.aab
for aab in "$mobile_aab" "$wear_aab"; do
    require_file "$aab"
    verify_jarsigner "$aab"
done

certificate_sha256() { "$keytool" -printcert -jarfile "$1" 2>/dev/null | awk -F': ' '/SHA256:/{print $2; exit}'; }
mobile_certificate=$(certificate_sha256 "$mobile_aab")
wear_certificate=$(certificate_sha256 "$wear_aab")
[[ -n "$mobile_certificate" && "$mobile_certificate" == "$wear_certificate" ]] || fail 'mobile and Wear AABs must use the same upload certificate'
[[ "$mobile_certificate" == "$expected_fingerprint" ]] || fail 'AAB certificate does not match the approved external upload key'

bundletool=${BUNDLETOOL:-$(command -v bundletool || true)}
[[ -n "$bundletool" ]] || fail 'bundletool is required to inspect AAB identities'
verify_bundle_manifest() {
    local module=$1 aab=$2 version_code=$3 manifest
    manifest=$($bundletool dump manifest --bundle="$aab") || fail "$module AAB manifest cannot be read"
    [[ "$manifest" == *'package="app.zhanzhuang.timer"'* ]] || fail "$module AAB package is incorrect"
    [[ "$manifest" == *"versionCode=\"$version_code\""* ]] || fail "$module AAB version code is incorrect"
    [[ "$manifest" == *'versionName="0.1.0"'* ]] || fail "$module AAB version name is incorrect"
    [[ "$manifest" == *'targetSdkVersion="36"'* ]] || fail "$module AAB target SDK is incorrect"
    [[ "$manifest" == *'android.permission.INTERNET'* ]] && fail "$module AAB must not request INTERNET"
    [[ "$manifest" == *'android.permission.ACCESS_FINE_LOCATION'* ]] && fail "$module AAB must not request location"
    [[ "$manifest" == *'com.google.android.gms.permission.AD_ID'* ]] && fail "$module AAB must not request Advertising ID"
    localized_resources="$module/build/intermediates/packaged_res/release/packageReleaseResources/values-zh-rCN/values-zh-rCN.xml"
    require_file "$localized_resources"
    require_text "$localized_resources" '站桩'
    if [[ "$module" == wear ]]; then
        [[ "$manifest" == *'android.hardware.type.watch'* && "$manifest" == *'com.google.android.wearable.standalone'* ]] || fail 'Wear AAB form-factor declarations are incorrect'
    else
        [[ "$manifest" != *'android.hardware.type.watch'* ]] || fail 'mobile AAB must not require watch hardware'
    fi
}
verify_bundle_manifest mobile "$mobile_aab" 1000001
verify_bundle_manifest wear "$wear_aab" 2000001

for aab in "$mobile_aab" "$wear_aab"; do
    printf 'Verified artifact: %s sha256=%s bytes=%s upload-cert-sha256=%s\n' \
        "$aab" "$(shasum -a 256 "$aab" | awk '{print $1}')" "$(stat -f '%z' "$aab")" "$mobile_certificate"
done
printf 'PASS: signed Google Play release candidates verified.\n'
