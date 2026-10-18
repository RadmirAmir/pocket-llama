#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
android_sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
test -r "$android_sdk/platforms/android-35/android.jar"
test -r "$project_dir/build/sdk-apk/classes/dev/pocketllama/security/Vault.class" || { echo 'Build APK once before running this host test'; exit 1; }
test_dir="$project_dir/build/vault-tests"
mkdir -p "$test_dir"
classpath="$project_dir/build/sdk-apk/classes:$android_sdk/platforms/android-35/android.jar:$project_dir/app/libs/sqlcipher-android-4.19.1.jar:$project_dir/app/libs/androidx-sqlite-2.7.0.jar"
javac --release 11 -cp "$classpath" -d "$test_dir" "$project_dir"/app/src/main/java/dev/pocketllama/security/{ChunkedFile,LegacyWav,PinCrypto,VaultMigration}.java "$project_dir/app/src/main/java/dev/pocketllama/voice/PcmFile.java" "$project_dir/tests/VaultCoreTests.java"
java -XX:-UsePerfData -cp "$test_dir:$classpath" VaultCoreTests
