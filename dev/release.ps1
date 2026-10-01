<#
  Firmament Ages - build the release exports (Windows PowerShell 5.1 compatible).

  Steps:
    0. line-ending check: a served text file with CRLF in the working tree while git stores LF
       gets a different hash in index.toml than the file GitHub serves, and packwiz-installer
       then fails with "Hash invalid!". The script stops; -FixLineEndings rewrites those files with LF
       (git content unchanged).
    1. packwiz refresh (index.toml, pack.toml hash)
    2. optional: set version = "<Version>" in pack.toml, refresh again
    3. packwiz modrinth export   -> dev/exports/FirmamentAges-<version>.mrpack
    4. packwiz curseforge export -> dev/exports/FirmamentAges-<version>-curseforge.zip (client side)
    5. SHA-256 list               -> dev/exports/FirmamentAges-<version>.sha256.txt
  Never commits, tags or pushes. Both exports embed jars that may not be redistributed
  (CurseForge-only mods in the .mrpack, Modrinth mods in the CurseForge zip): send them privately only.

  Usage:  powershell -NoProfile -ExecutionPolicy Bypass -File dev\release.ps1
          powershell -NoProfile -ExecutionPolicy Bypass -File dev\release.ps1 -Version 0.5.0
#>
param(
    [string]$Version = '',
    [switch]$FixLineEndings
)

$ErrorActionPreference = 'Stop'
$Repo    = Split-Path -Parent $PSScriptRoot
$Packwiz = Join-Path $Repo 'tools\packwiz.exe'
$OutDir  = Join-Path $PSScriptRoot 'exports'
$PackToml = Join-Path $Repo 'pack.toml'

if (-not (Test-Path -LiteralPath $Packwiz)) { throw "packwiz not found at $Packwiz" }
Set-Location -LiteralPath $Repo

function Invoke-Packwiz {
    & $Packwiz @args
    if ($LASTEXITCODE -ne 0) { throw "packwiz $($args -join ' ') failed with exit code $LASTEXITCODE" }
}

# Served text files whose working copy is not LF although .gitattributes says eol=lf.
$crlf = @()
foreach ($line in (& git ls-files --eol)) {
    $parts = $line -split "`t", 2
    if ($parts.Count -ne 2) { continue }
    $info = $parts[0]; $path = $parts[1]
    if ($info -match 'w/(crlf|mixed)' -and $info -match 'eol=lf' -and $path -notmatch '^(dev|server|mod|tools)/') { $crlf += $path }
}
if ($crlf.Count -gt 0) {
    if (-not $FixLineEndings) {
        $crlf | ForEach-Object { Write-Host "  CRLF: $_" }
        throw "$($crlf.Count) served file(s) have CRLF line endings; GitHub serves LF, so their hashes would not match. Re-run with -FixLineEndings."
    }
    foreach ($path in $crlf) {
        $full = Join-Path $Repo $path
        $text = [IO.File]::ReadAllText($full) -replace "`r`n", "`n"
        [IO.File]::WriteAllText($full, $text, (New-Object Text.UTF8Encoding($false)))
        Write-Host "  LF: $path"
    }
}

Invoke-Packwiz refresh

if ($Version -ne '') {
    if ($Version -notmatch '^\d+\.\d+\.\d+([-+][0-9A-Za-z.-]+)?$') { throw "Version '$Version' is not X.Y.Z" }
    $text = [IO.File]::ReadAllText($PackToml)
    $new  = [regex]::Replace($text, '(?m)^version = "[^"]*"', "version = `"$Version`"")
    # UTF-8 without BOM, packwiz writes the same.
    [IO.File]::WriteAllText($PackToml, $new, (New-Object Text.UTF8Encoding($false)))
    Invoke-Packwiz refresh
}

$m = [regex]::Match([IO.File]::ReadAllText($PackToml), '(?m)^version = "([^"]*)"')
if (-not $m.Success) { throw 'No version line in pack.toml' }
$PackVersion = $m.Groups[1].Value

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$Mrpack = Join-Path $OutDir "FirmamentAges-$PackVersion.mrpack"
$CfZip  = Join-Path $OutDir "FirmamentAges-$PackVersion-curseforge.zip"
foreach ($f in @($Mrpack, $CfZip)) { if (Test-Path -LiteralPath $f) { Remove-Item -LiteralPath $f } }

Invoke-Packwiz modrinth export -o $Mrpack
Invoke-Packwiz curseforge export -s client -o $CfZip

$SumFile = Join-Path $OutDir "FirmamentAges-$PackVersion.sha256.txt"
$lines = foreach ($f in @($Mrpack, $CfZip)) {
    $h = (Get-FileHash -LiteralPath $f -Algorithm SHA256).Hash.ToLower()
    "$h  $(Split-Path -Leaf $f)"
}
[IO.File]::WriteAllLines($SumFile, [string[]]$lines, (New-Object Text.UTF8Encoding($false)))

Write-Host ''
Write-Host "Firmament Ages $PackVersion"
foreach ($f in @($Mrpack, $CfZip)) {
    Write-Host ('  {0}  {1:N1} MB' -f (Split-Path -Leaf $f), ((Get-Item -LiteralPath $f).Length / 1MB))
}
Write-Host "  $(Split-Path -Leaf $SumFile)"
Write-Host ''
Write-Host 'Next (by hand): git add pack.toml index.toml; git commit; merge dev into main; tag; push.'
