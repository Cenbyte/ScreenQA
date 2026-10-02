package cn.screenqa.lite;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Bounded node extraction. A fresh tree is checked immediately before any action. */
public final class ScreenQaAccessibilityService extends AccessibilityService {
    static volatile ScreenQaAccessibilityService active;
    static volatile boolean trackingEnabled;
    static final class Snapshot {
        final String packageName;
        final LocalQuestionLocator.Candidate candidate;
        final String fingerprint;
        Snapshot(String pkg,LocalQuestionLocator.Candidate candidate) {
            this.packageName=pkg;this.candidate=candidate;fingerprint=candidate.document.fingerprint();
        }
    }
    interface ClickResult {void done(AnswerClickOutcome outcome);}
    static final class TextInputResult {
        final boolean success;final String reason,method;final int targets,completed;
        TextInputResult(boolean success,String reason,String method,int targets,int completed) {
            this.success=success;this.reason=reason;this.method=method;this.targets=targets;this.completed=completed;
        }
    }
    private static final class Record {
        final AccessibilityNodeInfo node;
        final ScreenDocument.Line line;
        Record(AccessibilityNodeInfo node,ScreenDocument.Line line){this.node=node;this.line=line;}
    }
    private static final class Visit {
        final AccessibilityNodeInfo node;final int depth;
        Visit(AccessibilityNodeInfo node,int depth){this.node=node;this.depth=depth;}
    }
    private static final class Capture {
        final boolean truncated;final String packageName;final Snapshot snapshot;final List<Record> records;final List<AccessibilityNodeInfo> owned;
        Capture(String packageName,Snapshot snapshot,List<Record> records,List<AccessibilityNodeInfo> owned,boolean truncated){
            this.truncated=truncated;
            this.packageName=packageName;this.snapshot=snapshot;this.records=records;this.owned=owned;
        }
        void release(){for(AccessibilityNodeInfo node:owned)node.recycle();}
    }
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicBoolean scanning=new AtomicBoolean();
    private volatile Snapshot latest;
    private volatile int editableCount;
    private volatile long version;
    private volatile long lastSecondLook=Long.MIN_VALUE;
    private volatile String lastScanSummary="";
    private volatile long lastScanLogAt;
    private volatile long lastScanCompletedAt;
    static Snapshot current() {
        ScreenQaAccessibilityService service=active;
        // WebViews can omit content events. Bound snapshot age instead of trusting events alone.
        if(service!=null&&trackingEnabled&&SystemClock.elapsedRealtime()-service.lastScanCompletedAt>=1000)
            service.scheduleScan();
        // Keep a recent completed scan while refreshing; executions still read and guard a fresh tree.
        // Returning null here made static screens alternate between node and OCR identities forever.
        return service!=null&&trackingEnabled&&SystemClock.elapsedRealtime()-service.lastScanCompletedAt<2500?
                service.latest:null;
    }
    static int visibleEditableCount() {
        ScreenQaAccessibilityService service=active;
        return service!=null&&trackingEnabled&&!service.scanning.get()?service.editableCount:0;
    }
    static boolean isScanning(){ScreenQaAccessibilityService s=active;return s!=null&&s.scanning.get();}
    static boolean requestSecondLook() {
        ScreenQaAccessibilityService s=active;
        if(s==null||s.scanning.get()||s.lastSecondLook==s.version)return false;
        s.lastSecondLook=s.version;s.scheduleScan();return true;
    }
    static void setTracking(boolean value) {
        trackingEnabled=value;
        ScreenQaAccessibilityService service=active;
        if(service!=null){if(value)service.scheduleScan();else {service.latest=null;service.editableCount=0;}}
    }
    @Override protected void onServiceConnected() {
        active=this;QaLog.start(this);QaLog.event("ACCESSIBILITY connected tracking="+trackingEnabled);
        if(trackingEnabled)scheduleScan();
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if(!trackingEnabled||event==null)return;
        CharSequence pkg=event.getPackageName();
        if(pkg!=null&&getPackageName().contentEquals(pkg))return;
        version++;latest=null;editableCount=0;scheduleScan();
    }
    @Override public void onInterrupt() {latest=null;QaLog.event("ACCESSIBILITY interrupted");}
    @Override public void onDestroy() {
        QaLog.event("ACCESSIBILITY disconnected");
        if(active==this)active=null;latest=null;worker.shutdownNow();super.onDestroy();
    }
    private void scheduleScan() {
        if(!scanning.compareAndSet(false,true))return;
        worker.execute(() -> {
            long started=SystemClock.elapsedRealtime();
            long completedVersion=Long.MIN_VALUE;
            boolean changedDuringScan=false;
            try {
                for(int attempt=0;attempt<2;attempt++) {
                    long before=version;
                    Capture capture=readTree();
                    Snapshot next=capture==null?null:capture.snapshot;
                    int editable=0;
                    if(capture!=null)for(AccessibilityNodeInfo node:capture.owned)
                        if(TextAnswerExecutor.editable(node))editable++;
                    if(capture!=null)capture.release();
                    if(before==version){
                        completedVersion=before;
                        latest=next;
                        lastScanCompletedAt=SystemClock.elapsedRealtime();
                        editableCount=editable;
                        String summary=next==null?"no_reliable_question editable="+editable:
                                "question="+Integer.toHexString(next.fingerprint.hashCode())+
                                " type="+next.candidate.type+" options="+(next.candidate.all.size()-next.candidate.stem.size())+
                                " editable="+editable;
                        if(!summary.equals(lastScanSummary)||SystemClock.elapsedRealtime()-lastScanLogAt>3000){
                            lastScanSummary=summary;lastScanLogAt=SystemClock.elapsedRealtime();
                            QaLog.event("ACCESSIBILITY scan "+summary+
                                    " elapsed_ms="+(lastScanLogAt-started));
                        }
                        break;
                    } else changedDuringScan=true;
                }
            } catch(Exception e){latest=null;QaLog.event("ACCESSIBILITY scan_exception="+e.getClass().getSimpleName());}
            finally {
                scanning.set(false);
                if(changedDuringScan&&trackingEnabled&&active==this&&version!=completedVersion) {
                    QaLog.event("ACCESSIBILITY rescan reason=events_during_scan");
                    main.postDelayed(this::scheduleScan,120);
                }
            }
        });
    }
    private Capture readTree() {return readTree(false);}
    private Capture readTree(boolean detailed) {
        int maxNodes=detailed?600:180,maxDepth=detailed?24:14;
        long budgetMs=detailed?300:100;
        // A read used as an execution guard must reach the app, not an obsolete framework cache.
        if(android.os.Build.VERSION.SDK_INT>=33)clearCache();
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null)return null;
        String pkg=root.getPackageName()==null?"":root.getPackageName().toString();
        if(pkg.isEmpty()||pkg.equals(getPackageName())){root.recycle();return null;}
        DisplayMetrics dm=new DisplayMetrics();getSystemService(WindowManager.class).getDefaultDisplay().getRealMetrics(dm);
        ArrayDeque<Visit> queue=new ArrayDeque<>();queue.add(new Visit(root,0));
        List<Record> records=new ArrayList<>();List<AccessibilityNodeInfo> owned=new ArrayList<>();
        Set<String> seen=new HashSet<>();long start=SystemClock.elapsedRealtime();
        boolean truncated=false;
        while(!queue.isEmpty()&&owned.size()<maxNodes&&SystemClock.elapsedRealtime()-start<budgetMs) {
            Visit visit=queue.removeFirst();AccessibilityNodeInfo node=visit.node;owned.add(node);
            Rect box=new Rect();node.getBoundsInScreen(box);
            CharSequence value=node.getText();if(value==null||value.length()==0)value=node.getContentDescription();
            if(value!=null&&value.length()>0&&value.length()<6000&&node.isVisibleToUser()&&
                    box.intersect(0,0,dm.widthPixels,dm.heightPixels)&&box.width()>2&&box.height()>2) {
                String text=value.toString().trim();String key=text+":"+box.flattenToString();
                if(!text.isEmpty()&&seen.add(key))records.add(new Record(node,new ScreenDocument.Line(text,box.left,box.top,box.right,box.bottom)));
            }
            int children=node.getChildCount();
            if(children>0&&visit.depth>=maxDepth)truncated=true;
            if(visit.depth<maxDepth)for(int i=0;i<children;i++) {
                if(queue.size()+owned.size()>=maxNodes){truncated=true;break;}
                AccessibilityNodeInfo child=node.getChild(i);if(child!=null)queue.add(new Visit(child,visit.depth+1));
            }
        }
        truncated|=!queue.isEmpty();
        while(!queue.isEmpty())queue.removeFirst().node.recycle();
        records.sort(Comparator.comparingInt((Record r)->r.line.top).thenComparingInt(r->r.line.left));
        List<ScreenDocument.Line> lines=new ArrayList<>();
        for(Record record:records)if(!TextAnswerExecutor.editable(record.node))lines.add(record.line);
        ScreenDocument doc=new ScreenDocument(lines,dm.widthPixels,dm.heightPixels);
        LocalQuestionLocator.Candidate candidate=LocalQuestionLocator.locate(doc);
        if(candidate==null&&!truncated)candidate=QuizLabLocator.locate(pkg,doc);
        if(candidate!=null) {
            List<Integer> all=candidate.all,stem=candidate.stem;
            boolean usable=candidate.type.equals("choice")&&
                    AnswerTargetResolver.resolve("choice","A",candidate.document,all,stem)!=null&&
                    AnswerTargetResolver.resolve("choice","B",candidate.document,all,stem)!=null&&
                    AnswerTargetResolver.resolve("choice","C",candidate.document,all,stem)!=null&&
                    AnswerTargetResolver.resolve("choice","D",candidate.document,all,stem)!=null;
            usable|=candidate.type.equals("true_false")&&
                    AnswerTargetResolver.resolve("true_false","正确",candidate.document,all,stem)!=null&&
                    AnswerTargetResolver.resolve("true_false","错误",candidate.document,all,stem)!=null;
            if(candidate.type.equals("fill_blank")||candidate.type.equals("short_answer")) {
                boolean hasInput=false;for(AccessibilityNodeInfo node:owned)hasInput|=TextAnswerExecutor.editable(node);
                usable|=hasInput&&TextQuestionIdentity.from(candidate.document,candidate.stem).matches(candidate.document);
            }
            if(usable)return new Capture(pkg,new Snapshot(pkg,candidate),records,owned,truncated);
        }
        return new Capture(pkg,null,records,owned,truncated);
    }
    void fillTextIfCurrent(ScreenDocument document,List<Integer> stemIds,List<Integer> questionIds,
            List<String> answers,java.util.function.BooleanSupplier allowed,Consumer<TextInputResult> callback) {
        try {worker.execute(() -> {
            TextInputResult result=fillText(document,stemIds,questionIds,answers,allowed);
            main.post(() -> callback.accept(result));
        });} catch(java.util.concurrent.RejectedExecutionException e) {
            main.post(() -> callback.accept(new TextInputResult(false,"service_stopped","",0,0)));
        }
    }
    private TextInputResult fillText(ScreenDocument document,List<Integer> stemIds,List<Integer> questionIds,
            List<String> answers,java.util.function.BooleanSupplier allowed) {
        if(document==null||stemIds==null||stemIds.isEmpty()||answers==null||answers.isEmpty())
            return new TextInputResult(false,"missing_question_or_answers","",0,0);
        int top=Integer.MAX_VALUE,bottom=0;
        for(int id:stemIds)if(id>0&&id<=document.lines.size())top=Math.min(top,document.lines.get(id-1).top);
        for(int id:questionIds)if(id>0&&id<=document.lines.size())bottom=Math.max(bottom,document.lines.get(id-1).bottom);
        if(top==Integer.MAX_VALUE)return new TextInputResult(false,"invalid_stem_bounds","",0,0);
        int lower=Math.min(document.height,Math.max(bottom+document.height/4,top+document.height/3));
        int completed=0,targetCount=0;String method="";String expectedPackage="";
        for(int index=0;index<answers.size();index++) {
            Capture capture=null;
            try {
                if(!allowed.getAsBoolean()||!trackingEnabled||active!=this)
                    return new TextInputResult(false,"question_changed","",targetCount,completed);
                capture=readTree(true);
                if(capture==null)return new TextInputResult(false,"no_application_root","",targetCount,completed);
                int inputs=0;for(AccessibilityNodeInfo node:capture.owned)if(TextAnswerExecutor.editable(node))inputs++;
                boolean stemMatches=TextQuestionIdentity.from(document,stemIds).matches(joinText(capture.records));
                QaLog.event("TextInput tree nodes="+capture.owned.size()+" lines="+capture.records.size()+
                        " truncated="+capture.truncated+" editable="+inputs+" stem_match="+stemMatches);
                if(capture.truncated)return new TextInputResult(false,"incomplete_accessibility_tree","",inputs,completed);
                if(!stemMatches)return new TextInputResult(false,"stem_not_matched","",inputs,completed);
                if(index==0)expectedPackage=capture.packageName;
                else if(!expectedPackage.equals(capture.packageName))
                    return new TextInputResult(false,"page_package_changed","",targetCount,completed);
                List<AccessibilityNodeInfo> targets=new ArrayList<>();
                for(AccessibilityNodeInfo node:capture.owned) {
                    if(!TextAnswerExecutor.editable(node))continue;
                    Rect box=new Rect();node.getBoundsInScreen(box);
                    if(box.width()>8&&box.height()>8&&box.centerY()>=top-24&&box.centerY()<=lower)
                        targets.add(node);
                }
                targets.sort((a,b)->{
                    Rect x=new Rect(),y=new Rect();a.getBoundsInScreen(x);b.getBoundsInScreen(y);
                    int row=Integer.compare(x.centerY(),y.centerY());
                    return row!=0?row:Integer.compare(x.centerX(),y.centerX());
                });
                targetCount=targets.size();
                QaLog.event("TextInput InputNodes="+targetCount+" AnswerCount="+answers.size()+
                        " TargetInputCount="+targetCount+" index="+index);
                if(targetCount!=answers.size())
                    return new TextInputResult(false,"input_answer_count_mismatch","",targetCount,completed);
                if(!allowed.getAsBoolean())return new TextInputResult(false,"question_changed","",targetCount,completed);
                String written=TextAnswerExecutor.write(this,targets.get(index),answers.get(index),allowed);
                if(written.isEmpty())return new TextInputResult(false,"write_or_verification_failed","",targetCount,completed);
                method=written;completed++;
                QaLog.event("TextInput success index="+index+" InputMethod="+written+
                        " AIAnswerLength="+answers.get(index).length());
            } catch(Exception e) {
                QaLog.event("TextInput exception="+e.getClass().getSimpleName());
                return new TextInputResult(false,"exception_"+e.getClass().getSimpleName(),"",targetCount,completed);
            } finally {if(capture!=null)capture.release();}
        }
        return new TextInputResult(true,"verified",method,targetCount,completed);
    }
    void clickIfCurrent(Snapshot expected,String type,String answer,Rect avoidControl,Rect avoidAnswer,
            java.util.function.BooleanSupplier allowed,ClickResult callback) {
        try {worker.execute(() -> {
            boolean clicked=false;Capture fresh=null;
            AnswerClickOutcome outcome=AnswerClickOutcome.accessibility(false);
            try {
                fresh=readTree();
                if(fresh!=null&&fresh.snapshot!=null&&expected!=null&&
                        fresh.snapshot.packageName.equals(expected.packageName)&&fresh.snapshot.fingerprint.equals(expected.fingerprint)) {
                    LocalQuestionLocator.Candidate candidate=fresh.snapshot.candidate;
                    AnswerTargetResolver.Target target=AnswerTargetResolver.resolve(type,answer,candidate.document,candidate.all,candidate.stem);
                    AnswerTargetResolver.Target old=AnswerTargetResolver.resolve(type,answer,expected.candidate.document,expected.candidate.all,expected.candidate.stem);
                    if(target!=null&&old!=null&&target.text.equals(old.text)) {
                        ScreenDocument.Line line=candidate.document.lines.get(target.lineId-1);
                        boolean foundNode=false;
                        for(Record record:fresh.records)if(record.line.text.equals(line.text)&&
                                Math.abs(record.line.left-line.left)<6&&Math.abs(record.line.top-line.top)<6) {
                            foundNode=true;
                            if(!allowed.getAsBoolean()||!trackingEnabled||active!=this)break;
                            int x=record.line.left+(record.line.right-record.line.left)/2;
                            int y=record.line.top+(record.line.bottom-record.line.top)/2;
                            if(!avoidControl.contains(x,y)&&!avoidAnswer.contains(x,y)){
                                outcome=TouchExecutor.execute(this,TouchAction.tap(x,y,candidate.document.width,candidate.document.height),
                                        ()->allowed.getAsBoolean()&&trackingEnabled&&active==this&&rootTargetStillCurrent(expected,line),
                                        ()->AnswerClickOutcome.accessibility(clickNodeOrParent(record.node,candidate.document.width,
                                                candidate.document.height,"ANSWER_CLICK")));
                                clicked=outcome.accepted;
                            }
                            break;
                        }
                        if(!foundNode)QaLog.event("ANSWER_CLICK rejected=target_node_missing");
                    } else QaLog.event("ANSWER_CLICK rejected=target_missing_or_changed");
                } else QaLog.event("ANSWER_CLICK rejected=node_stale_or_page_changed");
            } catch(Exception e){QaLog.event("ANSWER_CLICK exception="+e.getClass().getSimpleName());}
            finally {if(fresh!=null)fresh.release();}
            AnswerClickOutcome result=outcome.allowFallback?AnswerClickOutcome.accessibility(clicked):outcome;
            main.post(() -> callback.done(result));
        });} catch(java.util.concurrent.RejectedExecutionException ignored){main.post(() -> callback.done(AnswerClickOutcome.accessibility(false)));}
    }
    private boolean rootTargetStillCurrent(Snapshot expected,ScreenDocument.Line line){
        Capture check=null;
        try {
            check=readTree();
            if(check==null||check.snapshot==null||
                    !check.snapshot.packageName.equals(expected.packageName)||
                    check.snapshot.candidate.document.width!=expected.candidate.document.width||
                    check.snapshot.candidate.document.height!=expected.candidate.document.height||
                    !check.snapshot.fingerprint.equals(expected.fingerprint))return false;
            for(ScreenDocument.Line current:check.snapshot.candidate.document.lines)
                if(current.text.equals(line.text)&&current.left==line.left&&current.top==line.top&&
                        current.right==line.right&&current.bottom==line.bottom)return true;
            return false;
        } catch(RuntimeException e){return false;}
        finally{if(check!=null)check.release();}
    }
    void clickNearOcrTarget(ScreenDocument.Line expected,Rect avoidControl,Rect avoidAnswer,
            java.util.function.BooleanSupplier allowed,ClickResult callback) {
        try {worker.execute(() -> {
            Capture fresh=null;boolean clicked=false;String reason="no_matching_node";
            try {
                fresh=readTree();
                Rect target=new Rect(expected.left,expected.top,expected.right,expected.bottom);
                int x=target.centerX(),y=target.centerY();
                if(!avoidControl.contains(x,y)&&!avoidAnswer.contains(x,y)&&fresh!=null) {
                    String label=AnswerTargetResolver.optionLabel(expected.text);
                    String normalized=expected.text.replaceAll("[^\\p{L}\\p{N}]","").toUpperCase(Locale.ROOT);
                    Record best=null;long bestArea=Long.MAX_VALUE;
                    for(Record record:fresh.records) {
                        Rect box=new Rect(record.line.left,record.line.top,record.line.right,record.line.bottom);
                        if(!box.contains(x,y)||!record.node.isEnabled()||!record.node.isVisibleToUser())continue;
                        String nodeLabel=AnswerTargetResolver.optionLabel(record.line.text);
                        String nodeText=record.line.text.replaceAll("[^\\p{L}\\p{N}]","").toUpperCase(Locale.ROOT);
                        boolean same=label.isEmpty()?
                                (!normalized.isEmpty()&&(nodeText.equals(normalized)||nodeText.contains(normalized))):
                                label.equals(nodeLabel)&&(normalized.length()<=1||nodeText.length()<=1||
                                        nodeText.contains(normalized)||normalized.contains(nodeText));
                        if(!same)continue;
                        long area=(long)box.width()*box.height();
                        if(area<bestArea){best=record;bestArea=area;}
                    }
                    if(best!=null) {
                        reason="matched_node";
                        if(allowed.getAsBoolean()&&trackingEnabled&&active==this)
                            clicked=clickNodeOrParent(best.node,getResources().getDisplayMetrics().widthPixels,
                                    getResources().getDisplayMetrics().heightPixels,"ANSWER_CLICK");
                        else reason="session_changed";
                    }
                } else reason="overlay_or_no_tree";
            } catch(Exception e){reason="exception_"+e.getClass().getSimpleName();}
            finally {if(fresh!=null)fresh.release();}
            QaLog.event("ANSWER_CLICK method=ocr_matched_node accepted="+clicked+" reason="+reason);
            boolean done=clicked;main.post(() -> callback.done(AnswerClickOutcome.accessibility(done)));
        });} catch(java.util.concurrent.RejectedExecutionException e){main.post(() -> callback.done(AnswerClickOutcome.accessibility(false)));}
    }
    private boolean clickNodeOrParent(AccessibilityNodeInfo node,int width,int height,String tag) {
        AccessibilityNodeInfo current=node;
        try {
            for(int depth=0;current!=null&&depth<4;depth++) {
                Rect bounds=new Rect();current.getBoundsInScreen(bounds);
                if(current.isVisibleToUser()&&current.isEnabled()&&current.isClickable()&&
                        bounds.width()>0&&bounds.height()>0&&
                        (long)bounds.width()*bounds.height()<(long)width*height/6) {
                    boolean accepted=current.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    QaLog.event(tag+" method="+(depth==0?"ACTION_CLICK":"parent_click")+
                            " accepted="+accepted+" depth="+depth);
                    if(accepted)return true;
                }
                AccessibilityNodeInfo parent=current.getParent();
                if(current!=node)current.recycle();
                current=parent;
            }
            return false;
        } finally {if(current!=null&&current!=node)current.recycle();}
    }
    boolean tap(int x,int y) {
        if(x<0||y<0||!trackingEnabled||active!=this)return false;
        Path path=new Path();path.moveTo(x,y);
        GestureDescription gesture=new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path,0,70)).build();
        return dispatchGesture(gesture,null,null);
    }
    /** Must be called off main; cancellation after acceptance is not safe to inject again. */
    AnswerClickOutcome gesture(TouchAction action,java.util.function.BooleanSupplier allowed){
        java.util.concurrent.CompletableFuture<AnswerClickOutcome> done=new java.util.concurrent.CompletableFuture<>();
        AtomicBoolean sent=new AtomicBoolean();
        main.post(()->{
            if(done.isDone())return;
            boolean permitted;
            try{permitted=trackingEnabled&&active==this&&allowed.getAsBoolean();}
            catch(RuntimeException e){permitted=false;}
            if(!permitted){done.complete(AnswerClickOutcome.cancelled());return;}
            Path path=new Path();path.moveTo(action.x,action.y);
            if(action.swipe)path.lineTo(action.endX,action.endY);
            try {
                sent.set(true); // Treat a blocked/in-flight Binder dispatch as uncertain until rejected.
                boolean accepted=dispatchGesture(new GestureDescription.Builder().addStroke(
                        new GestureDescription.StrokeDescription(path,0,action.duration)).build(),new GestureResultCallback(){
                    @Override public void onCompleted(GestureDescription gesture){done.complete(AnswerClickOutcome.accessibility(true));}
                    @Override public void onCancelled(GestureDescription gesture){done.complete(AnswerClickOutcome.uncertain());}
                },main);
                sent.set(accepted);
                if(!accepted)done.complete(AnswerClickOutcome.accessibility(false));
            } catch(RuntimeException e){done.complete(AnswerClickOutcome.uncertain());}
        });
        try {return done.get(1600,java.util.concurrent.TimeUnit.MILLISECONDS);}
        catch(InterruptedException e){Thread.currentThread().interrupt();}
        catch(java.util.concurrent.ExecutionException|java.util.concurrent.TimeoutException e){QaLog.event("TOUCH gesture_completion_timeout");}
        // Completing the future also prevents a queued main-thread action from starting late.
        AnswerClickOutcome result=sent.get()?AnswerClickOutcome.uncertain():AnswerClickOutcome.cancelled();
        done.complete(result);return result;
    }
    AnswerClickOutcome ocrNode(ScreenDocument.Line expected,Rect avoidControl,Rect avoidAnswer,
            java.util.function.BooleanSupplier allowed){
        java.util.concurrent.CompletableFuture<AnswerClickOutcome> result=new java.util.concurrent.CompletableFuture<>();
        clickNearOcrTarget(expected,avoidControl,avoidAnswer,()->!result.isDone()&&allowed.getAsBoolean(),result::complete);
        try{return result.get(650,java.util.concurrent.TimeUnit.MILLISECONDS);}
        catch(InterruptedException e){Thread.currentThread().interrupt();}
        catch(java.util.concurrent.ExecutionException|java.util.concurrent.TimeoutException e){ }
        // A racing ACTION_CLICK could already have run: do not double click through another backend.
        result.complete(AnswerClickOutcome.uncertain());return AnswerClickOutcome.uncertain();
    }
    static final class NavigationResult {
        final String action,signature,reason;
        NavigationResult(String action,String signature,String reason) {
            this.action=action;this.signature=signature;this.reason=reason;
        }
    }
    void findNext(String expectedStem,TextQuestionIdentity textIdentity,String previousSignature,boolean mayScroll,
            String retrySignature,Rect avoidControl,Rect avoidAnswer,java.util.function.BooleanSupplier allowed,Consumer<NavigationResult> callback) {
        try {worker.execute(() -> {
            Capture capture=null;NavigationResult result;
            try {
                capture=readTree(textIdentity!=null);
                if(!allowed.getAsBoolean()||!trackingEnabled||active!=this)
                    result=new NavigationResult("STOP","","session_changed");
                else if(capture==null||(textIdentity!=null&&capture.truncated))result=new NavigationResult("RETRY","","no_or_incomplete_root");
                else {
                    String signature=screenSignature(capture.records);
                    String all=QuestionTracker.normalize(joinText(capture.records));
                    if(previousSignature!=null&&previousSignature.equals(signature))
                        result=new NavigationResult("STOP",signature,"scroll_no_page_change");
                    else if(!(textIdentity==null?all.contains(QuestionTracker.normalize(expectedStem)):
                            textIdentity.matches(joinText(capture.records)))) {
                        String newStem=capture.snapshot==null?"":capture.snapshot.candidate.document.text(capture.snapshot.candidate.stem);
                        boolean changed=!capture.truncated&&NavigationPolicy.evidence(expectedStem,all,newStem)==NavigationPolicy.Evidence.DIFFERENT;
                        result=new NavigationResult(changed?"PAGE_CHANGED":"RETRY",signature,
                                changed?"different_question_visible":"question_evidence_unknown");
                    }
                    else {
                        QaLog.event("NEXT_BUTTON search started");
                        if(retrySignature!=null&&!retrySignature.equals(signature)) {
                            result=new NavigationResult("STOP",signature,"retry_screen_changed");
                            NavigationResult delivered=result;main.post(()->callback.accept(delivered));return;
                        }
                        Record button=findNextButton(capture.records,getResources().getDisplayMetrics().heightPixels);
                        if(button!=null) {
                            Rect box=new Rect(button.line.left,button.line.top,button.line.right,button.line.bottom);
                            boolean safe=allowed.getAsBoolean()&&!Rect.intersects(box,avoidControl)&&!Rect.intersects(box,avoidAnswer);
                            DisplayMetrics dm=new DisplayMetrics();getSystemService(WindowManager.class).getDefaultDisplay().getRealMetrics(dm);
                            AnswerClickOutcome touch=safe?TouchExecutor.execute(this,TouchAction.tap(box.centerX(),box.centerY(),dm.widthPixels,dm.heightPixels),
                                    ()->allowed.getAsBoolean()&&navigationStillCurrent(expectedStem,textIdentity,signature),
                                    ()->AnswerClickOutcome.accessibility(clickNodeOrParent(button.node,dm.widthPixels,dm.heightPixels,"NEXT_BUTTON"))):
                                    AnswerClickOutcome.cancelled();
                            boolean accepted=touch.accepted;
                            if(touch.uncertain){
                                NavigationResult delivered=new NavigationResult("UNCERTAIN",signature,"next_touch_uncertain");
                                main.post(()->callback.accept(delivered));return;
                            }
                            QaLog.event("NEXT_BUTTON found=true click_accepted="+accepted);
                            result=new NavigationResult(accepted?"NEXT_CLICKED":"STOP",signature,
                                    accepted?"button_clicked":"button_not_clickable_or_covered");
                        } else {
                            QaLog.event("NEXT_BUTTON found=false");
                            List<ScreenDocument.Line> visible=new ArrayList<>();
                            for(Record record:capture.records)visible.add(record.line);
                            DisplayMetrics metrics=getResources().getDisplayMetrics();
                            if(NavigationPolicy.terminalVisible(new ScreenDocument(visible,metrics.widthPixels,metrics.heightPixels))) {
                                NavigationResult delivered=new NavigationResult("TERMINAL",signature,"terminal_visible");
                                main.post(()->callback.accept(delivered));return;
                            }
                            AnswerClickOutcome scroll=mayScroll&&allowed.getAsBoolean()?scrollForward(capture,avoidControl,avoidAnswer,allowed):
                                    AnswerClickOutcome.cancelled();
                            result=new NavigationResult(scroll.uncertain?"UNCERTAIN":scroll.accepted?"SCROLLED":"STOP",signature,
                                    scroll.uncertain?"scroll_touch_uncertain":scroll.accepted?"scroll_dispatched":"no_reliable_next_or_scroll");
                        }
                    }
                }
            } catch(Exception e){result=new NavigationResult("STOP","","exception_"+e.getClass().getSimpleName());}
            finally {if(capture!=null)capture.release();}
            NavigationResult delivered=result;main.post(() -> callback.accept(delivered));
        });} catch(java.util.concurrent.RejectedExecutionException e) {
            main.post(() -> callback.accept(new NavigationResult("STOP","","service_stopped")));
        }
    }
    private static String joinText(List<Record> records) {
        StringBuilder text=new StringBuilder();for(Record record:records)text.append(record.line.text).append(' ');
        return text.toString();
    }
    private static String screenSignature(List<Record> records) {
        StringBuilder value=new StringBuilder();
        for(Record record:records)value.append(record.line.text).append('@').append(record.line.top).append(';');
        return Integer.toHexString(value.toString().hashCode());
    }
    private static Record findNextButton(List<Record> records,int height) {
        Record best=null;int score=-1,count=0;
        for(Record record:records) {
            int confidence=NextButtonMatcher.confidence(record.line.text,record.line.top>=height/2);
            if(confidence==0||!record.node.isVisibleToUser()||!record.node.isEnabled())continue;
            if(confidence>score){best=record;score=confidence;count=1;}
            else if(confidence==score) {
                Rect a=new Rect(record.line.left,record.line.top,record.line.right,record.line.bottom);
                Rect b=best==null?new Rect():new Rect(best.line.left,best.line.top,best.line.right,best.line.bottom);
                if(!Rect.intersects(a,b))count++; // A parent and its same-label child represent one button.
            }
        }
        return count==1?best:null;
    }
    private boolean navigationStillCurrent(String stem,TextQuestionIdentity identity,String signature){
        Capture check=null;
        try {
            check=readTree(identity!=null);
            return check!=null&&!check.truncated&&signature.equals(screenSignature(check.records))&&
                    (identity==null?QuestionTracker.normalize(joinText(check.records)).contains(QuestionTracker.normalize(stem)):
                            identity.matches(joinText(check.records)));
        }catch(RuntimeException e){return false;}
        finally{if(check!=null)check.release();}
    }
    private AnswerClickOutcome scrollForward(Capture capture,Rect avoidControl,Rect avoidAnswer,java.util.function.BooleanSupplier allowed) {
        DisplayMetrics dm=new DisplayMetrics();getSystemService(WindowManager.class).getDefaultDisplay().getRealMetrics(dm);
        TouchAction action=TouchGeometry.scroll(0,0,dm.widthPixels,dm.heightPixels,avoidControl,avoidAnswer);
        if(action==null)return AnswerClickOutcome.accessibility(false);
        String signature=screenSignature(capture.records);
        AnswerClickOutcome outcome=TouchExecutor.execute(this,action,()->allowed.getAsBoolean()&&trackingEnabled&&active==this&&
                signature.equals(currentTreeSignature()),()->{
            for(AccessibilityNodeInfo node:capture.owned){
                Rect box=new Rect();node.getBoundsInScreen(box);
                if(node.isScrollable()&&node.isVisibleToUser()&&box.height()>dm.heightPixels/3&&box.width()>dm.widthPixels/2&&
                        node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))return AnswerClickOutcome.accessibility(true);
            }
            return AnswerClickOutcome.accessibility(false);
        });
        if(outcome.uncertain)QaLog.event("SCROLL result_uncertain manual_confirmation_required=true");
        return outcome;
    }
    private String currentTreeSignature(){
        Capture check=null;
        try{check=readTree();return check==null?"":screenSignature(check.records);}
        finally{if(check!=null)check.release();}
    }
}
