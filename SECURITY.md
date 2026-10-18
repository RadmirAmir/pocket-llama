# Security reporting

The publisher must set a private security contact before the first public release. Do not include real recordings, documents, PINs, API tokens or signing material in public issues.

The application stores sensitive content locally. Runtime and model dependencies are pinned and checksummed. Catalog updates require a pinned RSA signature. GGUF/ONNX weights are data inputs to the packaged native runtime, not a mechanism for downloading native code. Keep that runtime updated and test malformed model input handling.

Private signing keys and production report credentials must stay outside source control and public artifacts. The previously distributed personal/debug update key must never be republished as part of the open source archive.
