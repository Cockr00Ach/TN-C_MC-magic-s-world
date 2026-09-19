# TN-C shared helpers for the scripts in tools\.
#
#   Dot-source it at the top of a tool script:
#       . (Join-Path $PSScriptRoot '_common.ps1')
#
#   Why this file exists:
#     Every tool used to hardcode 'D:\ModTest\...' and 'E:\download\...', which
#     meant a collaborator HAD to clone to exactly D:\ModTest or nothing worked.
#     Now the repo root is derived from the script's own location, and the live
#     game instance is discovered by searching the usual launcher folders.
#
#   Nothing here is TN-C specific beyond the folder names; it is all path and
#   process plumbing.

# Captured at dot-source time. ($PSScriptRoot inside a *function* is not
# reliable for a dot-sourced file, so grab it now.)
$TncToolsDir = $PSScriptRoot
$TncRepoRoot = Split-Path $PSScriptRoot -Parent
$TncModpackDir = Join-Path $TncRepoRoot 'modpack'
$TncBuildLibs = Join-Path $TncRepoRoot 'build\libs'
$TncLibsDir = Join-Path $TncRepoRoot 'libs'
$TncRunMods = Join-Path $TncRepoRoot 'run\mods'

function Get-TncRepoRoot { return $TncRepoRoot }

function Get-TncVerifyScript { return (Join-Path $TncToolsDir 'verify_mod_jar.ps1') }

<#
  The modpack workspace: exactly one folder under <repo>\modpack (ignoring archive\).
  Returns $null and prints why when that is not the case.
#>
function Find-TncWorkPack {
    if (-not (Test-Path $TncModpackDir)) {
        Write-Host "ERROR: no modpack folder at $TncModpackDir"
        return $null
    }
    $dirs = @(Get-ChildItem $TncModpackDir -Directory | Where-Object { $_.Name -ne 'archive' })
    if ($dirs.Count -ne 1) {
        Write-Host "ERROR: expected exactly 1 modpack folder under $TncModpackDir (found $($dirs.Count))"
        return $null
    }
    return $dirs[0].FullName
}

<#
  The live game instance for a given workspace folder.
  Searches, in order: an explicit list, then <root>\.minecraft\versions\<name>,
  then <root>\*\ .minecraft\versions\<name> (launchers like PCL nest a folder deep).
  The pack folder name contains non-ASCII characters, so it is never hardcoded.
#>
function Find-TncLivePack {
    param(
        [Parameter(Mandatory = $true)][string]$WorkPack,
        [string[]]$LauncherRoots = @()
    )
    $name = Split-Path $WorkPack -Leaf

    # Allow callers to point directly at a launcher's versions directory.
    # This is needed for portable PCL installations that do not live under
    # one of the conventional .minecraft roots below.
    if ($env:TNC_LIVE_ROOT) {
        $explicit = Join-Path $env:TNC_LIVE_ROOT $name
        if (Test-Path $explicit) { return $explicit }
    }

    if ($LauncherRoots.Count -eq 0) {
        $LauncherRoots = @(
            'E:\download', 'D:\download', 'C:\download',
            "$env:USERPROFILE\AppData\Roaming\.minecraft",
            "$env:APPDATA\.minecraft"
        )
    }
    foreach ($root in $LauncherRoots) {
        if (-not (Test-Path $root)) { continue }

        $direct = Join-Path $root ('.minecraft\versions\' + $name)
        if (Test-Path $direct) { return $direct }

        foreach ($sub in Get-ChildItem $root -Directory -ErrorAction SilentlyContinue) {
            $cand = Join-Path $sub.FullName ('.minecraft\versions\' + $name)
            if (Test-Path $cand) { return $cand }
        }
    }
    return $null
}

function Get-TncJavaProcesses {
    return @(Get-CimInstance Win32_Process -Filter "Name like '%java%'" -ErrorAction SilentlyContinue)
}

<#
  Locate a JDK tool (javap.exe / javac.exe / jar.exe).

  These used to be hardcoded to
  'C:\Users\FDCX\AppData\Roaming\.minecraft\runtime\java-runtime-beta\bin\...'
  which obviously breaks for anybody else. Search order:
    1. %JAVA_HOME%\bin
    2. the Minecraft runtime folders (the launcher ships its own JDK)
    3. PATH
  Returns $null when not found, so callers can skip the check instead of crashing.
#>
function Find-TncJdkTool {
    param([Parameter(Mandatory = $true)][string]$Name)

    $candidates = New-Object System.Collections.Generic.List[string]

    if ($env:JAVA_HOME) { $candidates.Add((Join-Path $env:JAVA_HOME "bin\$Name")) }

    $runtimeBases = @(
        (Join-Path $env:APPDATA '.minecraft\runtime'),
        (Join-Path $env:USERPROFILE 'AppData\Roaming\.minecraft\runtime'),
        (Join-Path $env:LOCALAPPDATA 'Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe\LocalCache\Local\runtime')
    )
    foreach ($base in $runtimeBases) {
        if (-not (Test-Path $base)) { continue }
        foreach ($rt in Get-ChildItem $base -Directory -ErrorAction SilentlyContinue) {
            $candidates.Add((Join-Path $rt.FullName "bin\$Name"))
        }
    }

    foreach ($c in $candidates) { if (Test-Path $c) { return $c } }

    $onPath = Get-Command $Name -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    return $null
}

<#
  Classify a java process by its DECISIVE launch argument.
  Never by loose substrings - see PROJECT-STATE.md 4.15.2b: a blanket
  "kill every java" once force-killed the user's running game, and a later
  "BootstrapLauncher means game" guess misclassified a Forge dev server
  (both use BootstrapLauncher), leaving an orphan JVM that locked the save.

  Returns 'dev' | 'game' | 'unknown'. Anything launched from this checkout is
  ours; everything else is left strictly alone.
#>
function Get-TncProcessKind {
    param([string]$CommandLine)
    if ([string]::IsNullOrWhiteSpace($CommandLine)) { return 'unknown' }
    if ($CommandLine -match [regex]::Escape($TncRepoRoot)) { return 'dev' }
    if ($CommandLine -match 'launcher\.brand=PCL') { return 'game' }
    if ($CommandLine -match [regex]::Escape('.minecraft\versions')) { return 'game' }
    if ($CommandLine -match '--launchTarget\s+forgeclient') { return 'game' }
    return 'unknown'
}

<#
  Read a text file even while another process holds it open (log4j keeps the
  game's latest.log locked, and reading it as UTF-8 turns every Chinese line
  into mojibake because the game runs with -Dsun.stdout.encoding=GBK).

  $Encoding defaults to GBK (cp936).
#>
function Read-TncSharedText {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [System.Text.Encoding]$Encoding = $null
    )
    if ($null -eq $Encoding) { $Encoding = [System.Text.Encoding]::GetEncoding(936) }
    if (-not (Test-Path $Path)) { return $null }
    $stream = [System.IO.File]::Open($Path,
                                     [System.IO.FileMode]::Open,
                                     [System.IO.FileAccess]::Read,
                                     [System.IO.FileShare]::ReadWrite)
    try {
        $reader = New-Object System.IO.StreamReader($stream, $Encoding)
        return $reader.ReadToEnd()
    } finally {
        if ($reader) { $reader.Dispose() }
        $stream.Dispose()
    }
}
