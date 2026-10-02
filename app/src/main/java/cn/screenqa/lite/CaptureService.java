package cn.screenqa.lite;

import android.app.*;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CaptureService extends Service {
    static volatile boolean active;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService network=Executors.newSingleThreadExecutor();
    private final ExecutorService imaging=Executors.newSingleThreadExecutor();
    private final QuestionTracker tracker=new QuestionTracker();
    private WindowManager windows;
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private TextRecognizer recognizer;
    private LinearLayout panel;
    private ScrollView answerBox;
    private TextView status,answer,bubbleText,popupText,popupTitle;
    interface UiObserver { void onAnswerChanged(); }
    private static java.lang.ref.WeakReference<UiObserver> uiObserver=new java.lang.ref.WeakReference<>(null);
    static volatile String currentAnswer="";
    static void observe(UiObserver observer){uiObserver=new java.lang.ref.WeakReference<>(observer);}
    private void publishAnswer(String value){
        if(currentAnswer.equals(value))return;
        currentAnswer=value;
        UiObserver observer=uiObserver.get();if(observer!=null)observer.onAnswerChanged();
    }
    private Button inlineCopy;
    private ImageButton toggle,autoButton,fold;
    private ImageView bubbleIcon;
    private LoadingRingView bubbleRing;
    private FrameLayout bubble;
    private LinearLayout answerPopup;
    private WindowManager.LayoutParams bubbleParams,popupParams;
    private WindowManager.LayoutParams panelParams;
    private RegionSelector selector;
    private QuestionOutline outline;
    private WindowManager.LayoutParams outlineParams;
    private ScreenDocument latestDocument;
    private ScreenDocument latestOcrDocument;
    private long latestOcrAt,nodesScanningSince=-1;
    private ScreenDocument navigationFrame;
    private ScreenDocument.Line navigationTarget;
    private int navigationEpoch,navigationGeneration;
    private long navigationCreated;
    private String navigationClickSignature;
    private QuestionDetection lastDetection;
    private Rect region;
    private ApiRequest request;
    private String apiKey;
    private volatile boolean paused=true,destroyed,autoSelectEnabled;
    private boolean ocrBusy,collapsed=true,autoMode=true,minimized,tapInFlight,textInputInFlight;
    private AutoAnswerStrategy autoAnswerStrategy=AutoAnswerStrategy.HYBRID;
    private PendingTap pendingTap;
    private AnswerRetry answerRetry;
    private PendingTap rootTapPending;
    private VisionTapFallback visionTapFallback;
    private String fullAnswer="",shortAnswer="",copyAnswer="";
    private static final long MIN_ANSWER_VISIBLE_MS=4500;
    private enum AutoState { IDLE, DETECTING, REQUESTING_AI, RESOLVING_TARGET,
        CLICKING_ANSWER, FILLING_TEXT, WAITING_PAGE_CHANGE, FINDING_NEXT, SCROLLING, STOPPED }
    private AutoState autoState=AutoState.IDLE;
    private volatile String currentQuestionKey="";
    private String tapQuestionKey="",visibleQuestionKey="",lastAnsweredKey="",lastAnsweredStem="";
    private TextQuestionIdentity lastTextIdentity;
    private int navigationRetries;
    private int navigationAttempts,scrollAttempts;
    private volatile boolean nextPending;
    private long questionStartedAt,answerShownAt,ocrStartedAt,aiStartedAt;
    private OverlayState overlayState=OverlayState.IDLE;
    private int surfaceColor,foregroundColor,mutedColor,accentColor,controlColor;
    private ThemePalette palette;
    private android.content.SharedPreferences.OnSharedPreferenceChangeListener themeListener;
    private enum OverlayState { IDLE, SEARCHING, ANALYZING, ANSWER, ERROR, PAUSED }
    private int width,height,dpi;
    private volatile int epoch;
    private volatile int executionGeneration;
    private long lastFrame,lastAutoRequest,lastFrameArrival,readerCreatedAt;
    private final FrameHealth frameHealth=new FrameHealth();
    private long lastFrameRequest;
    private final FrameProof actionProof=new FrameProof();
    private boolean navigationTouchBusy;
    private TouchAction navigationAction;
    private List<ScreenDocument.Line> navigationChecks;
    private ScreenDocument scrollContext;
    private long scrollContextAt;
    private long navigationEvidenceAfter,navigationEvidenceWait;
    private boolean inlineChoice;
    private int insetLeft,insetTop,insetRight,insetBottom;
    // Owned exclusively by the imaging executor. Reuse large buffers to reduce GC pauses.
    private Bitmap captureBuffer,ocrBuffer;
    private static final class PendingTap {
        final ScreenDocument doc;final AnswerTargetResolver.Target target;
        final List<Integer> questionIds;final int token,epoch;final long created;
        final boolean nodeAttempted;
        PendingTap(ScreenDocument doc,AnswerTargetResolver.Target target,List<Integer> ids,int token,int epoch,boolean nodeAttempted) {
            this.doc=doc;this.target=target;questionIds=ids;this.token=token;this.epoch=epoch;
            this.nodeAttempted=nodeAttempted;created=SystemClock.elapsedRealtime();
        }
    }
    private static final class VisionTapFallback {
        final ScreenQaAccessibilityService.Snapshot source;
        final String type,answer;final int token,epoch;final long created;
        VisionTapFallback(ScreenQaAccessibilityService.Snapshot source,String type,String answer,int token,int epoch) {
            this.source=source;this.type=type;this.answer=answer;this.token=token;this.epoch=epoch;
            created=SystemClock.elapsedRealtime();
        }
    }
    private static final String CHANNEL="screenqa_capture";
    private final MediaProjection.Callback projectionCallback=new MediaProjection.Callback() {
        @Override public void onStop() { stopSelf(); }
        @Override public void onCapturedContentResize(int w,int h) {
            if(!destroyed && display!=null && w>0 && h>0 && (width!=w || height!=h)) resize(w,h);
        }
    };
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private void setAutoState(AutoState next,String reason) {
        if(autoState!=next)QaLog.event("STATE "+autoState+" -> "+next+" reason="+reason+
                " question="+shortId(currentQuestionKey));
        autoState=next;
    }
    private static String shortId(String key){return key.isEmpty()?"none":Integer.toHexString(key.hashCode());}
    private static String safeAnswer(String answer) {
        if(answer==null)return "";
        String shortText=answer.replaceAll("[\\r\\n]"," ").trim();
        return shortText.length()>40?shortText.substring(0,40)+"…":shortText;
    }
    private static String stemKey(ScreenDocument doc,List<Integer> ids) {
        return doc==null||ids==null||ids.isEmpty()?"":QuestionTracker.normalize(doc.text(ids));
    }
    /** Overlay colours follow the app theme so both surfaces read as one product. */
    private ThemePalette theme() {
        if(palette==null)palette=new Settings(this).theme();
        return palette;
    }
    private boolean dark() { return theme().dark; }
    private int answerFill() {return ThemePalette.blend(surfaceColor,accentColor,dark()?0.24f:0.14f);}
    @Override public void onCreate() {
        super.onCreate();windows=getSystemService(WindowManager.class);QaLog.start(this);
        themeListener=(prefs,key)->{
            if("theme_id".equals(key))main.post(()->{if(!destroyed){palette=null;applyTheme();}});
        };
        Settings serviceSettings=new Settings(this);
        serviceSettings.migrateThemeDefault();
        serviceSettings.prefs.registerOnSharedPreferenceChangeListener(themeListener);
        QaLog.event("CAPTURE service_created accessibility_connected="+(ScreenQaAccessibilityService.active!=null));
    }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        if(intent!=null&&"STRATEGY".equals(intent.getAction())) {
            autoAnswerStrategy=new Settings(this).strategy();QaLog.event("STRATEGY changed="+autoAnswerStrategy);
            return START_NOT_STICKY;
        }
        if(intent!=null&&"MANUAL_SELECT".equals(intent.getAction())) {
            if(active&&selector==null)select();
            return START_NOT_STICKY;
        }
        if(intent!=null&&"AUTO_SETTINGS".equals(intent.getAction())) {
            executionGeneration++;nextPending=false;pendingTap=null;rootTapPending=null;visionTapFallback=null;
            navigationTarget=null;
            tapInFlight=false;textInputInFlight=false;navigationTouchBusy=false;scrollContext=null;
            autoSelectEnabled=new Settings(this).autoSelect();
            ScreenQaAccessibilityService.setTracking(true);
            if(!copyAnswer.isEmpty()&&needsPopup(shortAnswer))openAnswerPopup();
            QaLog.event("AUTO_SETTINGS master="+autoSelectEnabled+" ChoiceAutoSelect="+new Settings(this).autoExecute("choice")+
                    " TrueFalseAutoSelect="+new Settings(this).autoExecute("true_false")+
                    " FillBlankAutoInput="+new Settings(this).autoExecute("fill_blank")+
                    " ShortAnswerAutoInput="+new Settings(this).autoExecute("short_answer"));
            return START_NOT_STICKY;
        }
        if(intent==null || "STOP".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if(active) return START_NOT_STICKY;
        try {
            Intent grant=Build.VERSION.SDK_INT>=33 ? intent.getParcelableExtra("grant",Intent.class) : intent.getParcelableExtra("grant");
            if(grant==null || !android.provider.Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY; }
            Settings settings=new Settings(this); apiKey=settings.key();
            autoSelectEnabled=settings.autoSelect();autoAnswerStrategy=settings.strategy();
            QaLog.event("CAPTURE start strategy="+autoAnswerStrategy+" auto_select="+autoSelectEnabled+
                    " accessibility_connected="+(ScreenQaAccessibilityService.active!=null)+" touch_priority="+settings.touchPriority()+
                    " root_touch="+settings.rootAnswerTap()+" root_authorized="+RootManager.get().authorized());
            if(apiKey.isEmpty()) throw new IllegalStateException();
            notification();
            MediaProjectionManager manager=getSystemService(MediaProjectionManager.class);
            projection=manager.getMediaProjection(intent.getIntExtra("code",Activity.RESULT_CANCELED),grant);
            if(projection==null) throw new IllegalStateException();
            projection.registerCallback(projectionCallback,main);
            recognizer=TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
            DisplayMetrics metrics=new DisplayMetrics(); windows.getDefaultDisplay().getRealMetrics(metrics);
            width=metrics.widthPixels; height=metrics.heightPixels; dpi=metrics.densityDpi;
            readerCreatedAt=SystemClock.elapsedRealtime();reader=newReader(width,height);
            display=projection.createVirtualDisplay("ScreenQA",width,height,dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,main);
            createPanel(); active=true;ScreenQaAccessibilityService.setTracking(true);
            main.postDelayed(this::monitorStatus,5000);main.postDelayed(this::pollFrames,700);
            setAutoState(AutoState.DETECTING,"capture_started");
        } catch(Exception e) {
            QaLog.event("CAPTURE start_failed exception="+e.getClass().getSimpleName());
            Toast.makeText(this,"启动失败，请返回应用检查权限和配置后重新开启",Toast.LENGTH_LONG).show(); stopSelf();
        }
        return START_NOT_STICKY;
    }
    private void notification() {
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,"屏幕助手运行状态",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,CaptureService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notice=new Notification.Builder(this,CHANNEL).setSmallIcon(cn.screenqa.lite.R.drawable.ic_notification)
                .setContentTitle(getString(R.string.app_name)+"已开启").setContentText("支持自动找题或手动选区。点击停止可结束屏幕共享。")
                .setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"停止",stop).build()).build();
        if(Build.VERSION.SDK_INT>=29) startForeground(7,notice,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(7,notice);
    }
    private ImageReader newReader(int w,int h) {
        ImageReader result=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,3);
        result.setOnImageAvailableListener(this::frame,main); return result;
    }
    private void monitorStatus(){
        if(destroyed)return;
        QaLog.event("MONITOR auto="+autoMode+" paused="+paused+" state="+autoState+
                " ocr_busy="+ocrBusy+" frame_age_ms="+(lastFrameArrival==0?-1:SystemClock.elapsedRealtime()-lastFrameArrival)+
                " processed_age_ms="+(lastFrame==0?-1:SystemClock.elapsedRealtime()-lastFrame)+" reader_repairs="+frameHealth.repairs()+
                " next_pending="+nextPending+" touch_pending="+(rootTapPending!=null||navigationTouchBusy)+
                " navigation_pending="+(navigationTarget!=null)+" node_scanning="+ScreenQaAccessibilityService.isScanning());
        main.postDelayed(this::monitorStatus,5000);
    }
    private void pollFrames(){
        if(destroyed)return;
        // Polling also drains an ImageReader whose listener delivery stopped; every acquired Image is closed.
        if(reader!=null)frame(reader);
        long now=SystemClock.elapsedRealtime();
        // A static screen is damage-driven: no new buffer is not a broken projection.
        // Ask the existing display for a new buffer before an action times out.
        if(!paused&&selector==null&&!ocrBusy&&rootTapPending==null&&!navigationTouchBusy&&
                now-Math.max(lastFrameArrival,readerCreatedAt)>=700&&now-lastFrameRequest>=1000)
            requestFreshFrame();
        if(pendingTap!=null&&now-pendingTap.created>=3000){
            PendingTap expired=pendingTap;pendingTap=null;completeTap(false,expired.token,expired.epoch);
            QaLog.event("TOUCH pending_expired=no_fresh_frame");
        }
        if(visionTapFallback!=null&&now-visionTapFallback.created>=3000){
            VisionTapFallback expired=visionTapFallback;visionTapFallback=null;completeTap(false,expired.token,expired.epoch);
        }
        if(!paused&&selector==null&&display!=null&&frameHealth.shouldRepair(now,
                Math.max(lastFrameArrival,readerCreatedAt),ocrBusy||tapInFlight||navigationTouchBusy)){
            frameHealth.repaired(now);
            ImageReader old=reader,replacement=null;
            try{
                replacement=newReader(width,height);
                // Android 14+: reuse the existing VirtualDisplay, never create another from the grant.
                display.setSurface(replacement.getSurface());reader=replacement;readerCreatedAt=now;
                old.setOnImageAvailableListener(null,null);old.close();
                String answered=lastAnsweredKey,stem=lastAnsweredStem;
                executionGeneration++;invalidateQuestion();lastAnsweredKey=answered;lastAnsweredStem=stem;lastFrame=0;
                QaLog.event("FRAME recovery=rebind_existing_surface attempt="+frameHealth.repairs());
                status.setText("屏幕画面已重新连接 · 正在重新识别");
            }catch(RuntimeException e){
                if(replacement!=null&&replacement!=reader)replacement.close();
                QaLog.event("FRAME recovery_failed exception="+e.getClass().getSimpleName());
            }
        }
        if(!paused&&frameHealth.exhausted(now)&&now-Math.max(lastFrameArrival,readerCreatedAt)>12000)
            status.setText("屏幕共享未恢复 · 请关闭助手后重新授权整个屏幕");
        main.postDelayed(this::pollFrames,700);
    }
    private void requestFreshFrame(){
        if(destroyed||display==null||reader==null||ocrBusy||rootTapPending!=null||navigationTouchBusy)return;
        ImageReader old=reader,replacement=null;
        lastFrameRequest=SystemClock.elapsedRealtime();
        try{
            replacement=newReader(width,height);
            display.setSurface(replacement.getSurface());reader=replacement;
            old.setOnImageAvailableListener(null,null);old.close();
            QaLog.event("FRAME refresh=requested_existing_display");
        }catch(RuntimeException e){
            if(replacement!=null&&replacement!=reader)replacement.close();
            QaLog.event("FRAME refresh_failed exception="+e.getClass().getSimpleName());
        }
    }
    private void createPanel() {
        panel=new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(14),dp(11),dp(14),dp(12));
        panel.setElevation(dp(14));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);panel.addView(header);
        TextView handle=new TextView(this); handle.setText(getString(R.string.overlay_drag_handle,getString(R.string.app_name))); handle.setTextSize(14);
        handle.setTypeface(null,Typeface.BOLD);handle.setPadding(dp(2),dp(8),0,dp(8));
        header.addView(handle,new LinearLayout.LayoutParams(0,dp(40),1));
        ImageButton minimize=iconButton(header,R.drawable.ic_minimize,"缩小悬浮窗");minimize.setOnClickListener(v -> setMinimized(true));
        status=new TextView(this); status.setTextSize(12);status.setTypeface(null,Typeface.BOLD);
        status.setText("自动模式 · 切到题目页面后点开始");status.setPadding(dp(11),dp(8),dp(11),dp(8));
        LinearLayout.LayoutParams statusLayout=new LinearLayout.LayoutParams(-1,-2);statusLayout.bottomMargin=dp(8);
        panel.addView(status,statusLayout);
        answerBox=new ScrollView(this); answer=new TextView(this); answer.setTextSize(17);
        answer.setText("答案会显示在这里"); answer.setPadding(dp(13),dp(12),dp(13),dp(12));
        answer.setLineSpacing(dp(4),1); answerBox.addView(answer);
        panel.addView(answerBox,new LinearLayout.LayoutParams(-1,dp(112)));
        answerBox.setVisibility(View.GONE);
        inlineCopy=new Button(this);inlineCopy.setText("复制答案");inlineCopy.setTextSize(13);
        inlineCopy.setVisibility(View.GONE);inlineCopy.setOnClickListener(v -> copyCurrentAnswer());
        panel.addView(inlineCopy,new LinearLayout.LayoutParams(-1,dp(38)));
        LinearLayout buttons=new LinearLayout(this); buttons.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams buttonLayout=new LinearLayout.LayoutParams(-1,-2);buttonLayout.topMargin=dp(3);
        panel.addView(buttons,buttonLayout);
        toggle=iconButton(buttons,R.drawable.ic_play,"开始或暂停"); toggle.setOnClickListener(v -> toggle());
        autoButton=iconButton(buttons,R.drawable.ic_auto,"自动找题"); autoButton.setOnClickListener(v -> {
            closeAnswerPopup(false);autoMode=true;paused=false;invalidateQuestion();updateToggleIcon();
            showState(OverlayState.SEARCHING,"自动找题中 · 请把悬浮窗移开题目","等待识别题干、题型和范围…");lastFrame=0;
        });
        fold=iconButton(buttons,R.drawable.ic_article,"查看答案"); fold.setOnClickListener(v -> {
            if(!fullAnswer.isEmpty())openAnswerPopup(); else {collapsed=!collapsed;answerBox.setVisibility(collapsed?View.GONE:View.VISIBLE);}
        });
        iconButton(buttons,R.drawable.ic_settings,"打开设置").setOnClickListener(v -> {
            Intent open=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(open);
        });
        iconButton(buttons,R.drawable.ic_close,"关闭悬浮窗").setOnClickListener(v -> stopSelf());
        panelParams=new WindowManager.LayoutParams(Math.min(dp(316),width-dp(16)),WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        panelParams.gravity=Gravity.TOP|Gravity.LEFT; panelParams.x=dp(8); panelParams.y=dp(48);
        windows.addView(panel,panelParams);
        bubble=new FrameLayout(this);bubble.setElevation(dp(16));
        bubbleIcon=new ImageView(this);bubbleIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams iconLp=new FrameLayout.LayoutParams(dp(26),dp(26),Gravity.CENTER);bubble.addView(bubbleIcon,iconLp);
        bubbleRing=new LoadingRingView(this);FrameLayout.LayoutParams ringLp=new FrameLayout.LayoutParams(dp(38),dp(38),Gravity.CENTER);bubble.addView(bubbleRing,ringLp);
        bubbleText=new TextView(this);bubbleText.setGravity(Gravity.CENTER);bubbleText.setTextSize(15);
        bubbleText.setTypeface(null,Typeface.BOLD);bubbleText.setMaxLines(2);
        bubbleText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        bubble.addView(bubbleText,new FrameLayout.LayoutParams(dp(48),dp(44),Gravity.CENTER));
        bubbleParams=new WindowManager.LayoutParams(dp(60),dp(60),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        bubbleParams.gravity=Gravity.TOP|Gravity.LEFT;bubbleParams.x=panelParams.x;bubbleParams.y=panelParams.y;
        windows.addView(bubble,bubbleParams);bubble.setVisibility(View.GONE);
        bubble.setOnTouchListener(new View.OnTouchListener() {
            float x,y;int startX,startY;boolean moved;
            @Override public boolean onTouch(View v,MotionEvent e) {
                if(e.getActionMasked()==MotionEvent.ACTION_DOWN){x=e.getRawX();y=e.getRawY();startX=bubbleParams.x;startY=bubbleParams.y;moved=false;return true;}
                if(e.getActionMasked()==MotionEvent.ACTION_MOVE){
                    if(Math.abs(e.getRawX()-x)>dp(5)||Math.abs(e.getRawY()-y)>dp(5))moved=true;
                    if(moved){bubbleParams.x=Math.max(0,Math.min(width-dp(60),startX+(int)(e.getRawX()-x)));
                        bubbleParams.y=Math.max(dp(24),Math.min(height-dp(84),startY+(int)(e.getRawY()-y)));
                        windows.updateViewLayout(bubble,bubbleParams);positionAnswerPopup();}return true;
                }
                if(e.getActionMasked()==MotionEvent.ACTION_UP){if(!moved)v.performClick();return true;}
                return true;
            }
        });
        bubble.setOnClickListener(v -> {
            if(overlayState==OverlayState.ANSWER && !fullAnswer.isEmpty() && needsPopup(shortAnswer))openAnswerPopup();
            else {setMinimized(false);if(overlayState==OverlayState.ERROR){
                paused=true;updateToggleIcon();collapsed=false;answerBox.setVisibility(View.VISIBLE);
            }}
        });
        handle.setOnTouchListener(new View.OnTouchListener() {
            float x,y; int startX,startY;
            @Override public boolean onTouch(View view,MotionEvent e) {
                if(e.getActionMasked()==MotionEvent.ACTION_DOWN) {x=e.getRawX();y=e.getRawY();startX=panelParams.x;startY=panelParams.y;return true;}
                if(e.getActionMasked()==MotionEvent.ACTION_MOVE) {
                    panelParams.x=Math.max(0,Math.min(width-panel.getWidth(),startX+(int)(e.getRawX()-x)));
                    panelParams.y=Math.max(dp(24),Math.min(height-panel.getHeight()-dp(24),startY+(int)(e.getRawY()-y)));
                    windows.updateViewLayout(panel,panelParams);positionAnswerPopup(); return true;
                }
                if(e.getActionMasked()==MotionEvent.ACTION_UP && Math.abs(e.getRawX()-x)<dp(6) && Math.abs(e.getRawY()-y)<dp(6)) view.performClick();
                return true;
            }
        });
        handle.setOnClickListener(v -> fold.performClick());
        applyTheme();renderBubble();
    }
    private ImageButton iconButton(LinearLayout box,int icon,String description) {
        ImageButton b=new ImageButton(this);b.setImageResource(icon);b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        b.setContentDescription(description);b.setPadding(dp(9),dp(9),dp(9),dp(9));
        b.setBackground(rounded(ThemePalette.alpha(theme().accent,0.12f),15));
        box.addView(b,new LinearLayout.LayoutParams(dp(44),dp(44)));return b;
    }
    private GradientDrawable rounded(int color,int radius) {
        GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(dp(radius));return bg;
    }
    private GradientDrawable bordered(int color,int radius,int border) {
        GradientDrawable bg=rounded(color,radius);bg.setStroke(dp(2),border);return bg;
    }
    private void tintIcons(View group,int color) {
        if(group instanceof ImageButton)((ImageButton)group).setImageTintList(ColorStateList.valueOf(color));
        if(group instanceof android.view.ViewGroup) {
            android.view.ViewGroup box=(android.view.ViewGroup)group;
            for(int i=0;i<box.getChildCount();i++)tintIcons(box.getChildAt(i),color);
        }
    }
    private void applyTheme() {
        if(panel==null)return;
        palette=new Settings(this).theme();
        surfaceColor=palette.surface;
        foregroundColor=palette.foreground;
        mutedColor=palette.secondary;
        accentColor=palette.accent;
        controlColor=palette.surfaceAlt;
        panel.setBackground(bordered(surfaceColor,24,palette.border));
        answerBox.setBackground(rounded(answerFill(),17));
        answer.setTextColor(foregroundColor);
        if(inlineCopy!=null){inlineCopy.setTextColor(accentColor);inlineCopy.setBackground(rounded(answerFill(),12));}
        tintIcons(panel,foregroundColor);
        LinearLayout header=(LinearLayout)panel.getChildAt(0);
        ((TextView)header.getChildAt(0)).setTextColor(foregroundColor);
        for(int i=0;i<panel.getChildCount();i++)if(panel.getChildAt(i) instanceof LinearLayout) {
            LinearLayout row=(LinearLayout)panel.getChildAt(i);
            for(int j=0;j<row.getChildCount();j++)if(row.getChildAt(j) instanceof ImageButton)
                row.getChildAt(j).setBackground(rounded(ThemePalette.alpha(accentColor,0.12f),15));
        }
        if(answerPopup!=null)answerPopup.setBackground(bordered(surfaceColor,23,palette.border));
        if(popupText!=null){popupText.setTextColor(foregroundColor);
            popupText.setBackground(rounded(answerFill(),17));}
        if(popupTitle!=null)popupTitle.setTextColor(accentColor);
        updateToggleIcon();
        renderBubble();
    }
    private void updateToggleIcon() {
        if(toggle!=null){toggle.setImageResource(paused?R.drawable.ic_play:R.drawable.ic_pause);
            toggle.setContentDescription(paused?"开始":"暂停");
            toggle.setImageTintList(ColorStateList.valueOf(foregroundColor));}
    }
    private void showState(OverlayState state,String message,String detail) {
        overlayState=state;status.setText(message);answer.setText(detail);
        if(state!=OverlayState.ANSWER){fullAnswer="";shortAnswer="";copyAnswer="";
            publishAnswer("");
            if(inlineCopy!=null)inlineCopy.setVisibility(View.GONE);closeAnswerPopup(false);}
        renderBubble();
    }
    private void showAnswer(String label,String detail,String message) {
        showAnswer(label,detail,message,"");
    }
    private void showAnswer(String label,String detail,String message,String copy) {
        String key=currentQuestionKey;int displayEpoch=epoch;
        long remaining=MIN_ANSWER_VISIBLE_MS-(SystemClock.elapsedRealtime()-answerShownAt);
        if(!fullAnswer.isEmpty()&&!visibleQuestionKey.equals(key)&&remaining>0) {
            QaLog.event("ANSWER_UI deferred_ms="+remaining+" next_question="+shortId(key));
            main.postDelayed(() -> {
                if(!destroyed&&!paused&&displayEpoch==epoch&&currentQuestionKey.equals(key))
                    applyAnswer(label,detail,message,key,copy);
            },remaining);
            return;
        }
        applyAnswer(label,detail,message,key,copy);
    }
    private void applyAnswer(String label,String detail,String message,String key,String copy) {
        if(!visibleQuestionKey.equals(key))closeAnswerPopup(false);
        overlayState=OverlayState.ANSWER;shortAnswer=label.trim();fullAnswer=detail.trim();copyAnswer=copy;
        inlineCopy.setVisibility(copy.isEmpty()?View.GONE:View.VISIBLE);
        answerShownAt=SystemClock.elapsedRealtime();visibleQuestionKey=key;
        QaLog.event("ANSWER_UI shown question="+shortId(key)+" min_visible_ms="+MIN_ANSWER_VISIBLE_MS);
        status.setText(message);answer.setText(fullAnswer);
        publishAnswer(fullAnswer);
        if(!minimized)setMinimized(true);
        renderBubble();
        if(needsPopup(shortAnswer)&&!tapInFlight&&!textInputInFlight)openAnswerPopup();
    }
    private boolean needsPopup(String value) { return !inlineChoice && !fullAnswer.isEmpty(); }
    private void copyCurrentAnswer() {
        QaLog.event("CopyAnswer clicked answer_length="+copyAnswer.length());
        if(copyAnswer.isEmpty())return;
        try {
            ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(clipboard==null)throw new IllegalStateException("clipboard_unavailable");
            clipboard.setPrimaryClip(ClipData.newPlainText("答案",copyAnswer));
            QaLog.event("Clipboard copy success answer_length="+copyAnswer.length());
            Toast.makeText(this,"已复制",Toast.LENGTH_SHORT).show();
        } catch(Exception e) {
            QaLog.event("Clipboard copy failed reason="+e.getClass().getSimpleName());
            Toast.makeText(this,"复制失败，请长按答案复制",Toast.LENGTH_SHORT).show();
        }
    }
    private void renderBubble() {
        if(bubble==null)return;
        ThemePalette p=theme();
        boolean showingAnswer=overlayState==OverlayState.ANSWER;
        boolean error=overlayState==OverlayState.ERROR;
        bubbleRing.setRunning(minimized && (overlayState==OverlayState.SEARCHING||overlayState==OverlayState.ANALYZING));
        bubbleRing.setRingColor(p.accent);
        bubble.setBackground(bordered(showingAnswer?accentColor:controlColor,30,
                showingAnswer?ThemePalette.blend(accentColor,p.onAccent,0.42f):p.border));
        bubbleText.setTextColor(showingAnswer?p.onAccent:foregroundColor);
        status.setTextColor(error?p.danger:accentColor);
        status.setBackground(rounded(error?ThemePalette.alpha(p.danger,0.16f):ThemePalette.alpha(accentColor,0.14f),13));
        bubbleText.setVisibility(overlayState==OverlayState.ANSWER?View.VISIBLE:View.GONE);
        bubbleIcon.setVisibility(overlayState==OverlayState.ANSWER?View.GONE:View.VISIBLE);
        if(overlayState==OverlayState.ANSWER){
            String preview=shortAnswer.isEmpty()?"答案":shortAnswer;
            bubbleText.setTextSize(preview.length()>3?12:24);
            bubbleText.setText(preview.length()>8?preview.substring(0,8)+"…":preview);
        } else {
            int icon=error?R.drawable.ic_error:
                    overlayState==OverlayState.PAUSED?R.drawable.ic_pause:
                    overlayState==OverlayState.IDLE?R.drawable.ic_app:R.drawable.ic_auto;
            bubbleIcon.setImageResource(icon);
            bubbleIcon.setImageTintList(ColorStateList.valueOf(error?p.danger:foregroundColor));
        }
        bubble.setContentDescription(error?"识别或分析出错，点击查看原因":
                overlayState==OverlayState.ANSWER?"已得出答案，点击查看详情":"大学生小帮手，点击展开");
    }
    private void setMinimized(boolean value) {
        if(panel==null||bubble==null)return;
        minimized=value;
        if(value){bubbleParams.x=Math.max(0,Math.min(width-dp(60),panelParams.x));
            bubbleParams.y=Math.max(dp(24),Math.min(height-dp(84),panelParams.y));windows.updateViewLayout(bubble,bubbleParams);}
        else {panelParams.x=Math.max(0,Math.min(width-panelParams.width,bubbleParams.x));
            panelParams.y=Math.max(dp(24),Math.min(height-dp(180),bubbleParams.y));windows.updateViewLayout(panel,panelParams);}
        panel.setVisibility(value?View.GONE:View.VISIBLE);bubble.setVisibility(value?View.VISIBLE:View.GONE);
        renderBubble();positionAnswerPopup();
    }
    private void openAnswerPopup() {
        if(fullAnswer.isEmpty()||answerPopup!=null||inlineChoice)return;
        answerPopup=new LinearLayout(this);answerPopup.setOrientation(LinearLayout.VERTICAL);
        answerPopup.setPadding(dp(14),dp(12),dp(14),dp(14));answerPopup.setElevation(dp(16));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);answerPopup.addView(top);
        TextView title=new TextView(this);title.setText(overlayState==OverlayState.ERROR?"报错详情":"答案");
        title.setTextSize(14);title.setTypeface(null,android.graphics.Typeface.BOLD);popupTitle=title;
        top.addView(title,new LinearLayout.LayoutParams(0,dp(42),1));
        if(!copyAnswer.isEmpty()) {
            Button copy=new Button(this);copy.setText("复制");copy.setTextSize(13);
            copy.setTextColor(accentColor);copy.setOnClickListener(v -> copyCurrentAnswer());
            top.addView(copy,new LinearLayout.LayoutParams(dp(72),dp(42)));
        }
        ImageButton close=iconButton(top,R.drawable.ic_close,"关闭详情");close.setOnClickListener(v -> closeAnswerPopup(true));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);
        popupText=new TextView(this);popupText.setText(fullAnswer);popupText.setTextSize(19);
        popupText.setTypeface(null,Typeface.BOLD);popupText.setPadding(dp(15),dp(14),dp(15),dp(15));
        popupText.setLineSpacing(dp(5),1);popupText.setTextIsSelectable(true);scroll.addView(popupText);
        answerPopup.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
        popupParams=new WindowManager.LayoutParams(Math.min(dp(340),width-dp(24)),WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        popupParams.gravity=Gravity.TOP|Gravity.LEFT;
        windows.addView(answerPopup,popupParams);
        answerPopup.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> positionAnswerPopup());
        positionAnswerPopup();
        answerPopup.setBackground(bordered(surfaceColor,23,theme().border));
        title.setTextColor(accentColor);popupText.setTextColor(foregroundColor);
        popupText.setBackground(rounded(answerFill(),17));
        close.setImageTintList(ColorStateList.valueOf(foregroundColor));
        // Answer stays visible until the next question or explicit user action.
    }
    private void positionAnswerPopup() {
        if(answerPopup==null)return;
        int anchorX=minimized?bubbleParams.x:panelParams.x;
        int anchorY=minimized?bubbleParams.y:panelParams.y;
        int anchorHeight=minimized?dp(60):panel.getHeight();
        int gap=dp(6),top=Math.max(dp(24),insetTop),bottom=height-Math.max(dp(24),insetBottom);
        int below=bottom-anchorY-anchorHeight-gap,above=anchorY-top-gap;
        boolean under=below>=Math.min(dp(220),Math.max(dp(110),answerPopup.getMeasuredHeight()))||below>=above;
        int room=Math.max(dp(64),under?below:above);
        int limit=Math.min((int)(height*0.65f),room);
        answerPopup.measure(View.MeasureSpec.makeMeasureSpec(popupParams.width,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(limit,View.MeasureSpec.AT_MOST));
        int h=Math.min(limit,answerPopup.getMeasuredHeight());
        int x=Math.max(dp(8),Math.min(width-popupParams.width-dp(8),anchorX));
        int y=under?anchorY+anchorHeight+gap:anchorY-h-gap;
        y=Math.max(top,Math.min(bottom-h,y));
        if(popupParams.x==x&&popupParams.y==y&&popupParams.height==h)return;
        popupParams.x=x;popupParams.y=y;popupParams.height=h;
        windows.updateViewLayout(answerPopup,popupParams);
    }
    private Rect contentBounds(int w,int h) {
        WindowInsets insets=panel==null?null:panel.getRootWindowInsets();
        insetLeft=0;insetRight=0;insetTop=0;insetBottom=0;
        if(Build.VERSION.SDK_INT>=30) {
            // Display metrics describe the full shared display (MainActivity requests it).
            insets=windows.getMaximumWindowMetrics().getWindowInsets();
            Insets bars=insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            insetLeft=bars.left;insetTop=bars.top;insetRight=bars.right;insetBottom=bars.bottom;
        } else if(insets!=null) {
            insetLeft=insets.getStableInsetLeft();insetTop=insets.getStableInsetTop();
            insetRight=insets.getStableInsetRight();insetBottom=insets.getStableInsetBottom();
            if(Build.VERSION.SDK_INT>=28 && insets.getDisplayCutout()!=null) {
                DisplayCutout cutout=insets.getDisplayCutout();
                insetTop=Math.max(insetTop,cutout.getSafeInsetTop());
                insetLeft=Math.max(insetLeft,cutout.getSafeInsetLeft());
                insetRight=Math.max(insetRight,cutout.getSafeInsetRight());
                insetBottom=Math.max(insetBottom,cutout.getSafeInsetBottom());
            }
        }
        if(insetTop==0) {
            int id=getResources().getIdentifier("status_bar_height","dimen","android");
            insetTop=id>0?getResources().getDimensionPixelSize(id):dp(24);
        }
        return new Rect(insetLeft,insetTop,w-insetRight,h-insetBottom);
    }
    private void closeAnswerPopup(boolean resume) {
        if(answerPopup!=null){try{windows.removeView(answerPopup);}catch(Exception ignored){}answerPopup=null;popupText=null;popupTitle=null;}
        if(resume)lastFrame=0;
    }
    private void invalidateQuestion() {
        answerRetry=null;
        actionProof.observe(SystemClock.elapsedRealtime(),false);
        epoch++;tracker.reset(); if(request!=null) {request.cancel();request=null;}
          pendingTap=null;rootTapPending=null;visionTapFallback=null;tapInFlight=false;textInputInFlight=false;
          nextPending=false;navigationTouchBusy=false;scrollContext=null;navigationAttempts=0;scrollAttempts=0;currentQuestionKey="";tapQuestionKey="";lastTextIdentity=null;
          lastAnsweredKey="";lastAnsweredStem="";
        navigationEvidenceAfter=0;navigationEvidenceWait=0;
        setAutoState(paused?AutoState.IDLE:AutoState.DETECTING,"question_invalidated");
        latestDocument=null;latestOcrDocument=null;navigationTarget=null;lastDetection=null;clearOutline();
    }
    private void toggle() {
        if(answerPopup!=null)closeAnswerPopup(true);
        if(!autoMode && region==null) {showState(OverlayState.ERROR,"请先选区，或点击自动找题","尚未设置手动选区");return;}
        if(!autoMode && paused && panelOverlaps()) {showState(OverlayState.ERROR,"请先收起或拖动悬浮窗，移出选区","悬浮窗挡住了选区");return;}
        paused=!paused; invalidateQuestion(); updateToggleIcon();
        showState(paused?OverlayState.PAUSED:OverlayState.SEARCHING,
                paused?"已暂停，点击开始可重新识别":autoMode?"自动找题中 · 等待画面稳定":"手动选区扫描中 · 等待画面稳定",
                paused?"已暂停":"等待识别题目…");lastFrame=0;
    }
    private void select() {
        closeAnswerPopup(false);paused=true;invalidateQuestion();updateToggleIcon();
        panel.setVisibility(View.GONE);bubble.setVisibility(View.GONE);
        selector=new RegionSelector(this,rect -> {
            if(!rect.intersect(0,0,width,height)) {closeSelector();showState(OverlayState.ERROR,"选区无效，请重新选择","选区超出屏幕范围");return;}
            autoMode=false;region=rect;closeSelector();
            showState(OverlayState.PAUSED,"选区已保存 · 点击开始","把悬浮窗拖到选区之外，再开始识别。");
        },() -> {closeSelector();showState(OverlayState.PAUSED,"已取消选区 · 已暂停","请重新选择题目范围");});
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.LEFT;
        if(Build.VERSION.SDK_INT>=28) params.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        windows.addView(selector,params);
    }
    private void closeSelector() {
        if(selector!=null) {windows.removeView(selector);selector=null;}
        if(panel!=null) panel.setVisibility(minimized?View.GONE:View.VISIBLE);
        if(bubble!=null)bubble.setVisibility(minimized?View.VISIBLE:View.GONE);
    }
    private boolean panelOverlaps() {
        return Rect.intersects(region,panelRect());
    }
    private Rect panelRect() {
        View visible=minimized?bubble:panel;
        int[] xy=new int[2];visible.getLocationOnScreen(xy);
        return new Rect(xy[0]-dp(5),xy[1]-dp(5),xy[0]+visible.getWidth()+dp(5),xy[1]+visible.getHeight()+dp(5));
    }
    private Rect popupRect() {
        if(answerPopup==null)return new Rect();
        int[] xy=new int[2];answerPopup.getLocationOnScreen(xy);
        return new Rect(xy[0]-dp(5),xy[1]-dp(5),xy[0]+answerPopup.getWidth()+dp(5),xy[1]+answerPopup.getHeight()+dp(5));
    }
    private void clearOutline() {
        if(outline!=null) {try {windows.removeView(outline);}catch(Exception ignored){}outline=null;}
    }
    private void showOutline(ScreenDocument doc,QuestionDetection detection) {
        int[] bounds=doc.bounds(detection.questionIds);
        if(outline==null) {
            outline=new QuestionOutline(this);
            outlineParams=new WindowManager.LayoutParams(bounds[2]-bounds[0],bounds[3]-bounds[1],
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
            outlineParams.gravity=Gravity.TOP|Gravity.LEFT;outlineParams.alpha=0.55f;
            outlineParams.x=bounds[0];outlineParams.y=bounds[1];windows.addView(outline,outlineParams);
        } else {
            outlineParams.x=bounds[0];outlineParams.y=bounds[1];outlineParams.width=bounds[2]-bounds[0];outlineParams.height=bounds[3]-bounds[1];
            windows.updateViewLayout(outline,outlineParams);
        }
    }
    private void frame(ImageReader source) {
        if(source!=reader)return;
        Image image=null;
        try {
            image=source.acquireLatestImage();
            if(image!=null)lastFrameArrival=SystemClock.elapsedRealtime();
            if(image==null || destroyed || paused || selector!=null || (!autoMode && region==null))return;
            if(rootTapPending!=null||navigationTouchBusy){
                // Keep draining while su/gesture waits, so the producer cannot freeze on queued old frames.
                // Observe only the current action's pixels; do not feed transient selection styling back into AI.
                boolean same=rootTapPending!=null?matchesFrame(image,rootTapPending.doc,linesFor(rootTapPending)):
                        matchesFrame(image,navigationFrame,navigationChecks);
                actionProof.observe(SystemClock.elapsedRealtime(),same);return;
            }
            if(navigationTarget!=null){finishNavigationTap(image);return;}
            if(pendingTap!=null){finishPendingTap(image);return;}
            if(ocrBusy)return;
            long now=SystemClock.elapsedRealtime();
            VisionTapFallback fallback=visionTapFallback;
            if(fallback!=null&&now-fallback.created>=3000) {
                visionTapFallback=null;completeTap(false,fallback.token,fallback.epoch);return;
            }
            if(fallback==null&&now-lastFrame<650)return;
            Rect overlay=panelRect(),popup=popupRect();
            if(fallback==null&&autoMode&&autoAnswerStrategy.readNodesFirst()&&
                    !(nextPending&&scrollAttempts>0&&navigationAttempts==0)) {
                ScreenQaAccessibilityService.Snapshot nodes=ScreenQaAccessibilityService.current();
                if(nodes!=null) {
                    lastFrame=now;nodesScanningSince=-1;
                    autoRecognized(nodes.candidate.document,nodes.candidate,nodes);
                    return;
                }
                if(ScreenQaAccessibilityService.isScanning()||autoAnswerStrategy.retryNodesBeforeVision()){
                    if(nodesScanningSince<0)nodesScanningSince=now;
                    if(NavigationPolicy.waitForScan(now,nodesScanningSince)){
                        if(ScreenQaAccessibilityService.isScanning())return;
                        if(autoAnswerStrategy.retryNodesBeforeVision()&&ScreenQaAccessibilityService.requestSecondLook())return;
                    }
                }
            }
            if(autoMode&&!popup.isEmpty()&&now-answerShownAt>=MIN_ANSWER_VISIBLE_MS) {
                closeAnswerPopup(false);popup=new Rect();
                QaLog.event("ANSWER_UI collapsed reason=resume_question_monitoring");
            }
            if(!autoMode && (Rect.intersects(region,overlay)||Rect.intersects(region,popup)))return;
            final boolean automatic=autoMode;
            final Rect crop=automatic?contentBounds(image.getWidth(),image.getHeight()):new Rect(region);
            if(!crop.intersect(0,0,image.getWidth(),image.getHeight())||crop.width()<10||crop.height()<10)return;
            final int snapshotEpoch=epoch;
            final Image captured=image;
            final Rect capturedPopup=popup;
            final int screenWidth=width,screenHeight=height;
            ocrBusy=true;lastFrame=now;ocrStartedAt=now;nodesScanningSince=-1;
            QaLog.event("OCR start source="+(automatic?"automatic":"manual")+" question="+shortId(currentQuestionKey));
            imaging.execute(() -> processFrame(captured,crop,overlay,capturedPopup,automatic,snapshotEpoch,screenWidth,screenHeight));
            image=null; // Ownership is transferred to the imaging worker.
        } catch(Exception e) {
            ocrBusy=false;
            QaLog.event("FRAME exception="+e.getClass().getSimpleName());
            if(!destroyed)showState(OverlayState.ERROR,"读取画面失败，请暂停后重试",ApiRequest.error(e));
        } finally {if(image!=null)image.close();}
    }
    private void processFrame(Image captured,Rect crop,Rect excluded,Rect popupExcluded,boolean automatic,int snapshotEpoch,int screenWidth,int screenHeight) {
        Bitmap bitmap=null;
        try {
            try {
                Image.Plane plane=captured.getPlanes()[0];ByteBuffer buffer=plane.getBuffer();
                int rowStride=plane.getRowStride(),pixelStride=plane.getPixelStride();
                ocrBuffer=bufferFor(ocrBuffer,crop.width(),crop.height());bitmap=ocrBuffer;
                if(pixelStride==4 && rowStride%4==0 && buffer.remaining()>=rowStride*captured.getHeight()) {
                    captureBuffer=bufferFor(captureBuffer,rowStride/4,captured.getHeight());
                    captureBuffer.copyPixelsFromBuffer(buffer);
                    bitmap.eraseColor(Color.WHITE);
                    new Canvas(bitmap).drawBitmap(captureBuffer,crop,new Rect(0,0,crop.width(),crop.height()),null);
                } else {
                    int[] pixels=new int[crop.width()*crop.height()];int p=0;
                    for(int y=crop.top;y<crop.bottom;y++) {
                        int offset=y*rowStride+crop.left*pixelStride;
                        for(int x=0;x<crop.width();x++,offset+=pixelStride)
                            pixels[p++]=Color.rgb(buffer.get(offset)&255,buffer.get(offset+1)&255,buffer.get(offset+2)&255);
                    }
                    bitmap.setPixels(pixels,0,crop.width(),0,0,crop.width(),crop.height());
                }
            } finally {captured.close();}
            if(automatic) {
                Canvas canvas=new Canvas(bitmap);Paint mask=new Paint();mask.setColor(Color.WHITE);
                canvas.drawRect(excluded.left-crop.left,excluded.top-crop.top,excluded.right-crop.left,excluded.bottom-crop.top,mask);
                if(!popupExcluded.isEmpty())canvas.drawRect(popupExcluded.left-crop.left,popupExcluded.top-crop.top,popupExcluded.right-crop.left,popupExcluded.bottom-crop.top,mask);
            }
            final Bitmap input=bitmap;
            recognizer.process(InputImage.fromBitmap(input,0)).addOnCompleteListener(imaging,task -> {
                ScreenDocument doc=null;String text=null;Exception error=null;
                LocalQuestionLocator.Candidate local=null;ScreenDocument observation=null;
                try {
                    if(task.isSuccessful()) {
                        if(automatic){doc=document(task.getResult(),crop,excluded,popupExcluded,screenWidth,screenHeight,input);observation=doc;local=LocalQuestionLocator.locate(doc);if(local!=null)doc=local.document;}
                        else {text=task.getResult().getText();doc=document(task.getResult(),crop,new Rect(),new Rect(),screenWidth,screenHeight,input);}
                    } else error=task.getException();
                } catch(Exception e){error=e;}
                final ScreenDocument result=doc;final String manual=text;final Exception failure=error;
                final LocalQuestionLocator.Candidate candidate=local;
                final ScreenDocument observed=observation;
                main.post(() -> {
                    ocrBusy=false;
                    QaLog.event("OCR end elapsed_ms="+(SystemClock.elapsedRealtime()-ocrStartedAt)+
                            " success="+(failure==null)+" lines="+(result==null?0:result.lines.size())+
                            " local_candidate="+(candidate!=null));
                    if(destroyed){finishImaging();return;}
                    if(paused||snapshotEpoch!=epoch)return;
                    if(observed!=null){latestOcrDocument=observed;latestOcrAt=ocrStartedAt;}
                    if(visionTapFallback!=null)finishVisionFallback(result,candidate);
                    else if(failure!=null){invalidateQuestion();showState(OverlayState.ERROR,"文字识别失败，稍后重试或重新选区",ApiRequest.error(failure));}
                    else if(automatic)autoRecognized(result,candidate,null);else recognized(manual,result);
                });
            });
        } catch(Exception e) {
            main.post(() -> {ocrBusy=false;QaLog.event("OCR exception="+e.getClass().getSimpleName());
                if(destroyed){finishImaging();return;}
                if(!paused&&snapshotEpoch==epoch){invalidateQuestion();showState(OverlayState.ERROR,"读取画面失败，请暂停后重试",ApiRequest.error(e));}});
        }
    }
    private Bitmap bufferFor(Bitmap existing,int w,int h) {
        if(existing!=null && existing.getWidth()==w && existing.getHeight()==h)return existing;
        if(existing!=null)existing.recycle();
        return Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
    }
    private void finishImaging() {
        if(recognizer!=null)recognizer.close();
        imaging.execute(() -> {
            if(captureBuffer!=null){captureBuffer.recycle();captureBuffer=null;}
            if(ocrBuffer!=null){ocrBuffer.recycle();ocrBuffer=null;}
        });
        imaging.shutdown();
    }
    private ScreenDocument document(Text text,Rect crop,Rect excluded,Rect popupExcluded,int screenWidth,int screenHeight,Bitmap input) {
        List<ScreenDocument.Line> lines=new ArrayList<>();
        for(Text.TextBlock block:text.getTextBlocks())for(Text.Line line:block.getLines()) {
            Rect box=line.getBoundingBox();if(box==null)continue;
            box=new Rect(box);box.offset(crop.left,crop.top);
            if(Rect.intersects(box,excluded)||Rect.intersects(box,popupExcluded))continue;
            long signature=VisualSignature.fromBitmap(input,box,crop);
            lines.add(new ScreenDocument.Line(line.getText(),box.left,box.top,box.right,box.bottom,signature));
        }
        return new ScreenDocument(lines,screenWidth,screenHeight);
    }
    private void autoRecognized(ScreenDocument doc,LocalQuestionLocator.Candidate candidate,ScreenQaAccessibilityService.Snapshot sourceNodes) {
        if(textInputInFlight||tapInFlight||navigationTarget!=null||navigationTouchBusy)return; // Don't invalidate a pending action from transient styling.
        if(doc.tooLarge()) {
            invalidateQuestion();showState(OverlayState.ERROR,"页面文字过多 · 请切到单题或手动选区","暂不提交此页，避免题目被截断。");return;
        }
        String candidateKey=candidate==null?"":stemKey(candidate.document,candidate.stem);
        boolean sameAnswered=!lastAnsweredKey.isEmpty()&&
                ((candidate!=null?candidateKey.equals(lastAnsweredKey):
                        !lastAnsweredStem.isEmpty()&&!NavigationPolicy.stemProof(doc,lastAnsweredStem).isEmpty())||
                        (lastTextIdentity!=null&&lastTextIdentity.matches(candidate==null?doc:candidate.document)));
        if(sameAnswered) {
            if(nextPending&&autoState==AutoState.WAITING_PAGE_CHANGE)
                QaLog.event("PAGE unchanged question="+shortId(lastAnsweredKey)+" awaiting_navigation");
            return; // Selected styling and progress counters must not trigger another AI request.
        }
        if(nextPending&&candidate==null&&navigationAttempts==0)return;
        if(retryCachedAnswer(doc,sourceNodes))return;
        // Our own scroll may hide the stem. After a next tap the existing AI locator may confirm the new PWA question.
        if(!candidateKey.isEmpty()&&!currentQuestionKey.isEmpty()&&!candidateKey.equals(currentQuestionKey)) {
            tapInFlight=false;textInputInFlight=false;pendingTap=null;visionTapFallback=null;
            lastTextIdentity=null;
            QaLog.event("PAGE changed previous="+shortId(currentQuestionKey)+" next="+shortId(candidateKey));
        }
        if(nextPending&&candidate!=null) {
            QaLog.event("NEXT_QUESTION detected source="+(sourceNodes==null?"VISION_OCR":"ACCESSIBILITY")+
                    " question="+shortId(candidateKey));
            nextPending=false;navigationAttempts=0;scrollAttempts=0;
            closeAnswerPopup(false);setAutoState(AutoState.DETECTING,"new_question_detected");
        }
        if(!candidateKey.isEmpty())currentQuestionKey=candidateKey;
        latestDocument=doc;
        int tokenNow=tracker.observe(doc.fingerprint());
        if(tokenNow!=activeToken) {
            if(request!=null){request.cancel();request=null;}
            activeToken=tokenNow;lastDetection=null;clearOutline();
            setAutoState(AutoState.DETECTING,"question_fingerprint_changed");
            questionStartedAt=SystemClock.elapsedRealtime();
            QaLog.event("QUESTION detected id="+shortId(candidateKey.isEmpty()?doc.fingerprint():candidateKey)+
                    " source="+(sourceNodes==null?"VISION_OCR":"ACCESSIBILITY")+
                    " stem="+(!candidateKey.isEmpty())+" option_lines="+
                    (candidate==null?"unknown":candidate.all.size()-candidate.stem.size()));
            showState(OverlayState.SEARCHING,doc.lines.isEmpty()?"未读到文字 · 请露出题目，移开悬浮窗":"自动模式 · 等待文字稳定","正在定位当前题目…");
        } else if(lastDetection!=null && lastDetection.found) {
            showOutline(doc,lastDetection);
        }
        long now=SystemClock.elapsedRealtime();
        if(!tracker.ready(now) || now-lastAutoRequest<900) return;
        lastAutoRequest=now;
        final int token=tracker.begin(),submissionEpoch=epoch;
        final int editableHint=ScreenQaAccessibilityService.visibleEditableCount();
            final ApiRequest call=new ApiRequest(this,sourceNodes==null?"ocr":"accessibility").attempt(tracker.attemptCount());request=call;
        aiStartedAt=now;setAutoState(AutoState.REQUESTING_AI,"question_ready");
        QaLog.event("DEEPSEEK start question="+shortId(candidateKey.isEmpty()?doc.fingerprint():candidateKey)+
                " source="+(sourceNodes==null?"VISION_OCR":"ACCESSIBILITY"));
        showState(OverlayState.ANALYZING,candidate==null?"正在快速定位题目…":"已在本机定位 · 正在快速作答…","正在获取答案…");
        if(candidate!=null){lastDetection=QuestionDetection.located(candidate);showOutline(doc,lastDetection);}
        network.execute(() -> {
            QuestionDetection detection=null;String error=null;
            try {detection=candidate==null?call.detect(apiKey,doc,editableHint):call.solve(apiKey,candidate);}
            catch(Exception e){error=ApiRequest.error(e);}
            final QuestionDetection result=detection;final String failure=error;
            main.post(() -> {
                if(destroyed || paused || !autoMode || submissionEpoch!=epoch || !tracker.complete(token,result!=null,SystemClock.elapsedRealtime()))return;
                request=null;
                QaLog.event("DEEPSEEK end success="+(result!=null)+" elapsed_ms="+
                        (SystemClock.elapsedRealtime()-aiStartedAt)+" error="+(result==null?failure:"none"));
                if(result==null){setAutoState(AutoState.DETECTING,"ai_failed");
                    showState(OverlayState.ERROR,"自动定位失败 · 将重试；手动框选见高级设置",failure);return;}
                lastDetection=result;
                if(!result.found){setAutoState(AutoState.DETECTING,"ai_found_no_question");
                    clearOutline();showState(OverlayState.SEARCHING,"当前未找到题目 · 持续监测","未发现可识别题目；请露出题干，或到高级设置使用手动框选。");return;}
                String resolvedKey=stemKey(doc,result.stemIds);
                if(nextPending&&resolvedKey.equals(lastAnsweredKey)){
                    QaLog.event("ANSWER duplicate_suppressed reason=answered_stem_still_visible");return;
                }
                if(nextPending&&!resolvedKey.isEmpty()&&!resolvedKey.equals(lastAnsweredKey)){
                    nextPending=false;navigationTarget=null;closeAnswerPopup(false);
                    QaLog.event("NEXT_QUESTION confirmed source=AI_LOCATED_STEM");
                }
                if(!currentQuestionKey.isEmpty()&&!currentQuestionKey.equals(resolvedKey)) {
                    tapInFlight=false;pendingTap=null;visionTapFallback=null;
                }
                currentQuestionKey=resolvedKey;
                QaLog.event("ANSWER received question="+shortId(currentQuestionKey)+
                        " type="+result.type+" complete="+result.complete+
                        " answer="+safeAnswer(result.answer));
                Settings execution=new Settings(this);
                boolean execute=autoSelectEnabled&&execution.autoExecute(result.type);
                QaLog.event("QuestionType="+result.type.toUpperCase(java.util.Locale.ROOT)+
                        " AutoExecuteEnabled="+execute+
                        " ChoiceAutoSelect="+execution.autoExecute("choice")+
                        " TrueFalseAutoSelect="+execution.autoExecute("true_false")+
                        " FillBlankAutoInput="+execution.autoExecute("fill_blank")+
                        " ShortAnswerAutoInput="+execution.autoExecute("short_answer")+
                        " AnswerCount="+result.answers.size()+" AIAnswerLength="+result.answer.length());
                showOutline(doc,result);
                boolean textType="fill_blank".equals(result.type)||"short_answer".equals(result.type);
                if(!textType)lastTextIdentity=null;
                String detail=textType&&result.complete?TextAnswer.display(result.answers):result.answer;
                inlineChoice=result.complete&&AnswerPresentation.inlineChoice(result.type,result.answer);
                if(result.complete){setAutoState(AutoState.RESOLVING_TARGET,"ai_answer_received");
                    if(!execute){QaLog.event("AutoExecute skipped: disabled for "+result.type.toUpperCase(java.util.Locale.ROOT));
                        lastAnsweredKey=currentQuestionKey;lastAnsweredStem=currentQuestionKey;
                        if(textType)lastTextIdentity=TextQuestionIdentity.from(doc,result.stemIds);
                        setAutoState(AutoState.STOPPED,"auto_execute_disabled_waiting_user");}
                    else if(textType)maybeFillText(result,doc,submissionEpoch);
                    else maybeAutoSelect(result.type,result.answer,doc,result.questionIds,result.stemIds,sourceNodes,token,submissionEpoch);}
                else setAutoState(AutoState.STOPPED,"question_incomplete");
                showAnswer(textType?result.typeName():AnswerPresentation.compact(result.type,result.answer),detail,
                        result.complete?"已定位"+result.typeName()+" · 点答案查看":"已定位"+result.typeName()+" · 条件不完整",
                        textType&&result.complete?TextAnswer.copyPayload(result.answers):"");
            });
        });
    }
    private void maybeFillText(QuestionDetection result,ScreenDocument doc,int requestEpoch) {
        ScreenQaAccessibilityService service=ScreenQaAccessibilityService.active;
        String expectedKey=currentQuestionKey;
        int settingsGeneration=executionGeneration;
        lastTextIdentity=TextQuestionIdentity.from(doc,result.stemIds);
        if(service==null||result.answers.isEmpty()||expectedKey.isEmpty()) {
            QaLog.event("TextInput skipped reason="+(service==null?"accessibility_disconnected":"missing_answer_or_question"));
            lastAnsweredKey=expectedKey;lastAnsweredStem=expectedKey;
            setAutoState(AutoState.STOPPED,"text_input_unavailable");return;
        }
        textInputInFlight=true;
        setAutoState(AutoState.FILLING_TEXT,"text_answer_ready");
        QaLog.event("TextInput start question="+shortId(expectedKey)+" QuestionType="+
                result.type.toUpperCase(java.util.Locale.ROOT)+" AnswerCount="+result.answers.size()+
                " AIAnswerLength="+result.answer.length());
        service.fillTextIfCurrent(doc,result.stemIds,result.questionIds,result.answers,
                ()->autoSelectEnabled&&new Settings(this).autoExecute(result.type)&&
                        settingsGeneration==executionGeneration&&!paused&&!destroyed&&requestEpoch==epoch&&
                        currentQuestionKey.equals(expectedKey),
                outcome -> {
                    if(destroyed||paused||requestEpoch!=epoch||settingsGeneration!=executionGeneration||
                            !currentQuestionKey.equals(expectedKey)) {
                        QaLog.event("TextInput callback_discarded reason=question_changed");return;
                    }
                    textInputInFlight=false;
                    QaLog.event("TextInput "+(outcome.success?"success":"failed")+" reason="+outcome.reason+
                            " InputMethod="+outcome.method+" TargetInputCount="+outcome.targets+
                            " completed="+outcome.completed);
                    if(outcome.success&&autoSelectEnabled&&new Settings(this).autoExecute(result.type)) {
                        if(needsPopup(shortAnswer))openAnswerPopup();
                        QaLog.event("NextQuestion transition reason=text_input_verified question="+shortId(expectedKey));
                        if(autoMode)beginWaitForNext(expectedKey,requestEpoch);
                    } else {
                        lastAnsweredKey=expectedKey;lastAnsweredStem=expectedKey;
                        setAutoState(AutoState.STOPPED,"text_input_failed_or_disabled_waiting_user");
                        status.setText("答案已显示 · 可复制后手动填写");
                        if(needsPopup(shortAnswer))openAnswerPopup();
                    }
                });
    }
    private static boolean sameQuestionText(ScreenDocument vision,List<Integer> visionStem,
            LocalQuestionLocator.Candidate nodes) {
        if(visionStem==null||visionStem.isEmpty())return false;
        String a=QuestionTracker.normalize(vision.text(visionStem));
        String b=QuestionTracker.normalize(nodes.document.text(nodes.stem));
        return a.length()>=8&&b.length()>=8&&
                (a.equals(b)||((a.contains(b)||b.contains(a))&&Math.min(a.length(),b.length())*5>=Math.max(a.length(),b.length())*4));
    }
    private void maybeAutoSelect(String type,String output,ScreenDocument doc,List<Integer> questionIds,
            List<Integer> stemIds,ScreenQaAccessibilityService.Snapshot sourceNodes,int token,int requestEpoch) {
        if(!new Settings(this).autoExecute(type)) {
            QaLog.event("AutoExecute skipped: disabled for "+type.toUpperCase(java.util.Locale.ROOT));
            setAutoState(AutoState.STOPPED,"auto_execute_disabled_waiting_user");return;
        }
        if(!autoSelectEnabled||paused||destroyed||!TouchExecutor.available(this)||
                (!"choice".equals(type)&&!"true_false".equals(type))) {
            QaLog.event("AUTO_CLICK skipped reason="+(!autoSelectEnabled?"switch_off":
                    !TouchExecutor.available(this)?"no_touch_permission":
                    "unsupported_type_or_inactive")+" type="+type);
            setAutoState(AutoState.STOPPED,"auto_click_not_applicable");return;
        }
        tapQuestionKey=stemKey(doc,stemIds);
        if(tapQuestionKey.isEmpty())tapQuestionKey=currentQuestionKey;
        if(answerRetry==null||answerRetry.epoch!=epoch||answerRetry.generation!=executionGeneration||
                !answerRetry.key.equals(tapQuestionKey))
            answerRetry=new AnswerRetry(type,output,tapQuestionKey,epoch,executionGeneration,SystemClock.elapsedRealtime());
        AnswerTargetResolver.Target boxed=AnswerTargetResolver.resolve(type,output,doc,questionIds,stemIds);
        AnswerTargetResolver.Target nearby=AnswerTargetResolver.resolveNearStem(type,output,doc,stemIds);
        if(boxed!=null&&nearby!=null&&boxed.lineId!=nearby.lineId) {
            QaLog.event("AUTO_CLICK skipped reason=box_nearby_conflict question="+shortId(tapQuestionKey));
            setAutoState(AutoState.STOPPED,"ambiguous_target_location");return;
        }
        AnswerTargetResolver.Target target=nearby!=null?nearby:boxed;
        List<Integer> verifiedIds=new ArrayList<>();
        if(stemIds!=null)verifiedIds.addAll(stemIds);
        List<Integer> nearbyIds=AnswerTargetResolver.nearbyOptionIds(type,doc,stemIds);
        if(!nearbyIds.isEmpty()) {
            for(int id:nearbyIds)if(!verifiedIds.contains(id))verifiedIds.add(id);
        } else {
            for(int id:questionIds)if(!verifiedIds.contains(id))verifiedIds.add(id);
        }
        if(target!=null&&!verifiedIds.contains(target.lineId))verifiedIds.add(target.lineId);
        if(target==null){QaLog.event("TARGET_RESOLVER failed question="+shortId(tapQuestionKey)+
                " reason=no_unique_option answer="+safeAnswer(output)+
                " boxed_labels="+AnswerTargetResolver.labeledOptionCount(type,doc,questionIds)+
                " nearby_labels="+nearbyIds.size()+" stem_lines="+(stemIds==null?0:stemIds.size()));
            setAutoState(AutoState.STOPPED,"target_not_unique");return;}
        QaLog.event("TARGET_RESOLVER matched question="+shortId(tapQuestionKey)+
                " line="+target.lineId+" source="+(nearby!=null?"nearby_options":"ai_box")+
                " box="+doc.lines.get(target.lineId-1).left+","+
                doc.lines.get(target.lineId-1).top+","+
                doc.lines.get(target.lineId-1).right+","+
                doc.lines.get(target.lineId-1).bottom+
                " verified_lines="+verifiedIds.size()+" answer="+safeAnswer(output));
        ScreenQaAccessibilityService service=ScreenQaAccessibilityService.active;
        ScreenQaAccessibilityService.Snapshot nodes=sourceNodes;
        if(nodes==null) {
            ScreenQaAccessibilityService.Snapshot visible=ScreenQaAccessibilityService.current();
            if(visible!=null&&sameQuestionText(doc,stemIds,visible.candidate)) {
                AnswerTargetResolver.Target nodeTarget=AnswerTargetResolver.resolve(type,output,
                        visible.candidate.document,visible.candidate.all,visible.candidate.stem);
                if(nodeTarget!=null&&QuestionTracker.normalize(nodeTarget.text).equals(QuestionTracker.normalize(target.text)))nodes=visible;
            }
        }
        if(nodes!=null) {
            tapInFlight=true;
            setAutoState(AutoState.CLICKING_ANSWER,"accessibility_target");
            final boolean fromVision=sourceNodes==null;
            final String clickKey=tapQuestionKey;
            final int settingsGeneration=executionGeneration;
            service.clickIfCurrent(nodes,type,output,panelRect(),popupRect(),
                    ()->autoSelectEnabled&&new Settings(this).autoExecute(type)&&
                            settingsGeneration==executionGeneration&&!paused&&!destroyed&&requestEpoch==epoch&&
                            currentQuestionKey.equals(clickKey),outcome -> {
                if(destroyed||paused||!autoSelectEnabled||!new Settings(this).autoExecute(type)||
                        settingsGeneration!=executionGeneration||requestEpoch!=epoch||
                        !currentQuestionKey.equals(clickKey)) {
                    QaLog.event("AUTO_CLICK callback_discarded reason=session_or_question_changed");return;
                }
                boolean clicked=outcome.accepted;
                if(!clicked&&!outcome.allowFallback){completeTap(false,token,requestEpoch,outcome.uncertain);return;}
                if(!clicked&&fromVision&&scheduleCoordinate(doc,target,verifiedIds,token,requestEpoch))return;
                if(!clicked&&!fromVision&&autoMode) {
                    visionTapFallback=new VisionTapFallback(sourceNodes,type,output,token,requestEpoch);
                    lastFrame=0;return;
                }
                completeTap(clicked,token,requestEpoch);
            });
        } else if(scheduleCoordinate(doc,target,verifiedIds,token,requestEpoch)) {
            setAutoState(AutoState.CLICKING_ANSWER,"vision_coordinate_target");
        } else {QaLog.event("AUTO_CLICK skipped reason=no_trusted_node_or_coordinate question="+shortId(tapQuestionKey));
            setAutoState(AutoState.STOPPED,"no_trusted_target");}
    }
    private boolean scheduleCoordinate(ScreenDocument doc,AnswerTargetResolver.Target target,
            List<Integer> ids,int token,int requestEpoch) {
        if(ids.size()>35)return false;
        for(int id:ids)if(id<1||id>doc.lines.size()||doc.lines.get(id-1).visualSignature==0)return false;
        pendingTap=new PendingTap(doc,target,new ArrayList<>(ids),token,requestEpoch,false);
        tapInFlight=true;requestFreshFrame();return true;
    }
    private boolean retryCachedAnswer(ScreenDocument doc,ScreenQaAccessibilityService.Snapshot nodes){
        AnswerRetry retry=answerRetry;
        if(retry==null||!retry.pending||!autoSelectEnabled||!new Settings(this).autoExecute(retry.type)||!TouchExecutor.available(this))return false;
        List<ScreenDocument.Line> proof=NavigationPolicy.stemProof(doc,retry.key);
        List<Integer> stem=new ArrayList<>();
        for(ScreenDocument.Line line:proof)stem.add(doc.lines.indexOf(line)+1);
        if(!retry.canRetry(SystemClock.elapsedRealtime(),epoch,executionGeneration,stemKey(doc,stem)))return false;
        retry.retrying();QaLog.event("ANSWER retry=cached_ai_fresh_ocr attempt="+retry.attempts);
        maybeAutoSelect(retry.type,retry.answer,doc,AnswerTargetResolver.everyLine(doc),stem,nodes,activeToken,epoch);
        return true;
    }
    private void finishVisionFallback(ScreenDocument doc,LocalQuestionLocator.Candidate candidate) {
        VisionTapFallback fallback=visionTapFallback;visionTapFallback=null;
        if(fallback==null)return;
        if(doc==null||candidate==null||!autoSelectEnabled||!new Settings(this).autoExecute(fallback.type)||fallback.epoch!=epoch||
                !currentQuestionKey.equals(tapQuestionKey)||SystemClock.elapsedRealtime()-fallback.created>=3000||
                !sameQuestionText(doc,candidate.stem,fallback.source.candidate)) {
            completeTap(false,fallback.token,fallback.epoch);return;
        }
        AnswerTargetResolver.Target target=AnswerTargetResolver.resolve(fallback.type,fallback.answer,
                doc,candidate.all,candidate.stem);
        if(target==null||!scheduleCoordinate(doc,target,candidate.all,fallback.token,fallback.epoch))
            completeTap(false,fallback.token,fallback.epoch);
    }
    private void finishPendingTap(Image current) {
        PendingTap plan=pendingTap;pendingTap=null;
        if(plan==null)return;
        final int generation=executionGeneration;
        boolean valid=answerPlanAllowed(plan,generation)&&matchesFrame(current,plan.doc,linesFor(plan));
        if(!valid){QaLog.event("TOUCH guard_rejected=answer_frame_or_session");completeTap(false,plan.token,plan.epoch);return;}
        actionProof.observe(SystemClock.elapsedRealtime(),true);rootTapPending=plan;
        ScreenDocument.Line line=plan.doc.lines.get(plan.target.lineId-1);
        ScreenQaAccessibilityService service=ScreenQaAccessibilityService.active;
        Rect control=panelRect(),popup=popupRect();
        TouchExecutor.executeAsync(this,TouchAction.tap((line.left+line.right)/2,(line.top+line.bottom)/2,plan.doc.width,plan.doc.height),
                ()->verifyActionFrame(plan.doc,linesFor(plan),()->answerPlanAllowed(plan,generation)),
                service==null?null:()->service.ocrNode(line,control,popup,()->answerPlanAllowed(plan,generation)),outcome->{
            if(rootTapPending==plan)rootTapPending=null;
            if(!answerSessionAllowed(plan,generation))return;
            completeTap(outcome.accepted,plan.token,plan.epoch,outcome.uncertain);
        });
    }
    private List<ScreenDocument.Line> linesFor(PendingTap plan){
        List<ScreenDocument.Line> lines=new ArrayList<>();
        for(int id:plan.questionIds)lines.add(plan.doc.lines.get(id-1));return lines;
    }
    private boolean answerPlanAllowed(PendingTap plan,int generation){
        return answerSessionAllowed(plan,generation)&&SystemClock.elapsedRealtime()-plan.created<3000;
    }
    private boolean answerSessionAllowed(PendingTap plan,int generation){
        return !destroyed&&!paused&&autoSelectEnabled&&lastDetection!=null&&new Settings(this).autoExecute(lastDetection.type)&&
                plan.epoch==epoch&&generation==executionGeneration&&currentQuestionKey.equals(tapQuestionKey)&&
                width==plan.doc.width&&height==plan.doc.height;
    }
    private boolean matchesFrame(Image image,ScreenDocument doc,List<ScreenDocument.Line> lines){
        if(image.getWidth()!=doc.width||image.getHeight()!=doc.height||lines.isEmpty())return false;
        for(ScreenDocument.Line line:lines){
            Rect box=new Rect(line.left,line.top,line.right,line.bottom);
            if(line.visualSignature==0||Rect.intersects(box,panelRect())||Rect.intersects(box,popupRect())||
                    VisualSignature.fromImage(image,box)!=line.visualSignature){
                QaLog.event("TOUCH guard_rejected=signature_or_overlay line_box="+box.flattenToString());return false;
            }
        }return true;
    }
    private boolean verifyActionFrame(ScreenDocument doc,List<ScreenDocument.Line> lines,java.util.function.BooleanSupplier state){
        if(Looper.myLooper()==Looper.getMainLooper())return checkActionFrame(doc,lines,state);
        java.util.concurrent.CompletableFuture<Boolean> result=new java.util.concurrent.CompletableFuture<>();
        main.post(()->{
            if(result.isDone())return;
            result.complete(checkActionFrame(doc,lines,state));
        });
        try{return result.get(450,java.util.concurrent.TimeUnit.MILLISECONDS);}
        catch(InterruptedException e){Thread.currentThread().interrupt();}
        catch(java.util.concurrent.ExecutionException|java.util.concurrent.TimeoutException e){ }
        result.cancel(false);return false;
    }
    private boolean checkActionFrame(ScreenDocument doc,List<ScreenDocument.Line> lines,java.util.function.BooleanSupplier state){
        Image image=null;boolean valid=false;
        try{
            if(state.getAsBoolean()){
                image=reader.acquireLatestImage();
                if(image!=null){lastFrameArrival=SystemClock.elapsedRealtime();valid=matchesFrame(image,doc,lines);
                    actionProof.observe(SystemClock.elapsedRealtime(),valid);}
                else {
                    valid=actionProof.recent(SystemClock.elapsedRealtime());
                    if(valid)for(ScreenDocument.Line line:lines){
                        Rect box=new Rect(line.left,line.top,line.right,line.bottom);
                        if(Rect.intersects(box,panelRect())||Rect.intersects(box,popupRect())){valid=false;break;}
                    }
                }
                if(!valid)QaLog.event("TOUCH guard_rejected="+(image==null?"no_recent_frame":"changed_frame"));
            }else QaLog.event("TOUCH guard_rejected=session_or_settings");
        }catch(RuntimeException e){QaLog.event("TOUCH guard_rejected=reader_exception");}
        finally{if(image!=null)image.close();}
        return valid;
    }
    private void completeTap(boolean clicked,int token,int requestEpoch) {
        completeTap(clicked,token,requestEpoch,false);
    }
    private void completeTap(boolean clicked,int token,int requestEpoch,boolean uncertain) {
        if(destroyed||requestEpoch!=epoch||!currentQuestionKey.equals(tapQuestionKey))return;
        tapInFlight=false;
        if(answerRetry!=null){
            if(!clicked)answerRetry.failed(SystemClock.elapsedRealtime(),uncertain);else answerRetry.pending=false;
        }
        if(uncertain){lastAnsweredKey=tapQuestionKey;lastAnsweredStem=tapQuestionKey;}
        QaLog.event("AUTO_CLICK complete question="+shortId(tapQuestionKey)+" dispatch_accepted="+clicked+
                " selection_confirmed=unknown total_ms="+(SystemClock.elapsedRealtime()-questionStartedAt));
        if(clicked&&autoMode&&autoSelectEnabled&&lastDetection!=null&&
                new Settings(this).autoExecute(lastDetection.type))beginWaitForNext(tapQuestionKey,requestEpoch);
        else if(!clicked)setAutoState(AutoState.STOPPED,"click_rejected_manual_answer_visible");
        if(overlayState==OverlayState.ANSWER) {
            status.setText(uncertain?"触摸结果不确定 · 请手动确认":
                    clicked?"已发送自动选择操作 · 继续监测":"答案已显示 · 请手动选择");
            if(!clicked&&needsPopup(shortAnswer))openAnswerPopup();
        }
    }
    private void beginWaitForNext(String key,int requestEpoch) {
        lastAnsweredKey=key;lastAnsweredStem=key;
        if(!new Settings(this).autoNext()) {
            nextPending=false;setAutoState(AutoState.STOPPED,"auto_next_disabled_waiting_user");
            status.setText("答案已展示 · 请手动切题");return;
        }
        nextPending=true;
        navigationAttempts=0;scrollAttempts=0;navigationRetries=0;
        navigationClickSignature=null;navigationTarget=null;
        clearOutline();
        navigationEvidenceAfter=SystemClock.elapsedRealtime()+250;
        navigationEvidenceWait=SystemClock.elapsedRealtime();
        latestOcrDocument=null;lastFrame=0;requestFreshFrame();
        setAutoState(AutoState.WAITING_PAGE_CHANGE,lastTextIdentity==null?"answer_click_accepted":"text_input_verified");
        QaLog.event("PAGE wait_for_change question="+shortId(key));
        main.postDelayed(() -> {
            if(nextPending&&requestEpoch==epoch&&currentQuestionKey.equals(key))
                attemptNavigation(key,requestEpoch,null);
        },450);
        // A large detail card can hide OCR text. The answer remains on the bubble.
        main.postDelayed(() -> {
            if(nextPending&&requestEpoch==epoch&&answerPopup!=null)closeAnswerPopup(false);
        },MIN_ANSWER_VISIBLE_MS);
    }
    private void attemptNavigation(String key,int requestEpoch,String priorSignature) {
        if(!nextPending||paused||destroyed||!autoSelectEnabled||!autoMode||requestEpoch!=epoch||
                !currentQuestionKey.equals(key))return;
        ScreenQaAccessibilityService service=ScreenQaAccessibilityService.active;
        if(navigationAttempts>=2) {
            QaLog.event("NEXT stopped reason=button_click_did_not_change_question");
            nextPending=false;setAutoState(AutoState.STOPPED,"no_page_change_after_next_click");return;
        }
        if(service==null){
            if(TouchExecutor.rootAvailable(this)&&scheduleNavigationFromOcr(key,requestEpoch))return;
            if(TouchExecutor.rootAvailable(this)){
                waitForNavigationEvidence(key,requestEpoch,null);return;}
            QaLog.event("NEXT stopped reason=no_verified_touch_path");
            nextPending=false;setAutoState(AutoState.STOPPED,"navigation_unavailable");
            status.setText("无法自动触摸 · 请启用无障碍或 Root；仍显示答案");return;
        }
        if(navigationAttempts>0&&navigationClickSignature!=null&&navigationClickSignature.startsWith("ocr:")){
            if(scheduleNavigationFromOcr(key,requestEpoch))return;
            if(navigationRetries++<4){main.postDelayed(()->attemptNavigation(key,requestEpoch,null),350);return;}
            nextPending=false;setAutoState(AutoState.STOPPED,"ocr_next_retry_not_verified");return;
        }
        setAutoState(AutoState.FINDING_NEXT,"page_still_on_answered_question");
        if(lastTextIdentity!=null)closeAnswerPopup(false); // Verified input no longer needs an obstructing card.
        final int navigationGeneration=executionGeneration;
        final ScreenDocument beforeScroll=latestOcrDocument;
        service.findNext(key,lastTextIdentity,priorSignature,scrollAttempts<2,
                navigationAttempts>0&&navigationClickSignature!=null&&navigationClickSignature.startsWith("node:")?
                        navigationClickSignature.substring(5):null,panelRect(),popupRect(),
                () -> nextPending&&!paused&&!destroyed&&autoSelectEnabled&&requestEpoch==epoch&&
                        navigationGeneration==executionGeneration&&currentQuestionKey.equals(key),result -> {
            if(!nextPending||paused||destroyed||requestEpoch!=epoch||navigationGeneration!=executionGeneration||!currentQuestionKey.equals(key))return;
            QaLog.event("NEXT result="+result.action+" reason="+result.reason+
                    " scroll_count="+scrollAttempts);
            switch(result.action) {
                case "TERMINAL" -> {
                    nextPending=false;setAutoState(AutoState.STOPPED,"terminal_visible");
                    status.setText("本题已答 · 请手动查看成绩或提交");
                }
                case "PAGE_CHANGED" -> {
                    if(navigationAttempts==0){
                        // OCR can differ from node text. Before a next touch this alone is not page-change proof.
                        if(!scheduleNavigationFromOcr(key,requestEpoch))waitForNavigationEvidence(key,requestEpoch,priorSignature);
                        break;
                    }
                    nextPending=false;closeAnswerPopup(false);
                    setAutoState(AutoState.DETECTING,"page_changed_after_click");lastFrame=0;
                }
                case "NEXT_CLICKED" -> {
                    navigationAttempts++;navigationClickSignature="node:"+result.signature;
                    setAutoState(AutoState.WAITING_PAGE_CHANGE,"next_button_clicked");
                    main.postDelayed(() -> attemptNavigation(key,requestEpoch,null),1400);
                }
                case "RETRY" -> {
                    if(scheduleNavigationFromOcr(key,requestEpoch))break;
                    waitForNavigationEvidence(key,requestEpoch,priorSignature);
                }
                case "UNCERTAIN" -> {
                    nextPending=false;setAutoState(AutoState.STOPPED,"navigation_touch_uncertain");
                    status.setText("下一题触摸结果不确定 · 请手动确认");
                }
                case "SCROLLED" -> {
                    scrollAttempts++;
                    ScreenDocument context=beforeScroll!=null?beforeScroll:latestDocument;
                    if(context!=null&&(!NavigationPolicy.stemProof(context,key).isEmpty())){
                        scrollContext=context;scrollContextAt=SystemClock.elapsedRealtime();
                    }
                    awaitPostScrollEvidence();
                    setAutoState(AutoState.SCROLLING,"searching_below_answered_question");
                    main.postDelayed(() -> {
                        if(nextPending&&requestEpoch==epoch){lastFrame=0;
                            attemptNavigation(key,requestEpoch,result.signature);}
                    },450);
                }
                default -> {
                    if(scheduleNavigationFromOcr(key,requestEpoch))break;
                    if(scrollAttempts>0&&navigationAttempts==0){waitForNavigationEvidence(key,requestEpoch,priorSignature);break;}
                    nextPending=false;setAutoState(AutoState.STOPPED,result.reason);
                    status.setText("未确认下一题按钮 · 请手动切题，仍在监测");
                }
            }
        });
    }
    private void awaitPostScrollEvidence(){
        clearOutline();latestOcrDocument=null;lastFrame=0;
        long now=SystemClock.elapsedRealtime();navigationEvidenceAfter=now+350;navigationEvidenceWait=now;
        requestFreshFrame();
    }
    private void waitForNavigationEvidence(String key,int requestEpoch,String priorSignature){
        long now=SystemClock.elapsedRealtime();
        if(now-navigationEvidenceWait<6000){
            lastFrame=0;requestFreshFrame();
            main.postDelayed(()->attemptNavigation(key,requestEpoch,priorSignature),400);
        }else{nextPending=false;setAutoState(AutoState.STOPPED,"navigation_evidence_timeout");
            status.setText("未能核验导航画面 · 请手动切题，仍在监测");}
    }
    private boolean scheduleNavigationFromOcr(String key,int requestEpoch){
        if(navigationTarget!=null||navigationTouchBusy)return true;
        ScreenDocument doc=latestOcrDocument;long now=SystemClock.elapsedRealtime();
        if(doc==null||doc.tooLarge()||now-latestOcrAt>2500||latestOcrAt<navigationEvidenceAfter){
            QaLog.event("NEXT OCR unavailable=awaiting_post_action_frame");return false;}
        List<ScreenDocument.Line> proof=NavigationPolicy.stemProof(doc,key);
        boolean same=!proof.isEmpty();
        boolean continued=!same&&scrollAttempts>0&&scrollContext!=null&&now-scrollContextAt<8000&&
                NavigationPolicy.scrollContinuation(scrollContext,doc);
        if(!same&&!continued){QaLog.event("NEXT OCR unavailable=question_evidence_unknown");return false;}
        String signature="ocr:"+doc.fingerprint();
        if(navigationAttempts>0&&!NavigationPolicy.mayRetryClick(navigationAttempts,same,
                signature.equals(navigationClickSignature)))return false;
        ScreenDocument.Line target=NavigationPolicy.uniqueNext(doc);
        TouchAction action;
        if(target!=null){
            Rect box=new Rect(target.left,target.top,target.right,target.bottom);
            if(Rect.intersects(box,panelRect())||Rect.intersects(box,popupRect())){
                if(answerPopup!=null){closeAnswerPopup(false);lastFrame=0;}return false;
            }
            action=TouchAction.tap(box.centerX(),box.centerY(),width,height);
            if(continued)proof=NavigationPolicy.continuationProof(scrollContext,doc);
            proof=new ArrayList<>(proof);proof.add(target);
        }else{
            // Never scroll into a submit/results operation, or after an unconfirmed next tap.
            if(scrollAttempts>=2||navigationAttempts>0||NavigationPolicy.terminalVisible(doc))return false;
            closeAnswerPopup(false);
            Rect viewport=contentBounds(width,height);
            action=TouchGeometry.scroll(viewport.left,viewport.top,viewport.right,viewport.bottom,panelRect(),popupRect());
            if(action==null)return false;
            action=TouchAction.swipe(action.x,action.y,action.endX,action.endY,action.duration,width,height);
            if(continued)proof=NavigationPolicy.continuationProof(scrollContext,doc);
            target=proof.get(0); // Pending identity only; actual action uses the viewport-derived swipe.
        }
        navigationFrame=doc;navigationTarget=target;navigationAction=action;navigationChecks=proof;
        navigationEpoch=requestEpoch;navigationGeneration=executionGeneration;navigationCreated=now;lastFrame=0;
        QaLog.event("NEXT OCR plan="+(action.swipe?"scroll":"next")+" stem_visible="+same+" continuation="+continued);
        requestFreshFrame();
        final ScreenDocument.Line identity=target;
        main.postDelayed(()->{
            if(navigationTarget==identity){navigationTarget=null;
                if(navigationAttempts==0&&nextPending&&requestEpoch==epoch){
                    latestOcrDocument=null;navigationEvidenceAfter=SystemClock.elapsedRealtime();
                    waitForNavigationEvidence(key,requestEpoch,null);return;}
                nextPending=false;
                setAutoState(AutoState.STOPPED,"navigation_no_fresh_frame");
                status.setText("无法复核下一题画面 · 请手动切题，仍在监测");}
        },1200);
        return true;
    }
    private void finishNavigationTap(Image image){
        ScreenDocument.Line target=navigationTarget;navigationTarget=null;
        ScreenDocument doc=navigationFrame;TouchAction action=navigationAction;
        List<ScreenDocument.Line> checks=new ArrayList<>(navigationChecks);
        final String key=currentQuestionKey;final int requestEpoch=navigationEpoch,generation=navigationGeneration;
        java.util.function.BooleanSupplier state=()->!destroyed&&!paused&&nextPending&&requestEpoch==epoch&&
                generation==executionGeneration&&autoSelectEnabled&&lastDetection!=null&&new Settings(this).autoExecute(lastDetection.type)&&
                currentQuestionKey.equals(key)&&doc.width==width&&doc.height==height&&
                SystemClock.elapsedRealtime()-navigationCreated<3500;
        boolean permitted=state.getAsBoolean();
        boolean valid=permitted&&matchesFrame(image,doc,checks);
        if(!valid){
            if(permitted&&navigationAttempts==0){
                QaLog.event("NEXT OCR retry=fresh_observation_before_dispatch");
                latestOcrDocument=null;navigationEvidenceAfter=SystemClock.elapsedRealtime();
                waitForNavigationEvidence(key,requestEpoch,null);return;}
            nextPending=false;setAutoState(AutoState.STOPPED,"ocr_navigation_not_verified");
            status.setText("导航画面已变化 · 请手动切题，仍在监测");return;}
        navigationTouchBusy=true;actionProof.observe(SystemClock.elapsedRealtime(),true);
        ScreenQaAccessibilityService service=ScreenQaAccessibilityService.active;
        Rect control=panelRect(),popup=popupRect();
        TouchExecutor.executeAsync(this,action,()->verifyActionFrame(doc,checks,state),
                action.swipe||service==null?null:()->service.ocrNode(target,control,popup,state),outcome->{
            if(requestEpoch!=epoch||generation!=executionGeneration)return;
            navigationTouchBusy=false;
            if(destroyed||paused||!autoSelectEnabled||!nextPending||!currentQuestionKey.equals(key))return;
            QaLog.event("NEXT OCR action="+(action.swipe?"scroll":"tap")+" accepted="+outcome.accepted+" uncertain="+outcome.uncertain);
            if(outcome.accepted){
                if(action.swipe){scrollAttempts++;scrollContext=doc;scrollContextAt=SystemClock.elapsedRealtime();
                    awaitPostScrollEvidence();
                    setAutoState(AutoState.SCROLLING,"ocr_scroll_dispatched");lastFrame=0;
                    main.postDelayed(()->attemptNavigation(key,requestEpoch,null),1000);
                }else{navigationAttempts++;navigationClickSignature="ocr:"+doc.fingerprint();
                    setAutoState(AutoState.WAITING_PAGE_CHANGE,"ocr_next_dispatched_not_yet_confirmed");
                    main.postDelayed(()->attemptNavigation(key,requestEpoch,null),1800);
                }
            }else{nextPending=false;setAutoState(AutoState.STOPPED,"navigation_touch_not_completed");
                status.setText(outcome.uncertain?"导航触摸结果不确定 · 请手动确认":"下一题未执行 · 请手动切题，仍在监测");}
        });
    }
    private void recognized(String text,ScreenDocument manualDocument) {
        if(text.length()>10000) {invalidateQuestion();showState(OverlayState.ERROR,"选区文字过多，请缩小到一道题","请重新选区");return;}
        currentQuestionKey=QuestionTracker.normalize(text);
        int before=tracker.observe(text);
        // activeToken only matches the currently displayed or pending question.
        if(before!=activeToken) {
            if(request!=null) {request.cancel();request=null;}
            questionStartedAt=SystemClock.elapsedRealtime();
            activeToken=before;showState(OverlayState.SEARCHING,
                    text.trim().length()<6?"未识别到文字 · 检查选区和截图权限":"题目变化 · 等待画面稳定","等待当前题目…");
        }
        if(!tracker.ready(SystemClock.elapsedRealtime())) return;
        final int token=tracker.begin();final int submissionEpoch=epoch;
        final ApiRequest call=new ApiRequest(this,"manual_region").attempt(tracker.attemptCount());request=call;
        aiStartedAt=SystemClock.elapsedRealtime();setAutoState(AutoState.REQUESTING_AI,"manual_question_ready");
        QaLog.event("DEEPSEEK start question="+shortId(currentQuestionKey)+" source=VISION_OCR_manual");
        showState(OverlayState.ANALYZING,"正在解答 · 换题会自动更新","模型正在分析…");
        network.execute(() -> {
            String result;boolean success;
            try {result=call.run(apiKey,text,false);success=true;}
            catch(Exception e) {result=ApiRequest.error(e);success=false;}
            final String output=result;final boolean ok=success;
            main.post(() -> {
                if(destroyed || paused || submissionEpoch!=epoch || !tracker.complete(token,ok,SystemClock.elapsedRealtime())) return;
                request=null;
                QaLog.event("DEEPSEEK end success="+ok+" elapsed_ms="+
                        (SystemClock.elapsedRealtime()-aiStartedAt)+" answer="+(ok?safeAnswer(output):"none"));
                if(ok){String type=LocalQuestionLocator.type(text);
                    if(type.isEmpty()&&output.trim().matches("[A-H]{1,8}"))type="choice";
                    inlineChoice=AnswerPresentation.inlineChoice(type,output);
                    maybeAutoSelect(type,output,manualDocument,AnswerTargetResolver.everyLine(manualDocument),null,null,token,submissionEpoch);
                    showAnswer(AnswerPresentation.compact(type,output),output,"已回答 · 持续监测下一题");}
                else showState(OverlayState.ERROR,"失败 · 最多重试2次；暂停再开始可重试",output);
            });
        });
    }
    private int activeToken=-1;
    private void resize(int w,int h) {
        closeAnswerPopup(false);paused=true;invalidateQuestion();region=null;width=w;height=h;
        if(selector!=null) closeSelector();
        if(toggle!=null) {updateToggleIcon();showState(OverlayState.PAUSED,autoMode?"屏幕尺寸变化 · 点开始重新自动找题":"屏幕尺寸变化 · 请重新选区","已暂停");}
        try {
            ImageReader old=reader;reader=newReader(w,h);
            display.resize(w,h,dpi);display.setSurface(reader.getSurface());old.close();
            if(panel!=null) {panelParams.x=dp(8);panelParams.y=dp(48);panelParams.width=Math.min(dp(316),w-dp(16));windows.updateViewLayout(panel,panelParams);}
            if(bubble!=null){bubbleParams.x=dp(8);bubbleParams.y=dp(48);windows.updateViewLayout(bubble,bubbleParams);}
        } catch(Exception e) {stopSelf();}
    }
    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if(display!=null) {DisplayMetrics m=new DisplayMetrics();windows.getDefaultDisplay().getRealMetrics(m);if(m.widthPixels!=width || m.heightPixels!=height)resize(m.widthPixels,m.heightPixels);}
        applyTheme();
    }
    @Override public void onDestroy() {
        QaLog.event("CAPTURE stopped state="+autoState+" last_question="+shortId(currentQuestionKey));
        destroyed=true;active=false;publishAnswer("");invalidateQuestion();
        if(themeListener!=null){new Settings(this).prefs.unregisterOnSharedPreferenceChangeListener(themeListener);themeListener=null;}
        ScreenQaAccessibilityService.setTracking(false);
        closeAnswerPopup(false);
        if(selector!=null) {try {windows.removeView(selector);}catch(Exception ignored){}selector=null;}
        if(panel!=null) {try {windows.removeView(panel);}catch(Exception ignored){}panel=null;}
        if(bubble!=null){try{windows.removeView(bubble);}catch(Exception ignored){}bubble=null;}
        if(display!=null)display.release();if(reader!=null)reader.close();
        if(projection!=null) {projection.unregisterCallback(projectionCallback);projection.stop();}
        if(!ocrBusy)finishImaging();network.shutdownNow();apiKey=null;
        stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) {return null;}
}
