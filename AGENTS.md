# AGENTS Guidelines for this Repository

This document provides quickly actionable rules for agents and humans working in this codebase. It covers build/test commands, code style expectations, and project-specific patterns to keep the project coherent across modules.

## Cursor and Copilot Rules
- Cursor rules: none detected in this repository (.cursor/rules or .cursorrules do not exist).
- Copilot rules: none detected in .github/copilot-instructions.md.

## Build, Lint, and Tests

### Environment Requirements
- JDK 21 (`JAVA_HOME` pointing to `D:/software/jdk-21.0.8`)
- Maven 3.6+ (`D:/software/apache-maven-3.6.3/bin/mvn.cmd`)

### Build Commands
```bash
# Full project build (skip tests)
mvn -B -DskipTests package

# Build with tests
mvn -B package

# Build specific module with dependencies
mvn -pl rect-agent-core -am -DskipTests package
```

### Test Commands
```bash
# Run all tests
mvn -B test

# Run single test (recommended pattern)
mvn -pl rect-agent-core -am -Dtest=com.tlq.rectagent.MyTest#testMethod test

# Run tests for a single module
mvn -pl rect-agent-core -am test
mvn -pl openclaw-java -am test

# Windows local check script
scripts\self_check.bat
```

### Lint and Formatting
- Checkstyle: `mvn checkstyle:checkstyle` (reports at `target/site/checkstyle.html`)
- SpotBugs: `mvn spotbugs:spotbugs` or `mvn -Pspotbugs clean install`
- Code formatter: No formatter plugin configured; rely on IDE formatting

## Module Structure
- **rect-agent-core**: Core Agent routing system with Spring AI Alibaba
- **openclaw-java**: WebSocket gateway module

## Code Style Guidelines (Java 21)

### Formatting
- 2-space indentation (Google Java Style)
- Line wrap at ≤100 characters
- Single blank line between import groups and before class body

### Import Organization
```java
// 1. static imports
import static java.util.Collections.*;

// 2. java.*, javax.*
import java.util.List;

// 3. third-party (Spring, Lombok, etc.)
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 4. project-specific
import com.tlq.rectagent.agent.CoordinatorAgent;
```

### Naming Conventions
| Element | Convention | Example |
|---------|------------|---------|
| Classes/Interfaces | PascalCase | `CoordinatorAgent`, `ModelRouter` |
| Methods/Fields | camelCase | `processRequest`, `maxRetries` |
| Constants | ALL_CAPS | `DEFAULT_TIMEOUT_MS` |
| Packages | lowercase | `com.tlq.rectagent.agent` |

### Types and Generics
- Use parameterized types: `List<String>`, `Map<String, Object>`
- Avoid raw types
- Use `Optional` for absent values; never return null from public APIs
- Prefer `record` for DTOs: `record AgentResponse(String content, String sessionId) {}`

### Error Handling
- Never swallow exceptions; wrap or propagate with context
- Use runtime exceptions with descriptive messages
- Use checked exceptions only when caller can recover
- Fail fast in CLI/integration points

### Logging
- Use SLF4J via `@Slf4j` or `LoggerFactory.getLogger()`
- Log levels: INFO (normal), DEBUG (diagnostics), WARN/ERROR (issues)
- Include traceId in logs: `log.info("[{}] Processing request", traceId)`

## Project-Specific Patterns

### Agent Interface
```java
public interface AgentTool {
    String getName();
    String getDescription();
    String apply(String input);
}
```

### Framework Agent Usage
- Use framework `SequentialAgent` for sequential workflows
- Use framework `SupervisorAgent` for LLM-controlled routing
- Register agents via Spring `@Autowired` or constructor injection

### Multi-Model Configuration
```yaml
rectagent:
  model:
    default-model: dashscope-qwen-turbo
    routing-strategy: cost  # cost | priority
    providers:
      dashscope:
        enabled: true
        type: dashscope
        api-key: ${DASHSCOPE_API_KEY:}
```

### Hook System (Spring AI Alibaba)
```java
// BEFORE_MODEL hooks: ContextInjectionHook, SummarizationHook
// AFTER_MODEL hooks: ProfileInferenceHook, ModelCallLimitHook
// Register via HookConfiguration
```

## Testing Guidelines
- Test naming: `ClassNameTest` for unit; `method_whenCondition_expected` for behavioral
- Use JUnit 4 (vintage) as per project scope
- Mock external services for fast tests
- Use `@DisplayName` for descriptive test names

## Quality Gates
- CI must pass before merging
- Run full build before changing code: `mvn -B -DskipTests package`
- Add tests for new public behavior
- Add justification for new dependencies in PR description

## Collaboration
- Descriptive commit messages focusing on "why":
  - "feat: add SupervisorAgent with tool registry"
  - "fix: handle null context gracefully in ContextLoader"
- Do not commit secrets or credentials
- Review changes before pushing to shared branches
