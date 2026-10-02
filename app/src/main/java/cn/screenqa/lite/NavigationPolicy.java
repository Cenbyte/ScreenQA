package cn.screenqa.lite;

import java.util.*;

/** Missing node text is unknown evidence, never proof that an OCR question changed. */
final class NavigationPolicy {
    private static String proofText(String text){return QuestionTracker.normalize(text).replace('內','内');}
    enum Evidence { SAME, DIFFERENT, UNKNOWN }
    static Evidence evidence(String expected,String visible,String reliableNewStem) {
        String old=QuestionTracker.normalize(expected),text=QuestionTracker.normalize(visible);
        if(old.length()<6)return Evidence.UNKNOWN;
        if(text.contains(old))return Evidence.SAME;
        String next=QuestionTracker.normalize(reliableNewStem);
        if(next.length()<6||old.contains(next)||next.contains(old))return Evidence.UNKNOWN;
        return Evidence.DIFFERENT;
    }
    static boolean waitForScan(long now,long started){return now-started<250;}
    static boolean mayRetryClick(int attempts,boolean sameQuestion,boolean unchangedScreen){
        return attempts<2&&sameQuestion&&unchangedScreen;
    }
    static ScreenDocument.Line uniqueNext(ScreenDocument doc){
        ScreenDocument.Line best=null;int score=0,count=0;
        for(ScreenDocument.Line line:doc.navigationLines){
            int confidence=NextButtonMatcher.confidence(line.text,line.top>=doc.height/2);
            if(confidence>score){best=line;score=confidence;count=1;}
            else if(confidence==score&&confidence>0)count++;
        }
        return count==1?best:null;
    }
    static List<ScreenDocument.Line> stemProof(ScreenDocument doc,String expected){
        String stem=proofText(expected);
        if(stem.length()<6)return Collections.emptyList();
        List<ScreenDocument.Line> best=Collections.emptyList();int length=Integer.MAX_VALUE;
        for(int start=0;start<doc.lines.size();start++){
            StringBuilder text=new StringBuilder();List<ScreenDocument.Line> proof=new ArrayList<>();
            for(int i=start;i<doc.lines.size()&&i<start+12;i++){
                ScreenDocument.Line line=doc.lines.get(i);proof.add(line);text.append(proofText(line.text));
                if(text.toString().contains(stem)){
                    if(text.length()<length){best=new ArrayList<>(proof);length=text.length();}break;
                }
                if(text.length()>stem.length()+100)break;
            }
        }
        return best;
    }
    static boolean terminalVisible(ScreenDocument doc){return !doc.terminalLines.isEmpty();}
    /** Continuity is only used after our own verified scroll, never to infer a new question. */
    static boolean scrollContinuation(ScreenDocument before,ScreenDocument after){return continuationProof(before,after).size()>=2;}
    static List<ScreenDocument.Line> continuationProof(ScreenDocument before,ScreenDocument after){
        Map<String,List<ScreenDocument.Line>> old=optionRows(before),next=optionRows(after);
        int firstOption=after.height;
        for(List<ScreenDocument.Line> row:next.values())firstOption=Math.min(firstOption,row.get(0).top);
        for(ScreenDocument.Line line:after.lines){
            String text=proofText(line.text);
            if(line.top>=firstOption||text.length()<6)continue;
            boolean known=false;
            for(ScreenDocument.Line prior:before.lines)
                if(proofText(prior.text).contains(text)){known=true;break;}
            if(!known)return Collections.emptyList(); // A new visible stem must not inherit old answer continuity.
        }
        List<ScreenDocument.Line> proof=new ArrayList<>();int matched=0;
        for(Map.Entry<String,List<ScreenDocument.Line>> entry:next.entrySet()){
            List<ScreenDocument.Line> prior=old.get(entry.getKey());
            if(prior==null)continue;
            String a=rowText(prior),b=rowText(entry.getValue());
            // Bare A/B and generic yes/no are not question identity evidence.
            if(a.length()>=4&&a.equals(b)){matched++;proof.addAll(entry.getValue());}
        }
        return matched>=2?proof:Collections.emptyList();
    }
    private static String rowText(List<ScreenDocument.Line> lines){
        StringBuilder text=new StringBuilder();for(ScreenDocument.Line line:lines)text.append(QuestionTracker.normalize(line.text));
        return text.toString();
    }
    private static Map<String,List<ScreenDocument.Line>> optionRows(ScreenDocument doc){
        Map<String,List<ScreenDocument.Line>> result=new LinkedHashMap<>();Set<String> ambiguous=new HashSet<>();
        for(ScreenDocument.Line line:doc.lines){
            String label=AnswerTargetResolver.optionLabel(line.text);if(label.isEmpty())continue;
            List<ScreenDocument.Line> row=new ArrayList<>();row.add(line);
            if(QuestionTracker.normalize(line.text).length()==1){
                for(ScreenDocument.Line other:doc.lines)
                    if(other.left>=line.right&&other.top<line.bottom&&other.bottom>line.top&&
                            AnswerTargetResolver.optionLabel(other.text).isEmpty())row.add(other);
            }
            if(result.put(label,row)!=null)ambiguous.add(label);
        }
        for(String label:ambiguous)result.remove(label);return result;
    }
}
