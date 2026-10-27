param()
$ErrorActionPreference='Stop'
$warriorRoot=Split-Path -Parent $PSScriptRoot
$warriorJava=$env:JAVA_HOME;$warriorGradle=$env:GRADLE_USER_HOME
Push-Location $warriorRoot
try {
    $env:JAVA_HOME=Join-Path $warriorRoot '.jdk17'
    $env:GRADLE_USER_HOME=Join-Path $warriorRoot '.gradle-user'
    & .\gradlew.bat classes -PwarriorUiAudit -PbuildingGameTests --offline --console=plain
    if($LASTEXITCODE -ne 0){throw 'UI audit compilation failed'}
    $warriorConfigPath=Join-Path $warriorRoot 'build/resources/main/tnc.equipment.mixins.json'
    $warriorOriginal=[IO.File]::ReadAllBytes($warriorConfigPath)
    $warriorBackup=Join-Path $warriorRoot 'work/warrior-ui-config-backup.json'
    [IO.File]::WriteAllBytes($warriorBackup,$warriorOriginal)
    $warriorStarted=Get-Date
    try {
        # This isolated GUI audit has no entities; skip the unrelated synthetic-Args ghost renderer.
        $warriorConfig=[Text.Encoding]::UTF8.GetString($warriorOriginal) | ConvertFrom-Json
        $warriorConfig.client=@($warriorConfig.client | Where-Object {$_ -ne 'ArcanaGhostRendererMixin'})
        [IO.File]::WriteAllText($warriorConfigPath,($warriorConfig | ConvertTo-Json -Depth 10),[Text.UTF8Encoding]::new($false))
        & .\gradlew.bat runClient -PwarriorUiAudit -PbuildingGameTests -x downloadAssets -x processResources --offline --console=plain *> work/warrior-ui-audit.log
        $warriorExit=$LASTEXITCODE
    } finally { [IO.File]::WriteAllBytes($warriorConfigPath,$warriorOriginal) }
    if($warriorExit -ne 0){throw 'UI audit failed; see work/warrior-ui-audit.log'}
    $warriorReport=Get-Item -LiteralPath 'work/warrior-ui-audit-client/screenshots/warrior-ui-audit.json'
    $warriorResult=Get-Content -LiteralPath $warriorReport.FullName -Raw | ConvertFrom-Json
    if($warriorReport.LastWriteTime -lt $warriorStarted -or $warriorResult.cases -ne 7 -or -not $warriorResult.buttonStatesVerified -or -not $warriorResult.hotkeyRegistered){throw 'Expected seven fresh rendered cases with verified buttons and registered hotkey'}
    Write-Output 'PASS: seven warrior practice screens rendered; buttons and hotkey registration verified.'
} finally {
    $env:JAVA_HOME=$warriorJava;$env:GRADLE_USER_HOME=$warriorGradle;Pop-Location
}
