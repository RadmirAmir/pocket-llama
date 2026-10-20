# Community 0.12.0 — embedded SearXNG update

This is the full update to the existing community Pocket app, including the
0.11.0 RC.3 personal-knowledge functionality. It uses `dev.pocketllama`, the
previous community signing certificate and version code 32. Speech, OCR,
notes, recordings, scans, chats and optional Surya remain available.

## Signed artifact

- APK: `PocketLlama-0.12.0-community-arm64.apk`, 662322940 bytes.
- SHA-256: `994bf8740457df1184b4bf3b3fd7791e1c7af7b88bf17877f30891ca689faede`.
- Version name `0.12.0`, version code `32`; Android 10+, ARM64.
- Certificate SHA-256: `ef3cea40110c13d179cc7e1f1e570aeb2f870cb7d46f16273eb5d733cf9b2ed0`.
- Compared against the exact published 0.11.0 RC.3 APK. All 16 model assets,
  six video-runtime files and three search-runtime files passed size/hash
  verification. Resource table and signature checks passed.
- [Machine-readable artifact report](artifact-validation-0.12.0.json).

## Local checks completed

- 106 core assertions, 14 note draft/search checks, 447 knowledge checks and
  23 voice checks.
- 24 query/source/context/cancellation checks, distribution and source-export
  checks, and six real-SearXNG protocol/privacy tests using fixed provider
  responses.
- Browser checks for native-consent state, default-off behavior, query
  isolation from attachments/history/RAG, safe citations, partial failures,
  RAG coexistence, RU/EN and narrow mobile layouts.

## Android acceptance

The source CI exercises the bundled Bionic CPython, lxml, msgspec, real
SearXNG parsing, in-memory caches and lock cancellation on an Android
emulator. The signed-release workflow downloads the exact staged APK,
checks its hash, package, version and old certificate, then tests fresh
installation, startup and an in-place upgrade from RC.3. It also runs the
notes, SQLCipher knowledge and video regressions using the candidate.

Run links and outcomes will be recorded after these jobs finish. Live
provider availability and end-to-end search performance on a physical
ARM64 phone remain separate from deterministic runtime checks.
