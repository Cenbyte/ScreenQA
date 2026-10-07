package cn.screenqa.lite;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.*;
import android.webkit.WebView;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.HttpsURLConnection;

/** Real Android SQLite/package/UI integration. AI transport is captured, no live key or charge. */
public final class KnowledgeIntegrationTest extends Instrumentation {
    private final JSONObject report=new JSONObject();private String mode;
    private final List<JSONObject> requests=new ArrayList<>();
    @Override public void onCreate(Bundle args){super.onCreate(args);mode=args.getString("mode","package");start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try {
            Context context=getTargetContext();KnowledgeLibrary library=KnowledgeLibrary.get(context);
            if(mode.equals("live"))downloadLive();
            else if((mode.equals("installed") || mode.equals("delete"))){Thread.sleep(500);}
            else {
                File inbox=new File(context.getFilesDir(),"rag/inbox");inbox.mkdirs();File zip=new File(inbox,"knowledge_base.zip");
                if(!mode.equals("inbox"))try(InputStream input=getContext().getAssets().open("knowledge_base.zip");OutputStream output=new FileOutputStream(zip)) {
                    byte[] buffer=new byte[65536];int n;while((n=input.read(buffer))!=-1)output.write(buffer,0,n);
                }
                long started=SystemClock.elapsedRealtime();String installed=library.install(zip,(phase,done,total)->{if(done%5000==0)status(phase+" "+done+"/"+total);});
                report.put("fixture_install",installed).put("install_ms",SystemClock.elapsedRealtime()-started);
            }
            check(library.state().packages.size()==1,"Installed library count");
            KnowledgeLibrary.Installed pack=library.state().packages.get(0);
            report.put("engine",pack.engine).put("records",pack.records).put("indexed",pack.indexed).put("relations",pack.relations).put("empty",pack.empty);
            check(pack.records==142009 && pack.indexed==97189 && pack.relations==44814 && pack.empty==6,"Counts must match the provided package");
            try(SQLiteDatabase database=SQLiteDatabase.openDatabase(new File(pack.directory,"index.db").getPath(),null,SQLiteDatabase.OPEN_READONLY);
                Cursor cursor=database.rawQuery("SELECT sqlite_version()",null)){cursor.moveToFirst();report.put("sqlite_version",cursor.getString(0));}
            String stem="人体内含有多种多样的蛋白质，每种蛋白质（    ）";
            String full=stem+"\nA. 都含有21种氨基酸\nB. 都是在细胞内发挥作用\nC. 都能调节生物体的生命活动\nD. 都具有一定的空间结构";
            long searchStart=SystemClock.elapsedRealtime();
            List<KnowledgeIndex.Hit> hits=library.search(new KnowledgeText.Query(stem,full,"高中","生物"),5,()->false);
            report.put("search_ms",SystemClock.elapsedRealtime()-searchStart);check(hits.size()==5,"Top K=5");
            check(hits.get(0).exact && hits.get(0).source.equals("e_eval") && hits.get(0).answer.equals("D"),"Exact protein exercise first, answer D");
            JSONArray top=new JSONArray();for(KnowledgeIndex.Hit hit:hits)top.put(new JSONObject().put("id",hit.id).put("source",hit.source).put("type",hit.type).put("exact",hit.exact).put("score",hit.score).put("question",hit.question).put("answer",hit.answer));report.put("top_k",top);
            String reference=KnowledgeRag.context(hits);check(reference.length()<=4000,"Context is bounded");report.put("reference",reference);
            List<KnowledgeIndex.Hit> concepts=library.search(new KnowledgeText.Query(null,"蛋白质空间结构与功能的关系",null,"生物"),5,()->false);
            check(!concepts.isEmpty(),"Concept lookup");report.put("concept_top",concepts.get(0).summary());
            long rawModified=new File(pack.directory,"payload/knowledge_base/data/e_eval.jsonl").lastModified();
            runOnMainSync(()->library.setRagEnabled(false));check(library.search(new KnowledgeText.Query(stem,full,null,null),5,()->false).isEmpty(),"RAG off");
            runOnMainSync(()->library.setRagEnabled(true));runOnMainSync(()->library.setEnabled(pack.id,false));
            check(library.search(new KnowledgeText.Query(stem,full,null,null),5,()->false).isEmpty(),"Package disabled");runOnMainSync(()->library.setEnabled(pack.id,true));
            check(rawModified==new File(pack.directory,"payload/knowledge_base/data/e_eval.jsonl").lastModified(),"Queries leave original JSONL untouched");
            report.put("package_id",pack.id).put("database_bytes",new File(pack.directory,"index.db").length());
            captureActualSolve(context,full);
            new Settings(context).prefs.edit().putInt(UsageDeclaration.KEY,UsageDeclaration.REVISION).commit();
            MainActivity activity=(MainActivity)startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            runOnMainSync(()->{try {java.lang.reflect.Method method=MainActivity.class.getDeclaredMethod("openDetail",int.class,boolean.class);method.setAccessible(true);method.invoke(activity,15,false);}catch(Exception error){throw new RuntimeException(error);}});
            waitForIdleSync();SystemClock.sleep(500);
            KnowledgeLibraryPanel panel=(KnowledgeLibraryPanel)findPanel(activity.getWindow().getDecorView());check(panel!=null,"Native knowledge panel");
            runOnMainSync(()->{try{field(panel,"question",android.widget.EditText.class).setText(full);field(panel,"stage",android.widget.EditText.class).setText("高中");field(panel,"subject",android.widget.EditText.class).setText("生物");field(panel,"search",android.widget.Button.class).performClick();}catch(Exception error){throw new RuntimeException(error);}});
            long previewStart=SystemClock.elapsedRealtime();String preview="";
            while(SystemClock.elapsedRealtime()-previewStart<30000){AtomicReference<String> value=new AtomicReference<>();runOnMainSync(()->{try{value.set(field(panel,"results",android.widget.TextView.class).getText().toString());}catch(Exception error){throw new RuntimeException(error);}});preview=value.get();if(preview.contains("e_eval:multiple_choice:335489e946eb9f3e386f79f8"))break;SystemClock.sleep(100);}
            check(preview.contains("e_eval:multiple_choice:335489e946eb9f3e386f79f8"),"Manual UI Top-K preview");report.put("ui_top_k",preview);
            android.graphics.Bitmap screenshot=getUiAutomation().takeScreenshot();
            try(OutputStream output=new FileOutputStream(new File(context.getFilesDir(),"knowledge-ui.png"))){screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);}screenshot.recycle();
            runOnMainSync(()->{try{android.widget.TextView text=field(panel,"results",android.widget.TextView.class);android.view.ViewParent parent=text.getParent();while(parent!=null && !(parent instanceof android.widget.ScrollView))parent=parent.getParent();if(parent instanceof android.widget.ScrollView){android.widget.ScrollView scroll=(android.widget.ScrollView)parent;android.graphics.Rect rect=new android.graphics.Rect();text.getDrawingRect(rect);scroll.offsetDescendantRectToMyCoords(text,rect);scroll.scrollTo(0,rect.top-24);}}catch(Exception error){throw new RuntimeException(error);}});
            SystemClock.sleep(250);android.graphics.Bitmap topScreenshot=getUiAutomation().takeScreenshot();try(OutputStream output=new FileOutputStream(new File(context.getFilesDir(),"knowledge-top-k.png"))){topScreenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);}topScreenshot.recycle();
            verifyFailedInstall(context,library,pack);
            report.put("ui_opened",true).put("ai_transport","captured mock HTTPS; no live DeepSeek call");
            if(mode.equals("delete")){
                library.delete(pack.id);long deleting=SystemClock.elapsedRealtime();while(SystemClock.elapsedRealtime()-deleting<30000 && (!library.state().packages.isEmpty() || library.state().busy))SystemClock.sleep(100);
                check(library.state().packages.isEmpty() && !pack.directory.exists(),"Delete cleans ZIP, JSONL and index");
                check(library.search(new KnowledgeText.Query(stem,full,null,null),5,()->false).isEmpty(),"Deleted library returns no references");report.put("deletion_verified",true);
            }
            saveReport();result.putString("stream","Knowledge integration PASS: "+report.toString()+"\n");finish(-1,result);
        } catch(Throwable error) {
            try{report.put("failure",android.util.Log.getStackTraceString(error));saveReport();}catch(Exception ignored){}
            result.putString("stream",android.util.Log.getStackTraceString(error));finish(0,result);
        }
    }
    private android.view.View findPanel(android.view.View view){if(view instanceof KnowledgeLibraryPanel)return view;if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){android.view.View result=findPanel(group.getChildAt(i));if(result!=null)return result;}}return null;}
    private <T>T field(Object instance,String name,Class<T> type)throws Exception{java.lang.reflect.Field field=instance.getClass().getDeclaredField(name);field.setAccessible(true);return type.cast(field.get(instance));}
    private void status(String text){Bundle bundle=new Bundle();bundle.putString("stream",text+"\n");sendStatus(0,bundle);}
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private void saveReport()throws Exception {try(OutputStream output=new FileOutputStream(new File(getTargetContext().getFilesDir(),"knowledge-integration.json"))){output.write(report.toString(2).getBytes(StandardCharsets.UTF_8));}}
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
    private void verifyFailedInstall(Context context,KnowledgeLibrary library,KnowledgeLibrary.Installed existing)throws Exception {
        File corrupt=new File(context.getCacheDir(),"bad-knowledge.zip");org.json.JSONArray datasets=new org.json.JSONArray();
        try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(new FileOutputStream(corrupt))){
            for(String name:new String[]{"e_eval","k12_kgraph","k12_knowledge_points","m3ke"}){
                JSONObject record=new JSONObject().put("id",name+":bad-test").put("source",name).put("stage","高中").put("subject","生物").put("type","exercise").put("question","蛋白质").put("options",JSONObject.NULL).put("answer","D").put("explanation",JSONObject.NULL).put("knowledge",JSONObject.NULL).put("content",JSONObject.NULL);
                byte[] bytes=(record.toString()+"\n").getBytes(StandardCharsets.UTF_8);String path="data/"+name+".jsonl";
                zip.putNextEntry(new java.util.zip.ZipEntry("knowledge_base/"+path));zip.write(bytes);zip.closeEntry();
                datasets.put(new JSONObject().put("name",name).put("file",path).put("cleaned_records",1).put("validation",new JSONObject().put("bytes",bytes.length).put("records",1).put("sha256",String.join("",Collections.nCopies(64,"0")))));
            }
            byte[] manifest=new JSONObject().put("schema_version","1.0").put("datasets",datasets).put("total_cleaned_records",4).toString().getBytes(StandardCharsets.UTF_8);
            zip.putNextEntry(new java.util.zip.ZipEntry("knowledge_base/manifest.json"));zip.write(manifest);zip.closeEntry();
        }
        boolean rejected=false;try{library.install(corrupt,(p,d,t)->{});}catch(IOException expected){rejected=expected.getMessage().contains("SHA-256");}finally{corrupt.delete();}
        check(rejected,"Wrong dataset hash must fail installation");check(library.state().packages.size()==1 && library.state().packages.get(0).id.equals(existing.id),"Failed installation preserves active library");
        File[] pending=existing.directory.getParentFile().listFiles((dir,name)->name.startsWith(".install-"));check(pending!=null && pending.length==0,"No partial index remains");report.put("failed_hash_rollback",true);
    }
    private void captureActualSolve(Context context,String full)throws Exception {
        URL.setURLStreamHandlerFactory(protocol->protocol.equals("https")?new URLStreamHandler(){
            @Override protected URLConnection openConnection(URL url){return new Captured(url);}
        }:null);
        List<ScreenDocument.Line> lines=new ArrayList<>();String[] values=full.split("\n");
        for(int i=0;i<values.length;i++)lines.add(new ScreenDocument.Line(values[i],0,100+i*50,1000,140+i*50));
        ScreenDocument document=new ScreenDocument(lines,1080,1920);
        LocalQuestionLocator.Candidate candidate=new LocalQuestionLocator.Candidate(document,"choice",Collections.singletonList(1),Arrays.asList(1,2,3,4,5));
        ApiRequest call=new ApiRequest(context,"knowledge_integration");QuestionDetection solved=call.solve("fixture-key",candidate);
        check(solved.complete && solved.answer.equals("D"),"Actual solve pipeline output");check(requests.size()==1,"One answer request");
        JSONObject body=requests.get(0);String user=body.getJSONArray("messages").getJSONObject(1).getString("content");
        check(user.contains("e_eval:multiple_choice:335489e946eb9f3e386f79f8") && user.contains("参考答案：D"),"Actual POST includes retrieved source and answer");
        check(body.getJSONArray("messages").getJSONObject(0).getString("content").contains(KnowledgeRag.INSTRUCTION),"Reference caution instruction in system prompt");
        check(body.getString("model").equals(Settings.MODEL),"Existing model configuration");report.put("captured_solve_request",body);
    }
    private final class Captured extends HttpsURLConnection {
        final ByteArrayOutputStream body=new ByteArrayOutputStream();Captured(URL url){super(url);}
        @Override public OutputStream getOutputStream(){return body;}
        @Override public int getResponseCode(){return 200;}
        @Override public InputStream getInputStream()throws IOException {
            try{requests.add(new JSONObject(body.toString("UTF-8")));JSONObject response=new JSONObject().put("model",Settings.MODEL)
                .put("usage",new JSONObject().put("prompt_tokens",100).put("completion_tokens",10).put("total_tokens",110))
                .put("choices",new JSONArray().put(new JSONObject().put("finish_reason","stop").put("message",new JSONObject().put("content","{\"complete\":true,\"answer\":\"D\"}"))));
                return new ByteArrayInputStream(response.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception error){throw new IOException(error);}
        }
        @Override public void connect(){}@Override public void disconnect(){}@Override public boolean usingProxy(){return false;}
        @Override public String getCipherSuite(){return "test";}@Override public java.security.cert.Certificate[] getLocalCertificates(){return null;}@Override public java.security.cert.Certificate[] getServerCertificates(){return new java.security.cert.Certificate[0];}
    }
}
