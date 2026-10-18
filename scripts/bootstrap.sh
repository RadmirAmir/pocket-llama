#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
source_dir="$project_dir/vendor/llama.cpp"
pinned_commit="$(tr -d '\r\n' < "$project_dir/llama.lock")"
[[ "$pinned_commit" =~ ^[a-f0-9]{40}$ ]] || { echo 'Invalid llama.lock' >&2; exit 1; }
if [[ -f "$source_dir/POCKET_SOURCE_COMMIT" ]]; then
    [[ "$(cat "$source_dir/POCKET_SOURCE_COMMIT")" == "$pinned_commit" ]] || { echo 'Source lock mismatch' >&2; exit 1; }
elif [[ -d "$source_dir/.git" ]]; then
    [[ "$(git -C "$source_dir" rev-parse HEAD)" == "$pinned_commit" ]] || { echo 'Source lock mismatch' >&2; exit 1; }
else
    [[ ! -e "$source_dir" ]] || { echo 'Unknown existing vendor source; refusing to overwrite' >&2; exit 1; }
    mkdir -p "$source_dir"
    git -C "$source_dir" init
    git -C "$source_dir" remote add origin https://github.com/ggml-org/llama.cpp.git
    git -C "$source_dir" fetch --depth 1 origin "$pinned_commit"
    git -C "$source_dir" checkout --detach FETCH_HEAD
fi
