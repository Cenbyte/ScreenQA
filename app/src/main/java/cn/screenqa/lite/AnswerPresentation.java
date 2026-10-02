package cn.screenqa.lite;

import java.util.regex.*;

final class AnswerPresentation {
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
