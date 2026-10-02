package cn.screenqa.lite;

public final class QuestionTrackerTest {
    static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
    public static void main(String[] args) {
        QuestionTracker t=new QuestionTracker();
        String q="以下哪个是质数？ A. 4 B. 7 C. 9";
        t.observe(q);check(!t.ready(0),"must wait for stable frame");
        t.observe(q);check(t.ready(0),"stable question should submit");
        int first=t.begin();check(!t.ready(0),"must not duplicate in-flight request");
        t.observe(q.replace(" ","\n"));check(t.isCurrent(first),"whitespace should not retrigger");
        t.complete(first,true,0);check(!t.ready(99999),"answered question must not repeat");
        t.observe(q+" D. 11");check(!t.isCurrent(first),"option changes invalidate answer");
        check(!t.complete(first,true,100),"late answer must be rejected");
        t.observe(q+" D. 11");check(t.ready(100),"new options should submit");
        int next=t.begin();t.complete(next,false,100);check(!t.ready(500),"failure needs backoff");
        check(t.ready(10100),"failure should retry after cooldown");
        t.complete(t.begin(),false,10100);t.complete(t.begin(),false,20100);
        check(!t.ready(999999),"must cap automatic retries");
        t.reset();t.observe(q);t.observe(q);check(t.ready(0),"manual restart resets retries");
        int stale=t.begin();t.observe("");check(!t.isCurrent(stale),"blank screen invalidates old question");
        t.observe("");check(!t.ready(999999),"blank frame must never submit");
        String prefix="共同题干".repeat(20);
        t.observe(prefix+"A甲 B乙");t.observe(prefix+"A甲 B乙");int longToken=t.begin();
        t.observe(prefix+"A甲 B丙");check(!t.isCurrent(longToken),"changes after first 50 characters count");
        System.out.println("PASS: stability, deduplication, option changes, stale answers, blank frames, retry bounds, reset");
    }
}
