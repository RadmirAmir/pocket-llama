# Distribution licenses

Pocket's own code is Apache-2.0. Preserve all third-party notices. This document records packaging decisions; it does not grant rights to model weights.

| Component | Declared license / treatment |
| --- | --- |
| llama.cpp and ONNX Runtime | MIT; sources and pinned versions are in the lock files and build scripts |
| GigaAM v3 and the selected ONNX conversion | MIT; preserve the model and converter notices |
| Moonshine Voice 0.1.5 and English Streaming Medium | MIT; runtime, word-alignment decoder, model mirror revision and hashes are pinned in `moonshine-runtime.lock.json` and `moonshine-models.lock.json`; see `notices/MOONSHINE.txt` |
| Silero VAD | MIT; preserve the bundled notice |
| CAM++ / 3D-Speaker | Retains its upstream notice; the exact conversion is pinned in `speech-models.lock.json` |
| PaddleOCR and OpenCV | Apache-2.0; preserve notices |
| SQLCipher, AndroidX, Kotlin, jsoup | Retain the notices and source links in `notices/` |
| Video runtime | Mixed upstream licenses, including the GPL-3.0 notice supplied by the runtime project. Preserve source links, build recipes and modifications; distribution review must cover corresponding-source obligations. Pocket does not relicense this runtime. |
| Surya OCR 2 weights | Modified AI Pubs OpenRAIL-M; optional direct upstream download in free community, after displaying and accepting the full terms |

Community 0.9.2 is a free personal assistant with no ads, payments or hosted OCR service. The separate Surya `line_detector0` model, its ONNX conversion, runtime, export dependencies and line-overlay UI have been removed. New APKs neither bundle nor download that model. On upgrade, only its known private cache files are removed; existing documents, OCR results and Surya VLM/projector files are preserved. Earlier releases retain their original notices and conditions.

Surya's complete pinned license is in `notices/Surya-weights.txt`. It includes restrictions on competing products, organization revenue/funding thresholds with personal/research exceptions, and wording applying ShareAlike to output. Personal use does not waive every clause. Moving its download to a separate button does not remove any terms. Before setup, the app displays the full model license and requires an explicit acceptance action. Refusing does not download models. See `notices/Surya-community.txt` for the exact scope and sources. This records our packaging decision; it is not a claim of special permission or a blanket clearance for third-party commercial use.

`distribution/release.json` enables the compact, free-personal-use community profile. The Surya VLM/projector stay out of the APK and are downloaded unchanged from the pinned author revision, with size/SHA-256 verification and resume support. Table-grid refinement uses this same VLM; PaddleOCR keeps its own Apache-2.0 detection stage.

The `suryaRedistribution` approval flag remains false. It is only for separately documented additional rights, such as a broader licensed public/Play build; no permission document has been fabricated. Play keeps Surya disabled by default. The personal package must not be uploaded as the public app. Removing the separate line detector does not remove the main Surya model's license conditions.

Primary references:

- https://github.com/moonshine-ai/moonshine
- https://huggingface.co/moonshine-ai/moonshine-voice-assets

- https://huggingface.co/ai-sage/GigaAM-v3
- https://huggingface.co/vvpreo/gigaam-v3-onnx
- https://huggingface.co/datalab-to/surya-ocr-2-gguf/blob/main/LICENSE
- https://github.com/yausername/youtubedl-android/tree/0.18.1
- https://github.com/yt-dlp/yt-dlp/tree/2026.08.19

The release profile and the in-app license list must agree about what is actually bundled. Catalog LLM files are not included in the APK/AAB and retain their Apache-2.0 notices and pinned source links.

## Optional EmbeddingGemma search model

The personal-knowledge tool downloads the pinned Unsloth EmbeddingGemma 300M Q8_0 GGUF directly after model-term acceptance. It is not bundled or Apache-licensed. The applicable Gemma Terms of Use, prohibited-use policy and required notice are included under `notices/Gemma*.txt` and staged into every distribution. Pocket code remains Apache-2.0. See [RAG setup and architecture](RAG.md).
