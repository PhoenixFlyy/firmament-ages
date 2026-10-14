@echo off
rem Firmament Ages server: installs NeoForge on the first start, then runs the server in a restart loop.
rem End the loop for good: create a file named STOP next to this script, then type "stop" in the console.
setlocal
cd /d "%~dp0"

set "NEOFORGE={{NEOFORGE}}"
set "RESTART_DELAY=10"
set "INSTALLER=neoforge-%NEOFORGE%-installer.jar"
set "WINARGS=libraries\net\neoforged\neoforge\%NEOFORGE%\win_args.txt"

rem Java 21: FA_JAVA_HOME, then JAVA_HOME, then java on PATH.
set "JAVA="
if defined FA_JAVA_HOME if exist "%FA_JAVA_HOME%\bin\java.exe" set "JAVA=%FA_JAVA_HOME%\bin\java.exe"
if not defined JAVA if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"
if not defined JAVA set "JAVA=java"
"%JAVA%" -version 2>&1 | findstr /r /c:"version .21[^0-9]" >nul
if errorlevel 1 (
  echo Java 21 not found. Install Java 21 and put it on PATH, or set FA_JAVA_HOME to its folder.
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
