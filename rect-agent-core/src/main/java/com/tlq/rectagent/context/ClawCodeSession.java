package com.tlq.rectagent.context;

import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;

public record ClawCodeSession(
    String sessionId,
    List<Message> messages,
    int version
) {
    public static ClawCodeSession of(String sessionId) {
        return new ClawCodeSession(sessionId, new ArrayList<>(), 1);
    }

    public void addMessage(Message msg) {
        messages.add(msg);
    }

    public int estimateTokens() {
        return messages.stream()
            .mapToInt(m -> m.getText() != null ? m.getText().length() / 4 : 0)
            .sum();
    }

    public List<Message> toMessageList() {
        return messages;
    }

    public static ClawCodeSession fromMessages(String sessionId, List<Message> messages) {
        return new ClawCodeSession(sessionId, new ArrayList<>(messages), 1);
    }
}