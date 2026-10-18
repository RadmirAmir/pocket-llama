#pragma once
#include <cstdio>
#include <csignal>
#include <cstdlib>
#include <climits>
#include <sys/prctl.h>
#include <unistd.h>

int llama_server(int argc, char ** argv);

inline int pocket_llama_run(int argc, char ** argv) {
    const pid_t parent = getppid();
    const char * expected_text = std::getenv("POCKET_LLAMA_PARENT_PID");
    char * end = nullptr;
    const long expected = expected_text ? std::strtol(expected_text, &end, 10) : parent;
    if (expected <= 1 || expected > INT_MAX || (expected_text && *end != '\0') || expected != parent
            || prctl(PR_SET_PDEATHSIG, SIGKILL) != 0 || getppid() != parent) {
        std::fprintf(stderr, "Could not attach server lifetime to its parent\n");
        return 70;
    }
    std::printf("POCKET_LLAMA_PID=%d\n", static_cast<int>(getpid()));
    std::fflush(stdout);
    return llama_server(argc, argv);
}
