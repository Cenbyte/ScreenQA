package cn.screenqa.lite;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Native, real network installation flow; never calls an AI endpoint. */
public final class KnowledgeUiFlowTest extends Instrumentation {
    private final JSONObject report=new JSONObject();
    private final JSONArray events=new JSONArray();
    private final Set<Integer> phases=new HashSet<>(),screens=new HashSet<>();
    private KnowledgeLibrary library;private MainActivity main;private boolean switched;
    private final Consumer<KnowledgeLibrary.State> observer=state->{
        try{KnowledgeTask task=state.task;if(task.busy()){phases.add(task.step.number);if(task.done==0 || task.done==task.total || task.done%5000==0)events.put(task.json());}}
        catch(Exception e){throw new RuntimeException(e);}
    };
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        Context context=getTargetContext();library=KnowledgeLibrary.get(context);SystemClock.sleep(500);
        for(KnowledgeLibrary.Installed pack:new ArrayList<>(library.state().packages)){runOnMainSync(()->library.delete(pack.id));long start=SystemClock.elapsedRealtime();while(SystemClock.elapsedRealtime()-start<30000 && (library.state().busy || !library.state().packages.isEmpty()))SystemClock.sleep(100);}
        check(library.state().packages.isEmpty(),"fresh install must have zero packages");
        Settings settings=new Settings(context);settings.prefs.edit().putInt(UsageDeclaration.KEY,UsageDeclaration.REVISION).remove("knowledge_onboarding_seen").commit();
        main=(MainActivity)startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();SystemClock.sleep(400);
        check(findText(main.getWindow().getDecorView(),"开启知识库（建议）")==null,"no blocking knowledge onboarding");
        runOnMainSync(()->{library.setRagEnabled(true);openKnowledge(main);});
        runOnMainSync(()->library.observe(observer));downloadLive();waitForIdleSync();
        check(library.state().packages.size()==1 && library.state().packages.get(0).records==142009,"installed real package");
        check(library.state().task.status==KnowledgeTask.Status.COMPLETE,"success only after publication");
        for(int i=1;i<=6;i++)check(phases.contains(i),"real backend step "+i+" seen");check(switched,"left and reentered management during import");
        runOnMainSync(()->openKnowledge(main));waitForIdleSync();SystemClock.sleep(300);inspect(main);shot("07-installed");
        runOnMainSync(()->{
            KnowledgeLibraryPanel panel=(KnowledgeLibraryPanel)findClass(main.getWindow().getDecorView(),KnowledgeLibraryPanel.class);
            try{java.lang.reflect.Field field=KnowledgeLibraryPanel.class.getDeclaredField("installed");field.setAccessible(true);View installed=(View)field.get(panel);ViewParent parent=installed.getParent();while(parent!=null && !(parent instanceof ScrollView))parent=parent.getParent();
                ScrollView scroll=(ScrollView)parent;android.graphics.Rect rect=new android.graphics.Rect();installed.getDrawingRect(rect);scroll.offsetDescendantRectToMyCoords(installed,rect);scroll.scrollTo(0,rect.top-24);
            }catch(Exception error){throw new RuntimeException(error);}
        });SystemClock.sleep(250);inspectManagement();shot("07a-installed-package");
        report.put("all_six_real_steps",true).put("page_switch_consistent",switched).put("records",142009).put("events",events);
        // Real malformed JSONL, then retry the same inbox file after replacing it with the valid package.
        File retryFile=new File(context.getFilesDir(),"rag/inbox/retry-ui.zip");retryFile.getParentFile().mkdirs();makeBadPackage(retryFile);
        runOnMainSync(()->library.installAsync(retryFile));awaitFinished();check(library.state().task.status==KnowledgeTask.Status.FAILED && library.state().task.step.number==4,"import failure reports step 4");waitForIdleSync();inspect(main);shot("08-failed-step4");report.put("failure",library.state().task.json());
        java.nio.file.Files.copy(new File(library.state().packages.get(0).directory,"package.zip").toPath(),retryFile.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        runOnMainSync(()->{Button retry=(Button)findText(main.getWindow().getDecorView(),"重试");check(retry!=null && retry.isEnabled(),"failure retry visible and available");retry.performClick();});awaitFinished();
        check(library.state().task.status==KnowledgeTask.Status.COMPLETE && !retryFile.exists(),"retry performs actual package validation and successful install");report.put("retry_success",true);
        runOnMainSync(()->library.setRagEnabled(false));waitForIdleSync();check(!library.ragEnabled(),"free disable persists");report.put("switch_off_persisted",true);
        runOnMainSync(()->{library.setRagEnabled(true);showTab(main,1);});waitForIdleSync();SystemClock.sleep(650);shot("09-settings");
        runOnMainSync(()->showTab(main,0));waitForIdleSync();SystemClock.sleep(650);shot("10-home");
        save();result.putString("stream","Knowledge UI flow PASS: "+report.toString()+"\n");finish(-1,result);
    }catch(Throwable error){try{report.put("failure",android.util.Log.getStackTraceString(error));report.put("events",events);save();}catch(Exception ignored){}result.putString("stream",android.util.Log.getStackTraceString(error));finish(0,result);}}
    private void awaitFinished(){long start=SystemClock.elapsedRealtime();SystemClock.sleep(200);while(SystemClock.elapsedRealtime()-start<60000 && library.state().busy)SystemClock.sleep(100);check(!library.state().busy,"background task finishes");}
    private void inspect(Activity activity)throws Exception{
        AtomicReference<KnowledgeTask> shown=new AtomicReference<>();runOnMainSync(()->{
            KnowledgeProgressView view=(KnowledgeProgressView)findClass(activity.getWindow().getDecorView(),KnowledgeProgressView.class);
            check(view!=null,"pinned step panel exists");KnowledgeTask task=(KnowledgeTask)view.getTag();shown.set(task);
            check(view.heading.getText().toString().equals(task.heading()),"visible heading matches rendered task");
            check(view.count.getText().toString().equals(task.count()),"visible progress matches real count");
            if(task.busy())check(!view.heading.getText().toString().contains("完成"),"no early completion");
            if(task.status!=KnowledgeTask.Status.IDLE)check(view.heading.isShown(),"current step always visible");
        });KnowledgeTask task=shown.get();
        if(task.busy() && screens.add(task.step.number))shot(String.format(java.util.Locale.ROOT,"step-%d",task.step.number));
        if(task.step==KnowledgeTask.Step.IMPORT && task.busy() && !switched){
            runOnMainSync(()->openKnowledge(main));Intent intent=new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);runOnMainSync(()->getTargetContext().startActivity(intent));waitForIdleSync();SystemClock.sleep(650);
            inspectManagement();runOnMainSync(()->showTab(main,1));runOnMainSync(()->openKnowledge(main));waitForIdleSync();inspectManagement();SystemClock.sleep(500);shot("05-management-import");
            switched=true;
        }
    }
    private void inspectManagement(){runOnMainSync(()->{KnowledgeProgressView view=(KnowledgeProgressView)findClass(main.getWindow().getDecorView(),KnowledgeProgressView.class);KnowledgeTask task=(KnowledgeTask)view.getTag();check(view.heading.isShown() && view.heading.getText().toString().equals(task.heading()),"reentered management reflects same task");});}
    private void openKnowledge(MainActivity activity){try{java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod("openDetail",int.class,boolean.class);m.setAccessible(true);m.invoke(activity,15,false);}catch(Exception e){throw new RuntimeException(e);}}
    private void showTab(MainActivity activity,int tab){try{java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod("showTab",int.class,boolean.class);m.setAccessible(true);m.invoke(activity,tab,false);}catch(Exception e){throw new RuntimeException(e);}}
    private View findClass(View view,Class<?> type){if(type.isInstance(view))return view;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){View found=findClass(g.getChildAt(i),type);if(found!=null)return found;}}return null;}
    private View findText(View view,String value){if(view instanceof TextView && ((TextView)view).getText().toString().equals(value))return view;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){View found=findText(g.getChildAt(i),value);if(found!=null)return found;}}return null;}
    private void makeBadPackage(File file)throws Exception{
        JSONArray datasets=new JSONArray();try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(new FileOutputStream(file))){
            for(String name:new String[]{"e_eval","k12_kgraph","k12_knowledge_points","m3ke"}){String path="data/"+name+".jsonl";byte[] bytes="{invalid json}\n".getBytes(StandardCharsets.UTF_8);zip.putNextEntry(new java.util.zip.ZipEntry("knowledge_base/"+path));zip.write(bytes);zip.closeEntry();
                datasets.put(new JSONObject().put("name",name).put("file",path).put("cleaned_records",1).put("validation",new JSONObject().put("records",1).put("bytes",bytes.length).put("sha256",String.join("",Collections.nCopies(64,"0")))));}
            zip.putNextEntry(new java.util.zip.ZipEntry("knowledge_base/manifest.json"));zip.write(new JSONObject().put("schema_version","1.0").put("datasets",datasets).put("total_cleaned_records",4).toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();
        }
    }
    private void shot(String name)throws Exception{android.graphics.Bitmap image=getUiAutomation().takeScreenshot();try(OutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"knowledge-ui-"+name+".png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private void status(String text){Bundle b=new Bundle();b.putString("stream",text+"\n");sendStatus(0,b);}
    private void save()throws Exception{try(OutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"knowledge-ui-flow.json"))){out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));}}
    private String js(WebView web,String expression)throws Exception {
        CountDownLatch done=new CountDownLatch(1);AtomicReference<String> value=new AtomicReference<>();
        runOnMainSync(()->web.evaluateJavascript("JSON.stringify("+expression+")",result->{value.set(result);done.countDown();}));
        check(done.await(10,TimeUnit.SECONDS),"WebView JS callback");return new JSONArray("["+value.get()+"]").getString(0);
    }
    private void downloadLive()throws Exception {
        Context context=getTargetContext();KnowledgeBrowserActivity browser=(KnowledgeBrowserActivity)startActivitySync(new Intent(context,KnowledgeBrowserActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        java.lang.reflect.Field field=KnowledgeBrowserActivity.class.getDeclaredField("web");field.setAccessible(true);WebView web=(WebView)field.get(browser);
        long started=SystemClock.elapsedRealtime();String fileLink=null;
        while(SystemClock.elapsedRealtime()-started<60000) {
            JSONObject page=new JSONObject(js(web,"({title:document.title,code:document.getElementById('pwd')?document.getElementById('pwd').value:'',links:Array.from(document.querySelectorAll('a')).map(a=>({text:a.innerText,url:a.href}))})"));
            JSONArray links=page.getJSONArray("links");
            for(int i=0;i<links.length();i++){JSONObject link=links.getJSONObject(i);if(link.getString("text").contains("knowledge_base.zip"))fileLink=link.getString("url");}
            if(fileLink!=null){report.put("password_flow",page);break;}SystemClock.sleep(500);
        }
        check(fileLink!=null,"Live share must expose knowledge_base.zip");String selected=fileLink;
        runOnMainSync(()->web.loadUrl(selected));Set<String> clicked=new HashSet<>();
        KnowledgeDownloadSession transfer=KnowledgeDownloadSession.get(context);started=SystemClock.elapsedRealtime();boolean sawBusy=false;
        while(SystemClock.elapsedRealtime()-started<300000) {
            AtomicReference<KnowledgeDownloadSession.State> state=new AtomicReference<>();
            runOnMainSync(()->{try{java.lang.reflect.Field sf=KnowledgeDownloadSession.class.getDeclaredField("state");sf.setAccessible(true);state.set((KnowledgeDownloadSession.State)sf.get(transfer));}catch(Exception error){throw new RuntimeException(error);}});
            KnowledgeDownloadSession.State current=state.get();
            if(current.busy)sawBusy=true;
            inspect(switched?main:browser);
            if(current.success){report.put("live_download",current.message).put("download_bytes",current.bytes);runOnMainSync(browser::finish);return;}
            if(sawBusy && !current.busy)throw new IOException(current.message);
            if(!sawBusy) {
                JSONArray links=new JSONArray(js(web,"(function(){var all=[];function read(d){Array.from(d.querySelectorAll('a')).forEach(a=>{if(/下载/.test(a.innerText)&& !/高速|会员|客户端|APP|浏览器|举报/.test(a.innerText))all.push({text:a.innerText,url:a.href})});Array.from(d.querySelectorAll('iframe')).forEach(f=>{try{if(f.contentDocument)read(f.contentDocument)}catch(e){}})}read(document);return all})()"));
                for(int i=0;i<links.length();i++) {
                    String url=links.getJSONObject(i).getString("url");if(clicked.add(url)) {
                        status("Real touch download "+links.getJSONObject(i).getString("text"));
                        JSONObject box=new JSONObject(js(web,"(function(){function find(d,ox,oy){for(var a of d.querySelectorAll('a'))if(a.href==="+JSONObject.quote(url)+"){a.target='_self';a.scrollIntoView({block:'center'});var r=a.getBoundingClientRect();return {x:ox+r.left+r.width/2,y:oy+r.top+r.height/2,width:window.innerWidth}}for(var f of d.querySelectorAll('iframe')){try{var r=f.getBoundingClientRect();var got=find(f.contentDocument,ox+r.left,oy+r.top);if(got)return got}catch(e){}}return null}return find(document,0,0)})()"));
                        int[] origin=new int[2];runOnMainSync(()->web.getLocationOnScreen(origin));
                        float scale=web.getWidth()/(float)box.getDouble("width");float x=origin[0]+(float)box.getDouble("x")*scale,y=origin[1]+(float)box.getDouble("y")*scale;
                        long now=SystemClock.uptimeMillis();android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,x,y,0),up=android.view.MotionEvent.obtain(now,now+60,android.view.MotionEvent.ACTION_UP,x,y,0);
                        getUiAutomation().injectInputEvent(down,true);getUiAutomation().injectInputEvent(up,true);down.recycle();up.recycle();break;
                    }
                }
            }
            if(SystemClock.elapsedRealtime()%15000<500)status(current.message);SystemClock.sleep(500);
        }
        report.put("web_html",KnowledgeText.limit(js(web,"document.documentElement.outerHTML"),16000));throw new IOException("Live download/install timeout");
    }
}
