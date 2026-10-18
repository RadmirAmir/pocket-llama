# Publishing Pocket 0.9.1

The code, personal update and public preview are prepared independently. Uploading to Google Play still requires the publisher's verified account, a production signature, real-device acceptance and completed store declarations.

## One source tree, three channels

`personal` retains `dev.pocketllama.memoryfix`. Sign it with the previous private update key to install over 0.7.3. GigaAM and PaddleOCR are always bundled. The supplied compact APK is built with `scripts/build-apk.py --channel personal --no-bundled-surya`; it retains the previous line detector and reuses verified Surya VLM/projector files. A fresh Surya setup downloads those large files separately.

Omit `--no-bundled-surya` for a fully bundled personal build, after restoring the permitted weights with `scripts/prepare-surya-weights.py --accept-model-license`. That approximately 1.52 GB APK copies and verifies the Surya files locally on first use without a network request. It needs additional disk space; bundled weights do not make inference faster. Broader licensed distribution still requires rights for both components; separate downloads do not remove model restrictions.

`community` uses `dev.pocketllama`, includes both speech models/PaddleOCR and the CC BY-NC-SA detector, and has no Google Play dependency. The free personal-use profile restores optional Surya: its VLM/projector are downloaded directly from the author after explicit acceptance of the full local license texts. No ads, paid features or hosted OCR are part of this edition. Preserve the model licenses and conversion-source attribution. It is a separate installation from the personal app. It does not migrate or erase the personal vault. Do not delete the personal app to try this preview.

`play` uses the same public ID. GigaAM and its metadata are in `speech_pack`; OCR weights and metadata are in `ocr_pack`. Both are install-time AI packs. The paths inside AssetManager are unchanged, and no Play AI runtime SDK is required for this delivery mode. The installer obtains the model packs before first launch. Native libraries remain in the base module. Optional licensed Surya packs are split into VLM and vision/detector packs.

## Publisher settings and signing

Fill `distribution/release.json`: publisher name, support email, a hosted privacy policy and the HTTPS JSON endpoint for voluntary content reports. The catalog update URL is optional: without it, the signed catalog updates with the application.

Keep all keys outside the repository. Release signing reads these environment variables:

```text
POCKET_KEYSTORE
POCKET_KEY_ALIAS
POCKET_STORE_PASSWORD
POCKET_KEY_PASSWORD
```

Use Play App Signing with a separate upload key. For the direct bundletool build, pass `--sign` to `scripts/build-play-bundle.py`; this signs the AAB using the environment variables above. The default output is deliberately unsigned. Decide whether to retain an app signing key usable for GitHub APK releases before enrolling. Cross-channel updates require the same package ID and compatible signing identity. Never include an upload or app-signing key in the repository, release ZIP or CI logs.

The supplied personal APK uses the old update certificate. A public preview signed with a test key is not a production signing identity. If the public ID or signature changes later, Android will not perform a normal in-place update; plan an explicit encrypted data migration before asking users to switch.

## Catalog maintenance

The manifest fixes each URL to a source revision, size, SHA-256, architecture, minimum app version and CPU/context defaults. Imported templates are not fetched as executable code. The app uses the GGUF template supported by its pinned runtime.

Generate a private RSA key of at least 3072 bits outside the repository, update `distribution/model-catalog.json`, then sign:

```bash
python3 scripts/sign-catalog.py --input distribution/model-catalog.json --key /secure/path/catalog-key.pem --output app/src/main/assets/model-catalog/catalog.signed.json --public-key app/src/main/assets/model-catalog/public-key.txt
```

Distribute the signed envelope at `catalogUrl` for updates. The app rejects invalid signatures and older revisions, retaining the bundled catalog. A different public key must be shipped in an app update. Back up the publisher's private key separately. The development catalog key is not part of the public source export.

## Acceptance before production

Run the app on Qualcomm, MediaTek and Exynos devices in the supported memory range. Verify a fresh install in airplane mode for ASR/OCR; download pause, process death, restart and low storage; recording with the screen locked; multi-page OCR cancellation; long transcription and summary; thermal interruption; updates without losing the PIN/vault; and all native libraries in a 16 KB environment. Record observed results in `distribution/device-validation.json` and set `approved` only after acceptance.

```bash
python3 scripts/release-check.py --production
python3 scripts/prepare-build-tools.py
./gradlew -Ppocket.channel=play :app:buildReleasePreBundle :app:assetPackReleasePreBundleTask :app:lintVitalRelease
python3 scripts/build-play-bundle.py --bundletool build/tools/bundletool.jar
```

In Play Console complete Data safety, app access instructions for the locally created PIN, content rating, foreground-service declarations, generative-AI content handling and privacy policy. The specialUse services need their actual user-started purposes reviewed. New personal developer accounts may require 12 continuously opted-in testers for 14 days before requesting production access.

Validate the generated AAB with bundletool and exercise the complete split installation through a Play internal test. Then submit a closed test and use staged rollout. Host-only tests and AAB validation do not establish that the Play installation or a physical phone works.

## Source publication

`scripts/export-public-source.py` creates a source-only archive without keys, model weights, native outputs or caches. The GitHub workflow reconstructs reviewed dependencies. Create the publisher's repository, import this archive and run CI before tagging a public release. Repository creation and Play Console submission are separate external account operations.

Primary delivery references: https://developer.android.com/google/play/on-device-ai and https://support.google.com/googleplay/android-developer/answer/9859372
