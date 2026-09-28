# Native E -> inventory questbook chapters. Never intercept any GUI.
# Original chapters, teammate story data and save progress are preserved.
param([Parameter(Mandatory=$true)][string]$LivePack,[switch]$CheckOnly)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot '_common.ps1')
$pack=[IO.Path]::GetFullPath($LivePack).TrimEnd('\')
if(-not(Test-Path -LiteralPath (Join-Path $pack 'mods') -PathType Container)){throw 'Expected an existing modpack'}
if(-not $CheckOnly -and @(Get-TncJavaProcesses).Count){throw 'Close Minecraft and dev Java processes first; no files changed.'}
$questDir=Join-Path $pack 'config\ftbquests\quests'
$groupTarget=Join-Path $questDir 'chapter_groups.snbt'
if(-not(Test-Path -LiteralPath $groupTarget -PathType Leaf)){throw 'Native FTB chapter_groups.snbt missing'}
$sourceDir=Join-Path $TncRepoRoot 'questbook\ftbquests'
$group=Get-Content -LiteralPath (Join-Path $sourceDir 'group.snbt') -Raw -Encoding UTF8 | ConvertFrom-Json
$groupText=[IO.File]::ReadAllText($groupTarget,[Text.Encoding]::UTF8)
if($groupText -notmatch [regex]::Escape($group.id)){
    $array=[regex]::Match($groupText,'["'']?chapter_groups["'']?\s*:\s*\[')
    if(-not $array.Success){throw 'Cannot safely find chapter group list'}
    $index=$array.Index+$array.Length
    $entry=$group | ConvertTo-Json -Depth 5 -Compress
    # FTB SNBT supports whitespace-separated list entries; existing bytes are retained.
    $groupText=$groupText.Insert($index,"`n$entry`n")
}
$files=@()
foreach($name in @('water_focus_1','water_staff_2','water_staff_3','water_staff_4','water_staff_5')){
    $files+=@{Source=(Join-Path $TncRepoRoot "src\main\resources\assets\tnc\textures\guide\$name.png");
        Relative="kubejs\assets\tnc\textures\guide\$name.png"}
}
$chapters=@(Get-ChildItem -LiteralPath (Join-Path $sourceDir 'chapters') -File -Filter 'tnc_guide_*.snbt')
if($chapters.Count -ne 9){throw 'Expected exactly nine TN-C guide chapters'}
$ids=[Collections.Generic.HashSet[string]]::new()
foreach($chapter in $chapters){
    $data=Get-Content -LiteralPath $chapter.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
    if($data.group -ne $group.id -or -not $ids.Add($data.id)){throw 'Invalid chapter group or duplicate ID'}
    foreach($quest in $data.quests){
        if(-not $ids.Add($quest.id)){throw 'Duplicate quest ID'}
        if($quest.rewards.Count -or $quest.dependencies.Count){throw 'Reading guide must not reward or lock gameplay'}
        foreach($task in $quest.tasks){if($task.type -ne 'checkmark' -or -not $ids.Add($task.id)){throw 'Invalid reading task'}}
    }
    $files+=@{Source=$chapter.FullName;Relative=('config\ftbquests\quests\chapters\'+$chapter.Name)}
}
foreach($f in $files){
    if(-not(Test-Path -LiteralPath $f.Source -PathType Leaf)){throw "Missing source: $($f.Source)"}
    $f.Target=[IO.Path]::GetFullPath((Join-Path $pack $f.Relative))
    if(-not $f.Target.StartsWith($pack+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Target outside pack'}
    $f.Hash=(Get-FileHash -LiteralPath $f.Source -Algorithm SHA256).Hash
}
if($CheckOnly){Write-Output "Native FTB preflight passed: nine chapters, $($ids.Count) stable IDs, no gameplay rewards. Nothing installed.";return}
$backup=Join-Path $TncRepoRoot ('work\backups\ftb-guide-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $backup | Out-Null
$groupBackup=Join-Path $backup 'chapter_groups.snbt'
Copy-Item -LiteralPath $groupTarget -Destination $groupBackup
foreach($f in $files){
    $f.Backup=Join-Path $backup $f.Relative
    $f.Existed=Test-Path -LiteralPath $f.Target -PathType Leaf
    New-Item -ItemType Directory -Path (Split-Path $f.Backup -Parent) -Force | Out-Null
    if($f.Existed){Copy-Item -LiteralPath $f.Target -Destination $f.Backup}
}
if(@(Get-TncJavaProcesses).Count){throw "Java started during preparation; no installation. Backup: $backup"}
try{
    foreach($f in $files){
        New-Item -ItemType Directory -Path (Split-Path $f.Target -Parent) -Force | Out-Null
        Copy-Item -LiteralPath $f.Source -Destination $f.Target -Force
        if((Get-FileHash -LiteralPath $f.Target -Algorithm SHA256).Hash -ne $f.Hash){throw "Hash mismatch: $($f.Relative)"}
    }
    [IO.File]::WriteAllText($groupTarget,$groupText,[Text.UTF8Encoding]::new($false))
    if([IO.File]::ReadAllText($groupTarget,[Text.Encoding]::UTF8) -ne $groupText){throw 'Group write verification failed'}
}catch{
    Copy-Item -LiteralPath $groupBackup -Destination $groupTarget -Force
    foreach($f in $files){
        if($f.Existed){Copy-Item -LiteralPath $f.Backup -Destination $f.Target -Force}
        elseif(Test-Path -LiteralPath $f.Target){Move-Item -LiteralPath $f.Target -Destination ($f.Backup+'.new-file')}
    }
    throw "Installation failed; restored previous files. $($_.Exception.Message)"
}
Write-Output "Installed five original icons, nine native chapters and merged guide group. SHA256 verified. Backup: $backup"
Write-Output 'Restart the same PCL instance, press E, click the top-left questbook, select TN-C guide.'
