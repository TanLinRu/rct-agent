package com.tlq.rectagent.model.routing;

import com.tlq.rectagent.model.pool.ChatModelPool;
import com.tlq.rectagent.model.pool.ModelInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class PriorityRoutingStrategy {

    public static final String NAME = "priority";

    private ChatModelPool modelPool;

    public void setModelPool(ChatModelPool modelPool) {
        this.modelPool = modelPool;
    }

    public String getName() {
        return NAME;
    }

    public ChatModel select() {
        return select(modelPool);
    }

    public ChatModel select(ChatModelPool pool) {
        List<ModelInstance> available = pool.getAllAvailable();

        if (available.isEmpty()) {
            log.error("No available model for priority-based routing");
            return null;
        }

        Optional<ModelInstance> highestPriority = available.stream()
                .min(Comparator.comparingInt(ModelInstance::getPriority));

        if (highestPriority.isPresent()) {
            log.debug("Priority-based selected: {} (priority={})",
                    highestPriority.get().getName(), highestPriority.get().getPriority());
            return highestPriority.get().getChatModel();
        }

        return null;
    }
}
