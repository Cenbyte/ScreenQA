package cn.screenqa.lite;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Bounded display text, Chinese search tokens and conservative question identity. */
final class KnowledgeText {
    static String limit(String text,int max) {return max<=0?"":text.length()<=max?text:text.substring(0,max-1)+"…";}
    static String scalar(Object value) {
        if(value==null || value==JSONObject.NULL)return "";
        if(value instanceof String || value instanceof Number || value instanceof Boolean)return String.valueOf(value);
        if(value instanceof JSONArray) {
            StringBuilder out=new StringBuilder();JSONArray array=(JSONArray)value;
            for(int i=0;i<array.length() && out.length()<8000;i++) {if(out.length()>0)out.append("；");out.append(scalar(array.opt(i)));}
            return limit(out.toString(),8000);
        }
        return "";
    }
    private static final Set<String> OMIT=new HashSet<>(Arrays.asList("source","confidence","labeledAt","annotatorName","annotatorVersion","model","curriculum_system","label","importance"));
    static String material(Object value) {StringBuilder out=new StringBuilder();material(value,out,0);return limit(out.toString(),8000);}
    private static void material(Object value,StringBuilder out,int depth) {
        if(depth>12 || out.length()>=8000 || value==null || value==JSONObject.NULL)return;
        if(value instanceof JSONObject) {
            JSONObject object=(JSONObject)value;
            if(object.has("value")) {material(object.opt("value"),out,depth+1);return;}
            Iterator<String> keys=object.keys();
            while(keys.hasNext() && out.length()<8000) {String key=keys.next();if(!OMIT.contains(key))material(object.opt(key),out,depth+1);}
        } else if(value instanceof JSONArray) {
            JSONArray array=(JSONArray)value;for(int i=0;i<array.length() && out.length()<8000;i++)material(array.opt(i),out,depth+1);
        } else if(value instanceof String && !((String)value).trim().isEmpty()) {
            if(out.length()>0)out.append('\n');out.append(limit((String)value,8000-out.length()));
        }
    }
    static LinkedHashMap<String,String> options(Object value) {
        LinkedHashMap<String,String> result=new LinkedHashMap<>();
        if(value instanceof JSONObject) {
            JSONObject object=(JSONObject)value;List<String> keys=new ArrayList<>();object.keys().forEachRemaining(keys::add);Collections.sort(keys);
            for(String key:keys)result.put(key,scalar(object.opt(key)));
        }
        return result;
    }
    static String optionText(Map<String,String> options) {
        StringBuilder out=new StringBuilder();for(Map.Entry<String,String> option:options.entrySet())out.append(option.getKey()).append(". ").append(option.getValue()).append('\n');
        return limit(out.toString().trim(),8000);
    }
    static String normalizeQuestion(String value) {
        String text=Normalizer.normalize(value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).trim()
                .replaceFirst("^\\s*(?:第\\s*)?\\d+[.)、](?!\\d)\\s*","")
                .replaceFirst("^(?:单项选择题|多项选择题|单选题|多选题|选择题|判断题|填空题|简答题)\\s*[:：]?\\s*","");
        // Remove layout whitespace only; preserve negation, numbers and mathematical operators.
        return text.replaceAll("[\\s\\p{Z}]","");
    }
    private static boolean han(int code) {return Character.UnicodeScript.of(code)==Character.UnicodeScript.HAN;}
    static List<String> tokens(String value,boolean unigrams) {
        String text=Normalizer.normalize(limit(value,16000),Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        List<String> tokens=new ArrayList<>();StringBuilder latin=new StringBuilder();int previous=-1,run=0;
        for(int offset=0;offset<text.length();) {
            int code=text.codePointAt(offset);offset+=Character.charCount(code);
            if(han(code)) {
                if(latin.length()>0){tokens.add(latin.toString());latin.setLength(0);}
                if(unigrams)tokens.add(new String(Character.toChars(code)));
                if(previous!=-1)tokens.add(new String(Character.toChars(previous))+new String(Character.toChars(code)));
                previous=code;run++;
            } else {
                if(previous!=-1 && !unigrams && run==1)
                    tokens.add(new String(Character.toChars(previous)));
                previous=-1;run=0;
                if(Character.isLetterOrDigit(code))latin.appendCodePoint(code);
                else if(latin.length()>0){tokens.add(latin.toString());latin.setLength(0);}
            }
        }
        if(latin.length()>0)tokens.add(latin.toString());
        if(previous!=-1 && !unigrams && run==1)tokens.add(new String(Character.toChars(previous)));
        return tokens;
    }
    static String indexed(String value) {return String.join(" ",tokens(value,true));}
    static double similarity(String a,String b) {
        Set<String> left=new HashSet<>(tokens(a,false)),right=new HashSet<>(tokens(b,false));
        if(left.isEmpty() || right.isEmpty())return 0;
        Set<String> both=new HashSet<>(left);both.retainAll(right);
        return 2.0*both.size()/(left.size()+right.size());
    }
    static final class Query {
        final String stem,whole,stage,subject;
        final Map<String,String> options;
        Query(String stem,String whole,String stage,String subject) {
            this.whole=Normalizer.normalize(limit(whole,16000),Normalizer.Form.NFKC);this.stage=stage==null?"":stage.trim();this.subject=subject==null?"":subject.trim();
            Matcher matcher=Pattern.compile("(?m)^\\s*([A-Ha-h])[.、:：)）．\\s]\\s*(.*?)(?=^\\s*[A-Ha-h][.、:：)）．\\s]|\\z)",Pattern.DOTALL).matcher(this.whole);
            Map<String,String> parsed=new LinkedHashMap<>();int first=-1;
            while(matcher.find()){if(first==-1)first=matcher.start();parsed.put(matcher.group(1).toUpperCase(Locale.ROOT),matcher.group(2).trim());}
            this.options=parsed;
            this.stem=limit(stem==null || stem.trim().isEmpty()?(first>=0?this.whole.substring(0,first).trim():this.whole):stem,12000);
        }
        boolean sameOptions(String recorded) {
            Map<String,String> reference=parseOptionLines(recorded);
            if(!recorded.trim().isEmpty() && reference.isEmpty())return false;
            if(reference.isEmpty() && options.isEmpty())return true;
            if(reference.size()!=options.size())return false;
            List<String> a=new ArrayList<>(),b=new ArrayList<>();
            for(String value:reference.values())a.add(normalizeQuestion(value));
            for(String value:options.values())b.add(normalizeQuestion(value));
            Collections.sort(a);Collections.sort(b);return a.equals(b);
        }
    }
    static Map<String,String> parseOptionLines(String text) {
        Map<String,String> values=new LinkedHashMap<>();Matcher matcher=Pattern.compile("(?m)^([A-Ha-h])[.] (.*?)(?=^[A-Ha-h][.] |\\z)",Pattern.DOTALL).matcher(text);
        while(matcher.find())values.put(matcher.group(1),matcher.group(2).trim());return values;
    }
    static String critical(String text) {
        Matcher matcher=Pattern.compile("不|未|无|非|错误|正确|全部|只有|至少|至多|最多|最少|一定|可能|任何|所有|都|[+-]?\\d+(?:\\.\\d+)?|[<>=+*/%\\-−≤≥≠≈^×÷]").matcher(normalizeQuestion(text));
        StringBuilder out=new StringBuilder();while(matcher.find())out.append(matcher.group()).append('|');
        Matcher english=Pattern.compile("\\b(?:not|no|never|always|only|all|except|incorrect|correct|false|true|least|most)\\b")
                .matcher(Normalizer.normalize(text,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT));
        while(english.find())out.append(english.group()).append('|');return out.toString();
    }
    private KnowledgeText() { }
}
