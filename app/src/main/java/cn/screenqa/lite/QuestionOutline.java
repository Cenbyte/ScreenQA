package cn.screenqa.lite;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Transparent, touch-through perimeter only. No text that could feed back into OCR. */
final class QuestionOutline extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    QuestionOutline(Context context) {super(context);}
    @Override protected void onDraw(Canvas canvas) {
        float stroke=2*getResources().getDisplayMetrics().density;
        paint.setColor(0xFF16AD88);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);
        canvas.drawRoundRect(stroke,stroke,getWidth()-stroke,getHeight()-stroke,stroke*2,stroke*2,paint);
    }
}
