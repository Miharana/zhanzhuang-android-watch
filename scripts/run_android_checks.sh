#!/usr/bin/env bash
# Reproducible Android/Wear debug contract gate. This intentionally never runs Gradle clean.
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_root"

fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
need_command() { command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"; }
expect_contains() {
    local haystack=$1 needle=$2 subject=$3
    [[ "$haystack" == *"$needle"* ]] || fail "$subject must contain '$needle'"
}
expect_absent() {
    local haystack=$1 needle=$2 subject=$3
    [[ "$haystack" != *"$needle"* ]] || fail "$subject must not contain '$needle'"
}

resolve_android_home() {
    if [[ -n "${ANDROID_HOME:-}" && -d "$ANDROID_HOME" ]]; then printf '%s\n' "$ANDROID_HOME"; return; fi
    if [[ -n "${ANDROID_SDK_ROOT:-}" && -d "$ANDROID_SDK_ROOT" ]]; then printf '%s\n' "$ANDROID_SDK_ROOT"; return; fi
    local candidate
    for candidate in /opt/homebrew/share/android-commandlinetools "$HOME/Library/Android/sdk"; do
        [[ -d "$candidate" ]] && { printf '%s\n' "$candidate"; return; }
    done
    fail 'Android SDK not found. Set ANDROID_HOME to an SDK containing cmdline-tools and build-tools.'
}

android_home=$(resolve_android_home)
export ANDROID_HOME="$android_home"
export ANDROID_SDK_ROOT="$android_home"
apkanalyzer="$android_home/cmdline-tools/latest/bin/apkanalyzer"
[[ -x "$apkanalyzer" ]] || apkanalyzer=$(command -v apkanalyzer || true)
[[ -n "$apkanalyzer" && -x "$apkanalyzer" ]] || fail "apkanalyzer not found under $android_home/cmdline-tools/latest/bin; install Android command-line tools."
aapt=$(find "$android_home/build-tools" -type f -name aapt -perm -u+x 2>/dev/null | sort -V | tail -n 1 || true)
[[ -n "$aapt" ]] || fail "aapt not found under $android_home/build-tools; install a build-tools package."

resolve_java_home() {
    if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then printf '%s\n' "$JAVA_HOME"; return; fi
    local candidate
    for candidate in /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home /opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home; do
        [[ -x "$candidate/bin/java" ]] && { printf '%s\n' "$candidate"; return; }
    done
    fail 'Java 17+ is required. Set JAVA_HOME to a JDK (for example /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home).'
}

export JAVA_HOME=$(resolve_java_home)
export PATH="$JAVA_HOME/bin:$PATH"
need_command java

marker=$(mktemp "${TMPDIR:-/tmp}/zhan-zhuang-android-checks.XXXXXX")
trap 'rm -f "$marker"' EXIT
touch "$marker"

printf 'Running Android unit tests, lint, and debug assemblies (without Gradle clean)…\n'
./gradlew --rerun-tasks \
    :core:model:test :core:domain:test \
    :mobile:test :wear:test \
    :mobile:lintDebug :wear:lintDebug \
    :mobile:assembleDebug :wear:assembleDebug

mobile_apk="$repo_root/mobile/build/outputs/apk/debug/mobile-debug.apk"
wear_apk="$repo_root/wear/build/outputs/apk/debug/wear-debug.apk"
for apk in "$mobile_apk" "$wear_apk"; do
    [[ -s "$apk" ]] || fail "missing debug APK: $apk"
    [[ "$apk" -nt "$marker" ]] || fail "debug APK was not freshly produced during this run: $apk"
done

inspect_apk() {
    local module=$1 apk=$2 min_sdk=$3 version_code=$4
    local application_id badging manifest resources
    application_id=$("$apkanalyzer" manifest application-id "$apk") || fail "$module: apkanalyzer could not read $apk"
    [[ "$application_id" == 'app.zhanzhuang.timer.debug' ]] || fail "$module: expected debug application ID app.zhanzhuang.timer.debug, got $application_id"
    badging=$("$aapt" dump badging "$apk") || fail "$module: aapt could not inspect $apk"
    manifest=$("$apkanalyzer" manifest print "$apk") || fail "$module: apkanalyzer could not print manifest"
    resources=$("$aapt" dump --values resources "$apk") || fail "$module: aapt could not inspect resources"
    expect_contains "$badging" "versionCode='$version_code'" "$module badging"
    expect_contains "$badging" "versionName='0.1.0'" "$module badging"
    expect_contains "$badging" "sdkVersion:'$min_sdk'" "$module badging"
    expect_contains "$badging" "targetSdkVersion:'36'" "$module badging"
    expect_contains "$badging" "application-icon-" "$module badging (launcher icon)"
    expect_contains "$resources" 'config zh-rCN' "$module resources (Chinese locale)"
    expect_absent "$manifest" 'android.permission.INTERNET' "$module manifest"
    expect_absent "$manifest" 'android.permission.ACCESS_FINE_LOCATION' "$module manifest"
    expect_absent "$manifest" 'android.permission.ACCESS_COARSE_LOCATION' "$module manifest"
    printf 'Verified %s: %s\n' "$module" "$apk"
    printf '%s\n' "$manifest"
}

mobile_manifest=$(inspect_apk mobile "$mobile_apk" 28 1000001)
expect_contains "$mobile_manifest" 'android.permission.FOREGROUND_SERVICE' 'mobile manifest'
expect_contains "$mobile_manifest" 'android.permission.FOREGROUND_SERVICE_SPECIAL_USE' 'mobile manifest'
expect_contains "$mobile_manifest" 'android.permission.health.WRITE_MINDFULNESS' 'mobile manifest'
expect_contains "$mobile_manifest" 'foregroundServiceType="0x40000000"' 'mobile manifest (special-use foreground service)'
expect_contains "$mobile_manifest" 'android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE' 'mobile manifest (special-use justification)'

wear_manifest=$(inspect_apk wear "$wear_apk" 33 2000001)
expect_contains "$wear_manifest" 'android.hardware.type.watch' 'Wear manifest (watch form factor)'
expect_contains "$wear_manifest" 'com.google.android.wearable.standalone' 'Wear manifest'
expect_contains "$wear_manifest" 'android:value="true"' 'Wear manifest (standalone)'
expect_contains "$wear_manifest" 'android.permission.FOREGROUND_SERVICE_HEALTH' 'Wear manifest'
expect_contains "$wear_manifest" 'android.permission.health.READ_HEART_RATE' 'Wear manifest'
expect_contains "$wear_manifest" 'android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND' 'Wear manifest'
expect_contains "$wear_manifest" 'foregroundServiceType="0x100"' 'Wear manifest (health foreground service)'

printf 'PASS: Android and Wear debug release contracts verified.\n'
