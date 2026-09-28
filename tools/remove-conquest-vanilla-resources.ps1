param([Parameter(Mandatory=$true)][string]$LivePack,[string]$VanillaClientJar,[switch]$Install)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot '_common.ps1')
Add-Type -AssemblyName System.IO.Compression.FileSystem
$pack=[IO.Path]::GetFullPath($LivePack)
$source=Join-Path $pack 'mods\ConquestReforged-forge-1.20.1-1.6.0.jar'
if(-not(Test-Path -LiteralPath $source)){throw 'Expected Conquest 1.6.0 jar not found.'}
if($Install -and @(Get-TncJavaProcesses).Count){throw 'Close Minecraft first; original jar was not changed.'}
if(-not $VanillaClientJar){
    $clients=@(Get-ChildItem -LiteralPath $pack -File -Filter '*.jar')
    if($clients.Count -ne 1){throw 'Specify the Minecraft 1.20.1 VanillaClientJar.'}
    $VanillaClientJar=$clients[0].FullName
}
$vanillaNames=New-Object 'System.Collections.Generic.HashSet[string]'
$client=[IO.Compression.ZipFile]::OpenRead($VanillaClientJar)
try{
    foreach($entry in $client.Entries){if($entry.FullName.StartsWith('assets/minecraft/')){[void]$vanillaNames.Add($entry.FullName)}}
    if(-not $vanillaNames.Contains('assets/minecraft/blockstates/oak_leaves.json') -or -not $vanillaNames.Contains('assets/minecraft/textures/block/oak_leaves.png')){throw 'Client jar is not a vanilla resource source.'}
}finally{$client.Dispose()}
function Test-Override([string]$Name){
    return $vanillaNames.Contains($Name) -or ($Name.EndsWith('.mcmeta') -and $vanillaNames.Contains($Name.Substring(0,$Name.Length-7))) -or $Name.StartsWith('assets/minecraft/optifine/') -or $Name.StartsWith('resourcepacks/rp_crrp/assets/minecraft/')
}
$backup=Join-Path $TncRepoRoot ('work\backups\conquest-vanilla-removal-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $backup | Out-Null
$original=Join-Path $backup 'ConquestReforged-original.jar'
$prepared=Join-Path $backup 'ConquestReforged-without-vanilla-overrides.jar'
Copy-Item -LiteralPath $source -Destination $original
Copy-Item -LiteralPath $original -Destination $prepared
$originalHash=(Get-FileHash -LiteralPath $original).Hash
$zip=[IO.Compression.ZipFile]::Open($prepared,[IO.Compression.ZipArchiveMode]::Update)
try{
    $entries=@($zip.Entries | Where-Object {Test-Override $_.FullName})
    $removed=$entries.Count
    if(-not $removed){throw 'No vanilla overrides found; refusing to modify an unexpected jar.'}
    foreach($entry in $entries){$entry.Delete()}
}finally{$zip.Dispose()}
$zip=[IO.Compression.ZipFile]::OpenRead($prepared)
try{
    if(@($zip.Entries | Where-Object {Test-Override $_.FullName}).Count){throw 'Removal verification failed.'}
    if(-not $zip.GetEntry('com/conquestrefabricated/RefabricatedMod.class') -or -not $zip.GetEntry('META-INF/mods.toml')){throw 'Mod code/registration missing.'}
    if(-not @($zip.Entries | Where-Object {$_.FullName.StartsWith('assets/conquest/textures/')}).Count){throw 'Conquest own textures missing.'}
}finally{$zip.Dispose()}
Write-Output "Prepared: $prepared"
Write-Output "Removed $removed vanilla asset overrides; kept mod classes and Conquest own block resources. Original: $original"
if($Install){
    if(@(Get-TncJavaProcesses).Count){throw 'Java started during preparation; no installation performed.'}
    if((Get-FileHash -LiteralPath $source).Hash -ne $originalHash){throw 'Source jar changed; no installation performed.'}
    try{
        Copy-Item -LiteralPath $prepared -Destination $source -Force
        if((Get-FileHash -LiteralPath $source).Hash -ne (Get-FileHash -LiteralPath $prepared).Hash){throw 'Installed hash mismatch.'}
    }catch{Copy-Item -LiteralPath $original -Destination $source -Force;throw}
    Write-Output 'Installed. Restart Minecraft. Saves and options were not edited.'
}
