# Community 0.12.0 — embedded SearXNG update

This is the full update to the existing community Pocket app, including the
0.11.0 RC.3 personal-knowledge functionality. It uses `dev.pocketllama`, the
previous community signing certificate and version code 32. Speech, OCR,
notes, recordings, scans, chats and optional Surya remain available.

## Signed artifact

- APK: `PocketLlama-0.12.0-community-arm64.apk`, 662322940 bytes.
- SHA-256: `fd5e7679f750e523c86bca3535eeefeaecf8b35cc1484dabfe85ef54c0136afa`.
- Version name `0.12.0`, version code `32`; Android 10+, ARM64.
- Certificate SHA-256: `fd5e7679f750e523c86bca3535eeefeaecf8b35cc1484dabfe85ef54c0136afa`.
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

The native extensions explicitly link the bundled shared Python library; the
Android build verifies their DT_NEEDED entries. This resolves Android loader
failures that host-only Python tests do not expose.

## Android acceptance

[Native Android verification](https://github.com/RadmirAmir/pocket-llama/actions/runs/39482462923/job/118834249895)
passed the actual bundled Bionic CPython, lxml, msgspec, real SearXNG parsing,
in-memory caches and bounded cancellation when access is revoked. Its
diagnostic Activity runs once across configuration changes and waits for
asynchronous process retirement. These diagnostic-only corrections do not
change the published APK.

[Exact published-APK acceptance on Android 16](https://github.com/RadmirAmir/pocket-llama/actions/runs/39481658866)
passed the downloaded SHA-256 above, package, version, previous certificate,
ZIP alignment, complete model set and current search/RAG assets. Fresh
installation, startup and an in-place upgrade from 0.11.0 RC.3 all passed.
The same job passed six checks loading the signed APK's own knowledge DEX,
21 notes persistence/search/encryption checks, 20 real SQLCipher knowledge
checks, and 34 video/migration checks.

Every job in the [source workflow](https://github.com/RadmirAmir/pocket-llama/actions/runs/39482462923)
passed: host, RU/EN frontend, native Android search, the complete community
source build, and Play bundle/lint with install-time model packs.
The APK is published as [v0.12.0-rc.1](https://github.com/RadmirAmir/pocket-llama/releases/tag/v0.12.0-rc.1)
and its bytes have not changed after publication. Live provider availability
and end-to-end search performance on a physical ARM64 phone remain separate
from deterministic runtime checks.
