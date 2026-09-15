# Kill ONLY the TN-C dev-server JVMs, never the player's game.
#
#   Why this file exists (2026-09-15 incident):
#     A blanket "kill every java.exe" cleanup step force-killed the user's
#     running Minecraft mid-load. It looked exactly like a mod crash:
#     exit code -1, no stack trace, no crash-report, no hs_err_pid, and
#     latest.log stopping in the middle of mod loading. Precious time was
#     then spent hunting a bug in the mod that did not exist.
#
#     => NEVER kill java processes by image name. Always match the command
#        line against this checkout (D:\ModTest) first, and refuse to touch
#        anything that looks like a game process.
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File kill-dev-java.ps1 [-WhatIf]
#
# Exit code 0 = ok, 1 = something that looks like a game was left alone (informational).

param(
    [switch]$WhatIf
)

$ErrorActionPreference = 'Stop'

# Classification is by the DECISIVE launch argument, not by loose substrings.
#
#   Learning the hard way, twice:
#     1) a blanket "kill all java" force-killed the user's game mid-load (exit -1,
#        no crash report - it looked exactly like a mod crash);
#     2) the first fix matched on 'BootstrapLauncher' / 'PCL' as game markers,
#        but a Forge DEV server also launches through
#        cpw.mods.bootstraplauncher.BootstrapLauncher, so a leftover dev JVM got
#        misclassified as a game and was left running -> orphan JVM -> the world
#        save stays locked -> "另一个程序已锁定文件的一部分" on the next run.
#
#   So: everything started from THIS checkout is ours; everything else is left
#   strictly alone. Never invert that default.
#   The repo root is derived from this script's location, so a collaborator can
#   clone anywhere (see tools\_common.ps1).
. (Join-Path $PSScriptRoot '_common.ps1')

$killed = 0
$skipped = 0
$kinds = @{}

$procs = @(Get-TncJavaProcesses)
if ($procs.Count -eq 0) {
    Write-Host "no java processes running."
    exit 0
}

foreach ($p in $procs) {
    $cl   = [string]$p.CommandLine
    $kind = Get-TncProcessKind $cl
    $mem  = [math]::Round($p.WorkingSetSize / 1MB)
    $kinds[$kind] = 1 + ($kinds[$kind] | ForEach-Object { $_ })

    if ($kind -ne 'dev') {
        Write-Host ("  [SKIP] pid={0,-6} {1,5}MB  ({2})" -f $p.ProcessId, $mem, $kind)
        $skipped++
        continue
    }

    if ($WhatIf) {
        Write-Host ("  [would kill] pid={0,-6} {1,5}MB  (dev)" -f $p.ProcessId, $mem)
    } else {
        Write-Host ("  [kill]       pid={0,-6} {1,5}MB  (dev)" -f $p.ProcessId, $mem)
        Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
    }
    $killed++
}

Write-Host ""
Write-Host "killed $killed dev JVM(s), left $skipped alone (game/unknown are never touched)."
if ($skipped -gt 0) { exit 1 }
exit 0
