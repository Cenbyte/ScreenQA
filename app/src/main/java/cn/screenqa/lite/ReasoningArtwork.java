package cn.screenqa.lite;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.Log;
import android.view.View;
import java.util.concurrent.CompletableFuture;

/** Entire-card scene and intact poster share the rail's progress, never a separate clock. */
final class ReasoningArtwork {
    private static CompletableFuture<Bitmap[]> cache;
    private View host;
    private final boolean reduceMotion;
    private final ThemePalette palette;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF destination = new RectF(), ambientBounds = new RectF();
    private Bitmap[] images;
    private float progress;
    private int scrimWidth;

    ReasoningArtwork(View host, float progress, boolean reduceMotion, ThemePalette palette) {
        this.host=host;this.progress=progress;this.reduceMotion=reduceMotion;this.palette=palette;
        preload(host.getResources()).whenComplete((loaded,error)->this.host.post(()->{
            if(error!=null){Log.e("ReasoningArtwork","Could not preload local scenes",error);return;}
            images=loaded;this.host.invalidate();
        }));
    }
    void setHost(View value){host=value;scrimWidth=0;host.invalidate();}
    boolean drawsIn(View value){return host==value;}
    private static synchronized CompletableFuture<Bitmap[]> preload(Resources resources) {
        if(cache!=null)return cache;
        cache=new CompletableFuture<>();CompletableFuture<Bitmap[]> result=cache;
        new Thread(()->{
            try {
                int[] ids={R.drawable.reasoning_wattson_1,R.drawable.reasoning_wattson_2,
                        R.drawable.reasoning_wattson_3,R.drawable.reasoning_wattson_4,
                        R.drawable.reasoning_wattson_5,R.drawable.reasoning_wattson_aura,
                        R.drawable.reasoning_ambient_1,R.drawable.reasoning_ambient_2,
                        R.drawable.reasoning_ambient_3,R.drawable.reasoning_ambient_4,R.drawable.reasoning_ambient_5};
                Bitmap[] bitmaps=new Bitmap[ids.length];
                BitmapFactory.Options options=new BitmapFactory.Options();
                options.inScaled=false;options.inPreferredConfig=Bitmap.Config.ARGB_8888;
                for(int i=0;i<ids.length;i++){
                    bitmaps[i]=BitmapFactory.decodeResource(resources,ids[i],options);
                    if(bitmaps[i]==null)throw new IllegalStateException("Missing scene "+i);
                    bitmaps[i].prepareToDraw();
                }
                result.complete(bitmaps);
            }catch(Throwable error){result.completeExceptionally(error);}
        },"screenqa-art-preload").start();
        return cache;
    }
    void setProgress(float value){progress=ReasoningStrategy.clampProgress(value);host.invalidate();}
    void draw(Canvas canvas) {
        if(images==null||host.getWidth()==0||host.getHeight()==0)return;
        int lower=Math.min(4,(int)Math.floor(progress)-1);
        float fraction=progress-(lower+1);
        ambientBounds.set(0,0,host.getWidth(),host.getHeight());
        // Opaque base plus fractional overlay gives a true linear ambient blend, without a white dip.
        paint.setAlpha(255);canvas.drawBitmap(images[lower+6],null,ambientBounds,paint);
        float energy=Math.max(0,Math.min(1,progress-4));
        float aura=(float)Math.pow(energy,.75);
        if(lower<4&&fraction>0){
            float ambientFraction=lower==3?(float)Math.pow(fraction,.85):fraction;
            paint.setAlpha(Math.round(ambientFraction*255));
            canvas.drawBitmap(images[lower+7],null,ambientBounds,paint);
        }
        if(scrimWidth!=host.getWidth()){
            scrimWidth=host.getWidth();int color=palette.surface&0x00FFFFFF;
            scrimPaint.setShader(new LinearGradient(0,0,scrimWidth*.84f,0,
                    new int[]{color|0xC7000000,color|0xBE000000,color|0x68000000,color},
                    new float[]{0,.65f,.84f,1},Shader.TileMode.CLAMP));
        }
        canvas.drawRect(ambientBounds,scrimPaint);
        float height=host.getHeight(),width=Math.min(height*.5f,host.getWidth()*.42f);
        height=width*2;float top=(host.getHeight()-height)/2;
        destination.set(host.getWidth()-width,top,host.getWidth(),top+height);
        float dx=Ui.dp(host.getContext(),4);
        drawLayer(canvas,images[lower],1-fraction,reduceMotion?1:1+.015f*fraction,reduceMotion?0:-dx*fraction);
        if(lower<4&&fraction>0)
            drawLayer(canvas,images[lower+1],fraction,reduceMotion?1:.985f+.015f*fraction,reduceMotion?0:dx*(1-fraction));
        // Supplement only the early halo; it is already present in the intact fifth poster at 5.
        float lead=Math.max(0,aura-energy);
        if(lead>0)drawLayer(canvas,images[5],lead,1,0);
    }
    private void drawLayer(Canvas canvas,Bitmap bitmap,float alpha,float scale,float dx){
        if(alpha<=0)return;
        int saved=canvas.save();canvas.translate(dx,0);
        canvas.scale(scale,scale,destination.centerX(),destination.centerY());
        paint.setAlpha(Math.round(alpha*255));canvas.drawBitmap(bitmap,null,destination,paint);
        canvas.restoreToCount(saved);
    }
}
