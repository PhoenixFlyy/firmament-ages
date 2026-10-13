# Starts the Firmament Ages test server (test-server/) in the foreground for an open-ended test.
# Console commands can be typed directly (op <name>, whitelist add <name>, stop); "stop" ends the server.
# Run from anywhere:  powershell -NoProfile -ExecutionPolicy Bypass -File dev\start_test_server.ps1
# Fresh world: delete test-server\world first. Automated boots with commands and a timeout: dev/run_server.py.
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$server = Join-Path $repo 'test-server'
$java = Join-Path $repo 'tools\jdk-21\bin\java.exe'
$argsFile = 'libraries\net\neoforged\neoforge\21.1.252\win_args.txt'
if (-not (Test-Path $java)) { throw "Java 21 not found: $java" }
if (-not (Test-Path (Join-Path $server $argsFile))) { throw "NeoForge args file not found: $(Join-Path $server $argsFile) (run the NeoForge installer in test-server first)" }
Push-Location $server
try {
    & $java -Xms6G -Xmx8G -XX:+UseG1GC -XX:+UnlockExperimentalVMOptions -XX:MaxGCPauseMillis=100 "@$argsFile" nogui
} finally {
    Pop-Location
}
