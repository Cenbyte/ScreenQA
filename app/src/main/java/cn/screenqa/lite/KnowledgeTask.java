package cn.screenqa.lite;

import org.json.JSONObject;
import java.util.Locale;

/** Immutable, shared installation truth; counts are local to the actual current step. */
final class KnowledgeTask {
    enum Step {
        DOWNLOAD(1,"下载知识包",true), VALIDATE(2,"校验文件",false), EXTRACT(3,"解压知识包",true),
        IMPORT(4,"导入数据并建立索引",false), INDEX(5,"整理并检查索引",false), FINISH(6,"完成安装",false);
        final int number;final String title;final boolean bytes;
        Step(int number,String title,boolean bytes){this.number=number;this.title=title;this.bytes=bytes;}
        static Step of(int number){return values()[Math.max(1,Math.min(6,number))-1];}
    }
    enum Status { IDLE, RUNNING, FAILED, COMPLETE }
    static final int TOTAL=6;
    final Step step;final Status status;final String name,detail;final long done,total,records;final boolean local;
    KnowledgeTask(Step step,Status status,String name,String detail,long done,long total,long records,boolean local){
        this.step=step;this.status=status;this.name=name;this.detail=detail;this.done=done;this.total=total;this.records=records;this.local=local;
    }
    static KnowledgeTask idle(){return new KnowledgeTask(Step.DOWNLOAD,Status.IDLE,"","选择知识包后开始安装",0,-1,0,false);}
    static KnowledgeTask begin(String name,boolean local){return new KnowledgeTask(Step.DOWNLOAD,Status.RUNNING,name,local?"正在读取选中的知识包":"连接下载服务器…",0,-1,0,local);}
    KnowledgeTask advance(Step next,String detail,long done,long total){return new KnowledgeTask(next,Status.RUNNING,name,detail,done,total,records,local);}
    KnowledgeTask named(String name){return new KnowledgeTask(step,status,name,detail,done,total,records,local);}
    KnowledgeTask fail(String reason){return new KnowledgeTask(step,Status.FAILED,name,reason,done,total,records,local);}
    KnowledgeTask complete(long records){return new KnowledgeTask(Step.FINISH,Status.COMPLETE,name,"知识库安装完成",records,records,records,local);}
    KnowledgeTask completeExisting(long records){return new KnowledgeTask(Step.FINISH,Status.COMPLETE,name,"知识包已安装，无需重复安装",records,records,records,local);}
    boolean reused(){return status==Status.COMPLETE && detail.equals("知识包已安装，无需重复安装");}
    boolean busy(){return status==Status.RUNNING;}
    int percent(){return total>0 && done>=0 && done<=total?(int)Math.min(100,100.0*done/total):-1;}
    String title(Step value){return local && value==Step.DOWNLOAD?"读取本地知识包":value.title;}
    String heading(){return status==Status.COMPLETE?(reused()?"✓ 知识包已安装":"✓ 知识库安装完成"):status==Status.FAILED?"安装失败 · 第 "+step.number+" / 6 步":status==Status.IDLE?"安装流程 · 共 6 步":"第 "+step.number+" / 6 步";}
    String count(){
        if(status==Status.COMPLETE)return (reused()?"无需重复安装 · 共 ":"6 / 6 步已完成 · 共导入 ")+number(records)+" 条数据";
        if(status==Status.IDLE)return "下载到安装，共 6 步";
        String prefix=step.bytes?String.format(Locale.ROOT,"%.2f MiB",done/1048576.0):number(done);
        if(total>0)return prefix+" / "+(step.bytes?String.format(Locale.ROOT,"%.2f MiB",total/1048576.0):number(total))+(step.bytes?"":" 条")+(percent()>=0?" · "+percent()+"%":"");
        return (done>0?"已处理 "+prefix+" · ":"")+"进度暂不可计算";
    }
    static String number(long value){return String.format(Locale.ROOT,"%,d",value);}
    JSONObject json()throws Exception{return new JSONObject().put("step",step.number).put("status",status.name()).put("name",name).put("detail",detail).put("done",done).put("total",total).put("records",records).put("local",local);}
    static KnowledgeTask restore(String text){try{JSONObject j=new JSONObject(text);Status status=Status.valueOf(j.getString("status"));KnowledgeTask result=new KnowledgeTask(Step.of(j.getInt("step")),status,j.getString("name"),j.getString("detail"),j.getLong("done"),j.getLong("total"),j.optLong("records"),j.optBoolean("local"));return result.busy()?result.fail("上次安装被中断，请重试"):result;}catch(Exception invalid){return idle();}}
}
