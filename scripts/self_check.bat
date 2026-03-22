@echo off
REM Self-check build and test for the multi-module project
REM Prerequisites: JAVA_HOME points to JDK 17+ and mvn is available in PATH

if not defined JAVA_HOME (
  echo JAVA_HOME is not defined. Please set JAVA_HOME to a JDK 17/21 installation.
  goto :eof
)

echo === Global build (no tests) ===
mvn -B -DskipTests package
if errorlevel 1 (
  echo Global build failed. Exiting.
  exit /b 1
)

echo === Module: rect-agent-core ===
mvn -pl rect-agent-core -am -DskipTests package
if errorlevel 1 (
  echo Build failed for rect-agent-core. Exiting.
  exit /b 1
)

echo === Module: openclaw-java ===
mvn -pl openclaw-java -am -DskipTests package
if errorlevel 1 (
  echo Build failed for openclaw-java. Exiting.
  exit /b 1
)

echo === Run tests for modules ===
mvn -pl rect-agent-core -am test
mvn -pl openclaw-java -am test
echo Self-check completed.
