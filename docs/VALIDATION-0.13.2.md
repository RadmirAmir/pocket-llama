# Pocket 0.13.2 validation

Complete community app `dev.pocketllama`, versionCode 35. The production agent
suite passes 63 checks. New regressions reproduce false web-evidence claims
and misclassified failures after personal retrieval against the actual HTTP/SSE
client, using deterministic model decisions.

Full source CI and exact signed APK acceptance are recorded before delivery.
Existing SearXNG/WebView behavior is unchanged from 0.13.1; this patch changes
agent prompt/evidence handling. No physical ARM64 LLM performance claim is made.
