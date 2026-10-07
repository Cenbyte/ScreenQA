package cn.screenqa.lite;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.widget.Switch;

/** Shared typography and bounded touch feedback for knowledge controls only. */
final class KnowledgeUi {
    static final int HEADLINE=20,TITLE=16,BODY=14,CAPTION=12;
    static Switch toggle(Context context,ThemePalette palette,String title){
        Switch toggle=new Switch(context);toggle.setText(title);toggle.setTextSize(BODY);toggle.setTextColor(palette.foreground);
        toggle.setMinHeight(Ui.dp(context,56));toggle.setPadding(0,Ui.dp(context,8),Ui.dp(context,8),Ui.dp(context,8));
        toggle.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{palette.accent,palette.secondary}));
        toggle.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{palette.accentSoft,palette.border}));
        GradientDrawable mask=new GradientDrawable();mask.setColor(Color.WHITE);mask.setCornerRadius(Ui.dp(context,12));
        toggle.setBackground(new RippleDrawable(ColorStateList.valueOf(ThemePalette.alpha(palette.accent,.14f)),null,mask));
        return toggle;
    }
    private KnowledgeUi(){}
}
