@echo off
rem Firmament Ages server: installs NeoForge on the first start, then runs the server.
rem /restart (console or in game): the server stops and this script starts it again.
rem /stop, then /stop confirm: the server stops and this script ends.
rem A crash: the script starts the server again after RESTART_DELAY seconds; the third crash within five minutes ends it.
setlocal
cd /d "%~dp0"

set "NEOFORGE={{NEOFORGE}}"
set "RESTART_DELAY=10"
set "INSTALLER=neoforge-%NEOFORGE%-installer.jar"
set "WINARGS=libraries\net\neoforged\neoforge\%NEOFORGE%\win_args.txt"

rem Java 21: the first of FA_JAVA_HOME, JAVA_HOME and java on PATH that is Java 21 (another version is skipped).
set "JAVA="
if defined FA_JAVA_HOME if exist "%FA_JAVA_HOME%\bin\java.exe" call :try "%FA_JAVA_HOME%\bin\java.exe"
if not defined JAVA if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" call :try "%JAVA_HOME%\bin\java.exe"
if not defined JAVA call :try java
if not defined JAVA (
  echo Java 21 not found. Install Java 21, or set FA_JAVA_HOME to its folder. FA_JAVA_HOME, JAVA_HOME and PATH were checked.
  pause
  exit /b 1
)
findstr /b /i /c:"eula=true" eula.txt >nul 2>&1 || echo WARNING: the Minecraft EULA is not accepted. Read https://aka.ms/MinecraftEULA and set eula=true in eula.txt.

if not exist "%WINARGS%" (
  echo Installing NeoForge %NEOFORGE% ...
  "%JAVA%" -jar "%INSTALLER%" --installServer
  if not exist "%WINARGS%" (
    echo NeoForge installer failed, see %INSTALLER%.log
    pause
    exit /b 1
  )
)

set "CRASH1="
set "CRASH2="
:loop
call :crashcount BEFORE
"%JAVA%" @user_jvm_args.txt @%WINARGS% nogui
set "CODE=%ERRORLEVEL%"
call :crashcount AFTER
if exist "RESTART" (
  del "RESTART"
  echo RESTART file found, starting the server again.
  goto loop
)
if not "%CODE%"=="0" goto crashed
if %AFTER% GTR %BEFORE% goto crashed
echo The server stopped. Run start.bat to start it again.
goto end

:crashed
call :now
set "AGE=999999"
if defined CRASH1 set /a "AGE=NOW-CRASH1"
if %AGE% LEQ 300 (
  echo The server crashed three times within five minutes ^(exit code %CODE%^), not restarting. See crash-reports and logs\latest.log.
  pause
  exit /b 1
)
set "CRASH1=%CRASH2%"
set "CRASH2=%NOW%"
echo The server crashed ^(exit code %CODE%^). Restarting in %RESTART_DELAY% s.
timeout /t %RESTART_DELAY% /nobreak >nul
goto loop

:end
endlocal
pause
exit /b 0

:try
"%~1" -version 2>&1 | findstr /r /c:"version .21[^0-9]" >nul
if not errorlevel 1 set "JAVA=%~1"
exit /b 0

:crashcount
set "%~1=0"
for %%f in ("crash-reports\*.txt") do set /a "%~1+=1"
exit /b 0

:now
set "NOW=0"
for /f %%t in ('powershell -NoProfile -Command "[DateTimeOffset]::UtcNow.ToUnixTimeSeconds()"') do set "NOW=%%t"
exit /b 0
