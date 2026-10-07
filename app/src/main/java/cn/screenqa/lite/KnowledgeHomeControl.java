package cn.screenqa.lite;

import android.content.Context;
import android.widget.Toast;
import java.util.function.Consumer;

final class KnowledgeHomeControl extends CompactToggleControl {
    private final KnowledgeLibrary library;
    private final Consumer<KnowledgeLibrary.State> observer=state->toggle.setCheckedSilently(state.rag);
    KnowledgeHomeControl(Context context,ThemePalette palette,boolean reduceMotion,Runnable explain){
        super(context,palette,reduceMotion,"知识库","知识库说明与加载状态",explain);library=KnowledgeLibrary.get(context);
        toggle.setContentDescription("启用本地知识库");
        toggle.setListener(value->{
            TouchFeedback.play(toggle,TouchFeedback.Strength.LIGHT);toggle.setEnabled(false);
            toggle.setCheckedSilently(library.ragEnabled());
            library.requestRagEnabled(value,error->{
                if(!isAttachedToWindow())return;toggle.setCheckedSilently(library.ragEnabled());toggle.setEnabled(true);
                if(error!=null)Toast.makeText(context,"开启失败："+error,Toast.LENGTH_LONG).show();
            });
        });
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();toggle.setEnabled(true);library.observe(observer);}
    @Override protected void onDetachedFromWindow(){library.remove(observer);super.onDetachedFromWindow();}
}
