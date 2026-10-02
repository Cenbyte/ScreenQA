package cn.screenqa.lite;

import java.text.Normalizer;
import java.util.*;

/** Stable text around blank slots, shared by OCR, fresh-node validation and navigation. */
final class TextQuestionIdentity {
    private final List<String> parts;
    private final int length;
    private TextQuestionIdentity(List<String> parts,int length){this.parts=parts;this.length=length;}
    static TextQuestionIdentity from(ScreenDocument document,List<Integer> ids) {
        StringBuilder stem=new StringBuilder();
        for(int id:ids)if(id>0&&id<=document.lines.size()) {
            String line=Normalizer.normalize(document.lines.get(id-1).text,Normalizer.Form.NFKC).trim();
            if(line.matches("[【\\[]?(填空题|简答题|问答题|主观题)[】\\]]?"))continue;
            // Question numbering is often exposed in a separate WebView node.
            line=line.replaceFirst("^(?:第\\s*\\d+\\s*题[、.:：]?|\\d{1,3}[、.])\\s*","");
            stem.append(line);
        }
        List<String> parts=new ArrayList<>();int length=0;
        for(String slot:stem.toString().split("_{2,}|[□]{2,}|\\(\\s*\\)")) {
            String part=words(slot);
            if(!part.isEmpty()){parts.add(part);length+=part.length();}
        }
        return new TextQuestionIdentity(Collections.unmodifiableList(parts),length);
    }
    static String words(String value) {
        return value==null?"":Normalizer.normalize(value,Normalizer.Form.NFKC)
                .replace('\u2212','-').replaceAll("(?<!\\d)\\.|\\.(?!\\d)","")
                .replaceAll("[^\\p{L}\\p{N}+\\-=<>*/%.÷×]","").toLowerCase(Locale.ROOT);
    }
    boolean matches(String page) {
        if(length<6||parts.isEmpty())return false;
        String text=words(page);int end=0;
        for(int i=0;i<parts.size();i++) {
            int found=text.indexOf(parts.get(i),end);
            // Only blank slots may contain inserted text, never an unbounded second question.
            if(found<0||(i>0&&found-end>1000))return false;
            end=found+parts.get(i).length();
        }
        return true;
    }
    boolean matches(ScreenDocument document) {
        StringBuilder text=new StringBuilder();
        for(ScreenDocument.Line line:document.lines)text.append(line.text).append('\n');
        return matches(text.toString()); // Never use fingerprint(): it inserts line IDs between stem lines.
    }
}
