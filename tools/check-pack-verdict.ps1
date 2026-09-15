# Read the modpack's log and report the TN-C verification verdict.
#
#   Read-only. Safe to run while the game is running.
#
#   Why this exists:
#     The whole point of the mana gate is that a failure is SILENT (the pack
#     starts fine, the gate just does nothing). Reading that out of a 700 KB
#     log by hand is slow and easy to get wrong, and the log is GBK-encoded so
#     a naive read turns every Chinese line into mojibake.
#
#     This script extracts exactly the lines that decide the question and prints
#     a verdict.
#
#   What it looks for:
#     1. did TN-C load at all
#     2. did the mana hook register on SPELL_CAST       (objective: mana is spent)
#     3. did our mixin actually get applied             (objective: hard gate)
#     4. self-test score + any failing check
#     5. mana gate probe score + each step
#     6. if the game died: was it KILLED or did it CRASH
#        (a killed process looks exactly like a crash: exit -1, no stack trace,
#         no crash-report, no hs_err. Never diagnose without checking this.)
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File check-pack-verdict.ps1
#   powershell -NoProfile -ExecutionPolicy Bypass -File check-pack-verdict.ps1 -LogPath <path>
#
# Exit code: 0 = verified pass, 1 = problem / not verified yet.

param(
    [string]$WorkPack = '',
    [string]$LogPath  = ''
)

$ErrorActionPreference = 'Stop'

# repo root / modpack / live-instance discovery + shared/GBK log reading
. (Join-Path $PSScriptRoot '_common.ps1')

# ---- locate the workspace, then the live game instance ----
if ([string]::IsNullOrWhiteSpace($WorkPack)) {
    $WorkPack = Find-TncWorkPack
    if (-not $WorkPack) { exit 1 }
}

$livePack = Find-TncLivePack -WorkPack $WorkPack
if (-not $livePack) {
    Write-Host "ERROR: live game instance not found (searched the usual launcher folders for '$(Split-Path $WorkPack -Leaf)')"
    exit 1
}

if ([string]::IsNullOrWhiteSpace($LogPath)) { $LogPath = Join-Path $livePack 'logs\latest.log' }
if (-not (Test-Path $LogPath)) { Write-Host "ERROR: log not found: $LogPath"; exit 1 }

# ---- read a log file as GBK (cp936) with a shared-read open ----
# Two traps this has to survive:
#   * the game is launched with -Dsun.stdout.encoding=GBK, so reading as UTF-8
#     turns every Chinese line into mojibake;
#   * the game holds the file open, and log4j does not share it for plain reads,
#     so File.ReadAllLines / Get-Content can fail with "being used by another
#     process". Open with FileShare.ReadWrite so this works WHILE the game runs
#     (which is when we actually want to look).
$gbk = [System.Text.Encoding]::GetEncoding(936)

function Read-SharedLines([string]$path) {
    if (-not (Test-Path $path)) { return @() }
    $stream = [System.IO.File]::Open($path,
                                     [System.IO.FileMode]::Open,
                                     [System.IO.FileAccess]::Read,
                                     [System.IO.FileShare]::ReadWrite)
    try {
        $reader = New-Object System.IO.StreamReader($stream, $gbk)
        $text = $reader.ReadToEnd()
        $reader.Dispose()
    } finally {
        $stream.Dispose()
    }
    return @($text -split "`r?`n")
}

$lines = Read-SharedLines $LogPath

Write-Host "log : $LogPath"
Write-Host ("size: {0:N0} bytes, {1:N0} lines, modified {2}" -f (Get-Item $LogPath).Length, $lines.Count, (Get-Item $LogPath).LastWriteTime)
Write-Host ''

function Show-Matches([string]$label, [string]$pattern, [int]$max = 20) {
    $found = @($script:lines | Where-Object { $_ -match $pattern })
    Write-Host "--- $label ($($found.Count)) ---"
    if ($found.Count -eq 0) { Write-Host '    (none)'; return @() }
    foreach ($l in ($found | Select-Object -Last $max)) { Write-Host ('    ' + $l.Trim()) }
    return $found
}

$problems = 0
$verified = $false

# 1. did TN-C load
$loadLines = Show-Matches 'TN-C loaded' 'TN-C common setup|magic stone data layer'
if ($loadLines.Count -eq 0) {
    Write-Host '    => TN-C never reached common setup. It did not load.'
    $problems++
}

# 2. mana hook  (objective: casting spends mana)
$hookLines = Show-Matches 'mana hook on SPELL_CAST' 'mana hook registered|mana hook not registered|could not register the mana hook'
$hookOk = @($hookLines | Where-Object { $_ -match 'mana hook registered' }).Count -gt 0
Write-Host ''

# 3. mixin applied  (objective: hard gate)
#
#    Two independent sources, because they live in different files:
#      * Mixin's own "Mixing <MixinClass> ... into <Target>" line is DEBUG level,
#        so it only lands in debug.log - grepping latest.log misses it.
#        (Missing this is what made this script print a false FAIL once.)
#      * the gate probe's FIRST step asserts exactly this ("gateChecks +1"),
#        and that IS the authoritative runtime evidence: the counter is
#        incremented by the code the mixin injects, so it cannot lie.
$mixinLines = Show-Matches 'our mixin applied to the engine (latest.log)' 'Mixing SpellHelperManaGateMixin|SpellHelperManaGateMixin from'
$debugLog = Join-Path (Split-Path $LogPath) 'debug.log'
$debugMixinLines = @(Read-SharedLines $debugLog | Where-Object { $_ -match 'Mixing SpellHelperManaGateMixin' })
Write-Host "--- our mixin applied to the engine (debug.log) ($($debugMixinLines.Count)) ---"
if ($debugMixinLines.Count -eq 0) { Write-Host '    (none)' }
foreach ($l in ($debugMixinLines | Select-Object -Last 5)) { Write-Host ('    ' + $l.Trim()) }

$probeInjected = @($lines | Where-Object { $_ -match 'mana gate probe' -and $_ -match 'Mixin .*注入' -and $_ -match '\[ok\]' }).Count -gt 0
$mixinOk = ($mixinLines.Count -gt 0) -or ($debugMixinLines.Count -gt 0) -or $probeInjected
Write-Host ''

# 4. self-test
$selfLines = Show-Matches 'self-test' 'self-test \[server start\]|\[FAIL\]'
$selfScore = $null
foreach ($l in $lines) {
    $mm = [regex]::Match($l, 'self-test \[server start\]: (\d+)/(\d+) passed')
    if ($mm.Success) { $selfScore = "$($mm.Groups[1].Value)/$($mm.Groups[2].Value)" }
}
Write-Host ''

# 5. the gate probe
$probeLines = Show-Matches 'mana gate probe (the verdict)' 'mana gate probe'
$probeScore = $null
foreach ($l in $lines) {
    $mm = [regex]::Match($l, 'mana gate probe \[server start\]: (\d+)/(\d+) passed')
    if ($mm.Success) { $probeScore = "$($mm.Groups[1].Value)/$($mm.Groups[2].Value)" }
}
$probeSkipped = @($probeLines | Where-Object { $_ -match 'skipped' }).Count -gt 0
Write-Host ''

# 6. killed vs crashed
$gameRunning = @(Get-CimInstance Win32_Process -Filter "Name like '%java%'" -ErrorAction SilentlyContinue |
                 Where-Object { $_.CommandLine -match 'launcher\.brand=PCL' }).Count -gt 0
$crashReport = @(Get-ChildItem (Join-Path $livePack 'crash-reports') -ErrorAction SilentlyContinue)
$hsErr = @(Get-ChildItem $livePack -Filter 'hs_err*' -ErrorAction SilentlyContinue)
Write-Host '--- death signature (killed vs crashed) ---'
Write-Host "    game running now : $gameRunning"
Write-Host "    crash-reports    : $($crashReport.Count)"
Write-Host "    hs_err_pid files : $($hsErr.Count)"
$stackLines = @($lines | Where-Object { $_ -match 'Failed to start the minecraft server|Exception in thread|net\.minecraft\.CrashReport' })
Write-Host "    fatal stack lines: $($stackLines.Count)"
if (-not $gameRunning -and $crashReport.Count -eq 0 -and $hsErr.Count -eq 0 -and $stackLines.Count -eq 0) {
    Write-Host '    => no crash evidence: if it stopped, it was KILLED (or closed), not crashed.'
}
Write-Host ''

# ---- verdict ----
Write-Host '================ VERDICT ================'
if ($probeScore) {
    $mm = [regex]::Match($probeScore, '^(\d+)/(\d+)$')
    $all = ($mm.Groups[1].Value -eq $mm.Groups[2].Value)
    if ($all -and $mixinOk) {
        Write-Host "PASS  mana gate probe $probeScore, mixin applied, hook registered=$hookOk"
        Write-Host '      => "no mana, no cast" is working in the real pack.'
        $verified = $true
    } else {
        Write-Host "FAIL  mana gate probe $probeScore (mixin applied=$mixinOk)"
        $problems++
    }
} elseif ($probeSkipped) {
    Write-Host 'PENDING  the probe skipped (engine not present?) - see the line above.'
} else {
    Write-Host 'PENDING  the probe has not run yet.'
    Write-Host '         It runs on world load, so ENTER A WORLD (singleplayer) first.'
}
Write-Host ''
Write-Host ("hook registered : {0}" -f $hookOk)
Write-Host ("mixin applied   : {0}" -f $mixinOk)
Write-Host ("self-test       : {0}" -f $(if ($selfScore) { $selfScore } else { 'n/a' }))
Write-Host ("gate probe      : {0}" -f $(if ($probeScore) { $probeScore } else { 'n/a' }))

if ($problems -gt 0 -and -not $verified) { exit 1 }
if (-not $verified) { exit 1 }
exit 0
