# Repository workflow

## Accepted baseline

The owner accepted Pocket Llama 0.13.4 on 2026-10-25 and requested that it remain a permanent return point before performance work begins.

- Published release tag: `v0.13.4-rc.1`. Never move or delete this tag.
- Frozen repository snapshot branch: `stable/0.13.4-2026-10-25`. Never advance, force-push or delete this branch.
- Repository head before this workflow-only checkpoint: `0feec6bb36f9f52da0b76e62f5cdbf6f72d55238`.
- Application source used for the published APK: `4147f62200698d31207919173a6558f95af4941e`.
- Published release: https://github.com/RadmirAmir/pocket-llama/releases/tag/v0.13.4-rc.1
- APK: https://github.com/RadmirAmir/pocket-llama/releases/download/v0.13.4-rc.1/PocketLlama-0.13.4-community-arm64.apk
- APK SHA-256: `06a5b7af6b851b4dc68dd788af4ca47ae85374ac1f53ab856d92074ec3664374`.

Keep the published APK, release evidence, dependency locks and reconstruction assets available. Source rollback and installation rollback are different: preserve signing compatibility and encrypted user data when planning a downgrade.

## Development and merging

1. `main` holds the owner's accepted application version. All subsequent implementation, fixes, optimization and experiments belong on a separate branch.
2. The next development branch is `feature/inference-performance`. Create other task branches from the accepted main branch when appropriate.
3. A request to research, implement, build or provide a test APK does not authorize merging it into `main`.
4. Merge through a pull request only after the owner has tested the specific candidate and explicitly confirmed that it should be merged. Passing automated checks does not substitute for that confirmation.
5. Preserve the established application identity and update path. Do not create a separate companion app unless the owner explicitly requests one.
6. Do not force-push `main`, rewrite the accepted baseline, or replace an existing release artifact. Keep the release tag and frozen snapshot branch even after future versions are accepted.
7. Run checks appropriate to the change. Distinguish host measurements from physical-device results, and preserve the supported Snapdragon, MediaTek, Google Tensor and Exynos device paths.
8. Keep credentials, signing keys, personal recordings/documents, caches and incomplete model downloads out of source commits.

The baseline checkpoint changes repository workflow documentation only; it does not change application code, model weights, version numbers or the published APK.
