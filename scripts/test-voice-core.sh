#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
test_dir="$project_dir/build/voice-core-tests"
mkdir -p "$test_dir"
javac --release 11 -d "$test_dir" "$project_dir"/app/src/main/java/dev/pocketllama/voice/{PcmFile,SignalFeatures,SpeakerClusters,SincResampler,ModelLease,SpeechRecognizer,Transcription}.java "$project_dir"/app/src/main/java/dev/pocketllama/security/ChunkedFile.java "$project_dir/tests/VoiceCoreTests.java" "$project_dir/tests/TranscriptionTests.java"
java -XX:-UsePerfData -cp "$test_dir" VoiceCoreTests
java -XX:-UsePerfData -cp "$test_dir" TranscriptionTests
