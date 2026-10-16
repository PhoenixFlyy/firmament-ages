@echo off
rem Firmament Ages server: installs NeoForge on the first start, then runs the server in a restart loop.
rem End the loop for good: create a file named STOP next to this script, then type "stop" in the console.
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
if not exist "eula.txt" echo WARNING: eula.txt is missing. Read https://aka.ms/MinecraftEULA and create eula.txt with eula=true.

if not exist "%WINARGS%" (
  echo Installing NeoForge %NEOFORGE% ...
  "%JAVA%" -jar "%INSTALLER%" --installServer
  if not exist "%WINARGS%" (
    echo NeoForge installer failed, see %INSTALLER%.log
    pause
    exit /b 1
  )
)

:loop
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
exit /b 0

:try
"%~1" -version 2>&1 | findstr /r /c:"version .21[^0-9]" >nul
if not errorlevel 1 set "JAVA=%~1"
exit /b 0
