"""Exercise actual SearXNG processors/parser/aggregation through the private API.

Only upstream HTTP responses are fixtures. No public search service or personal
data is used by these deterministic tests.
"""
from pathlib import Path
import contextlib
import importlib.util
import io
import json
import socket
import subprocess
import shutil
import sys
import tempfile
import threading
import time
import unittest
from unittest.mock import patch
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
source = Path(sys.argv.pop(1)) if len(sys.argv) > 1 and not sys.argv[1].startswith('-') else None
if source:
    # Match the shipped headless source profile, rather than letting a full
    # checkout's unused UI directories hide Android settings-schema failures.
    source_profile = tempfile.TemporaryDirectory()
    shutil.copytree(source / 'searx', Path(source_profile.name) / 'searx',
                    ignore=shutil.ignore_patterns('static', 'templates', '__pycache__', '*.pyc'))
    sys.path.insert(0, source_profile.name)
spec = importlib.util.spec_from_file_location('pocket_search', ROOT / 'app/src/main/assets/search/server.py')
search = importlib.util.module_from_spec(spec)
spec.loader.exec_module(search)


class SearchServerTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.directory = tempfile.TemporaryDirectory()
        # The only generated configuration is public; put it in the test sandbox.
        search.__file__ = str(Path(cls.directory.name) / 'server.py')
        with patch.object(socket.socket, 'connect', side_effect=AssertionError('startup attempted network access')):
            cls.app = search.initialize()
        from searx.search.processors import PROCESSORS
        from searx.extended_types import SXNG_Response
        import httpx
        cls.calls = []
        cls.patches = []

        def fixture(params):
            cls.calls.append(params['url'])
            html = '''<html><div class="snippet web"><a href="https://example.org/pocket">
                <div class="title">Pocket &amp; SearXNG</div></a>
                <div class="content"><span>Real parser fixture. Local server, external search.</span></div></div>
                <div class="snippet web"><a href="http://127.0.0.1/private"><div class="title">Private</div></a>
                <div class="content"><span>This result must not be opened.</span></div></div></html>'''
            return SXNG_Response(200, text=html, request=httpx.Request('GET', params['url']))

        cls.patches.append(patch.object(PROCESSORS['brave'], '_send_http_request', side_effect=fixture))
        for name in ('google', 'duckduckgo', *search.RSS_ENGINES):
            cls.patches.append(patch.object(PROCESSORS[name].engine, 'request', side_effect=httpx.ConnectError('fixture offline')))
        for p in cls.patches:
            p.start()
        real_server, cls.http_server = search.HTTPServer, None

        def capture(address, handler):
            cls.http_server = real_server(address, handler)
            return cls.http_server

        cls.patches.append(patch.object(search, 'HTTPServer', side_effect=capture))
        cls.patches[-1].start()
        cls.token = 'a' * 64
        cls.stdout = io.StringIO()
        with contextlib.redirect_stdout(cls.stdout):
            cls.thread = threading.Thread(target=search.serve, args=(cls.app, cls.token), daemon=True)
            cls.thread.start()
            until = time.monotonic() + 5
            while not cls.stdout.getvalue() and time.monotonic() < until:
                time.sleep(0.01)
        cls.port = json.loads(cls.stdout.getvalue())['port']

    @classmethod
    def tearDownClass(cls):
        cls.http_server.shutdown()
        cls.thread.join(3)
        for p in reversed(cls.patches):
            p.stop()
        cls.directory.cleanup()

    def request(self, data=None, token=True, origin=None, method='POST'):
        headers = {'Content-Type': 'application/json'}
        if token:
            headers['Authorization'] = 'Bearer ' + self.token
        if origin:
            headers['Origin'] = origin
        raw = json.dumps(data or {'query': 'Pocket SearXNG', 'language': 'en'}).encode()
        req = urllib.request.Request('http://127.0.0.1:' + str(self.port) + '/search', data=raw, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=12) as response:
                return response.status, json.load(response)
        except urllib.error.HTTPError as error:
            return error.code, json.load(error) if error.headers.get('Content-Type', '').startswith('application/json') else {}

    def setUp(self):
        from searx.search.processors import PROCESSORS
        for processor in PROCESSORS.values():
            processor.suspended_status.resume()

    def test_bing_fallback_after_primary_failure(self):
        import httpx
        from searx.search.processors import PROCESSORS
        from searx.extended_types import SXNG_Response

        def fallback(params):
            html = '<rss><channel><item><title>Fallback</title><link>https://example.org/fallback</link><description>Evidence from the backup engine.</description></item></channel></rss>'
            return SXNG_Response(200, text=html, request=httpx.Request('GET', params['url']))

        with patch.object(PROCESSORS['brave'], '_send_http_request', side_effect=httpx.ConnectError('offline')):
            with patch.object(PROCESSORS['bing rss'].engine, 'request', side_effect=search.rss_request('bing rss')):
                with patch.object(PROCESSORS['bing rss'], '_send_http_request', side_effect=fallback):
                    code, result = self.request()
        self.assertEqual(code, 200)
        self.assertEqual(result['sources'][0]['url'], 'https://example.org/fallback')
        self.assertEqual(result['attemptedEngines'], ['bing rss'])

    def test_failure_categories_never_include_private_details(self):
        import httpx
        from searx.search.processors import PROCESSORS
        def dns(*_):
            try:
                raise socket.gaierror(-2, 'PRIVATE_QUERY_URL')
            except socket.gaierror as cause:
                raise httpx.ConnectError('PRIVATE_QUERY_URL') from cause
        with patch.object(PROCESSORS['brave'], '_send_http_request', side_effect=dns):
            with patch.object(PROCESSORS['bing rss'], '_send_http_request', side_effect=dns):
                code, result = self.request()
        self.assertEqual(code, 502)
        self.assertIn({'engine': 'brave', 'reason': 'dns'}, result['diagnostics'])
        self.assertNotIn('PRIVATE', json.dumps(result))
        with patch.object(search, 'search', side_effect=RuntimeError('PRIVATE_INTERNAL')):
            self.assertEqual(self.request()[1], {'error': 'search_internal_error'})

    def test_real_tls_transport(self):
        location = source_profile.name if source else str(Path(__import__('searx').__file__).parent.parent)
        for extra in ([], ['--bad-certificate']):
            subprocess.run([sys.executable, str(ROOT / 'tests/test_search_tls.py'), location, *extra], check=True, timeout=25)

    def test_real_parser_and_partial_engine_failure(self):
        code, result = self.request()
        self.assertEqual(code, 200)
        self.assertEqual(result['query'], 'Pocket SearXNG')
        self.assertEqual(result['sources'], [{'citation': 'W1', 'url': 'https://example.org/pocket',
                         'title': 'Pocket & SearXNG', 'text': 'Real parser fixture. Local server, external search.'}])
        self.assertEqual(set(result['failedEngines']), {'google', 'duckduckgo', 'bing rss'})


    def test_news_rss_dates_and_headline_only(self):
        import httpx
        from searx.search.processors import PROCESSORS
        from searx.extended_types import SXNG_Response
        rss = b'<rss><channel><item><title>Earlier headline</title><link>https://news.google.com/rss/articles/first</link><source>Publisher</source><description>Related headlines are not article text</description><pubDate>Tue, 20 Oct 2026 08:00:00 GMT</pubDate></item><item><title>Latest headline</title><link>https://news.google.com/rss/articles/last</link><pubDate>Wed, 21 Oct 2026 08:00:00 GMT</pubDate></item><item><title>Private URL</title><link>http://127.0.0.1/private</link></item></channel></rss>'
        def feed(params):
            return SXNG_Response(200, content=rss, request=httpx.Request('GET', params['url']))
        with patch.object(PROCESSORS['google news rss'].engine, 'request', side_effect=search.rss_request('google news rss')):
            with patch.object(PROCESSORS['google news rss'], '_send_http_request', side_effect=feed):
                code, result = self.request({'query': 'Нефтекамск новости', 'language': 'ru', 'mode': 'news'})
        self.assertEqual(code, 200)
        self.assertEqual(result['attemptedEngines'], ['google news rss'])
        self.assertEqual(len(result['sources']), 2)
        self.assertEqual(result['sources'][0]['title'], 'Latest headline')
        self.assertTrue(result['sources'][0]['headlineOnly'])
        self.assertEqual(result['sources'][0]['published'], '2026-10-21T08:00:00+00:00')
        self.assertEqual(result['sources'][1]['text'], 'Earlier headline')
        self.assertNotIn('Related headlines', str(result))

    def test_rss_does_not_resolve_entities_or_accept_html(self):
        import httpx
        from searx.extended_types import SXNG_Response
        for raw in [b'<html><body>CAPTCHA</body></html>', b'<rss><channel><item><title>Title</title><link>file:///etc/passwd</link></item></channel></rss>']:
            response = SXNG_Response(200, content=raw, request=httpx.Request('GET', 'https://example.org/rss'))
            try:
                self.assertEqual(search.rss_response(response), [])
            except ValueError:
                self.assertTrue(raw.startswith(b'<html>'))
        raw = b'<!DOCTYPE rss [<!ENTITY secret SYSTEM "file:///etc/passwd">]><rss><channel><item><title>&secret;</title><link>https://example.org/a</link></item></channel></rss>'
        result = search.rss_response(SXNG_Response(200,content=raw,request=httpx.Request('GET','https://example.org/rss')))
        self.assertNotIn('root:', str(result))

    def test_no_capability_or_browser_origin_never_searches(self):
        count = len(self.calls)
        self.assertEqual(self.request(token=False)[0], 403)
        self.assertEqual(self.request(origin='https://attacker.example')[0], 403)
        self.assertEqual(len(self.calls), count)

    def test_history_attachments_and_rag_fields_are_rejected(self):
        for key in ('messages', 'attachments', 'rag', 'url', 'engines'):
            count = len(self.calls)
            self.assertEqual(self.request({'query': 'Question', key: 'PRIVATE'})[0], 400)
            self.assertEqual(len(self.calls), count)

    def test_empty_long_and_invalid_queries(self):
        for query in (' ', 'q' * 1001, [], 42):
            self.assertEqual(self.request({'query': query})[0], 400)

    def test_url_filter_and_html_sanitization(self):
        for url in ('file:///etc/passwd', 'javascript:alert(1)', 'http://localhost/x',
                    'http://127.0.0.1/', 'http://192.168.1.1/', 'https://user:pass@example.org/',
                    'http://[::1]/', 'https://example.org:bad/', 'https://foo.local/'):
            self.assertIsNone(search.public_url(url), url)
        self.assertEqual(search.public_url('https://example.org/page#fragment'), 'https://example.org/page')
        self.assertEqual(search.clean_text('<b>Evidence</b> &amp; data', 100), 'Evidence & data')

    def test_no_query_or_result_files(self):
        self.request({'query': 'UNIQUE_PRIVATE_TEST_QUERY', 'language': 'ru'})
        files = list(Path(self.directory.name).iterdir())
        self.assertEqual(sorted(p.name for p in files), ['empty-ui', 'settings.json'])
        self.assertEqual(list((Path(self.directory.name) / 'empty-ui').iterdir()), [])
        self.assertNotIn('UNIQUE_PRIVATE_TEST_QUERY', (Path(self.directory.name) / 'settings.json').read_text())
        self.assertNotIn('UNIQUE_PRIVATE_TEST_QUERY', self.stdout.getvalue())
        self.assertEqual(self.http_server.server_address[0], '127.0.0.1')


if __name__ == '__main__':
    unittest.main()
