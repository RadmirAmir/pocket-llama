#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
: "${CHAT_JSON_JAR:?Set CHAT_JSON_JAR to org.json:json}"
test_dir="$project_dir/build/bundled-surya-tests"
mkdir -p "$test_dir"
javac --release 17 -cp "$CHAT_JSON_JAR" -d "$test_dir" \
 "$project_dir/tests/bundled-stubs/android/content/Context.java" \
 "$project_dir/tests/bundled-stubs/android/content/res/AssetManager.java" \
 "$project_dir/tests/bundled-stubs/dev/pocketllama/DistributionConfig.java" \
 "$project_dir/tests/pipeline-stubs/android/os/SystemClock.java" \
 "$project_dir/app/src/main/java/dev/pocketllama/core/TextAttachment.java" \
 "$project_dir/app/src/main/java/dev/pocketllama/photo/SuryaAssets.java" \
 "$project_dir/tests/BundledSuryaAssetsTests.java"
java -cp "$test_dir:$CHAT_JSON_JAR" BundledSuryaAssetsTests "$project_dir/.cache/surya"
