#!/usr/bin/env bash
# Collect non-sensitive local Android/Wear device evidence. It never declares a QA pass.
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
package_name=${ZHANZHUANG_EVIDENCE_PACKAGE:-app.zhanzhuang.timer.debug}
output_path=''

usage() {
    cat <<'EOF'
Usage: scripts/collect_device_evidence.sh [--output docs/qa/file.md]

Collects anonymized connected-device evidence. Raw ADB serials and health samples are
never printed or persisted. Result status remains Not automatically passed.
EOF
}

while (($#)); do
    case "$1" in
        --output) [[ $# -ge 2 ]] || { usage >&2; exit 2; }; output_path=$2; shift 2 ;;
        --help|-h) usage; exit 0 ;;
        *) usage >&2; exit 2 ;;
    esac
done

fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
command -v adb >/dev/null 2>&1 || fail 'adb is required. Install Android platform-tools and retry.'
if [[ -n "$output_path" ]]; then
    [[ "$output_path" = /* ]] || output_path="$repo_root/$output_path"
    if [[ "$output_path" != "$repo_root/docs/qa/"* ]]; then
        fail 'refusing to write outside docs/qa; use --output docs/qa/<file>.md'
    fi
fi

declare -a serials=()
while IFS=$'\t' read -r serial state _; do
    [[ "$state" == device ]] && serials+=("$serial")
done < <(adb devices -l 2>/dev/null | awk 'NR > 1 && $2 == "device" { print $1 "\t" $2 }')

lines=()
append() { lines+=("$1"); }
append '# Local Android / Wear device evidence'
append ''
append "- Collected at (UTC): $(date -u +'%Y-%m-%dT%H:%M:%SZ')"
append "- Package inspected: \`$package_name\`"
append '- Privacy: raw ADB serials and health samples were neither printed nor persisted.'
append '- Result: Not automatically passed — an operator must assess each scenario against the QA matrix.'
append ''

if ((${#serials[@]} == 0)); then
    append '## Connected devices'
    append ''
    append 'No usable ADB devices were attached. No device evidence was collected.'
else
    for index in "${!serials[@]}"; do
        serial=${serials[$index]}
        label="connected-device-$((index + 1))"
        shell_value() { adb -s "$serial" shell "$1" 2>/dev/null | tr -d '\r' | head -n 1; }
        os_version=$(shell_value 'getprop ro.build.version.release')
        api_level=$(shell_value 'getprop ro.build.version.sdk')
        qemu=$(shell_value 'getprop ro.kernel.qemu')
        features=$(adb -s "$serial" shell pm list features 2>/dev/null || true)
        if [[ "$features" == *'android.hardware.type.watch'* ]]; then form_factor=watch; else form_factor=phone-or-tablet; fi
        if [[ "$qemu" == 1 ]]; then device_class=emulator; else device_class=physical-device; fi
        package_dump=$(adb -s "$serial" shell dumpsys package "$package_name" 2>/dev/null || true)
        version=$(sed -nE 's/^[[:space:]]*versionName=([^[:space:]]+).*/\1/p' <<<"$package_dump" | head -n 1)
        [[ -n "$version" ]] || version='not-installed'
        permission_state() {
            local permission=$1
            if grep -Eq "${permission}:.*granted=true|${permission}: granted=true" <<<"$package_dump"; then printf 'granted';
            elif grep -Fq "$permission" <<<"$package_dump"; then printf 'declared-not-granted';
            else printf 'not-declared-or-package-unavailable'; fi
        }
        services=$(adb -s "$serial" shell dumpsys activity services "$package_name" 2>/dev/null || true)
        notifications=$(adb -s "$serial" shell dumpsys notification 2>/dev/null || true)
        if [[ "$services" == *"$package_name"* ]]; then fgs_state='service-observed'; else fgs_state='not-observed'; fi
        if grep -Eq "NotificationRecord.*pkg=${package_name}([[:space:]]|$)" <<<"$notifications"; then notification_state='notification-observed'; else notification_state='not-observed'; fi

        append "## $label"
        append ''
        append "- Device class: $device_class"
        append "- Model class: $form_factor"
        append "- OS / API: ${os_version:-unknown} / ${api_level:-unknown}"
        append "- Installed version: $version"
        append "- Foreground-service state: $fgs_state"
        append "- Notification state: $notification_state"
        append "- Notification permission: $(permission_state android.permission.POST_NOTIFICATIONS)"
        append "- Heart-rate permission: $(permission_state android.permission.health.READ_HEART_RATE)"
        append "- Legacy body-sensor permission: $(permission_state android.permission.BODY_SENSORS)"
        append '- Health samples: intentionally not queried.'
        append ''
    done
fi

if [[ -n "$output_path" ]]; then
    mkdir -p "$(dirname "$output_path")"
    printf '%s\n' "${lines[@]}" >"$output_path"
    printf 'Wrote anonymized evidence to %s\n' "$output_path"
else
    printf '%s\n' "${lines[@]}"
fi
