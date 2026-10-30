param([Parameter(Mandatory=$true)][string]$ManifestPath,
      [ValidatePattern('^[a-z0-9-]+$')][string]$BackupPrefix='tnc-light-mirror-ink-clear')
$ErrorActionPreference='Stop'
$taskRoot='E:\download\正式版 2.12.6.1\.minecraft\versions\元素觉醒1.4.3-魔改版-20260915'
$taskRoot=[IO.Path]::GetFullPath($taskRoot)
$taskManifest=Get-Content -LiteralPath $ManifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
$taskTargets=@()
foreach($taskFile in $taskManifest.files){
    $taskTarget=[IO.Path]::GetFullPath([IO.Path]::Combine($taskRoot,$taskFile.relative))
    if(-not $taskTarget.StartsWith($taskRoot+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Target escapes intended modpack'}
    if((Get-FileHash -LiteralPath $taskFile.source -Algorithm SHA256).Hash -ne $taskFile.hash){throw 'Prepared source changed'}
    if($null -eq $taskFile.expectedLiveHash){
        if(Test-Path -LiteralPath $taskTarget){throw ('Unexpected existing target: '+$taskTarget)}
    }else{
        if((Get-FileHash -LiteralPath $taskTarget -Algorithm SHA256).Hash -ne $taskFile.expectedLiveHash){throw ('Formal target changed; rebuild merge: '+$taskTarget)}
    }
    $taskTargets+=@{File=$taskFile;Target=$taskTarget}
}
$taskBackup=Join-Path $taskRoot ('backups\'+$BackupPrefix+'-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
if(Test-Path -LiteralPath $taskBackup){throw 'Backup path already exists'}
New-Item -ItemType Directory -Path $taskBackup | Out-Null
foreach($taskEntry in $taskTargets){
    if($null -ne $taskEntry.File.expectedLiveHash){
        $taskCopy=Join-Path $taskBackup $taskEntry.File.relative
        New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($taskCopy)) -Force | Out-Null
        Copy-Item -LiteralPath $taskEntry.Target -Destination $taskCopy
        if((Get-FileHash -LiteralPath $taskCopy -Algorithm SHA256).Hash -ne $taskEntry.File.expectedLiveHash){throw 'Backup verification failed'}
    }
}
Copy-Item -LiteralPath $ManifestPath -Destination (Join-Path $taskBackup 'manifest.json')
# All preconditions and backups are complete before replacing anything.
foreach($taskEntry in $taskTargets){
    Copy-Item -LiteralPath $taskEntry.File.source -Destination $taskEntry.Target -Force
    if((Get-FileHash -LiteralPath $taskEntry.Target -Algorithm SHA256).Hash -ne $taskEntry.File.hash){throw ('Installed file verification failed: '+$taskEntry.Target)}
}
[PSCustomObject]@{Backup=$taskBackup;Files=$taskTargets.Count;InstalledJarHash=(Get-FileHash -LiteralPath (Join-Path $taskRoot 'mods\tnc-1.0.0.jar') -Algorithm SHA256).Hash} | ConvertTo-Json
