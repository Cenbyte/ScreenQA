package cn.screenqa.lite;

import org.junit.Test;
import static org.junit.Assert.*;

public class KnowledgeTaskTest {
    @Test public void progressIsRealAndLocalToEachStep(){
        KnowledgeTask task=KnowledgeTask.begin("k12.zip",false).advance(KnowledgeTask.Step.IMPORT,"导入",96500,142009);
        assertEquals(67,task.percent());assertTrue(task.heading().contains("4 / 6"));assertTrue(task.count().contains("96,500 / 142,009"));
        task=task.advance(KnowledgeTask.Step.INDEX,"检查",0,-1);
        assertEquals(-1,task.percent());assertTrue(task.busy());assertFalse(task.heading().contains("完成"));
        task=task.advance(KnowledgeTask.Step.FINISH,"发布",0,-1);
        assertTrue(task.busy());assertEquals(-1,task.percent());
        task=task.complete(142009);assertFalse(task.busy());assertTrue(task.count().contains("6 / 6 步已完成"));
    }
    @Test public void failuresKeepActualStepAndDoNotClaimCompletion()throws Exception{
        KnowledgeTask task=KnowledgeTask.begin("k12.zip",false).advance(KnowledgeTask.Step.IMPORT,"导入",3500,142009).fail("SHA-256 不一致");
        KnowledgeTask restored=KnowledgeTask.restore(task.json().toString());
        assertEquals(KnowledgeTask.Status.FAILED,restored.status);assertEquals(4,restored.step.number);assertEquals(3500,restored.done);assertTrue(restored.heading().contains("安装失败"));
    }
    @Test public void processDeathKeepsStepButMarksInterrupted()throws Exception{
        KnowledgeTask task=KnowledgeTask.begin("k12.zip",true).advance(KnowledgeTask.Step.EXTRACT,"解压",100,1000);
        KnowledgeTask restored=KnowledgeTask.restore(task.json().toString());
        assertEquals(KnowledgeTask.Status.FAILED,restored.status);assertEquals(3,restored.step.number);assertTrue(restored.detail.contains("被中断"));assertFalse(restored.busy());
        assertEquals("读取本地知识包",restored.title(KnowledgeTask.Step.DOWNLOAD));
    }
    @Test public void existingPackageDoesNotPretendToReimport()throws Exception{
        KnowledgeTask task=KnowledgeTask.begin("same.zip",true).completeExisting(142009);
        assertTrue(task.reused());assertFalse(task.count().contains("6 / 6"));assertTrue(task.heading().contains("已安装"));
        assertTrue(KnowledgeTask.restore(task.json().toString()).reused());
    }
    @Test public void unknownOrInvalidCountsCannotProduceFakePercent(){
        assertEquals(-1,KnowledgeTask.begin("",false).percent());
        assertEquals(-1,KnowledgeTask.begin("",false).advance(KnowledgeTask.Step.DOWNLOAD,"",101,100).percent());
        assertEquals(-1,KnowledgeTask.begin("",false).advance(KnowledgeTask.Step.DOWNLOAD,"",0,0).percent());
        assertEquals(0,KnowledgeTask.begin("",false).advance(KnowledgeTask.Step.DOWNLOAD,"",0,100).percent());
    }
}
