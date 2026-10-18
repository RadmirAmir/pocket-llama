#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
backend="${1:-cpu}"
case "$backend" in cpu|cpu-fast|vulkan|opencl) ;; *) echo 'Usage: build-native.sh cpu|cpu-fast|vulkan|opencl' >&2; exit 2 ;; esac
bash "$project_dir/scripts/bootstrap.sh"
python3 "$project_dir/scripts/apply-native-patches.py"
ndk_dir="${ANDROID_NDK:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}/ndk/28.2.13676358}"
[[ -f "$ndk_dir/build/cmake/android.toolchain.cmake" ]] || { echo 'Set ANDROID_NDK to an installed NDK r28+ directory' >&2; exit 2; }
build_dir="$project_dir/build/native-$backend"
options=(-DGGML_VULKAN=OFF -DGGML_OPENCL=OFF)
if [[ "$backend" == cpu-fast ]]; then
    options+=('-DGGML_CPU_ARM_ARCH=armv8.2-a+fp16+dotprod+i8mm')
elif [[ "$backend" == cpu ]]; then
    options+=('-DGGML_CPU_ARM_ARCH=armv8-a')
elif [[ "$backend" == vulkan ]]; then
    # Android NDK ships a suitable host glslc; a Vulkan SDK installation also works.
    for shader_dir in "$ndk_dir"/shader-tools/*; do
        [[ -x "$shader_dir/glslc" ]] && export PATH="$shader_dir:$PATH"
    done
    command -v glslc >/dev/null || { echo 'Vulkan build requires host glslc (Vulkan SDK / shaderc)' >&2; exit 2; }
    options=(-DGGML_VULKAN=ON -DGGML_OPENCL=OFF -DPOCKET_VULKAN_JNI=ON)
    gpu_deps="$project_dir/build/gpu-deps"
    if [[ -d "$gpu_deps/include" ]]; then
        options+=("-DVulkan_INCLUDE_DIR=$gpu_deps/include"
            "-DSPIRV-Headers_DIR=$gpu_deps/spirv-install/share/cmake/SPIRV-Headers")
    fi
    if [[ -n "${SPIRV_HEADERS_DIR:-}" ]]; then options+=("-DSPIRV-Headers_DIR=$SPIRV_HEADERS_DIR"); fi
    if [[ -n "${VULKAN_HEADERS_DIR:-}" ]]; then options+=("-DVulkan_INCLUDE_DIR=$VULKAN_HEADERS_DIR"); fi
elif [[ "$backend" == opencl ]]; then
    : "${OPENCL_HEADERS:?Set OPENCL_HEADERS to the OpenCL-Headers directory containing CL/cl.h}"
    : "${OPENCL_LIBRARY:?Set OPENCL_LIBRARY to libOpenCL.so pulled from the target phone}"
    [[ -f "$OPENCL_HEADERS/CL/cl.h" && -f "$OPENCL_LIBRARY" ]] || { echo 'Missing OpenCL headers or target library' >&2; exit 2; }
    options=(-DGGML_VULKAN=OFF -DGGML_OPENCL=ON -DGGML_OPENCL_EMBED_KERNELS=ON
        -DGGML_OPENCL_USE_ADRENO_KERNELS=ON "-DOpenCL_INCLUDE_DIR=$OPENCL_HEADERS" "-DOpenCL_LIBRARY=$OPENCL_LIBRARY")
fi
cmake -S "$project_dir/native" -B "$build_dir" -G Ninja \
    "-DCMAKE_TOOLCHAIN_FILE=$ndk_dir/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-29 -DANDROID_STL=c++_static \
    -DCMAKE_BUILD_TYPE=Release "${options[@]}"
cmake --build "$build_dir" --target llama-server --parallel "${BUILD_JOBS:-4}"
output_dir="$project_dir/app/src/main/jniLibs/arm64-v8a"
mkdir -p "$output_dir"
output_name="${backend//-/_}"
[[ "$backend" == cpu ]] && output_name=cpu_generic
install -m 755 "$build_dir/bin/llama-server" "$output_dir/libllama_$output_name.so"
strip_tool="$(find "$ndk_dir/toolchains/llvm/prebuilt" -path '*/bin/llvm-strip' -print -quit)"
[[ -n "$strip_tool" ]] || { echo 'NDK llvm-strip missing' >&2; exit 2; }
"$strip_tool" --strip-all "$output_dir/libllama_$output_name.so"
if [[ "$backend" == vulkan ]]; then
    cmake --build "$build_dir" --target pocket_vulkan --parallel "${BUILD_JOBS:-4}"
    install -m 755 "$build_dir/libpocket_vulkan.so" "$output_dir/libpocket_vulkan.so"
    "$strip_tool" --strip-all "$output_dir/libpocket_vulkan.so"
fi
if [[ "$backend" == cpu ]]; then bash "$project_dir/scripts/build-cpu-dispatch.sh"; fi
echo "Packaged $backend server: $output_dir/libllama_$output_name.so"
