# Pocket Llama 0.13.3 validation

The reported video output is reproduced by a production HTTP/SSE regression:
topic-only strings such as «Фрагмент про Пескова и Токаева», repeated source 66,
nested Markdown and citation-only output. There are 27 dedicated content,
bounded-repair, citation and final-coverage checks. Existing pipeline/recovery
tests and 36 transport-recovery checks pass, including reuse of old meeting
checkpoints. Video checkpoints use a new version.

The actual pinned MiniCPM5-2B-Q4_K_M GGUF was run through production
LocalSummary/ChatApi and the llama.cpp commit in llama.lock, on host CPU with
an 8,192-token context and 84 synthetic transcript turns. The resulting summary
contains five key points, the amount 12 million, the inspection deadline 20 May
and the qualification that the budget is unapproved. The synthetic input and
assertions are in VideoSummaryIntegration.java; model pins and captured output
are in tests/fixtures/video-summary-minicpm5-0.13.3.json. This is not a physical
phone performance benchmark or a guarantee of every model's factual/citation
accuracy.

Studio UI checks exercise the reported saved output, per-point source
deduplication, direct transcript navigation, regeneration and source-page
retention past turn 100. Notes and EN/RU UI checks pass. No private recording
or user video was used for these tests.

Full source CI, exact signed APK identity, install/upgrade acceptance and the
published artifact digest are recorded before delivery. The main package and
all previously shipped native runtime/model capabilities are preserved.

All four source CI jobs passed: https://github.com/RadmirAmir/pocket-llama/actions/runs/40104603215
Frozen source: d88132972914a0e5c60320fb87fba2d503054bb1.
Signed APK: 662339324 bytes; SHA-256 59024ef5da94be8f7591e9041f735f4496612e335694da63023c44853f0941ee. Original update certificate, all 16 models and 14 unchanged native libraries verified.

Exact signed APK acceptance passed: https://github.com/RadmirAmir/pocket-llama/actions/runs/40116475724
Fresh installation/startup, in-place upgrades from 0.11.0 RC.3 and 0.13.2, notes/RAG/SQLCipher/video regression passed on Android 36 emulator.
