param(
    [Parameter(Mandatory=$true)][string]$LauncherBatch,
    [Parameter(Mandatory=$true)][string]$LivePack,
    [string]$World
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot '_common.ps1')
if (@(Get-TncJavaProcesses).Count) {throw 'A Java process is already running; no second game was started.'}
$pack=[IO.Path]::GetFullPath($LivePack).TrimEnd('\')
# Reuse the launcher's own command, without printing, copying or saving credentials.
# Never execute the batch's other commands or invoke a shell with its text.
$batchBytes=[IO.File]::ReadAllBytes($LauncherBatch)
try {
    $batchText=(New-Object Text.UTF8Encoding($false,$true)).GetString($batchBytes)
} catch [Text.DecoderFallbackException] {
    # PCL on Chinese Windows exports its batch in the Windows ANSI code page.
    $batchText=[Text.Encoding]::GetEncoding(936).GetString($batchBytes)
}
$lines=@($batchText -split '\r?\n' | Where-Object {$_ -match '^"[^"\r\n]*javaw?\.exe"\s+'})
if($lines.Count -ne 1){throw 'Expected exactly one quoted Java launch command.'}
if($lines[0] -notmatch '^"(?<exe>[^"\r\n]+)"\s+(?<args>.+)$'){throw 'Unsupported launcher syntax.'}
$javaExe=$Matches.exe
$javaArguments=$Matches.args
if(-not (Test-Path -LiteralPath $javaExe)){throw 'Launcher Java executable is missing.'}
if($javaArguments -notmatch '--gameDir\s+"(?<dir>[^"\r\n]+)"'){throw 'Expected an explicit gameDir.'}
if([IO.Path]::GetFullPath($Matches.dir).TrimEnd('\') -ne $pack){throw 'Launcher targets a different instance.'}
if($World){
    if($World -match '["\\/\r\n]' -or $World -eq '..'){throw 'World must be one existing save folder name.'}
    if(-not (Test-Path -LiteralPath (Join-Path (Join-Path $pack 'saves') $World))){throw 'Save not found.'}
    if($javaArguments.Contains('--quickPlay')){throw 'Launcher already contains quick-play arguments.'}
    $javaArguments+=' --quickPlaySingleplayer "'+$World+'"'
}
$process=Start-Process -FilePath $javaExe -ArgumentList $javaArguments -WorkingDirectory $pack -WindowStyle Hidden -PassThru
Write-Output ('Started existing PCL launch configuration. Process ID: '+$process.Id)
