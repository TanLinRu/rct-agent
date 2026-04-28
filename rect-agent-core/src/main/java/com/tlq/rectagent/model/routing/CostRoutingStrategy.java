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
public class CostRoutingStrategy {

    public static final String NAME = "cost";

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
            log.error("No available model for cost-based routing");
            return null;
        }

        Optional<ModelInstance> cheapest = available.stream()
                .min(Comparator.comparingDouble(ModelInstance::getCostPerToken));

        if (cheapest.isPresent()) {
            log.debug("Cost-based selected: {} (cost={})",
                    cheapest.get().getName(), cheapest.get().getCostPerToken());
            return cheapest.get().getChatModel();
        }

        return null;
    }
}
