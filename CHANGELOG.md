# Changelog

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
