package com.tlq.rectagent;

import com.tlq.rectagent.agent.CoordinatorAgent;
import com.tlq.rectagent.scheduler.AgentScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 多智能体协作测试
 */
@SpringBootTest
public class MultiAgentTest {

    @Autowired
    private CoordinatorAgent coordinatorAgent;

    @Autowired
    private AgentScheduler agentScheduler;

    @Test
    public void testCoordinatorAgent() {
        // 测试协调智能体处理用户请求
        String userInput = "获取项目 test 2026-01-01 00:00:00 到 2026-02-01 00:00:00 的数据，并进行分析处理";
        String result = coordinatorAgent.processRequest(userInput);
        System.out.println("协调智能体处理结果：" + result);
    }

    @Test
    public void testAgentScheduler() {
        // 测试智能体调度器
        String taskId = agentScheduler.submitTask("coordinator", "process_request", "分析项目 test 的数据安全风险");
        System.out.println("提交任务ID：" + taskId);

        // 等待任务完成
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 获取任务状态和结果
        AgentScheduler.TaskStatus status = agentScheduler.getTaskStatus(taskId);
        String result = agentScheduler.getTaskResult(taskId);
        System.out.println("任务状态：" + status);
        System.out.println("任务结果：" + result);
    }

    @Test
    public void testIntentRecognition() {
        // 测试意图识别智能体
        String taskId = agentScheduler.submitTask("intent_recognition", "recognize", "我想分析项目 test 的数据安全风险");
        System.out.println("意图识别任务ID：" + taskId);

        // 等待任务完成
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        String result = agentScheduler.getTaskResult(taskId);
        System.out.println("意图识别结果：" + result);
    }

    @Test
    public void testDataAnalysis() {
        // 测试数据分析智能体
        String taskId = agentScheduler.submitTask("data_analysis", "analyze", "分析项目 test 的数据，识别安全风险");
        System.out.println("数据分析任务ID：" + taskId);

        // 等待任务完成
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        String result = agentScheduler.getTaskResult(taskId);
        System.out.println("数据分析结果：" + result);
    }
}