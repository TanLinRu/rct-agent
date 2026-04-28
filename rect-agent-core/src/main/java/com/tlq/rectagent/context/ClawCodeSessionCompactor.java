package com.tlq.rectagent.context;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ClawCodeSessionCompactor {

    @Autowired
    private CompactionConfig config;

    public boolean shouldCompact(List<Message> messages) {
        int estimated = estimateTokens(messages);
        return estimated >= config.getMaxEstimatedTokens();
    }

    public List<Message> compact(List<Message> messages) {
        int preserveCount = config.getPreserveRecentMessages();

        if (messages.size() <= preserveCount) {
            return messages;
        }

        List<Message> toCompress = messages.subList(0, messages.size() - preserveCount);
        List<Message> preserved = messages.subList(messages.size() - preserveCount, messages.size());

        String summary = generateSummary(toCompress);

        List<Message> result = new ArrayList<>();
        result.add(new SystemMessage("<summary>\n" + summary + "\n</summary>"));
        result.addAll(preserved);

        log.info("ClawCode compression: {} messages -> {} messages (preserve={})",
                messages.size(), result.size(), preserveCount);

        return result;
    }

    public int estimateTokens(List<Message> messages) {
        return messages.stream()
            .mapToInt(m -> m.getText() != null ? m.getText().length() / 4 : 0)
            .sum();
    }

    private String generateSummary(List<Message> messages) {
        int userCount = 0;
        int assistantCount = 0;
        int toolCallCount = 0;
        Set<String> toolNames = new LinkedHashSet<>();
        List<String> userRequests = new ArrayList<>();
        List<String> pendingWork = new ArrayList<>();
        List<String> keyFiles = new ArrayList<>();
        StringBuilder timeline = new StringBuilder();

        for (Message msg : messages) {
            String role = msg.getMessageType().getValue();
            String text = msg.getText();
            String truncatedText = truncate(text, 50);
            timeline.append(role).append(": ").append(truncatedText).append("\n");

            if (msg instanceof UserMessage um) {
                userCount++;
                userRequests.add(truncate(um.getText(), 100));
            } else if (msg instanceof AssistantMessage am) {
                assistantCount++;
                if (am.hasToolCalls()) {
                    for (var tc : am.getToolCalls()) {
                        toolCallCount++;
                        toolNames.add(tc.name());
                    }
                }
            } else if (msg instanceof ToolResponseMessage trm) {
                for (var tr : trm.getResponses()) {
                    extractFilePaths(tr.responseData(), keyFiles);
                }
            }
        }

        String toolsMentioned = toolNames.isEmpty() ? "(none)" : String.join(", ", toolNames);
        String userRequestsStr = userRequests.isEmpty() ? "  (none)" : 
            userRequests.stream().map("  - "::concat).collect(Collectors.joining("\n"));
        String pendingWorkStr = pendingWork.isEmpty() ? "  (none detected)" : 
            pendingWork.stream().map("  - "::concat).collect(Collectors.joining("\n"));
        String keyFilesStr = keyFiles.isEmpty() ? "(none detected)" : 
            String.join(", ", keyFiles.stream().limit(10).collect(Collectors.toList()));

        return String.format("""
Conversation summary:
- Scope: %d earlier messages compacted (user=%d, assistant=%d, tool=%d).
- Tools mentioned: %s.
- Recent user requests:
%s
- Pending work:
%s
- Key files referenced: %s
- Key timeline:
%s""",
                messages.size(), userCount, assistantCount, toolCallCount,
                toolsMentioned,
                userRequestsStr,
                pendingWorkStr,
                keyFilesStr,
                timeline
        );
    }

    private void extractFilePaths(String output, List<String> keyFiles) {
        if (output == null) return;
        Pattern pattern = Pattern.compile("([\\w/]+\\.[a-z]+)");
        Matcher matcher = pattern.matcher(output);
        while (matcher.find()) {
            String path = matcher.group(1);
            if (path.contains("/src/") || path.endsWith(".java") || 
                path.endsWith(".md") || path.endsWith(".xml") || 
                path.endsWith(".yaml") || path.endsWith(".yml")) {
                keyFiles.add(path);
            }
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null || text.isEmpty()) return "(empty)";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}