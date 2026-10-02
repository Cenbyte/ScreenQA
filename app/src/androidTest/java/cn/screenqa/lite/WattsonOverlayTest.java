package cn.screenqa.lite;

import android.app.Instrumentation;
import android.os.Bundle;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;

/** Runs the actual overlay View/resources on an emulator, without an AI account or API calls. */
public class WattsonOverlayTest extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{testTransparentLoopTapDragAndResourceLifetime();result.putString("stream","Wattson overlay: PASS (tap, idle, drag, transparency, 180s resource lifetime)\n");finish(-1,result);}
        catch(Throwable failure){result.putString("stream",android.util.Log.getStackTraceString(failure));finish(0,result);}
    }
    private Instrumentation getInstrumentation(){return this;}
    private static void assertTrue(boolean value){if(!value)throw new AssertionError("Expected true");}
    private static void assertFalse(boolean value){assertTrue(!value);}
    private static void assertEquals(int a,int b){if(a!=b)throw new AssertionError(a+" != "+b);}
    private static void assertSame(Object a,Object b){if(a!=b)throw new AssertionError("Bitmap was replaced");}
    private static void assertNull(Object a){if(a!=null)throw new AssertionError("Bitmap not released");}
    private NextOverlayView pet;
    private WindowManager windows;
    private WindowManager.LayoutParams params;
    private int clicks,moves;
    private volatile Throwable problem;
    private void ui(Runnable action){getInstrumentation().runOnMainSync(()->{try{action.run();}catch(Throwable e){problem=e;}});if(problem!=null)throw new AssertionError(problem);}
    private PetAnimation timeline() throws Exception {Field f=NextOverlayView.class.getDeclaredField("animation");f.setAccessible(true);return (PetAnimation)f.get(pet);}
    private Object field(String name)throws Exception{Field f=NextOverlayView.class.getDeclaredField(name);f.setAccessible(true);return f.get(pet);}
    private void snapshot(String name)throws Exception{
        Bitmap screen=getInstrumentation().getUiAutomation().takeScreenshot();
        File file=new File(getInstrumentation().getTargetContext().getExternalFilesDir(null),name+".png");
        try(FileOutputStream out=new FileOutputStream(file)){screen.compress(Bitmap.CompressFormat.PNG,100,out);}screen.recycle();
    }
    private void pointer(int action,float x,float y,long down){
        MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);
        getInstrumentation().sendPointerSync(e);e.recycle();
    }
    public void testTransparentLoopTapDragAndResourceLifetime()throws Exception{
        Context context=getInstrumentation().getTargetContext();windows=context.getSystemService(WindowManager.class);
        int side=Math.round(48*context.getResources().getDisplayMetrics().density);
        params=new WindowManager.LayoutParams(side,side,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.LEFT;params.x=500;params.y=900;
        try{
            ui(()->{
                pet=new NextOverlayView(context,(x,y)->{moves++;params.x+=x;params.y+=y;windows.updateViewLayout(pet,params);});
                pet.setOnClickListener(v->clicks++);windows.addView(pet,params);
            });
            SystemClock.sleep(750);getInstrumentation().waitForIdleSync();snapshot("wattson-idle");
            Object originalIdle=field("idleAtlas"),originalTap=field("tapAtlas");
            ui(()->{int w=pet.getWidth(),h=pet.getHeight();Bitmap rendered=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);pet.draw(new Canvas(rendered));
                try(FileOutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),"wattson-rendered.png"))){rendered.compress(Bitmap.CompressFormat.PNG,100,out);}catch(Exception e){throw new RuntimeException(e);}
                android.util.Log.e("WATTSON_CHECK","size="+w+"x"+h+" corners="+Integer.toHexString(rendered.getPixel(0,0))+","+Integer.toHexString(rendered.getPixel(w-1,h-1)));
                assertEquals(0,rendered.getPixel(0,0)>>>24);assertEquals(0,rendered.getPixel(w-1,h-1)>>>24);rendered.recycle();});
            int[] location=new int[2];ui(()->pet.getLocationOnScreen(location));
            long down=SystemClock.uptimeMillis();pointer(MotionEvent.ACTION_DOWN,location[0]+pet.getWidth()/2,location[1]+pet.getHeight()/2,down);
            pointer(MotionEvent.ACTION_UP,location[0]+pet.getWidth()/2,location[1]+pet.getHeight()/2,down);getInstrumentation().waitForIdleSync();
            assertEquals(1,clicks);assertTrue(timeline().tapping(SystemClock.uptimeMillis()));
            SystemClock.sleep(480);snapshot("wattson-tap");
            SystemClock.sleep(1050);assertFalse(timeline().tapping(SystemClock.uptimeMillis()));snapshot("wattson-restored");
            ui(()->pet.getLocationOnScreen(location));
            down=SystemClock.uptimeMillis();float x=location[0]+pet.getWidth()/2,y=location[1]+pet.getHeight()/2;
            pointer(MotionEvent.ACTION_DOWN,x,y,down);
            for(int i=1;i<=6;i++){pointer(MotionEvent.ACTION_MOVE,x-i*15,y+i*10,down);SystemClock.sleep(20);}
            pointer(MotionEvent.ACTION_UP,x-90,y+60,down);getInstrumentation().waitForIdleSync();
            assertTrue(moves>0);assertEquals(1,clicks);assertFalse(timeline().tapping(SystemClock.uptimeMillis()));
            // Three minutes of real looping; neither atlas is replaced or decoded per frame.
            for(int i=0;i<18;i++){SystemClock.sleep(10000);assertSame(originalIdle,field("idleAtlas"));assertSame(originalTap,field("tapAtlas"));}
            snapshot("wattson-long-running");
        }finally{if(pet!=null)ui(()->windows.removeView(pet));}
        getInstrumentation().waitForIdleSync();
        assertNull(field("idleAtlas"));assertNull(field("tapAtlas"));
    }
}
