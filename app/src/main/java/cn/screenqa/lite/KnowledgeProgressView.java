package cn.screenqa.lite;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.*;
import java.util.function.Consumer;

/** All installation surfaces render the same process-scoped snapshot. */
final class KnowledgeProgressView extends LinearLayout {
    private final KnowledgeLibrary library;
    private final ThemePalette palette;
    final TextView heading,operation,count,detail;
    private final ProgressBar bar;
    private final LinearLayout steps;
    private final TextView[] rows=new TextView[6];
    private final Button retry;
    private final Consumer<KnowledgeLibrary.State> observer=this::render;
    KnowledgeProgressView(Context context,ThemePalette palette){
        super(context);this.palette=palette;library=KnowledgeLibrary.get(context);setOrientation(VERTICAL);
        setPadding(dp(14),dp(10),dp(14),dp(10));
        GradientDrawable bg=new GradientDrawable();bg.setColor(palette.surface);bg.setCornerRadius(dp(16));bg.setStroke(dp(1),palette.border);setBackground(bg);
        heading=text(this,KnowledgeUi.HEADLINE,true);operation=text(this,KnowledgeUi.BODY,true);count=text(this,KnowledgeUi.CAPTION,false);
        bar=new ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal);bar.setProgressTintList(ColorStateList.valueOf(palette.accent));bar.setIndeterminateTintList(ColorStateList.valueOf(palette.accent));addView(bar,new LayoutParams(-1,dp(6)));
        detail=text(this,12,false);steps=new LinearLayout(context);steps.setOrientation(VERTICAL);addView(steps);
        for(int i=0;i<3;i++){
            LinearLayout line=new LinearLayout(context);steps.addView(line,new LayoutParams(-1,-2));
            for(int j=0;j<2;j++){int n=i*2+j;rows[n]=text(line,12,false);rows[n].setLayoutParams(new LayoutParams(0,-2,1));}
        }
        retry=new Button(context);retry.setText("重试");retry.setAllCaps(false);retry.setTextColor(palette.accent);
        GradientDrawable retryBackground=new GradientDrawable();retryBackground.setColor(palette.accentSoft);retryBackground.setCornerRadius(dp(14));retry.setBackground(retryBackground);
        LayoutParams retryLayout=new LayoutParams(-1,dp(44));retryLayout.topMargin=dp(6);addView(retry,retryLayout);retry.setOnClickListener(v->library.retry());
        render(library.state());
    }
    private int dp(int value){return Ui.dp(this,value);}
    private TextView text(LinearLayout parent,int size,boolean bold){TextView t=new TextView(getContext());t.setTextSize(size);t.setTextColor(palette.foreground);if(bold)t.setTypeface(null,Typeface.BOLD);t.setPadding(0,dp(3),0,dp(3));parent.addView(t);return t;}
    private void render(KnowledgeLibrary.State state){
        KnowledgeTask task=state.task;setTag(task);boolean idle=task.status==KnowledgeTask.Status.IDLE,complete=task.status==KnowledgeTask.Status.COMPLETE,failed=task.status==KnowledgeTask.Status.FAILED;
        setVisibility(idle?GONE:VISIBLE);operation.setVisibility(complete?GONE:VISIBLE);
        heading.setText(task.heading());heading.setTextColor(failed?palette.danger:palette.accent);
        operation.setText(complete?(task.reused()?"继续使用现有知识库，无需重复处理":"已安装，可用于 AI 作答前的本地参考"):idle?"选择在线下载或从文件安装":task.title(task.step));
        count.setText(task.count());detail.setText((task.detail.equals(task.title(task.step))?"":task.detail)+(task.busy()?"还剩 "+(6-task.step.number)+" 步":"")+(task.name.isEmpty()?"":"\n"+task.name));
        detail.setVisibility(idle||complete?GONE:VISIBLE);bar.setVisibility(task.busy()?VISIBLE:GONE);bar.setIndeterminate(task.percent()<0);if(task.percent()>=0)bar.setProgress(task.percent());
        steps.setVisibility(idle||complete?GONE:VISIBLE);
        for(KnowledgeTask.Step step:KnowledgeTask.Step.values()){
            boolean done=complete || step.number<task.step.number,current=!complete && step==task.step;
            TextView row=rows[step.number-1];row.setText((done?"✓ ":current?failed?"! ":"● ":"○ ")+step.number+" "+task.title(step));
            row.setTextColor(current?(failed?palette.danger:palette.accent):done?palette.foreground:palette.secondary);
            row.setTypeface(null,current?Typeface.BOLD:Typeface.NORMAL);row.setAlpha(done||current?1f:.60f);
        }
        retry.setVisibility(failed?VISIBLE:GONE);retry.setEnabled(!state.busy);retry.setAlpha(state.busy?.55f:1f);
        setContentDescription(task.heading()+"，"+task.title(task.step)+"，"+task.count());
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();library.observe(observer);}
    @Override protected void onDetachedFromWindow(){library.remove(observer);super.onDetachedFromWindow();}
}
