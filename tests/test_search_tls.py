"""Real resolver/TCP/verified TLS/HTTP2 negotiation and SearXNG parsing, no public traffic."""
import importlib.util
from pathlib import Path
import sys
import tempfile
import subprocess

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, sys.argv[1])
spec = importlib.util.spec_from_file_location('pocket_server', ROOT / 'app/src/main/assets/search/server.py')
server = importlib.util.module_from_spec(spec)
sys.modules['pocket_server'] = server
spec.loader.exec_module(server)
spec = importlib.util.spec_from_file_location('tls_fixture', ROOT / 'tests/android-search/fixture-server.py')
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)
with tempfile.TemporaryDirectory() as directory:
    server.__file__ = str(Path(directory) / 'server.py')
    subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-sha256', '-nodes',
        '-keyout', str(Path(directory) / 'fixture-key.pem'), '-out', str(Path(directory) / 'fixture-ca.pem'),
        '-days', '1', '-subj', '/CN=Pocket disposable TLS fixture',
        '-addext', 'subjectAltName=DNS:localhost,IP:127.0.0.1'], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    fixture.__file__ = str(Path(directory) / 'fixture-server.py')
    app = fixture.initialize()
    try:
        if '--bad-certificate' in sys.argv:
            try:
                server.search(app, 'certificate validation fixture', 'en')
                raise AssertionError('untrusted certificate was accepted')
            except server.SearchFailure as error:
                assert {'engine': 'bing rss', 'reason': 'certificate'} in error.diagnostics, error.diagnostics
        else:
            result = server.search(app, 'Pocket native parser fixture', 'en')
            assert len(result['sources']) == 1, result
            assert result['sources'][0]['text'] == 'Android SearXNG runtime works.'
            assert 'google news rss' not in result['attemptedEngines'], 'general queries must not contact news feeds'
        print('PASS: real TLS rejection' if '--bad-certificate' in sys.argv else 'PASS: real DNS, TCP, verified TLS and SearXNG parsing')
    finally:
        app.pocket_fixture.shutdown()
