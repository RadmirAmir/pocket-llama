# 0.10.1 navigation hotfix validation

Application source: `5b59478129ef609099d363778b14121fbf2d14ba`. Community package `dev.pocketllama`, version code 28.

- [Source CI](https://github.com/RadmirAmir/pocket-llama/actions/runs/39276809569): all host, frontend and Android build checks passed, including existing Studio, Notes and EN/RU interface checks.
- Caption layout uses natural content widths, single-line text and full-height touch targets. Manrope 600 caption widths were checked for RU/EN at 320, 360, 393 and 412 CSS pixels with 100%, 130% and 160% text sizes. A compact caption size applies at 340 pixels or narrower.
- The incremental APK package reuses the verified 0.10.0 DEX and unchanged large assets. Java, manifest source and resource inputs are unchanged; generated R.java and actual DEX bytes match the validated baseline. AAPT regenerated version metadata; all packaged UI assets are checked against source.
- All 16 model files and 6 video program files verified; preview signing certificate matches 0.10.0. See [artifact report](artifact-validation-0.10.1.json).
- APK size: 640,826,467 bytes. SHA-256: `4566700bd37d6651013c607648dddc4f0ec154d0a03bb187043ffd766ff5b91b`.

The initial rc.1 APK was withdrawn after its compressed resource table caused Android to reject installation. Corrected rc.2 stores the resource table uncompressed, with four-byte alignment; the artifact validator and published-package preflight now enforce this requirement. ZIP alignment and the signed APK have been verified.

Published-package fresh-install and 0.10.0 → 0.10.1 upgrade evidence will be recorded after the Android run completes. These checks do not certify every physical phone, font substitution or extreme accessibility scaling setting.
