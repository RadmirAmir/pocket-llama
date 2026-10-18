#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
: "${CHAT_JSON_JAR:?Set CHAT_JSON_JAR to org.json:json (host tests only)}"
test_dir="$project_dir/build/distribution-tests"
mkdir -p "$test_dir"
javac --release 17 --add-modules jdk.httpserver -cp "$CHAT_JSON_JAR" -d "$test_dir" \
 "$project_dir/app/src/main/java/dev/pocketllama/models/ModelCatalog.java" \
 "$project_dir/app/src/main/java/dev/pocketllama/models/VerifiedDownload.java" \
 "$project_dir/tests/ModelDistributionTests.java"
java --add-modules jdk.httpserver -cp "$test_dir:$CHAT_JSON_JAR" ModelDistributionTests "$project_dir"

python3 -m unittest discover -s "$project_dir/tests" -p test_surya_distribution.py

javac --release 17 -d "$test_dir" \
 "$project_dir/app/src/main/java/dev/pocketllama/photo/LegacyScannerCache.java" \
 "$project_dir/tests/LegacyScannerCacheTests.java"
java -cp "$test_dir" LegacyScannerCacheTests

python3 -m unittest discover -s "$project_dir/tests" -p test_video_distribution.py
