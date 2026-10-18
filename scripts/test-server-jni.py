#!/usr/bin/env python3
"""Execute the production Java/JNI entry point with a small host server stub."""
from pathlib import Path
import argparse, os, subprocess

ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--jdk',required=True);a=p.parse_args()
jdk=Path(a.jdk);out=ROOT/'build/jni-host-tests';out.mkdir(parents=True,exist_ok=True)
subprocess.run(['g++','-std=c++17','-fPIC','-shared','-Wall','-Wextra','-Werror',f'-I{jdk}/include',f'-I{jdk}/include/linux',
    str(ROOT/'native/android_server_jni.cpp'),str(ROOT/'tests/JniServerStub.cpp'),'-o',str(out/'libpocket_vulkan.so')],check=True)
subprocess.run(['javac','-d',str(out),str(ROOT/'app/src/main/java/dev/pocketllama/AndroidServerMain.java')],check=True)
argv=['java','-cp',str(out),'dev.pocketllama.AndroidServerMain',str(out/'libpocket_vulkan.so'),
    '/app/lib/libllama_vulkan.so','--model','/private/model with spaces.gguf','--api-key-file','/proc/self/fd/0']
env=dict(os.environ,POCKET_LLAMA_PARENT_PID=str(os.getpid()))
result=subprocess.run(argv,input='test-stdin-credential\n',text=True,capture_output=True,timeout=20,env=env)
assert result.returncode==13,(result.returncode,result.stdout,result.stderr)
assert 'JNI_SERVER_STUB_OK' in result.stdout and 'POCKET_LLAMA_PID=' in result.stdout,result.stdout
assert 'Android ART/JNI runtime' in result.stderr,result.stderr
assert 'test-stdin-credential' not in result.stdout+result.stderr,'Credential was logged'
rejected=subprocess.run(argv[:4]+['/invalid/libother.so']+argv[5:],text=True,capture_output=True,timeout=20)
assert rejected.returncode==70 and 'IllegalArgumentException' in rejected.stderr,rejected
wrong_parent=subprocess.run(argv,input='test-stdin-credential\n',text=True,capture_output=True,timeout=20,
    env=dict(os.environ,POCKET_LLAMA_PARENT_PID='1'))
assert wrong_parent.returncode==70 and 'Could not attach server lifetime' in wrong_parent.stderr,wrong_parent
assert 'JNI_SERVER_STUB_OK' not in wrong_parent.stdout,'Orphan process entered the server'
print('PASS: production Java/JNI argv, stdin credential, PID marker, exit code, library-name and parent validation')
