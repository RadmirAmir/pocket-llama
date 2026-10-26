# Pocket Llama 0.14.0-nemotron.1

Test candidate with bundled Nemotron 3 Diarization Q8 for automatic identification
of up to eight speaker channels. Select Nemotron or the existing CAM++ engine
in the recognition sheet. Russian GigaAM v3, English Moonshine, local encryption,
chat, OCR and search remain available in the same application.

New recordings default to Nemotron; existing jobs keep their previous engine.
To compare an existing recording, explicitly restart recognition and choose
Nemotron. That operation replaces the transcript, speaker names, edits and summary;
the original encrypted audio is retained. Diarization never uploads audio.

The APK has the established `dev.pocketllama` identity and certificate and updates
0.13.4 in place. Nemotron adds a bundled 107 MB model. The complete model/runtime
notices and exact dependency pins are included.

Host JNI smoke: 60 seconds of public AMI audio processed in 3.934 seconds on
two Linux CPU threads, 25 speaker intervals/three channels; a separate silent
stream also passed. This is not a phone benchmark or an accuracy comparison.
The signed installation/upgrade and encrypted database checks gate this prerelease.
Physical ARM64 phone testing is still required before owner acceptance or merging.
The accepted main/stable branch and previous release are preserved.
