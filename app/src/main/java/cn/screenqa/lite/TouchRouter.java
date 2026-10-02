package cn.screenqa.lite;

import java.util.function.BooleanSupplier;

/** A failed dispatch may fall back; an obsolete or possibly delivered action never does. */
final class TouchRouter {
    interface Backend {AnswerClickOutcome run();}
    static AnswerClickOutcome execute(TouchPriority priority,BooleanSupplier allowed,Backend node,Backend root,Backend gesture){
        Backend[] backends={node,root,gesture};
        for(int index:priority.order()){
            try {if(!allowed.getAsBoolean())return AnswerClickOutcome.cancelled();}
            catch(RuntimeException e){return AnswerClickOutcome.cancelled();}
            if(backends[index]==null)continue;
            AnswerClickOutcome result;
            try {result=backends[index].run();}
            catch(RuntimeException e){return AnswerClickOutcome.uncertain();}
            if(!result.allowFallback)return result;
        }
        return AnswerClickOutcome.accessibility(false);
    }
}
