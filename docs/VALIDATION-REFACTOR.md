# Refactoring validation

Base: `0426eae02a1a56f0ef3ce52cb0e35b732238c2df` (accepted 0.13.4 checkpoint).
Branch: `feature/inference-performance`. This is source refactoring, not a new
release or a performance measurement. See [ARCHITECTURE.md](ARCHITECTURE.md).

## Passed locally

Environment: OpenJDK 17, Java release target 11, Android SDK 36 API jar;
Playwright with Chrome for Testing 134.0.6998.35 for browser scenarios.

| Check | Result |
| --- | --- |
| All application Java sources | Compiled against SDK 36 and existing pinned dependency jars; resource identifiers generated from unchanged resources |
| Android video/notes/knowledge/migration regression sources | Compiled against the refactored application; not executed on Android |
| `test-voice-core.sh` | 23 existing checks + 104 transcription assertions; pure orchestration requires no ONNX/JNI/Android |
| `test-native-rpc.cjs` | Out-of-order/late/duplicate replies, existing 30/60-second deadlines, native/serialization failures, cleanup, no automatic retry, wire format, Android asset inclusion |
| `test-studio-ui.cjs` | 55 studio checks and 50 notes checks in RU/EN |
| `test-scanner-ui.cjs` | 53 scanner checks |
| `test-i18n-ui.cjs` | Language switching, reload, original user text, partial ASR language; includes knowledge-tool UI checks |
| `test-core.sh` | Core memory/server policy, notes draft/search and knowledge checks |
| `test-summary-pipeline.sh`, `test-summary-transport.sh` | Existing summary formats, content/evidence, checkpoint recovery, cancellation and transport checks |
| `test-scanner-core.sh` | Existing OCR parsing, privacy, stores, pipeline/recovery, PDF and spreadsheet export checks |
| `test-distribution.sh`, `release-check.py` | Model distribution/pins, legacy scanner cache, search/video packaging and source package checks |
| `git diff --check`, JS/Python syntax checks | Passed |

The browser regression tests caught lost startup handlers during the extraction;
both were restored and the affected suites passed afterward. Non-reply Studio
and Photo event handlers were also compared with the accepted source to confirm
that their behavior was retained.

## CI fixture correction

The historical installation workflow downloads fixed 0.11.0 RC3 bytes and
compares their assets with the checkout. It previously checked out the moving
branch, so any later UI change invalidated that comparison. The workflow now
explicitly replays `e76a2e0f179c262e5443fd407834a61eac37b7c4`, the matching
RC3 validation revision documented in [VALIDATION-0.11.0-rc3.md](VALIDATION-0.11.0-rc3.md).
Its app source/assets/runtime pins match the published RC3 source
`33517976485dc20e604cdcccd950d5d82d37a9cc`. The historical script itself is
unchanged. New shared-asset validation belongs to the current artifact validator
and RPC tests, not the immutable old APK. The workflow name now identifies the
old version; passing it does not validate installation of this refactor.

## Scope of evidence

The new speech tests use the production orchestration with fake audio/model/store
ports. Existing UI tests use production assets and synthetic native responses.
Existing OCR and summary tests include scripted transports. These checks cover
control flow and contracts, not speech/OCR quality on actual models.

No physical phone, Android emulator, full APK/AAB packaging, native rebuild,
device memory/thermal measurement, NPU experiment or iOS build was performed
locally for this change. Model files, runtime pins, window geometry, encrypted
database schema and release metadata are unchanged. The GitHub workflow remains
responsible for its separate host/frontend/Android checks; local results above
do not imply a successful remote run.

Before merging, test a specifically identified candidate on the owner's phone:
resume a partial RU and EN transcript, cancel/retry recognition, open scanner
documents and OCR results, edit/reopen a note, switch language and navigate to a
source from a summary. Owner approval remains required by [AGENTS.md](../AGENTS.md).
