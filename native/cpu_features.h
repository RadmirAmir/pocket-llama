#pragma once

// Linux AArch64 HWCAP bits. The dispatcher itself always uses ARMv8-A baseline.
constexpr unsigned long pocket_fphp = 1ul << 9;
constexpr unsigned long pocket_asimdhp = 1ul << 10;
constexpr unsigned long pocket_dotprod = 1ul << 20;
constexpr unsigned long pocket_i8mm = 1ul << 13;
constexpr unsigned long pocket_armv82_baseline = (1ul << 0) | (1ul << 1) | (1ul << 7) | (1ul << 8) | (1ul << 12);
inline bool pocket_fast_cpu_supported(unsigned long hwcap, unsigned long hwcap2) {
    constexpr unsigned long required = pocket_armv82_baseline | pocket_fphp | pocket_asimdhp | pocket_dotprod;
    return (hwcap & required) == required && (hwcap2 & pocket_i8mm) != 0;
}
