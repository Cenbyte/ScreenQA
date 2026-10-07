package cn.screenqa.lite;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.widget.*;
import java.util.function.Consumer;

/** Home and settings have identical live knowledge controls. */
final class KnowledgeSummaryView extends LinearLayout {
    private final KnowledgeLibrary library;private final TextView title,status;private final Switch enabled;
    private boolean rendering;private final Consumer<KnowledgeLibrary.State> observer=this::render;
    KnowledgeSummaryView(Context context,ThemePalette palette,Runnable manage,java.util.function.Function<String,Button> buttons){
        super(context);setOrientation(VERTICAL);library=KnowledgeLibrary.get(context);
        title=new TextView(context);title.setTextSize(KnowledgeUi.HEADLINE);title.setTextColor(palette.accent);title.setTypeface(null,Typeface.BOLD);addView(title);
        status=new TextView(context);status.setTextSize(KnowledgeUi.CAPTION);status.setTextColor(palette.secondary);status.setPadding(0,Ui.dp(this,7),0,Ui.dp(this,7));addView(status);
        enabled=KnowledgeUi.toggle(context,palette,"启用本地知识库（建议开启）");addView(enabled,new LayoutParams(-1,-2));
        enabled.setOnCheckedChangeListener((v,value)->{
            if(rendering)return;enabled.setEnabled(false);
            library.requestRagEnabled(value,error->{
                if(!isAttachedToWindow())return;rendering=true;enabled.setChecked(library.ragEnabled());rendering=false;enabled.setEnabled(true);
                if(error!=null)Toast.makeText(context,"开启失败："+error,Toast.LENGTH_LONG).show();
            });
        });
        Button button=buttons.apply("知识库管理");LayoutParams buttonLayout=new LayoutParams(-1,-2);buttonLayout.topMargin=Ui.dp(this,10);addView(button,buttonLayout);button.setOnClickListener(v->manage.run());
        render(library.state());
    }
    private void render(KnowledgeLibrary.State state){
        rendering=true;enabled.setChecked(state.rag);rendering=false;title.setText("知识库："+(state.rag?"已开启":"已关闭"));
        long active=state.packages.stream().filter(p->p.enabled).count();
        String current=state.busy && !state.task.busy()?state.message:state.task.busy()||state.task.status==KnowledgeTask.Status.FAILED?state.task.heading()+" · "+state.task.title(state.task.step):state.packages.isEmpty()?"尚未安装知识包":!state.rag?"已关闭 · 继续原有 AI 做题流程":active==0?"知识包均已禁用":"就绪 · "+active+" 个知识包已启用";
        status.setText("已安装 "+state.packages.size()+" 个知识包\n当前状态："+current);
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();enabled.setEnabled(true);library.observe(observer);}
    @Override protected void onDetachedFromWindow(){library.remove(observer);super.onDetachedFromWindow();}
}
