package cn.screenqa.lite;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.FrameMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Real emulator rendering and finger-event verification; no AI request or overlay startup. */
public final class ReasoningArtworkUiTest extends Instrumentation {
    private MainActivity activity;
    private ReasoningControls controls;
    private ReasoningArtwork artwork;
    private View depth, speed;
    private ReasoningCard card;
    private Bitmap[] cached;
    private Throwable problem;
    private final List<Long> frames = Collections.synchronizedList(new ArrayList<>());
    private final List<Long> drawFrames = Collections.synchronizedList(new ArrayList<>());
    private final List<Long> baselineFrames = Collections.synchronizedList(new ArrayList<>());
    private boolean withoutArt;
    private File output;

    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            verify();
            List<Long> sorted;
            synchronized (frames) { sorted = new ArrayList<>(frames); }
            Collections.sort(sorted);
            long slow = sorted.stream().filter(n -> n > 16_666_667).count();
            double p95 = sorted.isEmpty() ? 0 : sorted.get((int)((sorted.size()-1)*.95))/1e6;
            long memory = 0; for (Bitmap b : cached) memory += b.getAllocationByteCount();
            List<Long> draws=new ArrayList<>(drawFrames);Collections.sort(draws);
            List<Long> baseline=new ArrayList<>(baselineFrames);Collections.sort(baseline);
            double drawP95=draws.get((int)((draws.size()-1)*.95))/1e6;
            double baselineP95=baseline.get((int)((baseline.size()-1)*.95))/1e6;
            result.putString("stream", "Artwork UI: PASS; all adjacent pairs forward/reverse, both rails, " +
                    "fractional screenshots, frame-by-frame progress, mid-snap reversal, shared snap, " +
                    "rapid 1-5-1, process cache reuse, reduced motion.\n" +
                    "Measured render frames=" + sorted.size() + ", p95=" + p95 + "ms, >16.67ms=" + slow +
                    ", UI draw p95="+drawP95+"ms; same-page without-art p95="+baselineP95+"ms"+
                    "; decoded bitmap bytes=" + memory + "\n" +
                    "Controls=" + controls.getWidth() + "x" + controls.getHeight() +
                    ", reserved=" + controls.futureVisualContainer.getWidth() + "px\n");
            finish(-1, result);
        } catch (Throwable error) {
            result.putString("stream", android.util.Log.getStackTraceString(error));
            finish(0, result);
        }
    }
    private static Object field(Object target, String name) throws Exception {
        Field f=target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    private static <T extends View> T find(View view, Class<T> type) {
        if(type.isInstance(view))return type.cast(view);
        if(view instanceof ViewGroup) {ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){T found=find(group.getChildAt(i),type);if(found!=null)return found;}}
        return null;
    }
    private static void require(boolean ok, String message) { if(!ok)throw new AssertionError(message); }
    private void ui(Runnable action) {
        problem=null; runOnMainSync(()->{try {action.run();}catch(Throwable error){problem=error;}});
        if(problem!=null)throw new AssertionError(problem);
    }
    private void pointer(View rail, int action, float value) {
        int inset=Ui.dp(getTargetContext(),12);
        float display=rail==speed?6-value:value;
        float x=inset+(rail.getWidth()-2*inset)*(display-1)/4;
        MotionEvent event=MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis(),action,x,rail.getHeight()/2f,0);
        rail.dispatchTouchEvent(event);event.recycle();
        if(action!=MotionEvent.ACTION_CANCEL)assertProgress(value);
    }
    private void assertProgress(float expected) {
        try {
            require(Math.abs(controls.reasoningProgress()-expected)<.001, "Rail did not follow finger");
            require(Math.abs((Float)field(artwork,"progress")-expected)<.001,"Artwork lagged rail");
            require(field(artwork,"images")== (withoutArt?null:cached),"Drag changed decoded cache");
        }catch(Exception error){throw new AssertionError(error);}
    }
    private void screenshot(String name) throws Exception {
        waitForIdleSync();SystemClock.sleep(40);
        Bitmap shot=getUiAutomation().takeScreenshot();
        try(FileOutputStream out=new FileOutputStream(new File(output,name+".png"))){shot.compress(Bitmap.CompressFormat.PNG,100,out);}
        shot.recycle();
        ui(()->{
            Bitmap strip=Bitmap.createBitmap(card.getWidth(),card.getHeight(),Bitmap.Config.ARGB_8888);
            card.draw(new Canvas(strip));
            require(strip.getPixel(0,0)==0 && strip.getPixel(strip.getWidth()-1,0)==0
                    && strip.getPixel(0,strip.getHeight()-1)==0 && strip.getPixel(strip.getWidth()-1,strip.getHeight()-1)==0,
                    "Artwork escaped the card's rounded corners");
            try(FileOutputStream out=new FileOutputStream(new File(output,name+"-card.png"))){strip.compress(Bitmap.CompressFormat.PNG,100,out);}
            catch(Exception error){throw new AssertionError(error);}finally{strip.recycle();}
        });
    }
    private void sweep(View rail, float start, float end, int count) throws Exception {
        CountDownLatch done=new CountDownLatch(1);
        ui(()->{
            pointer(rail,MotionEvent.ACTION_DOWN,start);
            Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback(){
                int index;
                @Override public void doFrame(long time) {
                    try{
                        float value=start+(end-start)*(++index)/(float)count;
                        pointer(rail,MotionEvent.ACTION_MOVE,value);
                        if(index<count)Choreographer.getInstance().postFrameCallback(this);
                        else{pointer(rail,MotionEvent.ACTION_UP,end);done.countDown();}
                    }catch(Throwable error){problem=error;done.countDown();}
                }
            });
        });
        require(done.await(15,TimeUnit.SECONDS),"Sweep timed out");
        if(problem!=null)throw new AssertionError(problem);
    }
    private void verify() throws Exception {
        Settings settings=new Settings(getTargetContext());
        settings.prefs.edit().putInt(UsageDeclaration.KEY,UsageDeclaration.REVISION).putBoolean("reduce_motion",false).commit();
        settings.setReasoningLevel(3);
        activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitForIdleSync();
        controls=find(activity.getWindow().getDecorView(),ReasoningControls.class);
        require(controls!=null,"Missing reasoning controls");
        require(controls.getParent() instanceof ReasoningCard,"Artwork is not a full-card layer");
        card=(ReasoningCard)controls.getParent();
        artwork=(ReasoningArtwork)field(controls,"artwork");
        require(field(artwork,"host")==card,"Scene does not use the entire card bounds");
        depth=(View)field(controls,"depth");speed=(View)field(controls,"speed");
        for(int attempt=0;attempt<100;attempt++){
            ui(()->{try{cached=(Bitmap[])field(artwork,"images");}catch(Exception e){throw new AssertionError(e);}});
            if(cached!=null)break;SystemClock.sleep(20);
        }
        require(cached!=null && cached.length==11,"Preload failed");
        for(int i=0;i<cached.length;i++)require(cached[i].getWidth()==(i<6?416:512)
                && cached[i].getHeight()==(i<6?832:320),"Oversized bitmap");
        output=new File(getTargetContext().getExternalFilesDir(null),"reasoning-immersive-test");output.mkdirs();
        for(int quarter=4;quarter<=20;quarter++) {
            final float value=quarter/4f;
            ui(()->pointer(depth,MotionEvent.ACTION_DOWN,value));
            screenshot(String.format(java.util.Locale.US,"progress-%.2f",value));
            ui(()->pointer(depth,MotionEvent.ACTION_UP,value));
            SystemClock.sleep(220);
            ui(()->assertProgress(Math.round(value)));
        }
        HandlerThread metrics=new HandlerThread("art-frame-metrics");metrics.start();
        android.view.Window.OnFrameMetricsAvailableListener listener=(window,frame,dropped)->{
            frames.add(frame.getMetric(FrameMetrics.TOTAL_DURATION));drawFrames.add(frame.getMetric(FrameMetrics.DRAW_DURATION));};
        ui(()->activity.getWindow().addOnFrameMetricsAvailableListener(listener,new Handler(metrics.getLooper())));
        for(int i=1;i<5;i++){sweep(depth,i,i+1,24);sweep(depth,i+1,i,24);}
        for(int repeat=0;repeat<6;repeat++){sweep(repeat%2==0?depth:speed,1,5,12);sweep(repeat%2==0?depth:speed,5,1,12);}
        ui(()->activity.getWindow().removeOnFrameMetricsAvailableListener(listener));
        android.view.Window.OnFrameMetricsAvailableListener baselineListener=(window,frame,dropped)->baselineFrames.add(frame.getMetric(FrameMetrics.TOTAL_DURATION));
        ui(()->{
            try{Field f=artwork.getClass().getDeclaredField("images");f.setAccessible(true);f.set(artwork,null);withoutArt=true;}
            catch(Exception error){throw new AssertionError(error);}
            activity.getWindow().addOnFrameMetricsAvailableListener(baselineListener,new Handler(metrics.getLooper()));
        });
        for(int repeat=0;repeat<6;repeat++){sweep(depth,1,5,12);sweep(depth,5,1,12);}
        ui(()->{
            activity.getWindow().removeOnFrameMetricsAvailableListener(baselineListener);
            try{Field f=artwork.getClass().getDeclaredField("images");f.setAccessible(true);f.set(artwork,cached);withoutArt=false;}
            catch(Exception error){throw new AssertionError(error);}
        });metrics.quitSafely();
        // Every snap frame must drive the decoration and both rails together.
        CountDownLatch snapped=new CountDownLatch(1);
        ui(()->{
            pointer(depth,MotionEvent.ACTION_DOWN,2.37f);pointer(depth,MotionEvent.ACTION_UP,2.37f);
            Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback(){
                int count;
                @Override public void doFrame(long time){
                    try{assertProgress(controls.reasoningProgress());
                        if(++count<18)Choreographer.getInstance().postFrameCallback(this);
                        else{assertProgress(2);snapped.countDown();}}
                    catch(Throwable error){problem=error;snapped.countDown();}
                }
            });
        });
        require(snapped.await(5,TimeUnit.SECONDS),"Snap timed out");if(problem!=null)throw new AssertionError(problem);
        ui(()->{pointer(depth,MotionEvent.ACTION_DOWN,4.4f);pointer(depth,MotionEvent.ACTION_UP,4.4f);});
        SystemClock.sleep(60);
        ui(()->{pointer(depth,MotionEvent.ACTION_DOWN,3.8f);pointer(depth,MotionEvent.ACTION_MOVE,3.2f);pointer(depth,MotionEvent.ACTION_UP,3);});
        SystemClock.sleep(240);ui(()->assertProgress(3));
        // A recreated/theme-rebuilt control uses the identical process cache.
        ui(()->{
            ReasoningControls other=new ReasoningControls(activity,settings,ThemePalette.BLACK_GOLD);
            ((ViewGroup)controls.getParent()).addView(other);
            other.setVisibility(View.GONE);
        });
        waitForIdleSync();
        ui(()->{
            ViewGroup parent=(ViewGroup)controls.getParent();ReasoningControls other=(ReasoningControls)parent.getChildAt(parent.getChildCount()-1);
            try{require(field(field(other,"artwork"),"images")==cached,"Control rebuilt the bitmap cache");}catch(Exception e){throw new AssertionError(e);}
            parent.removeView(other);
        });
        // Reduced motion preserves fractional crossfade but settles immediately, without transforms.
        settings.setReduceMotion(true);
        ReasoningControls[] reduced={null};
        ui(()->{
            reduced[0]=new ReasoningControls(activity,settings,ThemePalette.BLACK_GOLD);
            ((ViewGroup)controls.getParent()).addView(reduced[0]);
        });waitForIdleSync();
        ui(()->{
            try{
                View rail=(View)field(reduced[0],"depth");int inset=Ui.dp(getTargetContext(),12);
                float x=inset+(rail.getWidth()-2*inset)*(4.3f-1)/4;
                for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){
                    MotionEvent event=MotionEvent.obtain(0,SystemClock.uptimeMillis(),action,x,rail.getHeight()/2f,0);rail.dispatchTouchEvent(event);event.recycle();
                    float value=action==MotionEvent.ACTION_DOWN?4.3f:4;
                    require(Math.abs(reduced[0].reasoningProgress()-value)<.001,"Reduced-motion progress/snap failed");
                    require(Math.abs((Float)field(field(reduced[0],"artwork"),"progress")-value)<.001,"Reduced-motion art lagged");
                }
                ((ViewGroup)reduced[0].getParent()).removeView(reduced[0]);
            }catch(Exception error){throw new AssertionError(error);}
        });settings.setReduceMotion(false);
        ui(()->pointer(depth,MotionEvent.ACTION_DOWN,5));screenshot("final-home");ui(()->pointer(depth,MotionEvent.ACTION_UP,5));
    }
}
