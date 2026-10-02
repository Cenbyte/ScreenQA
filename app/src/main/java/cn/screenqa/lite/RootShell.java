package cn.screenqa.lite;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** The only su boundary. Commands are sent only after uid=0 and a fresh action guard. */
final class RootShell {
    enum Status { SUCCESS, UNAVAILABLE, DENIED, TIMEOUT, FAILED, CANCELLED }
    static final class Result {
        final Status status;
        final boolean mayHaveExecuted;
        Result(Status status,boolean mayHaveExecuted){this.status=status;this.mayHaveExecuted=mayHaveExecuted;}
        boolean success(){return status==Status.SUCCESS;}
    }
    interface ProcessFactory { Process start() throws IOException; }
    static final String READY="__SCREENQA_ROOT_READY__";
    private final ProcessFactory factory;
    RootShell(){this(()->new ProcessBuilder("su","-c","/system/bin/sh").start());}
    RootShell(ProcessFactory factory){this.factory=factory;}

    Result execute(String command,long timeoutMillis,BooleanSupplier allowed) {
        return execute(command,timeoutMillis,allowed,allowed);
    }
    synchronized Result execute(String command,long timeoutMillis,BooleanSupplier readyToStart,
            BooleanSupplier allowed) {
        Process process=null;boolean sent=false;
        long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        try {
            if(!readyToStart.getAsBoolean())return new Result(Status.CANCELLED,false);
            process=factory.start();
            CountDownLatch handshake=new CountDownLatch(1);
            AtomicBoolean root=new AtomicBoolean();
            drain(process.getInputStream(),handshake,root);
            drain(process.getErrorStream(),null,null);
            OutputStream input=process.getOutputStream();
            input.write(("if [ \"$(id -u)\" = '0' ]; then printf '"+READY+
                    "\\n'; else exit 77; fi\n").getBytes(StandardCharsets.UTF_8));
            input.flush();
            if(!handshake.await(remaining(deadline),TimeUnit.NANOSECONDS))
                return new Result(Status.TIMEOUT,false);
            if(!root.get())return new Result(Status.DENIED,false);
            // su permission dialogs may have delayed us: never queue an old screen tap.
            if(!allowed.getAsBoolean())return new Result(Status.CANCELLED,false);
            if(remaining(deadline)==0)return new Result(Status.TIMEOUT,false);
            sent=true; // Conservative even if an I/O error interrupts writing the command.
            input.write((command+"\n_screenqa_rc=$?\nexit \"$_screenqa_rc\"\n")
                    .getBytes(StandardCharsets.UTF_8));
            input.flush();input.close();
            if(!process.waitFor(remaining(deadline),TimeUnit.NANOSECONDS))
                return new Result(Status.TIMEOUT,true);
            return new Result(process.exitValue()==0?Status.SUCCESS:Status.FAILED,true);
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();return new Result(Status.CANCELLED,sent);
        } catch(IOException|RuntimeException e) {
            return new Result(process==null?Status.UNAVAILABLE:Status.FAILED,sent);
        } finally {
            if(process!=null){
                try {if(process.isAlive())process.destroyForcibly();}catch(RuntimeException ignored){}
                close(process.getOutputStream());close(process.getInputStream());close(process.getErrorStream());
            }
        }
    }
    private static long remaining(long deadline){return Math.max(0,deadline-System.nanoTime());}
    private static void close(Closeable stream){try{stream.close();}catch(IOException ignored){}}
    private static void drain(InputStream stream,CountDownLatch handshake,AtomicBoolean root) {
        Thread reader=new Thread(()->{
            byte[] buffer=new byte[512];StringBuilder tail=new StringBuilder();
            try {
                int count;while((count=stream.read(buffer))!=-1) {
                    if(handshake!=null&&!root.get()) {
                        tail.append(new String(buffer,0,count,StandardCharsets.UTF_8));
                        if(tail.indexOf(READY)>=0){root.set(true);handshake.countDown();}
                        if(tail.length()>1024)tail.delete(0,tail.length()-128);
                    }
                }
            } catch(IOException ignored){}
            finally{if(handshake!=null)handshake.countDown();}
        },"screenqa-root-output");
        reader.setDaemon(true);reader.start();
    }
}
