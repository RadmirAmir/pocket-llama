from pathlib import Path
import hashlib,importlib.util,json,tempfile,unittest
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('release_copy',ROOT/'scripts/reconstruct-release-apk.py');copy=importlib.util.module_from_spec(spec);spec.loader.exec_module(copy)

class ReconstructionTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name)
        self.base=self.root/'base.apk';self.base.write_bytes(b'0123456789abcdefghij')
        self.data=self.root/'literal.bin';self.data.write_bytes(b'<>()')
        self.target=self.root/'output.apk';self.manifest=self.root/'plan.json'
        def item(path):return {'name':path.name,'bytes':path.stat().st_size,'sha256':copy.digest(path)}
        expected=b'<>abcdefghij()'
        self.plan={'format':'pocket-apk-copy-v1','tag':'v0.13.2-rc.1','sourceCommit':'a'*40,
                   'baseline':item(self.base),'literal':item(self.data),
                   'candidate':{'name':'candidate.apk','bytes':len(expected),'sha256':hashlib.sha256(expected).hexdigest()},
                   'operations':[['data',0,2],['copy',10,10],['data',2,2]]}
        self.save()
    def save(self):self.manifest.write_text(json.dumps(self.plan))
    def tearDown(self):self.temp.cleanup()
    def test_exact_bytes(self):
        copy.reconstruct(self.manifest,self.base,self.target)
        self.assertEqual(self.target.read_bytes(),b'<>abcdefghij()')
    def test_wrong_baseline(self):
        self.base.write_bytes(b'X'*20)
        with self.assertRaises(ValueError):copy.reconstruct(self.manifest,self.base,self.target)
        self.assertFalse(self.target.exists())
    def test_wrong_literal(self):
        self.data.write_bytes(b'xxxx')
        with self.assertRaises(ValueError):copy.reconstruct(self.manifest,self.base,self.target)
    def test_out_of_range(self):
        self.plan['operations'][1][1]=19;self.save()
        with self.assertRaises(ValueError):copy.reconstruct(self.manifest,self.base,self.target)
    def test_payload_path_escape(self):
        self.plan['literal']['name']='../literal.bin';self.save()
        with self.assertRaises(ValueError):copy.reconstruct(self.manifest,self.base,self.target)
    def test_candidate_digest_before_install(self):
        self.plan['candidate']['sha256']='b'*64;self.save()
        with self.assertRaises(ValueError):copy.reconstruct(self.manifest,self.base,self.target)
        self.assertFalse(self.target.exists())

if __name__=='__main__':unittest.main()
