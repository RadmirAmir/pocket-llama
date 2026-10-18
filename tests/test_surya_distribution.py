"""Regression checks for free community delivery versus separately licensed builds."""
import copy
import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('distribution_prepare', ROOT / 'scripts/prepare-distribution.py')
delivery = importlib.util.module_from_spec(spec)
spec.loader.exec_module(delivery)


class SuryaDistributionTests(unittest.TestCase):
    def setUp(self):
        self.config = json.loads((ROOT / 'distribution/release.json').read_text())

    def test_free_community_has_detector_and_downloads_vlm(self):
        self.assertEqual(delivery.surya_policy(self.config, 'community'), (True, False))
        self.assertFalse(self.config['suryaRedistribution']['approved'])
        self.assertFalse(self.config['detectorRedistribution']['approved'])

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
