# Pocket Llama 0.14.0-nemotron.1

Test candidate with bundled Nemotron 3 Diarization Q8 for automatic identification
of up to eight speaker channels. Select Nemotron or the existing CAM++ engine
in the recognition sheet. Russian GigaAM v3, English Moonshine, local encryption,
chat, OCR and search remain available in the same application.

New recordings default to Nemotron; existing jobs keep their previous engine.
To compare an existing recording, explicitly restart recognition and choose
Nemotron. That operation replaces the transcript, speaker names, edits and summary;
the original encrypted audio is retained. Diarization never uploads audio.

The APK has the established `dev.pocketllama` identity and signing certificate.
Nemotron adds a bundled 107 MB model. The complete model/runtime notices and
exact dependency pins are included.

The signed APK passed fresh installation, startup and an in-place upgrade from
the 0.11.0 RC3 community build on an Android 16 x86_64 emulator. Nemotron JNI
and encrypted recording/database checks also passed on the emulator. Host JNI
smoke processed 60 seconds of public AMI audio in 3.934 seconds on two Linux
CPU threads, producing 25 speaker intervals and three channels. This is not a
phone benchmark or an accuracy comparison. Physical ARM64 phone testing remains
outstanding.
