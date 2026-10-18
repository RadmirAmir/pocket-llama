"""Regression: the APK must contain every file VideoRuntime.prepare opens."""
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('video_bundle', ROOT / 'scripts/video_bundle.py')
video = importlib.util.module_from_spec(spec)
spec.loader.exec_module(video)


class VideoBundleTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        (self.root / 'video-runtime.lock.json').write_text('{"version":"test-pinned-runtime"}\n')
        self.files = []
        for relative in sorted(video.REQUIRED):
            path = self.root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(video.runtime_id(self.root) if path.name == 'runtime-id.txt' else ('fixture ' + path.name).encode())
            self.files.append({'path': relative, 'bytes': path.stat().st_size,
                               'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
        self.write_manifest()

    def write_manifest(self):
        (self.root / 'video-runtime.manifest.json').write_text(json.dumps({'files': self.files}))

    def archive(self, base='', omit=None, corrupt=None, duplicate=None):
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, 'w') as target:
            for item in self.files:
                source = self.root / item['path']
                if source.name == omit:
                    continue
                prefix = 'assets/video/' if '/assets/' in item['path'] else 'lib/arm64-v8a/'
                name = base + prefix + source.name
                target.writestr(name, b'corrupted' if source.name == corrupt else source.read_bytes())
                if source.name == duplicate:
                    import warnings
                    with warnings.catch_warnings():
                        warnings.simplefilter('ignore', UserWarning)
                        target.writestr(name, source.read_bytes())
        return zipfile.ZipFile(stream)

    def test_complete_inventory_staging_apk_and_bundle(self):
        files = video.inventory(self.root)
        video.validate_assets(self.root / 'app/src/main/assets', files)
        for base in ['', 'base/']:
            with self.archive(base=base) as archive:
                self.assertEqual(video.validate_archive(archive, files, base), 6)
        with self.archive() as source, zipfile.ZipFile(io.BytesIO(), 'w') as writing:
            for name in source.namelist():
                writing.writestr(name, source.read(name))
            self.assertEqual(video.validate_archive(writing, files), 6)

    def test_missing_any_inventory_entry_fails_including_legacy_four_file_manifest(self):
        original = self.files
        for missing in original:
            with self.subTest(path=missing['path']):
                self.files = [item for item in original if item != missing]
                self.write_manifest()
                with self.assertRaisesRegex(ValueError, 'Incomplete video runtime inventory'):
                    video.inventory(self.root)
        self.files = [item for item in original if Path(item['path']).name not in ['runtime-id.txt', 'launcher.py']]
        self.write_manifest()
        with self.assertRaises(ValueError):
            video.inventory(self.root)

    def test_wrong_runtime_id_fails_even_when_manifest_hash_was_updated(self):
        item = next(item for item in self.files if item['path'].endswith('/runtime-id.txt'))
        data = b'0' * 64 + b'\n'
        (self.root / item['path']).write_bytes(data)
        item.update(bytes=len(data), sha256=hashlib.sha256(data).hexdigest())
        self.write_manifest()
        with self.assertRaisesRegex(ValueError, 'runtime ID'):
            video.inventory(self.root)

    def test_corrupted_program_and_missing_staged_id_fail(self):
        (self.root / 'app/src/main/assets/video/yt-dlp').write_bytes(b'changed')
        with self.assertRaises(ValueError):
            video.inventory(self.root)
        (self.root / 'app/src/main/assets/video/runtime-id.txt').unlink()
        with self.assertRaisesRegex(ValueError, 'runtime-id.txt'):
            video.validate_assets(self.root / 'app/src/main/assets', self.files)

    def test_final_archive_rejects_each_missing_runtime_component(self):
        for item in self.files:
            with self.subTest(path=item['path']), self.archive(omit=Path(item['path']).name) as archive:
                with self.assertRaises(ValueError):
                    video.validate_archive(archive, self.files)

    def test_final_archive_rejects_changed_or_duplicate_program(self):
        with self.archive(corrupt='launcher.py') as archive:
            with self.assertRaises(ValueError):
                video.validate_archive(archive, self.files)
        with self.archive(duplicate='runtime-id.txt') as archive:
            with self.assertRaises(ValueError):
                video.validate_archive(archive, self.files)

    def test_clean_build_reconstruction_from_inventory_keeps_runtime_id_and_launcher(self):
        clean = self.root / 'clean'
        clean.mkdir()
        (clean / 'video-runtime.lock.json').write_bytes((self.root / 'video-runtime.lock.json').read_bytes())
        (clean / 'video-runtime.manifest.json').write_bytes((self.root / 'video-runtime.manifest.json').read_bytes())
        for item in video.inventory(self.root):
            target = clean / item['path']
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes((self.root / item['path']).read_bytes())
        self.assertEqual(video.inventory(clean), self.files)


if __name__ == '__main__':
    unittest.main()
