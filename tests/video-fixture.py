"""Deterministic subprocess producer. No network and no user content."""
import json,os,sys,time
url=sys.argv[-1]
if url.endswith('/bad-options'):
    sys.stderr.write('yt-dlp: error: no such option: --no-check-updates\n');sys.exit(2)
if url.endswith('/python-fail'):
    sys.stderr.write('Traceback (most recent call last):\n  File "/data/user/0/private/launcher.py", line 1\nModuleNotFoundError: No module named yt_dlp\n');sys.exit(1)
if url.endswith('/diagnostic'):
    sys.stderr.write('x'*200000+'\nERROR: transport https://cdn.example/audio?token=supersecret token=anothersecret /data/user/0/private/audio.m4a\n');sys.exit(7)
if url.endswith('/html'):
    for byte in b'\xef\xbb\xbf  <!DOCTYPE html><html><body>Site Unavailable</body></html>':sys.stdout.buffer.write(bytes([byte]));sys.stdout.flush()
    sys.exit()
if '--skip-download' in sys.argv:
    sys.stderr.write('diagnostic\n'*20000);sys.stderr.flush()
    print(json.dumps({'title':'Видео · тест','duration':30,'_type':'video'}));sys.exit()
if url.endswith('/fail'):
    sys.stderr.write('x'*200000+' ERROR HTTP Error 403: signed-private-url?token=secret\n');sys.stderr.flush();sys.stdout.buffer.write(b'x'*20000);sys.exit(1)
if url.endswith('/slow'):
    while True:sys.stdout.buffer.write(b's'*8192);sys.stdout.flush();time.sleep(.05)
size=1024*1024+333
for at in range(0,size,8192):sys.stdout.buffer.write(bytes(i%251 for i in range(at,min(size,at+8192))))
