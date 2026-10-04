package cn.screenqa.lite;

import java.util.regex.*;

final class AnswerPresentation {
    static String questionSummary(String stem,String generated){
        if(stem.codePointCount(0,stem.length())<=20)return stem;
        String summary=generated==null?"":generated.trim().replaceAll("[\\r\\n]+"," ");
        if(summary.isEmpty())summary=stem.replaceAll("[\\r\\n]+"," ").trim();
        return summary.codePointCount(0,summary.length())<=20?summary:
                summary.substring(0,summary.offsetByCodePoints(0,19))+"…";
    }
    // About five characters per second, with time to find the copy/close controls.
    static long visibleMillis(String answer,boolean textAnswer) {
        int count=answer.codePointCount(0,answer.length());
        return Math.min(90000L,Math.max(textAnswer?20000L:10000L,6000L+count*200L));
    }
    static int bubbleWidthDp(String answer) {
        int count=answer.codePointCount(0,answer.length());
        return Math.min(320,Math.max(184,144+count*4));
    }
    static String compact(String type,String answer) {
        String s=answer.trim();
        if(!"choice".equals(type))return s;
        Matcher match=Pattern.compile("^(?:答案\\s*[:：]?\\s*)?([A-H](?:[、,，\\s]*[A-H])*)(?=$|[.．、:：\\s（(])").matcher(s);
        if(match.find())return match.group(1).replaceAll("[、,，\\s]", "");
        return s;
    }
    static boolean inlineChoice(String type,String answer) {
        return "choice".equals(type)&&compact(type,answer).matches("[A-H]{1,8}");
    }
}
