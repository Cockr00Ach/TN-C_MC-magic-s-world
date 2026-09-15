# Sync workspace copy <-> live modpack
#
#   sync.cmd init <packname>  create the workspace copy from the live pack
#   sync.cmd diff             list differing files (default)
#   sync.cmd push             push NEWER workspace files to the pack
#   sync.cmd push -Force      push ALL workspace files (overwrites the pack)
#   sync.cmd pull             grab pack files back into the workspace
#
# AUTO-DETECT:
#   The modpack folder name inside D:\ModTest\modpack\ is detected
#   automatically, so this script keeps working when the pack is replaced
#   by a new download with a different name.
#
# WHY push is timestamp-aware:
#   The game rewrites config/*.toml constantly while running. A blind push
#   would clobber those newer files with older workspace copies. So push only
#   overwrites when the workspace copy is newer (or the target is missing).
#   Use -Force only when you deliberately want the workspace to win.

param(
    [ValidateSet('init', 'diff', 'push', 'pull')]
    [string]$Action = 'diff',

    [string]$PackName = '',

    [switch]$Force,

    # Explicit path to the launcher's ".minecraft\versions" folder.
    # Omit it and the script auto-detects (see Find-LiveVersionsRoot below).
    [string]$LiveRoot = ''
)

$wsRoot   = $PSScriptRoot

# ---- locate the live game instance's versions folder ----
# Used to be hardcoded to "E:\download\正式版 2.12.6.1\.minecraft\versions",
# which only works on one machine. Now it is discovered:
#   1. -LiveRoot <path>                       (explicit wins)
#   2. $env:TNC_LIVE_ROOT                     (env override, handy for a second PC)
#   3. the usual launcher folders, looking for <root>\*\.minecraft\versions
#      or <root>\.minecraft\versions
function Find-LiveVersionsRoot {
    param([string]$Explicit)

    if (-not [string]::IsNullOrWhiteSpace($Explicit)) {
        if (Test-Path $Explicit) { return $Explicit }
        Write-Host "ERROR: -LiveRoot does not exist: $Explicit"; exit 1
    }
    if (-not [string]::IsNullOrWhiteSpace($env:TNC_LIVE_ROOT)) {
        if (Test-Path $env:TNC_LIVE_ROOT) { return $env:TNC_LIVE_ROOT }
        Write-Host "ERROR: TNC_LIVE_ROOT does not exist: $env:TNC_LIVE_ROOT"; exit 1
    }

    $roots = @(
        'E:\download', 'D:\download', 'C:\download',
        "$env:USERPROFILE\AppData\Roaming\.minecraft",
        "$env:APPDATA\.minecraft"
    )
    foreach ($root in $roots) {
        if (-not (Test-Path $root)) { continue }
        $direct = Join-Path $root '.minecraft\versions'
        if (Test-Path $direct) { return $direct }
        foreach ($sub in Get-ChildItem $root -Directory -ErrorAction SilentlyContinue) {
            $cand = Join-Path $sub.FullName '.minecraft\versions'
            if (Test-Path $cand) { return $cand }
        }
    }
    return $null
}

$liveRoot = Find-LiveVersionsRoot -Explicit $LiveRoot
if (-not $liveRoot) {
    Write-Host "ERROR: could not find a .minecraft\versions folder."
    Write-Host "       Pass it explicitly:  sync.cmd diff -LiveRoot `"X:\...\.minecraft\versions`""
    Write-Host "       or set the environment variable TNC_LIVE_ROOT."
    exit 1
}
Write-Host "live versions root: $liveRoot"

# Only these editable dirs are synced.
# mods / saves / logs / backups / xaero / resourcepacks are never touched.
$dirs = @(
    'kubejs',
    'config',
    'defaultconfigs',
    'local',
    'data',
    'tlm_custom_pack',
    'vaultpatcher',
    'hotai',
    'immersive_furniture'
)

function Get-Files($root, $dir) {
    $p = Join-Path $root $dir
    if (-not (Test-Path $p)) { return @() }
    Get-ChildItem -Recurse -File $p -ErrorAction SilentlyContinue
}

# ---------------------------------------------------------------- init
if ($Action -eq 'init') {
    if ([string]::IsNullOrWhiteSpace($PackName)) {
        Write-Host "usage: sync.cmd init <packname>"
        Write-Host ""
        Write-Host "Available packs in the live folder:"
        Get-ChildItem $liveRoot -Directory | ForEach-Object { Write-Host "  $($_.Name)" }
        exit 1
    }
    $live = Join-Path $liveRoot $PackName
    if (-not (Test-Path $live)) {
        Write-Host "ERROR: live pack not found: $live"
        Write-Host ""
        Write-Host "Available packs:"
        Get-ChildItem $liveRoot -Directory | ForEach-Object { Write-Host "  $($_.Name)" }
        exit 1
    }
    $ws = Join-Path $wsRoot $PackName
    if (Test-Path $ws) {
        Write-Host "ERROR: workspace already exists: $ws"
        Write-Host "       delete it first if you really want to re-create it."
        exit 1
    }
    New-Item -ItemType Directory -Force -Path $ws | Out-Null
    $n = 0
    foreach ($d in $dirs) {
        $src = Join-Path $live $d
        if (Test-Path $src) {
            Copy-Item -Recurse -Force $src (Join-Path $ws $d)
            $c = (Get-ChildItem -Recurse -File (Join-Path $ws $d) -ErrorAction SilentlyContinue | Measure-Object).Count
            "  {0,-24} {1,5} files" -f $d, $c
            $n++
        }
    }
    $size = (Get-ChildItem -Recurse -File $ws | Measure-Object Length -Sum).Sum / 1MB
    ""
    "created workspace: $ws"
    "  {0} dir(s), {1:N1} MB" -f $n, $size
    exit 0
}

# ---------------------------------------------------------------- auto-detect
$packDirs = @(Get-ChildItem -Path $wsRoot -Directory -ErrorAction SilentlyContinue |
              Where-Object { $_.Name -notin @('archive') })

if ($packDirs.Count -eq 0) {
    Write-Host "ERROR: no modpack folder found under $wsRoot"
    Write-Host "       run 'sync.cmd init <packname>' to create one."
    exit 1
}
if ($packDirs.Count -gt 1) {
    Write-Host "ERROR: multiple folders found under $wsRoot - cannot decide which one is the pack:"
    $packDirs | ForEach-Object { Write-Host "         $($_.Name)" }
    exit 1
}

$packName = $packDirs[0].Name
$ws       = $packDirs[0].FullName
$live     = Join-Path $liveRoot $packName

if (-not (Test-Path $live)) {
    Write-Host "ERROR: live pack not found:"
    Write-Host "       $live"
    Write-Host "       (folder name must match the PCL version folder exactly)"
    exit 1
}

Write-Host "pack: $packName"
Write-Host "  workspace: $ws"
Write-Host "  live     : $live"
Write-Host ""

switch ($Action) {

    'diff' {
        $n = 0
        foreach ($d in $dirs) {
            $wsDir   = Join-Path $ws   $d
            $liveDir = Join-Path $live $d
            $a = Get-Files $ws   $d
            $b = Get-Files $live $d

            $mapB = @{}
            foreach ($f in $b) { $mapB[$f.FullName.Substring($liveDir.Length)] = $f }

            foreach ($f in $a) {
                $rel   = $f.FullName.Substring($wsDir.Length)
                $other = $mapB[$rel]
                if (-not $other) {
                    "  [ws-only]   {0}/{1}" -f $d, $rel; $n++
                }
                elseif ($f.Length -ne $other.Length -or $f.LastWriteTime -ne $other.LastWriteTime) {
                    $who = if ($f.LastWriteTime -gt $other.LastWriteTime) { 'ws-newer' } else { 'pack-newer' }
                    "  [{0}] {1}/{2}" -f $who, $d, $rel; $n++
                }
                $mapB.Remove($rel)
            }
            foreach ($k in $mapB.Keys) { "  [pack-only] {0}/{1}" -f $d, $k; $n++ }
        }
        if ($n -eq 0) { "In sync." } else { ""; "$n difference(s)." }
    }

    'push' {
        $copied = 0; $skipped = 0
        foreach ($d in $dirs) {
            $srcDir = Join-Path $ws $d
            if (-not (Test-Path $srcDir)) { continue }
            $dstDir = Join-Path $live $d
            if (-not (Test-Path $dstDir)) { New-Item -ItemType Directory -Force -Path $dstDir | Out-Null }

            foreach ($f in (Get-Files $ws $d)) {
                $rel  = $f.FullName.Substring($srcDir.Length)
                $dest = Join-Path $dstDir $rel
                $destDir = Split-Path $dest -Parent
                if (-not (Test-Path $destDir)) { New-Item -ItemType Directory -Force -Path $destDir | Out-Null }

                if ($Force -or -not (Test-Path $dest)) {
                    Copy-Item -Force $f.FullName $dest; $copied++
                }
                elseif ($f.LastWriteTime -gt (Get-Item $dest).LastWriteTime) {
                    Copy-Item -Force $f.FullName $dest; $copied++
                }
                else {
                    $skipped++
                }
            }
        }
        "pushed $copied file(s), skipped $skipped (pack copy is newer)."
        if ($skipped -gt 0 -and -not $Force) {
            "Use 'push -Force' if you really want the workspace to overwrite everything."
        }
        "Restart the game (or /reload for KubeJS server scripts)."
    }

    'pull' {
        $n = 0
        foreach ($d in $dirs) {
            $src = Join-Path $live $d
            if (Test-Path $src) {
                $dst = Join-Path $ws $d
                if (-not (Test-Path $dst)) { New-Item -ItemType Directory -Force -Path $dst | Out-Null }
                Copy-Item -Recurse -Force (Join-Path $src '*') $dst
                $n++
            }
        }
        "pulled $n dir(s). Workspace now matches the live pack."
    }
}
