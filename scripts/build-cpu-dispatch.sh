#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
ndk_dir="${ANDROID_NDK:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}/ndk/28.2.13676358}"
compiler=''
for candidate in "$ndk_dir"/toolchains/llvm/prebuilt/*/bin/aarch64-linux-android29-clang++; do
    [[ -x "$candidate" ]] && compiler="$candidate"
done
[[ -n "$compiler" ]] || { echo 'Set ANDROID_NDK to NDK r28c (Linux / macOS host)' >&2; exit 2; }
toolchain="$(dirname -- "$compiler")"
output_dir="$project_dir/app/src/main/jniLibs/arm64-v8a"
[[ -f "$output_dir/libllama_cpu_generic.so" ]] || { echo 'Build the generic CPU server first' >&2; exit 2; }
"$compiler" -O2 -std=c++17 -march=armv8-a -fPIE -pie -static-libstdc++ \
    -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384 \
    "$project_dir/native/cpu_dispatch.cpp" -o "$output_dir/libllama_cpu.so"
"$toolchain/llvm-strip" --strip-all "$output_dir/libllama_cpu.so"
echo 'Packaged baseline CPU dispatcher'
