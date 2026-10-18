#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
test_dir="$project_dir/build/summary-transport-tests"
test -r "$CHAT_JSON_JAR"
mkdir -p "$test_dir"
javac --release 11 --add-modules jdk.httpserver -cp "$CHAT_JSON_JAR" -d "$test_dir" \
 "$project_dir"/app/src/main/java/dev/pocketllama/core/{SseReader,TextAttachment,LoopbackHttp,GgufMetadata,GenerationProfile}.java \
 "$project_dir/app/src/main/java/dev/pocketllama/ChatApi.java" \
 "$project_dir"/app/src/main/java/dev/pocketllama/voice/{SummarySource,LocalSummary}.java \
 "$project_dir/tests/SummaryTransportTests.java"
fixture="$project_dir/tests/fixtures/summary-0.6.2-checkpoint.json"
java --add-modules jdk.httpserver -cp "$test_dir:$CHAT_JSON_JAR" SummaryTransportTests --checkpoint "$fixture"
if [[ -n "${ANDROID_HTTP_TEST_CP:-}" ]]; then
 java --add-modules jdk.httpserver -cp "$test_dir:$CHAT_JSON_JAR:$ANDROID_HTTP_TEST_CP" SummaryTransportTests --android-http --checkpoint "$fixture"
fi
