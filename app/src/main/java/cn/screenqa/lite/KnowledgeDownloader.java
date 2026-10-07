package cn.screenqa.lite;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Blocking downloader; invoke on a worker. Writes only inside the supplied private inbox. */
public interface KnowledgeDownloader {
    File download(Request request, Progress progress) throws IOException;

    final class Request {
        public final String url, userAgent, referer, disposition, mime;
        public Request(String url, String userAgent, String referer, String disposition, String mime) {
            this.url=url; this.userAgent=userAgent; this.referer=referer;
            this.disposition=disposition; this.mime=mime;
        }
    }
    interface Progress { void update(String filename, long bytes, long total);default void validating(String filename,long bytes){} }
    interface Cookies {
        String get(String url);
        void set(String url, String value);
    }
    interface Connections { HttpURLConnection open(URL url) throws IOException; }

    final class Http implements KnowledgeDownloader {
        private final File inbox;
        private final Cookies cookies;
        private final Connections connections;
        private final KnowledgePackageValidator validator;
        public Http(File inbox, Cookies cookies, KnowledgePackageValidator validator) {
            this(inbox, cookies, validator, url -> (HttpURLConnection) url.openConnection());
        }
        Http(File inbox, Cookies cookies, KnowledgePackageValidator validator, Connections connections) {
            this.inbox=inbox; this.cookies=cookies; this.validator=validator; this.connections=connections;
        }
        @Override public synchronized File download(Request request, Progress progress) throws IOException {
            if (!inbox.isDirectory() && !inbox.mkdirs()) throw new IOException("无法创建私有下载目录");
            URL url = networkUrl(request.url);
            String referer = request.referer;
            File part = null;
            HttpURLConnection connection = null;
            try {
                for (int hop=0; hop<=10; hop++) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("下载已取消");
                    connection = connections.open(url);
                    connection.setConnectTimeout(20000); connection.setReadTimeout(30000);
                    connection.setInstanceFollowRedirects(false);
                    connection.setRequestProperty("Accept-Encoding", "identity");
                    if (request.userAgent != null) connection.setRequestProperty("User-Agent", request.userAgent);
                    if (referer != null && !referer.isEmpty()) connection.setRequestProperty("Referer", referer);
                    String cookie = cookies.get(url.toString());
                    if (cookie != null && !cookie.isEmpty()) connection.setRequestProperty("Cookie", cookie);
                    int status = connection.getResponseCode();
                    for (Map.Entry<String,List<String>> header : connection.getHeaderFields().entrySet()) {
                        if ("Set-Cookie".equalsIgnoreCase(header.getKey()))
                            for (String value : header.getValue()) cookies.set(url.toString(), value);
                    }
                    if (status==301 || status==302 || status==303 || status==307 || status==308) {
                        String location = connection.getHeaderField("Location");
                        if (location == null || hop == 10) throw new IOException("下载重定向缺失或次数过多");
                        URL next = networkUrl(new URL(url, location).toString());
                        if ("https".equals(url.getProtocol()) && "http".equals(next.getProtocol()))
                            throw new IOException("下载地址降级到 HTTP，已拒绝不安全连接");
                        referer=url.toString(); url=next;
                        connection.disconnect(); connection=null;
                        continue;
                    }
                    if (status != 200) throw new IOException("服务器返回 HTTP " + status);
                    String disposition=connection.getHeaderField("Content-Disposition");
                    String mime=connection.getContentType();
                    String name=filename(url.toString(), disposition == null ? request.disposition : disposition,
                            mime == null ? request.mime : mime);
                    File target=availableFile(inbox, name);
                    File candidate=new File(inbox,target.getName()+".part");
                    // CREATE_NEW avoids overwriting another task or an interrupted partial file.
                    Files.createFile(candidate.toPath());
                    part=candidate;
                    long expected=connection.getContentLengthLong();
                    if (expected > KnowledgeSourceConfig.MAX_DOWNLOAD_BYTES) throw new IOException("文件超过 2 GiB 测试版限制");
                    long count=0, lastUpdate=0;
                    progress.update(target.getName(),0,expected);
                    try (InputStream input=connection.getInputStream(); FileOutputStream out=new FileOutputStream(part)) {
                        byte[] buffer=new byte[65536]; int size;
                        while ((size=input.read(buffer))!=-1) {
                            if (Thread.currentThread().isInterrupted()) throw new IOException("下载已取消");
                            count+=size;
                            if (count > KnowledgeSourceConfig.MAX_DOWNLOAD_BYTES) throw new IOException("文件超过 2 GiB 测试版限制");
                            out.write(buffer,0,size);
                            long now=System.nanoTime();
                            if (now-lastUpdate>150_000_000L) { progress.update(target.getName(),count,expected); lastUpdate=now; }
                        }
                        out.getFD().sync();
                    }
                    progress.update(target.getName(),count,expected);
                    progress.validating(target.getName(),count);
                    validator.validate(part,target.getName(),mime == null ? request.mime : mime,expected);
                    Files.move(part.toPath(),target.toPath());
                    part=null;
                    return target;
                }
                throw new IOException("下载重定向失败");
            } finally {
                if (connection != null) connection.disconnect();
                if (part != null) Files.deleteIfExists(part.toPath());
            }
        }
        static URL networkUrl(String value) throws IOException {
            try {
                URI uri=URI.create(value);
                if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) ||
                        uri.getHost()==null || uri.getUserInfo()!=null) throw new IOException("仅支持 HTTP(S) 下载链接");
                return uri.toURL();
            } catch (IllegalArgumentException e) { throw new IOException("下载链接无效",e); }
        }
        static File availableFile(File directory,String name) {
            int dot=name.lastIndexOf('.');
            String base=dot>0?name.substring(0,dot):name, ext=dot>0?name.substring(dot):"";
            File file=new File(directory,name);
            for(int suffix=1;file.exists() || new File(directory,file.getName()+".part").exists();suffix++)
                file=new File(directory,base+" ("+suffix+")"+ext);
            return file;
        }
        static String filename(String url,String disposition,String mime) {
            String name=null;
            if (disposition!=null) {
                Matcher encoded=Pattern.compile("(?i)filename\\*\\s*=\\s*([^;]+)").matcher(disposition);
                if(encoded.find()) {
                    String value=encoded.group(1).trim().replaceAll("^\"|\"$", "");
                    int first=value.indexOf('\''), second=first<0?-1:value.indexOf('\'',first+1);
                    if(second>=0) try {
                        name=URLDecoder.decode(value.substring(second+1).replace("+","%2B"),value.substring(0,first));
                    } catch (Exception ignored) { /* Fall back to filename. */ }
                }
                if(name==null) {
                    Matcher simple=Pattern.compile("(?i)filename\\s*=\\s*(?:\"([^\"]*)\"|([^;]*))").matcher(disposition);
                    if(simple.find()) name=simple.group(1)==null?simple.group(2).trim():simple.group(1);
                }
            }
            if(name==null || name.trim().isEmpty()) try {
                String path=URI.create(url).getRawPath();
                name=URLDecoder.decode(path.substring(path.lastIndexOf('/')+1).replace("+","%2B"),StandardCharsets.UTF_8.name());
            } catch(Exception ignored) { name=""; }
            name=name.replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]","_").replaceAll("^[. ]+|[. ]+$", "");
            if(name.length()>120) {
                int dot=name.lastIndexOf('.');
                String ext=dot>0 && name.length()-dot<=12?name.substring(dot):"";
                name=name.substring(0,120-ext.length())+ext;
            }
            // Android's filesystem limit is in bytes, including Unicode filenames and .part.
            if(name.getBytes(StandardCharsets.UTF_8).length>180) {
                int dot=name.lastIndexOf('.');
                String ext=dot>0 && name.length()-dot<=12?name.substring(dot):"";
                String base=ext.isEmpty()?name:name.substring(0,dot);
                while((base+ext).getBytes(StandardCharsets.UTF_8).length>180)
                    base=base.substring(0,base.offsetByCodePoints(base.length(),-1));
                name=base+ext;
            }
            if(name.trim().isEmpty()) name="knowledge-package";
            String type=mime==null?"":mime.toLowerCase(Locale.ROOT);
            if(!name.contains(".")) name+=type.contains("application/zip") || type.contains("application/x-zip")?".zip":".bin";
            if(name.toLowerCase(Locale.ROOT).endsWith(".part")) name+=".bin";
            return name;
        }
    }
}
