package dev.pocketllama.photo;
import ai.onnxruntime.*;
import android.graphics.Bitmap;
import java.io.*;
import java.nio.*;
import java.util.*;
import org.json.*;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

/** Official EfficientViT text heatmap, batch one. Closed before loading the VLM. */
public final class SuryaDetector implements AutoCloseable {
    private final OrtEnvironment env=OrtEnvironment.getEnvironment();private final OrtSession session;private volatile OrtSession.RunOptions running;
    public SuryaDetector(File weights)throws Exception {DocumentEdges.load();try(OrtSession.SessionOptions o=new OrtSession.SessionOptions()){o.setIntraOpNumThreads(2);o.setInterOpNumThreads(1);o.setCPUArenaAllocator(false);o.setMemoryPatternOptimization(false);o.addConfigEntry("session.intra_op.allow_spinning","0");session=env.createSession(weights.getPath(),o);}}
    public void cancel(){OrtSession.RunOptions o=running;if(o!=null)try{o.setTerminate(true);}catch(Exception ignored){}}
    public JSONArray detect(Bitmap bitmap,SuryaRuntime.Check check)throws Exception {
        JSONArray lines=new JSONArray();int height=bitmap.getHeight(),width=bitmap.getWidth();int chunks=height>1400?(height+1199)/1200:1;float highest=0;
        for(int chunk=0;chunk<chunks;chunk++){check.run();int top=chunks==1?0:chunk*1200,part=chunks==1?height:Math.min(1200,height-top),padded=chunks==1?height:1200;
            Bitmap section=Bitmap.createBitmap(width,padded,Bitmap.Config.ARGB_8888);android.graphics.Canvas canvas=new android.graphics.Canvas(section);canvas.drawColor(android.graphics.Color.WHITE);canvas.drawBitmap(bitmap,0,-top,null);
            Mat rgba=new Mat(),rgb=new Mat(),thumb=new Mat(),stretched=new Mat();float[] input=new float[3*1200*1200];
            try{Utils.bitmapToMat(section,rgba);Imgproc.cvtColor(rgba,rgb,Imgproc.COLOR_RGBA2RGB);double factor=Math.min(1,Math.min(1200./width,1200./padded));Imgproc.resize(rgb,thumb,new Size(Math.max(1,Math.round(width*factor)),Math.max(1,Math.round(padded*factor))),0,0,Imgproc.INTER_LANCZOS4);Imgproc.resize(thumb,stretched,new Size(1200,1200),0,0,Imgproc.INTER_LANCZOS4);byte[] pixels=new byte[1200*1200*3];stretched.get(0,0,pixels);float[] mean={.485f,.456f,.406f},std={.229f,.224f,.225f};for(int i=0;i<1200*1200;i++)for(int c=0;c<3;c++)input[c*1200*1200+i]=((pixels[i*3+c]&255)/255f-mean[c])/std[c];}
            finally{section.recycle();rgba.release();rgb.release();thumb.release();stretched.release();}
            try(OnnxTensor tensor=OnnxTensor.createTensor(env,FloatBuffer.wrap(input),new long[]{1,3,1200,1200});OrtSession.RunOptions options=new OrtSession.RunOptions()){
                running=options;try(OrtSession.Result result=session.run(Collections.singletonMap(session.getInputNames().iterator().next(),tensor),options)){OnnxTensor out=(OnnxTensor)result.get(0);long[] s=out.getInfo().getShape();if(s.length!=4||s[0]!=1||s[1]<1||s[1]>2||s[2]!=300||s[3]!=300)throw new IOException("Неизвестный выход детектора Surya");float[] map=new float[90000];out.getFloatBuffer().get(map);JSONArray found=boxes(map,300,300,width,padded,top,top+part,check);for(int i=0;i<found.length();i++){JSONObject line=found.getJSONObject(i);highest=Math.max(highest,(float)line.optDouble("confidence"));lines.put(line);if(lines.length()>3000)throw new IOException("На странице больше 3000 строк. Разделите её");}}finally{running=null;}
            }
        }
        if(highest>0)for(int i=0;i<lines.length();i++){JSONObject line=lines.getJSONObject(i);line.put("confidence",line.getDouble("confidence")/highest);}return lines;
    }
    private static JSONArray boxes(float[] heat,int w,int h,int originalW,int originalH,int top,int bottom,SuryaRuntime.Check check)throws Exception {
        Mat small=new Mat(h,w,CvType.CV_32F),map=new Mat(),mask=new Mat(),labels=new Mat(),stats=new Mat(),centroids=new Mat();JSONArray result=new JSONArray();
        try{small.put(0,0,heat);Imgproc.resize(small,map,new Size(1200,1200),0,0,Imgproc.INTER_LINEAR);float[] values=new float[1440000];map.get(0,0,values);float[] sorted=values.clone();Arrays.sort(sorted);double average=0;for(int i=(int)(sorted.length*.9);i<sorted.length;i++)average+=sorted[i];average/=sorted.length-(int)(sorted.length*.9);double scale=Math.sqrt(Math.min(1,Math.max(0,average/.7))),low=Math.max(.1,Math.min(.6,.35*scale)),threshold=Math.max(.15,Math.min(.8,.6*scale));Core.compare(map,new Scalar(low),mask,Core.CMP_GT);int count=Imgproc.connectedComponentsWithStats(mask,labels,stats,centroids,4,CvType.CV_32S);int[] allLabels=new int[1440000];labels.get(0,0,allLabels);
            for(int k=1;k<count;k++){if((k&31)==0)check.run();int area=(int)stats.get(k,Imgproc.CC_STAT_AREA)[0];if(area<10)continue;int x=(int)stats.get(k,0)[0],y=(int)stats.get(k,1)[0],bw=(int)stats.get(k,2)[0],bh=(int)stats.get(k,3)[0],n=(int)Math.sqrt(Math.min(bw,bh));float max=0;for(int yy=y;yy<y+bh;yy++)for(int xx=x;xx<x+bw;xx++)if(allLabels[yy*1200+xx]==k)max=Math.max(max,values[yy*1200+xx]);if(max<threshold)continue;
                int sx=Math.max(0,x-n-1),sy=Math.max(0,y-n-1),ex=Math.min(1200,x+bw+n+1),ey=Math.min(1200,y+bh+n+1);Mat region=labels.submat(sy,ey,sx,ex),selected=new Mat(),kernel=Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new Size(1+n,1+n)),nonzero=new Mat();MatOfPoint2f points=new MatOfPoint2f();try{Core.compare(region,new Scalar(k),selected,Core.CMP_EQ);Imgproc.dilate(selected,selected,kernel);Core.findNonZero(selected,nonzero);if(nonzero.empty())continue;nonzero.convertTo(points,CvType.CV_32F);RotatedRect rect=Imgproc.minAreaRect(points);Point[] p=new Point[4];rect.points(p);double[] q=new double[8];double minY=bottom,maxY=0,minX=originalW,maxX=0;for(int i=0;i<4;i++){q[i*2]=Math.max(0,Math.min(originalW,(p[i].x+sx)*originalW/1200.));q[i*2+1]=Math.max(top,Math.min(bottom,top+(p[i].y+sy)*originalH/1200.));minY=Math.min(minY,q[i*2+1]);maxY=Math.max(maxY,q[i*2+1]);minX=Math.min(minX,q[i*2]);maxX=Math.max(maxX,q[i*2]);}if(maxY-minY<1||maxX-minX<1)continue;
                    // Keep rotated quadrilateral; expand the upper/lower sides slightly, as upstream.
                    double cx=0,cy=0;for(int i=0;i<4;i++){cx+=q[i*2]/4;cy+=q[i*2+1]/4;}final double centerX=cx,centerY=cy;Integer[] order={0,1,2,3};Arrays.sort(order,Comparator.comparingDouble(i->Math.atan2(q[i*2+1]-centerY,q[i*2]-centerX)));int first=0;for(int i=1;i<4;i++)if(q[order[i]*2]+q[order[i]*2+1]<q[order[first]*2]+q[order[first]*2+1])first=i;JSONArray box=new JSONArray();for(int j=0;j<4;j++){int i=order[(first+j)%4];double yy=q[i*2+1];if(maxY-minY<3*(maxX-minX))yy=Math.max(top,Math.min(bottom,cy+(yy-cy)*1.1));box.put(q[i*2]).put(yy);}result.put(new JSONObject().put("box",box).put("confidence",max));
                }finally{region.release();selected.release();kernel.release();nonzero.release();points.release();}
            }return result;
        }finally{small.release();map.release();mask.release();labels.release();stats.release();centroids.release();}
    }
    @Override public void close()throws Exception {session.close();}
}
