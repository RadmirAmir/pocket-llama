#!/usr/bin/env python3
"""Sign a data-only model catalog. Keep the private RSA key outside the repository."""
import argparse,base64,json
from pathlib import Path
from cryptography.hazmat.primitives import hashes,serialization
from cryptography.hazmat.primitives.asymmetric import padding,rsa
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--input',required=True);p.add_argument('--key',required=True);p.add_argument('--output',required=True);p.add_argument('--public-key');a=p.parse_args()
 key=serialization.load_pem_private_key(Path(a.key).read_bytes(),password=None)
 if not isinstance(key,rsa.RSAPrivateKey) or key.key_size<3072:raise SystemExit('Use an RSA private key of at least 3072 bits')
 payload=json.dumps(json.loads(Path(a.input).read_text()),ensure_ascii=False,separators=(',',':')).encode()
 signature=key.sign(payload,padding.PKCS1v15(),hashes.SHA256())
 Path(a.output).write_text(json.dumps({'payload':base64.b64encode(payload).decode(),'signature':base64.b64encode(signature).decode()},separators=(',',':'))+'\n')
 if a.public_key:Path(a.public_key).write_text(base64.b64encode(key.public_key().public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)).decode()+'\n')
if __name__=='__main__':main()
