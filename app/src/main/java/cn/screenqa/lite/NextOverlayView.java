package cn.screenqa.lite;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.PorterDuff;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/** Transparent second overlay. Reuses its existing click listener; no action/API code here. */
final class NextOverlayView extends View {
    interface Move { void by(int x,int y); }
    private final Move move;
    private final int slop;
    private float startX,startY,lastX,lastY;
    private boolean dragged;
    private Bitmap idleAtlas,tapAtlas;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Rect source=new Rect(),destination=new Rect();
    private final PetAnimation animation=new PetAnimation();
    private final Runnable tick=()->{invalidate();schedule();};
    NextOverlayView(Context context,Move move){
        super(context);this.move=move;slop=ViewConfiguration.get(context).getScaledTouchSlop();
        setBackground(null);setForeground(null);
        setDefaultFocusHighlightEnabled(false);setFocusable(false);
        setContentDescription("点击切到下一题并继续识题，拖动移动桌宠");
    }
    private void decode(){
        if(idleAtlas!=null)return;
        BitmapFactory.Options options=new BitmapFactory.Options();options.inScaled=false;
        idleAtlas=BitmapFactory.decodeResource(getResources(),R.drawable.wattson_idle_atlas,options);
        tapAtlas=BitmapFactory.decodeResource(getResources(),R.drawable.wattson_tap_atlas,options);
    }
    private void schedule(){removeCallbacks(tick);if(isAttachedToWindow()&&getWindowVisibility()==VISIBLE&&getVisibility()==VISIBLE)
        postDelayed(tick,animation.delay(SystemClock.uptimeMillis()));}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();decode();animation.idle(SystemClock.uptimeMillis());schedule();}
    @Override protected void onDetachedFromWindow(){
        removeCallbacks(tick);if(idleAtlas!=null){idleAtlas.recycle();idleAtlas=null;}if(tapAtlas!=null){tapAtlas.recycle();tapAtlas=null;}
        super.onDetachedFromWindow();
    }
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility==VISIBLE)schedule();else removeCallbacks(tick);}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);destination.set(0,0,w,h);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        // This View owns its entire transparent overlay surface. Clear the prior pose before drawing.
        canvas.drawColor(android.graphics.Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
        if(idleAtlas==null)return;
        long now=SystemClock.uptimeMillis();int frame=animation.frame(now);boolean tap=animation.tapping(now);int columns=tap?8:6;
        int x=frame%columns*192,y=frame/columns*192;source.set(x,y,x+192,y+192);
        canvas.drawBitmap(tap?tapAtlas:idleAtlas,source,destination,paint);
    }
    @Override public boolean isOpaque(){return false;}
    // Existing controller status calls have no visible text/background effect.
    void setText(String ignored){}
    @Override public boolean onTouchEvent(MotionEvent event){
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:startX=lastX=event.getRawX();startY=lastY=event.getRawY();dragged=false;return true;
            case MotionEvent.ACTION_MOVE:
                float x=event.getRawX(),y=event.getRawY();if(Math.abs(x-startX)>slop||Math.abs(y-startY)>slop)dragged=true;
                if(dragged)move.by(Math.round(x-lastX),Math.round(y-lastY));lastX=x;lastY=y;return true;
            case MotionEvent.ACTION_UP:if(!dragged&&isEnabled())performClick();return true;
            case MotionEvent.ACTION_CANCEL:return true;
        }
        return super.onTouchEvent(event);
    }
    @Override public boolean performClick(){
        if(!isEnabled())return false;
        animation.tap(SystemClock.uptimeMillis());invalidate();schedule();
        return super.performClick();
    }
}
