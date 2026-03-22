AGENTS Guidelines for this Repository

This document provides quickly actionable rules for agents and humans working in this codebase. It covers build/test commands, and code style expectations to keep the project coherent across modules.

Cursor and Copilot Rules
- Cursor rules: none detected in this repository (.cursor/rules or .cursorrules do not exist).
- Copilot rules: none detected in .github/copilot-instructions.md.

Self-checks and tooling
- Added a Windows batch script at scripts/self_check.bat to perform full build and per-module tests:
  - Builds the entire project without tests
  - Builds each module individually (rect-agent-core and openclaw-java)
  - Runs tests for both modules
- Use the script after ensuring JAVA_HOME points to a Java 17+/21 JDK for compatibility with Spring Boot 3.x

- Cursor rules: none detected in this repository (.cursor/rules or .cursorrules do not exist).
- Copilot rules: none detected in .github/copilot-instructions.md.

Build, Lint, and Tests
- Build all modules (preferred):
  - mvn -B -DskipTests package
  - This builds the multi-module project from the repository root (pom.xml at the root).
- Build with tests: mvn -B package
- Run all tests: mvn -B test
- Run a single test (recommended patterns):
  - General form: mvn -Dtest=<FullyQualifiedTestName>#<testMethod> test
  - Example (module rect-agent-core): mvn -pl rect-agent-core -am -Dtest=com.example.MyTest#testCalculate test
- Run tests for a single module only: mvn -pl <module-name> -am test
- Lint / static analysis (if configured):
  - mvn checkstyle:checkstyle (reports under target/site/checkstyle.html)
  - If a SpotBugs/FindBugs configuration exists, use: mvn spotbugs:spotbugs or mvn -Pspotbugs clean install
- Format / code style (if a formatter plugin is configured):
  - mvn fmt:format or mvn google-java-format:format if using the google-java-format-maven-plugin
  - If no formatter is configured, rely on IDE formatting (see Code Style Guidelines below).

Module and Project Scope
- This is a Maven multi-module project with root pom.xml and modules:
  - openclaw-java
  - rect-agent-core
- Commands that rely on module selection can be combined with -pl and -am to ensure dependencies are built.

Code Style Guidelines (Java)
- Language level: Java 21 (as declared by the project). Prefer modern Java features where safe (var for local, records for DTOs, sealed types where appropriate).
- Formatting and imports:
  - Use 2-space indentation for Java source files (per Google Java Style guidance) and wrap lines to ≤100 characters where practical.
  - Organize imports in groups: static imports, then java.*, javax.*, third-party, and project-specific. Within groups, sort lexicographically.
  - Use single blank line between groups, and one blank line before class body after the header.
  - Avoid unused imports; remove them automatically via IDE or formatter.
- Naming conventions:
  - Classes and interfaces: PascalCase (e.g., DocumentParser, OpenClawService).
  - Methods and fields: camelCase (e.g., parseDocument, maxRetries).
  - Constants: ALL_CAPS_WITH_UNDERSCORES (e.g., DEFAULT_TIMEOUT_MS).
  - Package names: all lowercase.
- Types and generics:
  - Prefer parameterized types (List<String>), avoid raw types.
  - Use Optional where a value may be absent; do not return null from public APIs.
  - Favor strong typing and small, focused data carriers.
- Error handling:
  - Do not swallow exceptions; wrap or propagate with meaningful context.
  - Fail fast with clear messages in CLI components and integration points.
  - Use checked exceptions only when the caller can recover; otherwise, use runtime exceptions with descriptive messages.
- Logging:
  - Use SLF4J via Lombok @Slf4j or explicit LoggerFactory usage.
  - Log at appropriate levels (INFO for normal flow, DEBUG for diagnostics, WARN/ERROR for issues).
- Immutability and state:
  - Prefer immutable objects; use final fields; minimize mutability.
  - Avoid leaking internal state; defensively copy mutable inputs if required.
- Dependency usage:
  - Prefer explicit version management via the root POM. Avoid transitive surprises by using <exclusions> when necessary.
  - Keep a clean, minimal dependency surface for modules; prefer well-supported, up-to-date libraries.
- Testing guidelines:
  - Test naming: ClassNameTest for unit tests; methodName_whenCondition_expectedOutcome for behavioral tests.
  - Use JUnit 4 (or vintage) as per project scope; prefer descriptive @Test methods and @DisplayName where available.
  - For integration tests, use @SpringBootTest or dedicated test slices; mock external services to keep tests fast.
- API design:
  - Keep public APIs stable; document any breaking changes and deprecations.
  - Return meaningful error/warning messages in API responses.
- Quality gates:
  - Ensure CI passes (tests, lint, and formatting checks) before merging.
  - Add targeted tests for any new public behavior.

Editing and Collaboration
- Before changing code, run a full build to verify baseline: mvn -B -DskipTests package
- After edits, run tests: mvn -B test
- If you introduce new dependencies, add a brief justification in the PR description and update root pom if needed.
- Use descriptive commit messages focusing on why, not just what. For example:
  - "feat: add document parser with PDF/Word support"
  - "fix: handle null document gracefully in DocumentService"
- Do not commit secrets or credentials. Review changes with the team before pushing to shared branches.

Notes
- If you need tailored lints for Kotlin, Java, or mixed-language projects, extend the AGENTS.md with module-specific rules and tools.
- If you add Cursor or Copilot rules in the future, reflect them above in the Cursor Rules section.
