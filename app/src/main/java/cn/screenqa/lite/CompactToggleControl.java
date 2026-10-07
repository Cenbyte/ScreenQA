package cn.screenqa.lite;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared compact home controls, matching the existing second pet switch. */
class CompactToggleControl extends LinearLayout {
    final GoldSwitch toggle;
    CompactToggleControl(Context context,ThemePalette palette,boolean reduceMotion,String title,String help,Runnable explain){
        super(context);setOrientation(VERTICAL);setGravity(Gravity.CENTER);
        LinearLayout label=new LinearLayout(context);label.setGravity(Gravity.CENTER_VERTICAL);addView(label,new LayoutParams(-2,-2));
        TextView name=new TextView(context);name.setText(title);name.setTextSize(12);name.setTextColor(palette.onAccent);
        name.setTypeface(null,Typeface.BOLD);name.setIncludeFontPadding(false);label.addView(name,new LayoutParams(-2,-2));
        TextView question=new TextView(context);question.setText("?");question.setTextSize(12);question.setTextColor(palette.onAccent);
        question.setGravity(Gravity.CENTER);question.setIncludeFontPadding(false);question.setContentDescription(help);
        GradientDrawable circle=new GradientDrawable();circle.setShape(GradientDrawable.OVAL);circle.setColor(Color.TRANSPARENT);circle.setStroke(Ui.dp(this,1),palette.onAccent);
        question.setBackground(circle);question.setDefaultFocusHighlightEnabled(false);
        LayoutParams lp=new LayoutParams(Ui.dp(this,18),Ui.dp(this,18));lp.leftMargin=Ui.dp(this,4);label.addView(question,lp);
        question.setOnClickListener(v->explain.run());
        toggle=new GoldSwitch(context);toggle.setPalette(palette);toggle.setReduceMotion(reduceMotion);toggle.setThumbGlow(false);
        toggle.setDefaultFocusHighlightEnabled(false);toggle.setBackground(null);toggle.setForeground(null);addView(toggle,new LayoutParams(-2,-2));
    }
}
