param([Parameter(Mandatory=$true)][string]$ManifestPath)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot '_common.ps1')
$manifest=Get-Content -LiteralPath $ManifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
$work=Find-TncWorkPack
$live=Find-TncLivePack -WorkPack $work
if(-not $live){throw 'PCL instance not found'}
function Assert-Closed {
    foreach($process in @(Get-CimInstance Win32_Process -Filter "Name like '%java%'")){
        if($process.CommandLine -match 'GradleDaemon|GradleWrapperMain|GradleWorkerMain'){continue}
        if($process.CommandLine -match 'gametestserver' -and $process.CommandLine -match [regex]::Escape($TncRepoRoot) -and $process.CommandLine -notmatch '\.minecraft\\versions'){continue}
        # Another collaborator's isolated userdev client does not load the installed pack.
        if($process.CommandLine -match '--launchTarget\s+forgeclientuserdev' -and $process.CommandLine -match [regex]::Escape($TncRepoRoot) -and $process.CommandLine -notmatch '\.minecraft\\versions|\\modpack\\'){
            if($process.CommandLine -match '--gameDir\s+("[^"]+"|\S+)'){
                $argument=$Matches[1].Trim('"')
                if($argument -eq '.'){
                    $parent=Get-CimInstance Win32_Process -Filter "ProcessId=$($process.ParentProcessId)"
                    if($parent.CommandLine -match 'GradleDaemon|GradleWrapperMain'){continue}
                }
                $devDirectory=[IO.Path]::GetFullPath($argument)
                $projectRoot=[IO.Path]::GetFullPath($TncRepoRoot).TrimEnd('\')+'\'
                if($devDirectory.StartsWith($projectRoot,[StringComparison]::OrdinalIgnoreCase)){continue}
            }
        }
        throw 'Minecraft or an unclassified Java application is running; close it before installing.'
    }
}
function Assert-Base {
    if((Get-FileHash -LiteralPath (Join-Path $live 'mods\tnc-1.0.0.jar')).Hash -ne $manifest.baseInstalledHash){throw 'Live JAR changed; rebase the scoped patch before installing.'}
    foreach($file in $manifest.files){
        if($file.relative -eq 'mods\tnc-1.0.0.jar'){continue}
        $path=Join-Path $live $file.relative
        if($file.expectedLiveHash){
            if(-not(Test-Path -LiteralPath $path) -or (Get-FileHash -LiteralPath $path).Hash -ne $file.expectedLiveHash){throw "Live data changed: $($file.relative)"}
        }elseif(Test-Path -LiteralPath $path){throw "New live data appeared: $($file.relative)"}
    }
}
Assert-Closed
Assert-Base
$backup=Join-Path $TncRepoRoot ('work\install-backups\dungeon-mobs-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $backup -Force | Out-Null
$operations=@()
foreach($file in $manifest.files){
    if((Get-FileHash -LiteralPath $file.source).Hash -ne $file.hash){throw "Changed source: $($file.source)"}
    foreach($pack in @($live,$work)){
        $target=[IO.Path]::GetFullPath((Join-Path $pack $file.relative))
        $root=[IO.Path]::GetFullPath($pack).TrimEnd('\')+'\'
        if(-not $target.StartsWith($root,[StringComparison]::OrdinalIgnoreCase)){throw 'Manifest escaped pack root'}
        $saved=Join-Path $backup ('previous-'+$operations.Count)
        $exists=Test-Path -LiteralPath $target
        if($exists){Copy-Item -LiteralPath $target -Destination $saved}
        $operations+=[pscustomobject]@{source=$file.source;target=$target;hash=$file.hash;backup=$saved;existed=$exists}
    }
}
$operations | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $backup 'restore-manifest.json') -Encoding UTF8
Assert-Closed
Assert-Base
$written=@()
try {
    foreach($file in $operations){
        New-Item -ItemType Directory -Path (Split-Path $file.target -Parent) -Force | Out-Null
        Copy-Item -LiteralPath $file.source -Destination $file.target -Force
        $written+=$file
        if((Get-FileHash -LiteralPath $file.target).Hash -ne $file.hash){throw "Installed hash mismatch: $($file.target)"}
    }
    $env:JAVA_HOME=Join-Path $TncRepoRoot '.jdk17'
    $env:TNC_LIVE_ROOT=Split-Path $live -Parent
    $log=Join-Path (Split-Path ([IO.Path]::GetFullPath($ManifestPath)) -Parent) 'install-verification.log'
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'verify_mod_jar.ps1') -WorkPack $work -JarPath $manifest.jar *> $log
    if($LASTEXITCODE -ne 0){throw "JAR verification failed: $log"}
}catch{
    foreach($file in $written){
        if((Get-FileHash -LiteralPath $file.target).Hash -ne $file.hash){Write-Warning "Externally updated target retained: $($file.target)";continue}
        if($file.existed){Copy-Item -LiteralPath $file.backup -Destination $file.target -Force}
        else{Remove-Item -LiteralPath $file.target}
    }
    throw
}
Write-Output "Installed $($manifest.files.Count) files to both PCL and workspace; backup: $backup"
