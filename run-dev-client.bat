@echo off
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-11.0.32.9-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "%~dp0"
echo Starting RuneLite development client with OSRS Flip Desk...
gradlew.bat run
