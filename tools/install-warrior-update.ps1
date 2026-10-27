param(
    [Parameter(Mandatory=$true)][string]$ManifestPath,
    [Parameter(Mandatory=$true)][string]$LiveRoot
)
$ErrorActionPreference='Stop'
$warriorRoot=[IO.Path]::GetFullPath($LiveRoot).TrimEnd('\')
$warriorManifest=Get-Content -LiteralPath $ManifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
if (@($warriorManifest.files).Count -ne 1 -or $warriorManifest.files[0].relative -ne 'mods/tnc-1.0.0.jar') {throw 'Expected one TN-C mod jar'}
$warriorFile=$warriorManifest.files[0]
$warriorTarget=[IO.Path]::GetFullPath((Join-Path $warriorRoot $warriorFile.relative))
if ($warriorTarget -ne (Join-Path $warriorRoot 'mods\tnc-1.0.0.jar')) {throw 'Unexpected installation path'}
function Assert-WarriorGameStopped {
    $warriorGames=@(Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" |
        Where-Object {$_.CommandLine -match 'forgeclient|net\.minecraft\.client|\.minecraft\\versions'})
    if ($warriorGames.Count) {throw 'Minecraft is running; close the game before installation'}
}
Assert-WarriorGameStopped
if ((Get-FileHash -LiteralPath $warriorFile.source -Algorithm SHA256).Hash -ne $warriorFile.hash) {throw 'Prepared jar changed'}
if ((Get-FileHash -LiteralPath $warriorTarget -Algorithm SHA256).Hash -ne $warriorFile.expectedLiveHash) {throw 'Installed version changed; prepare again'}
$warriorBackup=Join-Path $warriorRoot ('backups\tnc-warrior-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
if (Test-Path -LiteralPath $warriorBackup) {throw 'Backup directory already exists'}
New-Item -ItemType Directory -Path $warriorBackup | Out-Null
$warriorOld=Join-Path $warriorBackup 'tnc-1.0.0.jar'
Copy-Item -LiteralPath $warriorTarget -Destination $warriorOld
if ((Get-FileHash -LiteralPath $warriorOld -Algorithm SHA256).Hash -ne $warriorFile.expectedLiveHash) {throw 'Backup verification failed'}
Copy-Item -LiteralPath $ManifestPath -Destination (Join-Path $warriorBackup 'manifest.json')
Assert-WarriorGameStopped
Copy-Item -LiteralPath $warriorFile.source -Destination $warriorTarget -Force
if ((Get-FileHash -LiteralPath $warriorTarget -Algorithm SHA256).Hash -ne $warriorFile.hash) {throw 'Installed hash mismatch'}
[pscustomobject]@{Installed=$warriorTarget;Hash=$warriorFile.hash;Backup=$warriorBackup;SavesModified=$false} | ConvertTo-Json
