#!/usr/bin/env python3
"""Verify/restore pinned scanner runtime. --export-detector rebuilds the bundled ONNX.

The GGUF/mmproj are intentionally downloaded by the Android app, after an explicit
setup action. No document, camera image, or user data is sent to a model service.
"""
from pathlib import Path
import argparse,hashlib,json,os,shutil,sys,tarfile,urllib.request,zipfile
ROOT=Path(__file__).resolve().parents[1]
SOURCE_COMMIT='a2363d3311773a2b6145a40458043211c50c52f4'
SOURCE_SHA='7eb2b067c516200e4a05cfaaacc1972124def1efd2b85d79dfd608a17af7b4ba'
def digest(p):
 with p.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
def download(url,path,size,sha):
 path.parent.mkdir(parents=True,exist_ok=True)
 if path.is_file() and path.stat().st_size==size and digest(path)==sha:return
 part=path.with_suffix(path.suffix+'.part');total=0
 with urllib.request.urlopen(url,timeout=60) as source,part.open('wb') as out:
  while block:=source.read(1024*1024):
   total+=len(block)
   if total>size:raise ValueError('Oversized download: '+path.name)
   out.write(block)
 if total!=size or digest(part)!=sha:raise ValueError('Incomplete download or checksum mismatch: '+path.name+'; retry')
 os.replace(part,path)
def export():
 import torch,onnx,numpy as np,onnxruntime as ort
 from safetensors.torch import load_file
 work=ROOT/'build/scanner-export';work.mkdir(parents=True,exist_ok=True)
 archive=work/'surya-source.tar.gz';download('https://codeload.github.com/datalab-to/surya/tar.gz/'+SOURCE_COMMIT,archive,25988153,SOURCE_SHA)
 with tarfile.open(archive) as tar:tar.extractall(work,filter='data')
 source=work/('surya-'+SOURCE_COMMIT);sys.path.insert(0,str(source))
 from surya.detection.model.config import EfficientViTConfig
 from surya.detection.model.encoderdecoder import EfficientViTForSemanticSegmentation
 revision='5b7f57b471c54026adb9726ab527c9fbbedac1b7';weights=work/'model.safetensors';download('https://huggingface.co/datalab-to/line_detector0/resolve/'+revision+'/model.safetensors',weights,76929706,'fed8f5e45287766dcb1706b405c8b9756a338f1753f5375dbad31318f0515f9d')
 # The classifier has one text channel; the metadata's num_classes is not the output shape.
 configuration=json.loads((ROOT/'model-configs/surya-line-detector.json').read_text());configuration['num_labels']=1
 config=EfficientViTConfig(**configuration)
 model=EfficientViTForSemanticSegmentation(config);model.load_state_dict(load_file(weights),strict=True);model.float().eval();torch.set_num_threads(3)
 class Wrapper(torch.nn.Module):
  def __init__(self):super().__init__();self.model=model
  def forward(self,x):return self.model(pixel_values=x).logits
 wrapper=Wrapper().eval();dest=ROOT/'app/src/main/assets/surya/line-detector.onnx';dest.parent.mkdir(parents=True,exist_ok=True);x=torch.zeros(1,3,1200,1200)
 with torch.inference_mode():torch.onnx.export(wrapper,x,str(dest),input_names=['image'],output_names=['heatmap'],opset_version=17,dynamo=False,external_data=False)
 onnx.checker.check_model(str(dest));options=ort.SessionOptions();options.intra_op_num_threads=3;s=ort.InferenceSession(str(dest),options,providers=['CPUExecutionProvider']);a=np.random.default_rng(7).normal(size=(1,3,1200,1200)).astype('float32')
 with torch.inference_mode():expected=wrapper(torch.from_numpy(a)).numpy()
 error=float(np.max(np.abs(expected-s.run(None,{'image':a})[0])))
 if error>=.001:raise ValueError('Detector ONNX parity failed: '+str(error))
 print('Detector exported; max absolute error:',error)
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--export-detector',action='store_true');p.add_argument('--public',action='store_true',help='Restore permissive runtime only; exclude the noncommercial detector');a=p.parse_args();lock=json.loads((ROOT/'scanner-runtime.lock.json').read_text());cache=ROOT/'build/scanner-download'
 for artifact in lock['artifacts']:
  missing=any(not (ROOT/f['path']).is_file() or digest(ROOT/f['path'])!=f['sha256'] for f in artifact['files'])
  if not missing:continue
  url=artifact['source'];path=cache/url.rsplit('/',1)[-1];size=artifact.get('bytes',sum(f['bytes'] for f in artifact['files']));download(url,path,size,artifact['sha256'])
  if path.suffix=='.aar':
   with zipfile.ZipFile(path) as z:
    for f in artifact['files']:
     dest=ROOT/f['path'];dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(z.read('classes.jar' if dest.suffix=='.jar' else 'jni/arm64-v8a/'+dest.name))
  else:shutil.copyfile(path,ROOT/artifact['files'][0]['path'])
 if a.export_detector and a.public:raise SystemExit('--export-detector is not a public-distribution preparation step')
 if a.export_detector:export()
 for f in lock['files']+[f for a in lock['artifacts'] for f in a['files']]:
  if a.public and f['path'].startswith('app/src/main/assets/surya/'):continue
  path=ROOT/f['path']
  if not path.is_file() or path.stat().st_size!=f['bytes'] or digest(path)!=f['sha256']:raise SystemExit('Scanner runtime mismatch: '+f['path']+'; rebuild with the pinned export requirements or restore the original source archive')
 print('Pinned scanner assets and runtime verified')
if __name__=='__main__':main()
