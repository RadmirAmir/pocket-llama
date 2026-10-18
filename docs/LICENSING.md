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
| Surya `line_detector0` | CC-BY-NC-SA-4.0; adapted ONNX bundled in free community with full attribution, modification notice and source recipe |

Community 0.9.1 is a free personal assistant with no ads, payments or hosted OCR service. The publisher has chosen noncommercial distribution. The model license permits distribution subject to its conditions; the detector license permits noncommercial sharing and adaptations with attribution and ShareAlike. The previous blanket exclusion from all public profiles was stricter than this policy requires.

Surya's complete pinned license is in `notices/Surya-weights.txt`. It includes restrictions on competing products, organization revenue/funding thresholds with personal/research exceptions, and wording applying ShareAlike to output. Personal use does not waive every clause. Moving its download to a separate button does not remove any terms. Before setup, the app displays the full model/detector licenses and requires an explicit acceptance action. Refusing does not download models. See `notices/Surya-community.txt` for the exact scope and sources. This records our packaging decision; it is not a claim of special permission or a blanket clearance for third-party commercial use.

`distribution/release.json` enables the compact, free-personal-use community profile. Its detector adaptation retains CC BY-NC-SA 4.0; the original source, configuration, ONNX export and parity checks are in `scripts/prepare-scanner.py`. The VLM/projector stay out of the APK and are downloaded unchanged from the pinned author revision, with size/SHA-256 verification and resume support.

The separate `suryaRedistribution` and `detectorRedistribution` approval flags remain false. They are only for separately documented additional rights, such as a broader licensed public/Play build; no permission document has been fabricated. Play keeps Surya disabled by default. The personal package must not be uploaded as the public app.

Primary references:

- https://github.com/moonshine-ai/moonshine
- https://huggingface.co/moonshine-ai/moonshine-voice-assets

- https://huggingface.co/ai-sage/GigaAM-v3
- https://huggingface.co/vvpreo/gigaam-v3-onnx
- https://huggingface.co/datalab-to/surya-ocr-2-gguf/blob/main/LICENSE
- https://huggingface.co/datalab-to/line_detector0
- https://github.com/yausername/youtubedl-android/tree/0.18.1
- https://github.com/yt-dlp/yt-dlp/tree/2026.08.19

The release profile and the in-app license list must agree about what is actually bundled. Catalog LLM files are not included in the APK/AAB and retain their Apache-2.0 notices and pinned source links.
