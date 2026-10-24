#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
test -r "$CHAT_JSON_JAR"
test_dir="$project_dir/build/summary-pipeline-tests"
mkdir -p "$test_dir"
javac --add-modules jdk.httpserver -cp "$CHAT_JSON_JAR" -d "$test_dir" \
  "$project_dir"/app/src/main/java/dev/pocketllama/core/{SseReader,TextAttachment,LoopbackHttp,GgufMetadata,GenerationProfile}.java \
  "$project_dir/app/src/main/java/dev/pocketllama/ChatApi.java" \
  "$project_dir"/app/src/main/java/dev/pocketllama/voice/{SummarySource,LocalSummary}.java \
  "$project_dir/tests/SummaryPipelineTests.java" "$project_dir/tests/SummaryRecoveryTests.java" "$project_dir/tests/VideoSummaryContentTests.java"
java -XX:-UsePerfData --add-modules jdk.httpserver -cp "$test_dir:$CHAT_JSON_JAR" SummaryPipelineTests
java -XX:-UsePerfData --add-modules jdk.httpserver -cp "$test_dir:$CHAT_JSON_JAR" SummaryRecoveryTests
java -XX:-UsePerfData --add-modules jdk.httpserver -cp "$test_dir:$CHAT_JSON_JAR" VideoSummaryContentTests
