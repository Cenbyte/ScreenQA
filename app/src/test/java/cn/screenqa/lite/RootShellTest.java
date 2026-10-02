package cn.screenqa.lite;

import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class RootShellTest {
    private static class FakeProcess extends Process {
        final ByteArrayOutputStream input=new ByteArrayOutputStream();
        final InputStream output;
        final int exit;
        final boolean finish;
        boolean destroyed;
        FakeProcess(String response,int exit,boolean finish){
            output=new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8));
            this.exit=exit;this.finish=finish;
        }
        public OutputStream getOutputStream(){return input;}
        public InputStream getInputStream(){return output;}
        public InputStream getErrorStream(){return new ByteArrayInputStream(new byte[0]);}
        public int waitFor(){return exit;}
        public boolean waitFor(long timeout,TimeUnit unit){return finish;}
        public int exitValue(){return exit;}
        public boolean isAlive(){return !destroyed;}
        public void destroy(){destroyed=true;}
        public Process destroyForcibly(){destroy();return this;}
        String sent(){return new String(input.toByteArray(),StandardCharsets.UTF_8);}
    }
    @Test public void missingSuIsSafe(){
        RootShell.Result r=new RootShell(()->{throw new IOException("missing");}).execute("ACTION",100,()->true);
        assertEquals(RootShell.Status.UNAVAILABLE,r.status);assertFalse(r.mayHaveExecuted);
    }
    @Test public void rejectionNeverQueuesAction(){
        FakeProcess p=new FakeProcess("permission denied",1,true);
        RootShell.Result r=new RootShell(()->p).execute("ACTION",100,()->true);
        assertEquals(RootShell.Status.DENIED,r.status);assertFalse(p.sent().contains("ACTION"));
        assertTrue(p.destroyed);assertFalse(r.mayHaveExecuted);
    }
    @Test public void firstGuardAvoidsStartingSu(){
        AtomicInteger starts=new AtomicInteger();
        RootShell.Result r=new RootShell(()->{starts.incrementAndGet();return null;}).execute("ACTION",100,()->false);
        assertEquals(RootShell.Status.CANCELLED,r.status);assertEquals(0,starts.get());
    }
    @Test public void timeoutWhileWaitingForPermissionNeverSendsAction() throws Exception {
        PipedOutputStream writer=new PipedOutputStream();PipedInputStream waiting=new PipedInputStream(writer);
        FakeProcess p=new FakeProcess("",0,false){public InputStream getInputStream(){return waiting;}};
        RootShell.Result r=new RootShell(()->p).execute("ACTION",30,()->true);
        writer.close();assertEquals(RootShell.Status.TIMEOUT,r.status);
        assertFalse(r.mayHaveExecuted);assertFalse(p.sent().contains("ACTION"));assertTrue(p.destroyed);
    }
    @Test public void interruptionCancelsWithoutCrashingOrSendingAction(){
        FakeProcess p=new FakeProcess(RootShell.READY,0,true);
        try {
            Thread.currentThread().interrupt();
            RootShell.Result r=new RootShell(()->p).execute("ACTION",100,()->true);
            assertEquals(RootShell.Status.CANCELLED,r.status);assertFalse(r.mayHaveExecuted);
            assertFalse(p.sent().contains("ACTION"));assertTrue(Thread.currentThread().isInterrupted());
        }finally{Thread.interrupted();}
    }
    @Test public void changedScreenAfterGrantCancelsBeforeSending(){
        FakeProcess p=new FakeProcess(RootShell.READY,0,true);AtomicInteger checks=new AtomicInteger();
        RootShell.Result r=new RootShell(()->p).execute("ACTION",100,()->checks.incrementAndGet()==1);
        assertEquals(RootShell.Status.CANCELLED,r.status);assertFalse(r.mayHaveExecuted);
        assertFalse(p.sent().contains("ACTION"));assertEquals(2,checks.get());
    }
    @Test public void successfulActionIsSentOnlyAfterRootHandshake(){
        FakeProcess p=new FakeProcess(RootShell.READY,0,true);
        RootShell.Result r=new RootShell(()->p).execute("ACTION",100,()->true);
        assertTrue(r.success());assertTrue(p.sent().indexOf("id -u")<p.sent().indexOf("ACTION"));
        assertTrue(p.destroyed);
    }
    @Test public void finalScreenGuardRunsOnceAfterPermissionHandshake(){
        FakeProcess p=new FakeProcess(RootShell.READY,0,true);AtomicInteger checks=new AtomicInteger();
        RootShell.Result r=new RootShell(()->p).execute("ACTION",100,()->true,
                ()->{checks.incrementAndGet();assertFalse(p.sent().contains("ACTION"));return true;});
        assertTrue(r.success());assertEquals(1,checks.get());
    }
    @Test public void failedCommandMustNotFallBackToAnotherTap(){
        FakeProcess p=new FakeProcess(RootShell.READY,17,true);
        RootShell.Result r=new RootShell(()->p).execute("ACTION",100,()->true);
        assertEquals(RootShell.Status.FAILED,r.status);assertTrue(r.mayHaveExecuted);
        AnswerClickOutcome o=AnswerClickOutcome.root(r);assertFalse(o.allowFallback);assertTrue(o.uncertain);
    }
    @Test public void timeoutAfterDispatchMustNotRepeatTap(){
        FakeProcess p=new FakeProcess(RootShell.READY,0,false);
        RootShell.Result r=new RootShell(()->p).execute("ACTION",100,()->true);
        assertEquals(RootShell.Status.TIMEOUT,r.status);assertTrue(r.mayHaveExecuted);
        assertFalse(AnswerClickOutcome.root(r).allowFallback);
    }
    @Test public void deniedAndUnavailableAllowOriginalExecutor(){
        for(RootShell.Status s:new RootShell.Status[]{RootShell.Status.DENIED,RootShell.Status.UNAVAILABLE,RootShell.Status.TIMEOUT}){
            AnswerClickOutcome o=AnswerClickOutcome.root(new RootShell.Result(s,false));
            assertFalse(o.accepted);assertTrue(o.allowFallback);assertFalse(o.uncertain);
        }
    }
    @Test public void staleCancellationNeverUsesOldTarget(){
        assertFalse(AnswerClickOutcome.root(new RootShell.Result(RootShell.Status.CANCELLED,false)).allowFallback);
        assertTrue(AnswerClickOutcome.accessibility(true).accepted);
        assertFalse(AnswerClickOutcome.accessibility(true).allowFallback);
    }
    @Test public void realHostShellExercisesHandshakeAndDrain() throws Exception {
        File bash=new File("C:/Program Files/Git/bin/bash.exe");
        org.junit.Assume.assumeTrue(bash.isFile());
        RootShell shell=new RootShell(()->new ProcessBuilder(bash.getPath(),"--noprofile","--norc","-c",
                "id() { printf '0\\n'; }; export -f id; bash --noprofile --norc").start());
        assertTrue(shell.execute("printf 'ordinary output\\n'; true",3000,()->true).success());
        RootShell.Result failed=shell.execute("false",3000,()->true);
        assertEquals(RootShell.Status.FAILED,failed.status);
        RootShell.Result timeout=shell.execute("sleep 1",100,()->true);
        assertEquals(RootShell.Status.TIMEOUT,timeout.status);
    }
}
