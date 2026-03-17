package com.tlq.rectagent.scheduler;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.tlq.rectagent.agent.*;
import com.tlq.rectagent.communication.AgentCommunicationManager;
import com.tlq.rectagent.config.ChatModelFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 智能体调度器
 * 负责管理和协调多个智能体的执行
 */
@Slf4j
@Component
public class AgentScheduler {

    @Autowired
    private IntentRecognitionAgent intentRecognitionAgent;

    @Autowired
    private DynamicPromptAgent dynamicPromptAgent;

    @Autowired
    private DataAnalysisAgent dataAnalysisAgent;

    @Autowired
    private MessageOptimizationAgent messageOptimizationAgent;

    @Autowired
    private AgentCommunicationManager communicationManager;

    @Autowired
    private ChatModelFactory chatModelFactory;

    @Autowired
    private SequentialAgentExecutor sequentialAgentExecutor;

    private final ExecutorService executorService;
    private final Map<String, AgentTask> runningTasks;

    public AgentScheduler() {
        this.executorService = Executors.newFixedThreadPool(5);
        this.runningTasks = new HashMap<>();
    }

    /**
     * 创建协调智能体
     * @return 协调智能体
     */
    public ReactAgent createCoordinatorAgent() {
        // 使用共享的ChatModel
        return ReactAgent.builder()
                .name("coordinator_agent")
                .model(chatModelFactory.getChatModel())
                .instruction("你是一位专业的智能体协调者，负责管理多个智能体之间的协作。请根据用户的请求，调用相应的智能体，并协调它们的工作流程。")
                .build();
    }

    /**
     * 提交任务给指定智能体
     * @param agentName 智能体名称
     * @param taskType 任务类型
     * @param input 输入数据
     * @return 任务ID
     */
    public String submitTask(String agentName, String taskType, String input) {
        String taskId = "task_" + System.currentTimeMillis();
        AgentTask task = new AgentTask(taskId, agentName, taskType, input);
        runningTasks.put(taskId, task);

        executorService.submit(() -> {
            try {
                executeTask(task);
            } catch (Exception e) {
                task.setStatus(TaskStatus.FAILED);
                task.setResult("任务执行失败: " + e.getMessage());
            } finally {
                // 任务完成后可以从运行任务列表中移除
                // runningTasks.remove(taskId);
            }
        });

        return taskId;
    }

    /**
     * 执行智能体任务
     * @param task 任务对象
     */
    private void executeTask(AgentTask task) {
        task.setStatus(TaskStatus.RUNNING);

        String result = "";
        if ("coordinator".equals(task.getAgentName())) {
            try {
                // 创建智能体列表，按照执行顺序排列
                List<com.alibaba.cloud.ai.graph.agent.ReactAgent> agents = new ArrayList<>();
                agents.add(intentRecognitionAgent.getAgent());
                agents.add(dynamicPromptAgent.getAgent());
                agents.add(dataAnalysisAgent.getAgent());

                // 定义输出key映射
                Map<String, String> outputKeyMap = new HashMap<>();
                outputKeyMap.put("intent_recognition_agent", "user_intent");
                outputKeyMap.put("dynamic_prompt_agent", "generated_prompt");
                outputKeyMap.put("data_analysis_agent", "analysis_result");

                // 使用自定义SequentialAgentExecutor执行
                SequentialAgentExecutor.SequentialResult execResult = sequentialAgentExecutor.execute(
                        agents, task.getInput(), outputKeyMap);
                result = execResult.getFinalOutput();
            } catch (Exception e) {
                log.error("协调智能体执行失败: {}", e.getMessage(), e);
                result = "协调智能体执行失败: " + e.getMessage();
            }
        } else {
            // 直接调用单个智能体
            switch (task.getAgentName()) {
                case "intent_recognition":
                    result = intentRecognitionAgent.recognizeIntent(task.getInput());
                    break;
                case "dynamic_prompt":
                    // 解析输入，提取意图和上下文
                    String[] parts = task.getInput().split("\\|\\|");
                    if (parts.length == 2) {
                        result = dynamicPromptAgent.generatePrompt(parts[0], parts[1]);
                    } else {
                        result = "输入格式错误，需要包含意图和上下文";
                    }
                    break;
                case "data_analysis":
                    result = dataAnalysisAgent.analyzeData(task.getInput());
                    break;
                case "message_optimization":
                    result = messageOptimizationAgent.compressMessage(task.getInput());
                    break;
                default:
                    result = "未知的智能体: " + task.getAgentName();
            }
        }

        task.setStatus(TaskStatus.COMPLETED);
        task.setResult(result);

        // 发送任务完成消息
        AgentCommunicationManager.AgentMessage message = new AgentCommunicationManager.AgentMessage(
                "scheduler",
                task.getAgentName(),
                "任务完成: " + task.getTaskId(),
                "task_completed"
        );
        communicationManager.sendMessage(task.getAgentName(), message);
    }

    /**
     * 获取任务状态
     * @param taskId 任务ID
     * @return 任务状态
     */
    public TaskStatus getTaskStatus(String taskId) {
        AgentTask task = runningTasks.get(taskId);
        return task != null ? task.getStatus() : TaskStatus.NOT_FOUND;
    }

    /**
     * 获取任务结果
     * @param taskId 任务ID
     * @return 任务结果
     */
    public String getTaskResult(String taskId) {
        AgentTask task = runningTasks.get(taskId);
        return task != null ? task.getResult() : "任务不存在";
    }

    /**
     * 取消任务
     * @param taskId 任务ID
     * @return 是否取消成功
     */
    public boolean cancelTask(String taskId) {
        AgentTask task = runningTasks.get(taskId);
        if (task != null && task.getStatus() == TaskStatus.RUNNING) {
            // 这里可以添加取消逻辑
            task.setStatus(TaskStatus.CANCELLED);
            return true;
        }
        return false;
    }

    /**
     * 任务状态枚举
     */
    public enum TaskStatus {
        PENDING, RUNNING, COMPLETED, FAILED, CANCELLED, NOT_FOUND
    }

    /**
     * 任务类
     */
    private static class AgentTask {
        private final String taskId;
        private final String agentName;
        private final String taskType;
        private final String input;
        private TaskStatus status;
        private String result;

        public AgentTask(String taskId, String agentName, String taskType, String input) {
            this.taskId = taskId;
            this.agentName = agentName;
            this.taskType = taskType;
            this.input = input;
            this.status = TaskStatus.PENDING;
            this.result = "";
        }

        // Getters and setters
        public String getTaskId() { return taskId; }
        public String getAgentName() { return agentName; }
        public String getTaskType() { return taskType; }
        public String getInput() { return input; }
        public TaskStatus getStatus() { return status; }
        public void setStatus(TaskStatus status) { this.status = status; }
        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }
    }

    /**
     * 关闭调度器，释放资源
     */
    public void shutdown() {
        log.info("AgentScheduler shutting down...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        runningTasks.clear();
        log.info("AgentScheduler shutdown complete");
    }
}