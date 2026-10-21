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

Full source CI and exact signed APK acceptance are recorded before delivery.
The Android probe now tests actual public news/general HTTPS plus absence of
new search windows, in addition to native parsing and cancellation. Physical
ARM64 LLM performance and every GGUF's answer quality are not established by
protocol or emulator checks.
