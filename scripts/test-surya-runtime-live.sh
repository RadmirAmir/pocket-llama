#!/usr/bin/env bash
set -euo pipefail
surya_project="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
test -r "${CHAT_JSON_JAR:?Set CHAT_JSON_JAR}"
test "$#" -eq 4 || { echo 'Usage: test-surya-runtime-live.sh HOST_SERVER MODEL MMPROJ JPEG_FIXTURE' >&2; exit 1; }
surya_server="$(realpath "$1")";surya_model="$(realpath "$2")";surya_projector="$(realpath "$3")";surya_fixture="$(realpath "$4")"
surya_build="$surya_project/build/surya-runtime-live";mkdir -p "$surya_build/classes" "$surya_build/native" "$surya_build/files/surya-runtime/models" "$surya_build/files/surya-runtime/projectors"
ln -sfn "$surya_server" "$surya_build/native/libllama_cpu.so"
ln -sfn "$surya_model" "$surya_build/files/surya-runtime/models/surya-2.gguf"
ln -sfn "$surya_projector" "$surya_build/files/surya-runtime/projectors/surya-2-mmproj.gguf"
surya_cp="$CHAT_JSON_JAR:$surya_project/app/libs/jsoup-1.21.2.jar"
javac --release 11 -encoding UTF-8 -cp "$surya_cp" -d "$surya_build/classes" \
 "$surya_project"/tests/runtime-stubs/android/content/{Context,pm/ApplicationInfo}.java \
 "$surya_project"/tests/runtime-stubs/android/{os/Process,util/Base64}.java \
 "$surya_project"/tests/pipeline-stubs/android/{graphics/Bitmap,os/SystemClock}.java \
 "$surya_project/tests/runtime-stubs/dev/pocketllama/photo/ScanImages.java" \
 "$surya_project"/app/src/main/java/dev/pocketllama/core/*.java \
 "$surya_project/app/src/main/java/dev/pocketllama/ChatApi.java" \
 "$surya_project"/app/src/main/java/dev/pocketllama/photo/{SuryaRuntime,SuryaProtocol,SuryaInputProgress,SuryaImageSize,SuryaPageHtml,SuryaOutputGuard,SuryaDeadline,SafeOcrHtml}.java \
 "$surya_project"/tests/{SuryaRuntimeLive,SuryaRuntimeGuardTests}.java
java -Dsurya.fixture="$surya_fixture" -cp "$surya_build/classes:$surya_cp" SuryaRuntimeLive "$surya_build" "$surya_build/files/surya-runtime/models/surya-2.gguf" "$surya_build/native"
