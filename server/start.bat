@echo off
rem Firmament Ages - dedicated server launcher with update + restart loop (cmd version of start.ps1).
rem Every pass: packwiz sync (server side) -> NeoForge install if its version folder is missing -> start -> wait -> again.
rem End the loop for good: create a file named STOP next to this script, then type "stop" in the console.
rem Usage: start.bat            update + run
rem        start.bat noupdate   run without the packwiz sync
setlocal
cd /d "%~dp0"

rem ---------------------------------------------------------------- settings
rem Java 21 (Temurin). Never rely on PATH: on this host PATH points to Corretto 25.
rem The environment variable FA_JAVA_HOME wins. Default: <repo>\tools\jdk-21 when the server folder is <repo>\test-server.
rem Live server: set FA_JAVA_HOME or put an absolute path here.
if defined FA_JAVA_HOME (set "JAVA_HOME=%FA_JAVA_HOME%") else (set "JAVA_HOME=%~dp0..\tools\jdk-21")
set "JAVA=%JAVA_HOME%\bin\java.exe"
rem Pack source. Local PoC: "packwiz serve" in the repo. Live: raw GitHub URL of the main branch.
set "PACK_URL=http://localhost:8080/pack.toml"
rem set "PACK_URL=https://raw.githubusercontent.com/PhoenixFlyy/firmament-ages/main/pack.toml"
rem Keep in sync with pack.toml [versions] neoforge. A new version needs its installer jar next to this script.
set "NEOFORGE=21.1.252"
set "RESTART_DELAY=10"
rem ------------------------------------------------------------------------

set "INSTALLER=neoforge-%NEOFORGE%-installer.jar"
set "WINARGS=libraries\net\neoforged\neoforge\%NEOFORGE%\win_args.txt"

if not exist "%JAVA%" (
  echo Java not found at "%JAVA%". Set FA_JAVA_HOME or edit JAVA_HOME in start.bat.
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

if not exist "%WINARGS%" (
  if not exist "%INSTALLER%" (
    echo NeoForge %NEOFORGE% is not installed and %INSTALLER% is missing.
    echo Download https://maven.neoforged.net/releases/net/neoforged/neoforge/%NEOFORGE%/%INSTALLER%
    pause
    exit /b 1
  )
  echo Installing NeoForge %NEOFORGE% ...
  "%JAVA%" -jar "%INSTALLER%" --installServer
  if not exist "%WINARGS%" (
    echo NeoForge installer failed, see %INSTALLER%.log
    pause
    exit /b 1
  )
)

"%JAVA%" @user_jvm_args.txt @%WINARGS% nogui

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
