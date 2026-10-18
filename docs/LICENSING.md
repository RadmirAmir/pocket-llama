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
| Surya OCR 2 weights | Modified AI Pubs OpenRAIL-M; excluded from public profiles unless redistribution rights are documented |
| Surya `line_detector0` | CC-BY-NC-SA-4.0; excluded from public profiles unless additional rights are documented |

The Surya license includes restrictions on competing products, funding/revenue thresholds, and wording applying ShareAlike to output. Moving its download to a separate button does not remove those terms. Obtain written scope covering redistribution in a public Android app and its users. The detector needs its own coverage.

`distribution/release.json` stores approval flags. The `evidenceFile` values must refer to actual permission documents outside the public repository. `prepare-distribution.py` refuses to stage a public Surya package without both approvals. The personal profile preserves the previously used model package; it must not be uploaded as the public release.

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
