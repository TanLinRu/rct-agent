package com.tlq.rectagent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class MockChatModel implements ChatModel {

    private final AtomicInteger callCount = new AtomicInteger(0);

    @Override
    public String call(String text) {
        log.debug("[MockChatModel] call: {}", text);

        int count = callCount.getAndIncrement();

        if (text.contains("FINISH") || (count >= 3)) {
            return "[\"FINISH\"]";
        }

        if (text.contains("intent_recognition_agent") || text.contains("识别意图")) {
            return "[\"data_analysis_agent\"]";
        }

        if (text.contains("data_analysis_agent") || text.contains("数据分析")) {
            return "[\"risk_assessment_agent\"]";
        }

        if (text.contains("risk_assessment_agent") || text.contains("风险评估")) {
            return "[\"FINISH\"]";
        }

        if (text.contains("请分析用户输入") || text.contains("决定调用哪个Agent")) {
            return "[\"intent_recognition_agent\"]";
        }

        if (count == 0) {
            return "[\"intent_recognition_agent\"]";
        } else if (count == 1) {
            return "[\"data_analysis_agent\"]";
        } else if (count == 2) {
            return "[\"risk_assessment_agent\"]";
        } else {
            return "[\"FINISH\"]";
        }
    }

    @Override
    public String call(Message... messages) {
        if (messages != null && messages.length > 0) {
            return call(messages[messages.length - 1].getText());
        }
        return call("");
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        String text = prompt.getContents();
        if (text == null || text.isEmpty()) {
            List<Message> msgs = prompt.getInstructions();
            if (msgs != null && !msgs.isEmpty()) {
                text = msgs.get(msgs.size() - 1).getText();
            }
        }
        String response = call(text != null ? text : "");
        AssistantMessage assistantMessage = new AssistantMessage(response);
        return new ChatResponse(List.of(new Generation(assistantMessage)));
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return ChatOptions.builder().temperature(0.7).build();
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        ChatResponse response = call(prompt);
        String text = response.getResult().getOutput().getText();
        AssistantMessage assistantMessage = new AssistantMessage(text);
        return Flux.just(new ChatResponse(List.of(new Generation(assistantMessage))));
    }

}
