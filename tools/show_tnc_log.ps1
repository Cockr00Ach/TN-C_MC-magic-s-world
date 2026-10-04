# show_tnc_log.ps1 -- print the TN-C relevant lines of the running instance's latest.log
#
# WHY: the log is huge (10k+ lines of other mods) and Chinese shows as mojibake in most
# editors, so "just open latest.log" is bad advice. This pulls out only our own lines.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File tools\show_tnc_log.ps1
#   powershell -ExecutionPolicy Bypass -File tools\show_tnc_log.ps1 -Tail 60 -All
#   powershell -ExecutionPolicy Bypass -File tools\show_tnc_log.ps1 -Instance "<...>\versions\<pack>"
#
# -All      : every TN-C line (default: only the interesting ones)
# -Tail N   : how many lines to print (default 40)
# -Instance : explicit instance folder; skips discovery entirely
#
# The instance is discovered, not hardcoded (see Resolve-TncInstance below), so a
# fresh clone on another machine works without editing tools\tnc_instance.json.
#
# NOTE: ASCII only (PowerShell 5.1 reads BOM-less .ps1 as ANSI; CJK would break it).

param(
    [int]$Tail = 40,
    [switch]$All,
    [string]$Instance = ''
)

$ErrorActionPreference = 'Stop'

# Resolve the live instance folder. Search order:
#   1. -Instance <path>          (explicit wins)
#   2. tools\tnc_instance.json   (a recorded machine-specific path - used only
#                                 when that path actually exists HERE)
#   3. auto-discovery            (via _common.ps1: $env:TNC_LIVE_ROOT first, then
#                                 the usual launcher folders)
# Step 2 keeps the old behaviour on the machine the file was recorded on; step 3
# is what makes a fresh clone work anywhere else.
# The recorded path contains CJK, so it stays in a UTF-8 side file: a .ps1 string
# literal would mojibake under PowerShell 5.1.
function Resolve-TncInstance {
    param([string]$Explicit)

    if (-not [string]::IsNullOrWhiteSpace($Explicit)) {
        if (Test-Path -LiteralPath $Explicit) { return $Explicit }
        Write-Host "ERROR: -Instance does not exist: $Explicit"
        exit 1
    }

    $recipe = Join-Path $PSScriptRoot 'tnc_instance.json'
    if (Test-Path -LiteralPath $recipe) {
        $recorded = (([IO.File]::ReadAllText($recipe, [Text.Encoding]::UTF8) | ConvertFrom-Json).instance)
        if (-not [string]::IsNullOrWhiteSpace($recorded) -and (Test-Path -LiteralPath $recorded)) {
            return $recorded
        }
    }

    . (Join-Path $PSScriptRoot '_common.ps1')
    $work = Find-TncWorkPack
    if ($work) {
        $live = Find-TncLivePack -WorkPack $work
        if ($live) { return $live }
    }
    return $null
}

$instance = Resolve-TncInstance -Explicit $Instance
if (-not $instance) {
    Write-Host 'ERROR: could not locate the live modpack instance.'
    Write-Host '       Pass it:  -Instance "<...>\.minecraft\versions\<pack>"'
    Write-Host '       or set the environment variable TNC_LIVE_ROOT to that versions folder.'
    exit 1
}
$log = Join-Path $instance 'logs\latest.log'

if (-not (Test-Path -LiteralPath $log)) {
    Write-Host "log not found: $log"
    exit 1
}

$info = Get-Item -LiteralPath $log
Write-Host ("log  : " + $log)
Write-Host ("time : " + $info.LastWriteTime + "   size: " + [int]($info.Length / 1024) + " KB")
Write-Host ""

# The interesting markers, most useful first:
#   SPELL_CAST        - did the engine actually release our spell (and did WE recognise it)
#   strike render     - did our bolt / ball / god entity reach the client renderer
#   mana gate         - was the cast blocked (unlearned / no mana / no wand)
#   auto-learned      - did the auto-learn add a chain spell
#   no magic stone    - the caster has no magic stone data
$pattern = if ($All) {
    'TN-C'
} else {
    'TN-C: SPELL_CAST|TN-C: strike render|mana gate|auto-learned|no magic stone|TN-C\]'
}

$hits = Select-String -LiteralPath $log -Pattern $pattern -Encoding UTF8
if (-not $hits) {
    Write-Host "no TN-C lines matched (pattern: $pattern). Re-run with -All to see everything."
    exit 0
}

Write-Host ("matched " + $hits.Count + " line(s), showing the last " + $Tail + ":")
Write-Host ""
$hits | Select-Object -Last $Tail | ForEach-Object {
    # drop the timestamp/mojibake prefix, keep from 'TN-C' (or the chat text) onwards
    $text = $_.Line
    $idx = $text.IndexOf('TN-C')
    if ($idx -lt 0) { $idx = $text.IndexOf('[CHAT]') }
    if ($idx -gt 0) { $text = $text.Substring($idx) }
    $text
}
