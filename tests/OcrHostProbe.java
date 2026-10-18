import dev.pocketllama.photo.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.*;

/** Runs the very same Java preprocessing, ONNX calls, DB geometry and CTC decoder shipped to Android. */
public final class OcrHostProbe {
    public static void main(String[] args)throws Exception {
        BufferedImage im=ImageIO.read(new File(args[1]));int w=im.getWidth(),h=im.getHeight();int[] pixels=im.getRGB(0,0,w,h,null,0,w);
        OcrRaster image=new OcrRaster(w,h,pixels).rotate(args.length>2?Integer.parseInt(args[2]):0);long start=System.nanoTime();
        List<OcrEngine.Line> lines=new OcrEngine(new File(args[0])).recognize(image,(done,total,partial)->{});
        System.out.println("Elapsed ms: "+(System.nanoTime()-start)/1000000+"; lines: "+lines.size());
        for(OcrEngine.Line line:lines)System.out.println(String.format(Locale.ROOT,"%.4f %s",line.score,line.text));
    }
}
