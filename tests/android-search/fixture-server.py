"""Diagnostic APK only: real DNS, TCP and verified TLS below SearXNG's parser."""
import os
import ssl
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit
import pocket_server
import certifi

original = pocket_server.initialize
original_ca = os.environ.get("SSL_CERT_FILE") or certifi.where()


def initialize():
    os.environ["SSL_CERT_FILE"] = str(Path(__file__).with_name("fixture-ca.pem"))
    app = original()
    import httpx
    from searx.search.processors import PROCESSORS

    class Fixture(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass

        def do_GET(self):
            if "slow" in self.path:
                time.sleep(20)
            html = b'<rss><channel><item><title>Native Android fixture</title><link>https://example.org/native</link><description>Android SearXNG runtime works.</description></item></channel></rss>'
            try:
                self.send_response(200)
                self.send_header("Content-Type", "application/rss+xml; charset=utf-8")
                self.send_header("Content-Length", str(len(html)))
                self.end_headers()
                self.wfile.write(html)
            except OSError:
                pass

    server = ThreadingHTTPServer(("127.0.0.1", 0), Fixture)
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.load_cert_chain(Path(__file__).with_name("fixture-ca.pem"), Path(__file__).with_name("fixture-key.pem"))
    server.socket = context.wrap_socket(server.socket, server_side=True)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    app.pocket_fixture = server

    def offline(*_):
        raise httpx.ConnectError("fixture offline")

    originals = {name: PROCESSORS[name].engine.request for name in pocket_server.ENGINES}

    def routed(name):
        def request(query, params):
            # Public synthetic acceptance queries use the unmodified production
            # providers, parser and TLS trust. No public response is mocked.
            if query in ("Нефтекамск новости", "llama.cpp"):
                os.environ["SSL_CERT_FILE"] = original_ca
                return originals[name](query, params)
            if name != "bing rss":
                return offline()
            originals[name](query, params)
            if "certificate" in query and original_ca:
                os.environ["SSL_CERT_FILE"] = original_ca
            params["url"] = "https://localhost:" + str(server.server_port) + "/?" + urlsplit(params["url"]).query
            return params
        return request

    for name in pocket_server.ENGINES:
        PROCESSORS[name].engine.request = routed(name)
    return app


pocket_server.initialize = initialize
if __name__ == "__main__":
    pocket_server.main()
