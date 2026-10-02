package cn.screenqa.lite;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.widget.LinearLayout;

/** Chat tail and touch observation, including touches consumed by the scroll/text children. */
final class AnswerChatView extends LinearLayout {
    interface Interaction { void onTouch(boolean touching); }
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tail=new Path();
    private final float unit;
    private final Interaction interaction;
    private boolean tailOnLeft=true;
    private int fill;
    private float tailCenter;
    AnswerChatView(Context context,Interaction interaction) {
        super(context);this.interaction=interaction;
        unit=getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);setWillNotDraw(false);
        setPadding((int)(14*unit),(int)(6*unit),(int)(14*unit),(int)(8*unit));
    }
    void setFill(int color){fill=color;invalidate();}
    void setTailOnLeft(boolean left){if(tailOnLeft!=left){tailOnLeft=left;invalidate();}}
    void setTailCenter(float center){if(tailCenter!=center){tailCenter=center;invalidate();}}
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);paint.setColor(fill);
        float edge=8*unit,y=Math.max(18*unit,Math.min(getHeight()-18*unit,tailCenter));
        canvas.drawRoundRect(edge,0,getWidth()-edge,getHeight(),18*unit,18*unit,paint);
        tail.reset();float x=tailOnLeft?edge:getWidth()-edge;
        tail.moveTo(x,y-7*unit);tail.lineTo(tailOnLeft?0:getWidth(),y);tail.lineTo(x,y+7*unit);
        tail.close();canvas.drawPath(tail,paint);
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN)interaction.onTouch(true);
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)interaction.onTouch(false);
        return super.dispatchTouchEvent(event);
    }
}
