package com.tlq.rectagent.agent;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Data
public class AgentDataContext {

    private final Map<String, String> dataMap;

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(\\w+)}");

    public AgentDataContext() {
        this.dataMap = new HashMap<>();
    }

    public void put(String key, String value) {
        dataMap.put(key, value);
        log.debug("AgentDataContext put: {} = {}", key, value);
    }

    public String get(String key) {
        return dataMap.get(key);
    }

    public String get(String key, String defaultValue) {
        return dataMap.getOrDefault(key, defaultValue);
    }

    public boolean has(String key) {
        return dataMap.containsKey(key);
    }

    public String replacePlaceholders(String template) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        String result = template;
        boolean changed;
        do {
            changed = false;
            Matcher matcher = PLACEHOLDER_PATTERN.matcher(result);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                String placeholder = matcher.group(0);
                String key = matcher.group(1);
                String value = dataMap.get(key);
                if (value != null) {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
                    changed = true;
                } else {
                    matcher.appendReplacement(sb, placeholder);
                }
            }
            matcher.appendTail(sb);
            result = sb.toString();
        } while (changed);

        return result;
    }

    public void clear() {
        dataMap.clear();
    }

    public Map<String, String> toMap() {
        return new HashMap<>(dataMap);
    }
}
