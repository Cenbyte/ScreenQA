package cn.screenqa.lite;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.*;
import java.io.File;
import java.util.*;
import java.util.function.Consumer;

/** Native management controls embedded in the existing knowledge detail card. */
final class KnowledgeLibraryPanel extends LinearLayout {
    private final KnowledgeLibrary library;private final ThemePalette palette;private final java.util.function.Function<String,Button> buttons;
    private TextView status,results;
    private final LinearLayout installed,otherMethods;private final Switch rag;
    private final Button local,inbox;private Button search;private final EditText question,stage,subject;
    private final Consumer<KnowledgeLibrary.State> observer=this::render;
    private boolean rendering;private int previewGeneration;
    KnowledgeLibraryPanel(Context context,ThemePalette palette,Runnable pickZip,java.util.function.Function<String,Button> buttons) {
        super(context);setOrientation(VERTICAL);this.palette=palette;this.buttons=buttons;library=KnowledgeLibrary.get(context);
        rag=KnowledgeUi.toggle(context,palette,"启用本地知识库（建议开启）");addView(rag,new LayoutParams(-1,-2));
        rag.setOnCheckedChangeListener((button,checked)->{
            if(rendering)return;rag.setEnabled(false);
            library.requestRagEnabled(checked,error->{
                if(!isAttachedToWindow())return;rendering=true;rag.setChecked(library.ragEnabled());rendering=false;rag.setEnabled(true);
                if(error!=null)Toast.makeText(context,"开启失败："+error,Toast.LENGTH_LONG).show();
            });
        });
        Button online=button(this,"在线获取知识库",()->context.startActivity(new Intent(context,KnowledgeBrowserActivity.class)));
        online.setBackgroundTintList(ColorStateList.valueOf(palette.accent));online.setTextColor(palette.onAccent);
        TextView other=link(this,"其他方法 ›");
        otherMethods=new LinearLayout(context);otherMethods.setOrientation(VERTICAL);otherMethods.setVisibility(GONE);addView(otherMethods);
        other.setOnClickListener(v->{boolean expand=otherMethods.getVisibility()!=VISIBLE;otherMethods.setVisibility(expand?VISIBLE:GONE);other.setText(expand?"其他方法 ⌄":"其他方法 ›");});
        local=button(otherMethods,"从文件安装 ZIP",pickZip);
        inbox=button(otherMethods,"安装收件箱中的 ZIP",()->library.inboxFiles(files->{
            if(!isAttachedToWindow())return;
            if(files.isEmpty()){status.setText("收件箱没有待安装 ZIP");return;}
            String[] names=new String[files.size()];for(int i=0;i<names.length;i++)names[i]=files.get(i).getName();
            new AlertDialog.Builder(context).setTitle("选择待安装知识包").setItems(names,(dialog,index)->library.installAsync(files.get(index))).setNegativeButton("取消",null).show();
        }));
        local.setTextSize(KnowledgeUi.BODY);inbox.setTextSize(KnowledgeUi.BODY);
        status=label(this,"已安装知识包",KnowledgeUi.TITLE);status.setTypeface(null,android.graphics.Typeface.BOLD);
        installed=new LinearLayout(context);installed.setOrientation(VERTICAL);addView(installed,new LayoutParams(-1,-2));
        LinearLayout preview=new LinearLayout(context);preview.setOrientation(VERTICAL);preview.setVisibility(GONE);
        if(BuildConfig.DEVELOPER_BUILD){TextView diagnostics=link(otherMethods,"检索诊断（开发者） ›");diagnostics.setOnClickListener(v->preview.setVisibility(preview.getVisibility()==VISIBLE?GONE:VISIBLE));}
        otherMethods.addView(preview);
        question=input(preview,"输入一道题及全部选项（每行 A. / B. / C. / D.）",true);
        stage=input(preview,"学段筛选（可空，例如 高中）",false);subject=input(preview,"学科筛选（可空，例如 生物）",false);
        search=button(preview,"查看实际检索结果",()->{
            final int token=++previewGeneration;search.setEnabled(false);results.setText("检索中…");
            library.preview(question.getText().toString(),stage.getText().toString(),subject.getText().toString(),hits->{
                if(token!=previewGeneration || !isAttachedToWindow())return;search.setEnabled(true);
                StringBuilder out=new StringBuilder();int i=0;
                for(KnowledgeIndex.Hit hit:hits)out.append(++i).append(". ").append(hit.exact?"同题同选项":"相关参考").append(" · ")
                        .append(String.format(Locale.ROOT,"%.3f",hit.score)).append('\n').append(hit.source).append(" / ").append(hit.id)
                        .append('\n').append(hit.summary()).append('\n').append(hit.answer.isEmpty()?"无明确答案":"参考答案："+KnowledgeText.limit(hit.answer,180)).append("\n\n");
                out.append(hits.isEmpty()?"未找到参考资料。请检查 RAG 与知识库启用开关。":"将进入 AI 请求的参考资料预览：\n"+KnowledgeRag.context(hits));
                results.setText(out.toString());
            },error->{if(token==previewGeneration && isAttachedToWindow()){search.setEnabled(true);results.setText("检索失败："+error);}});
        });
        results=label(preview,"检索预览只查本地索引，不调用 AI、不消耗 API Token。",13);results.setTextIsSelectable(true);
    }
    private TextView label(LinearLayout parent,String value,int size){TextView text=new TextView(getContext());text.setText(value);text.setTextSize(size);text.setIncludeFontPadding(false);text.setTextColor(palette.foreground);text.setPadding(0,Ui.dp(this,6),0,Ui.dp(this,6));parent.addView(text);return text;}
    private Button button(LinearLayout parent,String title,Runnable action){Button button=buttons.apply(title);LayoutParams params=new LayoutParams(-1,-2);params.topMargin=Ui.dp(this,8);parent.addView(button,params);button.setOnClickListener(v->action.run());return button;}
    private TextView link(LinearLayout parent,String title){TextView text=label(parent,title,KnowledgeUi.CAPTION);text.setTextColor(palette.accent);text.setMinHeight(Ui.dp(this,44));text.setGravity(android.view.Gravity.CENTER_VERTICAL);return text;}
    private EditText input(LinearLayout parent,String hint,boolean multiline){EditText text=new EditText(getContext());text.setHint(hint);text.setTextColor(palette.foreground);text.setHintTextColor(palette.secondary);text.setTextSize(14);text.setSingleLine(!multiline);if(multiline){text.setMinLines(3);text.setMaxLines(8);text.setGravity(android.view.Gravity.TOP);}parent.addView(text,new LayoutParams(-1,-2));return text;}
    private void render(KnowledgeLibrary.State state) {
        rendering=true;rag.setChecked(state.rag);rendering=false;
        status.setText("已安装知识包（"+state.packages.size()+"）");
        local.setEnabled(!state.busy);inbox.setEnabled(!state.busy);installed.removeAllViews();
        if(state.packages.isEmpty())label(installed,"尚未安装，在线获取后可使用本地参考。",KnowledgeUi.BODY).setTextColor(palette.secondary);
        for(KnowledgeLibrary.Installed pack:state.packages) {
            LinearLayout item=new LinearLayout(getContext());item.setOrientation(VERTICAL);item.setPadding(0,Ui.dp(this,8),0,Ui.dp(this,12));installed.addView(item,new LayoutParams(-1,-2));
            label(item,pack.name,KnowledgeUi.TITLE).setTypeface(null,android.graphics.Typeface.BOLD);
            label(item,KnowledgeTask.number(pack.records)+" 条数据 · 已安装"+(pack.version.equals("未提供")?"":" · 版本 "+pack.version),KnowledgeUi.CAPTION).setTextColor(palette.secondary);
            Switch enabled=KnowledgeUi.toggle(getContext(),palette,"启用此知识包");enabled.setChecked(pack.enabled);enabled.setEnabled(!state.busy);item.addView(enabled,new LayoutParams(-1,-2));
            enabled.setOnCheckedChangeListener((button,checked)->library.setEnabled(pack.id,checked));
            Button delete=button(item,"删除知识包",()->new AlertDialog.Builder(getContext()).setTitle("删除知识库？")
                    .setMessage("将清理此知识库的索引、解压文件和知识包 ZIP。").setNegativeButton("取消",null).setPositiveButton("删除",(dialog,which)->library.delete(pack.id)).show());delete.setEnabled(!state.busy);delete.setTextSize(KnowledgeUi.BODY);delete.setTextColor(palette.danger);delete.setBackgroundColor(android.graphics.Color.TRANSPARENT);delete.setElevation(0);LayoutParams deleteLayout=new LayoutParams(-2,-2);deleteLayout.gravity=android.view.Gravity.END;delete.setLayoutParams(deleteLayout);
        }
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();rag.setEnabled(true);library.observe(observer);}
    @Override protected void onDetachedFromWindow(){library.remove(observer);previewGeneration++;super.onDetachedFromWindow();}
}
