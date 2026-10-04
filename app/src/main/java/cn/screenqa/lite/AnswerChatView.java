package cn.screenqa.lite;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.LinearLayout;

/** Chat tail and touch observation, including touches consumed by the scroll/text children. */
final class AnswerChatView extends LinearLayout {
    interface Interaction { void onTouch(boolean touching); }
    private final FrostedGlassDrawable material;
    private final float unit;
    private final Interaction interaction;
    private boolean tailOnLeft=true;
    private float tailCenter;
    AnswerChatView(Context context,Interaction interaction) {
        super(context);this.interaction=interaction;
        unit=getResources().getDisplayMetrics().density;
        material=new FrostedGlassDrawable(unit,14,true);
        setOrientation(VERTICAL);setWillNotDraw(false);
        setPadding((int)(16*unit),(int)(6*unit),(int)(12*unit),(int)(6*unit));
    }
    FrostedGlassDrawable material(){return material;}
    boolean tailOnLeft(){return tailOnLeft;}
    float tailCenter(){return tailCenter;}
    void setTailOnLeft(boolean left){tailOnLeft=left;updateTail();}
    private void updateTail(){tailCenter=Math.max(0,getHeight()-12*unit);material.setTail(tailOnLeft,tailCenter);}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);updateTail();}
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN)interaction.onTouch(true);
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)interaction.onTouch(false);
        return super.dispatchTouchEvent(event);
    }
}
