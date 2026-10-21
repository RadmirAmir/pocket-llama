# Changelog

## 0.13.2 — accurate agent evidence status

- Preserve the original answer prompt when the agent chooses no tool; avoid extra final-context tokenization in that case.
- Say web evidence is available only when usable Internet sources are included in the final prompt. Personal-knowledge results do not imply Internet access.
- Keep the actual search failure when a required web lookup fails after local retrieval; do not misreport it as a context overflow. Optional failed web lookups can still answer from relevant personal evidence.
- Cover these cases in the production HTTP/SSE agent suite, now 63 checks. Preserve the complete main app, signing identity and existing two-call mobile budget; version code 35.

## 0.13.1 — mobile agent and on-device browser search

- Require a lookup for explicit web and current-news requests when the tool is enabled; preserve the topic for short search follow-ups and respect offline/private-source scope.
- Bound planning to 1,024 input tokens, 96 output tokens, two tool calls and three decisions including repair. Read prior evidence before refinement, suppress duplicate calls and avoid another model generation for hard search failures without sources.
- Read dated news through the real embedded SearXNG RSS pipeline. Mark headlines separately from full article text and fall back to browser search when feeds fail.
- Use a visible real Android WebView for general searches, with its normal networking/session and manual CAPTCHA handoff. Cancel on stop, consent revocation and vault lock.
- Verify live Russian/English news, actual Android CPython/TLS and WebView behavior. Preserve the main app package, original update certificate, all 14 native libraries and bundled speech/OCR/video features; version code 34.

## 0.12.0 — built-in SearXNG web search

- Connect Web search in Chat tools, with a native confirmation explaining that the current question and your public IP reach Internet search engines.
- Run the real SearXNG engine inside Pocket using the bundled Android Python runtime. No external SearXNG server, account or companion app is needed.
- Show Internet citations, source snippets and partial-provider failures. Keep chat history, attachments and personal-knowledge passages out of outgoing queries.
- Support Web search together with the existing encrypted personal-knowledge tool. Search starts off for a new chat and stops when cancelled, disconnected or locked.
- Ship the complete community update with the existing package and signing identity, version code 32, GigaAM, Moonshine and PaddleOCR. Preserve notes, recordings, scans, chats, optional Surya and RAG.

## 0.11.0-rc.3 — title-only notes in personal knowledge

- Include notes containing only a title in the signed community APK, with version code 31 for in-place upgrades from RC.2 and earlier community releases.
- Verify the RAG reader directly from the signed APK DEX in Android acceptance, alongside SQLCipher retrieval and existing notes/video checks.

## 0.11.0 — personal knowledge for local chat

- Explicitly connect Personal knowledge in Chat tools to search all active notes, edited audio/video transcripts, summaries and saved OCR.
- Optional pinned EmbeddingGemma 300M Q8_0 download with resume, integrity verification and model terms. Hybrid semantic/FTS5 search, encrypted incremental index and resumable embedding checkpoints.
- Source citations open notes, document pages and transcript timestamps. The chat model's tokenizer bounds evidence to its context.
- Embedded MCP JSON-RPC tool; disabled by default and disconnected for a new conversation. CPU embedding runs separately from the chat model to limit memory.

## 0.10.2 — copy audio transcripts

- Copy the complete audio transcript directly from its tab, including edited text, speaker names and timestamps.
- Copying includes unloaded pages and ignores the search filter. RU/EN labels and sensitive Android clipboard handling are preserved.

## 0.10.1 — single-line navigation labels

- Keep the five bottom-navigation captions on one line in RU/EN.
- Let longer labels such as «Настройки» use more space, remove excess button padding and retain full-size touch targets.
- Use a compact caption size on the narrowest screens. No data or model changes.

## 0.10.0 — encrypted notes and local search

- New Notes entry in the main navigation, with titles, text previews and recent edits.
- Native autosave with ordered draft snapshots, background/back flush and visible save/retry status.
- Literal title/body search, Russian case and е/ё handling, bounded result snippets and pagination.
- Pinning, plain-text checklists, searchable trash, restore and confirmed permanent deletion.
- Separate SQLCipher notes database under the existing vault key, PIN and secure local WebView origin; no model, account or network needed.
- RU/EN interface; user text is preserved verbatim. Added draft race tests, bilingual UI coverage and native persistence/search/encryption checks.

## 0.9.3 — restore video import in the community APK

- Fixed the missing `video/runtime-id.txt` in the 0.9.2 community APK. The generated runtime inventory now includes the marker and production launcher, alongside Python, yt-dlp and native executables.
- Validate every required video file and checksum in source, staging and the finished APK/AAB. An incomplete clean-build export now fails before signing.
- Test the loader using assets extracted from the signed APK. Android regression coverage includes native yt-dlp/QuickJS, encrypted download, audio decoding and preservation of older recordings.
- Show a translated update instruction when an installed video bundle is incomplete.
- Keep package ID, signing identity and data format. Existing interrupted imports can be resumed with “Retry import”.

## 0.9.2 — remove the separate Surya line detector

- Removed the CC BY-NC-SA line detector, its model/configuration, runtime class, conversion dependencies and download paths from every current distribution channel.
- Kept main Surya OCR 2 text, page layout, reading order and table recognition; optional table-grid refinement uses the same VLM. PaddleOCR remains bundled.
- Removed the precise-line overlay and offer table refinement only on pages containing tables, in EN/RU.
- Reclaim only the retired detector’s known private cache files on update. Preserve documents, manual corrections and downloaded Surya VLM/projector files.
- Keep the community package/update certificate and increment versionCode to 25. Main Surya license terms still apply.

## 0.9.1 — Surya returns to the free community edition

- Restored Surya OCR 2 text, layout, reading order, tables and optional precise line geometry in community.
- Bundled the existing pinned ONNX detector under CC BY-NC-SA 4.0 with attribution, modification notice and conversion source.
- Kept the APK compact: VLM/projector download once from the author (1.47 GB), with resume and SHA-256 checks.
- Added complete local model terms and an explicit acceptance action before setup, in EN/RU. Declining does not start a download.
- Preserved the community package and update certificate; installs over 0.9.0. No changes to vault format or inference engines.
- Kept the additional commercial-redistribution approval flags false and Play Surya disabled by default.

## 0.9.0 — English and Russian, fully local

- Persistent EN/RU setting across recorder, scanner, chat, PIN screens, dialogs and notifications.
- English ASR uses bundled Moonshine Streaming Medium with word timings; Russian ASR keeps GigaAM v3. Shared VAD and speaker grouping stay local.
- A transcript records its speech language. Resuming keeps its original engine; explicit re-recognition can use the newly selected language. Legacy transcripts migrate as Russian without changing their text.
- New chat instructions and generated summaries follow the selected interface language. Saved user content is preserved.
- Native Moonshine 0.1.5 and full ONNX Runtime 1.23.2 are pinned and checksum-verified. Both speech models are bundled in APKs / install-time speech packs for Play.
- English product screenshots and EN/RU user documentation.


## 0.8.0 — distribution preview

- Signed catalog of three pinned Qwen 3.5 GGUF files with device-memory recommendations.
- Foreground model downloads with pause/resume, restart recovery, bounded HTTP ranges, SHA-256 verification and atomic installation.
- CPU and bounded context defaults applied when selecting a catalog model; custom GGUF import retained.
- Separate personal, community and Google Play delivery profiles; install-time speech/OCR AI packs for Play.
- Built-in Surya package preparation without network in the personal update; verified existing models reused.
- Public profiles exclude restricted Surya models unless redistribution permissions are documented.
- Reviewed, editable, user-initiated answer reporting with a publisher-configured HTTPS endpoint.
- API 36 build configuration, source-only export, external signing and automated build/test workflow.

Physical-device and Play internal-track acceptance remain release prerequisites. See `docs/VALIDATION-0.8.0.md`.
