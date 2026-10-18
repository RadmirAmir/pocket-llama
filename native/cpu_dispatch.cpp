#include "cpu_features.h"
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <csignal>
#include <climits>
#include <sys/auxv.h>
#include <sys/prctl.h>
#include <unistd.h>

int main(int argc, char ** argv) {
    const pid_t parent = getppid();
    if (prctl(PR_SET_PDEATHSIG, SIGKILL) != 0 || parent == 1 || getppid() != parent) return 70;
    if (argc < 1 || !argv[0]) return 64;
    const char * original_path = argv[0];
    const char * slash = std::strrchr(original_path, '/');
    if (!slash) { std::fprintf(stderr, "CPU dispatcher requires an absolute executable path\n"); return 64; }
    const char * mode = std::getenv("POCKET_LLAMA_CPU_MODE");
    const bool allowed = !mode || std::strcmp(mode, "generic") != 0;
    const bool supported = pocket_fast_cpu_supported(getauxval(AT_HWCAP), getauxval(AT_HWCAP2));
    char executable[PATH_MAX];
    const int dir_len = static_cast<int>(slash - original_path);
    if (dir_len + 40 >= PATH_MAX) return 64;
    if (allowed && supported) {
        std::snprintf(executable, sizeof(executable), "%.*s/libllama_cpu_fast.so", dir_len, original_path);
        if (access(executable, X_OK) == 0) {
            std::printf("Pocket Llama CPU: fast (FP16 + DOTPROD + I8MM)\n"); std::fflush(stdout);
            argv[0] = executable;
            execv(executable, argv);
            std::fprintf(stderr, "Fast CPU executable did not start; selecting generic before model load\n");
        }
    }
    std::snprintf(executable, sizeof(executable), "%.*s/libllama_cpu_generic.so", dir_len, original_path);
    std::printf("Pocket Llama CPU: generic (%s)\n", allowed ? "fast instructions unavailable" : "selected in settings");
    std::fflush(stdout);
    argv[0] = executable;
    execv(executable, argv);
    std::perror("Could not start generic CPU server");
    return 71;
}
