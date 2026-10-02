package cn.screenqa.lite;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.*;
import java.util.regex.Pattern;

/** OCR lines retain real screen coordinates. AI selects IDs, never invents coordinates. */
final class ScreenDocument {
    static final class Line {
        final String text;
        final int left,top,right,bottom;
        final long visualSignature;
        Line(String text,int left,int top,int right,int bottom) {
            this(text,left,top,right,bottom,0);
        }
        Line(String text,int left,int top,int right,int bottom,long visualSignature) {
            this.text=text.trim();this.left=left;this.top=top;this.right=right;this.bottom=bottom;
            this.visualSignature=visualSignature;
        }
    }
    private static final Pattern TIMER=Pattern.compile("^(?:(?:剩余时间|倒计时|用时|已用时|答题时间)?[:：]?\\s*\\d{1,2}[:：]\\d{2}(?:[:：]\\d{2})?|(?:剩余时间|倒计时|用时|已用时|答题时间)[:：]?[\\d小时分秒sSmMhH.]+)$");
    private static final Set<String> CHROME=new HashSet<>(Arrays.asList(
            "返回","首页","设置","上一题","下一题","提交","提交答案","交卷","收藏","取消收藏","答题卡","纠错",
            "查看解析","查看答案","重新作答","请输入答案","请输入你的答案","点击输入答案","请输入内容","清空","确定","取消"));
    final List<Line> lines;
    final List<Line> navigationLines;
    final List<Line> terminalLines;
    final int width,height;
    ScreenDocument(List<Line> raw,int width,int height) {
        this.width=width;this.height=height;
        List<Line> selected=new ArrayList<>();
        List<Line> navigation=new ArrayList<>();
        List<Line> terminal=new ArrayList<>();
        for(Line line:raw) {
            if(line.text.isEmpty() || line.right<=line.left || line.bottom<=line.top) continue;
            String compact=QuestionTracker.normalize(line.text);
            if(compact.matches("(?:提交(?:答案|试卷)?|交卷|完成答题|结束答题|查看成绩)[→›»>]*")){terminal.add(line);continue;}
            if(NextButtonMatcher.confidence(line.text,line.top>=height/2)>0){navigation.add(line);continue;}
            if(CHROME.contains(compact) || TIMER.matcher(compact).matches()) continue;
            if(compact.matches("(?:进度|已完成)[:：]?\\d{1,3}/\\d{1,3}")) continue;
            selected.add(line);
        }
        selected.sort(Comparator.comparingInt((Line l)->l.top).thenComparingInt(l->l.left));
        lines=Collections.unmodifiableList(selected);
        navigationLines=Collections.unmodifiableList(navigation);
        terminalLines=Collections.unmodifiableList(terminal);
    }
    String fingerprint() {
        StringBuilder out=new StringBuilder();
        for(int i=0;i<lines.size();i++) out.append(i+1).append(':').append(lines.get(i).text).append('|');
        return out.toString();
    }
    boolean tooLarge() {return lines.size()>120 || fingerprint().length()>12000;}
    String json() throws JSONException {
        JSONArray data=new JSONArray();
        for(int i=0;i<lines.size();i++) {
            Line l=lines.get(i);
            data.put(new JSONObject().put("id",i+1).put("text",l.text)
                    .put("box",new JSONArray(new int[]{l.left,l.top,l.right,l.bottom})));
        }
        return new JSONObject().put("screen_width",width).put("screen_height",height).put("lines",data).toString();
    }
    // Full boxes stay on device; normalized vertical centers preserve multi-question selection.
    String modelJson() throws JSONException {
        JSONArray data=new JSONArray();
        for(int i=0;i<lines.size();i++)data.put(new JSONObject().put("id",i+1).put("text",lines.get(i).text)
                .put("y",(int)(500L*(lines.get(i).top+lines.get(i).bottom)/Math.max(1,height))));
        return new JSONObject().put("lines",data).toString();
    }
    String text(List<Integer> ids) {
        StringBuilder out=new StringBuilder();
        for(int id:ids) {if(out.length()>0)out.append('\n');out.append(lines.get(id-1).text);}
        return out.toString();
    }
    int[] bounds(List<Integer> ids) {
        if(ids.isEmpty())throw new IllegalArgumentException("Missing line IDs");
        int left=width,top=height,right=0,bottom=0;
        for(int id:ids) {
            Line l=lines.get(id-1);
            left=Math.min(left,l.left);top=Math.min(top,l.top);right=Math.max(right,l.right);bottom=Math.max(bottom,l.bottom);
        }
        return new int[]{Math.max(0,left-8),Math.max(0,top-8),Math.min(width,right+8),Math.min(height,bottom+8)};
    }
}
