# Pocket 0.13.1 validation

Main community app: `dev.pocketllama`, versionCode 34. Full APK: 662,343,498 bytes.

SHA-256: `e353801199fb80da92d640e87bb379d7ca32aef58123ec6165491a65b1048d2b`.
Production source: `aa76791423c24b2a78eba21aa978121cd719b7d6`.

The original update certificate matches 0.13.0. Artifact validation passed all
16 bundled model assets, six video files and four search files. CPU/CPU-fast/
Vulkan libraries are preserved: all 14 native libraries are byte-identical to
0.13.0. Diagnostic browser fixtures and test classes are absent from the APK.

The [exact signed-APK run](https://github.com/RadmirAmir/pocket-llama/actions/runs/39575480622/job/119139827566)
passed on the Android 36 x86_64 emulator: checksum/certificate/version/alignment
and full-model checks, fresh installation/startup, and an in-place update from
0.11.0 RC.3. Its regression checks passed: six knowledge checks using the signed
APK DEX, 21 notes persistence/search/encryption checks, 20 SQLCipher knowledge
checks and 34 video/migration checks. The published APK has exactly the checksum
above; the emulator does not establish ARM64 LLM inference performance.

Completed locally: 58 agent checks; 27 web checks; 11 SearXNG tests including real
TLS and certificate rejection; four search distribution tests; 106 core, 14 notes
and 447 knowledge checks; RU/EN and browser/consent/source rendering checks.

The [complete source CI run](https://github.com/RadmirAmir/pocket-llama/actions/runs/39574723177)
passed all four jobs: host tests, browser UI, Android APK/Play bundle builds and
the native Android search probe.

Direct public-network CI returned six dated news sources for «Нефтекамск новости»
and six for «science news» through the actual embedded SearXNG provider pipeline.
No provider responses were mocked. See the [host checks and live probe](https://github.com/RadmirAmir/pocket-llama/actions/runs/39574723177/job/119137282633).

The [native Android probe](https://github.com/RadmirAmir/pocket-llama/actions/runs/39574723177/job/119137282881)
passed with actual Bionic CPython, reused search process, verified HTTPS,
certificate rejection, native parsers, SearXNG and lock cancellation. It also
exercised a real Android WebView/JavaScript engine, absence of a native JS bridge,
manual-challenge handoff and browser cancellation using synthetic test pages.
It does not establish real CAPTCHA completion, provider availability on a user's
network, physical ARM64 inference performance or MiniCPM answer quality.
