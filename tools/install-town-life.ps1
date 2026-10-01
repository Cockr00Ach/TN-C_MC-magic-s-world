#requires -Version 7.0
param([string]$LivePack='D:\垃圾桶\PCL 正式版 2.9.3\.minecraft\versions\元素觉醒1.4.3-魔改版-20260915',[switch]$CheckOnly)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot '_common.ps1')
$townJar=Join-Path $TncBuildLibs 'tnc-1.0.0.jar'
$townWork=Find-TncWorkPack
$townScript=Join-Path $townWork 'kubejs\server_scripts\tnc_town_market.js'
if(-not(Test-Path -LiteralPath $townJar) -or -not(Test-Path -LiteralPath $townScript)){throw 'Build TN-C and prepare market script first.'}
$townPacks=@($townWork,[IO.Path]::GetFullPath($LivePack))|Select-Object -Unique
foreach($pack in $townPacks){if(-not(Test-Path -LiteralPath (Join-Path $pack 'mods\tnc-1.0.0.jar')) -or -not(Test-Path -LiteralPath (Join-Path $pack 'config\ftbquests\quests'))){throw 'Expected existing TN-C modpack and authored questbook.'}}
if($CheckOnly){Write-Output 'Town installer preflight passed: only TN-C jar and market script, no quest definitions or world blocks.';return}
$townGames=@(Get-CimInstance Win32_Process|Where-Object {$_.Name -match '^java(w)?\.exe$' -and $_.CommandLine -match 'net.minecraft.client.main.Main|--launchTarget.*client|net.minecraft.server.Main|--launchTarget.*server'})
if($townGames.Count){throw 'Minecraft/server is running. Installer preserved all files; close game before installation.'}
$townReport=@()
foreach($pack in $townPacks){
    $townBackup=Join-Path $TncRepoRoot ('work\backups\town-life-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
    New-Item -ItemType Directory -Path $townBackup|Out-Null
    $townBook=Join-Path $pack 'config\ftbquests\quests'
    $townBefore=@(Get-ChildItem -LiteralPath $townBook -Recurse -File|ForEach-Object {[ordered]@{Path=$_.FullName;Hash=(Get-FileHash -LiteralPath $_.FullName).Hash}})
    Copy-Item -LiteralPath $townBook -Destination (Join-Path $townBackup 'quests') -Recurse
    $townTargets=@(@{Source=$townJar;Target=(Join-Path $pack 'mods\tnc-1.0.0.jar');Backup='tnc-1.0.0.jar'},@{Source=$townScript;Target=(Join-Path $pack 'kubejs\server_scripts\tnc_town_market.js');Backup='tnc_town_market.js'})
    foreach($item in $townTargets){$item.Existed=Test-Path -LiteralPath $item.Target;if($item.Existed){Copy-Item -LiteralPath $item.Target -Destination (Join-Path $townBackup $item.Backup)}}
    # Back up account/ownership data for the live authored world without opening it.
    $townAccount=Join-Path $pack 'saves\新的世界\data\tnc_adventure_v1.dat'
    if(Test-Path -LiteralPath $townAccount){Copy-Item -LiteralPath $townAccount -Destination (Join-Path $townBackup 'tnc_adventure_v1.dat')}
    try{
        foreach($item in $townTargets){if([IO.Path]::GetFullPath($item.Source) -ne [IO.Path]::GetFullPath($item.Target)){New-Item -ItemType Directory -Path (Split-Path $item.Target -Parent) -Force|Out-Null;Copy-Item -LiteralPath $item.Source -Destination $item.Target -Force};if((Get-FileHash -LiteralPath $item.Source).Hash -ne (Get-FileHash -LiteralPath $item.Target).Hash){throw 'Installed file hash mismatch.'}}
        foreach($entry in $townBefore){if((Get-FileHash -LiteralPath $entry.Path).Hash -ne $entry.Hash){throw 'Authored questbook changed.'}}
        $entry=[ordered]@{Pack=$pack;JarHash=(Get-FileHash -LiteralPath $townTargets[0].Target).Hash;ScriptHash=(Get-FileHash -LiteralPath $townTargets[1].Target).Hash;PreservedQuestFiles=$townBefore.Count;Backup=$townBackup}
        [IO.File]::WriteAllText((Join-Path $townBackup 'manifest.json'),(@{Installed=$entry;Quests=$townBefore}|ConvertTo-Json -Depth 7),[Text.UTF8Encoding]::new($false));$townReport+=$entry
    }catch{
        foreach($item in $townTargets){if($item.Existed){Copy-Item -LiteralPath (Join-Path $townBackup $item.Backup) -Destination $item.Target -Force}elseif(Test-Path -LiteralPath $item.Target){Move-Item -LiteralPath $item.Target -Destination (Join-Path $townBackup ('failed-'+$item.Backup))}}
        throw
    }
    Write-Output "Installed RouchNao services; $($townBefore.Count) authored book files unchanged. Backup: $townBackup"
}
[IO.File]::WriteAllText((Join-Path $TncRepoRoot 'work\town-life-installed-verification.json'),($townReport|ConvertTo-Json -Depth 5),[Text.UTF8Encoding]::new($false))
