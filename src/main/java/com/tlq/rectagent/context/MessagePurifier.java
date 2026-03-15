package com.tlq.rectagent.context;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class MessagePurifier {

    private static final Pattern THOUGHT_PATTERN = Pattern.compile(
            "(?i)(Thought[:\\s]*|思考[:\\s]*| Reasoning[:\\s]*)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern OBSERVATION_PATTERN = Pattern.compile(
            "(?i)(Observation[:\\s]*|观察[:\\s]*|Result[:\\s]*)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ACTION_PATTERN = Pattern.compile(
            "(?i)(Action[:\\s]*|行动[:\\s]*|Tool Call[:\\s]*)",
            Pattern.CASE_INSENSITIVE
    );

    public PurifiedMessage purifyForInterAgent(String message) {
        PurifiedMessage result = new PurifiedMessage();
        result.setOriginalMessage(message);

        String purified = message;

        purified = removeThoughtProcess(purified);

        String observation = extractObservation(message);
        result.setObservation(observation);

        String toolCalls = extractToolCalls(message);
        result.setToolCalls(toolCalls);

        result.setPurifiedContent(purified);

        return result;
    }

    public String removeThoughtProcess(String message) {
        String result = message;

        result = THOUGHT_PATTERN.matcher(result).replaceAll("\n[Thought Removed]\n");

        String[] lines = result.split("\n");
        List<String> keptLines = new ArrayList<>();
        boolean inThoughtBlock = false;

        for (String line : lines) {
            if (line.contains("[Thought") || line.contains("Thought:") || line.toLowerCase().contains("思考")) {
                inThoughtBlock = true;
                continue;
            }
            if (inThoughtBlock && (line.trim().isEmpty() || line.contains("Observation") || line.contains("观察"))) {
                inThoughtBlock = false;
            }
            if (!inThoughtBlock) {
                keptLines.add(line);
            }
        }

        result = String.join("\n", keptLines);
        return result.trim();
    }

    public String extractObservation(String message) {
        StringBuilder observation = new StringBuilder();

        Matcher obsMatcher = OBSERVATION_PATTERN.matcher(message);
        while (obsMatcher.find()) {
            int start = obsMatcher.end();
            int nextSection = message.indexOf("\n\n", start);
            if (nextSection == -1) {
                nextSection = message.length();
            }
            observation.append(message, start, nextSection).append("\n");
        }

        return observation.toString().trim();
    }

    public String extractToolCalls(String message) {
        StringBuilder toolCalls = new StringBuilder();

        Matcher actionMatcher = ACTION_PATTERN.matcher(message);
        while (actionMatcher.find()) {
            int start = actionMatcher.end();
            int nextSection = message.indexOf("\n\n", start);
            if (nextSection == -1) {
                nextSection = message.length();
            }
            toolCalls.append(message, start, nextSection).append("\n");
        }

        return toolCalls.toString().trim();
    }

    public String buildMinimalContext(String observation, String toolResults, String sessionSummary) {
        StringBuilder context = new StringBuilder();

        if (sessionSummary != null && !sessionSummary.isEmpty()) {
            context.append("【会话背景】\n").append(sessionSummary).append("\n\n");
        }

        if (observation != null && !observation.isEmpty()) {
            context.append("【当前观察】\n").append(observation).append("\n\n");
        }

        if (toolResults != null && !toolResults.isEmpty()) {
            context.append("【工具结果】\n").append(toolResults).append("\n");
        }

        return context.toString();
    }

    public String sanitizeUserInput(String userInput) {
        String sanitized = userInput;

        sanitized = sanitizePromptInjection(sanitized);

        sanitized = sanitizePII(sanitized);

        return sanitized;
    }

    private String sanitizePromptInjection(String input) {
        String[] dangerousPatterns = {
                "ignore previous instructions",
                "忽略之前的指令",
                "system prompt",
                "系统提示",
                "you are now",
                "你现在是",
                "disregard",
                "忽略"
        };

        String result = input;
        for (String pattern : dangerousPatterns) {
            result = result.replaceAll("(?i)" + Pattern.quote(pattern), "[FILTERED]");
        }

        return result;
    }

    private String sanitizePII(String input) {
        String result = input;

        Pattern emailPattern = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
        result = emailPattern.matcher(result).replaceAll("[EMAIL_REDACTED]");

        Pattern phonePattern = Pattern.compile("1[3-9]\\d{9}");
        result = phonePattern.matcher(result).replaceAll("[PHONE_REDACTED]");

        Pattern idCardPattern = Pattern.compile("\\d{17}[\\dXx]");
        result = idCardPattern.matcher(result).replaceAll("[ID_REDACTED]");

        return result;
    }

    @lombok.Data
    public static class PurifiedMessage {
        private String originalMessage;
        private String purifiedContent;
        private String observation;
        private String toolCalls;
    }
}
