package cn.screenqa.lite;

import android.app.Instrumentation;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Isolated native UI test runner. No OCR, overlay, permissions or AI network requests. */
public final class ReasoningUiTest extends Instrumentation {
    private MainActivity activity;
    private Settings settings;
    private Throwable problem;
    private boolean restartCheck;
    private boolean pipelineCheck;
    @Override public void onCreate(Bundle args){super.onCreate(args);restartCheck="true".equals(args.getString("restart"));pipelineCheck="true".equals(args.getString("pipeline"));start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        if(pipelineCheck){
            try{ReasoningPipelineProbe.run(getTargetContext());result.putString("stream","AI pipeline: PASS (five fixed-fast locators, five answer policies, captured selection, final content only, incomplete/no-question skip, cancellation)\n");finish(-1,result);}
            catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));finish(0,result);}return;
        }
        try{if(restartCheck)checkRestart();else check();result.putString("stream",restartCheck?"Process restart: PASS (level 5 and announcement read state restored)\n":"Reasoning UI: PASS (mirror, rapid drag, snap, cancel, persistence, legacy migration, request snapshot, announcement, offline tutorial, reserved space)\n");finish(-1,result);}
        catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));finish(0,result);}
    }
    private void ui(Runnable action){problem=null;runOnMainSync(()->{try{action.run();}catch(Throwable e){problem=e;}});if(problem!=null)throw new AssertionError(problem);}
    private static void check(boolean value){if(!value)throw new AssertionError("UI assertion failed");}
    private static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static void invoke(Object o,String name,Class<?>[] types,Object... values)throws Exception{Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(o,values);}
    private MainActivity launch(){return (MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    private static <T extends View> T find(View v,Class<T> type){if(type.isInstance(v))return type.cast(v);if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){T match=find(g.getChildAt(i),type);if(match!=null)return match;}}return null;}
    private static View text(View v,String content){if(v instanceof TextView&&content.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View match=text(g.getChildAt(i),content);if(match!=null)return match;}}return null;}
    private void pointer(View rail,int action,float level){int inset=Ui.dp(getTargetContext(),12);
        float x=inset+(rail.getWidth()-2*inset)*(level-1)/4;
        MotionEvent event=MotionEvent.obtain(0,android.os.SystemClock.uptimeMillis(),action,x,rail.getHeight()/2f,0);rail.dispatchTouchEvent(event);event.recycle();}
    private void check()throws Exception{
        settings=new Settings(getTargetContext());settings.prefs.edit().clear().putInt(UsageDeclaration.KEY,UsageDeclaration.REVISION).commit();
        check(settings.reasoningLevel()==3);
        settings.prefs.edit().remove("reasoning_level").putBoolean("thinking_enabled",true).commit();check(settings.reasoningLevel()==4);check(!settings.prefs.contains("thinking_enabled"));
        settings.setReasoningLevel(3);activity=launch();waitForIdleSync();
        ReasoningControls controls=find(activity.getWindow().getDecorView(),ReasoningControls.class);check(controls!=null);
        View depth=(View)field(controls,"depth"),speed=(View)field(controls,"speed");
        ui(()->{check(controls.futureVisualContainer.getChildCount()==0);check(!controls.futureVisualContainer.isClickable());
            check(Math.abs(controls.futureVisualContainer.getWidth()*3-controls.getWidth())<4);
            for(View rail:new View[]{depth,speed})for(int i=1;i<=5;i++){
                pointer(rail,MotionEvent.ACTION_DOWN,i);pointer(rail,MotionEvent.ACTION_UP,i);
                check(controls.reasoningLevel()==(rail==speed?6-i:i));check(settings.reasoningLevel()==controls.reasoningLevel());
                check(controls.reasoningLevel()+controls.speedLevel()==6);
            }
            pointer(depth,MotionEvent.ACTION_DOWN,1);
            for(int i=0;i<400;i++){float value=1+(i*37%401)/100f;pointer(depth,MotionEvent.ACTION_MOVE,value);
                if(Math.abs(controls.reasoningProgress()-value)>=0.001f)throw new AssertionError("progress expected="+value+" actual="+controls.reasoningProgress()+" width="+depth.getWidth());
                check(Math.abs(controls.reasoningProgress()+controls.speedProgress()-6)<0.001f);
                check(controls.reasoningLevel()+controls.speedLevel()==6);}
            pointer(depth,MotionEvent.ACTION_UP,4.2f);
        });
        android.os.SystemClock.sleep(200);ui(()->check(controls.reasoningProgress()==4));
        ApiRequest running=new ApiRequest(getTargetContext());settings.setReasoningLevel(1);check(((ReasoningStrategy)field(running,"strategy")).reasoningLevel==4);
        ui(()->{pointer(depth,MotionEvent.ACTION_DOWN,5);pointer(depth,MotionEvent.ACTION_CANCEL,5);check(controls.reasoningLevel()==1);});
        settings.setReasoningLevel(5);ui(()->activity.finish());waitForIdleSync();activity=launch();waitForIdleSync();
        ReasoningControls restored=find(activity.getWindow().getDecorView(),ReasoningControls.class);check(restored.reasoningLevel()==5);
        check(((TextView)field(activity,"announcementDot")).getVisibility()==View.VISIBLE);
        ui(()->{try{invoke(activity,"openDetail",new Class<?>[]{int.class,boolean.class},13,false);}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();
        check("screenqa-1.4.5".equals(new Settings(getTargetContext()).lastReadAnnouncementId()));
        check(((TextView)field(activity,"announcementDot")).getVisibility()==View.GONE);
        ui(()->{View link=text(activity.getWindow().getDecorView(),"查看完整使用教程 →");check(link!=null);link.performClick();});waitForIdleSync();
        check((Integer)field(activity,"selectedDetail")==14);
        check(text(activity.getWindow().getDecorView(),"首次使用教学")!=null);check(text(activity.getWindow().getDecorView(),"DeepSeek API 获取教程")!=null);
        ui(()->activity.onBackPressed());waitForIdleSync();check((Integer)field(activity,"selectedDetail")==13);
        ui(()->{activity.onBackPressed();activity.finish();});waitForIdleSync();activity=launch();waitForIdleSync();
        check(((TextView)field(activity,"announcementDot")).getVisibility()==View.GONE);
        android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(getTargetContext().getExternalFilesDir(null),"reasoning-home.png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    }
    private void checkRestart()throws Exception{
        settings=new Settings(getTargetContext());check(settings.reasoningLevel()==5);
        check("screenqa-1.4.5".equals(settings.lastReadAnnouncementId()));
        activity=launch();waitForIdleSync();
        check(find(activity.getWindow().getDecorView(),ReasoningControls.class).reasoningLevel()==5);
        check(((TextView)field(activity,"announcementDot")).getVisibility()==View.GONE);
    }
}
