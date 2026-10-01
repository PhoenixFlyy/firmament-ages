<#
  Firmament Ages - build the friends' Prism instance zip (Windows PowerShell 5.1 compatible).

  Source: the dev instance tools\PrismLauncher\instances\FirmamentAgesDev. Taken from it:
    instance.cfg   (rewritten: name, pack URL, Java and memory settings for a friend's PC)
    mmc-pack.json  (Minecraft 1.21.1 + NeoForge, unchanged)
    minecraft\packwiz-installer-bootstrap.jar
  Never taken: the mods, config, kubejs and defaultconfigs folders (the pre-launch sync installs them),
  saves, logs, options, and any account data (accounts live in tools\PrismLauncher\accounts.json,
  outside the instance, and are never read here).
  Plus dev\friends\README-FRIENDS.md at the zip root.

  Output: dev\exports\FirmamentAges-Prism.zip
  Usage:  powershell -NoProfile -ExecutionPolicy Bypass -File dev\make_friends_zip.ps1
          ... -PackUrl https://raw.githubusercontent.com/PhoenixFlyy/firmament-ages/main/pack.toml -MaxMemMB 8192
#>
param(
    [string]$PackUrl  = 'https://raw.githubusercontent.com/PhoenixFlyy/firmament-ages/main/pack.toml',
    [int]$MaxMemMB    = 8192,
    [int]$MinMemMB    = 4096
)

$ErrorActionPreference = 'Stop'
$Repo     = Split-Path -Parent $PSScriptRoot
$Instance = Join-Path $Repo 'tools\PrismLauncher\instances\FirmamentAgesDev'
$OutDir   = Join-Path $PSScriptRoot 'exports'
$OutZip   = Join-Path $OutDir 'FirmamentAges-Prism.zip'
$Readme   = Join-Path $PSScriptRoot 'friends\README-FRIENDS.md'
$Bootstrap = Join-Path $Instance 'minecraft\packwiz-installer-bootstrap.jar'
if (-not (Test-Path -LiteralPath $Bootstrap)) { $Bootstrap = Join-Path $Repo 'tools\packwiz-installer-bootstrap.jar' }

foreach ($f in @((Join-Path $Instance 'instance.cfg'), (Join-Path $Instance 'mmc-pack.json'), $Bootstrap, $Readme)) {
    if (-not (Test-Path -LiteralPath $f)) { throw "Missing $f" }
}

# instance.cfg: keep the dev instance's keys, override the machine- and dev-specific ones, drop the Java path.
$set = [ordered]@{
    'name'                 = 'Firmament Ages'
    'notes'                = 'Firmament Ages. Before every launch packwiz-installer syncs mods, configs and scripts from ' + $PackUrl
    'iconKey'              = 'default'
    'OverrideJava'         = 'false'
    'OverrideJavaLocation' = 'false'
    'AutomaticJava'        = 'true'
    'OverrideMemory'       = 'true'
    'MinMemAlloc'          = "$MinMemMB"
    'MaxMemAlloc'          = "$MaxMemMB"
    'OverrideCommands'     = 'true'
    'PreLaunchCommand'     = '"$INST_JAVA" -jar packwiz-installer-bootstrap.jar ' + $PackUrl
    'WrapperCommand'       = ''
    'PostExitCommand'      = ''
}
$drop = @('JavaPath', 'JavaVersion', 'JavaArchitecture', 'JavaRealArchitecture', 'JavaVendor', 'JavaSignature',
          'lastLaunchTime', 'lastTimePlayed', 'totalTimePlayed', 'linkedInstances', 'ManagedPack', 'ManagedPackID',
          'ManagedPackName', 'ManagedPackType', 'ManagedPackVersionID', 'ManagedPackVersionName')
$lines = New-Object System.Collections.Generic.List[string]
$seen = @{}
foreach ($line in [IO.File]::ReadAllLines((Join-Path $Instance 'instance.cfg'))) {
    $i = $line.IndexOf('=')
    if ($i -lt 1 -or $line.StartsWith('[')) { $lines.Add($line); continue }
    $key = $line.Substring(0, $i)
    if ($drop -contains $key) { continue }
    if ($set.Contains($key)) { $lines.Add("$key=$($set[$key])"); $seen[$key] = $true } else { $lines.Add($line) }
}
foreach ($key in $set.Keys) { if (-not $seen[$key]) { $lines.Add("$key=$($set[$key])") } }
$cfg = ($lines -join "`n") + "`n"

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
if (Test-Path -LiteralPath $OutZip) { Remove-Item -LiteralPath $OutZip }

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
# Forward slashes in entry names (Compress-Archive in 5.1 writes backslashes, which Prism mis-reads).
$zip = [IO.Compression.ZipFile]::Open($OutZip, 'Create')
try {
    $e = $zip.CreateEntry('instance.cfg')
    $w = New-Object IO.StreamWriter($e.Open(), (New-Object Text.UTF8Encoding($false)))
    $w.Write($cfg); $w.Dispose()
    [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, (Join-Path $Instance 'mmc-pack.json'), 'mmc-pack.json')
    [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $Bootstrap, 'minecraft/packwiz-installer-bootstrap.jar')
    [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $Readme, 'README-FRIENDS.md')
} finally {
    $zip.Dispose()
}

$z = [IO.Compression.ZipFile]::OpenRead($OutZip)
Write-Host "Built $OutZip"
foreach ($entry in $z.Entries) { Write-Host ('  {0,-45} {1,10:N0} bytes' -f $entry.FullName, $entry.Length) }
$z.Dispose()
Write-Host "Pack URL: $PackUrl"
