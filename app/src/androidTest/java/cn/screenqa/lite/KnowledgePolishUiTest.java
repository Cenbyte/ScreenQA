package cn.screenqa.lite;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.webkit.*;import android.widget.*;import org.json.*;import java.io.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicReference;
public final class KnowledgePolishUiTest extends Instrumentation {
 private final JSONObject report=new JSONObject();private MainActivity main;private KnowledgeLibrary library;
 private final Set<Integer> phases=new java.util.concurrent.ConcurrentSkipListSet<>();private final JSONArray events=new JSONArray();
 private final java.util.function.Consumer<KnowledgeLibrary.State> observer=state->{try{if(state.task.busy()){phases.add(state.task.step.number);if(state.task.done==0 || state.task.done==state.task.total)events.put(state.task.json());}}catch(Exception e){throw new RuntimeException(e);}};
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 @Override public void onStart(){Bundle result=new Bundle();try{
  prepareUi();
  KnowledgeBrowserActivity browser=(KnowledgeBrowserActivity)startActivitySync(new Intent(getTargetContext(),KnowledgeBrowserActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  WebView web=field(browser,"web",WebView.class);long start=SystemClock.elapsedRealtime();JSONObject box=null;
  while(SystemClock.elapsedRealtime()-start<60000){String value=js(web,"(function(){var a=Array.from(document.querySelectorAll('a')).find(a=>a.innerText.includes('knowledge_base.zip'));if(!a)return null;var r=a.getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2,width:innerWidth,html:a.outerHTML}})()");if(!value.equals("null")){box=new JSONObject(value);break;}SystemClock.sleep(400);}
  check(box!=null,"file listed after auto password");report.put("file_link",box);touch(web,box);SystemClock.sleep(2500);waitForIdleSync();
  java.util.List<WebView> popups=field(browser,"popups",java.util.List.class);WebView target=popups.isEmpty()?web:popups.get(popups.size()-1);report.put("native_popup_count",popups.size());
  int[] location=new int[2];runOnMainSync(()->target.getLocationOnScreen(location));
  report.put("web_top",location[1]).put("web_height",target.getHeight());
  java.util.List<Dialog> dialogs=field(browser,"dialogs",java.util.List.class);check(dialogs.size()==1,"actual file popup");Dialog popup=dialogs.get(0);
  View close=findText(popup.getWindow().getDecorView(),"关闭文件页面");check(close!=null && close.getWidth()>target.getWidth()*.8,"close header has visible width");
  check(target.getHeight()>popup.getWindow().getDecorView().getHeight()*.75,"web fills popup without half-screen blank");
  check(js(target,"getComputedStyle(document.querySelector('.mico').previousElementSibling).display").contains("none"),"known empty file spacer removed");report.put("popup_blank_removed",true).put("empty_file_spacer_removed",true);
  shot("file-page");
  JSONObject download=new JSONObject(js(target,"(function(){function find(d,ox,oy){for(var a of d.querySelectorAll('a'))if(/下载/.test(a.innerText)&& !/高速|会员|客户端|APP|浏览器|举报/.test(a.innerText)){var r=a.getBoundingClientRect();return {x:ox+r.left+r.width/2,y:oy+r.top+r.height/2,width:innerWidth}}for(var f of d.querySelectorAll('iframe'))try{var r=f.getBoundingClientRect(),v=find(f.contentDocument,ox+r.left,oy+r.top);if(v)return v}catch(e){}return null}return find(document,0,0)})()"));
  touch(target,download);long started=SystemClock.elapsedRealtime();boolean running=false;Set<Integer> photographed=new HashSet<>();
  Set<String> followed=new HashSet<>();
  while(SystemClock.elapsedRealtime()-started<300000){KnowledgeLibrary.State state=library.state();if(state.task.busy()){running=true;if(photographed.add(state.task.step.number)){waitForIdleSync();shot("step-"+state.task.step.number);}}if(running && !state.busy)break;
   if(!running){
    java.util.List<WebView> active=field(browser,"popups",java.util.List.class);WebView current=active.isEmpty()?web:active.get(active.size()-1);
    JSONArray links=new JSONArray(js(current,"(function(){var all=[];function read(d,ox,oy){Array.from(d.querySelectorAll('a')).forEach(a=>{if(/下载/.test(a.innerText)&& !/高速|会员|客户端|APP|浏览器|举报/.test(a.innerText)){var r=a.getBoundingClientRect();all.push({url:a.href,x:ox+r.left+r.width/2,y:oy+r.top+r.height/2,width:innerWidth})}});Array.from(d.querySelectorAll('iframe')).forEach(f=>{try{var r=f.getBoundingClientRect();if(f.contentDocument)read(f.contentDocument,ox+r.left,oy+r.top)}catch(e){}})}read(document,0,0);return all})()"));
    for(int i=0;i<links.length();i++){JSONObject link=links.getJSONObject(i);if(followed.add(link.getString("url"))){touch(current,link);break;}}
   }
   SystemClock.sleep(500);}
  check(running && library.state().task.status==KnowledgeTask.Status.COMPLETE,"actual download and installation completed");check(library.state().packages.size()==1 && library.state().packages.get(0).records==142009,"real package record count");waitForIdleSync();for(int i=1;i<=6;i++)check(phases.contains(i),"real step "+i);
  runOnMainSync(browser::finish);runOnMainSync(()->openKnowledge());waitForIdleSync();SystemClock.sleep(450);
  KnowledgeProgressView progress=(KnowledgeProgressView)findClass(main.getWindow().getDecorView(),KnowledgeProgressView.class);
  check(progress.getHeight()<Ui.dp(getTargetContext(),100),"completion panel is compact");shot("management-installed");
  report.put("all_six_real_steps",true).put("records",142009).put("events",events).put("compact_success",true);save();result.putString("stream","Knowledge polish PASS\n");finish(-1,result);
 }catch(Throwable error){try{report.put("failure",android.util.Log.getStackTraceString(error));save();}catch(Exception ignored){}result.putString("stream",android.util.Log.getStackTraceString(error));finish(0,result);}}
 private void prepareUi()throws Exception{
  library=KnowledgeLibrary.get(getTargetContext());SystemClock.sleep(400);Settings settings=new Settings(getTargetContext());settings.prefs.edit().putInt(UsageDeclaration.KEY,UsageDeclaration.REVISION).remove("knowledge_onboarding_seen").commit();
  runOnMainSync(()->{library.setRagEnabled(false);library.observe(observer);});
  main=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();SystemClock.sleep(450);
  check(findText(main.getWindow().getDecorView(),"开启知识库（建议）")==null,"no first-use knowledge modal");
  KnowledgeNoticeView notice=(KnowledgeNoticeView)findClass(main.getWindow().getDecorView(),KnowledgeNoticeView.class);check(notice.getText().toString().contains("未开启") && notice.getText().toString().contains(UsageDeclaration.MARQUEE),"reminder shares declaration marquee");
  KnowledgeHomeControl summary=(KnowledgeHomeControl)findClass(main.getWindow().getDecorView(),KnowledgeHomeControl.class);GoldSwitch toggle=summary.toggle;
  runOnMainSync(()->scrollTo(summary));SystemClock.sleep(250);check(toggle.getBackground()==null,"compact switch has no external ripple");
  int[] xy=new int[2];runOnMainSync(()->toggle.getLocationOnScreen(xy));float x=xy[0]+toggle.getWidth()/2f,y=xy[1]+toggle.getHeight()/2f;long time=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(time,time,0,x,y,0);getUiAutomation().injectInputEvent(down,true);down.recycle();SystemClock.sleep(150);shot("switch-pressed");MotionEvent up=MotionEvent.obtain(time,SystemClock.uptimeMillis(),1,x,y,0);getUiAutomation().injectInputEvent(up,true);up.recycle();waitForIdleSync();SystemClock.sleep(500);check(!library.ragEnabled(),"fresh user cannot enable unloaded knowledge");
  check(notice.getText().toString().contains("未开启"),"fresh user gets non-blocking reminder");
  runOnMainSync(()->openKnowledge());waitForIdleSync();KnowledgeLibraryPanel panel=(KnowledgeLibraryPanel)findClass(main.getWindow().getDecorView(),KnowledgeLibraryPanel.class);
  LinearLayout methods=field(panel,"otherMethods",LinearLayout.class);check(methods.getVisibility()==View.GONE,"alternate methods collapsed");
  shot("management-empty");runOnMainSync(()->findText(panel,"其他方法 ›").performClick());waitForIdleSync();check(methods.isShown(),"alternate entry works");shot("other-methods");runOnMainSync(()->findText(panel,"其他方法 ⌄").performClick());
  report.put("no_first_use_modal",true).put("marquee_reminder",true).put("compact_switch",true).put("unloaded_enable_rejected",true).put("alternate_methods_collapsed",true);
 }
 private void openKnowledge(){try{java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod("openDetail",int.class,boolean.class);m.setAccessible(true);m.invoke(main,15,false);}catch(Exception e){throw new RuntimeException(e);}}
 private void scrollTo(View target){ViewParent parent=target.getParent();while(parent!=null && !(parent instanceof ScrollView))parent=parent.getParent();if(parent instanceof ScrollView){ScrollView scroll=(ScrollView)parent;android.graphics.Rect rect=new android.graphics.Rect();target.getDrawingRect(rect);scroll.offsetDescendantRectToMyCoords(target,rect);scroll.scrollTo(0,rect.top-24);}}
 private View findText(View view,String text){if(view instanceof TextView && ((TextView)view).getText().toString().equals(text))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=findText(group.getChildAt(i),text);if(found!=null)return found;}}return null;}
 private View findClass(View view,Class<?> cls){if(cls.isInstance(view))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=findClass(group.getChildAt(i),cls);if(found!=null)return found;}}return null;}
 private <T>T field(Object obj,String name,Class<T> cls)throws Exception{java.lang.reflect.Field f=obj.getClass().getDeclaredField(name);f.setAccessible(true);return cls.cast(f.get(obj));}
 private void touch(WebView web,JSONObject box)throws Exception{int[] origin=new int[2];runOnMainSync(()->web.getLocationOnScreen(origin));float scale=web.getWidth()/(float)box.getDouble("width");float x=origin[0]+(float)box.getDouble("x")*scale,y=origin[1]+(float)box.getDouble("y")*scale;long t=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(t,t,0,x,y,0),up=MotionEvent.obtain(t,t+70,1,x,y,0);getUiAutomation().injectInputEvent(down,true);getUiAutomation().injectInputEvent(up,true);down.recycle();up.recycle();}
 private void shot(String name)throws Exception{android.graphics.Bitmap image=getUiAutomation().takeScreenshot();try(OutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"knowledge-polish-"+name+".png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
 private void save()throws Exception{try(OutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"knowledge-polish-probe.json"))){out.write(report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}}
 private void check(boolean condition,String msg){if(!condition)throw new AssertionError(msg);}
    private String js(WebView web,String expression)throws Exception {
        CountDownLatch done=new CountDownLatch(1);AtomicReference<String> value=new AtomicReference<>();
        runOnMainSync(()->web.evaluateJavascript("JSON.stringify("+expression+")",result->{value.set(result);done.countDown();}));
        check(done.await(10,TimeUnit.SECONDS),"WebView JS callback");return new JSONArray("["+value.get()+"]").getString(0);
    }
}
