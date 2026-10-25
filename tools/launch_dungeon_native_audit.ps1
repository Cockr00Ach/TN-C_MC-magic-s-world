param([Parameter(Mandatory=$true)][string]$LauncherBatch,[Parameter(Mandatory=$true)][string]$LivePack,[Parameter(Mandatory=$true)][string]$AuditRoot,[Parameter(Mandatory=$true)][string]$Output,[string]$OffscreenAgent="",[switch]$UiOnly,[switch]$JourneyOnly,[switch]$FeedbackOnly,[switch]$LeylineOnly,[switch]$EquipmentOnly,[switch]$BotanicalOnly,[switch]$WorldFeedback)
$ErrorActionPreference='Stop'
if([string]::IsNullOrWhiteSpace($OffscreenAgent)){$OffscreenAgent=Join-Path (Split-Path $PSScriptRoot -Parent) 'work/offscreen-agent/offscreen-audit.jar'}
$live=[IO.Path]::GetFullPath($LivePack).TrimEnd('\')
$audit=[IO.Path]::GetFullPath($AuditRoot).TrimEnd('\')
if($audit -eq $live -or -not(Test-Path -LiteralPath (Join-Path $audit 'saves/zero-forge/level.dat'))){throw 'A separate prepared audit save is required'}
$validated=$false
foreach($encoding in @([Text.Encoding]::UTF8,[Text.Encoding]::GetEncoding(936))){
    $lines=@([IO.File]::ReadAllLines($LauncherBatch,$encoding)|Where-Object {$_ -match '^"[^"\r\n]*javaw?\.exe"\s+'})
    if($lines.Count -ne 1 -or $lines[0] -notmatch '^"(?<exe>[^"\r\n]+)"\s+(?<args>.+)$'){continue}
    $javaExe=$Matches.exe;$javaArguments=$Matches.args
    if($javaArguments -notmatch '--gameDir\s+"(?<dir>[^"\r\n]+)"'){continue}
    if([IO.Path]::GetFullPath($Matches.dir).TrimEnd('\') -eq $live){$validated=$true;break}
}
if(-not $validated){throw 'Launch command does not target the expected source instance'}
if($javaArguments.Contains('--quickPlay')){throw 'Unexpected preexisting quick-play arguments'}
# Launcher credentials remain in memory, never printed or written to disk.
$javaArguments=[regex]::Replace($javaArguments,'--gameDir\s+"[^"\r\n]+"','--gameDir "'+$audit+'"')
$javaArguments=[regex]::Replace($javaArguments,'--width\s+\d+','--width 1600')
$javaArguments=[regex]::Replace($javaArguments,'--height\s+\d+','--height 900')
if($LeylineOnly -or $EquipmentOnly -or $WorldFeedback){$javaArguments=[regex]::Replace($javaArguments,'-Xmx\S+','-Xmx8G');$javaArguments=[regex]::Replace($javaArguments,'-Xms\S+','-Xms1G')}
if(-not(Test-Path -LiteralPath $OffscreenAgent)){throw 'Build tools/OffscreenAuditAgent.java first'}
$javaArguments='-Dtnc.dungeonAudit=true "-javaagent:'+[IO.Path]::GetFullPath($OffscreenAgent)+'" "-Dtnc.dungeonAuditOutput='+[IO.Path]::GetFullPath($Output)+'" '+$javaArguments+' --quickPlaySingleplayer zero-forge'
if($FeedbackOnly){$javaArguments="-Dtnc.feedbackAudit=true -Dtnc.journeyAudit=true "+$javaArguments}
if($JourneyOnly){$javaArguments='-Dtnc.journeyAudit=true '+$javaArguments}
if($UiOnly){$javaArguments='-Dtnc.dungeonUiAudit=true '+$javaArguments}
if($LeylineOnly){$javaArguments='-Dtnc.leylineAudit=true '+$javaArguments}
if($BotanicalOnly){$javaArguments='-Dtnc.botanicalAudit=true '+$javaArguments}
if($WorldFeedback){$javaArguments='-Dtnc.worldFeedbackAudit=true '+$javaArguments}
if($EquipmentOnly){$javaArguments='-Dtnc.equipmentAudit=true '+$javaArguments}
$outputFolder=[IO.Path]::GetFullPath($Output)
[IO.Directory]::CreateDirectory($outputFolder) | Out-Null
$shaderFile=Join-Path $audit 'config/oculus.properties'
$shaders=(Test-Path -LiteralPath $shaderFile) -and ((Get-Content -LiteralPath $shaderFile) -contains 'enableShaders=true')
@{jar_sha256=(Get-FileHash -LiteralPath (Join-Path $audit 'mods/tnc-1.0.0.jar') -Algorithm SHA256).Hash.ToLowerInvariant();shaders=$shaders;started_utc=[DateTime]::UtcNow.ToString('o');audit_root=$audit} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $outputFolder 'launch.json') -Encoding utf8
$p=Start-Process -FilePath $javaExe -ArgumentList $javaArguments -WorkingDirectory $audit -WindowStyle Hidden -RedirectStandardOutput (Join-Path $audit "dungeon-audit-jvm-stdout.log") -RedirectStandardError (Join-Path $audit "dungeon-audit-jvm-stderr.log") -PassThru
Write-Output ('Isolated Dungeon audit started; process ID: '+$p.Id)
if($p.WaitForExit(1000)){Write-Output ('Audit exited early with code '+$p.ExitCode)}
