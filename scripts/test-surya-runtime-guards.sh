#!/usr/bin/env bash
set -euo pipefail
surya_project="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
test -r "${CHAT_JSON_JAR:?Set CHAT_JSON_JAR}"
surya_build="$surya_project/build/surya-runtime-guards"
mkdir -p "$surya_build/classes" "$surya_build/native" "$surya_build/files/surya-runtime/models"
cp "$surya_project/tests/fixtures/surya-scripted-server.py" "$surya_build/native/libllama_cpu.so"
chmod 700 "$surya_build/native/libllama_cpu.so"
touch "$surya_build/files/surya-runtime/models/surya-2.gguf"
surya_cp="$CHAT_JSON_JAR:$surya_project/app/libs/jsoup-1.21.2.jar"
javac --release 11 -encoding UTF-8 -cp "$surya_cp" -d "$surya_build/classes" \
 "$surya_project"/tests/runtime-stubs/android/content/{Context,pm/ApplicationInfo}.java \
 "$surya_project"/tests/runtime-stubs/android/{os/Process,util/Base64}.java \
 "$surya_project"/tests/pipeline-stubs/android/{graphics/Bitmap,os/SystemClock}.java \
 "$surya_project/tests/runtime-stubs/dev/pocketllama/photo/ScanImages.java" \
 "$surya_project"/app/src/main/java/dev/pocketllama/core/*.java \
 "$surya_project/app/src/main/java/dev/pocketllama/ChatApi.java" \
 "$surya_project"/app/src/main/java/dev/pocketllama/photo/{SuryaRuntime,SuryaProtocol,SuryaInputProgress,SuryaImageSize,SuryaPageHtml,SuryaOutputGuard,SuryaDeadline,SafeOcrHtml}.java \
 "$surya_project/tests/SuryaRuntimeGuardTests.java"
java -Dsurya.fixture="$surya_project/tests/fixtures/scan-page.jpg" -cp "$surya_build/classes:$surya_cp" SuryaRuntimeGuardTests "$surya_build" "$surya_build/files/surya-runtime/models/surya-2.gguf" "$surya_build/native"
