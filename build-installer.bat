@echo off
setlocal EnableExtensions

set "ROOT=%~dp0"
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-11.0.32.9-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "CSC=%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
set "JAR=%ROOT%build\libs\osrs-flip-desk-1.1.0.jar"
set "OUT=%ROOT%dist\OSRS_Flip_Desk_Install.exe"

if not exist "%CSC%" (
  echo Could not find csc.exe. Install .NET Framework 4.x developer tools / Windows SDK.
  exit /b 1
)

echo Building plugin JAR...
call "%ROOT%gradlew.bat" jar --no-daemon -q
if errorlevel 1 exit /b 1

if not exist "%JAR%" (
  echo Missing JAR: %JAR%
  exit /b 1
)

if not exist "%ROOT%dist" mkdir "%ROOT%dist"

echo Compiling installer EXE with embedded JAR...
"%CSC%" /nologo /optimize+ /target:winexe ^
  /r:System.Windows.Forms.dll ^
  /resource:"%JAR%",osrs_flip_desk.jar ^
  /out:"%OUT%" ^
  "%ROOT%installer\InstallFlipDesk.cs"
if errorlevel 1 exit /b 1

echo.
echo Done: %OUT%
echo Send that single EXE to someone. They double-click it to install.
exit /b 0
