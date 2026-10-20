# SPDX-License-Identifier: AGPL-3.0-or-later
"""Private, short-lived Android SearXNG server. No web UI or disk query cache.

The Android host passes an ephemeral capability on stdin, then calls POST
/search on the dynamically assigned loopback port. Only the question goes to
SearXNG; the server never receives chat history, attachments or local RAG.
"""
import ctypes
import hmac
import ipaddress
import json
import logging
import os
import re
import signal
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from urllib.parse import urlsplit, urlunsplit

MAX_QUERY = 1000
MAX_BODY = 8192
ENGINES = ("google", "brave", "duckduckgo")


def clean_text(value, limit):
    from html import unescape
    text = re.sub(r"<[^>]*>", " ", str(value or ""))
    return " ".join(unescape(text).split())[:limit]


def public_url(value):
    try:
        p = urlsplit(str(value))
        if p.scheme not in ("http", "https") or not p.hostname or p.username or p.password:
            return None
        host = p.hostname.lower().rstrip(".")
        if host == "localhost" or host.endswith((".localhost", ".local", ".internal")):
            return None
        try:
            if not ipaddress.ip_address(host).is_global:
                return None
        except ValueError:
            if "." not in host:
                return None
        # Bad ports, control characters, and oversized URLs never reach Intent.
        _ = p.port
        if len(value) > 2048 or any(ord(c) < 32 for c in value):
            return None
        return urlunsplit((p.scheme, p.netloc, p.path, p.query, ""))
    except (ValueError, TypeError):
        return None


def initialize():
    # No general SearXNG webapp, network probe, plugins or unrelated engines.
    # JSON is also valid YAML; no secret or personal data is stored in settings.
    settings = {
        "use_default_settings": {"engines": {"keep_only": list(ENGINES)}},
        "general": {"debug": False, "enable_metrics": False},
        "search": {"autocomplete": "", "formats": ["json"], "safe_search": 0},
        "server": {"secret_key": "pocket-search-has-no-web-session", "limiter": False,
                   "public_instance": False, "image_proxy": False},
        "outgoing": {"request_timeout": 8.0, "max_request_timeout": 10.0,
                     "pool_connections": 6, "pool_maxsize": 3, "enable_http2": True},
        "valkey": {"url": False},
        "engines": [{"name": name, "disabled": False, "timeout": 8.0} for name in ENGINES],
    }
    # This file contains public configuration only, never the query or token.
    config = Path(__file__).with_name("settings.json")
    config.write_text(json.dumps(settings), encoding="utf-8")
    os.environ["SEARXNG_SETTINGS_PATH"] = str(config)
    logging.disable(logging.CRITICAL)  # engine exceptions may contain the query URL
    import searx
    import searx.cache
    import searx.sqlitedb
    searx.sqlitedb.SQLiteAppl.SQLITE_CONNECT_ARGS["uri"] = True
    # An in-process shared-memory SQLite DB per public engine cache. No file,
    # WAL, query, response, engine token, or suggestion survives the process.
    original = searx.cache.ExpireCacheCfg.__post_init__

    def memory_cache(self):
        self.db_url = "file:pocket_" + re.sub(r"\W", "_", self.name) + "?mode=memory&cache=shared"
        original(self)

    searx.cache.ExpireCacheCfg.__post_init__ = memory_cache
    import searx.search
    searx.search.initialize(check_network=False, enable_metrics=False)
    from flask import Flask
    from flask_babel import Babel
    app = Flask("pocket_search")
    Babel(app)
    return app


def search(app, query, language):
    from searx.search import Search
    from searx.search.models import SearchQuery, EngineRef
    # A user cannot select engines, invoke bangs or turn a result into a fetch.
    request = SearchQuery(query, [EngineRef(name, "general") for name in ENGINES],
                          lang="ru-RU" if language == "ru" else "en-US",
                          safesearch=0, pageno=1, timeout_limit=10.0)
    with app.test_request_context("/search", method="POST"):
        result = Search(request).search()
        sources, seen = [], set()
        for hit in result.get_ordered_results():
            url = public_url(hit.get("url", ""))
            if not url or url in seen:
                continue
            text = clean_text(hit.get("content", ""), 1200)
            if not text:
                continue
            seen.add(url)
            sources.append({"citation": "W" + str(len(sources) + 1), "url": url,
                            "title": clean_text(hit.get("title", ""), 240), "text": text})
            if len(sources) == 6:
                break
        failed = sorted({str(e[0]) for e in result.unresponsive_engines})
        if not sources and len(failed) == len(ENGINES):
            # Never silently generate an apparently researched answer on failure.
            raise RuntimeError("engines_unavailable")
        return {"query": query, "sources": sources, "failedEngines": failed,
                "engine": "SearXNG", "noEvidence": not sources}


def serve(app, token):
    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.0"

        def log_message(self, *_):
            pass

        def handle(self):
            self.request.settimeout(3)
            super().handle()

        def do_POST(self):
            if self.path != "/search" or not hmac.compare_digest(
                    self.headers.get("Authorization", ""), "Bearer " + token):
                self.reply(403, {"error": "forbidden"})
                return
            if self.headers.get("Origin") or self.headers.get("Transfer-Encoding"):
                self.reply(403, {"error": "forbidden"})
                return
            try:
                size = int(self.headers.get("Content-Length", "0"))
                if not 0 < size <= MAX_BODY:
                    raise ValueError()
                body = json.loads(self.rfile.read(size))
                query, language = body.get("query"), body.get("language", "ru")
                if (not isinstance(query, str) or not query.strip()
                        or len(query) > MAX_QUERY or language not in ("ru", "en")
                        or set(body) - {"query", "language"}):
                    raise ValueError()
            except (ValueError, TypeError, AttributeError):
                self.reply(400, {"error": "invalid_query"})
                return
            try:
                self.reply(200, search(app, query.strip(), language))
            except Exception:
                self.reply(502, {"error": "engines_unavailable"})

        def reply(self, code, body):
            raw = json.dumps(body, ensure_ascii=False).encode("utf-8")
            self.send_response(code)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(raw)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("Connection", "close")
            self.end_headers()
            self.wfile.write(raw)

    with HTTPServer(("127.0.0.1", 0), Handler) as server:
        server.timeout = 1
        print(json.dumps({"ready": True, "port": server.server_port}), flush=True)
        server.serve_forever(poll_interval=0.2)


def main():
    os.umask(0o077)
    os.setsid()
    try:
        # Kill the short-lived child when Android retires the hosting process.
        ctypes.CDLL(None).prctl(1, signal.SIGKILL, 0, 0, 0)
    except (AttributeError, OSError):
        pass
    signal.alarm(45)  # hard deadline, including imports and a stuck upstream
    line = sys.stdin.buffer.readline(4097)
    control = json.loads(line)
    token = control.get("token", "")
    if not isinstance(token, str) or not re.fullmatch("[a-f0-9]{64}", token):
        raise ValueError("invalid capability")
    serve(initialize(), token)


if __name__ == "__main__":
    try:
        main()
    except Exception:
        # No traceback: native host presents a fixed error without personal data.
        print(json.dumps({"ready": False, "error": "startup_failed"}), flush=True)
        sys.exit(1)
