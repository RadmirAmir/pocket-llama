#!/usr/bin/env bash
set -euo pipefail
search_project="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
test -r "${CHAT_JSON_JAR:?Set CHAT_JSON_JAR}"
search_build="$search_project/build/search-core"
mkdir -p "$search_build"
javac --release 11 --add-modules jdk.httpserver -encoding UTF-8 -cp "$CHAT_JSON_JAR" -d "$search_build" \
 "$search_project"/app/src/main/java/dev/pocketllama/core/*.java \
 "$search_project/app/src/main/java/dev/pocketllama/ChatApi.java" \
 "$search_project"/app/src/main/java/dev/pocketllama/search/{WebQuery,WebPrompt,SearchErrors}.java "$search_project/tests/WebCoreTests.java"
java --add-modules jdk.httpserver -cp "$search_build:$CHAT_JSON_JAR" WebCoreTests

bash "$search_project/scripts/test-agent-core.sh"
