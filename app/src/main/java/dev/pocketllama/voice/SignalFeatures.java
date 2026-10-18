package dev.pocketllama.voice;

/** Exact model frontends, no resampling, gain normalization or preemphasis for GigaAM.
 * GigaAM: torchaudio Hann(periodic), FFT320, hop160, HTK64, power2, ln(clamp).
 * CAM++: Kaldi fbank, Povey400, FFT512, hop160, 80 bins, CMN.
 * One reusable FFT workspace; only a bounded audio window is ever resident. */
public final class SignalFeatures {
    private final int win, fft, bins;
    private final boolean kaldi;
    private final double[] window, power, frame, real, imag;
    private final double[][] weights;
    private final double[][] subR, subI;
    private final double[][] twR, twI;
    public SignalFeatures(boolean speaker) {
        kaldi = speaker; win = speaker ? 400 : 320; fft = speaker ? 512 : 320; bins = speaker ? 80 : 64;
        window = new double[win]; power = new double[fft / 2 + 1]; frame = new double[fft];
        real = new double[fft]; imag = new double[fft];
        for (int i=0;i<win;i++) window[i] = speaker
                ? Math.pow(0.5 - 0.5 * Math.cos(2*Math.PI*i/(win-1)), 0.85)
                : 0.5 - 0.5 * Math.cos(2*Math.PI*i/win);
        weights = new double[bins][power.length];
        double low = speaker ? mel(20) : 0, high = mel(8000);
        for(int b=0;b<bins;b++) {
            double left = low + (high-low)*b/(bins+1), mid = low+(high-low)*(b+1)/(bins+1), right=low+(high-low)*(b+2)/(bins+1);
            for(int k=0;k<power.length;k++) {
                double f=16000.0*k/fft;
                double x=speaker?mel(f):f, l=speaker?left:hz(left), m=speaker?mid:hz(mid), r=speaker?right:hz(right);
                weights[b][k]=Math.max(0,Math.min((x-l)/(m-l),(r-x)/(r-m)));
            }
        }
        if (!speaker) {
            subR=new double[5][64]; subI=new double[5][64]; twR=new double[power.length][5]; twI=new double[power.length][5];
            for(int k=0;k<power.length;k++)for(int r=0;r<5;r++) {twR[k][r]=Math.cos(-2*Math.PI*k*r/320);twI[k][r]=Math.sin(-2*Math.PI*k*r/320);}
        } else { subR=subI=twR=twI=null; }
    }
    private static double mel(double hz) { return 1127.0*Math.log1p(hz/700.0); }
    private static double hz(double mel) { return 700.0*Math.expm1(mel/1127.0); }
    public int frames(int samples) { return Math.max(1,1+(Math.max(samples,win)-win)/160); }
    /** Feature-major [64,T] for GigaAM; frame-major [T,80] for CAM++. */
    public float[] extract(float[] pcm) {
        int count=frames(pcm.length);float[] result=new float[count*bins];
        for(int t=0;t<count;t++) {
            java.util.Arrays.fill(frame,0);double mean=0;
            if(kaldi)for(int j=0;j<win;j++)mean+=(t*160+j<pcm.length?pcm[t*160+j]:0);
            mean/=win;
            for(int j=0;j<win;j++)frame[j]=(t*160+j<pcm.length?pcm[t*160+j]:0)-mean;
            if(kaldi) {for(int j=win-1;j>=1;j--)frame[j]-=0.97*frame[j-1];frame[0]*=0.03;}
            for(int j=0;j<win;j++)frame[j]*=window[j];
            spectrum();
            for(int b=0;b<bins;b++) {
                double v=0;for(int k=0;k<power.length;k++)v+=weights[b][k]*power[k];
                float log=(float)Math.log(kaldi?Math.max(1.1920928955078125e-7,v):Math.max(1e-9,Math.min(1e9,v)));
                result[kaldi?t*bins+b:b*count+t]=log;
            }
        }
        if(kaldi) for(int b=0;b<bins;b++) {double mean=0;for(int t=0;t<count;t++)mean+=result[t*bins+b];mean/=count;for(int t=0;t<count;t++)result[t*bins+b]-=(float)mean;}
        return result;
    }
    private void spectrum() {
        if(!kaldi) {
            for(int r=0;r<5;r++) {for(int m=0;m<64;m++){subR[r][m]=frame[5*m+r];subI[r][m]=0;} fft(subR[r],subI[r]);}
            for(int k=0;k<power.length;k++) {
                double re=0,im=0;int m=k%64;
                for(int r=0;r<5;r++){re+=subR[r][m]*twR[k][r]-subI[r][m]*twI[k][r];im+=subR[r][m]*twI[k][r]+subI[r][m]*twR[k][r];}
                power[k]=re*re+im*im;
            }
        } else {
            System.arraycopy(frame,0,real,0,fft);java.util.Arrays.fill(imag,0);fft(real,imag);
            for(int k=0;k<power.length;k++)power[k]=real[k]*real[k]+imag[k]*imag[k];
        }
    }
    private static void fft(double[] re,double[] im) {
        int n=re.length;
        for(int i=1,j=0;i<n;i++){int bit=n>>1;for(; (j&bit)!=0;bit>>=1)j^=bit;j^=bit;if(i<j){double v=re[i];re[i]=re[j];re[j]=v;v=im[i];im[i]=im[j];im[j]=v;}}
        for(int len=2;len<=n;len<<=1){double wr=Math.cos(-2*Math.PI/len),wi=Math.sin(-2*Math.PI/len);
            for(int i=0;i<n;i+=len){double cr=1,ci=0;for(int j=0;j<len/2;j++){
                int a=i+j,b=a+len/2;double vr=re[b]*cr-im[b]*ci,vi=re[b]*ci+im[b]*cr;
                re[b]=re[a]-vr;im[b]=im[a]-vi;re[a]+=vr;im[a]+=vi;double v=cr*wr-ci*wi;ci=cr*wi+ci*wr;cr=v;
            }}
        }
    }
}
