package cn.screenqa.lite;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;

/** Right-hand glass navigation, with vertical immediate selection. Phone docks are untouched. */
final class PadDock extends FrameLayout implements Dock {
    private final LiquidGlassView glass;
    private final LinearLayout items;
    private final View pill;
    private final List<ImageView> icons=new ArrayList<>();
    private final List<TextView> labels=new ArrayList<>();
    private final DockSwipeGesture swipe;
    private ThemePalette palette=ThemePalette.DEFAULT;
    private Listener listener;
    private int selected=-1;
    private boolean reduce;
    PadDock(Context context){
        super(context);setClipChildren(false);
        swipe=new DockSwipeGesture(ViewConfiguration.get(context).getScaledTouchSlop());
        glass=new LiquidGlassView(context);glass.setCornerRadius(Ui.dp(context,36));glass.setShadowRoom(Ui.dp(context,8));
        LayoutParams bg=new LayoutParams(-1,-1);bg.setMargins(Ui.dp(context,8),Ui.dp(context,8),Ui.dp(context,8),Ui.dp(context,8));addView(glass,bg);
        pill=new View(context);LayoutParams pillLp=new LayoutParams(-1,Ui.dp(context,68));pillLp.setMargins(Ui.dp(context,13),0,Ui.dp(context,13),0);addView(pill,pillLp);
        items=new LinearLayout(context);items.setOrientation(LinearLayout.VERTICAL);
        LayoutParams itemLp=new LayoutParams(-1,-1);itemLp.setMargins(Ui.dp(context,8),Ui.dp(context,14),Ui.dp(context,8),Ui.dp(context,14));addView(items,itemLp);
        items.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,orr,ob)->movePill(false));
    }
    public void setItems(String[] names,int[] resources){
        items.removeAllViews();icons.clear();labels.clear();
        for(int i=0;i<Math.min(names.length,resources.length);i++){
            LinearLayout item=new LinearLayout(getContext());item.setOrientation(LinearLayout.VERTICAL);item.setGravity(Gravity.CENTER);
            ImageView icon=new ImageView(getContext());icon.setImageResource(resources[i]);item.addView(icon,new LinearLayout.LayoutParams(Ui.dp(getContext(),25),Ui.dp(getContext(),25)));
            TextView label=new TextView(getContext());label.setText(names[i]);label.setTextSize(12);label.setTypeface(null,Typeface.BOLD);label.setGravity(Gravity.CENTER);label.setIncludeFontPadding(false);
            item.addView(label);icons.add(icon);labels.add(label);items.addView(item,new LinearLayout.LayoutParams(-1,0,1));
            final int id=i;item.setOnClickListener(v->choose(id));item.setContentDescription(names[i]);
        }applyColors();
    }
    private void choose(int id){if(id==selected||id<0||id>=labels.size())return;setSelected(id,true);if(listener!=null)listener.onSelected(id);}
    public void setSelected(int id,boolean animate){if(id<0||id>=labels.size())return;selected=id;applyColors();movePill(animate);}
    private void movePill(boolean animate){
        if(selected<0||labels.isEmpty()||items.getHeight()==0)return;
        int cell=items.getHeight()/labels.size(),height=cell-Ui.dp(getContext(),8);
        pill.getLayoutParams().height=height;float y=items.getTop()+selected*cell+Ui.dp(getContext(),4);pill.requestLayout();pill.animate().cancel();
        if(animate&&!reduce)pill.animate().translationY(y).setDuration(350).setInterpolator(Motion.SPRING).start();else pill.setTranslationY(y);
    }
    private void applyColors(){
        GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(Ui.dp(getContext(),28));bg.setColor(ThemePalette.alpha(palette.surface,.70f));bg.setStroke(Ui.dp(getContext(),1),ThemePalette.alpha(palette.border,.75f));pill.setBackground(bg);
        for(int i=0;i<labels.size();i++){int color=i==selected?palette.accent:palette.foreground;labels.get(i).setTextColor(color);icons.get(i).setColorFilter(color);items.getChildAt(i).setSelected(i==selected);}
    }
    public void setPalette(ThemePalette value){palette=value;glass.setPalette(value);applyColors();}
    public void setGlassEnabled(boolean enabled){glass.setGlassEnabled(enabled);}
    public void setReduceMotion(boolean value){reduce=value;}
    public void setListener(Listener value){listener=value;}
    public void setLifted(boolean lifted){}
    public void setBackdrop(Bitmap b,float scale,float x,float y){glass.setBackdrop(b,scale,x+glass.getLeft(),y+glass.getTop());}
    public void setBackdropSource(View source){}
    public boolean liveSampling(){return false;}
    private void selectAt(MotionEvent e){int target=swipe.selectionAt(e.getY(),e.getX(),items.getTop(),items.getLeft(),items.getHeight(),items.getWidth(),labels.size(),selected);if(target>=0)choose(target);}
    @Override public boolean onInterceptTouchEvent(MotionEvent e){
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN -> swipe.begin(e.getY(),e.getX(),glass.getTop(),glass.getLeft(),glass.getHeight(),glass.getWidth());
            case MotionEvent.ACTION_MOVE -> {swipe.move(e.getY(),e.getX());selectAt(e);}
            case MotionEvent.ACTION_POINTER_DOWN -> swipe.abort();
            case MotionEvent.ACTION_CANCEL -> swipe.reset();
        }return swipe.captured()||super.onInterceptTouchEvent(e);
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(!swipe.active())return super.onTouchEvent(e);
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_MOVE -> {swipe.move(e.getY(),e.getX());selectAt(e);}
            case MotionEvent.ACTION_POINTER_DOWN -> swipe.abort();
            case MotionEvent.ACTION_UP -> {selectAt(e);swipe.reset();performClick();}
            case MotionEvent.ACTION_CANCEL -> swipe.reset();
        }return true;
    }
    @Override public boolean performClick(){return super.performClick();}
}
