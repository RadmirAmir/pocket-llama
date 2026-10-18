# Validation of 0.8.0

Validated on the build host on 2026-10-18. This is a release candidate. Physical-device acceptance, installation from a Play test track and GitHub Actions have not been verified by this report.

## Functional checks

| Check | Result |
| --- | --- |
| GGUF metadata, memory planning and inference arguments | 101 assertions passed |
| Signed catalog, model recommendations and actual HTTP download recovery | 27 assertions passed |
| Voice state and audio helpers | 23 assertions passed |
| Surya process guard | 7 assertions passed |
| Scanner core, storage and privacy | 54 + 40 + 11 assertions passed |
| Surya guard and page pipeline | 16 + 79 assertions passed |
| Bundled Surya assets with real pinned weights and network access prohibited | 16 assertions passed |
| Studio, scanner and catalog browser UI | 44 + 44 + 15 assertions passed |
| Chat browser UI | Passed text/image payloads, streaming, cancellation, retry, draft/history, safe rendering, mobile layout and the explicit report action |
| PDF and spreadsheet imports | Passed PDF text/rasterization and XLSX merged-cell/inline-string checks |

Download tests use a real local HTTP server. They exercise interruption/resume, cancellation, ignored or invalid ranges, an already complete partial file and a bad checksum while preserving the previous installed model. Catalog tests reject tampering, invalid paths, insecure URLs and duplicate entries. UI checks include small and large viewports, safe model labels and focus retention during progress updates.

All three pinned catalog GGUF files were downloaded, checked against their full SHA-256 values and loaded by the pinned CPU runtime. Each produced a Russian response with thinking disabled and terminated normally. These are host compatibility smoke tests; they do not establish speed, memory use or answer quality on a phone. Reproduce with `scripts/test-catalogue-live.py` after building the host runtime.

## Android artifacts

| Artifact | Bytes | Embedded model files verified |
| --- | ---: | ---: |
| Personal ARM64 APK | 1,515,661,519 | 10 |
| Community ARM64 preview APK | 271,439,765 | 7 |
| Unsigned Play AAB | 271,450,429 | 7 |

`scripts/validate-distribution-artifacts.py` verified model sizes and full hashes inside the artifacts, current catalog/UI assets and exclusion of restricted Surya assets from public profiles. Its machine-readable result is [artifact-validation-0.8.0.json](artifact-validation-0.8.0.json).

The personal APK signature matches the supplied 0.7.3 APK certificate. This supports an ordinary Android update; retention of a real user's PIN, vault and recordings still requires device acceptance. The community APK has a different package ID and a test signature.

The Play modules were assembled with Android SDK 36 and validated with official bundletool 1.18.3. A universal APK generated from the AAB contains the speech and OCR models once each. This validates the pack structure and fusion, not Play delivery to an actual device. Native ELF LOAD segments in the supplied ARM64 runtime have at least 16 KB alignment. A 16 KB device/emulator run is still required.

## Remaining production acceptance

The production gate remains closed until publisher contacts, hosted privacy/reporting endpoints, publisher signing credentials and observed device results are provided. Record the phone checks in `distribution/device-validation.json`; do not mark them passed based on this host report. The public source workflow also needs a fresh GitHub Actions run to establish that its dependency restoration and NDK build succeed outside this prepared workspace.

The unsigned AAB and test-signed community APK are previews. No Google Play upload, store approval or production rollout is represented by these files.
