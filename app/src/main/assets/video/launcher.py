"""Private process group and RAM-only QuickJS challenge transport.

Upstream QuickJS adapter uses NamedTemporaryFile. Keep challenge scripts and
their URL/signature payloads in an anonymous memfd instead, inherited only by
the solver. Never fall back to a plaintext temporary file.
"""
import ctypes
import os
import platform
import runpy
import signal
import sys

os.setsid()
def stop(*_):
    os.killpg(os.getpgrp(), signal.SIGKILL)
signal.signal(signal.SIGTERM, stop)
# If Android kills the service process, retire its downloader and solver too.
try:
    ctypes.CDLL(None).prctl(1, signal.SIGTERM, 0, 0, 0)
except (AttributeError, OSError):
    pass

tool = sys.argv.pop(1)
sys.argv[0] = tool
sys.path.insert(0, tool)
from yt_dlp.extractor.youtube.jsc._builtin.quickjs import QuickJSJCP
from yt_dlp.extractor.youtube.jsc.provider import JsChallengeProviderError
from yt_dlp.utils import Popen

def memory_solver(self, source):
    fd = -1
    try:
        if hasattr(os, 'memfd_create'):
            fd = os.memfd_create('pocket-js', 1)
        else:
            # Android 10 has the kernel syscall even when libc lacks its wrapper.
            number = {'aarch64': 279, 'x86_64': 319}.get(platform.machine())
            if number is None:
                raise OSError('Unsupported memory transport')
            fd = ctypes.CDLL(None, use_errno=True).syscall(number, b'pocket-js', 1)
            if fd < 0:
                raise OSError(ctypes.get_errno(), 'Cannot create memory transport')
        data = source.encode('utf-8')
        with os.fdopen(os.dup(fd), 'wb') as out:
            out.write(data)
        os.lseek(fd, 0, os.SEEK_SET)
        with Popen([self.runtime_info.path, '--script', '/proc/self/fd/' + str(fd)],
                   text=True, stdout=-1, stderr=-1, pass_fds=(fd,)) as process:
            stdout, stderr = process.communicate_or_kill()
            if process.returncode or stderr:
                raise JsChallengeProviderError('In-memory QuickJS solver failed')
        return stdout
    except OSError as error:
        raise JsChallengeProviderError('In-memory QuickJS transport unavailable') from error
    finally:
        if fd >= 0:
            os.close(fd)

QuickJSJCP._run_js_runtime = memory_solver
runpy.run_path(tool, run_name='__main__')
