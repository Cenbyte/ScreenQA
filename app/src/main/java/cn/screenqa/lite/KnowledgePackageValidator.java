package cn.screenqa.lite;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipFile;

/** Container validation only: never extracts or parses knowledge content. */
public interface KnowledgePackageValidator {
    void validate(File file, String finalName, String contentType, long expectedBytes) throws IOException;

    final class Basic implements KnowledgePackageValidator {
        @Override public void validate(File file, String finalName, String contentType,
                                       long expectedBytes) throws IOException {
            if (!file.isFile() || file.length() == 0) throw new IOException("文件不存在或为空");
            if (expectedBytes >= 0 && file.length() != expectedBytes)
                throw new IOException("文件大小与服务器声明不符，下载可能中断");
            byte[] prefix;
            try (FileInputStream in = new FileInputStream(file)) {
                byte[] buffer=new byte[8192];int count=in.read(buffer);
                prefix=java.util.Arrays.copyOf(buffer,Math.max(0,count));
            }
            java.nio.charset.Charset charset=StandardCharsets.UTF_8;
            if(prefix.length>=2 && prefix[0]==(byte)0xFF && prefix[1]==(byte)0xFE)charset=StandardCharsets.UTF_16LE;
            if(prefix.length>=2 && prefix[0]==(byte)0xFE && prefix[1]==(byte)0xFF)charset=StandardCharsets.UTF_16BE;
            String text = new String(prefix, charset).replace("\uFEFF", "")
                    .trim().toLowerCase(Locale.ROOT);
            String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
            if (type.contains("text/html") || type.contains("application/xhtml") ||
                    text.startsWith("<") && (text.contains("<html") || text.contains("<!doctype html") ||
                    text.contains("<head") || text.contains("<body") || text.contains("<script") ||
                    text.matches("(?s).*<(?:div|form|meta|title|h1)(?:\\s|>).*")))
                throw new IOException("下载结果是 HTML 页面，可能是密码、限流或过期错误页");
            boolean zip = finalName.toLowerCase(Locale.ROOT).endsWith(".zip") ||
                    type.contains("application/zip") || type.contains("application/x-zip") ||
                    prefix.length >= 4 && prefix[0] == 'P' && prefix[1] == 'K';
            if (zip) {
                try (ZipFile archive = new ZipFile(file)) {
                    if (!archive.entries().hasMoreElements()) throw new IOException("ZIP 知识包为空");
                    // ZipFile verifies the central directory; content extraction belongs to Importer.
                } catch (IOException error) { throw new IOException("ZIP 无法正常打开：" + error.getMessage(), error); }
            }
        }
    }
}
