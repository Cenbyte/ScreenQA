package cn.screenqa.lite;

import android.graphics.Rect;

/** Uses the captured content viewport and rejects the entire overlay-crossing path. */
final class TouchGeometry {
    static TouchAction scroll(int left,int top,int right,int bottom,Rect control,Rect popup){
        return scroll(left,top,right,bottom,new int[][]{{control.left,control.top,control.right,control.bottom},
                {popup.left,popup.top,popup.right,popup.bottom}});
    }
    static TouchAction scroll(int left,int top,int right,int bottom,int[][] overlays){
        int width=right-left,height=bottom-top;
        if(width<50||height<100)return null;
        int from=top+height*4/5,to=top+height*2/5;
        for(int fraction:new int[]{3,1,2}){
            int x=left+width*fraction/4;
            boolean safe=true;
            for(int[] box:overlays)
                if(box[0]<box[2]&&box[1]<box[3]&&x-8<box[2]&&x+9>box[0]&&to-8<box[3]&&from+9>box[1]){safe=false;break;}
            if(safe)return TouchAction.swipe(x,from,x,to,360,right,bottom);
        }
        return null;
    }
}
