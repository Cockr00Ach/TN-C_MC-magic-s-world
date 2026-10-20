param([Parameter(Mandatory=$true)][string]$Pack)
$ErrorActionPreference='Stop'
function FileDigest([string]$Path){$stream=[IO.File]::OpenRead($Path);$hash=[Security.Cryptography.SHA256]::Create();try{return ([BitConverter]::ToString($hash.ComputeHash($stream))).Replace('-','').ToLowerInvariant()}finally{$stream.Dispose();$hash.Dispose()}}
$target=[IO.Path]::GetFullPath($Pack).TrimEnd('\')
if(-not(Test-Path -LiteralPath (Join-Path $target 'mods/tnc-1.0.0.jar'))){throw 'Choose the existing TN-C version directory'}
$processes=Get-CimInstance Win32_Process | Where-Object { $_.Name -in @('java.exe','javaw.exe') -and $_.CommandLine -and ($_.CommandLine.Contains($target) -or $_.CommandLine.Contains('BootstrapLauncher')) }
if($processes){throw 'Save and fully close Minecraft before applying this update'}
$manifest=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'manifest.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$allowed=@('mods/tnc-1.0.0.jar','config/ftbquests/quests/chapters/tnc_alchemy.snbt')
if(@($manifest.files.PSObject.Properties).Count -ne 2){throw 'Expected exactly the jar and the new alchemy chapter'}
foreach($file in $manifest.files.PSObject.Properties){
    if($file.Name -notin $allowed){throw 'Unexpected update file'}
    $dest=[IO.Path]::GetFullPath((Join-Path $target $file.Name))
    if(-not $dest.StartsWith($target+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Destination escapes the pack'}
    if((FileDigest (Join-Path $PSScriptRoot $file.Name)) -ne $file.Value){throw ('Hash mismatch: '+$file.Name)}
}
$configPath=Join-Path $target 'config/mca.json'
$config=if(Test-Path -LiteralPath $configPath){Get-Content -LiteralPath $configPath -Raw -Encoding UTF8 | ConvertFrom-Json}else{[PSCustomObject]@{}}
if($null -eq $config){throw 'Invalid MCA config; nothing installed'}
$questRoot=Join-Path $target 'config/ftbquests/quests'
$newChapter=[IO.Path]::GetFullPath((Join-Path $questRoot 'chapters/tnc_alchemy.snbt'))
$protected=@{}
Get-ChildItem -LiteralPath $questRoot -Recurse -File | Where-Object {$_.FullName -ne $newChapter} | ForEach-Object {$protected[$_.FullName]=(FileDigest $_.FullName)}
$backup=Join-Path $target ('.tnc-backups/life-expedition-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $backup -Force | Out-Null
Copy-Item -LiteralPath $questRoot -Destination (Join-Path $backup 'quests-full') -Recurse
if(Test-Path -LiteralPath $configPath){Copy-Item -LiteralPath $configPath -Destination (Join-Path $backup 'mca.json')}
foreach($file in $manifest.files.PSObject.Properties){
    $dest=Join-Path $target $file.Name
    if(Test-Path -LiteralPath $dest){$save=Join-Path $backup $file.Name;New-Item -ItemType Directory -Path (Split-Path $save -Parent) -Force | Out-Null;Copy-Item -LiteralPath $dest -Destination $save}
    New-Item -ItemType Directory -Path (Split-Path $dest -Parent) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $file.Name) -Destination $dest -Force
    if((FileDigest $dest) -ne $file.Value){throw 'Installed hash mismatch'}
}
foreach($key in @('launchIntoDestiny','allowDestinyTeleportation')){$config | Add-Member -NotePropertyName $key -NotePropertyValue $false -Force}
[IO.File]::WriteAllText($configPath,($config | ConvertTo-Json -Depth 100),[Text.UTF8Encoding]::new($false))
foreach($path in $protected.Keys){if((FileDigest $path) -ne $protected[$path]){throw 'An existing quest file changed'}}
Write-Output 'Journey/alchemy update installed. Existing quest chapters and saves preserved; only two MCA startup options changed.'
