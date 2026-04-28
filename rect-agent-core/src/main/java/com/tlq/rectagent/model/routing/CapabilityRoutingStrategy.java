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
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CapabilityRoutingStrategy {

    public static final String NAME = "capability";

    private ChatModelPool modelPool;

    public void setModelPool(ChatModelPool modelPool) {
        this.modelPool = modelPool;
    }

    public String getName() {
        return NAME;
    }

    public ChatModel select(Set<String> requiredCapabilities) {
        return select(requiredCapabilities, modelPool);
    }

    public ChatModel select(Set<String> requiredCapabilities, ChatModelPool pool) {
        List<ModelInstance> candidates;

        if (requiredCapabilities == null || requiredCapabilities.isEmpty()) {
            candidates = pool.getAllAvailable();
            log.debug("No capabilities required, selecting from all available models");
        } else {
            candidates = pool.getAllAvailable().stream()
                    .filter(m -> m.getCapabilities() != null)
                    .filter(m -> m.getCapabilities().containsAll(requiredCapabilities))
                    .collect(Collectors.toList());

            log.debug("Looking for models with capabilities: {}, found {} candidates",
                    requiredCapabilities, candidates.size());

            if (candidates.isEmpty()) {
                log.warn("No model matches required capabilities {}, falling back to all available",
                        requiredCapabilities);
                candidates = pool.getAllAvailable();
            }
        }

        if (candidates.isEmpty()) {
            log.error("No available model for capability-based routing");
            return null;
        }

        Optional<ModelInstance> selected = candidates.stream()
                .min(Comparator.comparingDouble(ModelInstance::getCostPerToken));

        if (selected.isPresent()) {
            log.debug("Capability-based selected: {} (cost={}, capabilities={})",
                    selected.get().getName(),
                    selected.get().getCostPerToken(),
                    selected.get().getCapabilities());
            return selected.get().getChatModel();
        }

        return null;
    }
}
