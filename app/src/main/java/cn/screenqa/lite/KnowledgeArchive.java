package cn.screenqa.lite;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import org.json.*;

/** Untrusted package data: bounded extraction and streaming UTF-8 JSONL, no executable content. */
final class KnowledgeArchive {
    static final long MAX_EXPANDED=1024L*1024*1024;
    interface Progress {void update(String phase,long done,long total);default void stage(KnowledgeTask.Step step,long done,long total){update(step.title,done,total);}}
    static File child(File root,String name) throws IOException {
        if(name.isEmpty() || name.startsWith("/") || name.contains("\\") || name.contains(":") || name.indexOf('\0')>=0)
            throw new IOException("知识包路径不安全："+name);
        for(String part:name.split("/"))if(part.equals("..") || part.equals("."))throw new IOException("知识包包含路径穿越");
        File file=new File(root,name).getCanonicalFile();String base=root.getCanonicalPath()+File.separator;
        if(!file.getPath().startsWith(base))throw new IOException("知识包包含路径穿越");return file;
    }
    static File extract(File archive,File destination,Progress progress) throws IOException {
        long total=0,expected=0;int entries=0;File manifest=null;
        try(ZipFile index=new ZipFile(archive)){Enumeration<? extends ZipEntry> files=index.entries();while(files.hasMoreElements()){ZipEntry item=files.nextElement();if(!item.isDirectory()){if(item.getSize()<0){expected=-1;break;}expected+=item.getSize();}}}
        long lastProgress=0;progress.stage(KnowledgeTask.Step.EXTRACT,0,expected);
        try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(new FileInputStream(archive)))) {
            ZipEntry entry;byte[] buffer=new byte[65536];
            while((entry=zip.getNextEntry())!=null) {
                if(++entries>10000)throw new IOException("知识包文件数量过多");
                File output=child(destination,entry.getName());
                if(entry.isDirectory())Files.createDirectories(output.toPath());
                else {
                    Files.createDirectories(output.getParentFile().toPath());
                    Files.createFile(output.toPath());long written=0;int count;
                    try(OutputStream out=new BufferedOutputStream(new FileOutputStream(output))) {
                        while((count=zip.read(buffer))!=-1) {
                            written+=count;total+=count;
                            if(total>MAX_EXPANDED || written>512L*1024*1024)throw new IOException("知识包解压大小超过限制");
                            out.write(buffer,0,count);if(System.nanoTime()-lastProgress>150_000_000L){progress.stage(KnowledgeTask.Step.EXTRACT,total,expected);lastProgress=System.nanoTime();}
                        }
                    }
                    if(output.getName().equals("manifest.json")) {
                        if(manifest!=null)throw new IOException("知识包有多个 manifest");manifest=output;
                    }
                }
                zip.closeEntry();progress.stage(KnowledgeTask.Step.EXTRACT,total,expected);
            }
        }
        if(manifest==null || manifest.length()>1024*1024)throw new IOException("缺少或过大的 manifest.json");
        return manifest;
    }
    static JSONObject json(File file,int maxBytes) throws Exception {
        if(file.length()>maxBytes)throw new IOException("元数据文件过大");
        byte[] bytes=Files.readAllBytes(file.toPath());
        return new JSONObject(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString());
    }
    static List<Dataset> datasets(File manifest) throws Exception {
        JSONObject object=json(manifest,1024*1024);
        if(!"1.0".equals(object.optString("schema_version")))throw new IOException("暂不支持此知识包 schema_version");
        JSONArray array=object.getJSONArray("datasets");if(array.length()!=4)throw new IOException("知识包必须包含四个已支持的数据集");
        Set<String> names=new HashSet<>(Arrays.asList("e_eval","k12_kgraph","k12_knowledge_points","m3ke"));
        List<Dataset> datasets=new ArrayList<>();long count=0;
        for(int i=0;i<array.length();i++) {
            JSONObject row=array.getJSONObject(i);String name=row.getString("name");
            if(!names.remove(name))throw new IOException("未知或重复的数据集："+name);
            if(!("data/"+name+".jsonl").equals(row.getString("file")))throw new IOException("数据文件路径不符");
            JSONObject validation=row.getJSONObject("validation");
            Dataset dataset=new Dataset(name,child(manifest.getParentFile(),row.getString("file")),
                    validation.getLong("records"),validation.getLong("bytes"),validation.getString("sha256"));
            if(!dataset.file.isFile() || dataset.file.length()!=dataset.bytes || dataset.count<0 || dataset.count>2000000 ||
                    !dataset.sha.matches("[a-fA-F0-9]{64}") || row.getLong("cleaned_records")!=dataset.count)
                throw new IOException("数据集大小、条数或校验信息无效："+name);
            datasets.add(dataset);count+=dataset.count;
        }
        if(count!=object.getLong("total_cleaned_records") || count>2000000)throw new IOException("manifest 总条数不一致");
        return datasets;
    }
    static final class Dataset {
        final String name,sha;final File file;final long count,bytes;
        Dataset(String name,File file,long count,long bytes,String sha){this.name=name;this.file=file;this.count=count;this.bytes=bytes;this.sha=sha;}
    }
    static String hash(File file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream input=new FileInputStream(file)){byte[] buffer=new byte[65536];int count;while((count=input.read(buffer))!=-1)digest.update(buffer,0,count);}
        return hex(digest.digest());
    }
    static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    static final class Lines implements Closeable {
        private final InputStream input;private final byte[] buffer=new byte[65536];private int position,end;private boolean eof;
        Lines(InputStream input){this.input=input;}
        String next() throws IOException {
            if(eof)return null;
            ByteArrayOutputStream line=new ByteArrayOutputStream(2048);
            while(true) {
                if(position==end){end=input.read(buffer);position=0;if(end==-1){eof=true;break;}}
                int start=position;while(position<end && buffer[position]!='\n')position++;
                if(line.size()+position-start>256*1024)throw new IOException("JSONL 单行超过 256 KiB");
                line.write(buffer,start,position-start);
                if(position<end && buffer[position]=='\n'){position++;return decode(line.toByteArray());}
            }
            return line.size()==0?null:decode(line.toByteArray());
        }
        private String decode(byte[] bytes) throws IOException {
            int count=bytes.length;if(count>0 && bytes[count-1]=='\r')count--;
            try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes,0,count)).toString();}
            catch(CharacterCodingException error){throw new IOException("JSONL 不是有效 UTF-8",error);}
        }
        @Override public void close()throws IOException{input.close();}
    }
    static void deleteTree(File directory) throws IOException {
        // Only app-owned directories supplied by the manager; never follows symbolic links.
        if(Files.isSymbolicLink(directory.toPath())) {Files.deleteIfExists(directory.toPath());return;}
        File[] children=directory.listFiles();if(children!=null)for(File child:children)deleteTree(child);
        Files.deleteIfExists(directory.toPath());
    }
    private KnowledgeArchive() { }
}
