package cn.screenqa.lite;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.json.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

public class KnowledgeParsingTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    @Test public void zipSlipAndDuplicateEntriesCannotOverwriteOutsideFiles()throws Exception {
        File root=temp.newFolder("root");
        for(String path:new String[]{"../escape","/escape","a/../../escape","C:/escape","a\\escape"})
            try{KnowledgeArchive.child(root,path);fail(path);}catch(IOException expected){}
        File archive=temp.newFile("bad.zip");
        try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(archive))){out.putNextEntry(new ZipEntry("../escape"));out.write(1);out.closeEntry();}
        try{KnowledgeArchive.extract(archive,root,(p,d,t)->{});fail();}catch(IOException expected){}
        assertFalse(new File(temp.getRoot(),"escape").exists());
    }
    @Test public void duplicateCanonicalZipPathsAreRejected()throws Exception {
        File root=temp.newFolder("duplicate-root"),archive=temp.newFile("duplicate.zip");
        try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(archive))){for(String path:new String[]{"a/file","a//file"}){out.putNextEntry(new ZipEntry(path));out.write(1);out.closeEntry();}}
        try{KnowledgeArchive.extract(archive,root,(p,d,t)->{});fail();}catch(IOException expected){}
    }
    @Test public void manifestRequiresSupportedFilesCountsSizesAndUniqueDatasets()throws Exception {
        File root=temp.newFolder("manifest"),manifest=new File(root,"manifest.json");
        JSONArray rows=new JSONArray();
        for(String name:new String[]{"e_eval","k12_kgraph","k12_knowledge_points","m3ke"}){
            File file=KnowledgeArchive.child(root,"data/"+name+".jsonl");file.getParentFile().mkdirs();java.nio.file.Files.write(file.toPath(),"{}\n".getBytes(StandardCharsets.UTF_8));
            rows.put(new JSONObject().put("name",name).put("file","data/"+name+".jsonl").put("cleaned_records",1).put("validation",new JSONObject().put("records",1).put("bytes",3).put("sha256",KnowledgeArchive.hash(file))));
        }
        JSONObject value=new JSONObject().put("schema_version","1.0").put("datasets",rows).put("total_cleaned_records",4);
        java.nio.file.Files.write(manifest.toPath(),value.toString().getBytes(StandardCharsets.UTF_8));assertEquals(4,KnowledgeArchive.datasets(manifest).size());
        value.put("total_cleaned_records",5);java.nio.file.Files.write(manifest.toPath(),value.toString().getBytes(StandardCharsets.UTF_8));
        try{KnowledgeArchive.datasets(manifest);fail();}catch(IOException expected){}
        value.put("total_cleaned_records",4);rows.getJSONObject(0).put("file","../e_eval.jsonl");java.nio.file.Files.write(manifest.toPath(),value.toString().getBytes(StandardCharsets.UTF_8));
        try{KnowledgeArchive.datasets(manifest);fail();}catch(IOException expected){}
        rows.getJSONObject(0).put("file","data/e_eval.jsonl").put("name","m3ke");java.nio.file.Files.write(manifest.toPath(),value.toString().getBytes(StandardCharsets.UTF_8));
        try{KnowledgeArchive.datasets(manifest);fail();}catch(IOException expected){}
    }
    @Test public void jsonlStreamsAcrossBufferBoundariesAndHandlesEof()throws Exception {
        String data="{\"content\":\""+"知识".repeat(17000)+"\"}\r\n{\"last\":true}";
        try(KnowledgeArchive.Lines lines=new KnowledgeArchive.Lines(new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)))) {
            assertEquals("知识".repeat(17000),new JSONObject(lines.next()).getString("content"));
            assertTrue(new JSONObject(lines.next()).getBoolean("last"));assertNull(lines.next());assertNull(lines.next());
        }
    }
    @Test public void oversizedLinesAndMalformedUtf8AreRejected()throws Exception {
        for(byte[] data:new byte[][]{new byte[]{(byte)0xc3,0x28},new byte[256*1024+1]})
            try(KnowledgeArchive.Lines lines=new KnowledgeArchive.Lines(new ByteArrayInputStream(data))){lines.next();fail();}catch(IOException expected){}
    }
    @Test public void structuredMaterialKeepsEducationalValuesAndOmitsMetadata()throws Exception {
        JSONObject value=new JSONObject("{\"canonical_name\":\"蛋白质\",\"annotations\":{\"learningObjectives\":{\"value\":[\"认识空间结构\"],\"model\":\"secret metadata\",\"source\":\"llm\"}},\"confidence\":0.9}");
        String text=KnowledgeText.material(value);assertTrue(text.contains("蛋白质"));assertTrue(text.contains("空间结构"));assertFalse(text.contains("secret metadata"));assertFalse(text.contains("0.9"));
    }
    @Test public void chineseBigramsAndEnglishTokensAreSharedByIndexAndQuery() {
        assertTrue(KnowledgeText.tokens("蛋白质结构，Protein 21",false).containsAll(Arrays.asList("蛋白","白质","质结","结构","protein","21")));
        assertTrue(KnowledgeText.indexed("酸").contains("酸"));
    }
    @Test public void identityPreservesNegationNumbersOperatorsAndRequiresAllOptions() {
        String question="下列不正确的是（ ）";
        assertNotEquals(KnowledgeText.normalizeQuestion(question),KnowledgeText.normalizeQuestion("下列正确的是（ ）"));
        assertNotEquals(KnowledgeText.critical("x≤2"),KnowledgeText.critical("x≥2"));
        assertNotEquals(KnowledgeText.critical("This is correct"),KnowledgeText.critical("This is not correct"));
        assertNotEquals(KnowledgeText.normalizeQuestion("1.2"),KnowledgeText.normalizeQuestion("2"));
        assertNotEquals(KnowledgeText.normalizeQuestion("x<2"),KnowledgeText.normalizeQuestion("x>2"));
        KnowledgeText.Query query=new KnowledgeText.Query(null,question+"\nA. 苹果\nB. 香蕉",null,null);
        assertEquals(KnowledgeText.normalizeQuestion(question),KnowledgeText.normalizeQuestion(query.stem));assertTrue(query.sameOptions("A. 香蕉\nB. 苹果"));
        assertFalse(query.sameOptions("A. 苹果"));assertFalse(query.sameOptions("A. 苹果\nB. 西瓜"));
    }
    @Test public void textLimitsAreHardCharacterLimits() {assertEquals(4000,KnowledgeText.limit("知".repeat(5000),4000).length());}
    @Test public void bm25UsesRarityFieldWeightsAndLengthNormalization() {
        byte[] a=matchInfo(1,1,10),b=matchInfo(1,9,10),longer=matchInfo(1,1,100);
        assertTrue(KnowledgeIndex.bm25(a)>KnowledgeIndex.bm25(b));assertTrue(KnowledgeIndex.bm25(a)>KnowledgeIndex.bm25(longer));
    }
    private byte[] matchInfo(int tf,int df,int length) {
        ByteBuffer bytes=ByteBuffer.allocate((3+16+24)*4).order(ByteOrder.LITTLE_ENDIAN);
        bytes.putInt(1).putInt(8).putInt(10);for(int i=0;i<8;i++)bytes.putInt(10);for(int i=0;i<8;i++)bytes.putInt(length);
        for(int i=0;i<8;i++)bytes.putInt(i==3?tf:0).putInt(i==3?tf*df:0).putInt(i==3?df:0);return bytes.array();
    }
}
