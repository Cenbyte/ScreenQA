package cn.screenqa.lite;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Confirm the complete located question, not unrelated page chrome or OCR line IDs. */
final class QuestionStability {
    private static final Pattern TYPE_LABEL=Pattern.compile("[【\\[]?(单选题|多选题|选择题|填空题|判断题|简答题)[】\\]]?");
    private static final Pattern OPTION=Pattern.compile("^([A-HＡ-Ｈ])(?:[.．、,，:：)）\\s]+|$)(.*)$");
    static String signature(ScreenDocument page,LocalQuestionLocator.Candidate candidate){
        // Without a reliable local grouping, every page line still matters.
        if(candidate==null)return pageSignature(page);
        StringBuilder stem=new StringBuilder(),options=new StringBuilder();
        for(int id:candidate.all){
            String text=candidate.document.lines.get(id-1).text;
            Matcher typeLabel=TYPE_LABEL.matcher(text.trim());
            if(typeLabel.matches())text=typeLabel.group(1);
            if(candidate.stem.contains(id))stem.append(QuestionTracker.normalize(text));
            else {
                Matcher option=OPTION.matcher(text);
                if("choice".equals(candidate.type)&&option.matches()){
                    char label=option.group(1).charAt(0);
                    if(label>127)label=(char)(label-'Ａ'+'A');
                    options.append('\u001e').append(label).append(':')
                            .append(QuestionTracker.normalize(option.group(2)));
                }else {
                    if(!"choice".equals(candidate.type))options.append('\u001e');
                    options.append(QuestionTracker.normalize(text));
                }
            }
        }
        return candidate.type+":"+stem+'\u001f'+options;
    }
    private static String pageSignature(ScreenDocument page){
        if(page.lines.isEmpty())return "";
        StringBuilder out=new StringBuilder("page:");
        for(ScreenDocument.Line line:page.lines){
            String text=line.text.trim(),compact=QuestionTracker.normalize(text);
            Matcher option=OPTION.matcher(text);
            if(option.matches()){
                char label=option.group(1).charAt(0);
                if(label>127)label=(char)(label-'Ａ'+'A');
                out.append('\u001e').append(label).append(':').append(QuestionTracker.normalize(option.group(2)));
            }else {
                // Keep short labels/isolated numbers and new numbered items separate. Never drop a line.
                boolean separate=compact.length()<=6||TYPE_LABEL.matcher(text).matches();
                boolean numbered=compact.matches("^\\d+[.、．)）].*");
                if(separate||numbered)out.append('\u001e');
                out.append(compact);
                if(separate)out.append('\u001e');
            }
        }
        return out.toString();
    }
}
