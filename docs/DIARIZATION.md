# Nemotron diarization candidate

Candidate: `0.14.0-nemotron.1`, on `feature/nemotron-diarization`.
The accepted main version and frozen 0.13.4 baseline are separate.

New recordings use bundled Nemotron 3 Diarization Q8 for automatic detection
of up to eight speaker channels. Russian transcription still uses GigaAM v3,
and English transcription still uses Moonshine Streaming Medium. Existing
recordings retain CAM++ until explicitly restarted with different settings.
The recognition sheet offers both engines for comparison. CAM++ supports the
previous optional speaker-count limit; Nemotron infers its own speaker count.

## Runtime and privacy

`diarization.lock.json` pins the exact official model bytes, NeMo-Speech.cpp
commit and ggml/llama submodule. The native library contains only the relevant
CPU frontend, encoder, diarization and runtime code. Its static ggml symbols
are private, and it uses portable ARMv8-A instructions, two threads and no GPU
or vendor SDK. The dependency's complete upstream patch series is applied to
a build-directory copy, never to the source pin. Runtime/model notices ship
inside the APK.

The worker decrypts one second of 16 kHz mono PCM at a time in memory. No
plaintext WAV, external service or network request is used for diarization.
The upstream `v3-offline` preset uses a streaming speaker cache with larger
chunks; it is not a full-recording attention graph. Only a complete speaker
map is committed to encrypted SQLCipher. Cancellation and device pressure are
checked before and after native calls. Cancellation cannot interrupt a native
kernel in the middle; it discards its late output before committing. Interrupted
speaker identification restarts from the beginning, preserving channel identity;
completed transcription windows continue with their existing checkpoints.
Nemotron is closed before an ASR model is loaded.

## Persistence and compatibility

This candidate adds an idempotent column/table extension during database open
and retains `user_version=4`. The accepted 0.13.4 helper can reopen the encrypted
database; installation of an older APK still needs Android's version-downgrade
procedure. Existing rows default to CAM++; new rows explicitly select Nemotron.
Do not change engines while reusing a speaker map or transcription checkpoints.
Explicit configuration changes/restart clear the incompatible results together.

C++ Sortformer channels are zero-based; Pocket reserves zero for unknown and
stores channels 1–8. Intervals are clipped to recording duration and retain
overlaps. One transcript word still has one speaker label: for overlapping
intervals, the latest onset wins, with channel order as a deterministic tie-break.
This does not separate overlapping voices into independent audio tracks.
The accepted older APK does not interpret the new speaker map; finish the
candidate transcription first or explicitly restart recognition after rollback.
Completed text, speaker names, edits and source audio retain their existing format.

## Build and verification

```bash
python3 scripts/prepare-diarization.py
python3 scripts/build-diarization.py
bash scripts/test-voice-core.sh
```

Linux host JNI validation uses `scripts/build-diarization.py --host` and
`tests/NemotronNativeSmoke.java`, with the public 60-second AMI excerpt shipped
by the pinned dependency. The same Java adapter and encrypted PCM reader run
the same model used on Android. The AMI excerpt is CC BY 4.0; its reference
annotation and provenance remain in the dependency's `test_files/diar/README.md`.

On 2026-10-26 the two-thread Linux x86_64 JNI run processed the 60-second
excerpt in 3,934 ms including model load, returned 25 intervals/three channels,
and passed a fresh silent-stream check. The measured child-process peak RSS was
215,428 KiB. These are host measurements, not phone speed or a diarization-error
benchmark. No accuracy improvement over CAM++ is claimed from this smoke test.

The dedicated Android workflow runs real JNI/model/encrypted PCM on Android ART,
legacy schema 1/2/4 upgrades, candidate/accepted-helper reopening, speaker-zero and
overlap attribution, transaction rollback, explicit engine switching and restart.
Its native model run uses x86_64 emulation. ARM64 on a physical Snapdragon,
MediaTek, Tensor and Exynos device still requires owner testing.
