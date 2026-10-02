package cn.screenqa.lite;

import java.util.*;
import java.util.regex.Pattern;

/** Conservative on-device grouping; ambiguous pages keep the semantic fallback. */
final class LocalQuestionLocator {
    private static final Pattern START=Pattern.compile("^(?:第\\s*\\d+\\s*题|\\d{1,3}[、．.]\\s*\\D|[【\\[]?(?:单选题|多选题|选择题|填空题|判断题|简答题)[】\\]]?).*");
    private static final Pattern OPTION=Pattern.compile("^[A-HＡ-Ｈ][.．、,，:：)）\\s].*|^[A-HＡ-Ｈ]$");
    private static final Pattern END=Pattern.compile("^(?:参考答案|正确答案|答案解析|试题解析|本题解析|解析[：:]|知识点[：:]).*");
    static final class Candidate {
        final ScreenDocument document;
        final String type;
        final List<Integer> stem,all;
        Candidate(ScreenDocument document,String type,List<Integer> stem,List<Integer> all) {
            this.document=document;this.type=type;this.stem=stem;this.all=all;
        }
    }
    static String type(String text) {
        // Explicit labels outrank blank symbols inside a choice question.
        if(text.contains("选择题")||text.contains("单选题")||text.contains("多选题"))return "choice";
        if(text.contains("判断题"))return "true_false";
        if(text.contains("填空题")||text.contains("___")||text.contains("＿＿"))return "fill_blank";
        if(text.contains("简答题"))return "short_answer";
        return "";
    }
    static Candidate locate(ScreenDocument doc) {
        List<Integer> starts=new ArrayList<>();
        for(int i=0;i<doc.lines.size();i++) {
            String s=doc.lines.get(i).text;
            if(START.matcher(s).matches()) {
                // A type label followed by its numbered stem is one anchor.
                if(!starts.isEmpty()&&i==starts.get(starts.size()-1)+1&&
                        doc.lines.get(i-1).text.matches("[【\\[]?(单选题|多选题|选择题|填空题|判断题|简答题)[】\\]]?"))continue;
                starts.add(i);
            }
        }
        if(starts.isEmpty())return null;
        Candidate best=null;double distance=Double.MAX_VALUE;
        for(int n=0;n<starts.size();n++) {
            int from=starts.get(n),to=n+1<starts.size()?starts.get(n+1):doc.lines.size();
            // Shared reading material needs model selection, not a guessed crop.
            for(int i=0;i<from;i++)if(doc.lines.get(i).text.matches(".*(?:阅读材料|根据材料|阅读下文|阅读下面).*"))return null;
            List<ScreenDocument.Line> group=new ArrayList<>();
            for(int i=from;i<to;i++) {
                ScreenDocument.Line l=doc.lines.get(i);
                if(END.matcher(l.text).matches())break;
                if(!group.isEmpty()&&l.top-group.get(group.size()-1).bottom>Math.max(180,doc.height/8))break;
                group.add(l);
            }
            ScreenDocument candidate=new ScreenDocument(group,doc.width,doc.height);
            StringBuilder text=new StringBuilder();List<Integer> stem=new ArrayList<>(),all=new ArrayList<>();
            Set<Character> options=new HashSet<>();boolean inOptions=false;
            for(int i=0;i<candidate.lines.size();i++) {
                String s=candidate.lines.get(i).text;text.append(s).append('\n');all.add(i+1);
                if(OPTION.matcher(s).matches()){inOptions=true;char ch=s.charAt(0);options.add(ch>127?(char)(ch-'Ａ'+'A'):ch);}
                if(!inOptions)stem.add(i+1);
            }
            String kind=type(text.toString());
            if(options.containsAll(Arrays.asList('A','B','C','D')))kind="choice";
            if(kind.equals("true_false")) {
                stem.clear();boolean afterOptions=false;
                for(int i=0;i<candidate.lines.size();i++) {
                    if(AnswerTargetResolver.isJudgmentOptionLine(candidate.lines.get(i).text))afterOptions=true;
                    if(!afterOptions)stem.add(i+1);
                }
            }
            if(kind.isEmpty()||stem.isEmpty()||QuestionTracker.normalize(candidate.text(stem)).length()<8)continue;
            // Missing/cut-off options must be assessed by the fallback model.
            if(kind.equals("choice")&&!options.containsAll(Arrays.asList('A','B','C','D')))continue;
            if(candidate.lines.get(candidate.lines.size()-1).bottom>doc.height-24)continue;
            double center=(candidate.lines.get(0).top+candidate.lines.get(candidate.lines.size()-1).bottom)/2.0;
            double d=Math.abs(center-doc.height/2.0);
            if(d<distance){distance=d;best=new Candidate(candidate,kind,stem,all);}
        }
        return best;
    }
}
