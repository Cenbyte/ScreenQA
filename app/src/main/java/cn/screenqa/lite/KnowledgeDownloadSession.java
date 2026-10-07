package cn.screenqa.lite;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/** One process-scoped transfer survives browser recreation; holds no Activity or WebView. */
final class KnowledgeDownloadSession {
    static final class State {
        final String message, name;
        final long bytes,total;
        final boolean busy,success;
        final boolean installing;final int installPercent;
        State(String message,String name,long bytes,long total,boolean busy,boolean success) {
            this(message,name,bytes,total,busy,success,false,0);
        }
        State(String message,String name,long bytes,long total,boolean busy,boolean success,boolean installing,int percent) {
            this.message=message;this.name=name;this.bytes=bytes;this.total=total;this.busy=busy;this.success=success;
            this.installing=installing;this.installPercent=percent;
        }
    }
    private static KnowledgeDownloadSession instance;
    static synchronized KnowledgeDownloadSession get(Context context) {
        if(instance==null) instance=new KnowledgeDownloadSession(context.getApplicationContext());
        return instance;
    }
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final KnowledgeDownloader downloader;
    private final KnowledgeLibrary importer;
    private Consumer<State> listener;
    private State state=new State("点击网盘知识包下载；验证后自动安装与建索引", "",0,-1,false,false);

    private KnowledgeDownloadSession(Context context) {
        importer=KnowledgeLibrary.get(context);
        CookieManager manager=CookieManager.getInstance();
        downloader=new KnowledgeDownloader.Http(new File(context.getFilesDir(),"rag/inbox"),
                new KnowledgeDownloader.Cookies() {
                    @Override public String get(String url) { return manager.getCookie(url); }
                    @Override public void set(String url,String value) {
                        // Synchronous setter makes redirect cookies available before the next request.
                        manager.setCookie(url,value);
                    }
                },new KnowledgePackageValidator.Basic());
    }
    void observe(Consumer<State> next) { listener=next; next.accept(state); }
    void remove(Consumer<State> old) { if(listener==old) listener=null; }
    boolean start(KnowledgeDownloader.Request request) {
        if(state.busy || !importer.beginDownload("",()->start(request))) return false;
        publish(new State("连接下载服务器…","",0,-1,true,false));
        worker.execute(()->{
            try {
                File file=downloader.download(request,new KnowledgeDownloader.Progress(){
                    @Override public void update(String name,long bytes,long total){importer.taskProgress(KnowledgeTask.Step.DOWNLOAD,name,"正在下载知识包",bytes,total);main.post(()->publish(new State("下载中…",name,bytes,total,true,false)));}
                    @Override public void validating(String name,long bytes){importer.taskProgress(KnowledgeTask.Step.VALIDATE,name,"正在校验文件",0,-1);}
                });
                long size=file.length();
                String result=importer.install(file,(phase,done,total)->main.post(()->publish(new State(
                        phase+(total>0?" · "+done+" / "+total:""),file.getName(),size,size,true,false,true,total>0?(int)(done*100/total):-1))));
                main.post(()->publish(new State("下载验证成功 · "+result,file.getName(),size,size,false,true)));
            } catch(Exception error) {
                if(importer.state().task.status!=KnowledgeTask.Status.FAILED)importer.failTask(error);
                String message=error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();
                main.post(()->publish(new State((state.installing?"知识包安装失败：":"下载失败：")+message,state.name,state.bytes,state.total,false,false)));
            }
        });
        return true;
    }
    private void publish(State next) { state=next; if(listener!=null)listener.accept(next); }
}
