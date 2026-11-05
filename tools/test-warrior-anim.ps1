param()
$ErrorActionPreference='Stop'
$animRoot=Split-Path -Parent $PSScriptRoot
$animJava=$env:JAVA_HOME;$animGradle=$env:GRADLE_USER_HOME
Push-Location $animRoot
try {
    $env:JAVA_HOME=Join-Path $animRoot '.jdk17'
    $env:GRADLE_USER_HOME=Join-Path $animRoot '.gradle-user'
    $animStarted=Get-Date
    & .\gradlew.bat classes -PwarriorAnimAudit -PbuildingGameTests --offline --console=plain
    if($LASTEXITCODE -ne 0){throw 'Animation audit compilation failed'}
    $animConfigPath=Join-Path $animRoot 'build/resources/main/tnc.equipment.mixins.json'
    $animOriginal=[IO.File]::ReadAllBytes($animConfigPath)
    try {
        # 和 UI 审计同样的规避：这台 dev 客户端上 ArcanaGhostRendererMixin 会触发
        # Mixin 的 synthetic Args 类加载失败（与战士动作无关）。
        $animText=[Text.Encoding]::UTF8.GetString($animOriginal)
        $animText=$animText -replace ',\s*"ArcanaGhostRendererMixin"',''
        [IO.File]::WriteAllText($animConfigPath,$animText,[Text.UTF8Encoding]::new($false))
        & .\gradlew.bat runClient -PwarriorAnimAudit -PbuildingGameTests -x downloadAssets -x processResources --offline --console=plain *> work\warrior-anim-audit.log
        $animExit=$LASTEXITCODE
    } finally { [IO.File]::WriteAllBytes($animConfigPath,$animOriginal) }
    if($animExit -ne 0){throw 'Animation audit failed; see work/warrior-anim-audit.log'}
    $animReport=Get-Item -LiteralPath 'run/warrior-anim-audit.json'
    $animResult=Get-Content -LiteralPath $animReport.FullName -Raw | ConvertFrom-Json
    if($animReport.LastWriteTime -lt $animStarted){throw 'Animation audit report is stale (client did not finish)'}
    if($animResult.animations -ne 10){throw "Expected ten animations, got $($animResult.animations)"}
    if($animResult.failures -ne 0){throw "Animations failed: $($animResult.failureDetails -join ' | ')"}
    Write-Output "PASS: $($animResult.animations) player animations rendered with a changed arm pose each; report: $($animReport.FullName)"
} finally {
    $env:JAVA_HOME=$animJava;$env:GRADLE_USER_HOME=$animGradle;Pop-Location
}
