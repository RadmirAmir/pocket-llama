package dev.pocketllama.photo;

/** Small, platform-independent raster. No full-size float image or image-wide JS/base64 copies. */
public final class OcrRaster {
    public final int width,height;public final int[] pixels;
    public OcrRaster(int width,int height,int[] pixels){if(width<1||height<1||(long)width*height!=pixels.length||pixels.length>12000000)throw new IllegalArgumentException("Размер фото");this.width=width;this.height=height;this.pixels=pixels;}
    public OcrRaster rotate(int quarterTurns){
        int turns=((quarterTurns%4)+4)%4;if(turns==0)return this;
        int w=turns%2==0?width:height,h=turns%2==0?height:width;int[] out=new int[pixels.length];
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            int dx=turns==1?height-1-y:turns==2?width-1-x:y;
            int dy=turns==1?x:turns==2?height-1-y:width-1-x;out[dy*w+dx]=pixels[y*width+x];
        }return new OcrRaster(w,h,out);
    }
    private float channel(double x,double y,int shift){
        x=Math.max(0,Math.min(width-1,x));y=Math.max(0,Math.min(height-1,y));int a=(int)x,b=(int)y,c=Math.min(width-1,a+1),d=Math.min(height-1,b+1);double fx=x-a,fy=y-b;
        return (float)((((pixels[b*width+a]>>shift)&255)*(1-fx)+((pixels[b*width+c]>>shift)&255)*fx)*(1-fy)+(((pixels[d*width+a]>>shift)&255)*(1-fx)+((pixels[d*width+c]>>shift)&255)*fx)*fy);
    }
    public float[] detector(int w,int h){
        float[] out=new float[3*w*h];double[] mean={.485,.456,.406},std={.229,.224,.225};
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)for(int c=0;c<3;c++)out[c*w*h+y*w+x]=(float)((channel((x+.5)*width/w-.5,(y+.5)*height/h-.5,c*8)/255.-mean[c])/std[c]);
        return out;
    }
    public float[] recognizer(OcrGeometry.Box box,int contentWidth,int paddedWidth){
        float[] out=new float[3*48*paddedWidth];double[] p=box.points;
        for(int y=0;y<48;y++)for(int x=0;x<contentWidth;x++){
            double u=(x+.5)/contentWidth,v=(y+.5)/48;
            double sx=(p[0]*(1-u)+p[2]*u)*(1-v)+(p[6]*(1-u)+p[4]*u)*v;
            double sy=(p[1]*(1-u)+p[3]*u)*(1-v)+(p[7]*(1-u)+p[5]*u)*v;
            for(int c=0;c<3;c++)out[c*48*paddedWidth+y*paddedWidth+x]=channel(sx,sy,c*8)/127.5f-1;
        }return out;
    }
}
