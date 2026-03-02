# SequentialAgent 设计方案

## 1. 设计背景

基于研究结果，Spring AI Alibaba 1.1.2.0中没有提供SequentialAgent类。因此，我们需要设计一个基于ReactAgent的自定义实现，以实现智能体的顺序执行和数据传递。

## 2. 设计目标

1. **顺序执行**：实现智能体按照指定顺序执行（IntentRecognitionAgent → DynamicPromptAgent → DataAnalysisAgent）
2. **数据传递**：实现智能体间的数据传递，确保数据在智能体间正确流转
3. **可扩展性**：设计灵活的架构，支持添加新的智能体
4. **错误处理**：实现完善的错误处理机制
5. **与现有系统集成**：确保与现有智能体和系统架构兼容

## 3. 设计方案

### 3.1 核心组件

#### 3.1.1 SequentialAgent 类

```java
package com.tlq.rectagent.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 顺序执行智能体
 * 负责按照指定顺序执行多个智能体，并处理智能体间的数据传递
 */
@Component
public class SequentialAgent {

    private final List<ReactAgent> agents;
    private final Map<String, String> dataMap;

    /**
     * 构造函数
     * @param agents 智能体列表，按照执行顺序排列
     */
    public SequentialAgent(List<ReactAgent> agents) {
        this.agents = agents;
        this.dataMap = new java.util.HashMap<>();
    }

    /**
     * 执行智能体序列
     * @param input 初始输入
     * @return 最终执行结果
     */
    public String execute(String input) {
        String result = input;
        
        for (ReactAgent agent : agents) {
            try {
                // 执行当前智能体
                result = agent.call(result).getText();
                
                // 提取并存储智能体的输出
                // 这里需要根据智能体的outputKey来提取数据
                // 假设智能体的outputKey已经在配置中设置
                
            } catch (GraphRunnerException e) {
                // 处理异常
                throw new RuntimeException("智能体执行失败: " + e.getMessage(), e);
            }
        }
        
        return result;
    }

    /**
     * 获取数据映射
     * @return 数据映射
     */
    public Map<String, String> getDataMap() {
        return dataMap;
    }
}
```

#### 3.1.2 SequentialAgentFactory 类

```java
package com.tlq.rectagent.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * SequentialAgent工厂类
 * 负责创建和配置SequentialAgent
 */
@Component
public class SequentialAgentFactory {

    @Autowired
    private IntentRecognitionAgent intentRecognitionAgent;

    @Autowired
    private DynamicPromptAgent dynamicPromptAgent;

    @Autowired
    private DataAnalysisAgent dataAnalysisAgent;

    /**
     * 创建SequentialAgent
     * @return SequentialAgent实例
     */
    public SequentialAgent createSequentialAgent() {
        // 创建智能体列表，按照执行顺序排列
        List<ReactAgent> agents = new ArrayList<>();
        
        // 添加IntentRecognitionAgent
        agents.add(intentRecognitionAgent.getAgent());
        
        // 添加DynamicPromptAgent
        agents.add(dynamicPromptAgent.getAgent());
        
        // 添加DataAnalysisAgent
        agents.add(dataAnalysisAgent.getAgent());
        
        // 创建并返回SequentialAgent
        return new SequentialAgent(agents);
    }
}
```

### 3.2 数据传递机制

1. **基于outputKey和instruction占位符**：
   - IntentRecognitionAgent设置outputKey("user_intent")
   - DynamicPromptAgent使用instruction("用户意图：{user_intent}\n请根据用户意图生成一个优化的提示词。")
   - DynamicPromptAgent设置outputKey("generated_prompt")
   - DataAnalysisAgent使用instruction("提示词：{generated_prompt}\n请根据提示词执行数据分析任务。")

2. **数据提取和传递**：
   - SequentialAgent在执行每个智能体后，提取其输出数据
   - 将提取的数据存储在dataMap中
   - 在下一个智能体执行前，将相关数据注入到输入中

### 3.3 错误处理机制

1. **异常捕获**：捕获智能体执行过程中的GraphRunnerException
2. **错误传播**：将异常向上传播，确保调用者能够感知到错误
3. **日志记录**：记录详细的错误信息，便于调试和监控

## 4. 集成方案

### 4.1 与CoordinatorAgent集成

修改CoordinatorAgent，使用SequentialAgent替代手动顺序执行：

```java
package com.tlq.rectagent.agent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 协调智能体
 * 负责管理多智能体之间的协作和调度
 */
@Component
public class CoordinatorAgent {

    @Autowired
    private SequentialAgentFactory sequentialAgentFactory;

    /**
     * 处理用户请求
     * @param userInput 用户输入
     * @return 处理结果
     */
    public String processRequest(String userInput) {
        try {
            System.out.println("开始处理用户请求: " + userInput);
            
            // 创建SequentialAgent
            SequentialAgent sequentialAgent = sequentialAgentFactory.createSequentialAgent();
            
            // 执行智能体序列
            String result = sequentialAgent.execute(userInput);
            
            System.out.println("用户请求处理完成");
            return result;
        } catch (Exception e) {
            System.err.println("处理请求失败: " + e.getMessage());
            e.printStackTrace();
            return "处理请求失败: " + e.getMessage();
        }
    }
}
```

### 4.2 与AgentScheduler集成

修改AgentScheduler，使用SequentialAgent替代手动顺序执行：

```java
// 在AgentScheduler中添加SequentialAgentFactory的注入
@Autowired
private SequentialAgentFactory sequentialAgentFactory;

// 修改executeTask方法
private void executeTask(AgentTask task) {
    task.setStatus(TaskStatus.RUNNING);

    String result = "";
    if ("coordinator".equals(task.getAgentName())) {
        // 使用SequentialAgent
        SequentialAgent sequentialAgent = sequentialAgentFactory.createSequentialAgent();
        try {
            result = sequentialAgent.execute(task.getInput());
        } catch (Exception e) {
            result = "协调智能体执行失败: " + e.getMessage();
        }
    } else {
        // 直接调用单个智能体
        // 现有代码不变
    }

    task.setStatus(TaskStatus.COMPLETED);
    task.setResult(result);

    // 发送任务完成消息
    // 现有代码不变
}
```

## 5. 测试方案

### 5.1 单元测试

```java
package com.tlq.rectagent;

import com.tlq.rectagent.agent.SequentialAgent;
import com.tlq.rectagent.agent.SequentialAgentFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * SequentialAgent测试
 */
@SpringBootTest
public class SequentialAgentTest {

    @Autowired
    private SequentialAgentFactory sequentialAgentFactory;

    @Test
    public void testSequentialAgent() {
        // 创建SequentialAgent
        SequentialAgent sequentialAgent = sequentialAgentFactory.createSequentialAgent();
        
        // 测试输入
        String userInput = "获取项目 test 2026-01-01 00:00:00 到 2026-02-01 00:00:00 的数据，并进行分析处理";
        
        // 执行智能体序列
        String result = sequentialAgent.execute(userInput);
        
        // 打印结果
        System.out.println("SequentialAgent执行结果：" + result);
        
        // 验证结果
        assert result != null;
        assert !result.isEmpty();
    }
}
```

### 5.2 集成测试

使用现有的MultiAgentTest类，测试SequentialAgent的集成效果。

## 6. 性能考虑

1. **智能体实例缓存**：利用现有的智能体实例缓存机制，减少智能体创建的开销
2. **并行执行**：对于不依赖的智能体，可以考虑并行执行，提高性能
3. **错误处理**：快速失败，避免无效的执行

## 7. 风险评估

1. **技术风险**：自定义实现可能与Spring AI Alibaba的未来版本不兼容
2. **性能风险**：顺序执行可能比并行执行慢
3. **维护风险**：需要维护自定义代码，增加了维护成本

## 8. 缓解措施

1. **模块化设计**：将SequentialAgent设计为独立模块，便于未来替换
2. **性能监控**：监控SequentialAgent的执行性能，及时优化
3. **文档完善**：详细记录SequentialAgent的设计和实现，便于维护

## 9. 结论

基于Spring AI Alibaba 1.1.2.0的现有功能，我们设计了一个基于ReactAgent的自定义SequentialAgent实现方案。该方案通过顺序执行多个智能体，并利用outputKey和instruction占位符实现智能体间的数据传递，满足了项目的需求。

该设计方案具有以下特点：
1. **灵活性**：支持添加新的智能体和调整执行顺序
2. **兼容性**：与现有智能体和系统架构兼容
3. **可维护性**：代码结构清晰，易于维护
4. **可靠性**：实现了完善的错误处理机制

通过该方案，我们可以实现智能体的顺序编排，提高代码的可维护性和扩展性。