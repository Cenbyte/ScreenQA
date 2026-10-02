package cn.screenqa.lite;

enum AutoAnswerStrategy {
    HYBRID, ACCESSIBILITY_FIRST, VISION_FIRST;
    static AutoAnswerStrategy fromStored(int value) {
        return value>=0&&value<values().length?values()[value]:HYBRID;
    }
    boolean readNodesFirst() {return this!=VISION_FIRST;}
    boolean retryNodesBeforeVision(){return this==ACCESSIBILITY_FIRST;}
}
