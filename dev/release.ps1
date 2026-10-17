<#
  Firmament Ages - build the release exports (Windows PowerShell 5.1 compatible).

  Steps:
    0. line-ending check: a served text file with CRLF in the working tree while git stores LF
       gets a different hash in index.toml than the file GitHub serves, and packwiz-installer
       then fails with "Hash invalid!". The script stops; -FixLineEndings rewrites those files with LF
       (git content unchanged).
    1. packwiz refresh (index.toml, pack.toml hash)
    2. optional: set version = "<Version>" in pack.toml, the title screen's version line
       (config/fancymenu/customization/title_screen_layout.txt, "source = Firmament Ages <Version>") and
       modpackVersion in config/bcc-common.toml (the server list tooltip), refresh again. Without -Version the
       script stops if the title line, bcc-common.toml and pack.toml disagree.
    3. packwiz modrinth export   -> dev/exports/FirmamentAges-<version>.mrpack
    4. packwiz curseforge export -> dev/exports/FirmamentAges-<version>-curseforge.zip (client side)
    5. server pack                -> dev/exports/FirmamentAges-<version>-server.zip, the "Additional File"
       (server pack) of the CurseForge upload: a runnable server folder, not a manifest. `packwiz curseforge
       export -s server` would only write a manifest zip, which CurseForge does not install for servers.
       The script serves the repo with `packwiz serve` on -ServePort, lets packwiz-installer-bootstrap
       (-g -s server) fill dev/exports/server-work/ with every server-side file (the same sync as a live
       server), adds the server pack's start.bat and start.sh (server/pack/, without the pack sync),
       server/user_jvm_args.txt with an 8 GB heap, server.properties (from the template), eula.txt (eula=false),
       README-SERVERPACK.md and tools/neoforge-<version>-installer.jar, and zips it. -SkipServerPack skips it.
    6. SHA-256 list               -> dev/exports/FirmamentAges-<version>.sha256.txt
    7. optional, -LocalServer or -LocalServerDir <path>: Felix' local test copy. Unpacks the server zip into
       <path> (default: FirmamentAges-Server-<version> next to the repo folder), replacing the folder but keeping
       world/, logs/, simplebackups/, ops.json, whitelist.json and an eula.txt that says eula=true. Sets
       white-list=false and an empty server-ip in server.properties and adds
       `if not defined FA_JAVA_HOME set "FA_JAVA_HOME=<repo>\tools\jdk-21"` after `setlocal` in start.bat (this
       machine has no system Java 21). There is no second start script: start.bat is the only one.
  Never commits, tags or pushes. All exports embed jars that may not be redistributed
  (CurseForge-only mods in the .mrpack, Modrinth mods in the CurseForge zip, every server jar in the server
  pack): send them privately only. The CurseForge upload zip itself is dev/cf/export_cf.py.

  Usage:  powershell -NoProfile -ExecutionPolicy Bypass -File dev\release.ps1
          powershell -NoProfile -ExecutionPolicy Bypass -File dev\release.ps1 -Version 0.5.0
          powershell -NoProfile -ExecutionPolicy Bypass -File dev\release.ps1 -SkipServerPack
          powershell -NoProfile -ExecutionPolicy Bypass -File dev\release.ps1 -Version 1.0.3 -LocalServer
          powershell -NoProfile -ExecutionPolicy Bypass -File dev\release.ps1 -LocalServerDir D:\Temp\fa-server
#>
param(
    [string]$Version = '',
    [switch]$FixLineEndings,
    [switch]$SkipServerPack,
    [int]$ServePort = 8091,
    [switch]$LocalServer,
    [string]$LocalServerDir = ''
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
# Better Compatibility Checker (server list tooltip): the pack version in config/bcc-common.toml, same number as pack.toml.
$BccConfig = Join-Path $Repo 'config\bcc-common.toml'
$BccVersionLine = New-Object Text.RegularExpressions.Regex('(?m)^([ \t]*modpackVersion = ")([^"]*)(")')

if (-not (Test-Path -LiteralPath $Packwiz)) { throw "packwiz not found at $Packwiz" }
Set-Location -LiteralPath $Repo

function Invoke-Packwiz {
    & $Packwiz @args
    if ($LASTEXITCODE -ne 0) { throw "packwiz $($args -join ' ') failed with exit code $LASTEXITCODE" }
}

# Felix' local test copy (-LocalServer / -LocalServerDir): the server zip unpacked into $Dir, ready for start.bat.
# The new folder is built next to $Dir first; the old $Dir is renamed to "<Dir>.old" (this fails while a server runs
# in it, before anything is lost), the new one takes its place, the files worth keeping move over and "<Dir>.old"
# goes. A leftover "<Dir>.old" stops the script: it may hold the only copy of a world.
function Install-LocalServer {
    param([string]$Zip, [string]$Dir, [string]$JdkHome, [string]$Repo)
    $Utf8 = New-Object Text.UTF8Encoding($false)
    $Keep = @('world', 'logs', 'simplebackups', 'ops.json', 'whitelist.json')
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    # One rename or nothing. Move-Item on a folder with an open file inside (a running server) moves the rest and
    # leaves a split folder behind; Directory.Move and File.Move fail as a whole.
    function Move-Whole([string]$From, [string]$To) {
        if (Test-Path -LiteralPath $To) { Remove-Item -LiteralPath $To -Recurse -Force }
        if ((Get-Item -LiteralPath $From -Force).PSIsContainer) { [IO.Directory]::Move($From, $To) } else { [IO.File]::Move($From, $To) }
    }

    $Dir = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Dir).TrimEnd('\')
    $r = $Repo.TrimEnd('\')
    $ic = [StringComparison]::OrdinalIgnoreCase
    if ($Dir -eq '' -or [IO.Path]::GetPathRoot($Dir).TrimEnd('\').Equals($Dir, $ic)) { throw "-LocalServerDir '$Dir' is a drive root" }
    if ($r.Equals($Dir, $ic) -or $r.StartsWith($Dir + '\', $ic) -or $Dir.StartsWith($r + '\', $ic)) {
        throw "-LocalServerDir '$Dir' is the repo, a parent of it or inside it"
    }
    if (-not (Test-Path -LiteralPath $Zip)) { throw "Server zip $Zip not found (run without -SkipServerPack)" }
    if (Test-Path -LiteralPath $Dir) {
        $isServer = (Test-Path -LiteralPath (Join-Path $Dir 'server.properties')) -or (Test-Path -LiteralPath (Join-Path $Dir 'start.bat'))
        $isEmpty = -not (Get-ChildItem -LiteralPath $Dir -Force | Select-Object -First 1)
        if (-not $isServer -and -not $isEmpty) { throw "'$Dir' exists and is no server folder (no server.properties, no start.bat); delete it by hand" }
    }
    $Stage = "$Dir.staging"
    $Old = "$Dir.old"
    if (Test-Path -LiteralPath $Old) { throw "'$Old' is left over from an earlier run and may hold a world; check it, then delete it by hand" }
    if (Test-Path -LiteralPath $Stage) { Remove-Item -LiteralPath $Stage -Recurse -Force }

    Write-Host "Local server: unpacking $(Split-Path -Leaf $Zip) ..."
    [IO.Compression.ZipFile]::ExtractToDirectory($Zip, $Stage)

    # server.properties: no whitelist, no bound address (reachable on localhost and in the LAN).
    $propsPath = Join-Path $Stage 'server.properties'
    $props = [IO.File]::ReadAllText($propsPath)
    foreach ($key in @('white-list', 'server-ip')) {
        $re = New-Object Text.RegularExpressions.Regex("(?m)^$key=[^\r\n]*")
        if ($re.Matches($props).Count -ne 1) { throw "Expected one '$key=' line in server.properties of the zip" }
    }
    $props = [regex]::Replace($props, '(?m)^white-list=[^\r\n]*', 'white-list=false')
    $props = [regex]::Replace($props, '(?m)^server-ip=[^\r\n]*', 'server-ip=')
    [IO.File]::WriteAllText($propsPath, $props, $Utf8)

    # start.bat: this machine has no system Java 21, so the repo's JDK is the fallback (FA_JAVA_HOME still wins).
    $batPath = Join-Path $Stage 'start.bat'
    $bat = [IO.File]::ReadAllText($batPath)
    $setlocal = New-Object Text.RegularExpressions.Regex('(?m)^setlocal(?=\r?$)')
    if ($setlocal.Matches($bat).Count -ne 1) { throw "Expected one 'setlocal' line in start.bat of the zip" }
    $javaLine = "if not defined FA_JAVA_HOME set `"FA_JAVA_HOME=$JdkHome`""
    $at = $setlocal.Match($bat)
    $bat = $bat.Insert($at.Index + $at.Length, "`r`nrem Local copy (release.ps1 -LocalServerDir): the Java 21 of the pack repo unless FA_JAVA_HOME is set.`r`n$javaLine")
    if (([regex]::Matches($bat, [regex]::Escape($javaLine))).Count -ne 1) { throw 'The FA_JAVA_HOME line did not land in start.bat' }
    [IO.File]::WriteAllText($batPath, $bat, $Utf8)

    # Swap: old folder aside, new one in place, keepers over, old folder away.
    try {
        if (Test-Path -LiteralPath $Dir) { Move-Whole $Dir $Old }
    } catch {
        Remove-Item -LiteralPath $Stage -Recurse -Force -ErrorAction SilentlyContinue
        throw "Cannot replace '$Dir' (a server still running in it?): $($_.Exception.Message)"
    }
    try {
        Move-Whole $Stage $Dir
        if (Test-Path -LiteralPath $Old) {
            foreach ($name in $Keep) {
                $src = Join-Path $Old $name
                if (Test-Path -LiteralPath $src) { Move-Whole $src (Join-Path $Dir $name); Write-Host "  kept: $name" }
            }
            $eula = Join-Path $Old 'eula.txt'
            if ((Test-Path -LiteralPath $eula) -and ([IO.File]::ReadAllText($eula) -match '(?mi)^eula=true\s*$')) {
                Move-Whole $eula (Join-Path $Dir 'eula.txt')
                Write-Host '  kept: eula.txt (eula=true)'
            }
            Remove-Item -LiteralPath $Old -Recurse -Force -ErrorAction Stop
        }
    } catch {
        throw "Local server swap failed: $($_.Exception.Message). The previous folder is '$Old' (world and settings), the new one '$Stage' or '$Dir'."
    }
    Write-Host "Local server: $Dir (start.bat)"
}

$DoLocalServer = $LocalServer.IsPresent -or $LocalServerDir -ne ''
if ($DoLocalServer -and $SkipServerPack) { throw '-LocalServer and -LocalServerDir need the server pack: leave out -SkipServerPack.' }

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
    $bcc = [IO.File]::ReadAllText($BccConfig)
    if ($BccVersionLine.Matches($bcc).Count -ne 1) { throw "Expected one 'modpackVersion = ...' line in $BccConfig" }
    $bcc = $BccVersionLine.Replace($bcc, "`${1}$Version`${3}")
    [IO.File]::WriteAllText($BccConfig, $bcc, (New-Object Text.UTF8Encoding($false)))
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
# The server list tooltip must not show the wrong pack version either (or the template's CHANGE_ME).
$bm = $BccVersionLine.Match([IO.File]::ReadAllText($BccConfig))
if (-not $bm.Success -or $bm.Groups[2].Value -ne $PackVersion) {
    throw "config\bcc-common.toml says '$($bm.Groups[2].Value)', pack.toml says '$PackVersion'. Run with -Version $PackVersion."
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
    $NeoForgeVersion = $m.Groups[1].Value
    $NeoInstaller = Join-Path $Repo "tools\neoforge-$NeoForgeVersion-installer.jar"
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
    $Utf8 = New-Object Text.UTF8Encoding($false)
    # The server pack's own start scripts (server/pack/): no pack sync, Java 21 from FA_JAVA_HOME, JAVA_HOME or PATH,
    # the NeoForge version of pack.toml. CRLF for cmd.exe, LF for sh.
    foreach ($f in @(@('start.bat', "`r`n"), @('start.sh', "`n"))) {
        $text = [IO.File]::ReadAllText((Join-Path $Server "pack\$($f[0])")) -replace "`r`n", "`n"
        $text = $text.Replace('{{NEOFORGE}}', $NeoForgeVersion).Replace("`n", $f[1])
        [IO.File]::WriteAllText((Join-Path $Work $f[0]), $text, $Utf8)
    }
    # Shipped heap 8 GB (up to about four players, fits an 8 GB host); README-SERVERPACK.md has the 10 GB row.
    $jvm = [IO.File]::ReadAllText((Join-Path $Server 'user_jvm_args.txt')) -replace '-Xms\d+G -Xmx\d+G', '-Xms8G -Xmx8G'
    if ($jvm -notmatch '-Xms8G -Xmx8G') { throw 'No -Xms/-Xmx line in server/user_jvm_args.txt' }
    [IO.File]::WriteAllText((Join-Path $Work 'user_jvm_args.txt'), $jvm, $Utf8)
    Copy-Item -LiteralPath (Join-Path $Server 'README-SERVERPACK.md') -Destination $Work
    Copy-Item -LiteralPath (Join-Path $Server 'server.properties.template') -Destination (Join-Path $Work 'server.properties')
    # eula=false: the host accepts the EULA by editing it (README-SERVERPACK.md, Start).
    Copy-Item -LiteralPath (Join-Path $Server 'eula.txt') -Destination $Work
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
if ($DoLocalServer) {
    if ($LocalServerDir -eq '') { $LocalServerDir = Join-Path (Split-Path -Parent $Repo) "FirmamentAges-Server-$PackVersion" }
    Install-LocalServer -Zip $ServerZip -Dir $LocalServerDir -JdkHome (Join-Path $Repo 'tools\jdk-21') -Repo $Repo
}
Write-Host ''
Write-Host 'Next (by hand): git add pack.toml index.toml; git commit; merge dev into main; tag; push.'
