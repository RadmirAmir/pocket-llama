"""Regression checks for free community delivery versus separately licensed builds."""
import copy
import importlib.util
import json
from pathlib import Path
import unittest
import tempfile
import zipfile
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('distribution_prepare', ROOT / 'scripts/prepare-distribution.py')
delivery = importlib.util.module_from_spec(spec)
spec.loader.exec_module(delivery)


class SuryaDistributionTests(unittest.TestCase):
    def setUp(self):
        self.config = json.loads((ROOT / 'distribution/release.json').read_text())

    def test_free_community_downloads_vlm_without_separate_detector(self):
        self.assertEqual(delivery.surya_policy(self.config, 'community'), (True, False))
        self.assertFalse(self.config['suryaRedistribution']['approved'])
        self.assertNotIn('detectorRedistribution', self.config)

    def test_stale_detector_assets_never_enter_any_new_channel(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / 'distribution').mkdir()
            (root / 'distribution/release.json').write_text(json.dumps(self.config))
            (root / 'notices').mkdir()
            (root / 'notices/Surya-community.txt').write_text('Main model terms')
            assets = root / 'app/src/main/assets'
            for group in ['speech', 'moonshine', 'ocr']:
                (assets / group).mkdir(parents=True)
                (assets / group / 'manifest.json').write_text('{"files": []}')
            (assets / 'surya').mkdir()
            (assets / 'surya/line-detector.onnx').write_bytes(b'stale developer weight')
            (assets / 'surya/manifest.json').write_text('{"stale": true}')
            (root / '.cache/surya').mkdir(parents=True)
            models = []
            for name in ['surya.gguf', 'mmproj.gguf']:
                (root / '.cache/surya' / name).write_bytes(b'fixture')
                models.append({'name': name, 'bytes': 7, 'sha256': 'unused fixture hash'})
            (root / 'scanner-runtime.lock.json').write_text(json.dumps({'surya': {'models': models}}))
            with patch.object(delivery, 'ROOT', root), patch.object(delivery, 'checked'), patch.object(delivery.video_bundle, 'inventory', return_value=[]):
                for channel in ['community', 'play', 'personal']:
                    stage = delivery.prepare(channel)
                    names = [p.relative_to(stage).as_posix() for p in stage.rglob('*') if p.is_file()]
                    self.assertFalse(any('line-detector' in n or n == 'assets/surya/manifest.json' for n in names))
                    if channel == 'personal':
                        self.assertEqual((stage / 'assets/surya/bundled/surya.gguf').read_bytes(), b'fixture')
                    else:
                        self.assertFalse((stage / 'assets/surya').exists())

    def test_retired_files_in_staging_stop_packaging(self):
        with tempfile.TemporaryDirectory() as temporary:
            common = Path(temporary)
            delivery.assert_scanner_assets(common, False, {})
            for path in ['surya/line-detector.onnx', 'surya/manifest.json', 'notices/Surya-detector-source.txt']:
                retired = common / path
                retired.parent.mkdir(parents=True, exist_ok=True)
                retired.write_bytes(b'stale build resource')
                with self.assertRaises(SystemExit):
                    delivery.assert_scanner_assets(common, False, {})
                retired.unlink()

    def test_apk_writer_rejects_retired_files_that_appear_after_staging(self):
        spec = importlib.util.spec_from_file_location('apk_build', ROOT / 'scripts/build-apk.py')
        builder = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(builder)
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            assets = root / 'assets'
            assets.mkdir()
            (assets / 'app.js').write_bytes(b'valid UI asset')
            for relative in ['surya/line-detector.onnx', 'notices/Surya-detector-source.txt']:
                retired = assets / relative
                retired.parent.mkdir(parents=True, exist_ok=True)
                retired.write_bytes(b'late stale file')
                with zipfile.ZipFile(root / 'output.apk', 'w') as archive:
                    with self.assertRaises(SystemExit):
                        builder.package_assets(archive, assets)
                with zipfile.ZipFile(root / 'output.apk') as archive:
                    self.assertEqual(archive.read('assets/app.js'), b'valid UI asset')
                    self.assertNotIn('assets/' + relative, archive.namelist())
                retired.unlink()

    def test_free_policy_does_not_enable_play_or_allow_embedding_vlm(self):
        self.assertEqual(delivery.surya_policy(self.config, 'play'), (False, False))
        with self.assertRaises(SystemExit):
            delivery.surya_policy(self.config, 'play', True)
        with self.assertRaises(SystemExit):
            delivery.surya_policy(self.config, 'community', True, True)

    def test_monetized_or_incomplete_profile_cannot_silently_inherit_permission(self):
        for key, value in [('monetized', True), ('noticeFile', ''), ('distribution', 'commercial')]:
            config = copy.deepcopy(self.config)
            config['communitySurya'][key] = value
            self.assertEqual(delivery.surya_policy(config, 'community'), (False, False))
            with self.assertRaises(SystemExit):
                delivery.surya_policy(config, 'community', True)

    def test_explicit_opt_out_and_personal_profile_are_preserved(self):
        self.assertEqual(delivery.surya_policy(self.config, 'community', False), (False, False))
        self.assertEqual(delivery.surya_policy(self.config, 'personal'), (True, True))
        self.assertEqual(delivery.surya_policy(self.config, 'personal', bundle_surya=False), (True, False))


if __name__ == '__main__':
    unittest.main()
