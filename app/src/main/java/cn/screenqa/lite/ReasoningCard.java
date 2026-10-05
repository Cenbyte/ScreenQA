package cn.screenqa.lite;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.widget.LinearLayout;

/** Only the reasoning card has an immersive backdrop; layout and padding stay unchanged. */
final class ReasoningCard extends LinearLayout {
    private final Path clip = new Path();
    private final RectF bounds = new RectF();
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ReasoningArtwork artwork;

    ReasoningCard(Context context) { this(context,ThemePalette.DEFAULT); }
    ReasoningCard(Context context, ThemePalette palette) {
        super(context);
        edge.setColor(palette.border);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Ui.dp(context,1));
    }
    void setArtwork(ReasoningArtwork value) { artwork=value; artwork.setHost(this); }
    @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight) {
        super.onSizeChanged(width,height,oldWidth,oldHeight);
        bounds.set(0,0,width,height);clip.reset();
        float radius=Ui.dp(getContext(),20);clip.addRoundRect(bounds,radius,radius,Path.Direction.CW);
    }
    @Override protected void dispatchDraw(Canvas canvas) {
        int saved=canvas.save();canvas.clipPath(clip);
        if(artwork!=null)artwork.draw(canvas);
        super.dispatchDraw(canvas);
        float radius=Ui.dp(getContext(),20),inset=edge.getStrokeWidth()/2;
        bounds.set(inset,inset,getWidth()-inset,getHeight()-inset);
        canvas.drawRoundRect(bounds,radius,radius,edge);
        canvas.restoreToCount(saved);
    }
}
