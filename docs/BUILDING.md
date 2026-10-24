# Build Pocket from source

[Product overview](../README.md) · [Release and signing guide](RELEASE.md) · [Licenses](LICENSING.md)

[Architecture and extension boundaries](ARCHITECTURE.md) documents resource
ownership, speech checkpoint compatibility, UI transport and future platform work.

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
| `community` | `dev.pocketllama` | GigaAM v3 INT8, Moonshine Streaming Medium, Silero VAD, CAM++ and PaddleOCR in the APK; Surya VLM/projector downloaded on demand; no separate line detector |
| `play` | `dev.pocketllama` | The same models in install-time AI packs |
| Personal | `dev.pocketllama.memoryfix` | Separate existing installation; restricted components require their own redistribution rights |

The free community profile enables Surya for personal, noncommercial use. The VLM is downloaded from the author after the user accepts the full model terms. Play keeps Surya disabled by default. See [the license inventory](LICENSING.md). A personal package's vault does not automatically migrate to the public package.

## Verification

For speech orchestration and the shared UI request client (JDK 17 and Node;
no Android SDK, model weights or browser required):

```bash
bash scripts/test-voice-core.sh
node scripts/test-native-rpc.cjs
```

For the affected screens, with Playwright and Chromium installed:

```bash
node scripts/test-studio-ui.cjs
node scripts/test-notes-ui.cjs
node scripts/test-scanner-ui.cjs
node scripts/test-i18n-ui.cjs
```

Set `CHAT_QA_BROWSER` if Chromium is installed outside Playwright's cache.
UI tests use synthetic data and native responses; they do not run phone models.

```bash
CHAT_JSON_JAR=/path/to/json.jar bash scripts/test-distribution.sh
bash scripts/test-core.sh
python3 scripts/release-check.py
```

The [GitHub workflow](../.github/workflows/android.yml) restores pinned dependencies and checks the host, frontend and Android build. [Validation notes](VALIDATION-0.9.3.md) distinguish automated checks from the phone and Play-track checks still needed.

## Documentation graphics

The README images use the actual EN/RU web UI assets and the same dictionary as the APK with synthetic, explicitly labeled demo content. They do not invoke a model or show private user data. Native screen-capture protection is not bypassed.

With Playwright and Chromium installed:

```bash
CHAT_QA_BROWSER=/path/to/chromium node scripts/render-marketing.cjs
```

The renderer writes previews to `docs/media/`. These images and demo bridges are documentation assets; they are not packaged in the APK.
