package cn.screenqa.lite;

/** Reading the retained answer never blocks the next-question monitor. */
final class AnswerFollowup {
    private String key="";
    private long scanAt;
    boolean retaining(){return !key.isEmpty();}
    String key(){return key;}
    void begin(String question,boolean automaticExecution,long now){
        key=QuestionTracker.normalize(question);scanAt=now+(automaticExecution?200:400);
    }
    long remaining(long now){return retaining()?Math.max(0,scanAt-now):0;}
    boolean same(String question){return retaining()&&key.equals(QuestionTracker.normalize(question));}
    boolean located(String question){
        String next=QuestionTracker.normalize(question);
        if(!retaining()||next.isEmpty()||same(next))return false;
        reset();return true;
    }
    void reset(){key="";scanAt=0;}
}
