# Your first day with Pocket

[← Pocket](../README.md) · [Русский](GETTING-STARTED-RU.md) · [Requirements](COMPATIBILITY.md)

## Install and protect your space

Download **[PocketLlama-0.13.1-community-arm64.apk](https://github.com/RadmirAmir/pocket-llama/releases/download/v0.13.1-rc.1/PocketLlama-0.13.1-community-arm64.apk)** from this repository's release. You need Android 10+ with a 64-bit ARM system. The APK is approximately 662 MB; both speech models and OCR account for most of the size.

Open it on your phone and allow installation from the browser or file manager when Android asks. You can turn that installation permission off again afterward. The `.aab` file is for Play publishers and cannot be installed directly like an APK. The release includes [SHA-256 checksums](https://github.com/RadmirAmir/pocket-llama/releases/download/v0.13.1-rc.1/SHA256SUMS.txt).

On first launch, create and repeat a **six-digit PIN**. It controls access to your recordings, notes, scans and chat. Remember it: there is no account-based recovery. Microphone and camera permissions are used for the corresponding tools; grant them when you want to record or scan.

Switch the interface and the default speech engine in **Settings → Interface language**: **EN · English** selects Moonshine Streaming Medium; **RU · Русский** selects GigaAM v3. Both models are bundled and run offline. New jobs follow your selection; a resumed transcript keeps its original engine. To change an unfinished transcript to another language, use **Transcribe again** in transcription settings. Existing text is never automatically translated.

The main tabs are **Recordings**, **Scanner**, **Notes**, **Chat** and **Settings**.

## Write a note and find it later

Open **Notes → New note**. Enter a title and text; wait for **Saved** or use **Done**. Search works across titles and text. Use the pin button for important notes and Checklist for plain-text checkboxes. Deleted notes stay in Trash until you explicitly delete them permanently. Notes need no model or internet. [Full notes guide →](NOTES.md)

## Record → transcribe → remember

1. On **Recordings**, start a recording. Pause or finish it using the recording controls.
2. Open the saved recording and choose **Transcribe recording**. Moonshine Streaming Medium (EN) and GigaAM v3 (RU) are included; no speech model download is needed. The first use prepares the installed assets locally.
3. Open **Transcript** to read the timestamped text. You can correct words and speaker names. Speaker detection is approximate. The **Copy** button copies the complete transcript, with speaker names, timestamps and your corrections.
4. Once a language model is selected, choose **Create summary** in **Summary**. The summary has source references you can tap to check against the audio.
5. Use the recording menu → **Save to file** for Markdown, SRT subtitles, JSON or WAV.

Transcription processes a saved recording in short windows. “Streaming” is the Moonshine model architecture; this version does not display live captions while recording.

Keep the original recording until you have checked important details. A quiet room, clear speech and a phone placed close to the speakers help recognition.

## Pick a chat model

Open **Settings** and find the model catalog. Start with the model marked as recommended for your available memory. A larger model is not always the better choice for a phone.

| Model | Download | Best starting use |
| --- | --- | --- |
| Qwen 3.5 · 0.8B | 646 MB | Short questions and simple summaries |
| Qwen 3.5 · 2B | 1.57 GB | A more capable option when memory permits |
| Qwen 3.5 · 4B | 3.01 GB | Higher memory budget; can be slower and warmer |

Choose **Download**, review the size and confirm **Download**. Wi-Fi is useful for the initial transfer. You can pause and resume; the app verifies the complete file before installing it. When finished, choose **Use model**. The catalog applies a conservative CPU profile with a 2,048-token context.

Open **Chat**. If the model is stopped, use the activation control. Wait for **Model ready**, then send a short question. Chat and summaries now work without an internet connection. Smaller models can lose track of long conversations; start a new chat when you change subjects.

Already have a compatible model? Use **Import your own GGUF** in Settings. Compatibility and memory still matter; an arbitrary GGUF or image model is not guaranteed to work. The supplied catalog is the easiest starting point.

## Search your own materials in Chat

Open **Chat → Tools → Personal knowledge**, download the optional 329 MB search model after reviewing its terms, then enable the switch. Choose **Update index** or ask the first question. Sources link back to notes, OCR pages and transcript times. A new conversation starts with the tool disconnected. Keep Pocket open during indexing. [Full guide →](RAG.md)

## Scan → recognize → find again

Open **Scanner** → **Scan a document** to capture a page, or **Photo or PDF** to import existing pages. Capture several pages into the same document if needed. Adjust the crop, perspective and orientation before recognition.

Choose **Recognize document**. The public build uses bundled PaddleOCR for Russian and English; no separate OCR download is needed. Read and correct the recognized text, search your documents, or export a PDF with a text layer. Sharp, evenly lit pages work better than blurred or curved ones.

Select **Surya OCR 2** inside a document for page layout, reading order, tables and optional table-grid refinement. First setup shows the complete model terms; accepting starts a resumable 1.47 GB model download. Once prepared, recognition runs offline; photos and text are never uploaded. Surya is offered for personal, noncommercial use, subject to its use restrictions and output license terms. PaddleOCR remains available without this download.

## Try offline mode

After installing the app and selecting a downloaded LLM, enable airplane mode. Try recording and English/Russian transcription, scan a page, then ask a short question in Chat. Core AI processing stays on the phone. Video imports by URL, new model downloads and external links need network access.

## Common questions

| What you see | What to try |
| --- | --- |
| Android says the app is incompatible | Check Android 10+ and a **64-bit ARM OS**, not just a 64-bit chip. x86 and 32-bit Android packages are not provided. |
| Installation fails | Check free storage and finish the APK download. If Android reports a signature conflict, keep the existing app and obtain an update signed for it; do not uninstall a vault you still need. |
| Model needs more memory | Close memory-heavy apps and pick a smaller model. Virtual/expanded RAM is not a substitute for the catalog's actual RAM checks. |
| Model download pauses | Keep enough free space and resume from the same catalog entry. Do not rename a partial file. |
| Chat is slow or the phone gets warm | Try 0.8B, shorter messages and the default context. Give a warm phone time to cool; larger models cost more time and power. |
| No summary yet | Finish transcription, select a local LLM in Settings, then choose Create summary. |
| OCR or transcript has mistakes | Improve light/audio, adjust crop or orientation, and correct important text manually. |
| Video import stops at `video/runtime-id.txt` | Update community to 0.9.3 or newer, then open the same record and choose **Retry import**. This fixes the missing video bundle marker in 0.9.2. |
| PIN forgotten | No cloud recovery exists. Clearing app data or uninstalling loses vault access; it does not recover your content. |

## Updates and existing installations

If Android only shows **“App not installed”**, try opening the downloaded APK through the phone's built-in file manager. For the previous 0.9.0 release, a [small local installer helper](../tools/community-installer/README.md) can verify the community APK and show Android's detailed error, including version or signature conflicts. It has no network permission and does not uninstall existing apps or access their vaults.

The public package is **`dev.pocketllama`**. The previous personal package is **`dev.pocketllama.memoryfix`**. They install alongside one another, with separate encrypted vaults. Installing the public preview does not migrate the personal vault. Keep the original app if it holds content you need.

An in-place update needs the same package ID and signing identity. Self-built debug APKs, CI debug APKs and maintainer-signed releases may use different certificates. Export important content while you still have access before changing installation channels. A normal compatible update preserves app data; uninstalling does not.

Exported files and clipboard text are outside Pocket's encryption. Choose a destination you trust. Android app backup is disabled; there is no automatic cloud backup. See [Privacy & security](SECURITY.md).

Found a bug? [Open an issue](https://github.com/RadmirAmir/pocket-llama/issues/new/choose) with the phone, Android version, model and steps. Use a harmless example instead of attaching private content.
