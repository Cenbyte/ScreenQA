package cn.screenqa.lite;

import java.util.Locale;

/** Strict visible-label matching; terminal exam actions are never navigation targets. */
final class NextButtonMatcher {
    static int confidence(String raw,boolean lowerHalf) {
        if(raw==null)return 0;
        String value=QuestionTracker.normalize(raw).replaceAll("[→›>»❯]+$","").toLowerCase(Locale.ROOT);
        if(value.matches(".*(?:提交|交卷|完成考试|结束答题|退出|返回|查看成绩|finish|submit|exit).*"))return 0;
        if(value.matches("下一题|下一页|继续答题"))return 3;
        if(lowerHalf&&value.matches("继续|next|continue"))return 2;
        return 0;
    }
}
