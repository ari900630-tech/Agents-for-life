#!/usr/bin/env bash
set -Eeuo pipefail

PACKAGE_NAME="com.ari900630.agentsforlife"
APK="android/app/build/outputs/apk/debug/app-debug.apk"
LOG_FILE="android-startup-log.txt"

adb install -r "$APK"
adb logcat -c

adb shell am force-stop "$PACKAGE_NAME"
adb shell monkey -p "$PACKAGE_NAME" 1

sleep 8
adb logcat -d -v threadtime > "$LOG_FILE"

if grep -q "FATAL EXCEPTION" "$LOG_FILE"; then
  echo "Android startup crash detected"
  grep -A40 -B5 "FATAL EXCEPTION" "$LOG_FILE" || true
  exit 1
fi

if ! adb shell dumpsys activity activities | grep -q "$PACKAGE_NAME"; then
  echo "Launcher activity was not found after startup"
  tail -n 120 "$LOG_FILE"
  exit 1
fi

echo "Android 11 startup smoke test passed"
