#!/bin/bash
# Build MockLocation APK
# Requires JDK 21+ (or set JAVA_HOME to a supported JDK)

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
export JAVA_HOME

echo "Using JDK: $JAVA_HOME"
"$JAVA_HOME/bin/java" -version 2>&1 | head -1

./gradlew clean assembleDebug

echo ""
echo "APK: $SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"
ls -lh "$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"
