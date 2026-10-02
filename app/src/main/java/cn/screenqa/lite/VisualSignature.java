package cn.screenqa.lite;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.Image;
import java.nio.ByteBuffer;

/** Cheap comparison of already OCR-scanned text boxes with the current captured frame. */
final class VisualSignature {
    private static long sample(Rect box,Sampler sampler) {
        if(box.width()<3||box.height()<3)return 0;
        long hash=0xcbf29ce484222325L;
        for(int row=0;row<5;row++)for(int col=0;col<8;col++) {
            int x=box.left+(box.width()-1)*(2*col+1)/16;
            int y=box.top+(box.height()-1)*(2*row+1)/10;
            int rgb=sampler.rgb(x,y);
            int gray=((rgb>>16&255)*77+(rgb>>8&255)*150+(rgb&255)*29)>>8;
            hash=(hash^(gray>>5))*0x100000001b3L;
        }
        return hash;
    }
    interface Sampler {int rgb(int x,int y);}
    static long fromBitmap(Bitmap image,Rect absolute,Rect crop) {
        if(!new Rect(crop).contains(absolute))return 0;
        return sample(absolute,(x,y)->image.getPixel(x-crop.left,y-crop.top));
    }
    static long fromImage(Image image,Rect absolute) {
        if(absolute.left<0||absolute.top<0||absolute.right>image.getWidth()||absolute.bottom>image.getHeight())return 0;
        Image.Plane plane=image.getPlanes()[0];ByteBuffer buffer=plane.getBuffer();
        int stride=plane.getRowStride(),pixel=plane.getPixelStride();
        return sample(absolute,(x,y)->{
            int at=y*stride+x*pixel;
            return ((buffer.get(at)&255)<<16)|((buffer.get(at+1)&255)<<8)|(buffer.get(at+2)&255);
        });
    }
}
