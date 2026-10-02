package cn.screenqa.lite;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small, private, asynchronous diagnostic log. Never pass credentials to event(). */
final class QaLog {
    private static final ExecutorService IO=BuildConfig.DIAGNOSTICS_ENABLED?
            Executors.newSingleThreadExecutor():null;
    private static final int MAX_FILES=8;
    private static final long MAX_BYTES=2L*1024*1024;
    private static final long PER_FILE_BYTES=256L*1024;
    private static File directory,file;
    private static boolean recording;
    private static boolean clearing;
    private static int sequence;
    private static final SimpleDateFormat NAME=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US);
    private static final SimpleDateFormat TIME=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",Locale.US);
    private static final Object CLOCK=new Object();
    static boolean defaultRecording(){return BuildConfig.DEVELOPER_BUILD;}
    static void startAsync(Context context){
        if(!BuildConfig.DIAGNOSTICS_ENABLED)return;
        Context app=context.getApplicationContext();
        IO.execute(()->start(app));
    }
    static synchronized void start(Context context) {
        if(!BuildConfig.DIAGNOSTICS_ENABLED)return;
        if(directory==null) {
            directory=new File(context.getFilesDir(),"Q&A");
            recording=context.getSharedPreferences("settings",Context.MODE_PRIVATE)
                    .getBoolean("qa_log_recording",defaultRecording());
        }
        if(!recording||clearing||file!=null)return;
        file=newFile();
        String version="unknown";
        try {android.content.pm.PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),0);
            version=info.versionName+"/"+info.versionCode;
        } catch(Exception ignored){}
        event("APP_START version="+version+" android="+Build.VERSION.RELEASE+" sdk="+Build.VERSION.SDK_INT);
    }
    static synchronized boolean isRecording(Context context) {
        if(!BuildConfig.DIAGNOSTICS_ENABLED)return false;
        start(context);return recording;
    }
    static synchronized void setRecording(Context context,boolean enabled) {
        if(!BuildConfig.DIAGNOSTICS_ENABLED)return;
        start(context);
        if(recording==enabled)return;
        if(!enabled)event("LOG_RECORDING stopped");
        recording=enabled;
        context.getSharedPreferences("settings",Context.MODE_PRIVATE).edit()
                .putBoolean("qa_log_recording",enabled).apply();
        if(enabled&&!clearing) {
            file=newFile();event("LOG_RECORDING started");
        } else file=null;
    }
    static void event(String message) {
        if(!BuildConfig.DIAGNOSTICS_ENABLED)return;
        final File session;
        synchronized(QaLog.class) {
            if(!recording||clearing||file==null)return;
            session=file;
        }
        String stamp;
        synchronized(CLOCK){stamp=TIME.format(new Date());}
        String line=stamp+" ["+Thread.currentThread().getName()+"] "+message.replace('\n',' ').replace('\r',' ')+"\n";
        Log.i("ScreenQA",line.trim());
        IO.execute(() -> {
            File target=session;
            synchronized(QaLog.class){
                if(target.equals(file)&&target.length()>=PER_FILE_BYTES)target=file=newFile();
            }
            try {
                File dir=target.getParentFile();if(!dir.exists()&&!dir.mkdirs())return;
                try(FileOutputStream out=new FileOutputStream(target,true)) {
                    out.write(line.getBytes(StandardCharsets.UTF_8));
                }
                prune(dir,target);
            } catch(IOException e){Log.e("ScreenQA","Log write failed",e);}
        });
    }
    private static File newFile() {
        String stamp;
        synchronized(CLOCK){stamp=NAME.format(new Date());}
        return new File(directory,"qa_"+stamp+"_"+String.format(Locale.US,"%03d",++sequence)+".log");
    }
    private static void prune(File dir,File writing) {
        File[] all=dir.listFiles((d,n)->n.startsWith("qa_")&&n.endsWith(".log"));
        if(all==null)return;
        Arrays.sort(all,Comparator.comparing(File::getName));
        long total=0;for(File item:all)total+=item.length();
        for(int i=0;i<all.length&&(all.length-i>MAX_FILES||total>MAX_BYTES);i++) {
            if(all[i].equals(file)||all[i].equals(writing))continue;
            long size=all[i].length();if(all[i].delete())total-=size;
        }
    }
    static void latest(java.util.function.Consumer<String> callback) {
        if(!BuildConfig.DIAGNOSTICS_ENABLED){callback.accept("诊断功能不可用");return;}
        IO.execute(() -> {
            callback.accept(tail(latestFile()));
        });
    }
    static void list(java.util.function.Consumer<String[]> callback) {
        if(!BuildConfig.DIAGNOSTICS_ENABLED){callback.accept(new String[0]);return;}
        IO.execute(() -> {
            File[] all=directory==null?null:directory.listFiles((d,n)->n.startsWith("qa_")&&n.endsWith(".log"));
            if(all==null){callback.accept(new String[0]);return;}
            Arrays.sort(all,(a,b)->b.getName().compareTo(a.getName()));
            String[] names=new String[all.length];for(int i=0;i<all.length;i++)names[i]=all[i].getName();
            callback.accept(names);
        });
    }
    static void read(String name,java.util.function.Consumer<String> callback) {
        if(!BuildConfig.DIAGNOSTICS_ENABLED){callback.accept("诊断功能不可用");return;}
        IO.execute(() -> callback.accept(name.matches("qa_[0-9_]+\\.log")?
                tail(new File(directory,name)):"日志文件名无效"));
    }
    private static String tail(File item) {
        if(item==null||!item.isFile())return "暂无日志";
        try(RandomAccessFile input=new RandomAccessFile(item,"r")) {
            int length=(int)Math.min(24000,input.length());
            input.seek(input.length()-length);byte[] data=new byte[length];input.readFully(data);
            return item.getName()+"（末尾 24 KB）\n\n"+new String(data,StandardCharsets.UTF_8);
        } catch(IOException e){return "读取日志失败："+e.getClass().getSimpleName();}
    }
    static void exportLatest(java.io.OutputStream destination,java.util.function.Consumer<Boolean> done) {
        if(!BuildConfig.DIAGNOSTICS_ENABLED){
            try{destination.close();}catch(IOException ignored){}
            done.accept(false);return;
        }
        IO.execute(() -> {
            boolean okay=false;File newest=latestFile();
            if(newest!=null)try(InputStream in=new FileInputStream(newest);OutputStream out=destination) {
                byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
                okay=true;
            } catch(IOException ignored){}
            else try{destination.close();}catch(IOException ignored){}
            done.accept(okay);
        });
    }
    static void clear(java.util.function.Consumer<Integer> done) {
        if(!BuildConfig.DIAGNOSTICS_ENABLED){done.accept(0);return;}
        synchronized(QaLog.class){clearing=true;file=null;}
        IO.execute(() -> {
            int removed=0;File[] all=directory==null?null:directory.listFiles((d,n)->n.startsWith("qa_")&&n.endsWith(".log"));
            if(all!=null)for(File item:all)if(item.delete())removed++;
            synchronized(QaLog.class){
                clearing=false;
                if(recording)file=newFile();
            }
            if(recording)event("LOGS_CLEARED removed="+removed);
            done.accept(removed);
        });
    }
    static String latestName() {
        if(!BuildConfig.DIAGNOSTICS_ENABLED)return "";
        File latest=latestFile();return latest==null?"":latest.getName();
    }
    private static File latestFile() {
        File[] all=directory==null?null:directory.listFiles((d,n)->n.startsWith("qa_")&&n.endsWith(".log"));
        if(all==null||all.length==0)return null;
        Arrays.sort(all,Comparator.comparing(File::getName));return all[all.length-1];
    }
}
