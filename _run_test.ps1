$env:JAVA_HOME = 'D:\software\jdk-21.0.8'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
cd 'D:\project\ai\java\rct-agent'
& 'D:\software\apache-maven-3.6.3\bin\mvn.cmd' -B test -pl rect-agent-core -am
