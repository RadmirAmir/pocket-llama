#!/usr/bin/env bash
set -euo pipefail
rag_project="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
test -r "${CHAT_JSON_JAR:?Set CHAT_JSON_JAR}"
rag_build="$rag_project/build/rag-core"
mkdir -p "$rag_build"
javac --release 11 --add-modules jdk.httpserver -encoding UTF-8 -cp "$CHAT_JSON_JAR" -d "$rag_build" \
 "$rag_project"/app/src/main/java/dev/pocketllama/core/*.java \
 "$rag_project/app/src/main/java/dev/pocketllama/ChatApi.java" \
 "$rag_project"/app/src/main/java/dev/pocketllama/rag/{RagText,RagMcp,RagPrompt}.java "$rag_project/tests/RagCoreTests.java"
java --add-modules jdk.httpserver -cp "$rag_build:$CHAT_JSON_JAR" RagCoreTests
python3 - "$rag_project" <<'PY'
import json,sys
from pathlib import Path
p=Path(sys.argv[1]);lock=json.loads((p/'rag-model.lock.json').read_text())
assert lock==json.loads((p/'app/src/main/assets/rag/model.json').read_text())
assert lock['dimensions']==768 and lock['pooling']=='mean' and len(lock['sha256'])==64 and len(lock['revision'])==40
for name in ['Gemma.txt','Gemma-terms.txt','Gemma-prohibited-uses.txt']:assert (p/'notices'/name).stat().st_size>500
print('PASS: embedding pin, packaged model metadata and notices')
PY
