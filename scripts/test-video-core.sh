#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
test_dir="$project_dir/build/video-core-tests"
mkdir -p "$test_dir"
javac --release 11 -cp "$CHAT_JSON_JAR" -d "$test_dir" \
 "$project_dir/app/src/main/java/dev/pocketllama/security/ChunkedFile.java" \
 "$project_dir/app/src/main/java/dev/pocketllama/video/VideoUrl.java" \
 "$project_dir/app/src/main/java/dev/pocketllama/video/VideoDownloader.java" \
 "$project_dir/tests/video-stubs/VideoRuntime.java" "$project_dir/tests/VideoCoreTests.java"
java -cp "$test_dir:$CHAT_JSON_JAR" VideoCoreTests "$project_dir/tests/video-fixture.py"
