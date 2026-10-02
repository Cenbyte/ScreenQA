package cn.screenqa.lite;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import java.util.function.Consumer;

final class RegionSelector extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box=new RectF();
    private final Consumer<Rect> selected;
    private final Runnable cancel;
    private float startX,startY;
    private boolean dragging;
    private final float density;
    RegionSelector(Context context,Consumer<Rect> selected,Runnable cancel) {
        super(context); this.selected=selected; this.cancel=cancel;
        density=getResources().getDisplayMetrics().density;
        setContentDescription("拖动框选题干和所有选项，松手确认；点击顶部取消");
    }
    @Override protected void onDraw(Canvas canvas) {
        canvas.drawColor(0x44000000);
        paint.setColor(0xFF167D71); canvas.drawRect(0,0,getWidth(),100*density,paint);
        paint.setColor(Color.WHITE); paint.setTextSize(17*density);
        canvas.drawText("拖动框选题干 + 全部选项",18*density,48*density,paint);
        paint.setTextSize(13*density); canvas.drawText("松手确认 · 点此顶部区域取消",18*density,76*density,paint);
        if(dragging) {
            paint.setColor(0x3355FFCA); canvas.drawRect(box,paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3*density); paint.setColor(0xFF55FFCA);
            canvas.drawRect(box,paint); paint.setStyle(Paint.Style.FILL);
        }
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        switch(e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if(e.getY()<100*density) { cancel.run(); return true; }
                startX=e.getX(); startY=e.getY(); dragging=true; box.set(startX,startY,startX,startY); break;
            case MotionEvent.ACTION_MOVE:
                if(dragging) update(e); break;
            case MotionEvent.ACTION_UP:
                if(dragging) {
                    update(e); dragging=false;
                    if(box.width()>=60*density && box.height()>=40*density) {
                        int[] location=new int[2]; getLocationOnScreen(location);
                        selected.accept(new Rect((int)box.left+location[0],(int)box.top+location[1],(int)box.right+location[0],(int)box.bottom+location[1]));
                    }
                } break;
            case MotionEvent.ACTION_CANCEL: dragging=false; break;
        }
        invalidate(); return true;
    }
    private void update(MotionEvent e) {
        float x=Math.max(0,Math.min(getWidth(),e.getX())),y=Math.max(0,Math.min(getHeight(),e.getY()));
        box.set(Math.min(startX,x),Math.min(startY,y),Math.max(startX,x),Math.max(startY,y));
    }
}
