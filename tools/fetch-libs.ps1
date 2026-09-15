# Fetch the two remapped SpellEngine jars into libs/ so the mod can compile.
#
#   Why this exists:
#     build.gradle declares these as compileOnly:
#       libs/spell_engine-0.15.12+1.20.1_mapped_srg_1.20.1.jar
#       libs/spell_power-0.12.0+1.20.1_mapped_srg_1.20.1.jar
#     They are Sinytra Connector's SRG-remapped builds of a third-party mod, so
#     they are NOT in version control (see .gitignore). Every clone has to fetch
#     them once from the local modpack, which already contains them here:
#       <pack>\mods\.connector\*_mapped_srg_1.20.1.jar
#
#   Run this after cloning, before the first build.
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\fetch-libs.ps1
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\fetch-libs.ps1 -PackDir <path>
#
# Exit code 0 = ok, 1 = could not find the jars.

param(
    [string]$PackDir = ''
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path $PSScriptRoot -Parent
$libsDir  = Join-Path $repoRoot 'libs'

# shared discovery (repo root / modpack / live instance) - no hardcoded paths
. (Join-Path $PSScriptRoot '_common.ps1')

# ---- locate the modpack workspace (or the live instance) ----
function Find-ConnectorDir([string]$root) {
    $candidate = Join-Path $root 'mods\.connector'
    if (Test-Path $candidate) { return $candidate }
    return $null
}

$connector = $null

if (-not [string]::IsNullOrWhiteSpace($PackDir)) {
    $connector = Find-ConnectorDir $PackDir
    if (-not $connector) { $connector = $PackDir }   # allow pointing straight at .connector
    if (-not (Test-Path $connector)) { Write-Host "ERROR: no such folder: $connector"; exit 1 }
} else {
    $workspace = Find-TncWorkPack
    if ($workspace) {
        # 1) the modpack workspace inside the repo
        $connector = Find-ConnectorDir $workspace

        # 2) else the live game instance (found by searching the usual launchers)
        if (-not $connector) {
            $live = Find-TncLivePack -WorkPack $workspace
            if ($live) { $connector = Find-ConnectorDir $live }
        }
    }
}

if (-not $connector) {
    Write-Host 'ERROR: could not find a mods\.connector folder.'
    Write-Host '       Point at it explicitly:  -PackDir "<path to pack>\mods\.connector"'
    exit 1
}

Write-Host "connector dir: $connector"
New-Item -ItemType Directory -Force -Path $libsDir | Out-Null

$wanted = @('spell_engine-*_mapped_srg_*.jar', 'spell_power-*_mapped_srg_*.jar')
$copied = 0

foreach ($pattern in $wanted) {
    $src = Get-ChildItem $connector -Filter $pattern -ErrorAction SilentlyContinue |
           Sort-Object Length -Descending | Select-Object -First 1
    if (-not $src) {
        Write-Host "  [MISSING] $pattern  (is SpellEngine installed in that pack?)"
        continue
    }
    $dest = Join-Path $libsDir $src.Name
    Copy-Item $src.FullName $dest -Force
    Write-Host ("  [ok] {0}  ({1:N0} bytes)" -f $src.Name, $src.Length)
    $copied++
}

if ($copied -eq 0) { Write-Host 'ERROR: nothing copied.'; exit 1 }

Write-Host ''
Write-Host "libs\ now holds:"
Get-ChildItem $libsDir -Filter '*.jar' | ForEach-Object { Write-Host ("  {0}  ({1:N0} bytes)" -f $_.Name, $_.Length) }

# ---- one-time per-clone setup: enable the commit guard ----
# The guard lives in the repo (tools/git-hooks/) but git only uses it when
# core.hooksPath points there, and that setting is per-clone. Without it a stray
# `git add -A` can swallow a 1.5 GB file - which actually happened once.
$hooksPath = & git -C $repoRoot config core.hooksPath 2>$null
Write-Host ''
if ($hooksPath -ne 'tools/git-hooks') {
    Write-Host '--------------------------------------------------------------'
    Write-Host ' ONE MORE STEP - enable the commit guard (once per clone):'
    Write-Host ''
    Write-Host '   git config core.hooksPath tools/git-hooks'
    Write-Host ''
    Write-Host ' It rejects commits containing mod jars / archives / big files.'
    Write-Host '--------------------------------------------------------------'
} else {
    Write-Host 'commit guard: enabled (core.hooksPath = tools/git-hooks)'
}

Write-Host ''
Write-Host 'Now run:  .\gradlew.bat build'
exit 0
