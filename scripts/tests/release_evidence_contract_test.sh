#!/usr/bin/env bash
# Contract tests for release verification and secret-safe evidence collection.
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
checks="$repo_root/scripts/run_android_checks.sh"
collector="$repo_root/scripts/collect_device_evidence.sh"
sdk23_fixture="$repo_root/scripts/tests/fixtures/forbidden-sdk23-permissions.xml"

fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
require_file() { [[ -f "$1" ]] || fail "missing $1"; }
require_text() { grep -Fq -- "$2" "$1" || fail "$1 must contain: $2"; }

require_file "$checks"
require_file "$collector"
require_file "$sdk23_fixture"
require_text "$repo_root/mobile/build.gradle.kts" 'applicationIdSuffix = ".debug"'
require_text "$repo_root/wear/build.gradle.kts" 'applicationIdSuffix = ".debug"'

require_text "$checks" ':mobile:test'
require_text "$checks" ':wear:test'
require_text "$checks" ':mobile:lintDebug'
require_text "$checks" ':wear:lintDebug'
require_text "$checks" ':mobile:assembleDebug'
require_text "$checks" ':wear:assembleDebug'
require_text "$checks" ':mobile:processReleaseManifest'
require_text "$checks" ':wear:processReleaseManifest'
require_text "$checks" 'resolve_java_home'
require_text "$checks" 'export ANDROID_HOME='
require_text "$checks" 'app.zhanzhuang.timer.debug'
require_text "$checks" 'versionName'
require_text "$checks" "sdkVersion:'"
require_text "$checks" "targetSdkVersion:'"
require_text "$checks" "compileSdkVersion='36'"
require_text "$checks" 'xmllint'
require_text "$checks" 'android.permission.POST_NOTIFICATIONS'
require_text "$checks" 'android.permission.BODY_SENSORS_BACKGROUND'
require_text "$checks" 'android.permission.ACCESS_BACKGROUND_LOCATION'
require_text "$checks" 'app.zhanzhuang.timer'
require_text "$checks" 'android.hardware.type.watch'
require_text "$checks" 'com.google.android.wearable.standalone'
require_text "$checks" 'FOREGROUND_SERVICE_HEALTH'
require_text "$checks" 'READ_HEART_RATE'
require_text "$checks" 'config zh-rCN'
require_text "$checks" 'dump --values resources'
require_text "$checks" 'INTERNET'
require_text "$checks" 'ACCESS_FINE_LOCATION'
require_text "$checks" "starts-with(local-name(),'uses-permission')"
[[ ! $(grep -E '(^|[[:space:]])clean($|[[:space:]])' "$checks") ]] || fail 'release checks must not invoke gradlew clean'

require_text "$collector" 'connected-device-'
require_text "$collector" 'adb devices -l'
require_text "$collector" 'getprop ro.build.version.release'
require_text "$collector" 'dumpsys notification'
require_text "$collector" 'dumpsys activity services'
require_text "$collector" 'raw ADB serial'
require_text "$collector" 'health samples'
require_text "$collector" 'Result: Not automatically passed'
[[ ! $(grep -E 'mktemp|adb devices -l[[:space:]]*>' "$collector") ]] || fail 'collector must keep ADB serials in memory and must not persist adb devices output'

for forbidden_permission in \
    android.permission.INTERNET \
    android.permission.ACCESS_FINE_LOCATION \
    android.permission.ACCESS_COARSE_LOCATION \
    android.permission.ACCESS_BACKGROUND_LOCATION; do
    matches=$(xmllint --xpath "count(//*[starts-with(local-name(),'uses-permission') and @*[local-name()='name']='$forbidden_permission'])" "$sdk23_fixture" 2>/dev/null) || fail "cannot inspect SDK-23 privacy fixture"
    [[ "$matches" == 1 ]] || fail "SDK-23 privacy fixture must be rejected for $forbidden_permission"
done

printf 'PASS: release evidence script contracts\n'
