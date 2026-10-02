package cn.screenqa.lite;

import java.util.List;

final class TextAnswer {
    private TextAnswer() {}
    static String clean(String value) {
        String result=value==null?"":value.trim();
        if(result.startsWith("```")) {
            int first=result.indexOf('\n'),last=result.lastIndexOf("```");
            if(first>=0&&last>first)result=result.substring(first+1,last).trim();
        }
        if(result.startsWith("**")&&result.endsWith("**")&&result.length()>4)
            result=result.substring(2,result.length()-2).trim();
        result=result.replaceFirst("^(?:答案|AI回答|参考答案)\\s*[：:]\\s*","").trim();
        return result;
    }
    static String copyPayload(List<String> answers) {
        StringBuilder result=new StringBuilder();
        for(String answer:answers) {
            String value=clean(answer);
            if(value.isEmpty())continue;
            if(result.length()>0)result.append('\n');
            result.append(value);
        }
        return result.toString();
    }
    static String display(List<String> answers) {
        if(answers.size()<2)return copyPayload(answers);
        StringBuilder result=new StringBuilder();
        for(int i=0;i<answers.size();i++)result.append(i+1).append(". ").append(answers.get(i)).append('\n');
        return result.toString().trim();
    }
}
