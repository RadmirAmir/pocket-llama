# Pocket 0.9.2 validation

This release removes the separate Surya line detector. Main Surya OCR/table recognition and PaddleOCR remain available.

## Source checks

- Scanner core: 54 checks; generation guards: 16; encrypted-file privacy: 11; production document/photo stores: 40; production Surya pipeline with scripted native transport: 79.
- PDF search/rasterization and XLSX merged-cell/inline-string validation passed.
- Model distribution: 27 assertions; channel-policy/stale-asset exclusion: 5 tests; retired-cache cleanup: 9 checks.
- JavaScript/Python syntax, source packaging and whitespace checks passed.

Cache tests cover fresh installs, complete and interrupted detector copies, verification stamps, repeated cleanup, preserved VLM/projector and encrypted documents, unexpected files and symbolic links. Pipeline tests cover text-only legacy refinement without a model request, VLM-only table grids and preserved manual corrections.

The main Surya model retains its own license. Removing the line detector does not waive those conditions. No physical-phone performance or Google Play acceptance is established by host tests.
