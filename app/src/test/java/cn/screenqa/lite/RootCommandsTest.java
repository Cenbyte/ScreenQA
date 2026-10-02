package cn.screenqa.lite;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class RootCommandsTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private final String own="cn.screenqa.lite.dev/cn.screenqa.lite.ScreenQaAccessibilityService";
    private final String shorthand="cn.screenqa.lite.dev/.ScreenQaAccessibilityService";
    private String runMerge(String before,String failKey) throws Exception {
        File bash=new File("C:/Program Files/Git/bin/bash.exe");
        org.junit.Assume.assumeTrue("Host shell integration requires Git Bash",bash.isFile());
        File dir=temp.newFolder();String path=dir.getPath().replace('\\','/');
        Files.write(new File(dir,"enabled_accessibility_services").toPath(),before.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir,"accessibility_enabled").toPath(),"0".getBytes(StandardCharsets.UTF_8));
        String fake="fake_settings() { [ \"$1\" = '--user' ] && [ \"$2\" = '10' ] || return 99; "+
                "shift 2; op=$1; key=$3; "+
                "if [ \"$op\" = 'get' ]; then cat '"+path+"/'\"$key\"; "+
                "else [ \"$key\" != '"+failKey+"' ] || return 1; printf '%s' \"$4\" > '"+path+"/'\"$key\"; fi; }\n";
        String script=fake+RootCommands.enableAccessibility(10,own,shorthand).replace("/system/bin/settings","fake_settings");
        File scriptFile=new File(dir,"test.sh");Files.write(scriptFile.toPath(),script.getBytes(StandardCharsets.UTF_8));
        Process p=new ProcessBuilder(bash.getPath(),"--noprofile","--norc",scriptFile.getPath().replace('\\','/'))
                .redirectErrorStream(true).start();
        assertTrue(p.waitFor(5,TimeUnit.SECONDS));
        assertEquals(failKey.isEmpty()?0:failKey.equals("accessibility_enabled")?22:21,p.exitValue());
        if(failKey.isEmpty())assertEquals("1",new String(Files.readAllBytes(new File(dir,"accessibility_enabled").toPath()),StandardCharsets.UTF_8));
        return new String(Files.readAllBytes(new File(dir,"enabled_accessibility_services").toPath()),StandardCharsets.UTF_8);
    }
    @Test public void preservesTwoExistingServicesAndAppendsOwn() throws Exception {
        assertEquals("talk.back/.Service:other.app/.Service:"+own,runMerge("talk.back/.Service:other.app/.Service",""));
    }
    @Test public void handlesNullAndEmptySettings() throws Exception {
        assertEquals(own,runMerge("null",""));assertEquals(own,runMerge("",""));
    }
    @Test public void existingFullOrShortComponentIsNotDuplicated() throws Exception {
        assertEquals("talk.back/.Service:"+own,runMerge("talk.back/.Service:"+own,""));
        assertEquals(shorthand+":other.app/.Service",runMerge(shorthand+":other.app/.Service",""));
    }
    @Test public void similarlyNamedServiceDoesNotHideOwn() throws Exception {
        assertEquals(own+"Other:"+own,runMerge(own+"Other",""));
    }
    @Test public void failedWriteLeavesExistingList() throws Exception {
        assertEquals("talk.back/.Service",runMerge("talk.back/.Service","enabled_accessibility_services"));
    }
    @Test public void failedGlobalEnableStillPreservesOtherServices() throws Exception {
        assertEquals("talk.back/.Service:"+own,runMerge("talk.back/.Service","accessibility_enabled"));
    }
    @Test public void settingsValuesAreDataNotShellCommands() throws Exception {
        String text="other.app/.Service;$(false)";
        assertEquals(text+":"+own,runMerge(text,""));
    }
    @Test public void rejectsInvalidComponentsAndUser(){
        for(String bad:new String[]{"app/Service;reboot","app/$(id)","app/Service\nexit","",null}){
            try{RootCommands.enableAccessibility(0,bad,shorthand);fail("invalid component");}
            catch(IllegalArgumentException expected){}
        }
        try{RootCommands.enableAccessibility(-1,own,shorthand);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void boundsUseActualDisplayInsteadOfFixedResolution(){
        assertEquals("/system/bin/input touchscreen tap 90 40",RootCommands.tap(90,40,100,50));
        assertEquals("/system/bin/input touchscreen tap 2399 1079",RootCommands.tap(2399,1079,2400,1080));
        for(int[] p:new int[][]{{-1,0,100,100},{0,-1,100,100},{100,0,100,100},{0,100,100,100},{0,0,0,100}}){
            try{RootCommands.tap(p[0],p[1],p[2],p[3]);fail();}catch(IllegalArgumentException expected){}
        }
    }
    @Test public void answerCoordinatesComeFromExistingResolver(){
        ScreenDocument d=new ScreenDocument(Arrays.asList(
                new ScreenDocument.Line("单选题",20,20,400,50),
                new ScreenDocument.Line("哪一个是恒星？",20,60,400,90),
                new ScreenDocument.Line("A. 地球",70,100,370,130),
                new ScreenDocument.Line("B. 太阳",70,140,370,180)),500,900);
        AnswerTargetResolver.Target target=AnswerTargetResolver.resolve("choice","B",d,
                AnswerTargetResolver.everyLine(d),Arrays.asList(1,2));
        assertNotNull(target);ScreenDocument.Line line=d.lines.get(target.lineId-1);
        assertEquals("/system/bin/input touchscreen tap 220 160",
                RootCommands.tap((line.left+line.right)/2,(line.top+line.bottom)/2,d.width,d.height));
    }
}
