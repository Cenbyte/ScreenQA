package cn.screenqa.lite;

import java.util.Random;

/** Preset local messages; no remote content or payment tracking. */
final class SponsorBlessings {
    private static final String[] MESSAGES={
        "愿你每一次认真，都能慢慢开花结果。",
        "愿你学有所获，也有时间享受生活。",
        "愿你遇到难题有思路，走到路口有方向。",
        "愿今天的小进步，成为明天的底气。",
        "愿你的努力被看见，你的热爱有回响。",
        "愿你眼里有光，心里有喜欢的远方。",
        "愿你保持好奇，找到属于自己的答案。",
        "愿你忙有所值，闲有所趣。",
        "愿你有乘风而行的勇气，也有慢慢来的从容。",
        "愿你一路遇见善意，也把温暖留给自己。",
        "愿你睡个好觉，醒来又是充满希望的一天。",
        "愿你的大学时光，有收获，也有值得珍藏的快乐。",
        "愿你翻过这一页，遇见新的可能。",
        "愿你所期待的好事，正在路上。",
        "愿你照顾好自己，把日子过成喜欢的样子。",
        "愿你一步一步，走到心中的目的地。"
    };
    private static final Random RANDOM=new Random();
    private static int previous=-1;
    static synchronized String next(){
        int index=RANDOM.nextInt(previous<0?MESSAGES.length:MESSAGES.length-1);
        if(index>=previous&&previous>=0)index++;
        previous=index;return MESSAGES[index];
    }
}
