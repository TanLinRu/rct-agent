package com.tlq.rectagent.model.router;

import com.tlq.rectagent.model.config.TrafficShiftingRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TrafficShiftingRouterTest {

    private TrafficShiftingRouter router;

    @BeforeEach
    void setUp() {
        router = new TrafficShiftingRouter();
    }

    @Test
    @DisplayName("禁用时应返回 null")
    void disabledShouldReturnNull() {
        router.setEnabled(false);

        var chatModel = router.getChatModel("test_agent");

        assertNull(chatModel);
    }

    @Test
    @DisplayName("启用但无规则时应返回 null")
    void enabledWithoutRulesShouldReturnNull() {
        router.setEnabled(true);

        var chatModel = router.getChatModel("test_agent");

        assertNull(chatModel);
    }

    @Test
    @DisplayName("注册规则应生效")
    void registeringRulesShouldTakeEffect() {
        router.setEnabled(true);

        List<TrafficShiftingRule> rules = new ArrayList<>();
        TrafficShiftingRule rule = new TrafficShiftingRule();
        rule.setAgent("test_agent");
        rule.setModel("model_a");
        rule.setPercentage(50);
        rules.add(rule);

        rule = new TrafficShiftingRule();
        rule.setAgent("test_agent");
        rule.setModel("model_b");
        rule.setPercentage(50);
        rules.add(rule);

        router.registerRules(rules);

        var registeredRules = router.getRulesForAgent("test_agent");

        assertNotNull(registeredRules);
        assertEquals(2, registeredRules.size());
    }

    @Test
    @DisplayName("百分比应正确求和")
    void percentageShouldSumCorrectly() {
        router.setEnabled(true);

        List<TrafficShiftingRule> rules = new ArrayList<>();
        TrafficShiftingRule rule = new TrafficShiftingRule();
        rule.setAgent("test_agent");
        rule.setModel("model_a");
        rule.setPercentage(30);
        rules.add(rule);

        rule = new TrafficShiftingRule();
        rule.setAgent("test_agent");
        rule.setModel("model_b");
        rule.setPercentage(70);
        rules.add(rule);

        router.registerRules(rules);

        var registeredRules = router.getRulesForAgent("test_agent");
        int total = registeredRules.stream().mapToInt(TrafficShiftingRule::getPercentage).sum();

        assertEquals(100, total);
    }

    @Test
    @DisplayName("isEnabled 应返回正确状态")
    void isEnabledShouldReturnCorrectState() {
        assertFalse(router.isEnabled());

        router.setEnabled(true);
        assertTrue(router.isEnabled());

        router.setEnabled(false);
        assertFalse(router.isEnabled());
    }

    @Test
    @DisplayName("获取所有规则应返回注册规则")
    void getRulesShouldReturnRegisteredRules() {
        router.setEnabled(true);

        List<TrafficShiftingRule> rules = new ArrayList<>();
        TrafficShiftingRule rule = new TrafficShiftingRule();
        rule.setAgent("agent1");
        rule.setModel("model1");
        rule.setPercentage(100);
        rules.add(rule);

        router.registerRules(rules);

        var allRules = router.getRules();

        assertNotNull(allRules);
        assertEquals(1, allRules.size());
        assertTrue(allRules.containsKey("agent1"));
    }

    @Test
    @DisplayName("未知 Agent 应返回空规则列表")
    void unknownAgentShouldReturnEmptyRulesList() {
        router.setEnabled(true);

        var rules = router.getRulesForAgent("unknown_agent");

        assertNotNull(rules);
        assertTrue(rules.isEmpty());
    }
}
