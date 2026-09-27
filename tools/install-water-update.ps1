param([Parameter(Mandatory=$true)][string]$LivePack)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot '_common.ps1')
$pack=[IO.Path]::GetFullPath($LivePack).TrimEnd('\')
if(-not(Test-Path -LiteralPath (Join-Path $pack 'mods')) -or -not(Test-Path -LiteralPath (Join-Path $pack 'kubejs'))){throw 'Expected an existing pack.'}
if(@(Get-TncJavaProcesses).Count){throw 'Close Minecraft and development Java processes first; no files changed.'}
$workPack=Find-TncWorkPack
$files=@(@{Source=(Join-Path $TncBuildLibs 'tnc-1.0.0.jar');Relative='mods\tnc-1.0.0.jar'})
$names=@('water_ripple','water_wave','wave_slash','tsunami','world_ending_sea','water_bind','water_prison','water_burial','abyss','sea_god_crypt','raindrop','first_rain','rainfall','downpour','flood_of_heaven')
foreach($name in $names){$relative="kubejs\data\tnc\spells\$name.json";$files+=@{Source=(Join-Path $workPack $relative);Relative=$relative}}
foreach($f in $files){
    if(-not(Test-Path -LiteralPath $f.Source -PathType Leaf)){throw "Missing source $($f.Source)"}
    $f.Target=[IO.Path]::GetFullPath((Join-Path $pack $f.Relative))
    if(-not $f.Target.StartsWith($pack+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Target outside pack'}
    $f.Hash=(Get-FileHash -LiteralPath $f.Source).Hash
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::OpenRead($files[0].Source)
try{foreach($entry in @('com/tnc/tnc/magic/water/TNWaterSpellEntity.class','com/tnc/tnc/magic/water/TNWaterFieldEntity.class','com/tnc/tnc/magic/water/ChaosSilence.class','assets/tnc/models/item/chaos_seal.json','com/tnc/tnc/world/SkyLandscapeUpgrade.class')){if(-not $zip.GetEntry($entry)){throw "Incomplete build: $entry"}}}finally{$zip.Dispose()}
# A stale external copy would take precedence over the five jar-owned spells.
foreach($name in @('water_ball','water_cannon','dragon_roar','dragon_howl','dragon_ruin')){
    $candidate=Join-Path $pack "kubejs\data\tnc\spells\$name.json"
    if(Test-Path -LiteralPath $candidate){throw "External override requires review before installation: $candidate"}
}
$backup=Join-Path $TncRepoRoot ('work\backups\water-update-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $backup | Out-Null
foreach($f in $files){
    $f.Backup=Join-Path $backup $f.Relative
    New-Item -ItemType Directory -Path (Split-Path $f.Backup -Parent) -Force | Out-Null
    $f.Existed=Test-Path -LiteralPath $f.Target
    if($f.Existed){Copy-Item -LiteralPath $f.Target -Destination $f.Backup}
}
if(@(Get-TncJavaProcesses).Count){throw "Java started during preparation; nothing installed. Backup: $backup"}
try{
    foreach($f in $files){
        New-Item -ItemType Directory -Path (Split-Path $f.Target -Parent) -Force | Out-Null
        Copy-Item -LiteralPath $f.Source -Destination $f.Target -Force
        if((Get-FileHash -LiteralPath $f.Target).Hash -ne $f.Hash){throw "Hash mismatch: $($f.Relative)"}
    }
}catch{
    foreach($f in $files){
        if($f.Existed){Copy-Item -LiteralPath $f.Backup -Destination $f.Target -Force}
        elseif(Test-Path -LiteralPath $f.Target){Move-Item -LiteralPath $f.Target -Destination ($f.Backup+'.new-file')}
    }
    throw "Update failed; existing files restored. $($_.Exception.Message)"
}
Write-Output "Installed $($files.Count) files and verified SHA256. Backup: $backup"
Write-Output 'Restart Minecraft. No save was edited. Dragon Ruin permanently destroys terrain: test in a separate world.'
