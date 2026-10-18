# Validation of 0.9.0

Checked on the build host on 2026-10-18. This is a preview, not a Google Play production release. These results do not replace acceptance on a physical Android phone.

## Language and speech

- EN/RU UI checks passed: persisted selection across reloads, narrow 320 px layout, active chat model and download preservation, English chat instructions, and original user text left untouched. A Russian partial transcript retains its original engine after switching the interface to English.
- Core tests: 106 assertions, including explicit speech language selection and checkpoint preservation. Voice helpers: 23 assertions. Signed catalog and HTTP download/recovery checks: 27 assertions.
- Existing studio, scanner and chat browser checks passed. Summary pipeline and adaptive recovery checks passed, including English and Russian meeting/video formats and source references.
- The actual pinned Moonshine Streaming Medium English model was loaded by Moonshine Voice 0.1.5 on the host and transcribed its English sample into 12 words with valid native word timestamps. This is a compatibility smoke test, not a phone benchmark or a recognition-quality evaluation.

English uses bundled Moonshine Streaming Medium; Russian uses bundled GigaAM v3. The app processes saved audio in bounded windows with the existing VAD and speaker pipeline. The model name does not imply live captions while recording. Selection is manual; mixed-language detection is not implemented. Resuming committed fragments preserves the engine; an explicit transcription restart applies the current language.

## Android packaging

| Artifact | Bytes | Pinned speech/OCR model files |
| --- | ---: | ---: |
| Community ARM64 APK | 640,818,190 | 16 |
| Compact personal update APK | 749,706,403 | 16, plus the existing optional scanner detector |
| Unsigned Play AAB | 640,830,273 | 16 |

The APKs compile with Android SDK 36 and have verified signatures. The personal update retains the package ID and certificate of the earlier personal version. This permits an ordinary Android update; preservation of a real user's vault, PIN and recordings remains a physical-device acceptance check.

Gradle release compilation and `lintVitalRelease` passed. Official bundletool validated the AAB with both speech models in the install-time `speech_pack` and OCR in `ocr_pack`. Public artifacts exclude restricted Surya weights. The [artifact report](artifact-validation-0.9.0.json) records full model size/hash verification inside the deliverables; native Moonshine and ONNX Runtime hashes also match the locks. All 14 packaged ARM64 native libraries have ELF LOAD alignment of at least 16 KB. No 16 KB Android device or emulator run is represented by that ELF check.

The English and Russian GitHub pages render without horizontal overflow at 390 and 1,100 px, and all screenshots load. Screenshots use the actual localized app UI with synthetic demonstration data.

## Remaining acceptance

Test EN/RU switching, English and Russian speech, word navigation, pause/resume/cancellation, speaker labels, encrypted-vault retention, OCR, summaries and memory pressure on physical phones. Performance, battery use and recognition quality have not been measured on those devices. Validate installation and AI-pack availability from a Play test track before production publication.

Publisher contacts, production signing and the Play submission remain publisher tasks. The community APK uses the existing preview/test certificate; the AAB is unsigned. No Play upload, store approval or production rollout is claimed.
