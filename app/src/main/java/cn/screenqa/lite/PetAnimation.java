package cn.screenqa.lite;

/** TAP completes once, independently of recognition/network state. */
final class PetAnimation {
    private static final int[] IDLE={650,110,110,420,550,110,380,150,110,450,160,500};
    static final int TAP_FRAMES=16,TAP_FRAME_MS=90;
    private long start;
    private boolean tapping;
    void idle(long now){start=now;tapping=false;}
    void tap(long now){if(!tapping){start=now;tapping=true;}}
    boolean tapping(long now){update(now);return tapping;}
    private void update(long now){if(tapping&&now-start>=TAP_FRAMES*TAP_FRAME_MS){start+=TAP_FRAMES*TAP_FRAME_MS;tapping=false;}}
    int frame(long now){
        update(now);long elapsed=Math.max(0,now-start);
        if(tapping)return (int)(elapsed/TAP_FRAME_MS);
        int total=0;for(int d:IDLE)total+=d;elapsed%=total;
        for(int i=0;i<IDLE.length;i++){if(elapsed<IDLE[i])return i;elapsed-=IDLE[i];}return 0;
    }
    long delay(long now){
        update(now);long elapsed=Math.max(0,now-start);
        if(tapping)return TAP_FRAME_MS-elapsed%TAP_FRAME_MS;
        int total=0;for(int d:IDLE)total+=d;elapsed%=total;
        for(int d:IDLE){if(elapsed<d)return d-elapsed;elapsed-=d;}return 1;
    }
}
