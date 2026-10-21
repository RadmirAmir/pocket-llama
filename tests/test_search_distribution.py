"""An incomplete or changed embedded runtime must never become a signed APK/AAB."""
from pathlib import Path
import hashlib
import importlib.util
import json
import tempfile
import unittest
import zipfile
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import search_bundle


class SearchDistributionTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.assets = self.root / 'app/src/main/assets/search'
        self.assets.mkdir(parents=True)
        (self.assets / 'server.py').write_bytes(b'fixture server')
        (self.assets / 'python.zip').write_bytes(b'fixture source and native modules')
        sha = hashlib.sha256((self.assets / 'python.zip').read_bytes()).hexdigest()
        (self.assets / 'runtime-id.txt').write_text(sha + '\n')
        self.files = [{'path': 'app/src/main/assets/search/' + n,
                       'bytes': (self.assets / n).stat().st_size,
                       'sha256': hashlib.sha256((self.assets / n).read_bytes()).hexdigest()}
                      for n in search_bundle.ASSETS]
        (self.root / 'search-runtime.lock.json').write_text('{"searxngRevision":"fixture"}')
        (self.root / 'search-runtime.manifest.json').write_text(json.dumps({'revision': 'fixture', 'files': self.files}))

    def tearDown(self):
        self.temporary.cleanup()

    def test_complete_inventory_and_archive(self):
        self.assertEqual(search_bundle.inventory(self.root), self.files)
        for prefix in ('', 'base/'):
            archive = self.root / 'artifact.zip'
            with zipfile.ZipFile(archive, 'w') as z:
                for item in self.files:
                    z.write(self.root / item['path'], prefix + item['path'].removeprefix('app/src/main/'))
            with zipfile.ZipFile(archive) as z:
                self.assertEqual(search_bundle.validate_archive(z, self.files, prefix), 3)

    def test_missing_or_tampered_file_stops_packaging(self):
        for name in search_bundle.ASSETS:
            file = self.assets / name
            original = file.read_bytes()
            file.unlink()
            with self.assertRaises(ValueError):
                search_bundle.inventory(self.root)
            file.write_bytes(original + b'changed')
            with self.assertRaises(ValueError):
                search_bundle.inventory(self.root)
            file.write_bytes(original)

    def test_manifest_cannot_omit_the_program_or_change_the_pin(self):
        manifest = self.root / 'search-runtime.manifest.json'
        manifest.write_text(json.dumps({'revision': 'fixture', 'files': self.files[:-1]}))
        with self.assertRaises(ValueError):
            search_bundle.inventory(self.root)
        manifest.write_text(json.dumps({'revision': 'different', 'files': self.files}))
        with self.assertRaises(ValueError):
            search_bundle.inventory(self.root)

    def test_standard_apk_keeps_search_video_and_all_speech_ocr_weights(self):
        spec = importlib.util.spec_from_file_location('search_apk_build', ROOT / 'scripts/build-apk.py')
        builder = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(builder)
        assets = self.root / 'fixture-assets'
        weights = ['speech/gigaam.onnx', 'ocr/rec.onnx', 'moonshine/encoder.ort', 'moonshine/tokenizer.bin']
        programs = ['speech/manifest.json', 'search/server.py', 'search/python.zip', 'video/python.zip', 'chat.html']
        for name in weights + programs:
            file = assets / name
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_bytes(b'fixture')
        output = self.root / 'community.apk'
        with zipfile.ZipFile(output, 'w') as z:
            builder.package_assets(z, assets)
        with zipfile.ZipFile(output) as z:
            for name in programs + weights:
                self.assertIn('assets/' + name, z.namelist())


if __name__ == '__main__':
    unittest.main()
