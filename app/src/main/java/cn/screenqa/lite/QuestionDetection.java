package cn.screenqa.lite;

import org.json.*;
import java.util.*;

final class QuestionDetection {
    final boolean found,complete;
    final String type,answer;
    final List<String> answers;
    final List<Integer> stemIds,questionIds;
    private QuestionDetection(boolean found,boolean complete,String type,String answer,List<String> answers,List<Integer> stem,List<Integer> all) {
        this.found=found;this.complete=complete;this.type=type;this.answer=answer;this.answers=answers;stemIds=stem;questionIds=all;
    }
    static QuestionDetection located(LocalQuestionLocator.Candidate candidate) {
        return new QuestionDetection(true,false,candidate.type,"",Collections.emptyList(),candidate.stem,candidate.all);
    }
    static QuestionDetection parse(String raw,ScreenDocument doc) throws JSONException {
        String value=raw.trim();
        if(value.startsWith("```")) {
            int newline=value.indexOf('\n');int end=value.lastIndexOf("```");
            if(newline>=0 && end>newline)value=value.substring(newline+1,end).trim();
        }
        JSONObject object=new JSONObject(value);
        if(!(object.get("has_question") instanceof Boolean))throw new JSONException("has_question must be boolean");
        if(!object.getBoolean("has_question"))return new QuestionDetection(false,false,"","",Collections.emptyList(),Collections.emptyList(),Collections.emptyList());
        String type=object.getString("question_type");
        if(!Arrays.asList("fill_blank","true_false","short_answer","choice").contains(type))throw new JSONException("Unknown question type");
        List<Integer> stem=ids(object.getJSONArray("stem_line_ids"),doc.lines.size());
        List<Integer> all=ids(object.getJSONArray("question_line_ids"),doc.lines.size());
        if(!all.containsAll(stem))throw new JSONException("Stem must be inside question");
        // A separate progress heading is page chrome, not part of the actual stem.
        if(stem.size()>1){
            List<Integer> actual=new ArrayList<>(stem);
            actual.removeIf(id->"QUESTION".equalsIgnoreCase(doc.lines.get(id-1).text.trim()));
            if(!actual.isEmpty())stem=Collections.unmodifiableList(actual);
        }
        if(QuestionTracker.normalize(doc.text(stem)).length()<4)throw new JSONException("Stem too short");
        if(!(object.get("complete") instanceof Boolean))throw new JSONException("complete must be boolean");
        boolean complete=object.getBoolean("complete");
        List<String> answers=new ArrayList<>();
        JSONArray array=object.optJSONArray("answers");
        if(array!=null&&"fill_blank".equals(type)) {
            if(array.length()>12)throw new JSONException("Too many answers");
            for(int i=0;i<array.length();i++) {
                String item=TextAnswer.clean(array.getString(i));
                if(item.isEmpty()||item.length()>1000)throw new JSONException("Invalid blank answer");
                answers.add(item);
            }
        }
        String answer=TextAnswer.clean(object.optString("answer",""));
        if(!answers.isEmpty())answer=String.join("\n",answers);
        else if(!answer.isEmpty()&&("fill_blank".equals(type)||"short_answer".equals(type)))answers.add(answer);
        if(complete && answer.isEmpty())throw new JSONException("Empty answer");
        if(answer.length()>6000)throw new JSONException("Answer too long");
        return new QuestionDetection(true,complete,type,complete?answer:"题目条件不完整，请露出完整题干；含图或复杂公式时请核对原题。",
                complete?Collections.unmodifiableList(answers):Collections.emptyList(),stem,all);
    }
    private static List<Integer> ids(JSONArray data,int max) throws JSONException {
        if(data.length()<1 || data.length()>max)throw new JSONException("Invalid line count");
        List<Integer> result=new ArrayList<>();
        for(int i=0;i<data.length();i++) {
            Object raw=data.get(i);
            if(!(raw instanceof Number))throw new JSONException("ID must be a number");
            double number=((Number)raw).doubleValue();int id=((Number)raw).intValue();
            if(number!=id || id<1 || id>max || result.contains(id))throw new JSONException("Invalid line ID");
            result.add(id);
        }
        Collections.sort(result);return Collections.unmodifiableList(result);
    }
    String typeName() {
        return switch(type) {case "fill_blank"->"填空题";case "true_false"->"判断题";case "short_answer"->"简答题";case "choice"->"选择题";default->"未识别";};
    }
}
