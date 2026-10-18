package dev.pocketllama.photo;
import android.graphics.*;
import java.io.*;
import org.json.*;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

/** Non-destructive perspective/rotation/filter recipe. Only encrypted originals persist. */
public final class ScanImages {
    private ScanImages(){}
    public static Bitmap render(byte[] bytes,JSONObject page,int side)throws Exception {
        Bitmap source=PhotoImages.decode(bytes,Math.min(4096,Math.max(480,side))),result=null;
        try {JSONObject scan=page.optJSONObject("scan");double[] q=scan==null?ScanGeometry.full():ScanGeometry.read(scan.optJSONArray("corners"));int[] size=ScanGeometry.size(q,source.getWidth(),source.getHeight());
            float[] from=new float[8],to={0,0,size[0],0,size[0],size[1],0,size[1]};for(int i=0;i<4;i++){from[i*2]=(float)(q[i*2]*source.getWidth());from[i*2+1]=(float)(q[i*2+1]*source.getHeight());}
            Matrix map=new Matrix();if(!map.setPolyToPoly(from,0,to,0,4))throw new IOException("Не удалось выпрямить страницу. Измените углы рамки");result=Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(result);canvas.drawColor(Color.WHITE);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);canvas.drawBitmap(source,map,paint);
            int rotation=page.optInt("rotation")%4;if(rotation!=0){Matrix m=new Matrix();m.postRotate(rotation*90);Bitmap rotated=Bitmap.createBitmap(result,0,0,result.getWidth(),result.getHeight(),m,true);if(rotated!=result){result.recycle();result=rotated;}}
            String filter=scan==null?"color":scan.optString("filter","color");if(filter.equals("gray")){ColorMatrix cm=new ColorMatrix();cm.setSaturation(0);paint.setColorFilter(new ColorMatrixColorFilter(cm));Bitmap gray=Bitmap.createBitmap(result.getWidth(),result.getHeight(),Bitmap.Config.ARGB_8888);new Canvas(gray).drawBitmap(result,0,0,paint);result.recycle();result=gray;}
            else if(filter.equals("bw")){DocumentEdges.load();Mat rgba=new Mat(),gray=new Mat();try{Utils.bitmapToMat(result,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);Imgproc.adaptiveThreshold(gray,gray,255,Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,Imgproc.THRESH_BINARY,51,15);Utils.matToBitmap(gray,result);}finally{rgba.release();gray.release();}}
            Bitmap out=result;result=null;return out;
        }finally{source.recycle();if(result!=null)result.recycle();}
    }
    public static byte[] jpeg(Bitmap bitmap,int quality)throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();if(!bitmap.compress(Bitmap.CompressFormat.JPEG,quality,out))throw new IOException("Не удалось подготовить страницу");return out.toByteArray();}
    public static OcrRaster raster(Bitmap b){int[] p=new int[b.getWidth()*b.getHeight()];b.getPixels(p,0,b.getWidth(),0,0,b.getWidth(),b.getHeight());return new OcrRaster(b.getWidth(),b.getHeight(),p);}
    public static int[] dimensions(JSONObject p)throws Exception {JSONObject scan=p.optJSONObject("scan");int[] s=ScanGeometry.size(scan==null?ScanGeometry.full():ScanGeometry.read(scan.optJSONArray("corners")),p.getInt("width"),p.getInt("height"));return p.optInt("rotation")%2==0?s:new int[]{s[1],s[0]};}
}
