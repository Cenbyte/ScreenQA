package cn.screenqa.lite;

/** One fresh re-resolution of a cached AI answer, only when no action could have been delivered. */
final class AnswerRetry {
    final String type,answer,key;
    final int epoch,generation;
    final long started;
    int attempts=1;
    boolean pending;
    private long retryAt;
    AnswerRetry(String type,String answer,String key,int epoch,int generation,long now){
        this.type=type;this.answer=answer;this.key=key;this.epoch=epoch;this.generation=generation;started=now;
    }
    void failed(long now,boolean uncertain){pending=!uncertain&&attempts<2&&now-started<6000;retryAt=now+300;}
    boolean canRetry(long now,int epoch,int generation,String exactStem){
        return pending&&attempts<2&&now>=retryAt&&now-started<6000&&this.epoch==epoch&&this.generation==generation&&key.equals(exactStem);
    }
    void retrying(){pending=false;attempts++;}
}
