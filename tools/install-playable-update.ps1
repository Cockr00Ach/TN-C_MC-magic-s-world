param([Parameter(Mandatory=$true)][string]$LivePack,[switch]$CheckOnly,[switch]$UpdateQuestbook)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot '_common.ps1')
$taskPack=[IO.Path]::GetFullPath($LivePack).TrimEnd('\')
if(-not(Test-Path -LiteralPath (Join-Path $taskPack 'mods') -PathType Container)){throw 'Expected an existing modpack.'}
$sourcePack=Find-TncWorkPack
$workspaceTarget=$taskPack.Equals([IO.Path]::GetFullPath($sourcePack).TrimEnd('\'),[StringComparison]::OrdinalIgnoreCase)
$jar=Join-Path $TncBuildLibs 'tnc-1.0.0.jar'
if(-not(Test-Path -LiteralPath $jar)){throw 'Build jar first.'}
$entries=[Collections.Generic.List[object]]::new()
function Add-InstallFile([string]$Source,[string]$Relative){
    $target=[IO.Path]::GetFullPath((Join-Path $taskPack $Relative))
    if(-not $target.StartsWith($taskPack+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Target escapes pack.'}
    if($Relative -match '(^|\\)saves(\\|$)'){throw 'Save editing is not allowed by this installer.'}
    if($target.Equals([IO.Path]::GetFullPath($Source),[StringComparison]::OrdinalIgnoreCase)){return}
    $entries.Add([ordered]@{Source=$Source;Relative=$Relative;Target=$target;SHA256=(Get-FileHash -LiteralPath $Source -Algorithm SHA256).Hash;Existed=(Test-Path -LiteralPath $target -PathType Leaf)})
}
Add-InstallFile $jar 'mods\tnc-1.0.0.jar'
$chapterDir=Join-Path $TncRepoRoot 'questbook\ftbquests\chapters'
$chapters=@(Get-ChildItem -LiteralPath $chapterDir -File | Where-Object {$_.Name -match '^tnc_(guide_|play_)'})
if($chapters.Count -ne 17){throw 'Expected ten guide and seven gameplay chapters.'}
$ids=[Collections.Generic.HashSet[string]]::new()
foreach($chapter in $chapters){
    $data=Get-Content -LiteralPath $chapter.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
    if(-not $ids.Add($data.id)){throw 'Duplicate chapter ID.'}
    foreach($quest in $data.quests){
        if(-not $ids.Add($quest.id)-or $quest.rewards.Count){throw 'Duplicate ID or duplicate account rewards.'}
        foreach($t in $quest.tasks){if(-not $ids.Add($t.id)){throw 'Duplicate task ID.'};if($chapter.Name -match '^tnc_play_' -and $t.type -notin @('advancement','item')){throw 'Gameplay objectives must be real advancements or held items.'}}
    }
    if($UpdateQuestbook){Add-InstallFile $chapter.FullName ('config\ftbquests\quests\chapters\'+$chapter.Name)}
}
if($UpdateQuestbook){foreach($name in @('water_focus_1','water_staff_2','water_staff_3','water_staff_4','water_staff_5','onboarding_header','gui_world_title','town_atlas')){Add-InstallFile (Join-Path $TncRepoRoot "src\main\resources\assets\tnc\textures\guide\$name.png") "kubejs\assets\tnc\textures\guide\$name.png"}}
Add-InstallFile (Join-Path $sourcePack 'kubejs\assets\tnc\textures\spell\divine_shot.png') 'kubejs\assets\tnc\textures\spell\divine_shot.png'
Add-InstallFile (Join-Path $sourcePack 'kubejs\assets\tnc\textures\spell\god_descent.png') 'kubejs\assets\tnc\textures\spell\god_descent.png'
# Whitelisted TN-C resources only. Existing legacy task definitions are deliberately retained.
foreach($relativeRoot in @('kubejs\data\tnc\whisperingquests','kubejs\data\tnc\dialogues')){
    $directory=Join-Path $sourcePack $relativeRoot
    if(Test-Path -LiteralPath $directory){foreach($file in Get-ChildItem -LiteralPath $directory -File -Recurse){$relative=[IO.Path]::GetRelativePath($sourcePack,$file.FullName);Add-InstallFile $file.FullName $relative}}
}
Add-InstallFile (Join-Path $sourcePack 'kubejs\server_scripts\world\tnc_quest_start.js') 'kubejs\server_scripts\world\tnc_quest_start.js'
if(@(Get-ChildItem -LiteralPath (Join-Path $sourcePack 'kubejs\data\tnc\whisperingquests\tasks\main') -File -Filter '*.json').Count -ne 18){throw 'Story source manifest changed; review before installing.'}
if(-not(Test-Path -LiteralPath (Join-Path $taskPack 'mods\whisperingquests-3.2.jar'))){throw 'Opening migration requires verified WhisperingQuests 3.2.'}
$groupTarget=Join-Path $taskPack 'config\ftbquests\quests\chapter_groups.snbt'
$groupExisted=Test-Path -LiteralPath $groupTarget -PathType Leaf
if($UpdateQuestbook -and -not $groupExisted -and -not $workspaceTarget){throw 'Native FTB chapter group file missing.'}
$groupText=if($groupExisted){[IO.File]::ReadAllText($groupTarget,[Text.Encoding]::UTF8)}else{'{"chapter_groups": []}'}
if($UpdateQuestbook -and $groupText -notmatch '544E434755494445'){
    $array=[regex]::Match($groupText,'["'']?chapter_groups["'']?\s*:\s*\[');if(-not $array.Success){throw 'Cannot safely merge group list.'}
    $insertAt=$array.Index+$array.Length
    $separator=if($groupText.Substring($insertAt).TrimStart().StartsWith(']')){''}else{','}
    $groupText=$groupText.Insert($insertAt,"`n"+[IO.File]::ReadAllText((Join-Path $TncRepoRoot 'questbook\ftbquests\group.snbt'))+$separator+"`n")
}
$removed=[Collections.Generic.List[object]]::new()
if($UpdateQuestbook){foreach($name in @('e.snbt','2032E61CAD845DDF.snbt')){
    $relative='config\ftbquests\quests\chapters\'+$name
    $target=[IO.Path]::GetFullPath((Join-Path $taskPack $relative))
    if(-not $target.StartsWith($taskPack+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Removal target escapes pack.'}
    if(Test-Path -LiteralPath $target){
        $text=[IO.File]::ReadAllText($target,[Text.Encoding]::UTF8)
        $expected=if($name -eq 'e.snbt'){'注意事项'}else{'鸣谢名单'}
        if($text -notmatch ('(?m)^\s*title:\s*"[^"\r\n]*'+$expected)){throw "Removal title mismatch: $name"}
        $removed.Add([ordered]@{Relative=$relative;Target=$target;SHA256=(Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash})
    }
}}
if($CheckOnly){Write-Output "Preflight passed: $($entries.Count) owned files, $($ids.Count) source FTB IDs; UpdateQuestbook=$UpdateQuestbook; $($removed.Count) requested author chapters to archive. No files changed.";return}
$gameProcesses=@(Get-CimInstance Win32_Process | Where-Object {$_.Name -match '^java(w)?\.exe$' -and $_.CommandLine -match 'forgeclient|net.minecraft.client.main.Main|--launchTarget.*client|net.minecraft.server.Main|--launchTarget.*server'})
if($gameProcesses.Count){throw 'Minecraft/server is running; no files changed.'}
$backup=Join-Path $TncRepoRoot ('work\backups\playable-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $backup | Out-Null
foreach($e in $entries){$e.Backup=Join-Path $backup $e.Relative;if($e.Existed){New-Item -ItemType Directory -Path (Split-Path $e.Backup -Parent) -Force | Out-Null;Copy-Item -LiteralPath $e.Target -Destination $e.Backup}}
$groupBackup=Join-Path $backup 'chapter_groups.snbt';if($UpdateQuestbook -and $groupExisted){Copy-Item -LiteralPath $groupTarget -Destination $groupBackup}
[IO.File]::WriteAllText((Join-Path $backup 'manifest.json'),($entries|ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
foreach($e in $removed){$e.Backup=Join-Path $backup $e.Relative}
[IO.File]::WriteAllText((Join-Path $backup 'removed-chapters.json'),($removed|ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
try{
    foreach($e in $removed){$e.Backup=Join-Path $backup $e.Relative;New-Item -ItemType Directory -Path (Split-Path $e.Backup -Parent) -Force | Out-Null;Move-Item -LiteralPath $e.Target -Destination $e.Backup;if((Get-FileHash -LiteralPath $e.Backup -Algorithm SHA256).Hash -ne $e.SHA256){throw 'Archived chapter hash mismatch.'}}
    foreach($e in $entries){New-Item -ItemType Directory -Path (Split-Path $e.Target -Parent) -Force | Out-Null;Copy-Item -LiteralPath $e.Source -Destination $e.Target -Force;if((Get-FileHash -LiteralPath $e.Target -Algorithm SHA256).Hash -ne $e.SHA256){throw "Hash mismatch $($e.Relative)"}}
    if($UpdateQuestbook){
        New-Item -ItemType Directory -Path (Split-Path $groupTarget -Parent) -Force | Out-Null
        [IO.File]::WriteAllText($groupTarget,$groupText,[Text.UTF8Encoding]::new($false))
        if([IO.File]::ReadAllText($groupTarget,[Text.Encoding]::UTF8) -ne $groupText){throw 'Group verification failed.'}
    }
}catch{
    foreach($e in $removed){if($e.Backup -and (Test-Path -LiteralPath $e.Backup)){Copy-Item -LiteralPath $e.Backup -Destination $e.Target -Force}}
    if($UpdateQuestbook){if($groupExisted){Copy-Item -LiteralPath $groupBackup -Destination $groupTarget -Force}elseif(Test-Path -LiteralPath $groupTarget){Move-Item -LiteralPath $groupTarget -Destination (Join-Path $backup 'new-chapter_groups.snbt')}}
    foreach($e in $entries){if($e.Existed){Copy-Item -LiteralPath $e.Backup -Destination $e.Target -Force}else{if(Test-Path -LiteralPath $e.Target){$recover=Join-Path $backup ('new-files\'+$e.Relative);New-Item -ItemType Directory -Path (Split-Path $recover -Parent) -Force | Out-Null;Move-Item -LiteralPath $e.Target -Destination $recover}}}
    throw "Installation failed; previous files restored. $($_.Exception.Message)"
}
Write-Output "Installed $($entries.Count) files with SHA256 verification. Backup: $backup"
Write-Output "Archived $($removed.Count) explicitly requested author chapters. No save or unrelated chapter was edited."
if(-not $UpdateQuestbook){Write-Output 'Author questbook definitions, groups and KubeJS guide artwork were preserved.'}
