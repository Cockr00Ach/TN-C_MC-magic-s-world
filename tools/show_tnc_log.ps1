# show_tnc_log.ps1 -- print the TN-C relevant lines of the running instance's latest.log
#
# WHY: the log is huge (10k+ lines of other mods) and Chinese shows as mojibake in most
# editors, so "just open latest.log" is bad advice. This pulls out only our own lines.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File tools\show_tnc_log.ps1
#   powershell -ExecutionPolicy Bypass -File tools\show_tnc_log.ps1 -Tail 60 -All
#
# -All   : every TN-C line (default: only the interesting ones)
# -Tail N: how many lines to print (default 40)
#
# NOTE: ASCII only (PowerShell 5.1 reads BOM-less .ps1 as ANSI; CJK would break it).

param(
    [int]$Tail = 40,
    [switch]$All
)

$ErrorActionPreference = 'Stop'

$instance = 'E:\download\正式版 2.12.6.1\.minecraft\versions\元素觉醒1.4.3-魔改版-20260915'
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
