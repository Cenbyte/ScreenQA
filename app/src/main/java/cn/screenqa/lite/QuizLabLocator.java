package cn.screenqa.lite;

import java.util.*;

/** The supplied Quiz Lab DOM has no question-type label; scope this adapter to its package. */
final class QuizLabLocator {
    static LocalQuestionLocator.Candidate locate(String pkg,ScreenDocument doc) {
        if(!"io.codex.quizlab".equals(pkg)||doc==null)return null;
        boolean title=false,progress=false;
        ScreenDocument.Line marker=null;
        Map<String,ScreenDocument.Line> options=new LinkedHashMap<>();
        for(ScreenDocument.Line line:doc.lines) {
            String text=QuestionTracker.normalize(line.text);
            if(text.equals("答题实验室"))title=true;
            if(text.matches("\\d+/\\d+"))progress=true;
            if(text.matches("#[A-Z]\\d{3}")) {
                if(marker!=null)return null;
                marker=line;
            }
            String label=AnswerTargetResolver.optionLabel(line.text);
            if(!label.matches("[A-D]"))continue;
            if(options.put(label,line)!=null)return null;
        }
        if(!title||!progress||marker==null||options.size()!=4)return null;
        int first=doc.height;
        for(ScreenDocument.Line line:options.values())first=Math.min(first,line.top);
        List<ScreenDocument.Line> stem=new ArrayList<>();
        for(ScreenDocument.Line line:doc.lines) {
            String text=QuestionTracker.normalize(line.text);
            // Exclude repeated accessible parent/group labels covering the whole question card.
            if(line.top<marker.bottom||line.bottom>first||line.bottom-line.top>doc.height/4||
                    text.equals("请选择一个答案")||text.isEmpty())continue;
            stem.add(line);
        }
        if(stem.isEmpty())return null;
        StringBuilder identity=new StringBuilder();
        for(ScreenDocument.Line line:stem)identity.append(line.text);
        if(QuestionTracker.normalize(identity.toString()).length()<6)return null;
        List<ScreenDocument.Line> lines=new ArrayList<>(stem);
        for(String label:Arrays.asList("A","B","C","D"))lines.add(options.get(label));
        ScreenDocument question=new ScreenDocument(lines,doc.width,doc.height);
        List<Integer> stemIds=new ArrayList<>(),all=new ArrayList<>();
        for(int i=0;i<question.lines.size();i++) {
            all.add(i+1);
            if(stem.contains(question.lines.get(i)))stemIds.add(i+1);
        }
        return new LocalQuestionLocator.Candidate(question,"choice",stemIds,all);
    }
}
