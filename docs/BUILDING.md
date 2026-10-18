# Build Pocket from source

[Product overview](../README.md) · [Release and signing guide](RELEASE.md) · [Licenses](LICENSING.md)

Use JDK 17, Python 3.11+, Android SDK platform 36, build-tools 36.0.0 and NDK r28.2.13676358. Native builds require CMake and Ninja. The Gradle wrapper pins its distribution SHA-256.

```bash
python3 -m pip install -r requirements-build.txt
python3 scripts/prepare-runtime.py
bash scripts/build-native.sh cpu
./gradlew -Ppocket.channel=community assembleDebug
```

The bootstrap downloads locked runtime dependencies and verifies the pinned speech/OCR assets. Model weights, native outputs, caches and signing credentials are excluded from git. The portable public CPU build targets ARMv8-A Android ARM64.

For an unsigned Play bundle:

```bash
python3 scripts/prepare-build-tools.py
./gradlew -Ppocket.channel=play :app:buildReleasePreBundle :app:assetPackReleasePreBundleTask :app:lintVitalRelease
python3 scripts/build-play-bundle.py --bundletool build/tools/bundletool.jar
```

An unsigned bundle is for structural validation. Upload requires the publisher's signing setup and the remaining checks in [RELEASE.md](RELEASE.md).

| Profile | Application ID | Model delivery |
| --- | --- | --- |
| `community` | `dev.pocketllama` | GigaAM v3 INT8, Silero VAD, CAM++ and PaddleOCR in the APK |
| `play` | `dev.pocketllama` | The same models in install-time AI packs |
| Personal | `dev.pocketllama.memoryfix` | Separate existing installation; restricted components require their own redistribution rights |

The public profiles exclude Surya weights and the CC-BY-NC-SA detector. See [the license inventory](LICENSING.md). A personal package's vault does not automatically migrate to the public package.

## Verification

```bash
CHAT_JSON_JAR=/path/to/json.jar bash scripts/test-distribution.sh
bash scripts/test-core.sh
python3 scripts/release-check.py
```

The [GitHub workflow](../.github/workflows/android.yml) restores pinned dependencies and checks the host, frontend and Android build. [Validation notes](VALIDATION-0.8.0.md) distinguish automated checks from the phone and Play-track checks still needed.

## Documentation graphics

The README images use actual web UI assets with synthetic, explicitly labeled demo content. They do not invoke a model or show private user data. Native screen-capture protection is not bypassed.

With Playwright and Chromium installed:

```bash
CHAT_QA_BROWSER=/path/to/chromium node scripts/render-marketing.cjs
```

The renderer writes previews to `docs/media/`. These images and demo bridges are documentation assets; they are not packaged in the APK.
