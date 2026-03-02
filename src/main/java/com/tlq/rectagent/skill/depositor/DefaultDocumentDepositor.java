package com.tlq.rectagent.skill.depositor;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DefaultDocumentDepositor implements DocumentDepositor {
    private static final String DEPOSIT_DIR = "deposits/";

    @Override
    public void deposit(String knowledge, String result, String userRequirement) {
        // 确保存储目录存在
        java.io.File dir = new java.io.File(DEPOSIT_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 生成唯一的文件名
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String fileName = DEPOSIT_DIR + "deposit_" + timestamp + ".txt";

        // 写入文件
        try (FileWriter writer = new FileWriter(fileName)) {
            writer.write("=== 用户需求 ===\n");
            writer.write(userRequirement);
            writer.write("\n\n=== 学习结果 ===\n");
            writer.write(knowledge);
            writer.write("\n\n=== 实现方案 ===\n");
            writer.write(result);
            writer.write("\n\n=== 时间戳 ===\n");
            writer.write(LocalDateTime.now().toString());
        } catch (IOException e) {
            System.err.println("文档沉淀失败：" + e.getMessage());
        }
    }
}