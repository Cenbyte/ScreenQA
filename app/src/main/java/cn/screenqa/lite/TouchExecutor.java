package cn.screenqa.lite;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Shared final action layer. Never runs Root or waits for a gesture on the main thread. */
final class TouchExecutor {
    private static final ExecutorService worker=Executors.newSingleThreadExecutor();
    private static final Handler main=new Handler(Looper.getMainLooper());
    static boolean available(Context context){
        return ScreenQaAccessibilityService.active!=null||rootAvailable(context);
    }
    static boolean rootAvailable(Context context){return new Settings(context).rootAnswerTap()&&RootManager.get().authorized();}
    static AnswerClickOutcome execute(Context context,TouchAction action,BooleanSupplier allowed,TouchRouter.Backend node){
        Settings settings=new Settings(context);TouchPriority priority=settings.touchPriority();
        return TouchRouter.execute(priority,allowed,
                node==null?null:()->log(action,"node",node.run()),
                ()->log(action,"root",RootManager.get().touch(context,action,allowed)),
                ()->{ScreenQaAccessibilityService service=ScreenQaAccessibilityService.active;
                    return log(action,"gesture",service==null?AnswerClickOutcome.accessibility(false):service.gesture(action,allowed));});
    }
    static void executeAsync(Context context,TouchAction action,BooleanSupplier allowed,TouchRouter.Backend node,
            Consumer<AnswerClickOutcome> callback){
        Context app=context.getApplicationContext();
        worker.execute(()->{AnswerClickOutcome result=execute(app,action,allowed,node);main.post(()->callback.accept(result));});
    }
    private static AnswerClickOutcome log(TouchAction action,String backend,AnswerClickOutcome result){
        QaLog.event("TOUCH action="+(action.swipe?"swipe":"tap")+" backend="+backend+
                " accepted="+result.accepted+" fallback="+result.allowFallback+" uncertain="+result.uncertain);
        return result;
    }
}
