package cn.screenqa.lite;

import java.io.File;

/** Called on a worker with a completed, validated private file. */
public interface KnowledgePackageImporter {
    String importPackage(File validatedFile) throws Exception;

    final class Pending implements KnowledgePackageImporter {
        @Override public String importPackage(File validatedFile) {
            return "等待解析 / 未实现";
        }
    }
}
