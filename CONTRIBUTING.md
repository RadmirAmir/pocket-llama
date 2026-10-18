# Contributing

Use small changes with a concrete reproduction and validation. Never commit user recordings, documents, credentials, model weights or signing material. Synthetic fixtures belong in `tests/fixtures`.

Restore dependencies from the lock files, build the ARM64 runtime, and run the tests relevant to the change. Distribution changes should include the resumable-transfer tests and catalogue UI tests. Keep the default community profile independent of Google Play services. New catalog models need pinned bytes, license review and runtime compatibility results; do not label desktop throughput as a phone benchmark.

Preserve the encrypted vault and in-place update compatibility. Schema or signing changes need an explicit migration design.
