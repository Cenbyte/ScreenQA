package cn.screenqa.lite;

/** Execution priority is independent of the recognition-source strategy. */
enum TouchPriority {
    AUTO("自动回退",new int[]{0,1,2}),
    ROOT_FIRST("Root 优先",new int[]{1,0,2}),
    ACCESSIBILITY_FIRST("无障碍优先",new int[]{0,2,1});
    final String label;
    private final int[] order;
    TouchPriority(String label,int[] order){this.label=label;this.order=order;}
    int[] order(){return order.clone();}
    static TouchPriority fromStored(int value){return value>=0&&value<values().length?values()[value]:AUTO;}
}
