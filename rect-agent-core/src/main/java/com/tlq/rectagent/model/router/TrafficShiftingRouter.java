package com.tlq.rectagent.model.router;

import com.tlq.rectagent.model.config.TrafficShiftingRule;
import com.tlq.rectagent.model.pool.ChatModelPool;
import com.tlq.rectagent.model.pool.ModelInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class TrafficShiftingRouter {

    @Autowired
    private ChatModelPool modelPool;

    private final Map<String, List<TrafficShiftingRule>> rules = new ConcurrentHashMap<>();
    private final AtomicInteger requestCounter = new AtomicInteger(0);
    private boolean enabled = false;

    @PostConstruct
    public void init() {
        log.info("TrafficShiftingRouter initialized");
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        log.info("Traffic shifting enabled: {}", enabled);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void registerRule(String agentName, List<TrafficShiftingRule> agentRules) {
        if (agentRules == null || agentRules.isEmpty()) {
            return;
        }

        int totalPercentage = agentRules.stream()
                .mapToInt(TrafficShiftingRule::getPercentage)
                .sum();

        if (totalPercentage != 100) {
            log.warn("Traffic shifting rules for {} sum to {}%, not 100%", agentName, totalPercentage);
        }

        rules.put(agentName, new ArrayList<>(agentRules));
        log.info("Registered traffic shifting rules for {}: {}", agentName, agentRules);
    }

    public void registerRules(List<TrafficShiftingRule> allRules) {
        if (allRules == null || allRules.isEmpty()) {
            return;
        }

        Map<String, List<TrafficShiftingRule>> byAgent = new HashMap<>();
        for (TrafficShiftingRule rule : allRules) {
            byAgent.computeIfAbsent(rule.getAgent(), k -> new ArrayList<>()).add(rule);
        }

        byAgent.forEach(this::registerRule);
    }

    public ChatModel getChatModel(String agentName) {
        if (!enabled) {
            return null;
        }

        List<TrafficShiftingRule> agentRules = rules.get(agentName);
        if (agentRules == null || agentRules.isEmpty()) {
            return null;
        }

        int roll = Math.abs(requestCounter.incrementAndGet() % 100);
        int cumulative = 0;

        for (TrafficShiftingRule rule : agentRules) {
            cumulative += rule.getPercentage();
            if (roll < cumulative) {
                String traceId = org.slf4j.MDC.get("traceId");
                log.info("[{}] Traffic shifting: {} -> {} (roll={}, threshold={})",
                        traceId, agentName, rule.getModel(), roll, rule.getPercentage());

                Optional<ModelInstance> instance = modelPool.get(rule.getModel());
                if (instance.isPresent() && instance.get().getBreaker().allowRequest()) {
                    return instance.get().getChatModel();
                }

                log.warn("[{}] Traffic shifting target model {} unavailable, falling back", traceId, rule.getModel());
            }
        }

        Optional<ModelInstance> firstAvailable = agentRules.stream()
                .map(r -> modelPool.get(r.getModel()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(m -> m.getBreaker().allowRequest())
                .findFirst();

        return firstAvailable.map(ModelInstance::getChatModel).orElse(null);
    }

    public Map<String, List<TrafficShiftingRule>> getRules() {
        return Collections.unmodifiableMap(rules);
    }

    public List<TrafficShiftingRule> getRulesForAgent(String agentName) {
        List<TrafficShiftingRule> agentRules = rules.get(agentName);
        return agentRules != null ? Collections.unmodifiableList(agentRules) : Collections.emptyList();
    }
}
