#!/usr/bin/env python3
"""Reproduce an already signed APK byte-for-byte; signing keys never enter CI."""
from pathlib import Path
import argparse
import hashlib
import json
import re


def digest(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def load_plan(path):
    path = Path(path).resolve()
    plan = json.loads(path.read_text())
    if plan.get('format') != 'pocket-apk-copy-v1':
        raise ValueError('Unsupported release copy plan')
    if not re.fullmatch(r'[a-f0-9]{40}', plan.get('sourceCommit', '')):
        raise ValueError('Invalid source commit')
    if not re.fullmatch(r'v[0-9][0-9A-Za-z.-]*', plan.get('tag', '')):
        raise ValueError('Invalid release tag')
    for group, limit in [('baseline', 2**31), ('candidate', 2**31), ('literal', 32*1024*1024)]:
        item = plan[group]
        if (type(item.get('bytes')) is not int or not 0 < item['bytes'] <= limit
                or not re.fullmatch(r'[a-f0-9]{64}', item.get('sha256', ''))
                or Path(item['name']).name != item['name']):
            raise ValueError('Invalid release file metadata')
    literal = path.parent / plan['literal']['name']
    if literal.resolve().parent != path.parent:
        raise ValueError('Release payload must be beside its plan')
    if literal.stat().st_size != plan['literal']['bytes'] or digest(literal) != plan['literal']['sha256']:
        raise ValueError('Release payload checksum mismatch')
    operations = plan['operations']
    if not isinstance(operations, list) or not 0 < len(operations) <= 4096:
        raise ValueError('Invalid release operations')
    total = 0
    for operation in operations:
        if (not isinstance(operation, list) or len(operation) != 3
                or operation[0] not in ('copy', 'data')
                or any(type(n) is not int for n in operation[1:])
                or operation[1] < 0 or operation[2] <= 0):
            raise ValueError('Invalid release range')
        source = plan['baseline'] if operation[0] == 'copy' else plan['literal']
        if operation[1] + operation[2] > source['bytes']:
            raise ValueError('Release range exceeds its source')
        total += operation[2]
    if total != plan['candidate']['bytes']:
        raise ValueError('Release ranges do not match candidate length')
    return plan, literal


def reconstruct(manifest, baseline, output):
    plan, literal = load_plan(manifest)
    baseline, output = Path(baseline).resolve(), Path(output).resolve()
    if output in (baseline, literal.resolve()) or baseline.stat().st_size != plan['baseline']['bytes']:
        raise ValueError('Invalid baseline or output')
    if digest(baseline) != plan['baseline']['sha256']:
        raise ValueError('Baseline checksum mismatch')
    output.parent.mkdir(parents=True, exist_ok=True)
    part = output.with_suffix(output.suffix + '.part')
    checksum = hashlib.sha256()
    with baseline.open('rb') as old, literal.open('rb') as data, part.open('wb') as target:
        for kind, offset, length in plan['operations']:
            source = old if kind == 'copy' else data
            source.seek(offset)
            while length:
                block = source.read(min(length, 1024*1024))
                if not block:
                    raise ValueError('Incomplete release source')
                target.write(block)
                checksum.update(block)
                length -= len(block)
    if part.stat().st_size != plan['candidate']['bytes'] or checksum.hexdigest() != plan['candidate']['sha256']:
        raise ValueError('Reconstructed signed APK checksum mismatch')
    part.replace(output)
    return {'file': str(output), 'bytes': output.stat().st_size, 'sha256': checksum.hexdigest()}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', required=True)
    parser.add_argument('--baseline', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    print(json.dumps(reconstruct(args.manifest, args.baseline, args.output)))
