package cn.screenqa.lite;

/** Bounded recovery of the existing projection surface, without reusing a projection grant. */
final class FrameHealth {
    private long lastRepair;
    private long windowStart;
    private int repairs,windowRepairs;
    boolean shouldRepair(long now,long lastArrival,boolean busy){
        return !busy&&now-lastArrival>=3500&&(lastRepair==0||now-lastRepair>=10000)&&!exhausted(now);
    }
    boolean exhausted(long now){return windowRepairs>=3&&now-windowStart<60000;}
    void repaired(long now){
        if(windowRepairs==0||now-windowStart>=60000){windowStart=now;windowRepairs=0;}
        lastRepair=now;repairs++;windowRepairs++;
    }
    int repairs(){return repairs;}
}
