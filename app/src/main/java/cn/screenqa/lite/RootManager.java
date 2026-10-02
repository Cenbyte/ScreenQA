package cn.screenqa.lite;

import android.content.ComponentName;
import android.content.Context;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Session-scoped permission and the two allowed Root capabilities. Never stores a Root grant. */
final class RootManager {
    private static final RootManager INSTANCE=new RootManager();
    private final RootShell shell=new RootShell();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean authorized;
    private volatile RootShell.Status status=RootShell.Status.UNAVAILABLE;
    static RootManager get(){return INSTANCE;}
    boolean authorized(){return BuildConfig.ROOT_SUPPORTED&&authorized;}
    RootShell.Status status(){return status;}
    void forget(){authorized=false;}
    void background(Runnable task){worker.execute(task);}
    RootShell.Result authorize() {
        if(!BuildConfig.ROOT_SUPPORTED)return new RootShell.Result(RootShell.Status.CANCELLED,false);
        RootShell.Result result=shell.execute("true",15000,()->true);
        remember(result);QaLog.event("ROOT permission status="+result.status);return result;
    }
    RootShell.Result enableAccessibility(Context context) {
        Settings settings=new Settings(context);
        if(!settings.rootAutoAccessibility())return new RootShell.Result(RootShell.Status.CANCELLED,false);
        if(!authorized()&&!authorize().success())return new RootShell.Result(status,false);
        ComponentName component=new ComponentName(context,ScreenQaAccessibilityService.class);
        int user=android.os.Process.myUid()/100000;
        RootShell.Result result=shell.execute(RootCommands.enableAccessibility(user,
                component.flattenToString(),component.flattenToShortString()),5000,
                ()->new Settings(context).rootAutoAccessibility());
        remember(result);QaLog.event("ROOT accessibility status="+result.status+" user="+user);return result;
    }
    AnswerClickOutcome tap(Context context,int x,int y,int width,int height,BooleanSupplier allowed) {
        return touch(context,TouchAction.tap(x,y,width,height),allowed);
    }
    AnswerClickOutcome touch(Context context,TouchAction action,BooleanSupplier allowed) {
        if(!new Settings(context).rootAnswerTap()||!authorized())
            return AnswerClickOutcome.accessibility(false);
        RootShell.Result result;
        try {result=shell.execute(action.command(),1800,
                ()->new Settings(context).rootAnswerTap()&&authorized(),
                ()->new Settings(context).rootAnswerTap()&&allowed.getAsBoolean());}
        catch(IllegalArgumentException e){result=new RootShell.Result(RootShell.Status.CANCELLED,false);}
        remember(result);
        QaLog.event("ROOT "+(action.swipe?"swipe":"tap")+" status="+result.status+" x="+action.x+" y="+action.y+
                " may_have_executed="+result.mayHaveExecuted);
        return AnswerClickOutcome.root(result);
    }
    void tapAsync(Context context,int x,int y,int width,int height,BooleanSupplier allowed,
            Consumer<AnswerClickOutcome> callback) {
        Context app=context.getApplicationContext();
        worker.execute(()->callback.accept(tap(app,x,y,width,height,allowed)));
    }
    private void remember(RootShell.Result result) {
        status=result.status;
        if(result.success())authorized=true;
        else if(result.status!=RootShell.Status.CANCELLED)authorized=false;
    }
    static String message(RootShell.Status status) {
        return switch(status) {
            case SUCCESS -> "Root 已授权";
            case UNAVAILABLE -> "Root 不可用，继续使用普通模式";
            case DENIED -> "Root 授权被拒绝或已撤销";
            case TIMEOUT -> "Root 响应超时，请在权限管理器中确认授权";
            case CANCELLED -> "操作已取消";
            case FAILED -> "Root 命令失败，保留手动授权方式";
        };
    }
}
