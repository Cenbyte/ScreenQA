package cn.screenqa.lite;

/** Only bounded default-display taps and swipes may reach either input backend. */
final class TouchAction {
    final int x,y,endX,endY,duration,width,height;
    final boolean swipe;
    private TouchAction(int x,int y,int endX,int endY,int duration,int width,int height,boolean swipe){
        RootCommands.tap(x,y,width,height);RootCommands.tap(endX,endY,width,height);
        if(duration<40||duration>1000)throw new IllegalArgumentException("Invalid touch duration");
        this.x=x;this.y=y;this.endX=endX;this.endY=endY;this.duration=duration;
        this.width=width;this.height=height;this.swipe=swipe;
    }
    static TouchAction tap(int x,int y,int width,int height){return new TouchAction(x,y,x,y,70,width,height,false);}
    static TouchAction swipe(int x,int y,int endX,int endY,int duration,int width,int height){
        return new TouchAction(x,y,endX,endY,duration,width,height,true);
    }
    String command(){return swipe?RootCommands.swipe(x,y,endX,endY,duration,width,height):RootCommands.tap(x,y,width,height);}
}
