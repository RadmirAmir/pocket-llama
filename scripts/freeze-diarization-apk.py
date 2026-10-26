#!/usr/bin/env python3
"""Prepare small reconstruction evidence without publishing a signing key or model weights."""
from pathlib import Path
import argparse
import hashlib
import json
import re
import struct
import zipfile

ROOT=Path(__file__).resolve().parents[1]


def digest(path):
    with Path(path).open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()


def start(stream,entry):
    stream.seek(entry.header_offset)
    header=stream.read(30)
    if header[:4]!=b'PK\x03\x04':raise ValueError('Invalid APK local header')
    names,extras=struct.unpack_from('<HH',header,26)
    return entry.header_offset+30+names+extras


def freeze(args):
    if not re.fullmatch('[a-f0-9]{40}',args.source_commit):raise ValueError('Invalid source commit')
    baseline,candidate,model=map(Path,(args.baseline,args.candidate,args.model))
    ranges=[]
    with baseline.open('rb') as old,candidate.open('rb') as new,zipfile.ZipFile(baseline) as bz,zipfile.ZipFile(candidate) as cz:
        for entry in cz.infolist():
            if not entry.compress_size:continue
            at=start(new,entry)
            if entry.filename=='assets/diarization/Nemotron-3-Diarization.q8_0.gguf':
                if entry.compress_type!=zipfile.ZIP_STORED or digest(model)!=hashlib.sha256(cz.read(entry)).hexdigest():raise ValueError('Store the exact public model uncompressed')
                ranges.append((at,entry.compress_size,'model',0));continue
            try:previous=bz.getinfo(entry.filename)
            except KeyError:continue
            if previous.compress_size!=entry.compress_size:continue
            before=start(old,previous);old.seek(before);new.seek(at)
            if old.read(entry.compress_size)==new.read(entry.compress_size):ranges.append((at,entry.compress_size,'copy',before))
    ranges.sort();ops=[];literal=bytearray();position=0
    with candidate.open('rb') as stream:
        for at,length,kind,origin in ranges+[(candidate.stat().st_size,0,'',0)]:
            if at<position:raise ValueError('Overlapping APK entries')
            if at>position:
                stream.seek(position);data=stream.read(at-position);ops.append(['data',len(literal),len(data)]);literal.extend(data)
            if length:ops.append([kind,origin,length])
            position=at+length
    if not 0<len(literal)<=32*1024*1024:raise ValueError('Candidate literal exceeds source evidence limit')
    manifest=Path(args.output);manifest.parent.mkdir(parents=True,exist_ok=True)
    payload=manifest.with_suffix('.literal.bin');payload.write_bytes(literal)
    def item(path):return {'name':path.name,'bytes':path.stat().st_size,'sha256':digest(path)}
    plan={'format':'pocket-diar-apk-copy-v1','sourceCommit':args.source_commit,
          'baseline':item(baseline),'model':item(model),'candidate':item(candidate),'literal':item(payload),'operations':ops}
    manifest.write_text(json.dumps(plan,indent=2)+'\n')
    print('Frozen signed candidate:',candidate.stat().st_size,'bytes; literal:',len(literal),'bytes; operations:',len(ops))


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    for n in ['baseline','candidate','model','source-commit','output']:p.add_argument('--'+n,required=True)
    freeze(p.parse_args())
