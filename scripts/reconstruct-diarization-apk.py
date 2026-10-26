#!/usr/bin/env python3
"""Replay a frozen signed candidate from public, hashed baseline/model bytes and a small literal."""
from pathlib import Path
import argparse
import hashlib
import json
import re


def digest(path):
    with Path(path).open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()


def reconstruct(manifest,baseline,model,output):
    manifest=Path(manifest).resolve();plan=json.loads(manifest.read_text())
    if plan.get('format')!='pocket-diar-apk-copy-v1' or not re.fullmatch('[a-f0-9]{40}',plan.get('sourceCommit','')):
        raise ValueError('Invalid signed candidate plan')
    sources={'copy':Path(baseline).resolve(),'model':Path(model).resolve(),'data':manifest.parent/plan['literal']['name']}
    if sources['data'].resolve().parent!=manifest.parent:raise ValueError('Invalid literal path')
    for kind,entry in [('copy','baseline'),('model','model'),('data','literal')]:
        item=plan[entry];file=sources[kind]
        if (type(item['bytes']) is not int or not 0<item['bytes']<=2**31
                or not re.fullmatch('[a-f0-9]{64}',item['sha256'])
                or file.stat().st_size!=item['bytes'] or digest(file)!=item['sha256']):
            raise ValueError('Candidate input checksum mismatch: '+entry)
    if plan['literal']['bytes']>32*1024*1024:raise ValueError('Oversized literal')
    output=Path(output).resolve();candidate=plan['candidate']
    if output in [p.resolve() for p in sources.values()] or output==manifest:raise ValueError('Output overlaps input')
    if (type(candidate['bytes']) is not int or not 0<candidate['bytes']<2**31
            or not re.fullmatch('[a-f0-9]{64}',candidate['sha256'])):raise ValueError('Invalid output metadata')
    ops=plan['operations']
    if not isinstance(ops,list) or not 0<len(ops)<=4096:raise ValueError('Invalid operation list')
    total=0
    for operation in ops:
        if not isinstance(operation,list) or len(operation)!=3:raise ValueError('Invalid range')
        kind,start,length=operation
        if kind not in sources or type(start) is not int or type(length) is not int or start<0 or length<=0 or start+length>sources[kind].stat().st_size:
            raise ValueError('Candidate range exceeds source')
        total+=length
    if total!=candidate['bytes']:raise ValueError('Candidate length mismatch')
    output.parent.mkdir(parents=True,exist_ok=True);part=output.with_suffix(output.suffix+'.part')
    checksum=hashlib.sha256()
    try:
        with part.open('wb') as out:
            for kind,start,length in ops:
                with sources[kind].open('rb') as stream:
                    stream.seek(start)
                    while length:
                        block=stream.read(min(length,1024*1024))
                        if not block:raise ValueError('Incomplete candidate input')
                        out.write(block);checksum.update(block);length-=len(block)
        if checksum.hexdigest()!=candidate['sha256']:raise ValueError('Signed APK checksum mismatch')
        part.replace(output)
    finally:part.unlink(missing_ok=True)
    return candidate


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ['manifest','baseline','model','output']:parser.add_argument('--'+name,required=True)
    print(json.dumps(reconstruct(**vars(parser.parse_args()))))
