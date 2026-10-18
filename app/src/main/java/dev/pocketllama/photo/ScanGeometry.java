package dev.pocketllama.photo;

import java.util.*;
import org.json.*;

/** Normalized source-image corners, independent of view size and camera orientation. */
public final class ScanGeometry {
    private ScanGeometry() { }
    public static double[] full() { return new double[]{0,0,1,0,1,1,0,1}; }
    public static double[] read(JSONArray array) {
        if(array==null)return full();
        if(array.length()!=8)throw new IllegalArgumentException("Укажите четыре угла документа");
        double[] q=new double[8];for(int i=0;i<8;i++)q[i]=array.optDouble(i,Double.NaN);
        validate(q);return q;
    }
    public static JSONArray json(double[] q) throws JSONException {JSONArray a=new JSONArray();for(double n:q)a.put(n);return a;}
    public static double area(double[] q) {double a=0;for(int i=0;i<4;i++){int j=(i+1)%4;a+=q[2*i]*q[2*j+1]-q[2*j]*q[2*i+1];}return a*.5;}
    public static void validate(double[] q) {
        if(q==null||q.length!=8)throw new IllegalArgumentException("Некорректная рамка");
        for(double n:q)if(!Double.isFinite(n)||n<0||n>1)throw new IllegalArgumentException("Углы должны находиться внутри фото");
        if(area(q)<.015)throw new IllegalArgumentException("Рамка слишком мала или углы пересекаются");
        for(int i=0;i<4;i++) {
            int j=(i+1)%4,k=(i+2)%4;
            double cross=(q[2*j]-q[2*i])*(q[2*k+1]-q[2*j+1])-(q[2*j+1]-q[2*i+1])*(q[2*k]-q[2*j]);
            if(cross<.00001||Math.hypot(q[2*j]-q[2*i],q[2*j+1]-q[2*i+1])<.025)throw new IllegalArgumentException("Разведите углы рамки и сохраните их порядок");
        }
    }
    public static double[] order(double[] points) {
        if(points.length!=8)throw new IllegalArgumentException("Четыре угла");
        double cx=0,cy=0;for(int i=0;i<4;i++){cx+=points[2*i]/4;cy+=points[2*i+1]/4;}
        final double x=cx,y=cy;Integer[] indices={0,1,2,3};Arrays.sort(indices,Comparator.comparingDouble(i->Math.atan2(points[2*i+1]-y,points[2*i]-x)));
        int start=0;double min=Double.MAX_VALUE;for(int i=0;i<4;i++){int k=indices[i];double sum=points[k*2]+points[k*2+1];if(sum<min){min=sum;start=i;}}
        double[] q=new double[8];for(int i=0;i<4;i++){int k=indices[(start+i)%4];q[i*2]=points[k*2];q[i*2+1]=points[k*2+1];}validate(q);return q;
    }
    public static double distance(double[] a,double[] b) {double n=0;for(int i=0;i<8;i++)n=Math.max(n,Math.abs(a[i]-b[i]));return n;}
    public static int[] size(double[] q,int width,int height) {
        validate(q);double w=Math.max(Math.hypot((q[2]-q[0])*width,(q[3]-q[1])*height),Math.hypot((q[4]-q[6])*width,(q[5]-q[7])*height));
        double h=Math.max(Math.hypot((q[6]-q[0])*width,(q[7]-q[1])*height),Math.hypot((q[4]-q[2])*width,(q[5]-q[3])*height));
        double scale=Math.min(1,Math.min(4096/Math.max(w,h),Math.sqrt(12000000/(w*h))));
        return new int[]{Math.max(1,(int)Math.round(w*scale)),Math.max(1,(int)Math.round(h*scale))};
    }
}
