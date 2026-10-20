#!/usr/bin/env bash
set -euo pipefail
agent_project="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
test -r "${CHAT_JSON_JAR:?Set CHAT_JSON_JAR}"
agent_build="$agent_project/build/agent-core"
mkdir -p "$agent_build"
javac --release 11 --add-modules jdk.httpserver -encoding UTF-8 -cp "$CHAT_JSON_JAR" -d "$agent_build" \
 "$agent_project"/app/src/main/java/dev/pocketllama/core/*.java \
 "$agent_project/app/src/main/java/dev/pocketllama/ChatApi.java" \
 "$agent_project/app/src/main/java/dev/pocketllama/agent/ChatAgent.java" "$agent_project/tests/ChatAgentTests.java"
java --add-modules jdk.httpserver -cp "$agent_build:$CHAT_JSON_JAR" ChatAgentTests
