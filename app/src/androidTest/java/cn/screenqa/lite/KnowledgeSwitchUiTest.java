package cn.screenqa.lite;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.file.Files;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Native device coverage for real installed indexes and permission-gated home switches. */
public final class KnowledgeSwitchUiTest extends Instrumentation {
    private final JSONObject report=new JSONObject();private MainActivity main;private KnowledgeLibrary library;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        library=KnowledgeLibrary.get(getTargetContext());await(()->!library.state().packages.isEmpty(),15000,"existing installed knowledge package");
        KnowledgeLibrary.Installed pack=library.state().packages.get(0);check(pack.records==142009,"real 142009-record package");
        Settings settings=new Settings(getTargetContext());settings.prefs.edit().putInt(UsageDeclaration.KEY,UsageDeclaration.REVISION).commit();
        runOnMainSync(()->{library.setRagEnabled(false);library.setEnabled(pack.id,true);settings.setNextOverlay(false);});await(()->library.state().packages.get(0).enabled,5000,"package enabled");
        main=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        View home=field(main,"pages",View[].class)[0];KnowledgeHomeControl knowledge=(KnowledgeHomeControl)findClass(home,KnowledgeHomeControl.class);check(knowledge!=null,"compact home knowledge control");
        GoldSwitch pet=field(main,"homePetSwitch",GoldSwitch.class);check(findClass(home,KnowledgeSummaryView.class)==null,"large home card removed");
        check(knowledge.toggle.getClass()==pet.getClass(),"identical switch UI");
        int[] ky=new int[2],py=new int[2],by=new int[2];Button capture=field(main,"captureButton",Button.class);
        runOnMainSync(()->{knowledge.toggle.getLocationOnScreen(ky);pet.getLocationOnScreen(py);capture.getLocationOnScreen(by);});
        check(ky[1]==py[1] && ky[0]<py[0],"switches share one aligned row");
        check(ky[1]>=by[1]+capture.getHeight(),"both switches below capture button");
        long checking=SystemClock.elapsedRealtime();runOnMainSync(()->knowledge.toggle.performClick());await(()->knowledge.toggle.isEnabled(),30000,"enable validation completed");report.put("enable_check_ms",SystemClock.elapsedRealtime()-checking);check(library.ragEnabled() && knowledge.toggle.isChecked(),"valid index enables");
        runOnMainSync(()->findDescription(home,"知识库说明与加载状态").performClick());await(()->activeText("加载检查：成功"),30000,"help confirms real index readiness");shot("help-ready");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();
        check(request(false)==null && !library.ragEnabled(),"free disable");
        runOnMainSync(()->library.setEnabled(pack.id,false));await(()->!library.state().packages.get(0).enabled,5000,"package disabled");
        runOnMainSync(()->knowledge.toggle.performClick());await(()->knowledge.toggle.isEnabled(),10000,"disabled package rejection");check(!knowledge.toggle.isChecked() && !library.ragEnabled(),"failed enable restores off");check(request(true).contains("均已禁用"),"disabled reason");
        runOnMainSync(()->library.setEnabled(pack.id,true));await(()->library.state().packages.get(0).enabled,5000,"package restored");
        File index=new File(pack.directory,"index.db"),backup=new File(pack.directory,"index.db.switch-test-backup");
        Files.move(index.toPath(),backup.toPath());
        try{
            check(request(true).contains("索引缺失") && !library.ragEnabled(),"missing index rejected");
            try(FileOutputStream file=new FileOutputStream(index)){file.write("invalid database".getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            check(request(true).contains("无法读取") && !library.ragEnabled(),"corrupt index rejected");Files.delete(index.toPath());
            runOnMainSync(()->library.setEnabled(pack.id,true));await(()->library.state().packages.isEmpty(),5000,"missing package refreshed");
            check(request(true).contains("尚未安装") && !library.ragEnabled(),"no installed package rejected");
        }finally{Files.deleteIfExists(index.toPath());Files.move(backup.toPath(),index.toPath());runOnMainSync(()->library.setEnabled(pack.id,true));}
        await(()->!library.state().packages.isEmpty(),5000,"actual package restored");
        check(request(true)==null && library.ragEnabled(),"restored index passes");
        CountDownLatch cancelled=new CountDownLatch(1);runOnMainSync(()->{library.requestRagEnabled(true,error->cancelled.countDown());library.requestRagEnabled(false,error->{});});check(cancelled.await(30,TimeUnit.SECONDS) && !library.ragEnabled(),"late validation cannot override disable");
        report.put("real_package_records",pack.records).put("compact_controls",true).put("help_load_check",true).put("disabled_missing_corrupt_empty_rejected",true).put("disable_cancels_pending_enable",true);
        String oldServices=shell("settings get secure enabled_accessibility_services").trim();String oldAccessibility=shell("settings get secure accessibility_enabled").trim();boolean oldOverlay=android.provider.Settings.canDrawOverlays(getTargetContext());
        try{
            shell("settings put secure enabled_accessibility_services null");shell("settings put secure accessibility_enabled 0");await(()->ScreenQaAccessibilityService.active==null,5000,"accessibility disconnected");
            shell("appops set "+getTargetContext().getPackageName()+" SYSTEM_ALERT_WINDOW deny");runOnMainSync(()->pet.performClick());check(!pet.isChecked() && !settings.nextOverlay(),"overlay permission gates pet");check(field(main,"feedback",TextView.class).getText().toString().contains("悬浮窗权限"),"overlay reason visible");shot("overlay-denied");
            shell("appops set "+getTargetContext().getPackageName()+" SYSTEM_ALERT_WINDOW allow");runOnMainSync(()->pet.performClick());check(!pet.isChecked() && !settings.nextOverlay(),"touch capability gates pet");check(field(main,"feedback",TextView.class).getText().toString().contains("无障碍"),"touch permission reason visible");
            shell("settings put secure enabled_accessibility_services "+getTargetContext().getPackageName()+"/cn.screenqa.lite.ScreenQaAccessibilityService");shell("settings put secure accessibility_enabled 1");await(()->ScreenQaAccessibilityService.active!=null,10000,"actual accessibility service connected");
            runOnMainSync(()->pet.performClick());check(pet.isChecked() && settings.nextOverlay(),"real permission grants allow pet");
            check(request(true)==null,"knowledge restored enabled");waitForIdleSync();SystemClock.sleep(4000);shot("home-ready");
            shell("appops set "+getTargetContext().getPackageName()+" SYSTEM_ALERT_WINDOW deny");runOnMainSync(()->invoke(main,"updateLiveStatus"));check(!pet.isChecked() && !settings.nextOverlay(),"revocation returns pet off");
            report.put("pet_overlay_denied",true).put("pet_touch_denied",true).put("pet_granted",true).put("pet_revoked",true);
        }finally{
            shell("appops set "+getTargetContext().getPackageName()+" SYSTEM_ALERT_WINDOW "+(oldOverlay?"allow":"deny"));shell("settings put secure enabled_accessibility_services "+oldServices);shell("settings put secure accessibility_enabled "+oldAccessibility);
        }
        save();result.putString("stream","Knowledge switch PASS\n");finish(-1,result);
    }catch(Throwable error){try{report.put("failure",android.util.Log.getStackTraceString(error));save();}catch(Exception ignored){}result.putString("stream",android.util.Log.getStackTraceString(error));finish(0,result);}}
    private String request(boolean enabled)throws Exception{AtomicReference<String> error=new AtomicReference<>();CountDownLatch done=new CountDownLatch(1);runOnMainSync(()->library.requestRagEnabled(enabled,value->{error.set(value);done.countDown();}));check(done.await(30,TimeUnit.SECONDS),"readiness callback");waitForIdleSync();return error.get();}
    private String shell(String command)throws Exception{try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).executeShellCommand(command))){return new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}}
    private boolean activeText(String text){AccessibilityNodeInfo root=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).getRootInActiveWindow();return root!=null && !root.findAccessibilityNodeInfosByText(text).isEmpty();}
    private void await(BooleanSupplier condition,long timeout,String reason){long start=SystemClock.elapsedRealtime();while(!condition.getAsBoolean() && SystemClock.elapsedRealtime()-start<timeout)SystemClock.sleep(80);check(condition.getAsBoolean(),reason);}
    private void shot(String name)throws Exception{android.graphics.Bitmap bitmap=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();try(OutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"knowledge-switch-"+name+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    private void save()throws Exception{try(OutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"knowledge-switch-result.json"))){out.write(report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}}
    private View findClass(View view,Class<?> type){if(type.isInstance(view))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=findClass(group.getChildAt(i),type);if(found!=null)return found;}}return null;}
    private View findDescription(View view,String description){if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=findDescription(group.getChildAt(i),description);if(found!=null)return found;}}return null;}
    private <T>T field(Object object,String name,Class<T> type)throws Exception{java.lang.reflect.Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return type.cast(field.get(object));}
    private void invoke(Object object,String name){try{java.lang.reflect.Method method=object.getClass().getDeclaredMethod(name);method.setAccessible(true);method.invoke(object);}catch(Exception error){throw new RuntimeException(error);}}
    private void check(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}
}
