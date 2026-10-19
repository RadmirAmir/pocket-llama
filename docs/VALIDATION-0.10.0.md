# 0.10.0 community preview validation

Application source: `8612a32ac7e0b23c4c30cedccb07d140310ef917`. Version code: 27. Community package: `dev.pocketllama`.

## Source and interface

[Source CI](https://github.com/RadmirAmir/pocket-llama/actions/runs/39271685987) runs the host, frontend and Android build checks. All three jobs passed, including the community APK and Play AAB builds. Frontend evidence includes 50 Notes checks across RU/EN, 45 Studio checks, catalog/scanner regressions and the shared language-switch checks.

The Notes UI checks cover creation and autosave, original user text, Cyrillic/literal search, late search replies, pinning, checkbox insertion without deleting a selection, reopening, failed writes with retry, trash/restore, confirmed permanent deletion, dark theme, narrow screens, landscape and larger text. Frontend fixtures simulate the native bridge; they do not substitute for native database tests.

Host tests include 14 Notes checks for draft ordering, saving during continued typing, stale-input rejection, Unicode folding, bounded snippets and preserving the last accepted draft on oversized input. Existing core, speech and distribution checks also pass.

## Published artifact and Android

The signed APK contains 16 verified model files and all 6 video runtime files. Its UI assets match the source, the Notes classes are present in DEX, and its preview certificate matches community 0.9.3. See [the artifact report](artifact-validation-0.10.0.json).

- APK: `PocketLlama-0.10.0-community-arm64.apk`, 640,826,467 bytes.
- SHA-256: `b34a7b66b39b2e371c348e3efc4f470bbfad47516bf1b83eebba2ed331b9123b`.
- Certificate SHA-256: `ef3cea40110c13d179cc7e1f1e570aeb2f870cb7d46f16273eb5d733cf9b2ed0`.

[Android installation and native regression run](https://github.com/RadmirAmir/pocket-llama/actions/runs/39273838783) passed for the published APK, using source `ebb5a80597c464b2f8f618f221180f65ccdb380c`:

- Fresh install/startup and community 0.9.3 → 0.10.0 upgrade on the API 36 Google APIs x86_64 emulator with ARM64 app translation; installed version 27 verified.
- 21 real SQLCipher Notes checks: original text, Russian/Unicode search, literal SQL punctuation, matching snippets, pins, pagination, stale-edit protection, trash exclusion/search/restore, permanent-delete guard, no plaintext titles/body in database/sidecars, and reopening with the same key.
- 8 real-loader host checks using program assets extracted from the exact APK.
- 34 Android video/migration checks, retaining the native loader, encrypted download/decoding and older recording data.

The native database tests use synthetic vault keys and a disposable Android emulator, without opening a real user vault. The x86_64 fixture uses the pinned x86_64 native libraries; the signed app and model checks cover the actual published ARM64 bytes. This proves store behavior and installed-package compatibility; it does not exercise every Notes edit through a physical phone keyboard.

## Scope

Notes are plain text with Unicode checkboxes. There is no cloud sync, automatic backup, rich-text/attachment system or folder hierarchy in this release. No language model is needed for notes. The Notes screen has no network route and uses the same app PIN and protected vault secret as the other tools.

Physical-device responsiveness, keyboard behavior across all Android keyboards and Google Play acceptance are not certified by these automated tests. Existing video-import fixture checks validate the bundled loader and encrypted audio path, not every live video platform or URL.
