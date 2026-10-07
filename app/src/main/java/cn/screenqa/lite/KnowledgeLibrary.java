package cn.screenqa.lite;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

/** Process-scoped package lifecycle; immutable installed snapshots and worker-only disk work. */
final class KnowledgeLibrary implements KnowledgePackageImporter {
    private static KnowledgeLibrary instance;
    static synchronized KnowledgeLibrary get(Context context) {
        if(instance==null)instance=new KnowledgeLibrary(context.getApplicationContext());return instance;
    }
    static final class Installed {
        final String id,name,engine,version;final long records,indexed,relations,empty;final boolean enabled;final File directory;
        Installed(File directory,JSONObject info,boolean enabled)throws Exception {
            this.directory=directory;id=directory.getName();name=info.getString("name");engine=info.getString("engine");version=info.optString("version","未提供");
            records=info.getLong("records");indexed=info.getLong("indexed");relations=info.getLong("relations");empty=info.optLong("empty",0);this.enabled=enabled;
            if(!id.matches("[a-f0-9]{64}") || !(engine.equals("FTS4") || engine.equals("FTS5")))throw new IOException("知识库安装信息无效");
        }
    }
    static final class State {
        final List<Installed> packages;final KnowledgeTask task;final String message;final long done,total;final boolean busy,rag;
        State(List<Installed> packages,String message,long done,long total,boolean busy,boolean rag,KnowledgeTask task) {
            this.packages=Collections.unmodifiableList(new ArrayList<>(packages));this.message=message;this.done=done;this.total=total;this.busy=busy;this.rag=rag;this.task=task;
        }
    }
    private final Context context;private final SharedPreferences preferences;
    private final File root,packages,inbox;private final Object searchLock=new Object();
    private final AtomicBoolean operating=new AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicInteger enableRevision=new java.util.concurrent.atomic.AtomicInteger();
    private final CountDownLatch initialized=new CountDownLatch(1);
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Set<Consumer<State>> observers=new HashSet<>();
    private volatile State state;
    private volatile KnowledgeTask task;private volatile Runnable retryAction;private long lastTaskSave;
    private KnowledgeLibrary(Context context) {
        this.context=context;preferences=context.getSharedPreferences("knowledge_library",Context.MODE_PRIVATE);
        root=new File(context.getFilesDir(),"rag");packages=new File(root,"packages");inbox=new File(root,"inbox");
        task=KnowledgeTask.restore(preferences.getString("task_snapshot",""));
        state=new State(Collections.emptyList(),"读取已安装知识库…",0,-1,false,ragEnabled(),task);
        worker.execute(()->{
            try {
                Files.createDirectories(packages.toPath());Files.createDirectories(inbox.toPath());
                File[] pending=packages.listFiles((dir,name)->name.startsWith(".install-") || name.startsWith(".delete-"));
                if(pending!=null)for(File path:pending)KnowledgeArchive.deleteTree(path);
                update(readInstalled(),"就绪",0,-1,false);
            } catch(Exception error){update(Collections.emptyList(),"读取知识库失败："+error.getMessage(),0,-1,false);}
            finally{initialized.countDown();}
        });
    }
    State state(){return state;}
    boolean ragEnabled(){return preferences.getBoolean("rag_enabled",false);}
    void observe(Consumer<State> observer){observers.add(observer);observer.accept(state);}
    void remove(Consumer<State> observer){observers.remove(observer);}
    void setRagEnabled(boolean enabled) {
        preferences.edit().putBoolean("rag_enabled",enabled).apply();
        update(state.packages,state.message,state.done,state.total,state.busy);
    }
    /** UI enablement checks the installed index on the worker, never scans source JSONL. */
    void requestRagEnabled(boolean enabled,Consumer<String> callback){
        int revision=enableRevision.incrementAndGet();
        if(!enabled){setRagEnabled(false);callback.accept(null);return;}
        if(initialized.getCount()!=0){setRagEnabled(false);callback.accept("知识库仍在加载，请稍后重试");return;}
        if(state.busy){setRagEnabled(false);callback.accept("正在安装或整理知识库，请稍后重试");return;}
        worker.execute(()->{
            String error=readinessError();
            main.post(()->{
                if(revision!=enableRevision.get()){callback.accept("开关状态已更新，请重试");return;}
                String current=state.busy?"正在安装或整理知识库，请稍后重试":error;
                setRagEnabled(current==null);callback.accept(current);
            });
        });
    }
    void checkReadiness(Consumer<String> callback){
        if(initialized.getCount()!=0){callback.accept("知识库仍在加载，请稍后重试");return;}
        if(state.busy){callback.accept("正在安装或整理知识库，请稍后重试");return;}
        worker.execute(()->{String error=readinessError();main.post(()->callback.accept(error));});
    }
    private String readinessError(){
        if(state.busy)return "正在安装或整理知识库，请稍后重试";
        if(state.packages.isEmpty())return state.message.startsWith("读取知识库失败")?"读取知识库失败，请重新安装":"尚未安装知识包，请先在线获取";
        long enabled=0;
        synchronized(searchLock){
            for(Installed pack:state.packages){
                if(!preferences.getBoolean("enabled_"+pack.id,true))continue;
                enabled++;
                if(pack.indexed<=0)return "知识包没有可检索内容，请重新安装";
                File index=new File(pack.directory,"index.db");
                if(!index.isFile() || index.length()==0)return "知识包索引缺失，请重新安装";
                try(android.database.sqlite.SQLiteDatabase db=android.database.sqlite.SQLiteDatabase.openDatabase(index.getPath(),null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY,broken->{})){
                    try(android.database.Cursor record=db.rawQuery("SELECT rid,question,options FROM docs LIMIT 1",null)){
                        if(!record.moveToFirst())return "知识包索引为空，请重新安装";
                    }
                    try(android.database.Cursor text=db.rawQuery("SELECT rowid FROM search LIMIT 1",null);
                        android.database.Cursor query=db.rawQuery("SELECT rowid FROM search WHERE search MATCH ? LIMIT 1",new String[]{"screenqareadinessprobe"})){
                        if(!text.moveToFirst())return "知识包检索索引为空，请重新安装";
                        query.moveToFirst();
                    }
                }catch(Exception error){android.util.Log.w("KnowledgeLibrary","索引加载检查失败",error);return "知识包索引无法读取，请重新安装";}
            }
        }
        return enabled==0?"知识包均已禁用，请在管理中启用":null;
    }
    void setEnabled(String id,boolean enabled) {
        preferences.edit().putBoolean("enabled_"+id,enabled).apply();
        worker.execute(()->{try{update(readInstalled(),state.message,state.done,state.total,state.busy);}catch(Exception error){failure(error);}});
    }
    private List<Installed> readInstalled()throws Exception {
        List<Installed> result=new ArrayList<>();File[] paths=packages.listFiles();if(paths==null)return result;
        Arrays.sort(paths,Comparator.comparing(File::getName));
        for(File path:paths)if(path.getName().matches("[a-f0-9]{64}") && new File(path,"index.db").isFile())
            result.add(new Installed(path,KnowledgeArchive.json(new File(path,"installed.json"),65536),preferences.getBoolean("enabled_"+path.getName(),true)));
        return result;
    }
    private synchronized void update(List<Installed> installed,String message,long done,long total,boolean busy) {
        State next=new State(installed,message,done,total,busy || task.busy() || operating.get(),ragEnabled(),task);state=next;
        main.post(()->{for(Consumer<State> observer:new ArrayList<>(observers))observer.accept(next);});
    }
    private void failure(Exception error){if(task.busy())failTask(error);else update(state.packages,"失败："+error.getMessage(),0,-1,false);}
    synchronized boolean beginDownload(String name,Runnable retry){if(task.busy() || operating.get())return false;retryAction=retry;task=KnowledgeTask.begin(name,false);publishTask(true);return true;}
    private synchronized void beginLocal(String name,Runnable retry){retryAction=retry;task=KnowledgeTask.begin(name,true);publishTask(true);}
    synchronized void taskProgress(KnowledgeTask.Step step,String name,String detail,long done,long total){
        boolean changed=task.step!=step;task=task.advance(step,detail,done,total);if(name!=null && !name.isEmpty())task=task.named(name);publishTask(changed);
    }
    synchronized void failTask(Exception error){task=task.fail((task.step==KnowledgeTask.Step.IMPORT?"数据导入失败：":task.title(task.step)+"失败：")+(error.getMessage()==null?error.getClass().getSimpleName():error.getMessage()));publishTask(true);}
    private synchronized void finishTask(long records){finishTask(records,false);}
    private synchronized void finishTask(long records,boolean reused){task=reused?task.completeExisting(records):task.complete(records);retryAction=null;publishTask(true);}
    private void publishTask(boolean force){
        long now=android.os.SystemClock.elapsedRealtime();if(force || now-lastTaskSave>1000){try{preferences.edit().putString("task_snapshot",task.json().toString()).apply();}catch(Exception ignored){}lastTaskSave=now;}
        update(state.packages,task.status==KnowledgeTask.Status.FAILED?task.detail:task.status==KnowledgeTask.Status.COMPLETE?"知识库安装完成":task.title(task.step),task.done,task.total,task.busy());
    }
    void retry(){Runnable action=retryAction;if(action!=null)action.run();else if(task.status==KnowledgeTask.Status.FAILED)context.startActivity(new android.content.Intent(context,KnowledgeBrowserActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));}

    @Override public String importPackage(File file)throws Exception {return install(file,(phase,done,total)->{});}
    String install(File file,KnowledgeArchive.Progress external)throws Exception {
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("安装必须在后台线程执行");
        if(!initialized.await(30,TimeUnit.SECONDS))throw new IOException("知识库初始化超时");
        if(!operating.compareAndSet(false,true))throw new IOException("已有知识库安装或删除任务，请稍后从收件箱重试");
        File staging=null;
        try {
            if(!task.busy())beginLocal(file.getName(),()->installAsync(file));else retryAction=()->installAsync(file);
            KnowledgeArchive.Progress progress=new KnowledgeArchive.Progress(){
                @Override public void update(String phase,long done,long total){taskProgress(task.step,file.getName(),phase,done,total);external.update(phase,done,total);}
                @Override public void stage(KnowledgeTask.Step step,long done,long total){taskProgress(step,file.getName(),step.title,done,total);external.stage(step,done,total);}
            };
            progress.stage(KnowledgeTask.Step.VALIDATE,0,-1);new KnowledgePackageValidator.Basic().validate(file,file.getName(),"application/zip",-1);
            String id=KnowledgeArchive.hash(file);File installed=new File(packages,id);
            if(installed.isDirectory()) {
                removeInboxCopy(file);Installed existing=new Installed(installed,KnowledgeArchive.json(new File(installed,"installed.json"),65536),true);finishTask(existing.records,true);return "知识包已安装";
            }
            if(state.packages.size()>=8)throw new IOException("最多同时安装 8 个知识库，请先删除不用的知识库");
            if(new StatFs(root.getPath()).getAvailableBytes()<512L*1024*1024)throw new IOException("私有存储可用空间不足 512 MiB");
            staging=new File(packages,".install-"+UUID.randomUUID());Files.createDirectories(staging.toPath());
            progress.stage(KnowledgeTask.Step.EXTRACT,0,-1);
            Files.copy(file.toPath(),new File(staging,"package.zip").toPath());
            File payload=new File(staging,"payload");Files.createDirectories(payload.toPath());
            File manifest=KnowledgeArchive.extract(new File(staging,"package.zip"),payload,progress);
            List<KnowledgeArchive.Dataset> datasets=KnowledgeArchive.datasets(manifest);
            KnowledgeIndex.Stats stats=KnowledgeIndex.build(new File(staging,"index.db"),datasets,progress);
            progress.stage(KnowledgeTask.Step.FINISH,0,-1);
            JSONObject packageInfo=KnowledgeArchive.json(manifest,1024*1024);
            JSONObject info=new JSONObject().put("version",packageInfo.optString("package_version",packageInfo.optString("version","未提供"))).put("id",id).put("name",file.getName()).put("engine",stats.engine)
                    .put("records",stats.records).put("indexed",stats.indexed).put("relations",stats.relations).put("empty",stats.empty).put("installed_at",System.currentTimeMillis());
            try(FileOutputStream output=new FileOutputStream(new File(staging,"installed.json"))) {output.write(info.toString().getBytes(StandardCharsets.UTF_8));output.getFD().sync();}
            synchronized(searchLock) {Files.move(staging.toPath(),installed.toPath());staging=null;}
            preferences.edit().putBoolean("enabled_"+id,true).apply();
            removeInboxCopy(file);
            finishTask(stats.records);
            return "安装成功 · "+stats.records+" 条数据 / "+stats.indexed+" 条可检索 · "+stats.engine;
        } catch(Exception error){failure(error);throw error;}
        finally {
            if(staging!=null)try{KnowledgeArchive.deleteTree(staging);}catch(IOException error){android.util.Log.w("KnowledgeLibrary","安装临时目录清理失败",error);}
            operating.set(false);
            try {update(readInstalled(),state.message,state.done,state.total,false);}catch(Exception error){failure(error);}
        }
    }
    private void removeInboxCopy(File file)throws IOException {
        if(file.getParentFile().getCanonicalFile().equals(inbox.getCanonicalFile()))Files.deleteIfExists(file.toPath());
    }
    void installAsync(File file){worker.execute(()->{try{importPackage(file);}catch(Exception ignored){}});}
    void importUri(Uri uri) {
        worker.execute(()->{
            File file=new File(inbox,"knowledge-"+System.currentTimeMillis()+".zip");File partial=new File(inbox,file.getName()+".part");
            try {
                if(task.busy())return;beginLocal(file.getName(),()->importUri(uri));long count=0,lastProgress=0;
                try(InputStream input=context.getContentResolver().openInputStream(uri);OutputStream output=new FileOutputStream(partial)) {
                    if(input==null)throw new IOException("无法读取选中的文件");byte[] buffer=new byte[65536];int n;
                    while((n=input.read(buffer))!=-1){count+=n;if(count>KnowledgeSourceConfig.MAX_DOWNLOAD_BYTES)throw new IOException("知识包过大");output.write(buffer,0,n);long now=android.os.SystemClock.elapsedRealtime();if(now-lastProgress>150){taskProgress(KnowledgeTask.Step.DOWNLOAD,file.getName(),"正在读取本地知识包",count,-1);lastProgress=now;}}
                }
                Files.move(partial.toPath(),file.toPath());importPackage(file);
            } catch(Exception error){failure(error);try{Files.deleteIfExists(partial.toPath());}catch(IOException ignored){}}
        });
    }
    void inboxFiles(Consumer<List<File>> consumer) {
        worker.execute(()->{
            File[] files=inbox.listFiles((dir,name)->name.toLowerCase(Locale.ROOT).endsWith(".zip"));
            List<File> found=files==null?Collections.emptyList():Arrays.asList(files);main.post(()->consumer.accept(found));
        });
    }
    void delete(String id) {
        if(!id.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("知识库 ID 无效");
        worker.execute(()->{
            if(!operating.compareAndSet(false,true)){failure(new IOException("请等待当前安装完成后删除"));return;}
            try {
                update(state.packages,"正在删除知识库与索引…",0,-1,true);
                File path=new File(packages,id),removed=new File(packages,".delete-"+id);
                synchronized(searchLock){if(path.exists())Files.move(path.toPath(),removed.toPath());}
                KnowledgeArchive.deleteTree(removed);preferences.edit().remove("enabled_"+id).apply();
                task=KnowledgeTask.idle();preferences.edit().remove("task_snapshot").apply();update(readInstalled(),"知识库已删除",0,-1,false);
            } catch(Exception error){failure(error);}finally{operating.set(false);update(state.packages,state.message,state.done,state.total,false);}
        });
    }
    List<KnowledgeIndex.Hit> search(KnowledgeText.Query query,int topK,BooleanSupplier cancelled)throws Exception {
        if(!ragEnabled())return Collections.emptyList();
        if(!initialized.await(5,TimeUnit.SECONDS))return Collections.emptyList();
        List<KnowledgeIndex.Hit> result=new ArrayList<>();
        synchronized(searchLock) {
            for(Installed installed:state.packages) {
                KnowledgeIndex.checkCancelled(cancelled);
                if(!preferences.getBoolean("enabled_"+installed.id,true) || !installed.directory.isDirectory())continue;
                result.addAll(KnowledgeIndex.search(new File(installed.directory,"index.db"),installed.engine,installed.id,query,Math.max(5,topK),cancelled));
            }
        }
        result.sort(Comparator.comparingDouble((KnowledgeIndex.Hit hit)->hit.score).reversed().thenComparing(KnowledgeIndex.Hit::identity));
        return new ArrayList<>(result.subList(0,Math.min(Math.max(1,topK),result.size())));
    }
    void preview(String text,String stage,String subject,Consumer<List<KnowledgeIndex.Hit>> consumer,Consumer<String> failure) {
        worker.execute(()->{try{List<KnowledgeIndex.Hit> hits=search(new KnowledgeText.Query(null,text,stage,subject),5,()->false);main.post(()->consumer.accept(hits));}
            catch(Exception error){main.post(()->failure.accept(error.getMessage()));}});
    }
}
