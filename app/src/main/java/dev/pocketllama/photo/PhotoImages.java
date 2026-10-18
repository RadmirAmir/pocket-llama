package dev.pocketllama.photo;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import java.io.*;
import java.nio.ByteBuffer;

public final class PhotoImages {
    private PhotoImages(){}
    public static String importUri(Context c,Uri uri)throws Exception {
        try(InputStream in=c.getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Фото недоступно");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){if(out.size()+n>24*1024*1024)throw new IOException("Фото больше 24 МБ. Выберите меньший файл");out.write(b,0,n);}return save(c,out.toByteArray());}
    }
    public static Bitmap decode(byte[] bytes,int side)throws Exception {
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes)),(decoder,info,source)->{
            int w=info.getSize().getWidth(),h=info.getSize().getHeight();if(w<1||h<1||(long)w*h>200000000)throw new IllegalArgumentException("Слишком большое фото");
            double scale=Math.min(1,Math.min((double)side/Math.max(w,h),Math.sqrt(12000000./((double)w*h))));
            decoder.setTargetSize(Math.max(1,(int)Math.round(w*scale)),Math.max(1,(int)Math.round(h*scale)));decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
        });
    }
    public static String save(Context c,byte[] source)throws Exception {
        Bitmap decoded=decode(source,4096),white=null,thumb=null;
        try {Bitmap image=decoded;if(decoded.hasAlpha()){white=Bitmap.createBitmap(decoded.getWidth(),decoded.getHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(white);canvas.drawColor(Color.WHITE);canvas.drawBitmap(decoded,0,0,null);image=white;}
            ByteArrayOutputStream jpeg=new ByteArrayOutputStream();if(!image.compress(Bitmap.CompressFormat.JPEG,95,jpeg)||jpeg.size()>24*1024*1024)throw new IOException("Не удалось подготовить фото");
            double s=Math.min(1,480./Math.max(image.getWidth(),image.getHeight()));thumb=Bitmap.createScaledBitmap(image,Math.max(1,(int)Math.round(image.getWidth()*s)),Math.max(1,(int)Math.round(image.getHeight()*s)),true);
            ByteArrayOutputStream preview=new ByteArrayOutputStream();if(!thumb.compress(Bitmap.CompressFormat.JPEG,82,preview))throw new IOException("Не удалось подготовить превью");
            // Re-encoding strips location, original name and EXIF. Only these normalized bytes persist.
            return new PhotoStore(c).create(jpeg.toByteArray(),preview.toByteArray(),image.getWidth(),image.getHeight());
        }finally{if(thumb!=null&&thumb!=decoded&&thumb!=white)thumb.recycle();if(white!=null)white.recycle();decoded.recycle();}
    }
    public static OcrRaster raster(byte[] source,int rotation)throws Exception {Bitmap image=decode(source,4096);try{int[] pixels=new int[image.getWidth()*image.getHeight()];image.getPixels(pixels,0,image.getWidth(),0,0,image.getWidth(),image.getHeight());return new OcrRaster(image.getWidth(),image.getHeight(),pixels).rotate(rotation);}finally{image.recycle();}}
    public static byte[] preview(byte[] source,int rotation,int side)throws Exception {
        Bitmap decoded=decode(source,side),image=decoded;try{if(rotation%4!=0){Matrix matrix=new Matrix();matrix.postRotate(rotation*90);image=Bitmap.createBitmap(decoded,0,0,decoded.getWidth(),decoded.getHeight(),matrix,true);}ByteArrayOutputStream out=new ByteArrayOutputStream();if(!image.compress(Bitmap.CompressFormat.JPEG,88,out))throw new IOException("Фото недоступно");return out.toByteArray();}finally{if(image!=decoded)image.recycle();decoded.recycle();}
    }
}
