@echo off
rem Firmament Ages - dedicated server launcher with update + restart loop (cmd version of start.ps1).
rem Every pass: packwiz sync (server side) -> NeoForge install if its version folder is missing -> start.
rem After the server ended: RESTART file (/restart) -> next pass at once; a crash -> next pass after RESTART_DELAY,
rem the third crash within five minutes ends the loop; a clean stop (/stop, then /stop confirm) ends the loop.
rem Usage: start.bat            update + run
rem        start.bat noupdate   run without the packwiz sync
setlocal
cd /d "%~dp0"

rem ---------------------------------------------------------------- settings
rem Java 21 (Temurin). Never rely on PATH: it may hold another Java version.
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
findstr /b /i /c:"eula=true" eula.txt >nul 2>&1 || echo WARNING: the Minecraft EULA is not accepted. Read https://aka.ms/MinecraftEULA and set eula=true in eula.txt.

set "CRASH1="
set "CRASH2="

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

call :crashcount BEFORE
"%JAVA%" @user_jvm_args.txt @%WINARGS% nogui
set "CODE=%ERRORLEVEL%"
call :crashcount AFTER
if exist "RESTART" (
  del "RESTART"
  echo RESTART file found, next pass.
  goto loop
)
if not "%CODE%"=="0" goto crashed
if %AFTER% GTR %BEFORE% goto crashed
echo The server stopped, leaving the loop.
goto end

:crashed
call :now
set "AGE=999999"
if defined CRASH1 set /a "AGE=NOW-CRASH1"
if %AGE% LEQ 300 (
  echo The server crashed three times within five minutes ^(exit code %CODE%^), leaving the loop.
  goto end
)
set "CRASH1=%CRASH2%"
set "CRASH2=%NOW%"
echo The server crashed ^(exit code %CODE%^). Next pass in %RESTART_DELAY% s.
timeout /t %RESTART_DELAY% /nobreak >nul
goto loop

:end
endlocal
pause
exit /b 0

:crashcount
set "%~1=0"
for %%f in ("crash-reports\*.txt") do set /a "%~1+=1"
exit /b 0

:now
set "NOW=0"
for /f %%t in ('powershell -NoProfile -Command "[DateTimeOffset]::UtcNow.ToUnixTimeSeconds()"') do set "NOW=%%t"
exit /b 0
