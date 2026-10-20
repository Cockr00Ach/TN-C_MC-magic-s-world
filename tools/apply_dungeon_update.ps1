param([Parameter(Mandatory=$true)][string]$Pack)
$ErrorActionPreference='Stop'
$target=[IO.Path]::GetFullPath($Pack)
if(-not(Test-Path -LiteralPath (Join-Path $target 'mods/tnc-1.0.0.jar'))){throw 'Choose the existing TN-C version directory'}
$processes=Get-CimInstance Win32_Process | Where-Object { $_.Name -in @('java.exe','javaw.exe') -and $_.CommandLine -and ($_.CommandLine.Contains($target) -or $_.CommandLine.Contains('BootstrapLauncher')) }
if($processes){throw 'Save and fully close Minecraft before applying this update'}
$manifest=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'manifest.json') -Raw | ConvertFrom-Json
$allowed=@('mods/tnc-1.0.0.jar','config/ftbquests/quests/chapters/tnc_dungeons.snbt')
foreach($file in $manifest.files.PSObject.Properties){if($file.Name -notin $allowed){throw 'Unexpected update file'};$source=Join-Path $PSScriptRoot $file.Name;if((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant() -ne $file.Value){throw ('Hash mismatch: '+$file.Name)}}
$backup=Join-Path $target ('.tnc-backups/dungeons-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $backup -Force | Out-Null
foreach($file in $manifest.files.PSObject.Properties){$dest=Join-Path $target $file.Name;if(Test-Path -LiteralPath $dest){$save=Join-Path $backup $file.Name;New-Item -ItemType Directory -Path (Split-Path $save -Parent) -Force | Out-Null;Copy-Item -LiteralPath $dest -Destination $save};New-Item -ItemType Directory -Path (Split-Path $dest -Parent) -Force | Out-Null;Copy-Item -LiteralPath (Join-Path $PSScriptRoot $file.Name) -Destination $dest -Force;if((Get-FileHash -LiteralPath $dest -Algorithm SHA256).Hash.ToLowerInvariant() -ne $file.Value){throw 'Installed hash mismatch'}}
Write-Output 'Dungeon update installed. Existing saves and all other quest chapters preserved.'
