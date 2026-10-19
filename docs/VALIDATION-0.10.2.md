# 0.10.2 transcript-copy validation

Application source: `7d5cfa89f5b9812ecbe79ef7ff5045c95052f010`. Community package `dev.pocketllama`, version code 29.

- [Source CI](https://github.com/RadmirAmir/pocket-llama/actions/runs/39326236924): host and frontend checks passed, including the existing Studio, Notes and EN/RU checks. The separate native-runtime build is still running.
- All Java sources were rebuilt locally with the official Android SDK 36 tools and compiled for Java 11. The APK includes the new native transcript-copy action.
- A focused check executed the compiled transcript formatter with a synthetic cursor containing 350 turns: all pages, order, speaker names, timestamps, edited text, Unicode and newlines were preserved. Empty and oversized transcripts failed explicitly. This checks the formatter using an in-memory store substitute; it does not exercise a physical phone's clipboard.
- The frontend action was checked for its recording ID, duplicate-click guard, completion message and empty-view state. Copying reads the complete native cursor rather than the displayed or filtered turns.
- Transcript reading runs on the IO executor. The main-thread clipboard write rechecks the app lock and its generation, and marks the text sensitive, as the summary copy does.
- All 16 model files and 6 video program files were verified. The resource table is stored uncompressed and aligned; ZIP alignment and the existing signing identity match the validated 0.10.1 baseline. See [artifact report](artifact-validation-0.10.2.json).
- APK: 640,826,467 bytes. SHA-256: `8a070a2f260c220fbe78892f59e7054ad97bff8406b53907ca51bfee99f5e23c`.

Published-package fresh-install and 0.10.1 → 0.10.2 upgrade evidence will be recorded after the Android run completes.
