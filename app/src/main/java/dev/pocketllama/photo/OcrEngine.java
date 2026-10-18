package dev.pocketllama.photo;

import ai.onnxruntime.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Mobile PaddleOCR models, batch 1, two threads, sequential detector/recognizer lifetimes. */
public final class OcrEngine {
    public interface Progress {void step(int done,int total,List<Line> lines)throws Exception;}
    public static final class Line {public final String text;public final float score;public final double[] box;public Line(String text,float score,double[] box){this.text=text;this.score=score;this.box=box;}}
    private final OrtEnvironment env=OrtEnvironment.getEnvironment();private final File models;
    private volatile OrtSession.RunOptions running;
    public OcrEngine(File models){this.models=models;}
    public void cancel(){OrtSession.RunOptions r=running;if(r!=null)try{r.setTerminate(true);}catch(Exception ignored){}}
    private OrtSession session(String name)throws Exception {
        try(OrtSession.SessionOptions o=new OrtSession.SessionOptions()){
            o.setIntraOpNumThreads(2);o.setInterOpNumThreads(1);o.setCPUArenaAllocator(false);o.setMemoryPatternOptimization(false);
            o.addConfigEntry("session.intra_op.allow_spinning","0");o.addConfigEntry("session.inter_op.allow_spinning","0");return env.createSession(new File(models,name).getPath(),o);
        }
    }
    private OrtSession.Result run(OrtSession s,OnnxTensor input)throws Exception {
        if(Thread.currentThread().isInterrupted())throw new InterruptedException();
        try(OrtSession.RunOptions options=new OrtSession.RunOptions()) {running=options;return s.run(Collections.singletonMap(s.getInputNames().iterator().next(),input),options);}finally{running=null;}
    }
    public List<Line> recognize(OcrRaster image,Progress progress)throws Exception {
        double ratio=Math.min(1,1280./Math.max(image.width,image.height));int w=Math.max(32,(int)Math.round(image.width*ratio/32)*32),h=Math.max(32,(int)Math.round(image.height*ratio/32)*32);
        List<OcrGeometry.Box> boxes;
        try(OrtSession det=session("det.onnx");OnnxTensor input=OnnxTensor.createTensor(env,FloatBuffer.wrap(image.detector(w,h)),new long[]{1,3,h,w});OrtSession.Result result=run(det,input)){
            OnnxTensor output=(OnnxTensor)result.get(0);long[] shape=output.getInfo().getShape();if(shape.length!=4||shape[0]!=1||shape[1]!=1)throw new IOException("Неизвестный выход детектора OCR");
            FloatBuffer values=output.getFloatBuffer();float[] map=new float[values.remaining()];values.get(map);boxes=OcrGeometry.detect(map,(int)shape[3],(int)shape[2],image.width,image.height);
        }
        ArrayList<Line> lines=new ArrayList<>();progress.step(0,boxes.size(),lines);if(boxes.isEmpty())return lines;
        List<String> alphabet=Files.readAllLines(new File(models,"characters.txt").toPath(),StandardCharsets.UTF_8);
        try(OrtSession rec=session("rec.onnx")) {
            for(int i=0;i<boxes.size();i++){
                OcrGeometry.Box box=boxes.get(i);int width=Math.max(1,(int)Math.ceil(48*box.width/box.height));
                if(width>3200)throw new IOException("Слишком длинная строка. Снимите участок крупнее");int padded=Math.max(320,((width+7)/8)*8);
                try(OnnxTensor input=OnnxTensor.createTensor(env,FloatBuffer.wrap(image.recognizer(box,width,padded)),new long[]{1,3,48,padded});OrtSession.Result result=run(rec,input)){
                    OnnxTensor output=(OnnxTensor)result.get(0);long[] shape=output.getInfo().getShape();if(shape.length!=3||shape[0]!=1)throw new IOException("Неизвестный выход распознавания OCR");
                    OcrGeometry.Text t=OcrGeometry.decode(output.getFloatBuffer(),(int)shape[1],(int)shape[2],alphabet);if(!t.text.isEmpty())lines.add(new Line(t.text,t.score,box.points));
                }progress.step(i+1,boxes.size(),lines);
            }
        }return lines;
    }
}
