# Pocket 0.13.0 validation

The community candidate uses `dev.pocketllama`, versionCode **33**, and the
same update certificate as 0.12.0. This is the complete main app with its
speech, OCR, notes, video, encrypted vault, RAG and embedded SearXNG features.

## Signed candidate

- File: `PocketLlama-0.13.0-community-arm64.apk`
- Size: 662,331,132 bytes
- SHA-256: `1c330031c1c92dbffdd37232e438b9927993d72fd9d2e9a85338ee51eb0306e7`
- Certificate SHA-256: `ef3cea40110c13d179cc7e1f1e570aeb2f870cb7d46f16273eb5d733cf9b2ed0`
- Android 36 community build and artifact validation passed: all 16 model
  assets, 6 video files, 3 search files, current UI and original update key.
  [Machine-readable validation](artifact-validation-0.13.0.json).

## Restored build provenance

The workspace was restored from repository commit
`02ce96630ad8740591ee696b401690d48dc8e3d3`. The APK was rebuilt from the
current Java/resources/UI with Android 36 tools and signed with the original
key. All model/search/video assets came from the SHA-256-verified source CI
artifact; all tracked assets match the repository byte for byte. The unchanged
CPU, CPU-fast and Vulkan native libraries were restored from the published
0.12.0 APK (SHA-256
`fd5e7679f750e523c86bca3535eeefeaecf8b35cc1484dabfe85ef54c0136afa`).
Native sources, patches and `llama.lock` are unchanged between the releases.
The recovered SDK, pinned dependencies and these runtimes can build the full
community APK again; the new hash above replaces the earlier lost candidate.

## Checks completed

[Source CI](https://github.com/RadmirAmir/pocket-llama/actions/runs/39492377814) checks commit `8dfc1c4f79eea2e5e49c79f2aa4ad0d33a21c387`: host tests, RU/EN frontend, Android community APK, Play bundle/lint and native Android network probe. The signed candidate uses the same app sources; only validation/release documentation follows that commit.

Host tests also exercise real DNS/TCP/verified HTTPS, certificate rejection,
Bing fallback, and sanitized failure categories through the SearXNG pipeline.
The Android probe passed real DNS/TCP/verified HTTPS, rejected an untrusted
certificate, exercised the native parsers/cache, and cancelled on vault-lock
revocation. [Android network evidence](https://github.com/RadmirAmir/pocket-llama/actions/runs/39492377814/job/118868617677).

- 38 agent checks exercise the production HTTP/SSE client: model-selected tool
  order, rewritten queries, results fed into later decisions, tool skipping,
  disabled-tool rejection, duplicate handling, tool errors, invalid-action
  fallback, four-call/six-decision limits, cancellation, citation identity,
  combined evidence truncation and final answer streaming.
- 27 search query, URL, evidence, tokenizer-budget and cancellation checks.
- 106 core, 14 note draft/search, 447 RAG, 23 voice and 27 distribution checks;
  source/distribution Python checks and release source review also pass.
- Browser checks cover consent defaults, coexistence of both tools, multiple
  action rows and queries, encrypted-history payloads, safe rendering, stop
  state, source links, RU/EN controls and narrow mobile layouts.
- Existing RU/EN interface and RAG UI regressions pass.

These checks use deterministic model decisions to validate the loop. They do
not measure how well every GGUF chooses queries. Physical ARM64 acceptance,
live-provider availability and an installation test of this exact signed
candidate remain device checks. The previous 0.12.0 signed release passed fresh
installation and in-place upgrade verification; this candidate retains its
package and certificate but has not yet repeated that emulator run.
