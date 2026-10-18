package dev.pocketllama.photo;

/** Task-specific visual work. Full-page originals remain available for a detailed retry. */
public final class SuryaImageSize {
    public enum Task { PAGE, LAYOUT, BLOCK, TABLE }
    public static final int MIN_IMAGE_TOKENS=128,MAX_IMAGE_TOKENS=4096;
    private SuryaImageSize() { }
    public static int[] fit(int width,int height,Task task) {
        if(width<1||height<1)throw new IllegalArgumentException("Пустое изображение");
        long max=task==Task.PAGE?2097152L:task==Task.LAYOUT?1048576L:4194304L;
        double pixels=(double)width*height,scale=Math.min(1,Math.sqrt(max/pixels));
        if(pixels<50176)scale=Math.sqrt(50176/pixels);
        // Extreme narrow crops should not produce gigantic intermediate bitmaps.
        scale=Math.min(scale,(8192/28*28.0)/Math.max(width,height));
        int w=Math.max(28,(int)Math.round(width*scale/28)*28),h=Math.max(28,(int)Math.round(height*scale/28)*28);
        while((long)w*h>max){if(w>=h)w-=28;else h-=28;}
        return new int[]{w,h};
    }
}
