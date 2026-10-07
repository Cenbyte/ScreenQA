package cn.screenqa.lite;

import android.content.Context;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.LinearLayout;
import java.util.function.Consumer;

/** A non-blocking knowledge reminder shares the existing declaration marquee. */
final class KnowledgeNoticeView extends LinearLayout {
    private final KnowledgeLibrary library;
    private boolean reminder;private final TextView text;
    private final Consumer<KnowledgeLibrary.State> observer=this::render;
    KnowledgeNoticeView(Context context,ThemePalette palette,Runnable manage){
        super(context);library=KnowledgeLibrary.get(context);text=new TextView(context);addView(text,new LayoutParams(-1,-2));text.setTextSize(KnowledgeUi.CAPTION);text.setTextColor(palette.accent);
        text.setSingleLine(true);text.setEllipsize(TextUtils.TruncateAt.MARQUEE);text.setMarqueeRepeatLimit(-1);text.setSelected(true);
        setOnClickListener(v->{if(reminder)manage.run();});render(library.state());
    }
    CharSequence getText(){return text.getText();}
    private void render(KnowledgeLibrary.State state){
        long active=state.packages.stream().filter(p->p.enabled).count();
        String hint=!state.rag?"本地知识库未开启，可在知识库管理中启用":state.task.busy()?"":state.packages.isEmpty()?"尚未安装知识包，可到知识库管理中在线获取":active==0?"知识包未启用，可在知识库管理中启用":"";
        reminder=!hint.isEmpty();String value=reminder?hint+" · "+UsageDeclaration.MARQUEE:UsageDeclaration.MARQUEE;
        if(!value.contentEquals(text.getText())){text.setSelected(false);text.setText(value);text.setSelected(true);}
        setClickable(reminder);setContentDescription(value+(reminder?"，点击打开知识库管理":""));
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();library.observe(observer);}
    @Override protected void onDetachedFromWindow(){library.remove(observer);super.onDetachedFromWindow();}
}
