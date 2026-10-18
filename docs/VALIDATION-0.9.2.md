# Pocket 0.9.2 validation

This release removes the separate Surya line detector. Main Surya OCR/table recognition and PaddleOCR remain available.

## Source checks

- Scanner core: 54 checks; generation guards: 16; encrypted-file privacy: 11; production document/photo stores: 40; production Surya pipeline with scripted native transport: 79.
- PDF search/rasterization and XLSX merged-cell/inline-string validation passed.
- Model distribution: 27 assertions; channel-policy/stale-asset exclusion: 7 tests; retired-cache cleanup: 9 checks.
- JavaScript/Python syntax, source packaging and whitespace checks passed.

Cache tests cover fresh installs, complete and interrupted detector copies, verification stamps, repeated cleanup, preserved VLM/projector and encrypted documents, unexpected files and symbolic links. Pipeline tests cover text-only legacy refinement without a model request, VLM-only table grids and preserved manual corrections.

The main Surya model retains its own license. Removing the line detector does not waive those conditions. No physical-phone performance or Google Play acceptance is established by host tests.

## Signed community APK

- File: `PocketLlama-0.9.2-community-arm64.apk`; 640,809,824 bytes (about 641 MB).
- SHA-256: `d888310192043510c8309edbb969fa512aec26c965e63bffd66a9a6607ac2991`.
- Source: `d9c48448e107f2c061d4fca6942202f2ea8fb461`; package `dev.pocketllama`, versionCode 25, Android 10+ ARM64, target API 36.
- All 16 bundled model files verified against their pinned size and SHA-256. No separate line-detector weights, runtime class or detector notices in the APK. Full main-Surya terms are included and match the source.
- Update certificate matches community 0.9.1: `ef3cea40110c13d179cc7e1f1e570aeb2f870cb7d46f16273eb5d733cf9b2ed0`.
- Built from a clean source directory. Distribution preparation stops if retired scanner files remain in staging; the APK writer also rejects any retired file that appears later.

[Artifact report](artifact-validation-0.9.2.json) · [Build and UI workflow](https://github.com/RadmirAmir/pocket-llama/actions/runs/39158492854).

Frontend verification: 15 catalogue assertions, 44 studio checks, 53 scanner checks and EN/RU switching/persistence checks passed. Scanner checks include table grids, no line-overlay controls, text-only pages without refinement, preserved manual edits, and complete main-model terms before consent.

## Published APK installation

[Exact-byte installation workflow](https://github.com/RadmirAmir/pocket-llama/actions/runs/39158933948) passed on Android 16 (API 36), Google APIs x86_64 emulator with ARM64 translation:

- Downloaded the published 0.9.2 APK and checked its pinned SHA-256.
- Confirmed community/Surya capability, complete main-model terms, and no retired detector file, DEX class or notices.
- Installed over the signed community 0.9.1 APK, checked version 0.9.2 / code 25 and launched the app successfully.
- Performed a fresh installation of the same published APK and confirmed startup with a live app process.

The [source build workflow](https://github.com/RadmirAmir/pocket-llama/actions/runs/39158492854) passed all host, frontend and Android jobs, including the community APK and Play bundle. The additional late-file packaging guard and all seven distribution-policy tests also passed in the [guard-check workflow's host job](https://github.com/RadmirAmir/pocket-llama/actions/runs/39159220979).

These are emulator installation/startup checks. Physical-device inference, thermal behavior and Play internal-track acceptance retain the separate requirements in [the release guide](RELEASE.md).
