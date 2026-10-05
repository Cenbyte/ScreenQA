package cn.screenqa.lite;

import java.util.*;
import org.json.*;

/** Main-thread guard for an AI-located request. OCR recognition itself is unchanged. */
final class RequestQuestionGuard {
    private final ScreenDocument submitted;
    private QuestionDetection located;
    private int first,last;
    RequestQuestionGuard(ScreenDocument submitted){this.submitted=submitted;}
    boolean locate(QuestionDetection detection,ScreenDocument current){
        located=detection;
        if(detection.found){
            first=Collections.min(detection.questionIds)-1;
            last=Collections.max(detection.questionIds)-1;
        }
        return accepts(current);
    }
    boolean accepts(ScreenDocument current){
        // Finish the small, fixed-fast locator before deciding which page text is relevant.
        if(current==null||current.lines.isEmpty()||current.width!=submitted.width||current.height!=submitted.height)return false;
        if(located==null)return true;
        if(!located.found)return QuestionStability.signature(submitted,null)
                .equals(QuestionStability.signature(current,null));
        return offset(current)!=Integer.MIN_VALUE;
    }
    private int offset(ScreenDocument current){
        if(current==null||current.width!=submitted.width||current.height!=submitted.height)return Integer.MIN_VALUE;
        int length=last-first+1,match=Integer.MIN_VALUE;
        // Include every intervening line, including material the model did not select.
        // Exact text comparison preserves negations, numeric conditions and all options.
        for(int start=0;start+length<=current.lines.size();start++){
            boolean same=true;
            for(int i=0;i<length;i++){
                if(!QuestionTracker.normalize(submitted.lines.get(first+i).text)
                        .equals(QuestionTracker.normalize(current.lines.get(start+i).text))){same=false;break;}
            }
            if(same){
                // An appended option changes the question even if the old options remain visible.
                int after=start+length,oldAfter=last+1;
                if(after<current.lines.size()&&option(current.lines.get(after).text)&&
                        (oldAfter>=submitted.lines.size()||!QuestionTracker.normalize(current.lines.get(after).text)
                        .equals(QuestionTracker.normalize(submitted.lines.get(oldAfter).text))))continue;
                if(match!=Integer.MIN_VALUE)return Integer.MIN_VALUE; // Ambiguous duplicate: don't act.
                match=start-first;
            }
        }
        return match;
    }
    private static boolean option(String text){
        return text.trim().matches("^[A-HＡ-Ｈ][.．、,，:：)）\\s]+.*");
    }
    QuestionDetection rebase(QuestionDetection result,ScreenDocument current)throws JSONException{
        if(!result.found)return accepts(current)?result:null;
        int shift=offset(current);
        if(shift==Integer.MIN_VALUE)return null;
        JSONObject value=new JSONObject().put("has_question",true).put("complete",result.complete)
                .put("question_type",result.type).put("answer",result.answer)
                .put("answers",new JSONArray(result.answers)).put("question_summary",result.summary)
                .put("stem_line_ids",ids(result.stemIds,shift)).put("question_line_ids",ids(result.questionIds,shift));
        return QuestionDetection.parse(value.toString(),current);
    }
    private static JSONArray ids(List<Integer> source,int shift){
        JSONArray ids=new JSONArray();for(int id:source)ids.put(id+shift);return ids;
    }
}
