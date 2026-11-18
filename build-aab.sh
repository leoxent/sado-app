#!/usr/bin/env bash
# Builds the Play Store bundle, signed with the upload key in C:\Sado-App\keys (see build.gradle.kts).
set -e
export TMP='C:\Sado-App\toolchain\tmp' TEMP='C:\Sado-App\toolchain\tmp'
export JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Sado-App\toolchain\tmp -Djava.io.tmpdir=C:\Sado-App\toolchain\tmp'
export JAVA_HOME='C:\Sado-App\toolchain\jdk17' ANDROID_HOME='C:\Sado-App\toolchain\android-sdk'
cd "$(dirname "$0")"
./gradlew bundleRelease -Pplay --console=plain
ver=$(grep -oE 'versionName = "[^"]+"' app/build.gradle.kts | cut -d'"' -f2)
cp app/build/outputs/bundle/release/app-release.aab "../apk/sado-$ver.aab"
echo "../apk/sado-$ver.aab"
