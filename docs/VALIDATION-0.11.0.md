# Personal knowledge preview validation — 0.11.0

Verified RC.2 checks on 2026-10-20:

- All Android Java sources compile against API 36 / Java 11.
- 447 host assertions cover complete Unicode passage coverage, byte limits, normalized vectors, disconnected-tool access, MCP input validation, protocol results and tokenizer-based context fitting. The context transport fixture uses a synthetic tokenizer, not a claimed chat-model quality benchmark.
- The exact pinned Unsloth Q8_0 GGUF was loaded using the runtime revision in `llama.lock`, with mean pooling and the documented query/document task prefixes. On the small synthetic fixture of 12 RU/EN documents, all 9 queries ranked the expected document first, including cross-language queries. See [recorded host result](rag-live-0.11.0.json) and [reproducible script](../scripts/test-rag-live.py).
- That host run used Linux x86_64 and three CPU threads: model load about 1.01 seconds, median short-query embedding 39.77 ms, peak child RSS 385,560 KiB. These are host observations, **not Android speed, battery, latency or memory guarantees**. The fixture is a smoke check, not a general retrieval benchmark.
- The Android SQLCipher regression compiles with production source readers and index code. It exercises FTS5, notes beyond UI pagination, transcript edits after turn 100, legacy/page OCR, encrypted storage, stale-source rejection, incremental updates and cancelled-index resume. Execution passed on Android 16: 19 real SQLCipher knowledge checks, including canonical source fingerprints, full-source reads, edits, deletion, reopen, incremental updates and cancellation/resume.
- RU/EN browser tests cover explicit connection, disconnected requests, source references, hostile markup as text, narrow layout and new-conversation disconnection. These checks passed in the repository's frontend CI job. Local Chromium was blocked by the workspace's Unix-socket restriction.

No physical-phone retrieval speed or end-to-end answer accuracy is claimed. The public community package and existing signing identity must be preserved for in-place upgrades.

## Published candidate artifact

APK: 640863630 bytes. SHA-256: `5154a6f203b8d1ba264966049b08e5ea3f304e4b2ba3a44e28a06f8697a99d07`. All 16 bundled model files and 6 video program files passed their pinned checks. RAG metadata, model terms and native classes are present; embedding weights remain a separate download. Resource-table storage/alignment, 16 KiB ZIP alignment and the existing signing certificate were checked. See [artifact report](artifact-validation-0.11.0.json).

Source fingerprints use a canonical field order, so document-page revalidation is independent of JSON construction order. A native regression covers this. The earlier RC.1 candidate is withdrawn; use RC.2.

## Automated release acceptance

[Full source CI](https://github.com/RadmirAmir/pocket-llama/actions/runs/39415093503) passed all host, RU/EN frontend and Android jobs, including the community APK and Play bundle/lint build. [Exact published APK installation](https://github.com/RadmirAmir/pocket-llama/actions/runs/39415093584) passed the upgrade from 0.10.2, fresh installation, startup and installed version 0.11.0 / code 30. That job also passed 8 exact-APK video-loader checks, 21 Android Notes checks, 19 SQLCipher knowledge checks and 34 Android video/migration checks. The native knowledge fixtures compile the production reader/index code with synthetic keys and embeddings; they do not execute EmbeddingGemma on a physical phone. The separate GGUF host test is described above.

Additional local chat transport regression: 70 HTTP assertions passed. Published APK SHA-256: `5154a6f203b8d1ba264966049b08e5ea3f304e4b2ba3a44e28a06f8697a99d07`. Package and certificate match the previous community release.

## Unreleased title-only note fix

The source now indexes a note title when the body is empty, with a native SQLCipher retrieval/revalidation regression. The signed RC.2 APK predates this follow-up. Its signing/build execution server lost its connection, so a new signed APK could not be produced in this session. The published RC.2 remains the verified downloadable artifact; its limitations are documented in [RAG setup](RAG.md).
