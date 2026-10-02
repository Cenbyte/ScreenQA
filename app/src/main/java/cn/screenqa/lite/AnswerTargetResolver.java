package cn.screenqa.lite;

import java.util.*;
import java.util.regex.*;

/** Maps one short model answer to exactly one visible option line. */
final class AnswerTargetResolver {
    static final class Target {
        final int lineId;
        final String text;
        Target(int lineId,String text){this.lineId=lineId;this.text=text;}
    }
    private static final Pattern CHOICE=Pattern.compile("^([A-HＡ-Ｈ])(?:[.．、,，:：)）\\s]+(.+))?$");
    private static final Pattern MULTI=Pattern.compile("多选|多项|哪些选项|哪几项|选择所有|至少两项");
    private static String clean(String value) {return QuestionTracker.normalize(value).replace('Ａ','A').replace('Ｂ','B').replace('Ｃ','C').replace('Ｄ','D').replace('Ｅ','E').replace('Ｆ','F').replace('Ｇ','G').replace('Ｈ','H');}
    private static int truth(String value) {
        String s=clean(value).replaceFirst("^(?:答案|判断)[:：]","");
        if(s.equals("正确")||s.equals("对")||s.equals("是")||s.equals("√")||s.equals("✓")||s.equals("TRUE"))return 1;
        if(s.equals("错误")||s.equals("错")||s.equals("否")||s.equals("×")||s.equals("✗")||s.equals("FALSE"))return -1;
        return 0;
    }
    static boolean isJudgmentOptionLine(String value) {
        Matcher match=CHOICE.matcher(value.trim());
        return truth((match.matches()&&match.group(2)!=null?match.group(2):value).toUpperCase(Locale.ROOT))!=0;
    }
    static Target resolve(String type,String answer,ScreenDocument doc,List<Integer> questionIds,List<Integer> stemIds) {
        if(doc==null||answer==null||questionIds==null||questionIds.isEmpty())return null;
        if(!type.equals("choice")&&!type.equals("true_false"))return null;
        Set<Integer> stem=stemIds==null?Collections.emptySet():new HashSet<>(stemIds);
        String stemText=stemIds==null?"":doc.text(stemIds);
        if(type.equals("choice")&&(MULTI.matcher(stemText).find()||
                MULTI.matcher(doc.text(questionIds)).find()))return null;
        String letter="";int wantedTruth=0;
        if(type.equals("choice")) {
            String compact=AnswerPresentation.compact(type,answer).toUpperCase(Locale.ROOT);
            if(!compact.matches("[A-H]"))return null;
            letter=compact;
        } else {
            wantedTruth=truth(answer.toUpperCase(Locale.ROOT));if(wantedTruth==0)return null;
        }
        List<Target> options=new ArrayList<>();Set<String> labels=new HashSet<>();
        int positive=0,negative=0;
        for(int id:questionIds) {
            if(id<1||id>doc.lines.size()||stem.contains(id))continue;
            String raw=doc.lines.get(id-1).text.trim();
            Matcher match=CHOICE.matcher(raw);String label="",content=raw;
            if(match.matches()) {
                label=clean(match.group(1)).toUpperCase(Locale.ROOT);
                content=match.group(2)==null?"":match.group(2).trim();
                // OCR merged multiple options onto one line; no unambiguous tap box.
                if(Pattern.compile("[\\s　][A-HＡ-Ｈ][.．、:：)）]").matcher(content).find())return null;
                if(!labels.add(label))return null;
            }
            int direction=truth(content.toUpperCase(Locale.ROOT));
            if(direction>0)positive++;else if(direction<0)negative++;
            if(type.equals("choice")&&label.equals(letter))options.add(new Target(id,raw));
            if(type.equals("true_false")&&direction==wantedTruth)options.add(new Target(id,raw));
        }
        if(type.equals("choice")&&labels.size()<2)return null;
        if(type.equals("true_false")&&(positive!=1||negative!=1))return null;
        return options.size()==1?options.get(0):null;
    }
    /** Finds the visible option group below the stem without trusting an AI-drawn question box. */
    static Target resolveNearStem(String type,String answer,ScreenDocument doc,List<Integer> stemIds) {
        if(doc==null||stemIds==null||stemIds.isEmpty()||
                (!"choice".equals(type)&&!"true_false".equals(type)))return null;
        if("choice".equals(type)&&MULTI.matcher(doc.text(stemIds)).find())return null;
        List<Integer> optionIds=nearbyOptionIds(type,doc,stemIds);
        if(optionIds.size()<2)return null;
        return resolve(type,answer,doc,optionIds,Collections.emptyList());
    }
    static List<Integer> nearbyOptionIds(String type,ScreenDocument doc,List<Integer> stemIds) {
        if(doc==null||stemIds==null||stemIds.isEmpty())return Collections.emptyList();
        int stemBottom=0;
        for(int id:stemIds)if(id>=1&&id<=doc.lines.size()) {
            ScreenDocument.Line line=doc.lines.get(id-1);
            if(!isOption(type,line.text))stemBottom=Math.max(stemBottom,line.bottom);
        }
        if(stemBottom==0)return Collections.emptyList();
        List<Integer> optionIds=new ArrayList<>();
        int limit=Math.min(doc.height,stemBottom+Math.max(500,doc.height*3/5));
        int lastBottom=stemBottom;
        for(int i=0;i<doc.lines.size();i++) {
            ScreenDocument.Line line=doc.lines.get(i);
            if(line.top<stemBottom-12||line.top>limit)continue;
            String raw=line.text.trim();
            if(!optionIds.isEmpty()&&line.top-lastBottom>Math.max(160,doc.height/8))break;
            if(raw.matches("^(?:第\\s*\\d+\\s*题|\\d{1,3}[、．.]\\s*\\D).*"))break;
            if(!isOption(type,raw))continue;
            optionIds.add(i+1);lastBottom=line.bottom;
            if(optionIds.size()>8)return Collections.emptyList();
        }
        return optionIds;
    }
    private static boolean isOption(String type,String raw) {
        return "choice".equals(type)?CHOICE.matcher(raw.trim()).matches():isJudgmentOptionLine(raw);
    }
    static String optionLabel(String raw) {
        Matcher match=CHOICE.matcher(raw==null?"":raw.trim());
        return match.matches()?clean(match.group(1)).toUpperCase(Locale.ROOT):"";
    }
    static int labeledOptionCount(String type,ScreenDocument doc,List<Integer> ids) {
        if(doc==null||ids==null)return 0;
        int count=0;for(int id:ids)if(id>=1&&id<=doc.lines.size()&&isOption(type,doc.lines.get(id-1).text))count++;
        return count;
    }
    static List<Integer> everyLine(ScreenDocument doc) {
        List<Integer> ids=new ArrayList<>();for(int i=1;i<=doc.lines.size();i++)ids.add(i);return ids;
    }
}
