# Install this feature's jar AND both external KubeJS portal templates as one backed-up update.
param([Parameter(Mandatory=$true)][string]$LivePack)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot '_common.ps1')
$pack=[IO.Path]::GetFullPath($LivePack)
if (-not (Test-Path -LiteralPath (Join-Path $pack 'mods')) -or -not (Test-Path -LiteralPath (Join-Path $pack 'kubejs'))) {
    throw 'Expected an existing Minecraft instance with mods and kubejs; nothing changed.'
}
$running=@(Get-TncJavaProcesses)
if ($running.Count) { throw 'Close Minecraft and dev Java processes first. Nothing was copied; no processes were killed.' }
$workPack=Find-TncWorkPack
if (-not $workPack) { throw 'Cannot locate repository modpack' }
$portal='kubejs\data\tnc\structures\sky_island\portal'
$files=@(
    @{Source=(Join-Path $TncBuildLibs 'tnc-1.0.0.jar'); Relative='mods\tnc-1.0.0.jar'},
    @{Source=(Join-Path (Join-Path $workPack $portal) 'ground_portal.nbt'); Relative=(Join-Path $portal 'ground_portal.nbt')},
    @{Source=(Join-Path (Join-Path $workPack $portal) 'island_portal.nbt'); Relative=(Join-Path $portal 'island_portal.nbt')}
)
foreach ($f in $files) {
    if (-not (Test-Path -LiteralPath $f.Source -PathType Leaf)) { throw "Missing source $($f.Source)" }
    $f.Target=Join-Path $pack $f.Relative
    if (-not (Test-Path -LiteralPath $f.Target -PathType Leaf)) { throw "Expected existing target $($f.Target)" }
    $f.Hash=(Get-FileHash -LiteralPath $f.Source).Hash
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::OpenRead($files[0].Source)
try {
    foreach ($entry in @('com/tnc/tnc/world/stonecrest/LargeLandmarkJobs.class','data/tnc/buildings/end_pvp_island.json',
                        'data/tnc/buildings/heroskand_complex.json','data/tnc/buildings/gothic_cathedral.json','data/tnc/buildings/elden_coastal_castle.json',
                        'com/tnc/tnc/client/PortalRitualRenderer.class','com/tnc/tnc/network/PortalRitualPacket.class',
                        'data/tnc/structures/sky_island/portal/ritual_gate.nbt')) {
        if (-not $zip.GetEntry($entry)) { throw "Build is missing $entry; rebuild before installing." }
    }
} finally { $zip.Dispose() }
$backup=Join-Path $TncRepoRoot ('work\backups\landmark-update-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $backup | Out-Null
foreach ($f in $files) {
    $f.Backup=Join-Path $backup (Split-Path $f.Target -Leaf)
    Copy-Item -LiteralPath $f.Target -Destination $f.Backup
}
if (@(Get-TncJavaProcesses).Count) { throw "Java started during preparation; not installed. Backup: $backup" }
try {
    foreach ($f in $files) {
        Copy-Item -LiteralPath $f.Source -Destination $f.Target -Force
        if ((Get-FileHash -LiteralPath $f.Target).Hash -ne $f.Hash) { throw "Hash mismatch: $($f.Target)" }
    }
} catch {
    foreach ($f in $files) { Copy-Item -LiteralPath $f.Backup -Destination $f.Target -Force }
    throw "Installation failed; original three files restored. $($_.Exception.Message)"
}
Write-Host "Installed and SHA256 verified. Backup: $backup"
Write-Host 'Restart PCL. Use a NEW TEST WORLD for complete structures. Old portals: /tnc skyportal refresh'
