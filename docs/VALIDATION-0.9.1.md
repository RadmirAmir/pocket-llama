# Pocket 0.9.1 community validation

This release restores optional Surya in the free community edition. Its inference
pipeline, model revisions, native engines and vault format are unchanged from
0.9.0. The changes enable the distribution capability, package the existing
detector and show the full model terms before setup.

## Published APK

| Field | Verified value |
| --- | --- |
| File | `PocketLlama-0.9.1-community-arm64.apk` |
| Size | 749,706,483 bytes |
| SHA-256 | `91e424025d3d0594e47fd277bea241a4dd876b1a4daddba1d8b6fa8945cb5b93` |
| Package | `dev.pocketllama` |
| Version | 0.9.1 / versionCode 24 |
| Android | Minimum API 29, target API 36; ARM64 |
| Update certificate | Matches the previous community/personal preview certificate |

The GitHub release asset's server-side SHA-256 matches the local APK. An
anonymous request retrieved the published checksum file. The artifact validator
checked all 17 model files against the locked sizes and hashes, current UI
assets, the complete model notices and absence of bundled Surya VLM/projector.
The scanner retains `connect-src 'none'`; terms are returned through a native
bridge that reads four fixed local notice files.

## Automated checks

- [Source build and checks](https://github.com/RadmirAmir/pocket-llama/actions/runs/39150233347): host, frontend, ARM64 Android APK and Play bundle builds passed.
- Scanner UI: 52 checks passed, including local terms, missing-terms failure,
  cancellation without a download, one job after acceptance and EN/RU setup.
- EN/RU regression checks passed for language persistence, original user text,
  partial ASR language and preservation of active model downloads.
- Four distribution-policy tests passed: free community delivery, Play default,
  explicit opt-out and refusal to inherit the free policy for monetized builds.
- [Published APK installation check](https://github.com/RadmirAmir/pocket-llama/actions/runs/39151160249) passed on Android 16: fresh installation, startup and the 0.9.0 → 0.9.1 update, using the exact published APK.

## Scope

Emulator installation does not establish physical-device OCR performance.
This update has not been benchmarked on the user's Samsung phone or installed
through Google Play. The release is a GitHub community preview. Surya's separate
model/output terms and the detector's CC BY-NC-SA license remain in force.
