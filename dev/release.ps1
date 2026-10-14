<#
  Firmament Ages - build the release exports (Windows PowerShell 5.1 compatible).

  Steps:
    0. line-ending check: a served text file with CRLF in the working tree while git stores LF
       gets a different hash in index.toml than the file GitHub serves, and packwiz-installer
       then fails with "Hash invalid!". The script stops; -FixLineEndings rewrites those files with LF
       (git content unchanged).
    1. packwiz refresh (index.toml, pack.toml hash)
    2. optional: set version = "<Version>" in pack.toml and the title screen's version line
       (config/fancymenu/customization/title_screen_layout.txt, "source = Firmament Ages <Version>"), refresh
       again. Without -Version the script stops if the title line and pack.toml disagree.
    3. packwiz modrinth export   -> dev/exports/FirmamentAges-<version>.mrpack
    4. packwiz curseforge export -> dev/exports/FirmamentAges-<version>-curseforge.zip (client side)
    5. server pack                -> dev/exports/FirmamentAges-<version>-server.zip, the "Additional File"
       (server pack) of the CurseForge upload: a runnable server folder, not a manifest. `packwiz curseforge
       export -s server` would only write a manifest zip, which CurseForge does not install for servers.
       The script serves the repo with `packwiz serve` on -ServePort, lets packwiz-installer-bootstrap
       (-g -s server) fill dev/exports/server-work/ with every server-side file (the same sync as a live
       server), adds server/start.bat, start.ps1, user_jvm_args.txt, server.properties (from the template),
       README-SERVERPACK.md and tools/neoforge-<version>-installer.jar, and zips it. -SkipServerPack skips it.
    6. SHA-256 list               -> dev/exports/FirmamentAges-<version>.sha256.txt
  Never commits, tags or pushes. All exports embed jars that may not be redistributed
  (CurseForge-only mods in the .mrpack, Modrinth mods in the CurseForge zip, every server jar in the server
  pack): send them privately only. The CurseForge upload zip itself is dev/cf/export_cf.py.

  Usage:  powershell -NoProfile -ExecutionPolicy Bypass -File dev\release.ps1
          powershell -NoProfile -ExecutionPolicy Bypass -File dev\release.ps1 -Version 0.5.0
          powershell -NoProfile -ExecutionPolicy Bypass -File dev\release.ps1 -SkipServerPack
#>
param(
    [string]$Version = '',
    [switch]$FixLineEndings,
    [switch]$SkipServerPack,
    [int]$ServePort = 8091
)

$ErrorActionPreference = 'Stop'
$Repo    = Split-Path -Parent $PSScriptRoot
$Packwiz = Join-Path $Repo 'tools\packwiz.exe'
$OutDir  = Join-Path $PSScriptRoot 'exports'
$PackToml = Join-Path $Repo 'pack.toml'
$Java     = Join-Path $Repo 'tools\jdk-21\bin\java.exe'
$Bootstrap = Join-Path $Repo 'tools\packwiz-installer-bootstrap.jar'
$TitleLayout = Join-Path $Repo 'config\fancymenu\customization\title_screen_layout.txt'
$TitleVersionLine = New-Object Text.RegularExpressions.Regex('(?m)^([ \t]*source = Firmament Ages )(\S+)')

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
    # The title screen's version line is fixed text in the FancyMenu layout (element fa_title_version).
    $title = [IO.File]::ReadAllText($TitleLayout)
    if ($TitleVersionLine.Matches($title).Count -ne 1) { throw "Expected one 'source = Firmament Ages <v>' line in $TitleLayout" }
    $title = $TitleVersionLine.Replace($title, "`${1}$Version")
    [IO.File]::WriteAllText($TitleLayout, $title, (New-Object Text.UTF8Encoding($false)))
    Invoke-Packwiz refresh
}

$m = [regex]::Match([IO.File]::ReadAllText($PackToml), '(?m)^version = "([^"]*)"')
if (-not $m.Success) { throw 'No version line in pack.toml' }
$PackVersion = $m.Groups[1].Value
# A release must never show an old number on the title screen.
$tm = $TitleVersionLine.Match([IO.File]::ReadAllText($TitleLayout))
if (-not $tm.Success -or $tm.Groups[2].Value -ne $PackVersion) {
    throw "Title screen says '$($tm.Groups[2].Value)', pack.toml says '$PackVersion'. Run with -Version $PackVersion."
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$Mrpack = Join-Path $OutDir "FirmamentAges-$PackVersion.mrpack"
$CfZip  = Join-Path $OutDir "FirmamentAges-$PackVersion-curseforge.zip"
$ServerZip = Join-Path $OutDir "FirmamentAges-$PackVersion-server.zip"
foreach ($f in @($Mrpack, $CfZip, $ServerZip)) { if (Test-Path -LiteralPath $f) { Remove-Item -LiteralPath $f } }

Invoke-Packwiz modrinth export -o $Mrpack
Invoke-Packwiz curseforge export -s client -o $CfZip
$Exports = @($Mrpack, $CfZip)

if (-not $SkipServerPack) {
    $m = [regex]::Match([IO.File]::ReadAllText($PackToml), '(?m)^neoforge = "([^"]*)"')
    if (-not $m.Success) { throw 'No neoforge line in pack.toml' }
    $NeoInstaller = Join-Path $Repo "tools\neoforge-$($m.Groups[1].Value)-installer.jar"
    foreach ($f in @($Java, $Bootstrap, $NeoInstaller)) { if (-not (Test-Path -LiteralPath $f)) { throw "Missing $f" } }

    $Work = Join-Path $OutDir 'server-work'
    if (Test-Path -LiteralPath $Work) { Remove-Item -LiteralPath $Work -Recurse -Force }
    New-Item -ItemType Directory -Path $Work | Out-Null
    Copy-Item -LiteralPath $Bootstrap -Destination $Work

    # The same server-side sync a live server does, from a local packwiz serve of this working tree.
    $serve = Start-Process -FilePath $Packwiz -ArgumentList @('serve', '-p', "$ServePort") -WorkingDirectory $Repo -PassThru -WindowStyle Hidden
    try {
        $url = "http://localhost:$ServePort/pack.toml"
        $deadline = (Get-Date).AddSeconds(60)
        while ($true) {
            try { Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5 | Out-Null; break }
            catch { if ((Get-Date) -gt $deadline) { throw "packwiz serve did not answer on $url" }; Start-Sleep -Milliseconds 500 }
        }
        Push-Location -LiteralPath $Work
        try {
            & $Java -jar (Join-Path $Work 'packwiz-installer-bootstrap.jar') -g -s server $url
            if ($LASTEXITCODE -ne 0) { throw "packwiz-installer failed with exit code $LASTEXITCODE" }
        } finally { Pop-Location }
    } finally {
        Stop-Process -Id $serve.Id -Force -ErrorAction SilentlyContinue
    }

    foreach ($f in @('packwiz-installer-bootstrap.jar', 'packwiz-installer.jar', 'packwiz.json')) {
        $p = Join-Path $Work $f
        if (Test-Path -LiteralPath $p) { Remove-Item -LiteralPath $p -Force }
    }
    $Server = Join-Path $Repo 'server'
    foreach ($f in @('start.bat', 'start.ps1', 'user_jvm_args.txt', 'README-SERVERPACK.md')) {
        Copy-Item -LiteralPath (Join-Path $Server $f) -Destination $Work
    }
    Copy-Item -LiteralPath (Join-Path $Server 'server.properties.template') -Destination (Join-Path $Work 'server.properties')
    Copy-Item -LiteralPath $NeoInstaller -Destination $Work

    # Entry names with '/': ZipFile.CreateFromDirectory of .NET Framework writes '\', which Linux hosts unpack as
    # flat file names.
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::Open($ServerZip, [IO.Compression.ZipArchiveMode]::Create)
    try {
        $root = (Resolve-Path -LiteralPath $Work).Path.TrimEnd('\') + '\'
        foreach ($file in (Get-ChildItem -LiteralPath $Work -Recurse -File)) {
            $name = $file.FullName.Substring($root.Length).Replace('\', '/')
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $file.FullName, $name, [IO.Compression.CompressionLevel]::Optimal) | Out-Null
        }
    } finally { $zip.Dispose() }
    $Exports += $ServerZip
}

$SumFile = Join-Path $OutDir "FirmamentAges-$PackVersion.sha256.txt"
$lines = foreach ($f in $Exports) {
    $h = (Get-FileHash -LiteralPath $f -Algorithm SHA256).Hash.ToLower()
    "$h  $(Split-Path -Leaf $f)"
}
[IO.File]::WriteAllLines($SumFile, [string[]]$lines, (New-Object Text.UTF8Encoding($false)))

Write-Host ''
Write-Host "Firmament Ages $PackVersion"
foreach ($f in $Exports) {
    Write-Host ('  {0}  {1:N1} MB' -f (Split-Path -Leaf $f), ((Get-Item -LiteralPath $f).Length / 1MB))
}
Write-Host "  $(Split-Path -Leaf $SumFile)"
Write-Host ''
Write-Host 'Next (by hand): git add pack.toml index.toml; git commit; merge dev into main; tag; push.'
