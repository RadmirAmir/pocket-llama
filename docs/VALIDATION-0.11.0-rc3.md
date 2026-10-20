# Signed community RC.3 validation — 0.11.0

The signed candidate includes the title-only note fix already present in main. The released RC.2 predates that fix. RC.3 uses version code 31 so it can update both community 0.10.2 and RC.2 without uninstalling the vault. The signed candidate is published as [v0.11.0-rc.3](https://github.com/RadmirAmir/pocket-llama/releases/tag/v0.11.0-rc.3). Fresh installation and both upgrade paths passed on Android 16.

## Artifact

- Source commit: `33517976485dc20e604cdcccd950d5d82d37a9cc`. All Android Java sources were rebuilt with JDK 17 and Android SDK 36, targeting Java 11.
- APK: `PocketLlama-0.11.0-community-arm64.apk`, 640863630 bytes.
- SHA-256: `fe9e47e9ea489a27549a9b760af29df70c7f4303df852273775d8bf2f40c0464`.
- Package: `dev.pocketllama`; version name `0.11.0`; version code `31`; ARM64, Android 10+.
- The signing certificate matches the installed community lineage: `ef3cea40110c13d179cc7e1f1e570aeb2f870cb7d46f16273eb5d733cf9b2ed0`.
- All 14 native libraries are byte-identical to the verified RC.2 APK (SHA-256 `5154a6f203b8d1ba264966049b08e5ea3f304e4b2ba3a44e28a06f8697a99d07`). Models and runtime pins did not change.

## Completed checks

- Local host checks passed: 106 core assertions, 14 note draft/search checks, 447 RAG checks and 23 voice checks. Distribution, retired-detector, video-inventory and source-profile checks passed.
- The finished APK passed all 16 model hashes and six video-runtime files, Android resource-table checks, 16 KiB ZIP alignment and signature verification. See [artifact report](artifact-validation-0.11.0-rc3.json).
- Eight host video-loader checks passed using the finished signed APK, including encrypted audio download.
- [RC.3 source CI](https://github.com/RadmirAmir/pocket-llama/actions/runs/39436506571): all host, RU/EN frontend and Android source-build/lint jobs passed, including the community APK and Play bundle.
- The unchanged title-only retrieval logic passed [20 real Android SQLCipher checks](https://github.com/RadmirAmir/pocket-llama/actions/runs/39417923550) before this rebuild, including title-only note retrieval and revalidation. That earlier run installed RC.2 and tested the new source in a separate native fixture.

## Exact published APK acceptance

[Android 16 acceptance](https://github.com/RadmirAmir/pocket-llama/actions/runs/39439605986) passed the exact downloaded APK hash, fresh installation, startup, and in-place upgrades from community 0.10.2 and RC.2. Installed version 0.11.0 / code 31 was verified. Six checks load the production RAG reader directly from the signed APK DEX and prove title-only/whitespace-only note handling while preserving nonempty bodies and excluding empty notes/unrecognized OCR.

The same job passed eight exact-APK host loader checks, 21 Android notes checks, 20 real SQLCipher knowledge checks, and 34 Android video/migration checks. Its companion [full source CI](https://github.com/RadmirAmir/pocket-llama/actions/runs/39439605902) passed every host, RU/EN frontend and Android build/lint job. The APK was not changed after these checks.

Physical-phone retrieval speed and end-to-end answer quality remain unmeasured; these are installation, packaging and deterministic retrieval regressions.
