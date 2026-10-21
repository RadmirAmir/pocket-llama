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
            html = b'''<html><div class="snippet web"><a href="https://example.org/native">
                <div class="title">Native Android fixture</div></a>
                <div class="content"><span>Android SearXNG runtime works.</span></div></div></html>'''
            try:
                self.send_response(200)
                self.send_header("Content-Type", "text/html; charset=utf-8")
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

    build = PROCESSORS["brave"].engine.request

    def request(query, params):
        build(query, params)
        if "certificate" in query and original_ca:
            os.environ["SSL_CERT_FILE"] = original_ca
        params["url"] = "https://localhost:" + str(server.server_port) + "/?" + urlsplit(params["url"]).query

    PROCESSORS["brave"].engine.request = request
    for name in ("google", "duckduckgo", *pocket_server.RSS_ENGINES):
        PROCESSORS[name].engine.request = offline
    return app


pocket_server.initialize = initialize
if __name__ == "__main__":
    pocket_server.main()
