# AGENTS Guidelines

This file provides repo-specific guidance for agents. Only include what would take significant investigation to discover.

## Config
- **Java**: JDK 21 required
- **Framework**: Spring AI Alibaba Agent Framework 1.1.2.0
- **Testing**: Real API calls (set `OPENAI_API_KEY`)

## Build & Test Commands
```powershell
# Build (skip tests)
$env:JAVA_HOME='D:\software\jdk-21.0.8'; mvn -B -DskipTests package

# Build specific module
$env:JAVA_HOME='D:\software\jdk-21.0.8'; mvn -pl rect-agent-core -am -DskipTests package

# Run single test class
$env:JAVA_HOME='D:\software\jdk-21.0.8'; mvn -pl rect-agent-core -am -Dtest=SupervisorAgentFrameworkTest test

# Run single test method
$env:JAVA_HOME='D:\software\jdk-21.0.8'; mvn -pl rect-agent-core -am -Dtest=ClassName#methodName test
```

## Quick Check
```powershell
scripts\self_check.bat
```

## Modules
- **rect-agent-core**: Core Agent routing system (main module)
- **openclaw-java**: WebSocket gateway

## Known Issues
- Spring AI 1.0.0-M6 has `OpenAiChatOptions` compatibility issues
- `@SpringBootTest` may not discover tests properly; use surefire `-Dtest=` directly
- SupervisorAgentFramework bean may not load in test context

## Additional References
- Architecture details: see `README.md`
- Claw code context: see `docs/claw_code.md`
