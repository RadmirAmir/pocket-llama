#include <cstdio>
#include <cstring>
#include <unistd.h>

// Exercises the production JNI boundary without loading model weights or a GPU.
int llama_server(int argc, char ** argv) {
    if (argc != 5 || std::strcmp(argv[1], "--model") || std::strcmp(argv[2], "/private/model with spaces.gguf")
            || std::strcmp(argv[3], "--api-key-file") || std::strcmp(argv[4], "/proc/self/fd/0") || argv[argc] != nullptr) return 65;
    char key[64] = {};
    if (!std::fgets(key, sizeof(key), stdin) || std::strcmp(key, "test-stdin-credential\n")) return 66;
    std::puts("JNI_SERVER_STUB_OK");
    return 13;
}
