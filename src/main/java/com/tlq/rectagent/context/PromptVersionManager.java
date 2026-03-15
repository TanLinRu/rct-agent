package com.tlq.rectagent.context;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class PromptVersionManager {

    private final Map<String, PromptVersion> versionStore = new ConcurrentHashMap<>();
    private final Map<String, String> promptTemplates = new ConcurrentHashMap<>();

    private static final String DEFAULT_VERSION = "v1.0";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public PromptVersionManager() {
        initializeDefaultTemplates();
    }

    private void initializeDefaultTemplates() {
        promptTemplates.put("intent_recognition",
                "你是一位专业的意图识别专家，擅长分析用户的查询意图。请仔细分析用户的输入，识别出用户的具体意图，并返回结构化的意图信息。");

        promptTemplates.put("dynamic_prompt",
                "你是一位提示词优化专家，擅长根据上下文生成优化的提示词。请根据用户的输入和对话历史，生成最适合的提示词。");

        promptTemplates.put("data_analysis",
                "你是一位资深的数据安全分析专家，专注于从复杂的数据结构中识别安全风险、异常模式和关键洞察。你的核心能力包括数据解析、风险识别、跨维度关联分析、风险量化评估、数据质量评估和业务影响映射。");

        promptTemplates.put("message_optimization",
                "你是一位消息处理专家，擅长优化消息内容和压缩存储。请分析消息内容，进行必要的脱敏和压缩处理。");

        for (Map.Entry<String, String> entry : promptTemplates.entrySet()) {
            String versionId = entry.getKey() + "_" + DEFAULT_VERSION;
            versionStore.put(versionId, new PromptVersion(
                    versionId,
                    entry.getKey(),
                    DEFAULT_VERSION,
                    entry.getValue(),
                    LocalDateTime.now(),
                    "system"
            ));
        }
    }

    public PromptVersion getVersion(String agentType, String version) {
        String versionId = agentType + "_" + (version != null ? version : DEFAULT_VERSION);
        PromptVersion promptVersion = versionStore.get(versionId);

        if (promptVersion == null && DEFAULT_VERSION.equals(version)) {
            String defaultId = agentType + "_" + DEFAULT_VERSION;
            promptVersion = versionStore.get(defaultId);
        }

        return promptVersion;
    }

    public PromptVersion getLatestVersion(String agentType) {
        return versionStore.values().stream()
                .filter(v -> v.getAgentType().equals(agentType))
                .max(Comparator.comparing(PromptVersion::getCreatedAt))
                .orElse(null);
    }

    public void registerVersion(String agentType, String content, String createdBy) {
        String version = generateVersionNumber(agentType);
        String versionId = agentType + "_" + version;

        PromptVersion promptVersion = new PromptVersion(
                versionId,
                agentType,
                version,
                content,
                LocalDateTime.now(),
                createdBy
        );

        versionStore.put(versionId, promptVersion);
        promptTemplates.put(agentType, content);

        log.info("注册新Prompt版本: agentType={}, version={}", agentType, version);
    }

    private String generateVersionNumber(String agentType) {
        List<PromptVersion> versions = versionStore.values().stream()
                .filter(v -> v.getAgentType().equals(agentType))
                .sorted(Comparator.comparing(PromptVersion::getVersion).reversed())
                .toList();

        if (versions.isEmpty()) {
            return DEFAULT_VERSION;
        }

        String latestVersion = versions.get(0).getVersion();
        if (latestVersion.startsWith("v")) {
            try {
                int num = Integer.parseInt(latestVersion.substring(1));
                return "v" + (num + 1);
            } catch (NumberFormatException e) {
                return "v1";
            }
        }
        return "v1";
    }

    public String getPrompt(String agentType) {
        return promptTemplates.getOrDefault(agentType, "");
    }

    public String getPromptWithVersion(String agentType, String version) {
        PromptVersion promptVersion = getVersion(agentType, version);
        return promptVersion != null ? promptVersion.getContent() : getPrompt(agentType);
    }

    public List<PromptVersion> getVersionHistory(String agentType) {
        return versionStore.values().stream()
                .filter(v -> v.getAgentType().equals(agentType))
                .sorted(Comparator.comparing(PromptVersion::getCreatedAt).reversed())
                .toList();
    }

    public boolean validateSemanticIntegrity(String promptContent, List<String> criticalConstraints) {
        for (String constraint : criticalConstraints) {
            if (!promptContent.contains(constraint)) {
                log.error("Prompt语义完整性校验失败: 缺失约束 - {}", constraint);
                return false;
            }
        }
        return true;
    }

    @Data
    public static class PromptVersion {
        private String versionId;
        private String agentType;
        private String version;
        private String content;
        private LocalDateTime createdAt;
        private String createdBy;
        private String description;
        private Map<String, Object> metadata;

        public PromptVersion(String versionId, String agentType, String version,
                           String content, LocalDateTime createdAt, String createdBy) {
            this.versionId = versionId;
            this.agentType = agentType;
            this.version = version;
            this.content = content;
            this.createdAt = createdAt;
            this.createdBy = createdBy;
            this.metadata = new HashMap<>();
        }
    }
}
