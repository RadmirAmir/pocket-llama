#!/usr/bin/env python3
"""Independent numerical reference for VoiceHostProbe features output.
Requires numpy and kaldi-native-fbank. Run VoiceHostProbe features PREFIX first.
"""
import sys
import numpy as np
import kaldi_native_fbank as knf
p=sys.argv[1]
x=np.fromfile(p+'-input.f32',dtype='<f4')
frames=np.lib.stride_tricks.sliding_window_view(x,320)[::160]
power=abs(np.fft.rfft(frames*np.hanning(321)[:-1],n=320))**2
points=700*np.expm1(np.linspace(0,np.log1p(8000/700),66));freq=np.linspace(0,8000,161)
filters=np.maximum(0,np.minimum((freq[:,None]-points[:-2])/(points[1:-1]-points[:-2]),(points[2:]-freq[:,None])/(points[2:]-points[1:-1])))
ref=np.log(np.clip(power@filters,1e-9,1e9)).T.astype('f4')
got=np.fromfile(p+'-asr.f32',dtype='<f4').reshape(ref.shape)
asr_error=float(abs(ref-got).max());assert asr_error<2e-4,asr_error
opts=knf.FbankOptions();opts.frame_opts.dither=0;opts.frame_opts.snip_edges=True;opts.frame_opts.samp_freq=16000;opts.mel_opts.num_bins=80
f=knf.OnlineFbank(opts);f.accept_waveform(16000,x.tolist());f.input_finished()
ref=np.array([f.get_frame(i) for i in range(f.num_frames_ready)]);ref-=ref.mean(axis=0)
got=np.fromfile(p+'-speaker.f32',dtype='<f4').reshape(ref.shape)
delta=abs(ref-got);assert delta.mean()<0.002 and delta.max()<0.02,(delta.mean(),delta.max())
print({'gigaam_max_error':asr_error,'speaker_max_error':float(delta.max()),'speaker_mean_error':float(delta.mean())})
