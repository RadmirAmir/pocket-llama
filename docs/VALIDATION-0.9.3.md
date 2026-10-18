# Pocket 0.9.3 validation

The released 0.9.2 APK contains Python and yt-dlp but is missing `assets/video/runtime-id.txt`. This reproduces the user's failure before metadata extraction or any YouTube request. Clean-build reconstruction copied the generated four-file inventory, which omitted the marker; previous startup checks did not exercise video import.

The runtime inventory now covers all four required assets (`runtime-id.txt`, `launcher.py`, `python.zip`, `yt-dlp`) and both native executables. The marker must match the pinned runtime lock. Source, staging and final APK/AAB validation enforce presence, unique paths, sizes and SHA-256 hashes.

Seven video distribution tests pass, including the old incomplete inventory, a missing marker after staging, each missing final-archive component, modified/duplicate program files and clean reconstruction from the generated inventory. The existing seven scanner distribution tests and nine retired-cache checks also pass.

The new runtime tests accept an actual APK and extract its video assets, so a developer checkout cannot hide a packaging omission. Android regression uses the pinned x86_64 interpreter for the emulator ABI, with yt-dlp, launcher and marker from the published ARM64 APK. These checks do not establish physical-phone performance or access to every YouTube video.

## Signed APK checks

- File: `PocketLlama-0.9.3-community-arm64.apk`, 640,809,897 bytes (about 641 MB).
- SHA-256: `bf064dac3023c27e3d831d70fe2d6c850be9b8bf697ba5aae56759a92b51a1ed`.
- App source: `086b67b998d7c9742cbf6dff8a6f5738ae7c1980`; package `dev.pocketllama`, versionCode 26.
- Verified all 16 speech/OCR package files and all 6 video program files; no retired Surya detector. [Artifact report](artifact-validation-0.9.3.json).
- Certificate matches community 0.9.2: `ef3cea40110c13d179cc7e1f1e570aeb2f870cb7d46f16273eb5d733cf9b2ed0`.
- Eight real-loader host checks passed using assets extracted from this signed APK: production launcher/arguments, actual pinned yt-dlp, synthetic M4A metadata and download, encrypted byte parity, committed media and no plaintext temporary media.

The same APK test on 0.9.2 fails because `assets/video/runtime-id.txt` is absent. A separate live-network YouTube probe obtained metadata but received HTML instead of audio; the downloader rejected that response. Controlled media tests establish the packaging fix and pipeline behavior, not access to every video or network.

[Source build and UI workflow](https://github.com/RadmirAmir/pocket-llama/actions/runs/39165050827).
