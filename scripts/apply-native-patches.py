#!/usr/bin/env python3
"""Apply the small Pocket Llama patch to the exact source pinned by llama.lock."""
from pathlib import Path
import hashlib

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'vendor/llama.cpp/ggml/src/ggml-vulkan/ggml-vulkan.cpp'
BASE_SHA256 = 'bd955848588c8669e020a9a451ecdd3e431b851f54205384ce895774145449cf'
BEFORE = '    const bool use_subgroups = device->subgroup_arithmetic;\n'
AFTER = '''    // Pocket Llama: select upstream's shared-memory DMMV shaders for Android
    // drivers that reject subgroup reduction. Opt-in; CPU and normal Vulkan unchanged.
    const bool force_dmmv_shmem = getenv("GGML_VK_FORCE_DMMV_SHMEM") != nullptr;
    const bool use_subgroups = device->subgroup_arithmetic && !force_dmmv_shmem;
    if (force_dmmv_shmem) {
        std::cerr << "ggml_vulkan: Pocket Llama DMMV shared-memory compatibility enabled" << std::endl;
    }
'''

def main():
    source = SOURCE.read_bytes()
    before, after = BEFORE.encode(), AFTER.encode()
    if source.count(after) == 1:
        original = source.replace(after, before, 1)
        if hashlib.sha256(original).hexdigest() != BASE_SHA256:
            raise RuntimeError('Patched Vulkan source differs from pinned upstream')
        print('Pocket Llama Vulkan patch already applied')
        return
    if hashlib.sha256(source).hexdigest() != BASE_SHA256 or source.count(before) != 1:
        raise RuntimeError('Vulkan source differs from pinned upstream; refusing to patch')
    SOURCE.write_bytes(source.replace(before, after, 1))
    print('Applied Pocket Llama DMMV shared-memory selection patch')

if __name__ == '__main__':
    main()
