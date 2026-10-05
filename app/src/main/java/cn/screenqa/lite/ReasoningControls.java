package cn.screenqa.lite;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Both rails render the same continuous progress; one animator drives the shared snap. */
final class ReasoningControls extends LinearLayout {
    private final Settings settings;
    private final ThemePalette palette;
    private final TextView status;
    private final Rail depth,speed;
    private int reasoningLevel;
    private float reasoningProgress;
    private ValueAnimator snapAnimation;
    private long lastTick;
    private java.util.function.Consumer<Float> progressListener;
    private final ReasoningArtwork artwork;
    final android.widget.FrameLayout futureVisualContainer;

    ReasoningControls(Context context,Settings settings,ThemePalette palette){
        super(context);this.settings=settings;this.palette=palette;
        reasoningLevel=settings.reasoningLevel();reasoningProgress=reasoningLevel;
        setOrientation(HORIZONTAL);
        LinearLayout controls=new LinearLayout(context);controls.setOrientation(VERTICAL);
        addView(controls,new LayoutParams(0,-2,2f));
        status=label(controls,"",15,palette.accent);updateStatus();
        label(controls,"思考程度  低 → 高",12,palette.secondary);
        depth=new Rail(context,false);controls.addView(depth,new LayoutParams(-1,dp(48)));
        label(controls,"思考速度  慢 → 快",12,palette.secondary);
        speed=new Rail(context,true);controls.addView(speed,new LayoutParams(-1,dp(48)));
        futureVisualContainer=new android.widget.FrameLayout(context);
        futureVisualContainer.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        futureVisualContainer.setClickable(false);futureVisualContainer.setFocusable(false);
        addView(futureVisualContainer,new LayoutParams(0,-1,1f));
        artwork=new ReasoningArtwork(this,reasoningProgress,settings.reduceMotion(),palette);
    }
    void attachArtwork(ReasoningCard card){card.setArtwork(artwork);}
    @Override protected void dispatchDraw(Canvas canvas){
        if(artwork.drawsIn(this))artwork.draw(canvas);
        super.dispatchDraw(canvas);
    }
    private int dp(float n){return Ui.dp(getContext(),n);}
    private TextView label(LinearLayout parent,String text,int size,int color){
        TextView v=new TextView(getContext());v.setText(text);v.setTextSize(size);v.setTextColor(color);
        v.setPadding(0,dp(4),0,dp(2));parent.addView(v);return v;
    }
    int reasoningLevel(){return reasoningLevel;}
    int speedLevel(){return 6-reasoningLevel;}
    float reasoningProgress(){return reasoningProgress;}
    float speedProgress(){return ReasoningStrategy.mirror(reasoningProgress);}
    void setProgressListener(java.util.function.Consumer<Float> listener){progressListener=listener;}
    private void updateStatus(){status.setText(reasoningLevel+" · "+ReasoningStrategy.NAMES[reasoningLevel-1]);}
    private void progress(float value,boolean tick){
        reasoningProgress=ReasoningStrategy.clampProgress(value);
        int level=ReasoningStrategy.snap(reasoningProgress);
        if(level!=reasoningLevel){
            reasoningLevel=level;updateStatus();
            long now=android.os.SystemClock.uptimeMillis();
            if(tick&&now-lastTick>70){TouchFeedback.play(this,TouchFeedback.Strength.LIGHT);lastTick=now;}
        }
        depth.invalidate();speed.invalidate();
        artwork.setProgress(reasoningProgress);
        if(progressListener!=null)progressListener.accept(reasoningProgress);
    }
    private void cancelSnap(){if(snapAnimation!=null){snapAnimation.cancel();snapAnimation=null;}}
    private void settle(){
        settings.setReasoningLevel(reasoningLevel);
        cancelSnap();
        if(settings.reduceMotion()){progress(reasoningLevel,false);return;}
        snapAnimation=ValueAnimator.ofFloat(reasoningProgress,reasoningLevel);snapAnimation.setDuration(180);
        snapAnimation.setInterpolator(new android.view.animation.DecelerateInterpolator());
        snapAnimation.addUpdateListener(a->progress((float)a.getAnimatedValue(),false));snapAnimation.start();
    }
    @Override protected void onDetachedFromWindow(){cancelSnap();super.onDetachedFromWindow();}
    private final class Rail extends View {
        private final boolean mirrored;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private int pointer=-1;
        private float startProgress;
        Rail(Context c,boolean mirrored){super(c);this.mirrored=mirrored;setFocusable(true);setClickable(true);
            setContentDescription(mirrored?"思考速度，慢到快":"思考程度，低到高");}
        private float displayProgress(){return mirrored?speedProgress():reasoningProgress;}
        @Override protected void onDraw(Canvas canvas){
            float left=dp(12),right=getWidth()-left,y=getHeight()/2f;
            paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(dp(4));paint.setColor(palette.border);
            canvas.drawLine(left,y,right,y,paint);
            float x=left+(right-left)*(displayProgress()-1)/4f;
            paint.setColor(palette.accent);canvas.drawLine(left,y,x,y,paint);
            for(int i=0;i<5;i++){paint.setColor(palette.secondary);canvas.drawCircle(left+(right-left)*i/4f,y,dp(2),paint);}
            paint.setColor(palette.accentSoft);canvas.drawCircle(x,y,dp(12),paint);
            paint.setColor(palette.accent);canvas.drawCircle(x,y,dp(8),paint);
            paint.setColor(palette.onAccent);paint.setStrokeWidth(dp(1));canvas.drawLine(x-dp(2),y-dp(3),x-dp(2),y+dp(3),paint);
            canvas.drawLine(x+dp(2),y-dp(3),x+dp(2),y+dp(3),paint);
        }
        private void move(float x){
            float value=1+4*(x-dp(12))/Math.max(1,getWidth()-2*dp(12));
            progress(mirrored?ReasoningStrategy.mirror(value):value,true);
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN -> {cancelSnap();startProgress=settings.reasoningLevel();pointer=e.getPointerId(0);
                    getParent().requestDisallowInterceptTouchEvent(true);move(e.getX());return true;}
                case MotionEvent.ACTION_MOVE -> {int index=e.findPointerIndex(pointer);if(index>=0)move(e.getX(index));return true;}
                case MotionEvent.ACTION_POINTER_UP -> {if(e.getPointerId(e.getActionIndex())==pointer){pointer=-1;settle();}return true;}
                case MotionEvent.ACTION_UP -> {if(pointer!=-1){int index=e.findPointerIndex(pointer);if(index>=0)move(e.getX(index));settle();}
                    pointer=-1;getParent().requestDisallowInterceptTouchEvent(false);performClick();return true;}
                case MotionEvent.ACTION_CANCEL -> {pointer=-1;progress(startProgress,false);getParent().requestDisallowInterceptTouchEvent(false);return true;}
                default -> {return true;}
            }
        }
        @Override public boolean performClick(){super.performClick();return true;}
        private void step(int direction){cancelSnap();int value=(mirrored?speedLevel():reasoningLevel)+direction;
            progress(mirrored?ReasoningStrategy.mirror(value):value,true);settle();sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SELECTED);}
        @Override public boolean onKeyDown(int key,KeyEvent e){
            if(key==KeyEvent.KEYCODE_DPAD_RIGHT){step(1);return true;}
            if(key==KeyEvent.KEYCODE_DPAD_LEFT){step(-1);return true;}return super.onKeyDown(key,e);
        }
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){
            super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.SeekBar");
            info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,1,5,mirrored?speedLevel():reasoningLevel));
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);
            info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
        }
        @Override public boolean performAccessibilityAction(int action,Bundle arguments){
            if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD){step(1);return true;}
            if(action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){step(-1);return true;}
            if(action==AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId()&&arguments!=null){
                cancelSnap();float value=arguments.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE);
                progress(mirrored?ReasoningStrategy.mirror(value):value,true);settle();return true;}
            return super.performAccessibilityAction(action,arguments);
        }
    }
}
