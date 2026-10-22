param([Parameter(Mandatory=$true)][string]$Pack)
$ErrorActionPreference='Stop'
function FileDigest([string]$Path){$stream=[IO.File]::OpenRead($Path);$hash=[Security.Cryptography.SHA256]::Create();try{return ([BitConverter]::ToString($hash.ComputeHash($stream))).Replace('-','').ToLowerInvariant()}finally{$stream.Dispose();$hash.Dispose()}}
$target=[IO.Path]::GetFullPath($Pack).TrimEnd('\')
if(-not(Test-Path -LiteralPath (Join-Path $target 'mods/tnc-1.0.0.jar'))){throw 'Choose the existing TN-C version directory'}
$processes=Get-CimInstance Win32_Process | Where-Object { $_.Name -in @('java.exe','javaw.exe') -and $_.CommandLine -and ($_.CommandLine.Contains($target) -or $_.CommandLine.Contains('BootstrapLauncher')) }
if($processes){throw 'Save and fully close Minecraft before applying this update'}
$manifest=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'manifest.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$allowed=@('mods/tnc-1.0.0.jar','config/ftbquests/quests/chapters/tnc_alchemy.snbt','config/ftbquests/quests/chapters/tnc_deep_arcana.snbt','config/ftbquests/quests/chapters/tnc_field_04_workshop.snbt','config/ftbquests/quests/chapters/tnc_magic_progression.snbt')
if(@($manifest.files.PSObject.Properties).Count -ne 5){throw 'Expected exactly the jar and four TN-C blueprint chapters'}
foreach($file in $manifest.files.PSObject.Properties){
    if($file.Name -notin $allowed){throw 'Unexpected update file'}
    $dest=[IO.Path]::GetFullPath((Join-Path $target $file.Name))
    if(-not $dest.StartsWith($target+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Destination escapes the pack'}
    if((FileDigest (Join-Path $PSScriptRoot $file.Name)) -ne $file.Value){throw ('Hash mismatch: '+$file.Name)}
}
$questRoot=Join-Path $target 'config/ftbquests/quests'
$newChapters=@($allowed | Where-Object { $_.StartsWith('config/ftbquests/quests/') } | ForEach-Object {[IO.Path]::GetFullPath((Join-Path $target $_))})
$protected=@{}
Get-ChildItem -LiteralPath $questRoot -Recurse -File | Where-Object { $_.FullName -notin $newChapters} | ForEach-Object {$protected[$_.FullName]=(FileDigest $_.FullName)}
$backup=Join-Path $target ('.tnc-backups/magic-progression-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $backup -Force | Out-Null
Copy-Item -LiteralPath $questRoot -Destination (Join-Path $backup 'quests-full') -Recurse
foreach($file in $manifest.files.PSObject.Properties){
    $dest=Join-Path $target $file.Name
    if(Test-Path -LiteralPath $dest){$save=Join-Path $backup $file.Name;New-Item -ItemType Directory -Path (Split-Path $save -Parent) -Force | Out-Null;Copy-Item -LiteralPath $dest -Destination $save}
    New-Item -ItemType Directory -Path (Split-Path $dest -Parent) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $file.Name) -Destination $dest -Force
    if((FileDigest $dest) -ne $file.Value){throw 'Installed hash mismatch'}
}
foreach($path in $protected.Keys){if((FileDigest $path) -ne $protected[$path]){throw 'An existing quest file changed'}}
Write-Output 'Magic progression update installed. Four TN-C blueprint chapters updated; other quests and saves preserved.'
