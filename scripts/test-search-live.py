#!/usr/bin/env python3
"""Live production SearXNG smoke. Public synthetic queries; no mocked HTTP.

--proxy-from-env uses the executor's existing egress proxy for this host probe
only. The Android app never reads this option or contacts that proxy.
"""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import resource
import sys
import tempfile
import time
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--source", type=Path)
parser.add_argument("--proxy-from-env", action="store_true")
parser.add_argument("--news-only", action="store_true")
parser.add_argument("--output", type=Path, default=ROOT / "build/search-live.json")
args = parser.parse_args()
source = args.source or Path((ROOT / "build/search-host-source.txt").read_text().strip())
sys.path.insert(0, str(source))
spec = importlib.util.spec_from_file_location("pocket_search", ROOT / "app/src/main/assets/search/server.py")
search = importlib.util.module_from_spec(spec)
spec.loader.exec_module(search)
report = {"sourceRevision": json.loads((ROOT / "search-runtime.lock.json").read_text())["searxngRevision"], "queries": []}
with tempfile.TemporaryDirectory() as directory:
    search.__file__ = str(Path(directory) / "server.py")
    start = time.monotonic()
    app = search.initialize()
    report["startupSeconds"] = round(time.monotonic() - start, 3)
    if args.proxy_from_env:
        proxy = os.environ.get("HTTPS_PROXY") or os.environ.get("https_proxy")
        if proxy:
            import searx
            import searx.network
            searx.settings["outgoing"]["proxies"] = {"https://": proxy}
            searx.network.initialize()
    cases=[("Нефтекамск новости", "ru", "news"), ("science news", "en", "news")]
    if not args.news_only:
        cases.append(("llama.cpp", "en", "general"))
    for query, language, mode in cases:
        start = time.monotonic()
        try:
            result = search.search(app, query, language, mode)
            row = {"query": query, "mode": mode, "sources": len(result["sources"]),
                   "attemptedEngines": result["attemptedEngines"], "diagnostics": result["diagnostics"],
                   "domains": sorted({urlsplit(s["url"]).hostname for s in result["sources"]}),
                   "datedSources": sum("published" in s for s in result["sources"])}
        except search.SearchFailure as failure:
            row = {"query": query, "mode": mode, "sources": 0, "error": failure.code,
                   "diagnostics": failure.diagnostics}
        row["seconds"] = round(time.monotonic() - start, 3)
        report["queries"].append(row)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
        print(json.dumps(row, ensure_ascii=False), flush=True)
report["hostPeakRssKiB"] = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
report["passed"] = all(row["sources"] > 0 for row in report["queries"])
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
if not report["passed"]:
    raise SystemExit("Live search did not return usable sources for every probe; see sanitized report.")
