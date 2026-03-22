package com.tlq.rectagent;

import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.tlq.rectagent.service.RectAgentService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RectAgentApplicationTests {

    @Resource
    private RectAgentService rectAgentService;

    @Test
    void contextLoads() throws GraphRunnerException {
        rectAgentService.testAgent();
    }

}
