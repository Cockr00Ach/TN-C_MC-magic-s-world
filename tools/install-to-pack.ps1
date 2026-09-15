# Install the built TN-C jar into the live modpack, safely.
#
#   Exists because of a real incident: a jar was copied into mods\ while the game
#   was mid-load. Overwriting a mod jar underneath a running game is exactly the
#   kind of thing that produces a confusing, unreproducible failure.
#
#   So this script REFUSES to copy while a game (or any dev JVM) is running,
#   unless you explicitly ask it to wait.
#
# Usage:
#   ... -File install-to-pack.ps1            # refuse if the game is running
#   ... -File install-to-pack.ps1 -Wait      # wait for the game to close, then install
#   ... -File install-to-pack.ps1 -Wait -TimeoutMinutes 120
#
# Exit code 0 = installed and verified.

param(
    [switch]$Wait,
    [int]$TimeoutMinutes = 60,
    [switch]$SkipVerify
)

$ErrorActionPreference = 'Stop'

# repo root / modpack / live-instance discovery + the java-process classifier
. (Join-Path $PSScriptRoot '_common.ps1')

# ---- locate things ----
$WorkPack = Find-TncWorkPack
if (-not $WorkPack) { exit 1 }

$livePack = Find-TncLivePack -WorkPack $WorkPack
if (-not $livePack) { Write-Host "ERROR: live game instance not found for '$(Split-Path $WorkPack -Leaf)'"; exit 1 }

$jars = @(Get-ChildItem $TncBuildLibs -Filter 'tnc-*.jar' -ErrorAction SilentlyContinue |
          Where-Object { $_.Name -notmatch 'sources|javadoc' } | Sort-Object LastWriteTime -Descending)
if ($jars.Count -eq 0) { Write-Host 'ERROR: no build artifact - run .\gradlew.bat build first'; exit 1 }
$built = $jars[0]

Write-Host "build  : $($built.FullName) ($('{0:N0}' -f $built.Length) bytes, $($built.LastWriteTime))"
Write-Host "target : $livePack\mods\$($built.Name)"
Write-Host ''

# ---- bail out (or wait) while anything is running ----
$deadline = (Get-Date).AddMinutes($TimeoutMinutes)
while ($true) {
    $running = @(Get-TncJavaProcesses)
    if ($running.Count -eq 0) { break }

    foreach ($p in $running) {
        $kind = Get-TncProcessKind ([string]$p.CommandLine)
        Write-Host ("  running: pid={0,-6} {1,5}MB  {2}" -f $p.ProcessId, [math]::Round($p.WorkingSetSize / 1MB), $kind)
    }

    if (-not $Wait) {
        Write-Host ''
        Write-Host 'REFUSING to install while java processes are running.'
        Write-Host 'Close the game (or use -Wait to wait for it), then run this again.'
        exit 1
    }
    if ((Get-Date) -gt $deadline) {
        Write-Host ''
        Write-Host "TIMEOUT: still running after $TimeoutMinutes minute(s); nothing was copied."
        exit 1
    }
    Write-Host '  waiting for it to close...'
    Start-Sleep -Seconds 10
}

# ---- install ----
Copy-Item $built.FullName (Join-Path $livePack ("mods\" + $built.Name)) -Force
$installed = Join-Path $livePack ("mods\" + $built.Name)
$same = (Get-FileHash $built.FullName).Hash -eq (Get-FileHash $installed).Hash
Write-Host "installed: $installed"
Write-Host "identical: $same"
if (-not $same) { Write-Host 'ERROR: copied file does not match the build output'; exit 1 }

if (-not $SkipVerify) {
    Write-Host ''
    Write-Host '--- verifying the jar ---'
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Get-TncVerifyScript)
    if ($LASTEXITCODE -ne 0) { Write-Host 'ERROR: jar verification failed'; exit 1 }
}

Write-Host ''
Write-Host 'Done. Start the game, ENTER A WORLD, then run:'
Write-Host ('  powershell -NoProfile -ExecutionPolicy Bypass -File "{0}"' -f (Join-Path $PSScriptRoot 'check-pack-verdict.ps1'))
exit 0
