@echo off
set JAVA_HOME=D:\software\jdk-21.0.8
set PATH=%JAVA_HOME%\bin;%PATH%
mvn -B -DskipTests compile
