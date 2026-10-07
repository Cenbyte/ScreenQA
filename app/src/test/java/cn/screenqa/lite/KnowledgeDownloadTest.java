package cn.screenqa.lite;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

public class KnowledgeDownloadTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private static byte[] zip() throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream out=new ZipOutputStream(bytes)) {
            out.putNextEntry(new ZipEntry("questions.txt"));out.write("test knowledge".getBytes(StandardCharsets.UTF_8));out.closeEntry();
        }
        return bytes.toByteArray();
    }
    private static class Response extends HttpURLConnection {
        final Map<String,List<String>> headers=new LinkedHashMap<>();
        final Map<String,String> sent=new HashMap<>();
        final byte[] body;
        final int status;
        boolean disconnected;
        Response(String url,int status,byte[] body) throws Exception { super(new URL(url));this.status=status;this.body=body; }
        Response header(String name,String value) {headers.put(name,Collections.singletonList(value));return this;}
        @Override public String getHeaderField(String name) { List<String> values=headers.get(name);return values==null?null:values.get(0); }
        @Override public Map<String,List<String>> getHeaderFields(){return headers;}
        @Override public String getContentType(){return getHeaderField("Content-Type");}
        @Override public long getContentLengthLong(){String value=getHeaderField("Content-Length");return value==null?-1:Long.parseLong(value);}
        @Override public int getResponseCode(){return status;}
        @Override public InputStream getInputStream(){return new ByteArrayInputStream(body);}
        @Override public void setRequestProperty(String key,String value){sent.put(key,value);}
        @Override public void disconnect(){disconnected=true;}
        @Override public boolean usingProxy(){return false;}
        @Override public void connect(){}
    }
    private static class CookieJar implements KnowledgeDownloader.Cookies {
        final Map<String,String> values=new HashMap<>();
        @Override public String get(String url){return values.get(URI.create(url).getHost());}
        @Override public void set(String url,String value){values.put(URI.create(url).getHost(),value);}
    }
    private KnowledgeDownloader.Request request(){return new KnowledgeDownloader.Request("https://share.lanzoul.com/file","WebView-UA","https://share.lanzoul.com/list",null,null);}
    private KnowledgeDownloader.Http downloader(File inbox,Response...responses) {
        Iterator<Response> iterator=Arrays.asList(responses).iterator();
        return new KnowledgeDownloader.Http(inbox,new CookieJar(),new KnowledgePackageValidator.Basic(),url->{
            Response response=iterator.next();assertEquals(response.getURL().toString(),url.toString());return response;
        });
    }
    @Test public void redirectUsesPerHostCookiesHeadersAndFinalFilename() throws Exception {
        File inbox=temp.newFolder("inbox");byte[] data=zip();CookieJar cookies=new CookieJar();
        cookies.values.put("share.lanzoul.com","session=share");cookies.values.put("cdn.example.com","session=cdn");
        Response first=new Response(request().url,302,new byte[0]).header("Location","/redirect").header("Set-Cookie","token=fresh");
        Response second=new Response("https://share.lanzoul.com/redirect",307,new byte[0]).header("Location","https://cdn.example.com/blob");
        Response last=new Response("https://cdn.example.com/blob",200,data).header("Content-Disposition","attachment; filename=wrong.zip; filename*=UTF-8''k12%E7%9F%A5%E8%AF%86.zip")
                .header("Content-Type","application/zip").header("Content-Length",String.valueOf(data.length));
        Iterator<Response> iterator=Arrays.asList(first,second,last).iterator();List<Long> progress=new ArrayList<>();
        KnowledgeDownloader.Http download=new KnowledgeDownloader.Http(inbox,cookies,new KnowledgePackageValidator.Basic(),url->{
            Response current=iterator.next();assertEquals(current.getURL().toString(),url.toString());return current;
        });
        File file=download.download(request(),(name,bytes,total)->{
            progress.add(bytes);assertTrue(new File(inbox,name+".part").exists());assertFalse(new File(inbox,name).exists());
        });
        assertEquals("k12知识.zip",file.getName());assertArrayEquals(data,Files.readAllBytes(file.toPath()));
        assertEquals("session=share",first.sent.get("Cookie"));assertEquals("token=fresh",second.sent.get("Cookie"));
        assertEquals("session=cdn",last.sent.get("Cookie"));assertEquals("WebView-UA",last.sent.get("User-Agent"));
        assertEquals(second.getURL().toString(),last.sent.get("Referer"));assertEquals("identity",last.sent.get("Accept-Encoding"));
        assertEquals(Long.valueOf(data.length),progress.get(progress.size()-1));
        assertTrue(first.disconnected && second.disconnected && last.disconnected);assertEquals(1,inbox.list().length);
        assertEquals("等待解析 / 未实现",new KnowledgePackageImporter.Pending().importPackage(file));
    }
    @Test public void htmlDisguisedAsZipFailsAndRemovesPart() throws Exception {
        File inbox=temp.newFolder("html");
        Response response=new Response(request().url,200,"<!DOCTYPE html><html>expired</html>".getBytes(StandardCharsets.UTF_8))
                .header("Content-Type","application/octet-stream").header("Content-Disposition","attachment; filename=k12.zip");
        expectFailure(downloader(inbox,response),"HTML");assertEquals(0,inbox.list().length);
    }
    @Test public void truncatedSizeFailsAndRemovesPart() throws Exception {
        File inbox=temp.newFolder("truncated");
        Response response=new Response(request().url,200,zip()).header("Content-Length","9999").header("Content-Type","application/zip");
        expectFailure(downloader(inbox,response),"大小");assertEquals(0,inbox.list().length);
    }
    @Test public void corruptZipFailsAndRemovesPart() throws Exception {
        File inbox=temp.newFolder("corrupt");byte[] data=zip();
        Response response=new Response(request().url,200,Arrays.copyOf(data,20)).header("Content-Disposition","attachment; filename=k12.zip");
        expectFailure(downloader(inbox,response),"ZIP");assertEquals(0,inbox.list().length);
    }
    @Test public void emptyResponseFails() throws Exception {
        File inbox=temp.newFolder("empty");expectFailure(downloader(inbox,new Response(request().url,200,new byte[0])),"为空");
        assertEquals(0,inbox.list().length);
    }
    @Test public void repeatedDownloadsPreserveExistingAndInterruptedFiles() throws Exception {
        File inbox=temp.newFolder("duplicate");Files.write(new File(inbox,"k12.zip").toPath(),new byte[]{1});
        Files.write(new File(inbox,"k12 (1).zip.part").toPath(),new byte[]{2});
        Response response=new Response(request().url,200,zip()).header("Content-Disposition","attachment; filename=k12.zip");
        File result=downloader(inbox,response).download(request(),(n,b,t)->{});
        assertEquals("k12 (2).zip",result.getName());assertArrayEquals(new byte[]{1},Files.readAllBytes(new File(inbox,"k12.zip").toPath()));
        assertArrayEquals(new byte[]{2},Files.readAllBytes(new File(inbox,"k12 (1).zip.part").toPath()));
    }
    @Test public void filenamesCannotEscapeInboxAndDecodeUtf8() {
        assertEquals("_.._evil.zip",KnowledgeDownloader.Http.filename(request().url,"attachment; filename=../..\\evil.zip",null));
        assertEquals("a+b.zip",KnowledgeDownloader.Http.filename("https://cdn.example.com/a+b.zip",null,null));
        assertEquals("知识.zip",KnowledgeDownloader.Http.filename(request().url,"attachment; filename*=UTF-8'zh'%E7%9F%A5%E8%AF%86.zip",null));
        assertFalse(KnowledgeDownloader.Http.filename(request().url,"attachment; filename=bad.zip.part",null).endsWith(".part"));
    }
    @Test public void redirectFailureAndHttpErrorsDoNotLeaveFiles() throws Exception {
        File inbox=temp.newFolder("error");
        expectFailure(downloader(inbox,new Response(request().url,403,new byte[0])),"403");
        expectFailure(downloader(inbox,new Response(request().url,302,new byte[0]).header("Location","http://cdn.example.com/a.zip")),"HTTP");
        expectFailure(downloader(inbox,new Response(request().url,302,new byte[0])),"重定向");assertEquals(0,inbox.list().length);
    }
    @Test public void unknownLengthBinarySucceeds() throws Exception {
        File inbox=temp.newFolder("binary");byte[] data={1,2,3};
        File file=downloader(inbox,new Response(request().url,200,data).header("Content-Disposition","attachment; filename=notes.txt"))
                .download(request(),(n,b,t)->assertEquals(-1,t));
        assertEquals("notes.txt",file.getName());assertEquals(3,file.length());
    }
    @Test public void realHttpConnectionFollowsRedirectAndWritesValidatedPrivateFile() throws Exception {
        File inbox=temp.newFolder("http");byte[] payload=zip();
        ServerSocket server=new ServerSocket(0,2,InetAddress.getByName("127.0.0.1"));server.setSoTimeout(5000);
        List<String> headers=Collections.synchronizedList(new ArrayList<>());
        java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
        Thread responder=new Thread(()->{
            try {
                for(int index=0;index<2;index++)try(Socket client=server.accept()) {
                    client.setSoTimeout(5000);
                    BufferedReader input=new BufferedReader(new InputStreamReader(client.getInputStream(),StandardCharsets.US_ASCII));
                    String requestLine=input.readLine();
                    Map<String,String> received=new HashMap<>();String line;
                    while((line=input.readLine())!=null && !line.isEmpty()) {
                        int colon=line.indexOf(':');if(colon>0)received.put(line.substring(0,colon).toLowerCase(Locale.ROOT),line.substring(colon+1).trim());
                    }
                    OutputStream output=client.getOutputStream();
                    if(index==0) {
                        assertTrue(requestLine.contains("/start"));
                        output.write(("HTTP/1.1 302 Found\r\nLocation: /package\r\nSet-Cookie: ticket=valid\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    } else {
                        assertTrue(requestLine.contains("/package"));
                        headers.add(received.get("cookie"));headers.add(received.get("user-agent"));headers.add(received.get("referer"));
                        output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/zip\r\nContent-Disposition: attachment; filename=k12.zip\r\nContent-Length: "+payload.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                        output.write(payload);
                    }
                    output.flush();
                }
            } catch(Throwable error) {failure.set(error);}
        });
        responder.start();String base="http://127.0.0.1:"+server.getLocalPort();
        try {
            KnowledgeDownloader.Http download=new KnowledgeDownloader.Http(inbox,new CookieJar(),new KnowledgePackageValidator.Basic());
            File file=download.download(new KnowledgeDownloader.Request(base+"/start","Real-UA",base+"/list",null,null),(n,b,t)->{});
            assertEquals("k12.zip",file.getName());assertArrayEquals(payload,Files.readAllBytes(file.toPath()));
            assertEquals(Arrays.asList("ticket=valid","Real-UA",base+"/start"),headers);
            assertEquals(1,inbox.list().length);
            responder.join(5000);assertFalse(responder.isAlive());assertNull(failure.get());
        } finally {server.close();responder.join(5000);}
    }
    @Test public void publicPasswordIsInjectedOnlyOnTrustedHttpsDomains() {
        assertTrue(KnowledgeSourceConfig.isPasswordPageOrigin(KnowledgeSourceConfig.URL));
        assertTrue(KnowledgeSourceConfig.isPasswordPageOrigin("https://www.lanzoux.com/abc"));
        assertFalse(KnowledgeSourceConfig.isPasswordPageOrigin("https://lanzoul.com.evil.example/"));
        assertFalse(KnowledgeSourceConfig.isPasswordPageOrigin("http://wwapn.lanzoul.com/"));
    }
    @Test public void unicodeFilenameFitsAndroidByteLimitAndPreservesZipExtension() {
        String name=KnowledgeDownloader.Http.filename(request().url,"attachment; filename=\""+"知识".repeat(100)+".zip\"",null);
        assertTrue(name.endsWith(".zip"));assertTrue(name.getBytes(StandardCharsets.UTF_8).length<=180);
    }
    @Test public void gzipMimeIsNotMistakenForZipAndUtf16HtmlIsRejected() throws Exception {
        File binary=temp.newFile("archive.gz");Files.write(binary.toPath(),new byte[]{31,(byte)139,8,1});
        new KnowledgePackageValidator.Basic().validate(binary,"archive.gz","application/gzip",4);
        File html=temp.newFile("error.txt");Files.write(html.toPath(),"\uFEFF<html>error</html>".getBytes(StandardCharsets.UTF_16LE));
        try {new KnowledgePackageValidator.Basic().validate(html,"error.txt","application/octet-stream",-1);fail();}
        catch(IOException error){assertTrue(error.getMessage().contains("HTML"));}
    }
    private void expectFailure(KnowledgeDownloader downloader,String message) throws Exception {
        try {downloader.download(request(),(n,b,t)->{});fail("Expected failure");}
        catch(IOException error){assertTrue(error.getMessage(),error.getMessage().contains(message));}
    }
}
