package dev.pocketllama.photo;

import java.util.*;
import java.nio.FloatBuffer;

/** DB components, convex hull, minimum-area boxes and bounded CTC decoding. */
public final class OcrGeometry {
    public static final class Box {
        public final double[] points;public final double width,height;public final float score;
        public Box(double[] p,double w,double h,float s){points=p;width=w;height=h;score=s;}
    }
    public static final class Text {public final String text;public final float score;Text(String t,float s){text=t;score=s;}}
    private OcrGeometry(){}
    public static Text decode(FloatBuffer data,int steps,int classes,List<String> alphabet){
        if(classes!=alphabet.size()+2||steps<1||data.remaining()!=(long)steps*classes)throw new IllegalArgumentException("OCR: словарь не совпадает с моделью");
        StringBuilder text=new StringBuilder();float sum=0;int count=0,last=-1;int base=data.position();
        for(int t=0;t<steps;t++){int index=0;float best=-Float.MAX_VALUE;for(int c=0;c<classes;c++){float value=data.get(base+t*classes+c);if(value>best){best=value;index=c;}}
            if(index!=0&&index!=last){text.append(index==classes-1?" ":alphabet.get(index-1));sum+=best;count++;}last=index;
        }return new Text(text.toString().trim(),count==0?0:sum/count);
    }
    public static List<Box> detect(float[] map,int w,int h,int originalW,int originalH)throws InterruptedException {
        if(map.length!=(long)w*h||map.length>2560000)throw new IllegalArgumentException("Размер карты OCR");
        boolean[] seen=new boolean[map.length];int[] queue=new int[map.length];ArrayList<Box> boxes=new ArrayList<>();int components=0;
        for(int seed=0;seed<map.length;seed++){
            if((seed&16383)==0&&Thread.currentThread().isInterrupted())throw new InterruptedException();
            if(seen[seed]||map[seed]<.3f)continue;if(++components>6000)throw new IllegalArgumentException("Слишком сложное фото. Приблизьте нужный участок");
            int head=0,tail=1;queue[0]=seed;seen[seed]=true;ArrayList<double[]> boundary=new ArrayList<>();
            while(head<tail){int i=queue[head++],x=i%w,y=i/w;boolean edge=false;
                for(int d=0;d<4;d++){int nx=x+(d==0?-1:d==1?1:0),ny=y+(d==2?-1:d==3?1:0);if(nx<0||ny<0||nx>=w||ny>=h){edge=true;continue;}int n=ny*w+nx;if(map[n]<.3f){edge=true;continue;}if(!seen[n]){seen[n]=true;queue[tail++]=n;}}
                if(edge)boundary.add(new double[]{x,y});
            }
            if(tail<5||boundary.size()<4)continue;
            List<double[]> hull=hull(boundary);double best=Double.MAX_VALUE,bux=1,buy=0,minU=0,maxU=0,minV=0,maxV=0;
            for(int i=0;i<hull.size();i++){double[] a=hull.get(i),b=hull.get((i+1)%hull.size());double dx=b[0]-a[0],dy=b[1]-a[1],length=Math.hypot(dx,dy);if(length==0)continue;double ux=dx/length,uy=dy/length,loU=Double.MAX_VALUE,hiU=-loU,loV=loU,hiV=-loU;
                for(double[] p:hull){double u=p[0]*ux+p[1]*uy,v=-p[0]*uy+p[1]*ux;loU=Math.min(loU,u);hiU=Math.max(hiU,u);loV=Math.min(loV,v);hiV=Math.max(hiV,v);}
                double area=(hiU-loU)*(hiV-loV);if(area<best){best=area;bux=ux;buy=uy;minU=loU;maxU=hiU;minV=loV;maxV=hiV;}
            }
            double rw=maxU-minU,rh=maxV-minV;if(Math.min(rw,rh)<2||best<6)continue;
            double cx=(minU+maxU)/2*bux-(minV+maxV)/2*buy,cy=(minU+maxU)/2*buy+(minV+maxV)/2*bux;
            // Score the unexpanded rectangle, not only the positive component pixels.
            int left=w-1,right=0,top=h-1,bottom=0;for(double[] p:hull){left=Math.min(left,(int)p[0]);right=Math.max(right,(int)p[0]);top=Math.min(top,(int)p[1]);bottom=Math.max(bottom,(int)p[1]);}
            double sum=0;int n=0;for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++){double u=x*bux+y*buy,v=-x*buy+y*bux;if(u>=minU&&u<=maxU&&v>=minV&&v<=maxV){sum+=map[y*w+x];n++;}}
            float score=(float)(sum/Math.max(1,n));if(score<.6f)continue;
            double expand=best*1.5/(2*(rw+rh));rw+=2*expand;rh+=2*expand;
            // Orient long text axis left-to-right; vertical lines are read top-to-bottom.
            double ux=bux,uy=buy;if(rh>rw){double t=rw;rw=rh;rh=t;ux=-buy;uy=bux;}
            if(Math.abs(ux)>=Math.abs(uy)?ux<0:uy<0){ux=-ux;uy=-uy;}
            double vx=-uy,vy=ux,sx=(double)originalW/w,sy=(double)originalH/h;
            double[] points=new double[8];int[] signs={-1,-1,1,-1,1,1,-1,1};for(int k=0;k<4;k++){points[k*2]=Math.max(0,Math.min(originalW-1,(cx+signs[k*2]*rw/2*ux+signs[k*2+1]*rh/2*vx)*sx));points[k*2+1]=Math.max(0,Math.min(originalH-1,(cy+signs[k*2]*rw/2*uy+signs[k*2+1]*rh/2*vy)*sy));}
            double bw=Math.hypot(points[2]-points[0],points[3]-points[1]),bh=Math.hypot(points[6]-points[0],points[7]-points[1]);if(bw>3&&bh>3)boxes.add(new Box(points,bw,bh,score));
            if(boxes.size()>1000)throw new IllegalArgumentException("Больше 1000 строк. Разделите документ на фото");
        }
        boxes.sort(Comparator.comparingDouble((Box b)->b.points[1]).thenComparingDouble(b->b.points[0]));
        // Stable insertion within a row, without a non-transitive Comparator.
        for(int i=1;i<boxes.size();i++)for(int j=i;j>0;j--){Box a=boxes.get(j-1),b=boxes.get(j);if(Math.abs(a.points[1]-b.points[1])<Math.min(a.height,b.height)*.5&&a.points[0]>b.points[0])Collections.swap(boxes,j,j-1);else break;}
        return boxes;
    }
    private static double cross(double[] o,double[] a,double[] b){return(a[0]-o[0])*(b[1]-o[1])-(a[1]-o[1])*(b[0]-o[0]);}
    private static List<double[]> hull(List<double[]> points){points.sort(Comparator.comparingDouble((double[] p)->p[0]).thenComparingDouble(p->p[1]));ArrayList<double[]> h=new ArrayList<>();for(double[] p:points){while(h.size()>1&&cross(h.get(h.size()-2),h.get(h.size()-1),p)<=0)h.remove(h.size()-1);h.add(p);}int lower=h.size();for(int i=points.size()-2;i>=0;i--){double[] p=points.get(i);while(h.size()>lower&&cross(h.get(h.size()-2),h.get(h.size()-1),p)<=0)h.remove(h.size()-1);h.add(p);}if(h.size()>1)h.remove(h.size()-1);return h;}
}
