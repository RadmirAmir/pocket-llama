# Repository workflow

Pocket Llama is maintained with a lightweight indie workflow.

- Work directly on `main` for ordinary changes. Use a branch or pull request when it is useful, not as a mandatory process.
- The owner's request authorizes the commits, merges and releases needed to complete it. Do not ask for a separate approval for routine repository steps.
- Keep application identity, signing compatibility and encrypted user data safe when changing or releasing the app.
- Run checks appropriate to each change. Report outstanding limitations honestly, and distinguish emulator and host results from physical-phone results.
- Support the portable Android device paths, including Snapdragon, MediaTek, Google Tensor and Exynos.
- Keep credentials, signing keys, personal recordings/documents, caches and incomplete model downloads out of source commits.
- Do not rewrite history or overwrite a published release artifact. Preserve the previously accepted 0.13.4 release and its frozen snapshot as a rollback point.

## Preserved 0.13.4 baseline

- Release: https://github.com/RadmirAmir/pocket-llama/releases/tag/v0.13.4-rc.1
- Frozen snapshot branch: `stable/0.13.4-2026-10-25`
- Application source used for the published APK: `4147f62200698d31207919173a6558f95af4941e`
- APK: https://github.com/RadmirAmir/pocket-llama/releases/download/v0.13.4-rc.1/PocketLlama-0.13.4-community-arm64.apk
- APK SHA-256: `06a5b7af6b851b4dc68dd788af4ca47ae85374ac1f53ab856d92074ec3664374`

Keep the published APK, release evidence, dependency locks and reconstruction assets available. Source rollback and installation rollback are different; retain signing compatibility and account for encrypted data when planning a downgrade.
