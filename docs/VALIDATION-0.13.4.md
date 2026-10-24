# Pocket Llama 0.13.4 validation

Investigation found three gaps in 0.13.3: the content filter recognized only a
few literal topic-label prefixes; the generator could write other headings and
attach any valid source ID; cached notes were checked only for valid citations.
The 84-turn real-model fixture repeated six sentences and did not test coverage
of a varied recording. Opening an already saved summary did not regenerate it.

Video summaries now use extractive selection. The model chooses indices from
bounded banks of actual speech, and the app renders the corresponding text and
source IDs. The model cannot supply displayed text or manufacture a citation.
Intermediate notes retain speech rather than generated section headings.
Every cache reuse checks the exact text/source binding. New video checkpoint
version: summary-v5-video-evidence; meeting v3 checkpoints stay compatible.
Video wording and language follow the recording; meeting summaries retain their
existing generation path.

A real MiniCPM5-2B-Q4_K_M run initially selected six statements only from the
first news story. Temporal groups and a constrained tuple of one selection per
group fix that failure. The pinned llama.cpp JSON-schema implementation was
checked with actual native inference, including its prefixItems support.

Host tests cover arbitrary headings (not just the reported exact phrase), invalid
indices, fractional/string output, bounded repair, direct source binding,
deduplication, split ASR sentences/negations, stale-cache replacement, six-point
coverage and a 2048-token hierarchical reduction/resume. Pipeline/recovery and
transport tests preserve the meeting path. Studio tests cover an update action
for old video summaries, direct transcript navigation, duplicate source buttons
and source pages beyond turn 100. No private recording was accessed.

Real-model evidence uses the pinned MiniCPM GGUF and llama.lock on host CPU.
The new synthetic fixture contains 84 unique ASR-style turns across six fictional
local news stories, amounts, dates, negations and uncertainty. It checks topic
coverage and the exact selected speech/citation binding. This is not a physical
phone performance measurement or a guarantee of perfect importance ranking.

Passed locally: 35 dedicated video-content/budget/recovery checks, the existing
pipeline and 26 adaptive-recovery checks, 36 transport-recovery checks, 55 Studio
UI checks and 50 notes UI checks; EN/RU, core and distribution checks pass.
Actual pinned MiniCPM inference covers all six topics at both 4096 and 2048
context; the latter exercises map/reduce. The repeated 84-turn amount/deadline/
qualification regression also passes at 2048. Captured outputs and model pins:
`tests/fixtures/video-summary-minicpm5-0.13.4.json`. Output budget: 192 tokens;
source-ID lookup cache: bounded to 512 entries to avoid repeated SQLite queries.

Exact signed APK acceptance passed: https://github.com/RadmirAmir/pocket-llama/actions/runs/40131927433
Fresh installation/startup, in-place upgrades from 0.11.0 RC.3 and 0.13.3, notes/RAG/SQLCipher/video regression passed on Android 36 emulator.

All four source CI jobs passed: https://github.com/RadmirAmir/pocket-llama/actions/runs/40131671584
Frozen source: 4147f62200698d31207919173a6558f95af4941e.
Published APK: 662343420 bytes; SHA-256 06a5b7af6b851b4dc68dd788af4ca47ae85374ac1f53ab856d92074ec3664374.
Original update certificate, all 16 bundled models and 14 unchanged native libraries verified.
