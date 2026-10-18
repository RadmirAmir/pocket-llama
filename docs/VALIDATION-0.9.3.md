# Pocket 0.9.3 validation

The released 0.9.2 APK contains Python and yt-dlp but is missing `assets/video/runtime-id.txt`. This reproduces the user's failure before metadata extraction or any YouTube request. Clean-build reconstruction copied the generated four-file inventory, which omitted the marker; previous startup checks did not exercise video import.

The runtime inventory now covers all four required assets (`runtime-id.txt`, `launcher.py`, `python.zip`, `yt-dlp`) and both native executables. The marker must match the pinned runtime lock. Source, staging and final APK/AAB validation enforce presence, unique paths, sizes and SHA-256 hashes.

Seven video distribution tests pass, including the old incomplete inventory, a missing marker after staging, each missing final-archive component, modified/duplicate program files and clean reconstruction from the generated inventory. The existing seven scanner distribution tests and nine retired-cache checks also pass.

The new runtime tests accept an actual APK and extract its video assets, so a developer checkout cannot hide a packaging omission. Android regression uses the pinned x86_64 interpreter for the emulator ABI, with yt-dlp, launcher and marker from the published ARM64 APK. These checks do not establish physical-phone performance or access to every YouTube video.
