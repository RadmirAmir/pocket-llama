# Pocket

Private Android recorder, Russian speech transcription, document scanner and local LLM chat. Android 10+, ARM64. Processing runs on the device. Optional model downloads and explicitly requested video imports need a network connection.

Version 0.8.0 prepares distribution and model management. It is a release candidate, not an already reviewed Google Play release.

**[Download the Android preview APK](https://github.com/RadmirAmir/pocket-llama/releases/download/v0.8.0-rc.1/PocketLlama-0.8.0-community-arm64.apk)** · [Release notes, unsigned AAB and SHA-256 checksums](https://github.com/RadmirAmir/pocket-llama/releases/tag/v0.8.0-rc.1). Android 10+, ARM64. GigaAM and PaddleOCR are included; choose and download an LLM from the in-app catalog. The public preview installs alongside the personal app.

| Channel | Application ID | Included models |
| --- | --- | --- |
| Personal update | `dev.pocketllama.memoryfix` | GigaAM v3 INT8, Silero VAD, CAM++, PaddleOCR; optional Surya for the existing permitted personal use |
| Community APK | `dev.pocketllama` | GigaAM v3 INT8, Silero VAD, CAM++, PaddleOCR |
| Google Play AAB | `dev.pocketllama` | The community models, delivered in install-time AI packs |

The public profiles exclude the restricted Surya weights and the CC-BY-NC-SA detector. Including them requires documented redistribution rights for both components. See [licensing](docs/LICENSING.md).

The supplied compact personal update retains the previous Surya detector and reuses existing VLM weights; a fresh Surya setup needs a separate download. GigaAM and PaddleOCR work offline immediately. A fully bundled personal build can also be made for a permitted use; see the release guide.

The in-app catalog contains three pinned Qwen 3.5 files. Downloads support pause/resume, SHA-256 checks, atomic installation and a signed catalog. The catalog chooses a recommendation based on RAM and current free memory. This is a compatibility heuristic, not a phone benchmark. Custom GGUF import remains available.

## Build

Use JDK 17, Python 3.11+, Android SDK platform 36, build-tools 36.0.0 and NDK r28.2.13676358. Native builds require CMake and Ninja.

```bash
python3 -m pip install -r requirements-build.txt
python3 scripts/prepare-runtime.py
bash scripts/build-native.sh cpu
./gradlew -Ppocket.channel=community assembleDebug
python3 scripts/prepare-build-tools.py
./gradlew -Ppocket.channel=play :app:buildReleasePreBundle :app:assetPackReleasePreBundleTask :app:lintVitalRelease
python3 scripts/build-play-bundle.py --bundletool build/tools/bundletool.jar
```

An unsigned release bundle is suitable for structural validation; a publisher keystore is required for upload. See [release instructions](docs/RELEASE.md), [privacy draft](docs/PRIVACY.md), [validation](docs/VALIDATION-0.8.0.md) and [Russian guide](README-RU.md).

```bash
CHAT_JSON_JAR=/path/to/json.jar bash scripts/test-distribution.sh
bash scripts/test-core.sh
python3 scripts/release-check.py
```

The GitHub workflow restores the locked dependencies and builds the Android artifacts. Large model files, native outputs and credentials are excluded from git. The Gradle wrapper distribution has a pinned SHA-256.

## License

Pocket's own code is Apache-2.0. Dependencies, model weights and bundled runtimes retain their individual licenses; see `NOTICE`, `notices/` and [the component inventory](docs/LICENSING.md). Model licenses are not replaced by the app's source license.
