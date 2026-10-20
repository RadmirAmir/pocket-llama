# Pocket 0.13.0 validation

The community candidate uses `dev.pocketllama`, versionCode **33**, and the
same update certificate as 0.12.0. This is the complete main app with its
speech, OCR, notes, video, encrypted vault, RAG and embedded SearXNG features.

## Signed candidate

The complete signed candidate is being rebuilt after adding search fallback and
network diagnostics. The final artifact hash and size will be recorded here
once the refreshed APK passes distribution validation.

## Checks completed

Host tests also exercise real DNS/TCP/verified HTTPS, certificate rejection,
Bing fallback, and sanitized failure categories through the SearXNG pipeline.
The updated Android TLS probe is pending CI.

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
