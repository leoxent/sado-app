#!/usr/bin/env bash
# Builds the debug and the release APK with the project-local toolchain. The short temp dir avoids
# Java's "Unable to establish loopback connection" on long Windows temp paths.
set -e
export TMP='C:\Sado-App\toolchain\tmp' TEMP='C:\Sado-App\toolchain\tmp'
export JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Sado-App\toolchain\tmp -Djava.io.tmpdir=C:\Sado-App\toolchain\tmp'
export JAVA_HOME='C:\Sado-App\toolchain\jdk17' ANDROID_HOME='C:\Sado-App\toolchain\android-sdk'
cd "$(dirname "$0")"
./gradlew assembleDebug assembleRelease --console=plain
cp app/build/outputs/apk/debug/app-debug.apk ../apk/sado-latest-debug.apk
# release: shrunk by R8, signed with the same debug key so it updates the installed app
cp app/build/outputs/apk/release/app-release.apk ../apk/sado-latest.apk
