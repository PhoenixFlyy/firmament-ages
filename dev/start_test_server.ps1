# Starts the Firmament Ages test server (test-server/) in the foreground for an open-ended test.
# Console commands can be typed directly (op <name>, whitelist add <name>); "stop", then "stop confirm" ends the server.
# Run from anywhere:  powershell -NoProfile -ExecutionPolicy Bypass -File dev\start_test_server.ps1
# Fresh world: delete test-server\world first. Automated boots with commands and a timeout: dev/run_server.py.
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$server = Join-Path $repo 'test-server'
$java = Join-Path $repo 'tools\jdk-21\bin\java.exe'
$argsFile = 'libraries\net\neoforged\neoforge\21.1.252\win_args.txt'
if (-not (Test-Path $java)) { throw "Java 21 not found: $java" }
# JVM flags: server\user_jvm_args.txt (what the server pack ships, Aikar's G1 set) with an 8 GB heap, like dev/run_server.py.
$heap = '8G'
$jvmFlags = @("-Xms$heap", "-Xmx$heap") + @(Get-Content (Join-Path $repo 'server\user_jvm_args.txt') |
    Where-Object { $_ -notmatch '^\s*#' } | ForEach-Object { -split $_ } | Where-Object { $_ -notmatch '^-Xm[sx]' })
if (-not (Test-Path (Join-Path $server $argsFile))) { throw "NeoForge args file not found: $(Join-Path $server $argsFile) (run the NeoForge installer in test-server first)" }
Push-Location $server
try {
    & $java @jvmFlags "@$argsFile" nogui
} finally {
    Pop-Location
}
