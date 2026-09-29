@echo off
rem Firmament Ages - dedicated server launcher with update + restart loop (cmd version of start.ps1).
rem Every pass: packwiz sync (server side) -> ServerStarterJar starts NeoForge -> wait -> again.
rem End the loop for good: create a file named STOP next to this script, then type "stop" in the console.
rem Usage: start.bat            update + run
rem        start.bat noupdate   run without the packwiz sync
setlocal
cd /d "%~dp0"

rem ---------------------------------------------------------------- settings
rem Java 21 (Temurin). Never rely on PATH: on this host PATH points to Corretto 25.
rem Default: <repo>\tools\jdk-21 when the server folder is <repo>\test-server. Live server: absolute path.
set "JAVA_HOME=%~dp0..\tools\jdk-21"
set "JAVA=%JAVA_HOME%\bin\java.exe"
rem Pack source. Local PoC: "packwiz serve" in the repo. Live: raw GitHub URL of the main branch.
set "PACK_URL=http://localhost:8080/pack.toml"
rem set "PACK_URL=https://raw.githubusercontent.com/<user>/firmament-ages/main/pack.toml"
rem Keep in sync with pack.toml [versions] neoforge.
set "NEOFORGE=21.1.252"
set "RESTART_DELAY=10"
rem ------------------------------------------------------------------------

if not exist "%JAVA%" (
  echo Java not found at "%JAVA%". Install Temurin 21 into tools\jdk-21 or edit JAVA_HOME in start.bat.
  pause
  exit /b 1
)
if not exist "eula.txt" echo WARNING: eula.txt is missing. Read https://aka.ms/MinecraftEULA and create eula.txt with eula=true yourself.

:loop
if /i not "%~1"=="noupdate" (
  if exist "packwiz-installer-bootstrap.jar" (
    "%JAVA%" -jar packwiz-installer-bootstrap.jar -g -s server "%PACK_URL%"
    if errorlevel 1 echo WARNING: packwiz sync failed, starting with the files on disk.
  ) else (
    echo WARNING: packwiz-installer-bootstrap.jar not found, skipping the pack sync.
  )
)

rem Reinstall NeoForge only when the pinned version changed.
set "INSTALLED="
if exist ".installed-neoforge" set /p INSTALLED=<".installed-neoforge"
set "FORCE="
if not "%INSTALLED%"=="%NEOFORGE%" set "FORCE=--installer-force"

"%JAVA%" @user_jvm_args.txt -jar server.jar --installer %NEOFORGE% %FORCE% nogui
if not errorlevel 1 (>".installed-neoforge" echo %NEOFORGE%)

if exist "STOP" (
  del "STOP"
  echo STOP file found, leaving the restart loop.
  goto end
)
echo Server stopped. Restarting in %RESTART_DELAY% s (create a file named STOP to end the loop).
timeout /t %RESTART_DELAY% /nobreak >nul
goto loop

:end
endlocal
pause
