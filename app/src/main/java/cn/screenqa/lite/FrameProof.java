package cn.screenqa.lite;

/** Recent proof belongs to the pending action; any changed frame revokes it immediately. */
final class FrameProof {
    private long verifiedAt;
    private boolean matching;
    void observe(long now,boolean matching){this.matching=matching;verifiedAt=now;}
    boolean recent(long now){return matching&&now>=verifiedAt&&now-verifiedAt<=450;}
}
