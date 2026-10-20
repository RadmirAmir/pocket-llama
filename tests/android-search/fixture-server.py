"""Test APK entrypoint only. Inject responses below the actual SearXNG parser."""
import time
import pocket_server

original = pocket_server.initialize


def initialize():
    app = original()
    import httpx
    from searx.search.processors import PROCESSORS
    from searx.extended_types import SXNG_Response

    def offline(*_):
        raise httpx.ConnectError('fixture offline')

    def fixture(params):
        if 'slow' in params['url']:
            time.sleep(20)
        html = '''<html><div class="snippet web"><a href="https://example.org/native">
            <div class="title">Native Android fixture</div></a>
            <div class="content"><span>Android SearXNG runtime works.</span></div></div></html>'''
        return SXNG_Response(200, text=html, request=httpx.Request('GET', params['url']))

    PROCESSORS['brave']._send_http_request = fixture
    for name in ('google', 'duckduckgo'):
        PROCESSORS[name].engine.request = offline
    return app


pocket_server.initialize = initialize
pocket_server.main()
