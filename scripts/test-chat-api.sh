#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "$0")/.." && pwd)"
test -r "$CHAT_JSON_JAR"
test_dir="$project_dir/build/chat-api-tests"
mkdir -p "$test_dir/classes"
javac --add-modules jdk.httpserver -cp "$CHAT_JSON_JAR" -d "$test_dir/classes" \
  "$project_dir/app/src/main/java/dev/pocketllama/core/TextAttachment.java" \
  "$project_dir/app/src/main/java/dev/pocketllama/core/SseReader.java" \
  "$project_dir/app/src/main/java/dev/pocketllama/core/LoopbackHttp.java" \
  "$project_dir/app/src/main/java/dev/pocketllama/core/GgufMetadata.java" \
  "$project_dir/app/src/main/java/dev/pocketllama/core/GenerationProfile.java" \
  "$project_dir/app/src/main/java/dev/pocketllama/ChatApi.java" "$project_dir/tests/ChatApiTests.java"
java --add-modules jdk.httpserver -cp "$test_dir/classes:$CHAT_JSON_JAR" ChatApiTests
