#requires -Version 7.0
param([Parameter(Mandatory=$true)][string]$Candidate,[Parameter(Mandatory=$true)][string]$FullBackup,[switch]$CheckOnly)
$ErrorActionPreference='Stop'
$candidateRoot=[IO.Path]::GetFullPath($Candidate);$backupRoot=[IO.Path]::GetFullPath($FullBackup)
$repairReport=Get-Content -LiteralPath (Join-Path $candidateRoot 'repair-report.json') -Raw|ConvertFrom-Json
$worldRoot=[IO.Path]::GetFullPath($repairReport.source)
if(-not(Test-Path -LiteralPath (Join-Path $backupRoot 'level.dat'))){throw 'Full world backup required.'}
$games=@(Get-CimInstance Win32_Process|Where-Object {$_.Name -match '^java(w)?\.exe$' -and $_.CommandLine -match 'net.minecraft.client.main.Main|--launchTarget.*client|net.minecraft.server.Main|--launchTarget.*server'})
if($games.Count){throw 'Close Minecraft before repair installation.'}
$repairFiles=@()
foreach($r in $repairReport.regions){
 if($r.name -notmatch '^r\.-?\d+\.-?\d+\.mca$'){throw 'Invalid region name.'}
 $relative='region/'+$r.name
 if((Get-FileHash -LiteralPath (Join-Path $worldRoot $relative)).Hash.ToLower() -ne $r.original_hash -or (Get-FileHash -LiteralPath (Join-Path $backupRoot $relative)).Hash.ToLower() -ne $r.original_hash){throw 'Source/backup changed after candidate inspection.'}
 if((Get-FileHash -LiteralPath (Join-Path $candidateRoot $relative)).Hash.ToLower() -ne $r.repaired_hash){throw 'Repair candidate hash changed.'}
 $repairFiles+=@{Relative=$relative;Hash=$r.repaired_hash}
}
foreach($name in @('tnc_sky_island_v5.dat','tnc_adventure_v1.dat','tnc_sky_landscape_v1.dat')){
 $relative='data/'+$name
 if((Get-FileHash -LiteralPath (Join-Path $worldRoot $relative)).Hash -ne (Get-FileHash -LiteralPath (Join-Path $backupRoot $relative)).Hash){throw 'Account or island checkpoint changed after backup.'}
}
$repairFiles+=@{Relative='data/tnc_sky_landscape_v1.dat';Hash=(Get-FileHash -LiteralPath (Join-Path $candidateRoot 'data/tnc_sky_landscape_v1.dat')).Hash.ToLower()}
if($CheckOnly){Write-Output 'Repair preflight passed: two-way hashes, complete backup, game closed.';return}
try{
 foreach($f in $repairFiles){
  $target=Join-Path $worldRoot $f.Relative;$source=Join-Path $candidateRoot $f.Relative
  $temporary=$target+'.tnc-repair-tmp';Copy-Item -LiteralPath $source -Destination $temporary
  [IO.File]::Move($temporary,$target,$true)
  if((Get-FileHash -LiteralPath $target).Hash.ToLower() -ne $f.Hash){throw 'Installed repair mismatch.'}
 }
}catch{foreach($f in $repairFiles){Copy-Item -LiteralPath (Join-Path $backupRoot $f.Relative) -Destination (Join-Path $worldRoot $f.Relative) -Force};throw}
[ordered]@{World=$worldRoot;Backup=$backupRoot;ChangedFiles=$repairFiles.Count;CheckedVoxels=$repairReport.checked_voxels;ChangedVoxels=$repairReport.changed_voxels;QuestbookUntouched=$true;AccountsUntouched=$true}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $candidateRoot 'installed-repair-verification.json') -Encoding utf8
Write-Output "Installed inspected repair: $($repairReport.changed_voxels) unfinished landscape voxels; original island/account files preserved."
