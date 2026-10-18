package dev.pocketllama.voice;
/** Streaming, anti-aliased mono resampler. Fixed 128-sample ring and 256 polyphases. */
public final class SincResampler {
    public interface Sink{void sample(float x)throws Exception;}
    private final Sink sink;private final int rate;private final double step;private final double[][] kernel=new double[256][32];
    private final float[] ring=new float[128];private long input;private double position;private boolean done;
    public SincResampler(int rate,Sink sink){
        if(rate<8000||rate>192000)throw new IllegalArgumentException("Неподдерживаемая частота аудио");this.rate=rate;this.sink=sink;step=rate/16000.0;
        double cutoff=0.47*Math.min(1,16000.0/rate);
        for(int p=0;p<256;p++){double sum=0;for(int k=0;k<32;k++){double x=k-15-p/256.0;double v=(Math.abs(x)<1e-10?2*cutoff:Math.sin(2*Math.PI*cutoff*x)/(Math.PI*x))*(0.5+0.5*Math.cos(Math.PI*x/16));kernel[p][k]=v;sum+=v;}for(int k=0;k<32;k++)kernel[p][k]/=sum;}
    }
    public void accept(float x)throws Exception{if(done)throw new IllegalStateException();if(rate==16000){sink.sample(x);input++;return;}ring[(int)(input++%128)]=x;emit(false);}
    private void emit(boolean finish)throws Exception{
        while(finish?position<input:Math.floor(position)+16<input){long center=(long)Math.floor(position);int phase=(int)((position-center)*256);double sum=0;
            for(int k=0;k<32;k++){long at=center+k-15;if(at>=0&&at<input)sum+=ring[(int)(at%128)]*kernel[phase][k];}sink.sample((float)sum);position+=step;
        }
    }
    public void finish()throws Exception{if(done)return;if(rate!=16000)emit(true);done=true;}
}
