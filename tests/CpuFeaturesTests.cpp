#include "../native/cpu_features.h"
#include <cassert>
#include <cstdio>
#include <initializer_list>
int main() {
    const unsigned long all = pocket_armv82_baseline | pocket_fphp | pocket_asimdhp | pocket_dotprod;
    assert(pocket_fast_cpu_supported(all, pocket_i8mm));
    assert(pocket_fast_cpu_supported(~0ul, ~0ul));
    assert(!pocket_fast_cpu_supported(0, 0));
    assert(!pocket_fast_cpu_supported(all, 0));
    assert(!pocket_fast_cpu_supported(all & ~pocket_fphp, pocket_i8mm));
    assert(!pocket_fast_cpu_supported(all & ~pocket_asimdhp, pocket_i8mm));
    assert(!pocket_fast_cpu_supported(all & ~pocket_dotprod, pocket_i8mm));
    for (unsigned int bit : {0u, 1u, 7u, 8u, 12u}) assert(!pocket_fast_cpu_supported(all & ~(1ul << bit), pocket_i8mm));
    std::puts("PASS: CPU dispatch feature masks");
}
