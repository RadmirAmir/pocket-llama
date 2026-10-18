#!/usr/bin/env python3
"""Download pinned speech assets and make the GigaAM ConvInteger graph mobile compatible.

Build-time only dependencies: numpy==2.2.6, onnx==1.19.1. No Python in APK.
All external bytes are SHA-256 checked. No runtime downloads.
"""
import hashlib
import json
from pathlib import Path
import shutil
import urllib.request
import numpy as np
import onnx
from onnx import numpy_helper

ROOT=Path(__file__).resolve().parents[1]

def digest(p):
    with open(p,'rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()

def portable_conv(source,destination):
    """ConvInteger CPU kernel needs U8 x U8. Exact recentering of INT8 weights:
    (weight + 128) - (zero_point + 128) == weight - zero_point.
    No dequantization, lost precision, retraining, or float weight expansion.
    Copy zero points because MatMulInteger may share the original signed zero.
    """
    model=onnx.load(source);init={x.name:x for x in model.graph.initializer};count=0
    for node in model.graph.node:
        if node.op_type!='ConvInteger':continue
        w=init[node.input[1]];values=numpy_helper.to_array(w)
        if values.dtype!=np.int8:continue
        name=w.name+'_mobile_u8'
        if name not in init:
            value=numpy_helper.from_array((values.astype(np.int16)+128).astype(np.uint8),name)
            model.graph.initializer.append(value);init[name]=value
        node.input[1]=name
        if len(node.input)<4 or not node.input[3]:raise ValueError('Expected explicit weight zero point')
        old=node.input[3];name=old+'_mobile_u8'
        if name not in init:
            value=numpy_helper.from_array((numpy_helper.to_array(init[old]).astype(np.int16)+128).astype(np.uint8),name)
            model.graph.initializer.append(value);init[name]=value
        node.input[3]=name;count+=1
    if count!=51:raise ValueError(f'Unexpected graph: expected 51 rewritten convolutions, got {count}')
    used={s for n in model.graph.node for s in n.input}
    keep=[i for i in model.graph.initializer if i.name in used]
    del model.graph.initializer[:];model.graph.initializer.extend(keep)
    onnx.checker.check_model(model);onnx.save(model,destination)

def main():
    lock=json.loads((ROOT/'speech-models.lock.json').read_text())
    cache=ROOT/'.cache/speech';cache.mkdir(parents=True,exist_ok=True)
    assets=ROOT/'app/src/main/assets/speech';assets.mkdir(parents=True,exist_ok=True)
    for item in lock['models']:
        source=cache/item['name']
        if not source.exists() or digest(source)!=item['sha256']:
            partial=source.with_suffix('.part')
            with urllib.request.urlopen(item['url'],timeout=120) as r,open(partial,'wb') as f:shutil.copyfileobj(r,f)
            if partial.stat().st_size!=item['bytes'] or digest(partial)!=item['sha256']:raise ValueError('Model checksum failed: '+item['name'])
            partial.replace(source)
        target=assets/item['name']
        if item['name']=='gigaam.int8.onnx':portable_conv(source,target)
        else:shutil.copyfile(source,target)
        print(item['name'],target.stat().st_size,digest(target))
    manifest=json.loads((assets/'manifest.json').read_text())
    for f in manifest['files']:
        p=assets/f['name']
        if p.stat().st_size!=f['bytes'] or digest(p)!=f['sha256']:raise ValueError('Derived asset mismatch: '+f['name'])
    print('All pinned speech assets verified')

if __name__=='__main__':main()
