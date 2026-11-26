#!/usr/bin/env bash
# run:android and run:android:release: prints the serial of the device to install on, from `adb devices`. A USB device
# first, then a wireless one (`adb pair`/`adb connect`, or mDNS), then an emulator; the first of each kind in adb's
# order. Fails when nothing usable is attached, naming the ones adb sees but cannot use (unauthorized, offline).
set -euo pipefail

command -v adb >/dev/null || { echo "adb is not on PATH (Android SDK platform-tools)." >&2; exit 1; }

usb=() wireless=() emulator=() unusable=()
while read -r serial state _; do
	[ -n "$serial" ] || continue
	if [ "$state" != device ]; then
		unusable+=("$serial ($state)")
	elif [[ $serial == emulator-* ]]; then
		emulator+=("$serial")
	elif [[ $serial == *:* || $serial == *._tcp ]]; then
		wireless+=("$serial")
	else
		usb+=("$serial")
	fi
done < <(adb devices | tail -n +2)

for serial in "${usb[@]}" "${wireless[@]}" "${emulator[@]}"; do
	echo "Installing on $serial" >&2
	echo "$serial"
	exit 0
done

echo "No Android device attached: connect one over USB with USB debugging on, or pair one with adb pair / adb connect." >&2
if [ ${#unusable[@]} -gt 0 ]; then
	echo "adb sees, but cannot use: ${unusable[*]}. Accept the debugging prompt on the device, or reconnect it." >&2
fi
exit 1
