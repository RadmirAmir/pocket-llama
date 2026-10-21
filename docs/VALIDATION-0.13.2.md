# Pocket 0.13.2 validation

Complete community app dev.pocketllama, versionCode 35. This release removes
search-result WebViews and uses embedded SearXNG feeds/APIs in the background.
The native action/result loop has three tool calls, five decisions, 1,024 input
and 96 output planning tokens, and six final deduplicated passages.

Completed locally: 74 production HTTP/SSE agent checks, 27 web checks and 11
SearXNG tests including actual TLS and certificate rejection. Live host probes
returned six dated Neftekamsk news sources, six dated EN news sources and six
results for llama.cpp. These host probes used the executor's egress proxy;
Android does not use that proxy.

Source commit: b8677e9d35e9e33cfaa4c548718c640d237cf515.
Full source CI passed all four jobs:
https://github.com/RadmirAmir/pocket-llama/actions/runs/39604483610
The Android native probe returned six public RU news sources and six general
EN sources without creating a search window. Native parsing, verified HTTPS,
certificate rejection and cancellation passed. Direct CI host probes also
returned six results for each of the three live queries.

Frozen signed community APK: 662,339,324 bytes; SHA-256
bf451871743f9063368e8c3bddb5e3fb277d02a42dfa498998acd3df9060f6ce.
The original signing certificate matches; all 14 native libraries are unchanged
from 0.13.1. DEX contains no browser-search or diagnostic classes, and exactly
three search runtime assets remain. The release workflow reconstructs this
already signed file byte-for-byte from the previous public APK and checked
public app bytes; no signing key enters GitHub Actions. Publication requires
fresh installation, startup, both 0.11.0 RC.3 and 0.13.1 upgrades, and the
signed-APK notes/RAG/SQLCipher/video regression to pass first.

Physical
ARM64 LLM performance and every GGUF's answer quality are not established by
protocol or emulator checks.

Exact signed APK acceptance passed: https://github.com/RadmirAmir/pocket-llama/actions/runs/39628125888
Fresh installation/startup, in-place upgrades from 0.11.0 RC.3 and 0.13.1, notes/RAG/SQLCipher/video regression passed on Android 36 emulator.
