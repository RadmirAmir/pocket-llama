# Your first day with Pocket

[← Pocket](../README.md) · [Русский](GETTING-STARTED-RU.md) · [Requirements](COMPATIBILITY.md)

## Install and protect your space

Download **[PocketLlama-0.8.0-community-arm64.apk](https://github.com/RadmirAmir/pocket-llama/releases/download/v0.8.0-rc.1/PocketLlama-0.8.0-community-arm64.apk)** from this repository's release. You need Android 10+ with a 64-bit ARM system. The APK is approximately 271 MB; speech and OCR models account for most of the size.

Open it on your phone and allow installation from the browser or file manager when Android asks. You can turn that installation permission off again afterward. The `.aab` file is for Play publishers and cannot be installed directly like an APK. The release includes [SHA-256 checksums](https://github.com/RadmirAmir/pocket-llama/releases/download/v0.8.0-rc.1/SHA256SUMS.txt).

On first launch, create and repeat a **six-digit PIN**. It controls access to your recordings, scans and chat. Remember it: there is no account-based recovery. Microphone and camera permissions are used for the corresponding tools; grant them when you want to record or scan.

The current interface uses Russian. The main tabs are **«Записи»** (Recordings), **«Сканер»** (Scanner), **«Чат»** (Chat) and **«Настройки»** (Settings).

## Record → transcribe → remember

1. On **«Записи»**, start a recording. Pause or finish it using the recording controls.
2. Open the saved recording and choose **«Распознать запись»**. GigaAM v3 is included; no speech model download is needed. The first use prepares the installed assets locally.
3. Open **«Расшифровка»** to read the timestamped text. You can correct words and speaker names. Speaker detection is approximate.
4. Once a language model is selected, choose **«Создать конспект»** in **«Итоги»**. The summary has source references you can tap to check against the audio.
5. Use the recording menu → **«Сохранить в файл»** for Markdown, SRT subtitles, JSON or WAV.

Keep the original recording until you have checked important details. A quiet room, clear speech and a phone placed close to the speakers help recognition.

## Pick a chat model

Open **«Настройки»** and find the model catalog. Start with the model marked as recommended for your available memory. A larger model is not always the better choice for a phone.

| Model | Download | Best starting use |
| --- | --- | --- |
| Qwen 3.5 · 0.8B | 646 MB | Short questions and simple summaries |
| Qwen 3.5 · 2B | 1.57 GB | A more capable option when memory permits |
| Qwen 3.5 · 4B | 3.01 GB | Higher memory budget; can be slower and warmer |

Choose **«Скачать»**, review the size and confirm **«Загрузить»**. Wi-Fi is useful for the initial transfer. You can pause and resume; the app verifies the complete file before installing it. When finished, choose **«Использовать»**. The catalog applies a conservative CPU profile with a 2,048-token context.

Open **«Чат»**. If the model is stopped, use the activation control. Wait for **«Модель готова»**, then send a short question. Chat and summaries now work without an internet connection. Smaller models can lose track of long conversations; start a new chat when you change subjects.

Already have a compatible model? Use **«Импортировать свой GGUF»** in Settings. Compatibility and memory still matter; an arbitrary GGUF or image model is not guaranteed to work. The supplied catalog is the easiest starting point.

## Scan → recognize → find again

Open **«Сканер»** → **«Сканировать документ»** to capture a page, or **«Фото или PDF»** to import existing pages. Capture several pages into the same document if needed. Adjust the crop, perspective and orientation before recognition.

Choose **«Распознать документ»**. The public build uses bundled PaddleOCR for Russian and English; no separate OCR download is needed. Read and correct the recognized text, search your documents, or export a PDF with a text layer. Sharp, evenly lit pages work better than blurred or curved ones.

The public preview does not include Surya's layout and table features. The source contains those optional paths for separately permitted builds.

## Try offline mode

After installing the app and selecting a downloaded LLM, enable airplane mode. Try recording and transcription, scan a page, then ask a short question in Chat. Core AI processing stays on the phone. Video imports by URL, new model downloads and external links need network access.

## Common questions

| What you see | What to try |
| --- | --- |
| Android says the app is incompatible | Check Android 10+ and a **64-bit ARM OS**, not just a 64-bit chip. x86 and 32-bit Android packages are not provided. |
| Installation fails | Check free storage and finish the APK download. If Android reports a signature conflict, keep the existing app and obtain an update signed for it; do not uninstall a vault you still need. |
| Model needs more memory | Close memory-heavy apps and pick a smaller model. Virtual/expanded RAM is not a substitute for the catalog's actual RAM checks. |
| Model download pauses | Keep enough free space and resume from the same catalog entry. Do not rename a partial file. |
| Chat is slow or the phone gets warm | Try 0.8B, shorter messages and the default context. Give a warm phone time to cool; larger models cost more time and power. |
| No summary yet | Finish transcription, select a local LLM in Settings, then choose «Создать конспект». |
| OCR or transcript has mistakes | Improve light/audio, adjust crop or orientation, and correct important text manually. |
| PIN forgotten | No cloud recovery exists. Clearing app data or uninstalling loses vault access; it does not recover your content. |

## Updates and existing installations

The public package is **`dev.pocketllama`**. The previous personal package is **`dev.pocketllama.memoryfix`**. They install alongside one another, with separate encrypted vaults. Installing the public preview does not migrate the personal vault. Keep the original app if it holds content you need.

An in-place update needs the same package ID and signing identity. Self-built debug APKs, CI debug APKs and maintainer-signed releases may use different certificates. Export important content while you still have access before changing installation channels. A normal compatible update preserves app data; uninstalling does not.

Exported files and clipboard text are outside Pocket's encryption. Choose a destination you trust. Android app backup is disabled; there is no automatic cloud backup. See [Privacy & security](SECURITY.md).

Found a bug? [Open an issue](https://github.com/RadmirAmir/pocket-llama/issues/new/choose) with the phone, Android version, model and steps. Use a harmless example instead of attaching private content.
