package dev.pocketllama.photo;
import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.util.*;

/** Geometry only. This preview helper is independent of Surya's learned text detector. */
public final class DocumentEdges {
    private static boolean loaded;
    public static synchronized void load(){if(!loaded){System.loadLibrary("opencv_java4");loaded=true;}}
    public static double[] detect(Bitmap image)throws Exception {
        load();Mat rgba=new Mat(),gray=new Mat(),edges=new Mat(),hierarchy=new Mat(),kernel=new Mat();List<MatOfPoint> contours=new ArrayList<>();
        try {Utils.bitmapToMat(image,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);Imgproc.GaussianBlur(gray,gray,new Size(5,5),0);Imgproc.Canny(gray,edges,40,120);kernel.release();kernel=Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new Size(5,5));Imgproc.morphologyEx(edges,edges,Imgproc.MORPH_CLOSE,kernel);Imgproc.findContours(edges,contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_SIMPLE);
            double best=0;double[] quad=null;double total=image.getWidth()*(double)image.getHeight();
            contours.sort((a,b)->Double.compare(Imgproc.contourArea(b),Imgproc.contourArea(a)));
            for(int i=0;i<Math.min(30,contours.size());i++){MatOfPoint c=contours.get(i);double area=Imgproc.contourArea(c);if(area<total*.15||area>total*.985)continue;MatOfPoint2f points=new MatOfPoint2f(c.toArray()),approx=new MatOfPoint2f();MatOfPoint integer=new MatOfPoint();try{Imgproc.approxPolyDP(points,approx,.025*Imgproc.arcLength(points,true),true);Point[] p=approx.toArray();if(p.length!=4)continue;integer.fromArray(p);if(!Imgproc.isContourConvex(integer))continue;double[] q=new double[8];for(int k=0;k<4;k++){q[k*2]=Math.max(0,Math.min(1,p[k].x/image.getWidth()));q[k*2+1]=Math.max(0,Math.min(1,p[k].y/image.getHeight()));}q=ScanGeometry.order(q);double score=ScanGeometry.area(q);if(score>best){quad=q;best=score;}}catch(IllegalArgumentException ignored){}finally{points.release();approx.release();integer.release();}}
            return quad;
        }finally{for(Mat c:contours)c.release();rgba.release();gray.release();edges.release();hierarchy.release();kernel.release();}
    }
}
