package dev.pocketllama.voice;

import ai.onnxruntime.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** All ONNX sessions live in :speech, never in the recording/UI process. CPU INT8,
 * sequential execution, no spinning, no persistent large allocation arena. */
public final class SpeechModels {
    private SpeechModels(){}
    private static final OrtEnvironment ENV=OrtEnvironment.getEnvironment();
    public static OrtSession open(File file,int threads)throws OrtException {
        try(OrtSession.SessionOptions options=new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(Math.max(1,Math.min(4,threads))); options.setInterOpNumThreads(1);
            options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL);
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            options.setCPUArenaAllocator(false);options.setMemoryPatternOptimization(false);
            options.addConfigEntry("session.intra_op.allow_spinning","0");
            options.addConfigEntry("session.inter_op.allow_spinning","0");
            return ENV.createSession(file.getAbsolutePath(),options);
        }
    }
    public static final class Word {
        public final String text;public final long startMs,endMs;
        public Word(String t,long s,long e){text=t;startMs=s;endMs=e;}
    }
    public static final class Asr implements AutoCloseable {
        private final OrtSession session;
        private final SignalFeatures frontend=new SignalFeatures(false);
        private final String[] tokens=new String[256];
        private final OrtSession.RunOptions run;
        public Asr(File dir,int threads)throws Exception {
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream(new File(dir,"tokens.tsv")),StandardCharsets.UTF_8))){
                String line;int n=0;while((line=reader.readLine())!=null){String[] p=line.split("\t",3);if(p.length!=3)throw new IOException("Повреждён tokenizer");int id=Integer.parseInt(p[0]);tokens[id]=p[1].equals("control")?"":p[1].equals("unknown")?"⁇":p[2];n++;}if(n!=256)throw new IOException("Нужен GigaAM v3 e2e CTC tokenizer (256)");
            }
            run=new OrtSession.RunOptions();session=open(new File(dir,"gigaam.int8.onnx"),threads);
        }
        public void cancel(){try{run.setTerminate(true);}catch(Exception ignored){}}
        public List<Word> recognize(float[] pcm,long offsetMs)throws Exception {
            if(pcm.length>16000*30)throw new IOException("ASR окно превышает 30 секунд");
            float[] features=frontend.extract(pcm);int frames=frontend.frames(pcm.length);
            try(OnnxTensor f=OnnxTensor.createTensor(ENV,FloatBuffer.wrap(features),new long[]{1,64,frames});
                OnnxTensor len=OnnxTensor.createTensor(ENV,LongBuffer.wrap(new long[]{frames}),new long[]{1});
                OrtSession.Result result=session.run(new HashMap<String,OnnxTensor>(){{put("features",f);put("feature_lengths",len);}},run)) {
                OnnxTensor probs=(OnnxTensor)result.get("log_probs").get();long[] shape=probs.getInfo().getShape();
                int t=(int)shape[1],classes=(int)shape[2];if(classes!=257)throw new IOException("Неверная ASR модель: требуется e2e CTC 257 classes");
                Object lengths=result.get("encoded_lengths").get().getValue();
                int valid=lengths instanceof int[]?((int[])lengths)[0]:(int)((long[])lengths)[0];valid=Math.min(t,valid);
                FloatBuffer values=probs.getFloatBuffer();ArrayList<Word> words=new ArrayList<>();
                StringBuilder word=new StringBuilder();long wordStart=offsetMs,wordEnd=offsetMs;int previous=-1;
                double shift=(pcm.length/16.0)/Math.max(1,valid);
                for(int i=0;i<valid;i++) {
                    int best=0;float score=-Float.MAX_VALUE;for(int j=0;j<classes;j++){float v=values.get(i*classes+j);if(v>score){score=v;best=j;}}
                    if(best!=256&&best!=previous){String piece=tokens[best];long start=offsetMs+Math.round(i*shift),end=Math.min(offsetMs+pcm.length/16,offsetMs+Math.round((i+1)*shift));
                        for(int k=0;k<piece.length();k++){char c=piece.charAt(k);if(c=='▁'){if(word.length()>0){words.add(new Word(word.toString(),wordStart,Math.max(wordStart+1,wordEnd)));word.setLength(0);}wordStart=start;}else{if(word.length()==0)wordStart=start;word.append(c);wordEnd=end;}}
                    }
                    previous=best;
                }
                if(word.length()>0)words.add(new Word(word.toString(),wordStart,Math.max(wordStart+1,wordEnd)));
                return words;
            }
        }
        @Override public void close()throws Exception{try{session.close();}finally{run.close();}}
    }
    public static final class Speaker implements AutoCloseable {
        private final OrtSession session;private final SignalFeatures frontend=new SignalFeatures(true);
        public Speaker(File dir,int threads)throws Exception{session=open(new File(dir,"campplus.onnx"),threads);}
        public float[] embed(float[] pcm)throws Exception {
            float[] features=frontend.extract(pcm);
            try(OnnxTensor f=OnnxTensor.createTensor(ENV,FloatBuffer.wrap(features),new long[]{1,frontend.frames(pcm.length),80});OrtSession.Result result=session.run(Collections.singletonMap("x",f))){
                FloatBuffer b=((OnnxTensor)result.get(0)).getFloatBuffer();float[] v=new float[b.remaining()];b.get(v);return SpeakerClusters.normalize(v);
            }
        }
        @Override public void close()throws Exception{session.close();}
    }
    public static final class Vad implements AutoCloseable {
        private final OrtSession session;private float[] state=new float[256],context=new float[64];
        public Vad(File dir)throws Exception{session=open(new File(dir,"silero.onnx"),1);}
        /** Silero v5 16k: 512 samples plus a 64-sample left context; state reset per recording. */
        public float probability(float[] pcm,int count)throws Exception {
            float[] input=new float[576];System.arraycopy(context,0,input,0,64);System.arraycopy(pcm,0,input,64,Math.min(count,512));
            try(OnnxTensor x=OnnxTensor.createTensor(ENV,FloatBuffer.wrap(input),new long[]{1,576});OnnxTensor s=OnnxTensor.createTensor(ENV,FloatBuffer.wrap(state),new long[]{2,1,128});OnnxTensor sr=OnnxTensor.createTensor(ENV,LongBuffer.wrap(new long[]{16000}),new long[]{});
                OrtSession.Result r=session.run(new HashMap<String,OnnxTensor>(){{put("input",x);put("state",s);put("sr",sr);}})){
                ((OnnxTensor)r.get("stateN").get()).getFloatBuffer().get(state);System.arraycopy(input,512,context,0,64);return ((OnnxTensor)r.get("output").get()).getFloatBuffer().get();
            }
        }
        @Override public void close()throws Exception{session.close();}
    }
}
