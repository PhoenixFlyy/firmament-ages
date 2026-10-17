<#
  Firmament Ages - dedicated server launcher with update + restart loop.

  Every loop pass:
    1. packwiz-installer-bootstrap syncs mods/config/kubejs from pack.toml (server side only)
    2. if libraries\net\neoforged\neoforge\<version>\ is missing, the NeoForge installer
       (neoforge-<version>-installer.jar next to this script) installs it (--installServer)
    3. NeoForge starts (java @user_jvm_args.txt @libraries\...\win_args.txt nogui)
    4. after the server ended: a RESTART file (/restart) -> next pass at once; a crash (exit code not 0 or a new
       file in crash-reports) -> next pass after $RestartDelay seconds, the third crash within five minutes ends
       the loop; a clean stop (/stop, then /stop confirm) ends the loop.

  Usage:  .\start.ps1                       update + run
          .\start.ps1 -NoUpdate             run without the packwiz sync (offline tests)
          .\start.ps1 -PackUrl <url> -JavaHome <jdk21 folder>   override the settings below
#>
param(
    [switch]$NoUpdate,
    [string]$PackUrl  = '',
    [string]$JavaHome = ''
)

Set-Location -LiteralPath $PSScriptRoot

# ---------------------------------------------------------------- settings
# Java 21 (Temurin). Never rely on PATH: it may hold another Java version.
# Order: -JavaHome, then the environment variable FA_JAVA_HOME, then this default.
# Default: <repo>\tools\jdk-21 when the server folder is <repo>\test-server. For a live server use an absolute path.
$DefaultJavaHome = Join-Path $PSScriptRoot '..\tools\jdk-21'
# Pack source. Local PoC: "packwiz serve" in the repo. Live: raw GitHub URL of the main branch.
$DefaultPackUrl  = 'http://localhost:8080/pack.toml'
# $DefaultPackUrl = 'https://raw.githubusercontent.com/PhoenixFlyy/firmament-ages/main/pack.toml'
# Keep in sync with pack.toml [versions] neoforge. A new version needs its installer jar next to this script.
$NeoForge     = '21.1.252'
$RestartDelay = 10
# ------------------------------------------------------------------------

if ($JavaHome -eq '') { $JavaHome = if ($env:FA_JAVA_HOME) { $env:FA_JAVA_HOME } else { $DefaultJavaHome } }
if ($PackUrl -eq '')  { $PackUrl = $DefaultPackUrl }
$Java      = Join-Path $JavaHome 'bin\java.exe'
$Bootstrap = Join-Path $PSScriptRoot 'packwiz-installer-bootstrap.jar'
$Installer = Join-Path $PSScriptRoot "neoforge-$NeoForge-installer.jar"
$WinArgs   = "libraries/net/neoforged/neoforge/$NeoForge/win_args.txt"

if (-not (Test-Path -LiteralPath $Java)) {
    Write-Error "Java not found at $Java. Pass -JavaHome, set FA_JAVA_HOME or edit `$DefaultJavaHome in start.ps1."
    exit 1
}
if (-not (Select-String -LiteralPath 'eula.txt' -Pattern '^eula=true' -Quiet -ErrorAction SilentlyContinue)) {
    Write-Warning 'The Minecraft EULA is not accepted. Read https://aka.ms/MinecraftEULA and set eula=true in eula.txt.'
}

function Get-CrashCount { @(Get-ChildItem -Path 'crash-reports\*.txt' -File -ErrorAction SilentlyContinue).Count }
$Crashes = New-Object System.Collections.Generic.List[datetime]

while ($true) {
    if (-not $NoUpdate) {
        if (Test-Path -LiteralPath $Bootstrap) {
            & $Java -jar $Bootstrap -g -s server $PackUrl
            if ($LASTEXITCODE -ne 0) { Write-Warning "packwiz sync failed (exit $LASTEXITCODE), starting with the files on disk." }
        } else {
            Write-Warning "packwiz-installer-bootstrap.jar not found, skipping the pack sync."
        }
    }

    if (-not (Test-Path -LiteralPath $WinArgs)) {
        if (-not (Test-Path -LiteralPath $Installer)) {
            Write-Error "NeoForge $NeoForge is not installed and $Installer is missing. Download it from https://maven.neoforged.net/releases/net/neoforged/neoforge/$NeoForge/neoforge-$NeoForge-installer.jar"
            exit 1
        }
        Write-Host "Installing NeoForge $NeoForge ..."
        & $Java -jar $Installer --installServer
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $WinArgs)) {
            Write-Error "NeoForge installer failed (exit $LASTEXITCODE), see $Installer.log"
            exit 1
        }
    }

    $Before = Get-CrashCount
    & $Java '@user_jvm_args.txt' "@$WinArgs" nogui
    $Code = $LASTEXITCODE

    if (Test-Path -LiteralPath 'RESTART') {
        Remove-Item -LiteralPath 'RESTART'
        Write-Host 'RESTART file found, next pass.'
        continue
    }
    if ($Code -eq 0 -and (Get-CrashCount) -le $Before) {
        Write-Host 'The server stopped, leaving the loop.'
        break
    }
    $Now = Get-Date
    if ($Crashes.Count -ge 2 -and ($Now - $Crashes[$Crashes.Count - 2]).TotalSeconds -le 300) {
        Write-Host "The server crashed three times within five minutes (exit code $Code), leaving the loop."
        break
    }
    $Crashes.Add($Now)
    Write-Host "The server crashed (exit code $Code). Next pass in $RestartDelay s."
    Start-Sleep -Seconds $RestartDelay
}
