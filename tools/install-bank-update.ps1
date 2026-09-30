#requires -Version 7.0
param([string]$LivePack='D:\垃圾桶\PCL 正式版 2.9.3\.minecraft\versions\元素觉醒1.4.3-魔改版-20260915',[switch]$CheckOnly)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot '_common.ps1')
$chapter=Join-Path $TncRepoRoot 'questbook\ftbquests\chapters\tnc_equipment.snbt'
$jar=Join-Path $TncBuildLibs 'tnc-1.0.0.jar'
if(-not(Test-Path -LiteralPath $jar)){throw 'Build bank jar first.'}
$content=Get-Content -LiteralPath $chapter -Raw -Encoding UTF8 | ConvertFrom-Json
if($content.quests.Count -ne 35 -or $content.id -ne '544E474100000001'){throw 'Unexpected equipment chapter.'}
$ids=@($content.id)+@($content.quests|ForEach-Object {$_.id;$_.tasks|ForEach-Object {$_.id}})
$taskPacks=@((Find-TncWorkPack),[IO.Path]::GetFullPath($LivePack))|Select-Object -Unique
foreach($pack in $taskPacks){
    if(-not(Test-Path -LiteralPath (Join-Path $pack 'mods') -PathType Container)){throw 'Expected existing modpack mods directory.'}
    $questRoot=Join-Path $pack 'config\ftbquests\quests'
    $target=Join-Path $questRoot 'chapters\tnc_equipment.snbt'
    if(Test-Path -LiteralPath $target){if((Get-FileHash -LiteralPath $target).Hash -ne (Get-FileHash -LiteralPath $chapter).Hash){throw 'Existing equipment page has author changes; preserve it.'}}
    foreach($file in Get-ChildItem -LiteralPath (Join-Path $questRoot 'chapters') -File){
        if($file.FullName -eq $target){continue}
        $text=[IO.File]::ReadAllText($file.FullName,[Text.Encoding]::UTF8)
        foreach($id in $ids){if($text.Contains($id)){throw "Duplicate equipment ID in $($file.Name)"}}
    }
}
if($CheckOnly){Write-Output 'Bank jar and isolated equipment chapter preflight passed; no files changed.';return}
$games=@(Get-CimInstance Win32_Process|Where-Object {$_.Name -match '^java(w)?\.exe$' -and $_.CommandLine -match 'net.minecraft.client.main.Main|--launchTarget.*client|net.minecraft.server.Main|--launchTarget.*server'})
if($games.Count){throw 'Minecraft/server is running. Close it before installing; no files changed.'}
foreach($pack in $taskPacks){
    # Exactly the bank jar and new equipment chapter; no unrelated story or spell files.
    $questRoot=Join-Path $pack 'config\ftbquests\quests'
    $target=Join-Path $questRoot 'chapters\tnc_equipment.snbt'
    $backup=Join-Path $TncRepoRoot ('work\backups\bank-book-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
    New-Item -ItemType Directory -Path $backup | Out-Null
    Copy-Item -LiteralPath $questRoot -Destination (Join-Path $backup 'quests') -Recurse
    $jarTarget=Join-Path $pack 'mods\tnc-1.0.0.jar'
    Copy-Item -LiteralPath $jarTarget -Destination (Join-Path $backup 'tnc-1.0.0.jar')
    $before=@(Get-ChildItem -LiteralPath $questRoot -Recurse -File|Where-Object {$_.FullName -ne $target}|ForEach-Object {[ordered]@{Path=$_.FullName;Hash=(Get-FileHash -LiteralPath $_.FullName).Hash}})
    [IO.File]::WriteAllText((Join-Path $backup 'manifest.json'),($before|ConvertTo-Json -Depth 5),[Text.UTF8Encoding]::new($false))
    New-Item -ItemType Directory -Path (Split-Path $target -Parent) -Force | Out-Null
    $hadChapter=Test-Path -LiteralPath $target
    try{
        Copy-Item -LiteralPath $jar -Destination $jarTarget -Force
        Copy-Item -LiteralPath $chapter -Destination $target
        if((Get-FileHash -LiteralPath $target).Hash -ne (Get-FileHash -LiteralPath $chapter).Hash){throw 'Equipment chapter copy mismatch.'}
        if((Get-FileHash -LiteralPath $jarTarget).Hash -ne (Get-FileHash -LiteralPath $jar).Hash){throw 'Bank jar copy mismatch.'}
        foreach($entry in $before){if((Get-FileHash -LiteralPath $entry.Path).Hash -ne $entry.Hash){throw "Existing authored page changed: $($entry.Path)"}}
    }catch{
        Copy-Item -LiteralPath (Join-Path $backup 'tnc-1.0.0.jar') -Destination $jarTarget -Force
        if($hadChapter){Copy-Item -LiteralPath (Join-Path $backup 'quests\chapters\tnc_equipment.snbt') -Destination $target -Force}
        elseif(Test-Path -LiteralPath $target){Move-Item -LiteralPath $target -Destination (Join-Path $backup 'failed-new-page.snbt')}
        throw
    }
    Write-Output "Added only equipment chapter; $($before.Count) existing book files unchanged. Backup: $backup"
}
