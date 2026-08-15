# Build, install and run the zip64 debug APK on an emulator.
#
# Two things this file is deliberately opinionated about, both learned the hard way
# on this machine:
#
#   * ADB/EMULATOR are the *SDK* binaries, not Fedora's /usr/bin/adb (35.0.2), which
#     is first in PATH and speaks a different protocol version than the SDK's
#     platform-tools (37.0.1) — mixing them causes spurious "device offline" churn.
#   * The AVD must run with hw.gpu.mode=host. The bundled SwiftShader software GLES
#     SIGSEGVs on this laptop's hybrid AMD/NVIDIA setup. Both AVDs below are already
#     configured that way; `check-avd` fails loudly if that ever gets reverted.

set shell := ["bash", "-uc"]

sdk      := env_var('HOME') / "Android/Sdk"
adb      := sdk / "platform-tools/adb"
emulator := sdk / "emulator/emulator"

# 24 G data partition — the signed zip64 APK is ~1.5 GB, and the emulator needs room
# for both the staged copy and the installed one. medium_phone's 6 G is not enough.
avd := "bigdisk_api36"

apk := justfile_directory() / "android/build/outputs/apk_zip64/debug/app-debug-signed-zip64.apk"

pkg      := "aoc.kingdoms.lukasz.jakowski"
activity := pkg + "/.android.AndroidLauncher"

_default:
    @just --list

# Build, boot the emulator, install and launch.
run: build boot install launch

# Sign + pack the zip64 APK (AGP's own packaging task is disabled for this variant).
build:
    ./gradlew :android:signZip64Debug
    @ls -lh "{{apk}}"

# Fail early if the AVD lost its host-GPU setting.
check-avd:
    #!/usr/bin/env bash
    set -euo pipefail
    cfg="$HOME/.android/avd/{{avd}}.avd/config.ini"
    [[ -f "$cfg" ]] || { echo "No such AVD: {{avd}} ($cfg)"; exit 1; }
    grep -qx 'hw.gpu.mode=host' "$cfg" || {
        echo "{{avd}} is not set to hw.gpu.mode=host — software GLES will segfault."
        echo "Fix: set hw.gpu.enabled=yes and hw.gpu.mode=host in $cfg"
        exit 1
    }

# Start the emulator (if not already running) and wait for boot to complete.
boot: check-avd
    #!/usr/bin/env bash
    set -euo pipefail
    if [[ -n "$({{adb}} devices | awk '/^emulator-.*device$/ {print $1}')" ]]; then
        echo "Emulator already running."
        exit 0
    fi
    # The laptop has 13 GiB total and this AVD asks for 4 GiB; a global OOM here kills
    # qemu itself, not the app, which looks like a mysterious emulator crash.
    avail=$(free -m | awk '/^Mem:/ {print $7}')
    (( avail > 4500 )) || echo "WARNING: only ${avail} MiB available; close some apps or the kernel may OOM-kill qemu."

    "{{emulator}}" -avd {{avd}} -gpu host -no-boot-anim -no-snapshot >/dev/null 2>&1 &
    {{adb}} wait-for-device
    # wait-for-device returns as soon as adbd answers, long before the framework is up.
    until [[ "$({{adb}} shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
        sleep 2
    done
    echo "Emulator booted."

# Install the signed APK. ~1.5 GB, so this takes a few minutes.
install:
    #!/usr/bin/env bash
    set -euo pipefail
    [[ -f "{{apk}}" ]] || { echo "APK not found — run 'just build' first: {{apk}}"; exit 1; }
    # -r reinstall, -t allow test-only builds, -d allow downgrade across rebuilds.
    time {{adb}} install -r -t -d "{{apk}}"

# Launch the activity (clears the log first so 'just logcat' starts at the launch).
launch:
    {{adb}} logcat -c
    {{adb}} shell am start -n "{{activity}}"

# Follow the app's log. Loading stalls at "Loading graphics 100%" mean a missing asset.
logcat:
    {{adb}} logcat -v time AndroidRuntime:E libGDX:V "{{pkg}}":V *:W

# Uninstall — worth doing between runs, a failed 1.5 GB install can leave staged data.
uninstall:
    -{{adb}} uninstall "{{pkg}}"

stop:
    -{{adb}} emu kill
