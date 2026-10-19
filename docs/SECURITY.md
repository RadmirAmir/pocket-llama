# Privacy & security in Pocket

[← Pocket](../README.md) · [Русский](SECURITY-RU.md)

This page describes the implementation in the 0.9.0 public preview. It is a technical product explanation, not an independent security audit or the publisher's final legal privacy policy.

## What stays on your phone

Recording, GigaAM (RU) / Moonshine Streaming Medium (EN) speech recognition, speaker grouping, PaddleOCR, local LLM chat and summaries run on the Android device. There is no cloud inference account, advertising SDK, automatic analytics or automatic crash-reporting integration. Recordings, scans, transcripts and conversations are not automatically uploaded.

The public APK includes the speech and OCR assets. Chat and summaries need a compatible language model downloaded or imported once. After preparation, the core tools can run in airplane mode.

## Saved content and access

| Protection | Implementation |
| --- | --- |
| Audio files | Authenticated chunked AES-256-GCM encryption with per-file keys wrapped by the vault key |
| Photos, document records, chat and app-managed private files | Encrypted local files using AES-GCM |
| Transcripts and recording metadata | SQLCipher database with a protected database secret |
| Personal notes, titles and search fields | Separate SQLCipher database, using the same protected vault secret; no external search service |
| Keys | Android Keystore; hardware backing depends on the device |
| App access | Six-digit PIN with retry delays; the PIN is a separate access control, not the encryption key itself |
| Backgrounding | The app locks when it backgrounds; an active recording or processing service can continue |
| Screen capture | Android `FLAG_SECURE` on app activities |
| Android backup | Disabled in the manifest and backup/extraction rules |

The app decrypts data as needed while you use it. Encryption at rest does not protect content already exported, copied to another app, or exposed by a compromised operating system or an unlocked device. There is no claim of an independently audited security guarantee.

## When a network is used

| Explicit action | Network boundary |
| --- | --- |
| Download/refresh a model catalog or download a model | The hosting provider receives the request needed to serve the catalog/file; personal recordings, scans and chats are not part of that download |
| Import video from a URL | The video provider is contacted to retrieve the requested media; the imported audio is processed locally |
| Open a model source or external video link | The browser or chosen app contacts that site |
| Export/share/copy | The selected destination receives the content you choose; its own networking and storage rules then apply |
| Enable the optional LAN API | Other network clients with the API token can access the selected model's HTTP API; there is no TLS on this API. It is off by default and does not expose the recording/document database |
| Send a voluntary content report | Only the reviewed answer excerpt, comment, category and app version are submitted after pressing Send; the rest of the conversation, recordings and documents are not automatically attached |

**No report endpoint is configured in the public preview.** The source supports configuring one for a publisher. Reports require HTTPS, do not follow redirects, and are not silently queued after a failed send. A publisher enabling reports must disclose the receiving service and retention policy.

Model downloads verify SHA-256 and use a signed catalog. This checks downloaded bytes against the expected artifact; it does not guarantee that a model's answers are correct or safe.

## Your vault belongs to this installation

There is no cloud sync, cloud vault copy or PIN reset service. Uninstalling the app or clearing its data removes access to the encrypted vault. Android backup is disabled. Export important content while you have access and protect exported copies yourself.

The personal and public packages use separate storage and keys. Installing the public APK does not migrate the personal vault. Compatible updates must preserve the package and signing identity.

## Inspect the implementation

[Vault and encryption](../app/src/main/java/dev/pocketllama/security/) · [Chat storage](../app/src/main/java/dev/pocketllama/ChatStore.java) · [Photo storage](../app/src/main/java/dev/pocketllama/photo/PhotoStore.java) · [Voice database](../app/src/main/java/dev/pocketllama/voice/VoiceStore.java) · [Manifest](../app/src/main/AndroidManifest.xml) · [Feedback reporting](../app/src/main/java/dev/pocketllama/FeedbackReporter.java)

The separate [publisher privacy-policy draft](PRIVACY.md) still needs publisher identity, contacts and the final deployment details before use as the store policy. Please use synthetic examples in public bug reports.
