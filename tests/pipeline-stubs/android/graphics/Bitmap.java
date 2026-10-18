package android.graphics;
public final class Bitmap {
 private final int width,height;public boolean recycled;
 public Bitmap(int w,int h){width=w;height=h;}public int getWidth(){return width;}public int getHeight(){return height;}
 public static Bitmap createBitmap(Bitmap b,int x,int y,int w,int h){if(x<0||y<0||w<1||h<1||x+w>b.width||y+h>b.height)throw new IllegalArgumentException();return new Bitmap(w,h);}
 public static Bitmap createScaledBitmap(Bitmap b,int w,int h,boolean filter){return new Bitmap(w,h);}
 public void recycle(){recycled=true;}
}
