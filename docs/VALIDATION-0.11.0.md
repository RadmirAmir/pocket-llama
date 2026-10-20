# Personal knowledge preview validation — 0.11.0

Candidate checks on 2026-10-20:

- All Android Java sources compile against API 36 / Java 11.
- 447 host assertions cover complete Unicode passage coverage, byte limits, normalized vectors, disconnected-tool access, MCP input validation, protocol results and tokenizer-based context fitting. The context transport fixture uses a synthetic tokenizer, not a claimed chat-model quality benchmark.
- The exact pinned Unsloth Q8_0 GGUF was loaded using the runtime revision in `llama.lock`, with mean pooling and the documented query/document task prefixes. On the small synthetic fixture of 12 RU/EN documents, all 9 queries ranked the expected document first, including cross-language queries. See [recorded host result](rag-live-0.11.0.json) and [reproducible script](../scripts/test-rag-live.py).
- That host run used Linux x86_64 and three CPU threads: model load about 1.01 seconds, median short-query embedding 39.77 ms, peak child RSS 385,560 KiB. These are host observations, **not Android speed, battery, latency or memory guarantees**. The fixture is a smoke check, not a general retrieval benchmark.
- The Android SQLCipher regression compiles with production source readers and index code. It exercises FTS5, notes beyond UI pagination, transcript edits after turn 100, legacy/page OCR, encrypted storage, stale-source rejection, incremental updates and cancelled-index resume. Execution on Android and the published-APK install/update check will be recorded below after CI completes.
- RU/EN browser tests cover explicit connection, disconnected requests, source references, hostile markup as text, narrow layout and new-conversation disconnection. Local Chromium is blocked by the workspace's Unix-socket restriction; execution is delegated to the repository's existing frontend CI job.

No physical-phone retrieval speed or end-to-end answer accuracy is claimed. The public community package and existing signing identity must be preserved for in-place upgrades.

## Published candidate artifact

APK: 640863630 bytes. SHA-256: `82df8edbf229553ec4e960c275baa585a5d035480095e0d0c685dc7fc2e3c0e9`. All 16 bundled model files and 6 video program files passed their pinned checks. RAG metadata, model terms and native classes are present; embedding weights remain a separate download. Resource-table storage/alignment, 16 KiB ZIP alignment and the existing signing certificate were checked. See [artifact report](artifact-validation-0.11.0.json).
