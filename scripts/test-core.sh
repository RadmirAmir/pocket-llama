#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
test_dir="$project_dir/build/core-tests"
mkdir -p "$test_dir/classes" "$test_dir/fixtures"
javac -d "$test_dir/classes" "$project_dir"/app/src/main/java/dev/pocketllama/core/*.java "$project_dir/tests/CoreTests.java"
java -cp "$test_dir/classes" CoreTests "$test_dir/fixtures"

javac --release 11 -d "$test_dir/classes" "$project_dir"/app/src/main/java/dev/pocketllama/notes/{NoteDraft,NoteText}.java "$project_dir/tests/NoteDraftTests.java"
java -cp "$test_dir/classes" NoteDraftTests
CHAT_JSON_JAR="${CHAT_JSON_JAR:-$project_dir/build/test-deps/json.jar}" bash "$project_dir/scripts/test-rag-core.sh"
