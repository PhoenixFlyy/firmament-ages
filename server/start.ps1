<#
  Firmament Ages - dedicated server launcher with update + restart loop.

  Every loop pass:
    1. packwiz-installer-bootstrap syncs mods/config/kubejs from pack.toml (server side only)
    2. ServerStarterJar (server.jar) starts NeoForge; it installs NeoForge first if needed
    3. after "stop" or a crash the loop waits and starts again
  End the loop for good: create a file named STOP next to this script, then type "stop" in the console.

  Usage:  .\start.ps1            update + run
          .\start.ps1 -NoUpdate  run without the packwiz sync (offline tests)
#>
param([switch]$NoUpdate)

Set-Location -LiteralPath $PSScriptRoot

# ---------------------------------------------------------------- settings
# Java 21 (Temurin). Never rely on PATH: on this host PATH points to Corretto 25.
# Default: <repo>\tools\jdk-21 when the server folder is <repo>\test-server. For a live server use an absolute path.
$JavaHome     = Join-Path $PSScriptRoot '..\tools\jdk-21'
$Java         = Join-Path $JavaHome 'bin\java.exe'
# Pack source. Local PoC: "packwiz serve" in the repo. Live: raw GitHub URL of the main branch.
$PackUrl      = 'http://localhost:8080/pack.toml'
# $PackUrl    = 'https://raw.githubusercontent.com/<user>/firmament-ages/main/pack.toml'
$Bootstrap    = Join-Path $PSScriptRoot 'packwiz-installer-bootstrap.jar'
# Keep in sync with pack.toml [versions] neoforge.
$NeoForge     = '21.1.252'
$RestartDelay = 10
# ------------------------------------------------------------------------

if (-not (Test-Path -LiteralPath $Java)) {
    Write-Error "Java not found at $Java. Install Temurin 21 into tools\jdk-21 or edit `$JavaHome in start.ps1."
    exit 1
}
if (-not (Test-Path -LiteralPath 'eula.txt')) {
    Write-Warning 'eula.txt is missing. Read https://aka.ms/MinecraftEULA and create eula.txt with eula=true yourself.'
}

$Marker = Join-Path $PSScriptRoot '.installed-neoforge'
while ($true) {
    if (-not $NoUpdate) {
        if (Test-Path -LiteralPath $Bootstrap) {
            & $Java -jar $Bootstrap -g -s server $PackUrl
            if ($LASTEXITCODE -ne 0) { Write-Warning "packwiz sync failed (exit $LASTEXITCODE), starting with the files on disk." }
        } else {
            Write-Warning "packwiz-installer-bootstrap.jar not found, skipping the pack sync."
        }
    }

    # Reinstall NeoForge only when the pinned version changed (ServerStarterJar --installer-force).
    $Installed = if (Test-Path -LiteralPath $Marker) { (Get-Content -LiteralPath $Marker -Raw).Trim() } else { '' }
    $SsjArgs = @('--installer', $NeoForge)
    if ($Installed -ne $NeoForge) { $SsjArgs += '--installer-force' }

    & $Java '@user_jvm_args.txt' -jar server.jar @SsjArgs nogui
    $Code = $LASTEXITCODE
    if ($Code -eq 0) { Set-Content -LiteralPath $Marker -Value $NeoForge -Encoding ascii }

    if (Test-Path -LiteralPath 'STOP') {
        Remove-Item -LiteralPath 'STOP'
        Write-Host 'STOP file found, leaving the restart loop.'
        break
    }
    Write-Host "Server exited with code $Code. Restarting in $RestartDelay s (create a file named STOP to end the loop)."
    Start-Sleep -Seconds $RestartDelay
}
