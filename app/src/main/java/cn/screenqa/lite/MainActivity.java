package cn.screenqa.lite;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.CheckBox;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class MainActivity extends Activity implements CaptureService.UiObserver {
    private static final int CAPTURE=40,EXPORT_LOG=42,ACCESSIBILITY=43,EXPORT_USAGE=44;
    private Settings settings;
    private ThemePalette palette;
    private EditText key,customModelField,customEndpointField;
    private ApiSettingsSelection apiSelection;
    private TextView feedback,accessibilityStatus,captureStatus,logDetail,overlayPermissionStatus,overlaySettingsStatus,notificationStatus,tokenStats,rootStatus;
    private boolean rootRequestInFlight,rootAccessibilityPending;
    private Button test,captureButton,logButton;
    private ExecutorService executor;
    private ApiRequest testing;
    private boolean notificationAsked,resumeCaptureAfterAccessibility,notificationForCapture;
    private AlertDialog accessibilityDialog;
    private FrameLayout contentHost,pagesLayer;
    private AuroraBackground aurora;
    private Dock dock;
    private View[] pages=new View[3];
    private ScrollView[] scrolls=new ScrollView[3];
    private View[] details=new View[13];
    private int selectedTab,selectedDetail=-1;
    private int feedbackToken;
    private final GlassBackdrop backdrop=new GlassBackdrop();
    private final List<LiquidGlassView> glassViews=new ArrayList<>();
    private boolean backdropPending,lifted;
    private int uiGeneration;
    private ValueAnimator liquidDriver;
    private boolean uiReady;

    private int dp(float value){return Ui.dp(this,value);}

    @Override public void onCreate(Bundle bundle){
        super.onCreate(bundle);
        settings=new Settings(this);
        settings.migrateThemeDefault();
        palette=settings.theme();
        applyWindowColors();
        if(!UsageDeclaration.isAccepted(settings.prefs.getInt(UsageDeclaration.KEY,0))){
            showUsageDeclaration(bundle);
            return;
        }
        initializeUi(bundle);
    }

    private void initializeUi(Bundle bundle){
        uiReady=true;
        resumeCaptureAfterAccessibility=bundle!=null&&bundle.getBoolean("resumeCaptureAfterAccessibility",false);
        notificationAsked=bundle!=null&&bundle.getBoolean("notificationAsked",false);
        selectedTab=bundle==null?0:Math.max(0,Math.min(2,bundle.getInt("selectedTab",0)));
        selectedDetail=bundle==null?-1:bundle.getInt("selectedDetail",-1);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        int restoredDetail=selectedDetail;
        buildUi();
        showTab(selectedTab,false);
        if(restoredDetail>=0)openDetail(restoredDetail,false);
        updateLiveStatus();
        contentHost.postDelayed(()->QaLog.startAsync(this),250);
        if(getIntent().getBooleanExtra("request_accessibility",false)){
            getIntent().removeExtra("request_accessibility");
            contentHost.post(()->requestAccessibility(false));
        }
    }

    private void showUsageDeclaration(Bundle bundle){
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(palette.background);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(24),dp(48),dp(24),dp(32));
        scroll.addView(body);
        text(body,"欢迎使用大学生小帮手",24,palette.foreground,true);
        text(body,"请先阅读用途声明",16,palette.accent,true).setPadding(0,dp(16),0,dp(12));
        text(body,UsageDeclaration.BODY,15,palette.foreground,false);
        Button terms=action("阅读用户协议",false);addButton(body,terms,18);
        terms.setOnClickListener(v->showLegalDocument("用户协议","USER_AGREEMENT.md"));
        Button privacy=action("阅读隐私政策",false);addButton(body,privacy,10);
        privacy.setOnClickListener(v->showLegalDocument("隐私政策","PRIVACY_POLICY.md"));
        CheckBox agree=new CheckBox(this);
        agree.setText("我已阅读用途声明、用户协议与隐私政策，并承诺遵守用途限制");
        agree.setTextColor(palette.foreground);
        agree.setButtonTintList(ColorStateList.valueOf(palette.accent));
        body.addView(agree);
        Button proceed=action("同意并进入",true);addButton(body,proceed,12);
        proceed.setEnabled(false);
        agree.setOnCheckedChangeListener((button,checked)->proceed.setEnabled(checked));
        proceed.setOnClickListener(v->{
            settings.prefs.edit().putInt(UsageDeclaration.KEY,UsageDeclaration.REVISION)
                    .putLong("usage_declaration_accepted_at",System.currentTimeMillis()).apply();
            initializeUi(bundle);
            CaptureService.observe(this);
            if(aurora!=null)aurora.start();
            startLiquidDriver();
        });
        Button decline=action("不同意并退出",false);addButton(body,decline,10);
        decline.setOnClickListener(v->finish());
        setContentView(scroll);
    }

    private void showLegalDocument(String title,String asset){
        try(java.io.InputStream input=getAssets().open("legal/"+asset)){
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
            byte[] buffer=new byte[4096];int count;
            while((count=input.read(buffer))!=-1)bytes.write(buffer,0,count);
            showThemedInfo(title,bytes.toString(java.nio.charset.StandardCharsets.UTF_8.name()),false);
        }catch(java.io.IOException error){
            showThemedInfo(title,"文档加载失败，请退出后重试。",false);
        }
    }

    private void applyWindowColors(){
        getWindow().setStatusBarColor(palette.background);
        getWindow().setNavigationBarColor(palette.dark?palette.background:palette.surface);
        getWindow().getDecorView().setSystemUiVisibility(palette.dark?0:
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    private void buildUi(){
        FrameLayout root=new FrameLayout(this);
        root.setBackgroundColor(palette.background);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
                android.graphics.Insets ime=insets.getInsets(WindowInsets.Type.ime());
                v.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,ime.bottom));
            }else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });

        // The page layer is what the dock samples, so it has to be opaque: the aurora lives inside
        // it and the layer carries the themed background colour itself.
        pagesLayer=new FrameLayout(this);
        pagesLayer.setBackgroundColor(palette.background);
        root.addView(pagesLayer,new FrameLayout.LayoutParams(-1,-1));
        aurora=new AuroraBackground(this);
        aurora.setPalette(palette);
        aurora.setReduceMotion(settings.reduceMotion());
        pagesLayer.addView(aurora,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout column=new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        pagesLayer.addView(column,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout top=new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(20),dp(12),dp(20),dp(6));
        column.addView(top);
        LinearLayout brand=new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(brand);
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.ic_app);
        brand.addView(logo,new LinearLayout.LayoutParams(dp(40),dp(40)));
        LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams namesLp=new LinearLayout.LayoutParams(0,-2,1);namesLp.leftMargin=dp(12);
        brand.addView(names,namesLp);
        text(names,"大学生小帮手",21,palette.foreground,true);
        text(names,BuildConfig.DEVELOPER_BUILD?"开发者版本 / Developer Build":"个人学习 · 独立思考",
                10,BuildConfig.DEVELOPER_BUILD?palette.accent:palette.secondary,true);
        TextView version=text(brand,BuildConfig.VERSION_NAME,11,palette.accent,true);
        version.setPadding(dp(10),dp(5),dp(10),dp(5));
        version.setBackground(shape(palette.accentSoft,0,dp(100),0));

        TextView declaration=text(top,UsageDeclaration.MARQUEE,12,palette.accent,false);
        declaration.setSingleLine(true);
        declaration.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        declaration.setMarqueeRepeatLimit(-1);
        declaration.setSelected(true);
        declaration.setContentDescription(UsageDeclaration.MARQUEE);
        declaration.setPadding(0,dp(10),0,dp(4));
        declaration.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));

        feedback=text(top,"",13,palette.accent,false);
        feedback.setVisibility(View.GONE);
        feedback.setPadding(dp(12),dp(8),dp(12),dp(8));
        LinearLayout.LayoutParams feedbackLp=(LinearLayout.LayoutParams)feedback.getLayoutParams();
        feedbackLp.topMargin=dp(10);
        feedback.setBackground(shape(palette.accentSoft,0,dp(11),0));

        contentHost=new FrameLayout(this);
        contentHost.setClipToPadding(false);
        column.addView(contentHost,new LinearLayout.LayoutParams(-1,0,1));

        dock=createDock();
        dock.setPalette(palette);
        dock.setGlassEnabled(settings.glassDock());
        dock.setReduceMotion(settings.reduceMotion());
        dock.setItems(new String[]{"首页","设置","我的"},
                new int[]{R.drawable.ic_dock_home,R.drawable.ic_dock_settings,R.drawable.ic_dock_profile});
        dock.setListener(index->showTab(index,true));
        dock.setBackdropSource(pagesLayer);
        aurora.setBackdropChanged(()->{
            if(dock instanceof LiquidDock)((LiquidDock)dock).refreshBackdrop();
        });
        FrameLayout.LayoutParams dockLp=new FrameLayout.LayoutParams(-1,
                Dock.heightFor(this),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        dockLp.bottomMargin=dp(12);
        root.addView((View)dock,dockLp);

        pages[0]=page(0);
        buildHome((LinearLayout)pages[0].getTag());
        pages[0].setVisibility(View.GONE);
        contentHost.addView(pages[0]);

        setContentView(root);
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,orr,ob)->scheduleBackdrop());
    }

    /**
     * Live liquid glass on devices that carry the native blur pipeline (arm64-v8a), the hand-drawn
     * glass panel everywhere else. Any load/link failure downgrades instead of crashing the app.
     */
    private Dock createDock(){
        if(!LiquidDock.isSupported())return new GlassDock(this);
        try{
            return new LiquidDock(this);
        }catch(Throwable error){
            QaLog.event("dock liquid-fallback "+error.getClass().getSimpleName());
            return new GlassDock(this);
        }
    }

    private ScrollView page(int index){
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        // Reserve the dock at the end of the scroll range but let the cards travel under the capsule:
        // the glass needs moving content behind it to read as frosted glass.
        scroll.setPadding(0,0,0,Dock.heightFor(this)+dp(8));
        FrameLayout shell=new FrameLayout(this);
        scroll.addView(shell);
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18),dp(12),dp(18),dp(28));
        int width=Math.min(getResources().getDisplayMetrics().widthPixels,dp(640));
        shell.addView(content,new FrameLayout.LayoutParams(width,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL));
        shell.addOnLayoutChangeListener((v,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{
            int available=right-left;
            if(available<=0)return;
            int desired=Math.min(available,dp(640));
            FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)content.getLayoutParams();
            if(params.width!=desired){params.width=desired;content.setLayoutParams(params);}
        });
        scroll.setOnScrollChangeListener((v,sx,sy,osx,osy)->{
            boolean next=sy>dp(6);
            if(next!=lifted){lifted=next;if(dock!=null)dock.setLifted(next);}
            // The library dock refreshes itself on scroll; only the hand-drawn fallback needs
            // fresh bitmaps while the content slides under the capsule.
            if(dock==null||!dock.liveSampling())scheduleBackdrop(next?170:90);
        });
        scroll.setTag(content);
        if(index>=0)scrolls[index]=scroll;
        return scroll;
    }

    private void scheduleBackdrop(){scheduleBackdrop(90);}

    private void scheduleBackdrop(int delayMs){
        if(backdropPending||pagesLayer==null)return;
        final int generation=uiGeneration;
        backdropPending=true;
        pagesLayer.postDelayed(()->{
            if(generation!=uiGeneration)return;
            backdropPending=false;
            if(isDestroyed()||pagesLayer.getWidth()<=4)return;
            if(dock instanceof LiquidDock)((LiquidDock)dock).refreshBackdrop();
            boolean visibleGlass=false;
            for(LiquidGlassView view:glassViews)if(view.isShown()){visibleGlass=true;break;}
            if(dock!=null&&dock.liveSampling()&&!visibleGlass)return;
            // Exclude existing glass snapshots from the next snapshot (no recursive feedback).
            for(LiquidGlassView view:glassViews)view.setCapturingBackdrop(true);
            try{if(backdrop.capture(pagesLayer,0.2f,4))applyBackdrop();}
            finally{for(LiquidGlassView view:glassViews)view.setCapturingBackdrop(false);}
        },delayMs);
    }

    private void applyBackdrop(){
        if(pagesLayer==null||backdrop.bitmap()==null)return;
        int[] origin=new int[2];
        pagesLayer.getLocationInWindow(origin);
        for(LiquidGlassView view:glassViews)attachBackdrop(view,origin);
        if(dock!=null){
            int[] loc=new int[2];
            ((View)dock).getLocationInWindow(loc);
            dock.setBackdrop(backdrop.bitmap(),backdrop.scale(),loc[0]-origin[0],loc[1]-origin[1]);
        }
    }

    private void attachBackdrop(LiquidGlassView view,int[] origin){
        if(view==null||view.getParent()==null||view.getWidth()==0)return;
        int[] loc=new int[2];
        view.getLocationInWindow(loc);
        view.setBackdrop(backdrop.bitmap(),backdrop.scale(),loc[0]-origin[0],loc[1]-origin[1]);
    }

    /** One slow driver for visible glass cards. */
    private void startLiquidDriver(){
        if(settings.reduceMotion()||liquidDriver!=null)return;
        final float[] last={-1f};
        liquidDriver=ValueAnimator.ofFloat(0f,1f);
        liquidDriver.setDuration(11000L);
        liquidDriver.setRepeatCount(ValueAnimator.INFINITE);
        liquidDriver.setInterpolator(new LinearInterpolator());
        liquidDriver.addUpdateListener(animation->{
            float value=(float)animation.getAnimatedValue();
            if(Math.abs(value-last[0])<0.012f)return;
            last[0]=value;
            for(LiquidGlassView view:glassViews)if(view.isShown())view.setLiquidPhase(value);
        });
        liquidDriver.start();
    }

    private void stopLiquidDriver(){
        if(liquidDriver!=null){liquidDriver.cancel();liquidDriver=null;}
    }

    private void rebuildForTheme(){
        // The stored theme has to be read back first: rebuilding with the old palette is exactly why
        // switching a theme used to leave every colour untouched.
        int keepDetail=selectedDetail;
        palette=settings.theme();
        applyWindowColors();
        View decor=getWindow().getDecorView();
        decor.animate().cancel();
        decor.setAlpha(0.45f);
        decor.animate().alpha(1f).setDuration(Motion.SLOW).setInterpolator(Motion.SPRING).start();
        if(aurora!=null)aurora.stop();
        stopLiquidDriver();
        uiGeneration++;
        backdrop.release();
        backdropPending=false;
        lifted=false;
        glassViews.clear();
        pages=new View[3];
        scrolls=new ScrollView[3];
        details=new View[13];
        selectedDetail=-1;
        dock=null;
        aurora=null;
        contentHost=null;
        pagesLayer=null;
        feedback=null;
        key=null;
        test=null;
        captureStatus=null;
        captureButton=null;
        accessibilityStatus=null;
        overlayPermissionStatus=null;
        overlaySettingsStatus=null;
        notificationStatus=null;
        logDetail=null;
        logButton=null;
        rootStatus=null;
        tokenStats=null;
        customModelField=null;
        customEndpointField=null;
        apiSelection=null;
        buildUi();
        showTab(selectedTab,false);
        // Stay on the page the user was reading instead of dropping back to the tab landing page.
        if(keepDetail>=0)openDetail(keepDetail,false);
        startLiquidDriver();
        updateLiveStatus();
    }

    private void showTab(int index,boolean animate){
        selectedDetail=-1;
        lifted=false;
        if(dock!=null)dock.setLifted(false);
        if(pages[index]==null){
            pages[index]=page(index);
            if(index==1)buildSettingsLanding((LinearLayout)pages[index].getTag());
            else buildAccount((LinearLayout)pages[index].getTag());
            contentHost.addView(pages[index]);
        }
        selectedTab=index;
        for(int i=0;i<3;i++){
            if(pages[i]!=null){
                pages[i].animate().cancel();
                pages[i].setVisibility(i==index?View.VISIBLE:View.GONE);
            }
        }
        for(View detail:details)if(detail!=null)detail.setVisibility(View.GONE);
        if(dock!=null)dock.setSelected(index,animate);
        if(animate){
            View target=pages[index];
            if(target instanceof ScrollView){
                LinearLayout body=(LinearLayout)((ScrollView)target).getTag();
                if(body!=null)for(int i=0;i<body.getChildCount();i++)
                    Motion.entrance(body.getChildAt(i),i,dp(20),settings.reduceMotion());
            }
            InputMethodManager keyboard=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            if(keyboard!=null)keyboard.hideSoftInputFromWindow(contentHost.getWindowToken(),0);
        }
        scheduleBackdrop();
    }

    private GradientDrawable shape(int fill,int stroke,int radius,int strokeWidth){
        GradientDrawable bg=new GradientDrawable();bg.setColor(fill);bg.setCornerRadius(radius);
        if(strokeWidth>0)bg.setStroke(strokeWidth,stroke);return bg;
    }
    private RippleDrawable ripple(int fill,int rippleColor,int radius){
        return new RippleDrawable(ColorStateList.valueOf(rippleColor),shape(fill,0,radius,0),null);
    }
    private TextView text(LinearLayout parent,String value,int size,int color,boolean bold){
        TextView label=new TextView(this);label.setText(value);label.setTextSize(size);label.setTextColor(color);
        label.setLineSpacing(dp(2),1);if(bold)label.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        parent.addView(label);return label;
    }
    private LinearLayout card(LinearLayout parent){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18),dp(17),dp(18),dp(17));
        card.setBackground(shape(palette.surface,palette.border,dp(20),dp(1)));card.setElevation(dp(1));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(14);parent.addView(card,lp);
        return card;
    }
    private GlassCard glassCard(LinearLayout parent,float cornerDp){
        GlassCard card=new GlassCard(this,palette,settings.glassDock(),cornerDp);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(14);
        parent.addView(card,lp);
        glassViews.add(card.glass());
        return card;
    }
    private void section(LinearLayout parent,String name,String sub){
        TextView title=text(parent,name,20,palette.foreground,true);title.setPadding(dp(2),dp(11),0,0);
        TextView caption=text(parent,sub,13,palette.secondary,false);caption.setPadding(dp(2),dp(2),0,dp(14));
    }
    private Button action(String label,boolean filled){
        Button button=new Button(this);button.setText(label);button.setAllCaps(false);button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        button.setTextColor(filled?palette.onAccent:palette.accent);
        button.setMinimumHeight(dp(50));button.setPadding(dp(10),dp(8),dp(10),dp(8));
        button.setBackground(ripple(filled?palette.accent:palette.accentSoft,
                filled?0x55FFFFFF:ThemePalette.alpha(palette.accent,0.22f),dp(14)));
        return button;
    }
    private void addButton(LinearLayout parent,Button button,int top){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(top);parent.addView(button,lp);
    }
    private EditText field(String hint,String value,int inputType){
        EditText field=new ClipboardEditText(this);
        field.setSingleLine(true);field.setHint(hint);field.setText(value);
        field.setTextColor(palette.foreground);field.setHintTextColor(palette.secondary);field.setTextSize(14);
        field.setInputType(inputType);
        field.setPadding(dp(14),dp(10),dp(14),dp(10));
        field.setBackground(shape(palette.background,palette.border,dp(12),dp(1)));
        field.setMinimumHeight(dp(50));
        return field;
    }
    private TextView chip(String value){
        TextView chip=new TextView(this);
        chip.setText(value);chip.setTextSize(11);chip.setTextColor(palette.accent);
        chip.setPadding(dp(11),dp(5),dp(11),dp(5));
        chip.setBackground(shape(ThemePalette.alpha(palette.accent,0.12f),
                ThemePalette.alpha(palette.accent,0.35f),dp(100),Math.max(1,dp(1))));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);
        lp.rightMargin=dp(8);
        chip.setLayoutParams(lp);
        return chip;
    }

    private void buildHome(LinearLayout page){
        LinearLayout hero=new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(22),dp(24),dp(22),dp(22));
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{palette.heroStart,palette.heroEnd});
        bg.setCornerRadius(dp(26));hero.setBackground(bg);
        LinearLayout.LayoutParams heroLp=new LinearLayout.LayoutParams(-1,-2);heroLp.bottomMargin=dp(16);
        page.addView(hero,heroLp);
        TextView overline=text(hero,"主控制台",12,ThemePalette.blend(palette.onAccent,palette.accent,0.55f),true);
        overline.setLetterSpacing(0.12f);
        TextView headline=text(hero,"自动识题，快速看答案",28,palette.onAccent,true);
        headline.setPadding(0,dp(11),0,dp(7));
        text(hero,"开启后切到题目页面，自动识别并显示答案。",14,ThemePalette.alpha(palette.onAccent,0.80f),false);
        captureStatus=text(hero,"",13,ThemePalette.blend(palette.onAccent,palette.accent,0.4f),true);
        captureStatus.setPadding(0,dp(17),0,dp(4));
        captureButton=action("开启悬浮助手",true);
        captureButton.setTextColor(palette.onAccent);
        LinearLayout captureControls=new LinearLayout(this);captureControls.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams captureControlsLp=new LinearLayout.LayoutParams(-1,-2);captureControlsLp.topMargin=dp(12);
        hero.addView(captureControls,captureControlsLp);
        captureControls.addView(captureButton,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout petControl=new LinearLayout(this);petControl.setOrientation(LinearLayout.VERTICAL);petControl.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams petControlLp=new LinearLayout.LayoutParams(dp(90),-2);petControlLp.leftMargin=dp(8);
        captureControls.addView(petControl,petControlLp);
        LinearLayout petLabel=new LinearLayout(this);petLabel.setGravity(Gravity.CENTER_VERTICAL);
        petControl.addView(petLabel,new LinearLayout.LayoutParams(-2,-2));
        TextView petTitle=text(petLabel,"2号桌宠",12,palette.onAccent,true);
        // The text helper uses the parent's default width; this horizontal row needs wrap-content.
        petTitle.setLayoutParams(new LinearLayout.LayoutParams(-2,-2));
        petTitle.setIncludeFontPadding(false);
        TextView petHelp=new TextView(this);petHelp.setText("?");petHelp.setTextSize(12);petHelp.setTextColor(palette.onAccent);
        petHelp.setGravity(Gravity.CENTER);petHelp.setIncludeFontPadding(false);petHelp.setContentDescription("2号桌宠介绍");
        GradientDrawable helpCircle=new GradientDrawable();helpCircle.setShape(GradientDrawable.OVAL);
        helpCircle.setColor(android.graphics.Color.TRANSPARENT);helpCircle.setStroke(dp(1),palette.onAccent);
        petHelp.setBackground(helpCircle);petHelp.setDefaultFocusHighlightEnabled(false);
        LinearLayout.LayoutParams petHelpLp=new LinearLayout.LayoutParams(dp(18),dp(18));petHelpLp.leftMargin=dp(4);
        petLabel.addView(petHelp,petHelpLp);petHelp.setOnClickListener(v->showPetIntroduction());
        GoldSwitch petSwitch=new GoldSwitch(this);petSwitch.setPalette(palette);petSwitch.setReduceMotion(settings.reduceMotion());
        // This hand-drawn control has its own track; the framework focus rectangle must not show.
        petSwitch.setDefaultFocusHighlightEnabled(false);petSwitch.setBackground(null);petSwitch.setForeground(null);
        petSwitch.setThumbGlow(false);
        petSwitch.setCheckedSilently(settings.nextOverlay());petSwitch.setContentDescription("2号桌宠悬浮窗，主助手开启后生效");
        petSwitch.setListener(value->{
            settings.setNextOverlay(value);
            if(CaptureService.active)startService(new Intent(this,CaptureService.class).setAction("NEXT_OVERLAY"));
        });
        petControl.addView(petSwitch,new LinearLayout.LayoutParams(-2,-2));
        captureButton.setOnClickListener(v->{
            if(CaptureService.active){
                stopService(new Intent(this,CaptureService.class));
                showFeedback("正在关闭悬浮助手");
                captureButton.postDelayed(this::updateLiveStatus,300);
            }else startCapture();
        });
        Button permissions=action("权限说明与状态",false);
        addButton(page,permissions,4);
        permissions.setOnClickListener(v->openDetail(7,true));
        buildHomeGuide(page);
    }

    private void showPetIntroduction(){
        showThemedInfo("2号桌宠","点击桌宠快捷强制开始识别题目，桌宠形象为沃特森抱着小水怪，此乃apex萌物",true);
    }
    private void showThemedInfo(String heading,String message,boolean pet){
        showThemedInfo(heading,message,pet,null);
    }
    private void showThemedInfo(String heading,String message,boolean pet,Runnable continueAction){
        android.app.Dialog dialog=new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(24),dp(24),dp(24),dp(24));
        content.setBackground(shape(palette.surface,palette.border,dp(24),dp(1)));
        TextView title=text(content,heading,24,palette.foreground,true);title.setPadding(0,0,0,dp(16));
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body);
        TextView paragraph=text(body,message,15,palette.foreground,false);paragraph.setTextIsSelectable(!pet);
        paragraph.setLinkTextColor(palette.accent);
        if(!pet)android.text.util.Linkify.addLinks(paragraph,android.text.util.Linkify.WEB_URLS);
        if(pet){
            TextView face=text(body,"(｡•ᴗ•｡)♡",22,palette.accent,false);face.setGravity(Gravity.CENTER);
            face.setPadding(0,dp(22),0,dp(8));face.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));
        }
        content.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
        scroll.setPadding(0,0,0,dp(16));
        if(continueAction==null){
            Button close=action("知道啦",true);addButton(content,close,0);close.setOnClickListener(v->dialog.dismiss());
        }else{
            LinearLayout buttons=new LinearLayout(this);buttons.setGravity(Gravity.CENTER_VERTICAL);
            content.addView(buttons,new LinearLayout.LayoutParams(-1,-2));
            Button cancel=action("取消",false),proceed=action("继续",true);
            buttons.addView(cancel,new LinearLayout.LayoutParams(0,-2,1));
            LinearLayout.LayoutParams proceedLp=new LinearLayout.LayoutParams(0,-2,1);proceedLp.leftMargin=dp(10);buttons.addView(proceed,proceedLp);
            cancel.setOnClickListener(v->dialog.dismiss());
            proceed.setOnClickListener(v->{proceed.setEnabled(false);dialog.dismiss();continueAction.run();});
        }
        dialog.setContentView(content);
        android.view.Window window=dialog.getWindow();
        if(window!=null){window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(0.32f);}
        dialog.show();
        if(window!=null){
            int width=Math.min(getResources().getDisplayMetrics().widthPixels-dp(40),dp(420));
            window.setLayout(width,-2);
            scroll.post(()->{int limit=Math.round(getResources().getDisplayMetrics().heightPixels*0.5f);if(scroll.getHeight()>limit){scroll.setLayoutParams(new LinearLayout.LayoutParams(-1,limit));window.setLayout(width,-2);}});
        }
    }
    private void buildHomeGuide(LinearLayout page){
        section(page,"公告","请先了解当前功能的使用边界");
        LinearLayout notice=card(page);
        text(notice,"识别看答案已较成熟，自动选择仍需留意",16,palette.accent,true);
        text(notice,"自动识别并展示答案功能已经比较成熟，适合学习练习时查看参考解析；AI 答案仍需自行核验。",14,palette.foreground,false);
        text(notice,"自动选择答案的稳定性还不够，可以在有人看护时尝试，但不建议用于无人值守、挂机刷题。出现误选或卡住时，请关闭辅助自动执行，继续使用识别和复制答案。",14,palette.secondary,false);

        section(page,"首次使用教学","常驻首页，随时回来查看");
        LinearLayout guide=card(page);
        guideStep(guide,"1  配置 AI","先按下面的教程获取自己的 DeepSeek API Key，然后到“设置 → AI 与模型”粘贴 Key，选择官方接口并保存配置、测试连接。");
        Button ai=action("去配置 AI 与模型",false);addButton(guide,ai,8);
        ai.setOnClickListener(v->openDetail(2,true));
        guideStep(guide,"2  开启悬浮助手","点击首页“开启悬浮助手”，按提示允许悬浮窗和屏幕共享；选择整个屏幕。授权通知便于查看运行状态和停止入口。");
        guideStep(guide,"3  开始识别","切到学习练习页面，在悬浮窗点击开始。让题干和选项完整显示，停止滚动，把小水怪移开题目；允许无障碍可帮助读取页面文字，未开启时使用 OCR。");
        guideStep(guide,"4  查看与复制","答案会在小水怪旁自动展开。填空、简答完整答案会自动复制，也可以点小复制图标。长答案可滚动；关闭后立即继续识题。需要重看时，点击小水怪展开控制面板，再点查看答案。");
        guideStep(guide,"5  暂停或停止","悬浮窗可暂停、继续或关闭；也可以回首页停止助手。自动选择、填写和下一题默认关闭，需要尝试时到“设置 → 更多 → 辅助自动执行”单独开启。");

        section(page,"DeepSeek API 获取教程","在官方平台创建自己的 Key");
        LinearLayout apiGuide=card(page);
        guideStep(apiGuide,"1  登录官方开放平台","打开下方官方平台入口，按平台提示注册或登录，在 API Keys 页面创建一个 Key，创建后复制并妥善保存。");
        Button platform=action("打开 DeepSeek API Keys",false);addButton(apiGuide,platform,8);
        platform.setOnClickListener(v->openGuideUrl("https://platform.deepseek.com/api_keys"));
        guideStep(apiGuide,"2  确认 API 账户可用","API 按实际 Token 用量计费，平台余额不足时无法调用；是否充值和金额由你自行决定，以平台当前规则为准。聊天网页可以使用，不代表 API 账户一定可调用。");
        guideStep(apiGuide,"3  填入本应用","进入“设置 → AI 与模型”，粘贴 Key，接口选“DeepSeek 官方”，模型选 deepseek-flash；本应用的默认接口为 https://api.deepseek.com/chat/completions。点击“保存配置”后再“测试连接”。");
        guideStep(apiGuide,"4  连接失败时","401：检查 Key 是否正确；402：检查 DeepSeek API 账户余额；429：请求过快，稍后再试；500/503：服务异常或繁忙。具体原因以页面错误提示和官方说明为准。");
        text(apiGuide,"不要把 API Key 发给别人、公开截图或上传到代码仓库。教程依据 DeepSeek 官方文档整理，界面和计费规则可能调整。",12,palette.secondary,false);
        Button docs=action("查看 DeepSeek 官方 API 文档",false);addButton(apiGuide,docs,8);
        docs.setOnClickListener(v->openGuideUrl("https://api-docs.deepseek.com/"));
    }
    private void guideStep(LinearLayout box,String title,String body){
        text(box,title,15,palette.foreground,true).setPadding(0,dp(14),0,dp(4));
        text(box,body,13,palette.secondary,false);
    }
    private void openGuideUrl(String url){
        try {startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
        catch(android.content.ActivityNotFoundException e){showFeedback("未找到可打开网页的浏览器，请手动访问："+url);}
    }

    private TextView statusLine(LinearLayout box,int icon,String title,String value){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(row,new LinearLayout.LayoutParams(-1,-2));
        ImageView image=new ImageView(this);image.setImageResource(icon);image.setColorFilter(palette.accent);
        row.addView(image,new LinearLayout.LayoutParams(dp(23),dp(23)));
        LinearLayout line=new LinearLayout(this);line.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=dp(13);row.addView(line,lp);
        text(line,title,14,palette.foreground,true);
        TextView detail=text(line,value,12,palette.secondary,false);detail.setPadding(0,dp(3),0,0);
        return detail;
    }
    private void divider(LinearLayout box){
        View rule=new View(this);rule.setBackgroundColor(palette.border);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));
        lp.topMargin=dp(14);lp.bottomMargin=dp(14);box.addView(rule,lp);
    }
    private GoldSwitch settingRow(LinearLayout box,String title,String subtitle,boolean initial,Consumer<Boolean> changed){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(64));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(6);box.addView(row,lp);
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        text(labels,title,15,palette.foreground,true);
        text(labels,subtitle,12,palette.secondary,false);
        GoldSwitch toggle=new GoldSwitch(this);
        toggle.setPalette(palette);
        toggle.setReduceMotion(settings.reduceMotion());
        toggle.setCheckedSilently(initial);
        toggle.setContentDescription(title);
        toggle.setListener(changed::accept);
        LinearLayout.LayoutParams switchLp=new LinearLayout.LayoutParams(-2,-2);switchLp.leftMargin=dp(10);
        row.addView(toggle,switchLp);
        row.setOnClickListener(v->{
            boolean next=!toggle.isChecked();
            toggle.setChecked(next,true);
            changed.accept(next);
        });
        return toggle;
    }
    private void entry(LinearLayout page,String title,String subtitle,int destination){
        LinearLayout item=card(page);item.setMinimumHeight(dp(72));
        item.setBackground(ripple(palette.surface,ThemePalette.alpha(palette.accent,0.16f),dp(20)));
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);item.addView(row);
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        text(labels,title,16,palette.foreground,true);
        text(labels,subtitle,12,palette.secondary,false).setPadding(0,dp(3),0,0);
        text(row,"›",26,palette.secondary,false);
        item.setContentDescription(title+"，"+subtitle);
        item.setOnClickListener(v->openDetail(destination,true));
        item.setOnTouchListener((v,event)->{
            int action=event.getActionMasked();
            if(action==android.view.MotionEvent.ACTION_DOWN)Motion.press(v,true,settings.reduceMotion());
            else if(action==android.view.MotionEvent.ACTION_UP||action==android.view.MotionEvent.ACTION_CANCEL)
                Motion.press(v,false,settings.reduceMotion());
            return false;
        });
    }
    private void buildSettingsLanding(LinearLayout page){
        section(page,"设置",BuildConfig.DEVELOPER_BUILD?"按用途调整答题与诊断选项":"调整答题与运行选项");
        section(page,"外观","主题、液态玻璃与动效");
        entry(page,"外观与主题","默认悬浮窗绿 · 7 套配色 · 玻璃与动效开关",11);
        section(page,"识题与答案","最常用的行为设置");
        entry(page,"识别","选择智能混合或识别优先级",1);
        entry(page,"AI 与模型","DeepSeek Key、模型与接口地址",2);
        entry(page,"Token 用量","最近、今日与累计实际用量",9);
        section(page,"运行与权限","屏幕上的显示和系统授权");
        entry(page,"悬浮窗","显示权限与使用说明",3);
        entry(page,"权限说明","查看授权状态与开启方式",7);
        section(page,"更多","诊断、兼容性与后续扩展");
        if(BuildConfig.DIAGNOSTICS_ENABLED)
            entry(page,"日志与诊断","开始或停止记录，查看、导出、清除",5);
        entry(page,"高级设置","手动框选兼容入口",6);
        entry(page,"辅助自动执行","可选：选答、填写与下一题 · 默认关闭",0);
        if(BuildConfig.ROOT_SUPPORTED)entry(page,"Root","权限、自动授权与答案点击",4);
        entry(page,"某 APP 专用自动答题","后续接入指定 APP 流程",8);
    }
    private int parentTabFor(int id){return id==10||id==11||id==12?2:1;}

    private void openDetail(int id,boolean animate){
        if(id<0||id>=details.length)return;
        if(id==5&&!BuildConfig.DIAGNOSTICS_ENABLED)return;
        if(id==4&&!BuildConfig.ROOT_SUPPORTED)return;
        if(details[id]==null){
            ScrollView view=page(-1);
            LinearLayout body=(LinearLayout)view.getTag();
            int parentTab=parentTabFor(id);
            Button back=action(parentTab==2?"‹  返回我的":"‹  返回设置",false);
            addButton(body,back,0);
            back.setOnClickListener(v->showTab(parentTab,true));
            switch(id){
                case 0 -> buildAutoSettings(body);
                case 1 -> buildRecognitionSettings(body);
                case 2 -> buildAiSettings(body);
                case 3 -> buildOverlaySettings(body);
                case 4 -> {if(BuildConfig.ROOT_SUPPORTED)buildRootSettings(body);}
                case 5 -> {if(BuildConfig.DIAGNOSTICS_ENABLED)buildLogs(body);}
                case 6 -> buildAdvancedSettings(body);
                case 7 -> buildPermissionPage(body);
                case 8 -> buildDedicatedPlaceholder(body);
                case 9 -> buildTokenStats(body);
                case 10 -> buildAbout(body);
                case 11 -> buildAppearanceSettings(body);
                case 12 -> buildSponsor(body);
                default -> {return;}
            }
            details[id]=view;
            contentHost.addView(view);
        }
        selectedDetail=id;
        for(View page:pages)if(page!=null)page.setVisibility(View.GONE);
        for(int i=0;i<details.length;i++)if(details[i]!=null)details[i].setVisibility(i==id?View.VISIBLE:View.GONE);
        selectedTab=parentTabFor(id);
        if(dock!=null){dock.setLifted(false);dock.setSelected(selectedTab,false);}
        if(animate&&details[id] instanceof ScrollView){
            LinearLayout body=(LinearLayout)((ScrollView)details[id]).getTag();
            if(body!=null)for(int i=0;i<body.getChildCount();i++)
                Motion.entrance(body.getChildAt(i),i,dp(16),settings.reduceMotion());
        }
        if(id==9)renderTokenStats();
        if(id==12&&sponsorBlessing!=null)sponsorBlessing.setText(SponsorBlessings.next());
        updateLiveStatus();
        scheduleBackdrop();
    }

    private void buildOverlaySettings(LinearLayout page){
        section(page,"悬浮窗","在其他应用上显示识题与答案");
        LinearLayout card=card(page);
        overlaySettingsStatus=statusLine(card,R.drawable.ic_auto,"显示在其他应用上层","检查中");
        Button open=action("打开悬浮窗权限设置",false);addButton(card,open,15);
        open.setOnClickListener(v->openOverlayPermission());
        text(page,"悬浮窗在屏幕共享启动后出现；可在悬浮窗内暂停、继续或关闭。",12,palette.secondary,false);
    }
    private void buildRootSettings(LinearLayout page){
        section(page,"Root 增强","可选权限增强 · 默认关闭");
        LinearLayout card=card(page);
        rootStatus=text(card,"Root 尚未检查",16,palette.foreground,true);
        settingRow(card,"启用 Root 增强","关闭后继续使用原有无障碍方式",settings.rootEnabled(),enabled->{
            settings.setRootEnabled(enabled);refreshAutoSettings();
            if(enabled)requestRootAccess();
            else {RootManager.get().forget();updateRootStatus();}
        });
        settingRow(card,"自动启用无障碍服务","保留设备已开启的其他服务",
                settings.prefs.getBoolean("root_auto_accessibility",true),enabled->{
            settings.setRootAutoAccessibility(enabled);refreshAutoSettings();
        });
        settingRow(card,"Root 虚拟触摸","用于选择答案、下一题和滑动；顺序可在辅助自动执行设置中调整",
                settings.prefs.getBoolean("root_answer_tap",true),enabled->{
            settings.setRootAnswerTap(enabled);refreshAutoSettings();
        });
        Button grant=action("检查 / 申请 Root 权限",false);addButton(card,grant,12);
        grant.setOnClickListener(v->requestRootAccess());
        Button enable=action("通过 Root 启用本应用无障碍",false);addButton(card,enable,10);
        enable.setOnClickListener(v->{
            if(!settings.rootAutoAccessibility()){showFeedback("请先开启 Root 增强和自动启用无障碍服务");return;}
            requestAccessibility(false);
        });
        text(page,"Root 由设备的权限管理器授权；未安装、拒绝授权或授权撤销时可继续手动授权。"+
                "Root 不绕过屏幕共享授权；文本填写仍需无障碍，触摸顺序可在辅助自动执行设置中选择。",12,palette.secondary,false);
        updateRootStatus();
    }
    private void requestRootAccess(){
        if(!BuildConfig.ROOT_SUPPORTED||rootRequestInFlight)return;
        rootRequestInFlight=true;
        if(rootStatus!=null)rootStatus.setText("正在检查 Root，请确认设备上的授权提示…");
        RootManager manager=RootManager.get();android.content.Context app=getApplicationContext();
        manager.background(()->{
            RootShell.Result result=manager.authorize();
            if(result.success()&&new Settings(app).rootAutoAccessibility())result=manager.enableAccessibility(app);
            final RootShell.Result outcome=result;
            runOnUiThread(()->{
                rootRequestInFlight=false;if(isDestroyed()||isFinishing())return;
                updateRootStatus();updateAccessibilityStatus();showFeedback(RootManager.message(outcome.status));
                if(outcome.success()&&settings.rootAutoAccessibility())waitForRootAccessibility(false,0);
            });
        });
    }
    private void updateRootStatus(){
        if(rootStatus==null)return;
        rootStatus.setText(!settings.rootEnabled()?"Root 增强已关闭":RootManager.get().authorized()?
                "Root 已授权 · 增强功能可用":"Root 未授权，请检查或申请权限");
    }
    private void buildAdvancedSettings(LinearLayout page){
        section(page,"高级设置","兼容与低频操作");
        LinearLayout card=card(page);
        text(card,"手动框选识别范围",16,palette.foreground,true);
        text(card,"仅作为自动识别失败时的兼容入口。需先开启悬浮助手；框选后在悬浮窗点击开始。",13,palette.secondary,false);
        Button select=action("进入手动框选",false);addButton(card,select,12);
        select.setOnClickListener(v->{
            if(!CaptureService.active){showFeedback("请先在首页开启悬浮助手");return;}
            startService(new Intent(this,CaptureService.class).setAction("MANUAL_SELECT"));moveTaskToBack(true);
        });
    }
    private void buildDedicatedPlaceholder(LinearLayout page){
        section(page,"某 APP 专用自动答题","功能预留");
        LinearLayout card=card(page);
        text(card,"尚未配置目标 APP",16,palette.foreground,true);
        text(card,"后续确定目标 APP 后，再接入专用识题、填写和切题流程。",13,palette.secondary,false);
    }
    private void buildAccount(LinearLayout page){
        GlassCard header=glassCard(page,24f);
        LinearLayout body=header.body();
        ImageView logo=new ImageView(this);
        logo.setImageResource(R.drawable.ic_app);
        body.addView(logo,new LinearLayout.LayoutParams(dp(52),dp(52)));
        text(body,"大学生小帮手",21,palette.foreground,true).setPadding(0,dp(12),0,0);
        text(body,"版本 "+BuildConfig.VERSION_NAME+"（"+BuildConfig.VERSION_CODE+"）",12,palette.secondary,false);
        text(body,BuildConfig.DEVELOPER_BUILD?"开发者版本 / Developer Build":"个人学习 · 独立思考",
                11,palette.accent,true).setPadding(0,dp(8),0,0);
        section(page,"我的","识别题目，查看答案");
        entry(page,"关于应用","版本、开源与协议入口",10);
        entry(page,"外观与主题","默认悬浮窗绿 · 7 套配色 · 玻璃与动效",11);
        entry(page,"赞助支持","自愿赞赏 · 感谢支持与陪伴",12);
    }
    private TextView sponsorBlessing;
    private void buildSponsor(LinearLayout page){
        section(page,"赞助支持","感谢你支持大学生小帮手");
        LinearLayout message=card(page);
        sponsorBlessing=text(message,"",17,palette.accent,true);
        text(message,"赞助完全自愿，不影响现有功能的使用。",12,palette.secondary,false);
        ImageView code=new ImageView(this);code.setImageResource(R.drawable.sponsor_code);
        code.setAdjustViewBounds(true);code.setScaleType(ImageView.ScaleType.FIT_CENTER);
        code.setContentDescription("张延济的微信赞赏码");
        LinearLayout.LayoutParams imageLayout=new LinearLayout.LayoutParams(-1,-2);
        imageLayout.topMargin=dp(16);page.addView(code,imageLayout);
        text(page,"可截图保存后，在微信中识别赞赏码。谢谢你的心意。",12,palette.secondary,false);
    }
    private void buildAbout(LinearLayout page){
        section(page,"关于大学生小帮手","版本与项目说明");
        LinearLayout info=card(page);
        text(info,getString(R.string.app_name),20,palette.foreground,true);
        text(info,"版本 "+BuildConfig.VERSION_NAME+"（"+BuildConfig.VERSION_CODE+"）",13,palette.secondary,false);
        if(BuildConfig.DEVELOPER_BUILD){
            LinearLayout developer=card(page);
            text(developer,"开发者版本 / Developer Build",16,palette.accent,true);
            text(developer,"用于验证自动识题、答案展示和实际 Token 用量。",13,palette.secondary,false);
            Button notes=action("查看开发版本说明",false);addButton(developer,notes,12);
            notes.setOnClickListener(v->showDeveloperNotes());
        }
        text(info,"通用自动答题已暂缓新增能力。指定 APP 专用入口目前仅为占位。",13,palette.secondary,false);
        section(page,"信息与许可","用途限制与数据处理说明");
        aboutEntry(page,"开源信息","Cenbyte / ScreenQA\nhttps://github.com/Cenbyte/ScreenQA\n查看作者、识别引擎与素材来源")
                .setOnClickListener(v->showLegalDocument("开源信息","OPEN_SOURCE_INFO.txt"));
        Button privacy=action("隐私政策",false);addButton(page,privacy,10);
        privacy.setOnClickListener(v->showLegalDocument("隐私政策","PRIVACY_POLICY.md"));
        Button agreement=action("用户协议",false);addButton(page,agreement,10);
        agreement.setOnClickListener(v->showLegalDocument("用户协议","USER_AGREEMENT.md"));
        Button notice=action("查看用途声明",false);addButton(page,notice,10);
        notice.setOnClickListener(v->showThemedInfo("用途声明",UsageDeclaration.BODY,false));
        aboutEntry(page,"第三方许可与素材","Google ML Kit · Liquid-Glass-Android · Material Design Icons\n查看来源、作者与适用条款")
                .setOnClickListener(v->showLegalDocument("第三方许可与素材","OPEN_SOURCE_INFO.txt"));
        Button glassLicense=action("Liquid-Glass-Android · MIT 许可全文",false);addButton(page,glassLicense,10);
        glassLicense.setOnClickListener(v->showLegalDocument("MIT 许可","LICENSE_LIQUID_GLASS.txt"));
        Button apacheLicense=action("图标与 Kotlin · Apache-2.0 许可全文",false);addButton(page,apacheLicense,10);
        apacheLicense.setOnClickListener(v->showLegalDocument("Apache-2.0 许可","LICENSE_APACHE_2_0.txt"));
        text(page,"由 Codex 辅助开发",12,palette.secondary,false).setPadding(dp(4),dp(14),dp(4),dp(12));
    }
    private LinearLayout aboutEntry(LinearLayout page,String title,String detail){
        LinearLayout item=card(page);text(item,title,15,palette.foreground,true);
        text(item,detail,12,palette.secondary,false);
        item.setBackground(ripple(palette.surface,ThemePalette.alpha(palette.accent,0.16f),dp(20)));
        item.setOnClickListener(v->showThemedInfo(title,detail,false));
        return item;
    }
    private void buildTokenStats(LinearLayout page){
        section(page,"Token 用量","按 API 返回的实际 usage 统计");
        LinearLayout card=card(page);
        tokenStats=text(card,"读取中…",14,palette.foreground,false);
        tokenStats.setTextIsSelectable(true);
        Button refresh=action("刷新统计",false);addButton(card,refresh,14);
        refresh.setOnClickListener(v->renderTokenStats());
        text(page,"统计包括连接测试；缺少 usage 时记录为未知，不估算 Token。",12,palette.secondary,false);
        if(!BuildConfig.DEVELOPER_BUILD)return;
        TokenUsageTracker tracker=new TokenUsageTracker(this);
        text(card,"自设 API 单价（每百万 Token，同一货币单位）",13,palette.foreground,true);
        EditText hit=priceField(card,"缓存命中输入单价",tracker.rateValue("hit"));
        EditText miss=priceField(card,"缓存未命中输入单价",tracker.rateValue("miss"));
        EditText output=priceField(card,"输出单价",tracker.rateValue("output"));
        Button prices=action("保存单价并计算预算",false);addButton(card,prices,10);
        prices.setOnClickListener(v->{try{tracker.saveRates(hit.getText().toString(),miss.getText().toString(),output.getText().toString());renderTokenStats();}
            catch(Exception e){showFeedback("请输入非负单价（最多8位小数）");}});
        Button export=action("导出分类 Token CSV",false);addButton(card,export,10);
        export.setOnClickListener(v->startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .setType("text/csv").addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_TITLE,"screenqa-dev-token-"+java.time.LocalDate.now()+".csv"),EXPORT_USAGE));
        text(page,"新增分类从本版起记录，历史总量仍保留。明细保留最近500次，分类累计不随明细淘汰；每日保留31天。"+
                "有效答案指模型结果可用，不代表点击成功。未知用量不按0计费；缓存拆分未知时不计入单价预算。"+
                "预算仅使用自设单价；CSV 不含题目、答案或密钥。",12,palette.secondary,false);
        text(page,"CSV 带脱敏题干 SHA-256 标识与稳定识别周期内的请求次数，可汇总同题成本；未定位成功时题标识为空。"+
                "混合模型请按 CSV 的模型分别计价，页面预算使用统一自设单价。",12,palette.secondary,false);
        text(page,"分类说明：connection_test=连接测试，manual_answer=手动答题，screen_detect=AI定位并解题，local_solve=本地定位后解题；"+
                "choice=选择，true_false=判断，fill_blank=填空，short_answer=简答，unknown=未知。"+
                "answered=答案可用，incomplete=条件不完整，no_question=无题，test_ok=连接成功，"+
                "cancelled=取消，timeout=超时，parse_error=格式异常，truncated=输出截断，http_error=接口失败，"+
                "network_error=网络错误，missing_usage=响应缺少有效用量。",12,palette.secondary,false);
    }
    private EditText priceField(LinearLayout card,String label,String value){
        text(card,label,12,palette.secondary,false);
        EditText field=field("",value,InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        card.addView(field,new LinearLayout.LayoutParams(-1,dp(46)));
        return field;
    }
    private void renderTokenStats(){
        if(tokenStats==null)return;
        TokenUsageTracker.Snapshot s=new TokenUsageTracker(this).snapshot();
        tokenStats.setText(getString(R.string.token_stats,
                s.lastPrompt,s.lastCompletion,s.lastTotal,s.todayTokens,s.todayRequests,
                s.historyTokens,s.historyRequests,s.averageTokens(),s.missingUsageResponses)+
                (BuildConfig.DEVELOPER_BUILD?"\n\n"+new TokenUsageTracker(this).detailedReport():""));
    }
    private void showDeveloperNotes(){
        try(java.io.InputStream input=getAssets().open("DEVELOPER_BUILD.md")){
            java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();
            byte[] buffer=new byte[4096];int count;
            while((count=input.read(buffer))!=-1)output.write(buffer,0,count);
            showThemedInfo("开发版本说明",output.toString("UTF-8"),false);
        }catch(java.io.IOException e){showFeedback("无法读取开发版本说明");}
    }
    private void buildPermissionPage(LinearLayout page){
        section(page,"权限说明","查看状态并前往系统设置");
        LinearLayout accessibility=card(page);
        accessibilityStatus=statusLine(accessibility,R.drawable.ic_settings,"无障碍服务","检查中");
        text(accessibility,"用于选择答案、填写文本和切换下一题。关闭自动执行后仍可只查看答案。",12,palette.secondary,false);
        Button enable=action("开启无障碍服务",false);addButton(accessibility,enable,14);
        enable.setOnClickListener(v->requestAccessibility(false));
        LinearLayout overlay=card(page);
        overlayPermissionStatus=statusLine(overlay,R.drawable.ic_auto,"悬浮窗","检查中");
        text(overlay,"用于在题目页面显示控制按钮和答案。",12,palette.secondary,false);
        Button overlayButton=action("开启悬浮窗权限",false);addButton(overlay,overlayButton,14);
        overlayButton.setOnClickListener(v->openOverlayPermission());
        LinearLayout notification=card(page);
        notificationStatus=statusLine(notification,R.drawable.ic_article,"通知","检查中");
        text(notification,"用于显示屏幕共享正在运行的前台服务。",12,palette.secondary,false);
        Button notify=action("申请通知权限",false);addButton(notification,notify,14);
        notify.setOnClickListener(v->{if(Build.VERSION.SDK_INT>=33)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},41);});
        LinearLayout capture=card(page);
        text(capture,"屏幕共享",16,palette.foreground,true);
        text(capture,"开始识题时由系统逐次授权。应用只在运行期间读取屏幕。",12,palette.secondary,false);
    }
    private void openOverlayPermission(){
        startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:"+getPackageName())));
    }
    private void buildAutoSettings(LinearLayout page){
        section(page,"辅助自动执行","可选功能；核心流程为自动识题并显示答案");
        LinearLayout auto=card(page);
        final GoldSwitch[] children=new GoldSwitch[4];
        settingRow(auto,"自动执行","关闭后仍会识题和显示答案",settings.autoSelect(),value->{
            settings.setAutoSelect(value);
            for(GoldSwitch child:children)if(child!=null)child.setEnabled(value);
            refreshAutoSettings();updateLiveStatus();
            if(value&&!TouchExecutor.available(this))requestAccessibility(false);
        });
        settingRow(auto,"自动下一题","执行答案后尝试切题 · 默认关闭",settings.autoNext(),
                value->{settings.setAutoNext(value);refreshAutoSettings();});
        divider(auto);
        String[] types={"choice","true_false","fill_blank","short_answer"};
        String[] titles={"选择题","判断题","填空题","简答题"};
        String[] subtitles={"自动选择选项","自动选择对错","自动填写单空和多空","自动填写主观文本 · 默认关闭"};
        for(int i=0;i<4;i++){
            String type=types[i];
            children[i]=settingRow(auto,titles[i],subtitles[i],settings.autoExecute(type),value->{
                settings.setAutoExecute(type,value);refreshAutoSettings();
            });
            children[i].setEnabled(settings.autoSelect());
        }
        if(BuildConfig.ROOT_SUPPORTED){
            Button priority=action(getString(R.string.overlay_touch_priority,settings.touchPriority().label),false);
            addButton(page,priority,12);
            priority.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("辅助执行顺序")
                    .setSingleChoiceItems(new String[]{"自动回退：节点 → Root → 手势","Root 优先：Root → 节点 → 手势",
                            "无障碍优先：节点 → 手势 → Root"},settings.touchPriority().ordinal(),(dialog,index)->{
                        settings.setTouchPriority(TouchPriority.fromStored(index));refreshAutoSettings();
                        priority.setText(getString(R.string.overlay_touch_priority,settings.touchPriority().label));dialog.dismiss();
                    }).setNegativeButton("关闭",null).show());
        }
        TextView note=text(page,"关闭某题型的自动执行后仍显示答案；需自行切换下一题。",12,palette.secondary,false);
        note.setPadding(dp(4),dp(5),dp(4),0);
    }
    private void showChoices(String title,String[] labels,int checked,IntConsumer picked){
        new AlertDialog.Builder(this).setTitle(title)
                .setSingleChoiceItems(labels,checked,(dialog,index)->{picked.accept(index);dialog.dismiss();})
                .setNegativeButton("取消",null).show();
    }
    private void buildAiSettings(LinearLayout page){
        section(page,"模型与接口","预设模型、自定义模型与任意 OpenAI 兼容接口");
        LinearLayout connection=card(page);
        TextView privacy=text(connection,"Key 加密保存在本机。题目文字用于获取答案；不会上传截图。",12,palette.secondary,false);
        privacy.setPadding(0,dp(2),0,dp(12));
        String savedKey="";try{savedKey=settings.key();}catch(Exception ignored){}
        key=new ClipboardEditText(this);
        key.setSingleLine(true);key.setText(savedKey);key.setHint("输入 DeepSeek API Key");
        key.setTextColor(palette.foreground);key.setHintTextColor(palette.secondary);key.setTextSize(15);
        key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        key.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                |android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        key.setSaveEnabled(false);key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        key.setPadding(dp(14),dp(10),dp(14),dp(10));
        key.setBackground(shape(palette.background,palette.border,dp(12),dp(1)));
        key.setMinimumHeight(dp(52));
        connection.addView(key,new LinearLayout.LayoutParams(-1,-2));

        final Button modelButton=action("",false);
        final Button endpointButton=action("",false);
        final GoldSwitch thinking=new GoldSwitch(this);
        thinking.setPalette(palette);
        thinking.setReduceMotion(settings.reduceMotion());
        thinking.setCheckedSilently(settings.thinking());

        apiSelection=new ApiSettingsSelection(settings.modelId(),settings.endpoint());
        customModelField=field("自定义模型 ID",apiSelection.customModel?settings.modelId():"",InputType.TYPE_CLASS_TEXT);
        customEndpointField=field("自定义接口地址（https://…/chat/completions）",
                apiSelection.customEndpoint?settings.endpoint():"",
                InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);

        Runnable sync=()->{
            modelButton.setText("模型 · "+(apiSelection.customModel?"自定义模型":ModelCatalog.modelLabel(settings.modelId())));
            endpointButton.setText("接口 · "+(apiSelection.customEndpoint?"自定义接口":ModelCatalog.endpointLabel(settings.endpoint())));
            customModelField.setVisibility(apiSelection.customModel?View.VISIBLE:View.GONE);
            customEndpointField.setVisibility(apiSelection.customEndpoint?View.VISIBLE:View.GONE);
        };
        sync.run();

        addButton(connection,modelButton,14);
        connection.addView(customModelField,new LinearLayout.LayoutParams(-1,dp(50)));
        addButton(connection,endpointButton,10);
        connection.addView(customEndpointField,new LinearLayout.LayoutParams(-1,dp(50)));

        LinearLayout thinkingRow=new LinearLayout(this);
        thinkingRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams thinkingLp=new LinearLayout.LayoutParams(-1,-2);thinkingLp.topMargin=dp(14);
        connection.addView(thinkingRow,thinkingLp);
        LinearLayout thinkingLabels=new LinearLayout(this);thinkingLabels.setOrientation(LinearLayout.VERTICAL);
        thinkingRow.addView(thinkingLabels,new LinearLayout.LayoutParams(0,-2,1));
        text(thinkingLabels,"思考模式",15,palette.foreground,true);
        text(thinkingLabels,"deepseek-reasoner 一类模型需要开启；普通模型建议关闭",12,palette.secondary,false);
        thinkingRow.addView(thinking,new LinearLayout.LayoutParams(-2,-2));
        thinking.setListener(value->{settings.setThinking(value);updateLiveStatus();});

        modelButton.setOnClickListener(v->{
            String[] labels=new String[ModelCatalog.MODELS.length];
            int checked=apiSelection.customModel?ModelCatalog.MODELS.length-1:0;
            for(int i=0;i<labels.length;i++){
                ModelCatalog.Model model=ModelCatalog.MODELS[i];
                labels[i]=model.label+" · "+model.note;
                if(!apiSelection.customModel&&!model.id.isEmpty()&&model.id.equalsIgnoreCase(settings.modelId()))checked=i;
            }
            showChoices("选择模型",labels,checked,index->{
                ModelCatalog.Model model=ModelCatalog.MODELS[index];
                apiSelection.selectModel(model.id);
                if(!model.id.isEmpty())settings.setModelId(model.id);
                if(model.thinking){settings.setThinking(true);thinking.setCheckedSilently(true);}
                sync.run();
                updateLiveStatus();
            });
        });
        endpointButton.setOnClickListener(v->{
            String[] labels=new String[ModelCatalog.ENDPOINTS.length];
            int checked=apiSelection.customEndpoint?ModelCatalog.ENDPOINTS.length-1:0;
            for(int i=0;i<labels.length;i++){
                ModelCatalog.Endpoint endpoint=ModelCatalog.ENDPOINTS[i];
                labels[i]=endpoint.label+" · "+endpoint.note;
                if(!apiSelection.customEndpoint&&!endpoint.url.isEmpty()&&endpoint.url.equalsIgnoreCase(settings.endpoint()))checked=i;
            }
            showChoices("选择接口地址",labels,checked,index->{
                ModelCatalog.Endpoint endpoint=ModelCatalog.ENDPOINTS[index];
                apiSelection.selectEndpoint(endpoint.url);
                if(!endpoint.url.isEmpty())settings.setEndpoint(endpoint.url);
                sync.run();
            });
        });

        LinearLayout actions=new LinearLayout(this);
        LinearLayout.LayoutParams actionsLp=new LinearLayout.LayoutParams(-1,-2);actionsLp.topMargin=dp(12);
        connection.addView(actions,actionsLp);
        Button save=action("保存配置",true);
        actions.addView(save,new LinearLayout.LayoutParams(0,-2,1));
        save.setOnClickListener(v->{if(save()){sync.run();updateLiveStatus();showFeedback("Key 与模型设置已保存");}});
        test=action("测试连接",false);
        LinearLayout.LayoutParams testLp=new LinearLayout.LayoutParams(0,-2,1);testLp.leftMargin=dp(10);
        actions.addView(test,testLp);
        test.setOnClickListener(v->testConnection());
        text(page,"自定义接口仅接受 https 地址，且不能带账号信息、查询参数或片段。模型 ID 由接口决定是否可用。",
                12,palette.secondary,false);
    }
    private void buildRecognitionSettings(LinearLayout page){
        section(page,"识别方式","默认使用智能混合策略");
        LinearLayout methodCard=card(page);
        Button expand=action("调整识别策略",false);addButton(methodCard,expand,0);
        RadioGroup methods=new RadioGroup(this);methods.setOrientation(RadioGroup.VERTICAL);methods.setVisibility(View.GONE);
        methodCard.addView(methods);
        String[] names={"智能混合（默认）","无障碍优先","视觉识别优先"};
        for(int i=0;i<names.length;i++){
            RadioButton option=new RadioButton(this);option.setId(200+i);option.setText(names[i]);
            option.setTextColor(palette.foreground);option.setTextSize(14);
            option.setButtonTintList(ColorStateList.valueOf(palette.accent));
            option.setPadding(dp(6),dp(6),0,dp(6));
            methods.addView(option);
        }
        methods.check(200+settings.strategy().ordinal());
        methods.setOnCheckedChangeListener((group,id)->{
            settings.setStrategy(AutoAnswerStrategy.fromStored(id-200));
            if(CaptureService.active)startService(new Intent(this,CaptureService.class).setAction("STRATEGY"));
        });
        expand.setOnClickListener(v->{
            boolean opening=methods.getVisibility()!=View.VISIBLE;
            methods.setVisibility(opening?View.VISIBLE:View.GONE);
            expand.setText(opening?"收起识别策略":"调整识别策略");
        });
        TextView note=text(page,"智能混合为默认策略。调整后正在运行的悬浮助手会同步更新。",12,palette.secondary,false);
        note.setPadding(dp(4),dp(5),dp(4),0);
    }
    private void buildLogs(LinearLayout page){
        section(page,"诊断日志","控制记录状态，查看或导出复现信息");
        text(page,"日志仅保存在本机，可能包含题目、答案片段和运行状态；可随时停止或清除。",12,palette.secondary,false);
        LinearLayout state=card(page);
        text(state,"记录状态",15,palette.foreground,true);
        logDetail=text(state,"",13,palette.secondary,false);logDetail.setPadding(0,dp(8),0,0);
        logButton=action("",true);addButton(state,logButton,15);
        logButton.setOnClickListener(v->{
            boolean next=!QaLog.isRecording(this);QaLog.setRecording(this,next);updateLiveStatus();
            showFeedback(next?"已开始记录，新日志文件已创建":"已停止记录，已有日志仍可查看");
        });
        section(page,"日志文件","仅保存在此设备的应用内部存储");
        LinearLayout files=card(page);
        Button view=action("查看日志",false);addButton(files,view,0);view.setOnClickListener(v->showLogList());
        Button export=action("导出最新日志",false);addButton(files,export,10);
        export.setOnClickListener(v->{
            Intent saveLog=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("text/plain").putExtra(Intent.EXTRA_TITLE,
                            QaLog.latestName().isEmpty()?"qa_log.log":QaLog.latestName());
            startActivityForResult(saveLog,EXPORT_LOG);
        });
        Button clear=action("清除旧日志",false);addButton(files,clear,10);
        clear.setTextColor(palette.secondary);
        clear.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("清除日志？")
                .setMessage("将删除本机已保存的 Q&A 日志。")
                .setNegativeButton("取消",null)
                .setPositiveButton("清除",(dialog,which)->QaLog.clear(count->runOnUiThread(()->{
                    QaLog.start(this);updateLiveStatus();
                    showFeedback("已清除 "+count+" 个日志文件");
                }))).show());
        TextView note=text(page,"停止记录后，不再向 Q&A 文件写入新事件；已有文件仍可查看和导出。再次开始会创建新的日志文件。",
                12,palette.secondary,false);
        note.setPadding(dp(4),dp(5),dp(4),0);
    }
    private void showLogList(){
        QaLog.list(logNames->runOnUiThread(()->{
            if(isDestroyed())return;
            if(logNames.length==0){showFeedback("暂无日志文件");return;}
            new AlertDialog.Builder(this).setTitle("Q&A 日志文件").setItems(logNames,(dialog,which)->
                    QaLog.read(logNames[which],value->runOnUiThread(()->{
                        if(!isDestroyed())new AlertDialog.Builder(this).setTitle(logNames[which])
                                .setMessage(value).setPositiveButton("关闭",null).show();
                    }))).setNegativeButton("关闭",null).show();
        }));
    }
    private View swatch(int color){
        View dot=new View(this);
        GradientDrawable bg=new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(color);
        bg.setStroke(Math.max(1,dp(1)),ThemePalette.alpha(palette.foreground,0.20f));
        dot.setBackground(bg);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(20),dp(20));
        lp.rightMargin=dp(7);
        dot.setLayoutParams(lp);
        return dot;
    }
    private View themeRow(LinearLayout page,ThemePalette option){
        boolean active=option.id.equals(palette.id);
        LinearLayout item=card(page);
        item.setMinimumHeight(dp(78));
        item.setBackground(ripple(palette.surface,ThemePalette.alpha(palette.accent,0.16f),dp(20)));
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);item.addView(row);
        LinearLayout swatches=new LinearLayout(this);swatches.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(swatches,new LinearLayout.LayoutParams(-2,-2));
        swatches.addView(swatch(option.background));
        swatches.addView(swatch(option.accent));
        swatches.addView(swatch(option.surfaceAlt));
        swatches.addView(swatch(option.heroStart));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelLp=new LinearLayout.LayoutParams(0,-2,1);labelLp.leftMargin=dp(12);
        row.addView(labels,labelLp);
        text(labels,option.name,16,palette.foreground,true);
        text(labels,(option.dark?"深色主题 · ":"浅色主题 · ")+option.id,12,palette.secondary,false);
        text(row,active?"✓":"",20,active?palette.accent:palette.secondary,true);
        item.setContentDescription(option.name+"主题"+(active?"，当前使用":""));
        item.setOnClickListener(v->{
            if(active)return;
            settings.setThemeId(option.id);
            getWindow().getDecorView().post(this::rebuildForTheme);
        });
        return item;
    }
    private void buildAppearanceSettings(LinearLayout page){
        section(page,"配色主题","默认悬浮窗绿，与悬浮窗配色一致；切换后界面立即重建并全局生效");
        for(ThemePalette option:ThemePalette.ALL)themeRow(page,option);
        section(page,"动效与玻璃","非线性动画、液态玻璃与省电选项");
        LinearLayout motion=card(page);
        settingRow(motion,"液态玻璃","实时采样背后页面：轻模糊、边缘折射与高光",settings.glassDock(),
                value->{settings.setGlassDock(value);getWindow().getDecorView().post(this::rebuildForTheme);});
        settingRow(motion,"减弱动效","停用极光流动、入场与按压动画",settings.reduceMotion(),
                value->{settings.setReduceMotion(value);getWindow().getDecorView().post(this::rebuildForTheme);});
        text(page,"底部导航栏会透出滚动中的页面，经过胶囊的内容带有轻微模糊、折射与边缘高光；不支持该效果的机型自动降级为手绘玻璃。关闭后改用不透明磨砂底色。",12,palette.secondary,false);
    }
    private void showFeedback(String message){
        if(feedback==null)return;
        feedback.setText(message);feedback.setVisibility(View.VISIBLE);feedback.setAlpha(0f);
        feedback.animate().cancel();
        feedback.animate().alpha(1f).setDuration(130).setInterpolator(Motion.DECELERATE).start();
        int token=++feedbackToken;
        feedback.postDelayed(()->{
            if(token==feedbackToken&&!isDestroyed())feedback.animate().alpha(0f).setDuration(170)
                    .withEndAction(()->{if(token==feedbackToken)feedback.setVisibility(View.GONE);}).start();
        },5000);
    }
    private void updateLiveStatus(){
        if(captureStatus!=null)captureStatus.setText(CaptureService.active?"● 悬浮助手运行中":"○ 悬浮助手尚未开启");
        if(captureButton!=null)captureButton.setText(CaptureService.active?"停止悬浮助手":"开启悬浮助手");
        updateAccessibilityStatus();
        updateRootStatus();
        if(logDetail!=null||logButton!=null){
            boolean recording=QaLog.isRecording(this);
            if(logDetail!=null)logDetail.setText(recording?"正在写入诊断事件":"已暂停文件记录，历史日志仍可用");
            if(logButton!=null)logButton.setText(recording?"停止记录":"开始记录");
        }
        if(overlayPermissionStatus!=null)overlayPermissionStatus.setText(
                android.provider.Settings.canDrawOverlays(this)?"已授权":"未授权");
        if(overlaySettingsStatus!=null)overlaySettingsStatus.setText(
                android.provider.Settings.canDrawOverlays(this)?"已授权":"未授权");
        if(notificationStatus!=null)notificationStatus.setText(Build.VERSION.SDK_INT<33||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED?
                "已授权 / 无需单独授权":"未授权");
    }
    private void refreshAutoSettings() {
        if(CaptureService.active)startService(new Intent(this,CaptureService.class).setAction("AUTO_SETTINGS"));
    }
    private boolean save() {
        try {
            String model=settings.modelId(),endpoint=settings.endpoint();
            if(apiSelection!=null){
                model=apiSelection.modelValue(customModelField.getText().toString(),model);
                endpoint=apiSelection.endpointValue(customEndpointField.getText().toString(),endpoint);
            }
            settings.save(key.getText().toString());
            settings.setModelId(model);
            settings.setEndpoint(endpoint);
            return true;
        } catch (IllegalArgumentException e) { showFeedback(e.getMessage()); return false;
        } catch (Exception e) { showFeedback("无法保存密钥，请重新填写后重试"); return false; }
    }
    private void testConnection() {
        if (!save()) return;
        test.setEnabled(false); showFeedback("正在测试连接…（会发送一次简短请求）");
        final String apiKey=key.getText().toString().trim();
        testing = new ApiRequest(this); final ApiRequest request = testing;
        background().execute(() -> {
            String result;
            try { request.run(apiKey,"",true); result="DeepSeek 连接成功。"; }
            catch (Exception e) { result=request.message(e); }
            final String message=result;
            runOnUiThread(() -> { if (!isDestroyed()) { showFeedback(message); test.setEnabled(true); } });
        });
    }
    private void startCapture() {
        if(key!=null){if(!save()){openDetail(2,true);return;}}
        else try {if(settings.key().isEmpty()){showFeedback("请先配置 DeepSeek API Key");openDetail(2,true);return;}}
             catch(Exception e){showFeedback("读取 Key 失败，请重新填写");openDetail(2,true);return;}
        if(settings.autoSelect()&&!TouchExecutor.available(this)) {
            requestAccessibility(true);return;
        }
        if (CaptureService.active) { showFeedback("悬浮窗已开启。更新 Key 后请先停止，再重新开启。"); return; }
        if (!android.provider.Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this).setTitle("允许悬浮窗")
                    .setMessage("请在下一页允许“显示在其他应用上层”，返回后再次点击开启。")
                    .setPositiveButton("去设置",(d,w) -> startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))))
                    .setNegativeButton("取消",null).show(); return;
        }
        if (Build.VERSION.SDK_INT>=33 && !notificationAsked && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationAsked=true;notificationForCapture=true;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},41);
            return;
        }
        showThemedInfo("开始屏幕共享",
                "默认自动模式会读取屏幕文字，将需要的文字、行号与相对位置发送到 DeepSeek，用于定位题目和解答；手动选区只发送选区文字。请切到题目页面再开始，避免发送无关内容。可随时暂停或关闭。",false,() -> {
                    MediaProjectionManager manager=getSystemService(MediaProjectionManager.class);
                    Intent intent=Build.VERSION.SDK_INT>=34 ? manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()) : manager.createScreenCaptureIntent();
                    startActivityForResult(intent,CAPTURE);
                });
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if(!uiReady)return;
        if(request==EXPORT_USAGE){
            if(!BuildConfig.DEVELOPER_BUILD)return;
            if(result==RESULT_OK&&data!=null&&data.getData()!=null){android.net.Uri uri=data.getData();
                background().execute(()->{
                    try(OutputStream target=getContentResolver().openOutputStream(uri)){
                        if(target==null)throw new java.io.IOException();
                        target.write(("\uFEFF"+new TokenUsageTracker(this).exportCsv()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        runOnUiThread(()->showFeedback("Token 分类 CSV 已导出"));
                    }catch(Exception e){runOnUiThread(()->showFeedback("Token CSV 导出失败，请重试"));}
                });
            }return;
        }
        if(request==ACCESSIBILITY) {
            boolean resume=resumeCaptureAfterAccessibility;resumeCaptureAfterAccessibility=false;
            checkAccessibilityReturn(resume,0);return;
        }
        if(request==EXPORT_LOG) {
            if(!BuildConfig.DIAGNOSTICS_ENABLED)return;
            if(result==RESULT_OK&&data!=null&&data.getData()!=null)background().execute(() -> {
                try {
                    OutputStream target=getContentResolver().openOutputStream(data.getData());
                    if(target==null)throw new java.io.IOException("No output stream");
                    QaLog.exportLatest(target,okay -> runOnUiThread(() ->
                            showFeedback(okay?"最新日志已导出":"导出日志失败，请重试")));
                } catch(Exception e){runOnUiThread(() -> showFeedback("导出日志失败，请重试"));}
            });
            return;
        }
        if(request==CAPTURE && result==RESULT_OK && data!=null) {
            Intent intent=new Intent(this,CaptureService.class).putExtra("code",result).putExtra("grant",data);
            startForegroundService(intent); moveTaskToBack(true);
        } else if(request==CAPTURE) showFeedback("未授权屏幕共享，可以稍后重新开启。");
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if(!uiReady)return;
        if(request==41){updateLiveStatus();if(notificationForCapture){notificationForCapture=false;startCapture();}}
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("resumeCaptureAfterAccessibility",resumeCaptureAfterAccessibility);
        state.putBoolean("notificationAsked",notificationAsked);
        state.putInt("selectedTab",selectedTab);state.putInt("selectedDetail",selectedDetail);
        super.onSaveInstanceState(state);
    }
    @Override protected void onStart() {
        super.onStart();
        if(!uiReady)return;
        if(aurora!=null)aurora.start();
        startLiquidDriver();
    }
    @Override protected void onResume() {
        super.onResume();if(!uiReady)return;
        CaptureService.observe(this);updateLiveStatus();
        if(aurora!=null)aurora.start();
        startLiquidDriver();
        if(selectedDetail==9)renderTokenStats();
        scheduleBackdrop();
    }
    @Override public void onAnswerChanged(){
        if(!uiReady)return;
        if(Looper.myLooper()==Looper.getMainLooper())updateLiveStatus();
        else runOnUiThread(this::updateLiveStatus);
    }
    @Override protected void onPause(){
        CaptureService.observe(null);
        if(aurora!=null)aurora.stop();
        stopLiquidDriver();
        super.onPause();
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);setIntent(intent);
        if(!uiReady)return;
        if(intent.getBooleanExtra("request_accessibility",false)) {
            intent.removeExtra("request_accessibility");requestAccessibility(false);
        }
    }
    private void updateAccessibilityStatus() {
        if(accessibilityStatus==null)return;
        accessibilityStatus.setText(ScreenQaAccessibilityService.active!=null?"无障碍服务：已连接":
                AccessibilityPermission.enabled(this)?"无障碍服务：已启用，等待系统连接":
                TouchExecutor.rootAvailable(this)?"无障碍未连接 · 可用 Root 触摸，填写仍需无障碍":"无障碍未启用 · 自动触摸不可用");
    }
    private void requestAccessibility(boolean resumeCapture) {
        if(BuildConfig.ROOT_SUPPORTED&&settings.rootAutoAccessibility()&&ScreenQaAccessibilityService.active==null){
            if(rootAccessibilityPending)return;
            rootAccessibilityPending=true;showFeedback("正在通过 Root 启用无障碍…");
            android.content.Context app=getApplicationContext();
            RootManager.get().background(()->{
                RootShell.Result result=RootManager.get().enableAccessibility(app);
                runOnUiThread(()->{
                    rootAccessibilityPending=false;if(isDestroyed()||isFinishing())return;
                    updateRootStatus();
                    if(result.success())waitForRootAccessibility(resumeCapture,0);
                    else {showFeedback(RootManager.message(result.status));requestManualAccessibility(resumeCapture);}
                });
            });return;
        }
        requestManualAccessibility(resumeCapture);
    }
    private void waitForRootAccessibility(boolean resume,int attempt){
        if(isDestroyed()||isFinishing())return;
        updateAccessibilityStatus();
        if(ScreenQaAccessibilityService.active!=null){
            refreshAutoSettings();showFeedback("无障碍服务已连接。");if(resume)startCapture();return;
        }
        if(attempt<10){contentHost.postDelayed(()->waitForRootAccessibility(resume,attempt+1),200);return;}
        showFeedback("系统尚未连接无障碍服务，可使用原有手动授权入口");
        requestManualAccessibility(resume);
    }
    private void requestManualAccessibility(boolean resumeCapture) {
        if(accessibilityDialog!=null&&accessibilityDialog.isShowing())return;
        if(ScreenQaAccessibilityService.active!=null) {
            updateAccessibilityStatus();if(resumeCapture)startCapture();return;
        }
        QaLog.event("ACCESSIBILITY_PERMISSION prompt resume_capture="+resumeCapture+
                " enabled="+AccessibilityPermission.enabled(this));
        accessibilityDialog=new AlertDialog.Builder(this).setTitle("启用大学生小帮手学习辅助")
                .setMessage("自动选择、填写答案和切换下一题需要无障碍服务。点击去启用后，请在系统页面开启“"+
                        getString(R.string.accessibility_service_name)+"”，再返回本应用。若已经启用但未连接，请关闭后重新开启。")
                .setPositiveButton("去启用",(d,w) -> {
                    resumeCaptureAfterAccessibility=resumeCapture;
                    if(!AccessibilityPermission.open(this,ACCESSIBILITY)) {
                        resumeCaptureAfterAccessibility=false;
                        showFeedback("无法打开系统授权页，请在设置 → 无障碍中启用"+
                                getString(R.string.accessibility_service_name)+"。");
                    }
                }).setNegativeButton("暂不启用",(d,w) -> {
                    resumeCaptureAfterAccessibility=false;
                    showFeedback("尚未启用无障碍。可关闭自动执行总开关，仅识题和显示答案。");
                }).create();
        accessibilityDialog.show();
    }
    private void checkAccessibilityReturn(boolean resume,int attempt) {
        if(isDestroyed()||isFinishing())return;
        updateAccessibilityStatus();
        if(ScreenQaAccessibilityService.active!=null) {
            QaLog.event("ACCESSIBILITY_PERMISSION return=connected");refreshAutoSettings();
            showFeedback("无障碍服务已连接。");if(resume)startCapture();return;
        }
        if(AccessibilityPermission.enabled(this)&&attempt<10) {
            contentHost.postDelayed(() -> checkAccessibilityReturn(resume,attempt+1),200);return;
        }
        QaLog.event("ACCESSIBILITY_PERMISSION return="+(AccessibilityPermission.enabled(this)?"enabled_not_connected":"not_granted"));
        showFeedback("无障碍服务尚未连接。启用后可再次点击开启悬浮窗。");
    }
    @Override public void onBackPressed() {
        if(selectedDetail>=0){showTab(selectedTab,true);return;}
        if(selectedTab!=0){showTab(0,true);return;}
        super.onBackPressed();
    }
    @Override protected void onDestroy() {
        if (testing!=null) testing.cancel();
        if(executor!=null)executor.shutdownNow();
        backdrop.release();
        stopLiquidDriver();
        if(aurora!=null)aurora.stop();
        super.onDestroy();
    }
    private ExecutorService background(){if(executor==null)executor=Executors.newSingleThreadExecutor();return executor;}
}
