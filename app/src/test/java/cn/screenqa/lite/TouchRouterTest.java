package cn.screenqa.lite;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import static org.junit.Assert.*;

public class TouchRouterTest {
    private TouchRouter.Backend backend(List<Integer> calls,int id,AnswerClickOutcome result){return ()->{calls.add(id);return result;};}
    @Test public void autoUsesNodeThenRootThenGesture(){
        List<Integer> calls=new ArrayList<>();
        AnswerClickOutcome result=TouchRouter.execute(TouchPriority.AUTO,()->true,
                backend(calls,0,AnswerClickOutcome.accessibility(false)),backend(calls,1,AnswerClickOutcome.accessibility(false)),
                backend(calls,2,AnswerClickOutcome.accessibility(true)));
        assertEquals(Arrays.asList(0,1,2),calls);assertTrue(result.accepted);
    }
    @Test public void rootFirstWorksWithoutAnyAccessibilityBackend(){
        List<Integer> calls=new ArrayList<>();
        assertTrue(TouchRouter.execute(TouchPriority.ROOT_FIRST,()->true,null,
                backend(calls,1,AnswerClickOutcome.accessibility(true)),null).accepted);
        assertEquals(Collections.singletonList(1),calls);
    }
    @Test public void accessibilityFirstOnlyUsesRootAfterGestureRejection(){
        List<Integer> calls=new ArrayList<>();
        TouchRouter.execute(TouchPriority.ACCESSIBILITY_FIRST,()->true,backend(calls,0,AnswerClickOutcome.accessibility(false)),
                backend(calls,1,AnswerClickOutcome.accessibility(true)),backend(calls,2,AnswerClickOutcome.accessibility(false)));
        assertEquals(Arrays.asList(0,2,1),calls);
    }
    @Test public void nodeSuccessDoesNotDoubleTapThroughRoot(){
        List<Integer> calls=new ArrayList<>();
        TouchRouter.execute(TouchPriority.AUTO,()->true,backend(calls,0,AnswerClickOutcome.accessibility(true)),
                backend(calls,1,AnswerClickOutcome.accessibility(true)),backend(calls,2,AnswerClickOutcome.accessibility(true)));
        assertEquals(Collections.singletonList(0),calls);
    }
    @Test public void denialBeforeDispatchFallsBackToGesture(){
        List<Integer> calls=new ArrayList<>();
        assertTrue(TouchRouter.execute(TouchPriority.AUTO,()->true,null,
                backend(calls,1,AnswerClickOutcome.root(new RootShell.Result(RootShell.Status.DENIED,false))),
                backend(calls,2,AnswerClickOutcome.accessibility(true))).accepted);
        assertEquals(Arrays.asList(1,2),calls);
    }
    @Test public void timeoutAfterRootDispatchNeverFallsBack(){
        List<Integer> calls=new ArrayList<>();
        AnswerClickOutcome result=TouchRouter.execute(TouchPriority.AUTO,()->true,null,
                backend(calls,1,AnswerClickOutcome.root(new RootShell.Result(RootShell.Status.TIMEOUT,true))),
                backend(calls,2,AnswerClickOutcome.accessibility(true)));
        assertTrue(result.uncertain);assertEquals(Collections.singletonList(1),calls);
    }
    @Test public void cancelledGestureNeverInvokesRootAgain(){
        List<Integer> calls=new ArrayList<>();
        assertTrue(TouchRouter.execute(TouchPriority.ACCESSIBILITY_FIRST,()->true,null,
                backend(calls,1,AnswerClickOutcome.accessibility(true)),backend(calls,2,AnswerClickOutcome.uncertain())).uncertain);
        assertEquals(Collections.singletonList(2),calls);
    }
    @Test public void changedSettingsBetweenBackendsCancelTheAction(){
        AtomicBoolean allowed=new AtomicBoolean(true);List<Integer> calls=new ArrayList<>();
        AnswerClickOutcome result=TouchRouter.execute(TouchPriority.AUTO,allowed::get,()->{
            calls.add(0);allowed.set(false);return AnswerClickOutcome.accessibility(false);
        },backend(calls,1,AnswerClickOutcome.accessibility(true)),null);
        assertFalse(result.accepted);assertFalse(result.allowFallback);assertEquals(Collections.singletonList(0),calls);
    }
    @Test public void noPermissionReturnsManualFailure(){
        AnswerClickOutcome result=TouchRouter.execute(TouchPriority.AUTO,()->true,null,null,null);
        assertFalse(result.accepted);assertFalse(result.uncertain);
    }
    @Test public void backendExceptionIsNotRetriedAsPossiblyDispatched(){
        List<Integer> calls=new ArrayList<>();
        assertTrue(TouchRouter.execute(TouchPriority.AUTO,()->true,()->{throw new IllegalStateException();},
                backend(calls,1,AnswerClickOutcome.accessibility(true)),null).uncertain);
        assertTrue(calls.isEmpty());
    }
    @Test public void invalidSavedPriorityDefaultsToAuto(){
        assertEquals(TouchPriority.AUTO,TouchPriority.fromStored(-1));assertEquals(TouchPriority.AUTO,TouchPriority.fromStored(99));
        int[] order=TouchPriority.AUTO.order();order[0]=2;assertEquals(0,TouchPriority.AUTO.order()[0]);
    }
    @Test public void fixedSwipeCommandUsesCurrentScreenBounds(){
        assertEquals("/system/bin/input touchscreen swipe 1200 700 1200 300 360",
                TouchAction.swipe(1200,700,1200,300,360,1600,900).command());
        assertEquals("/system/bin/input touchscreen tap 299 699",TouchAction.tap(299,699,300,700).command());
        for(int[] values:new int[][]{{0,0,300,200,360},{0,0,0,-1,360},{0,0,10,20,1001},{0,0,10,20,1}}){
            try{TouchAction.swipe(values[0],values[1],values[2],values[3],values[4],300,700);fail();}
            catch(IllegalArgumentException expected){ }
        }
    }
}
