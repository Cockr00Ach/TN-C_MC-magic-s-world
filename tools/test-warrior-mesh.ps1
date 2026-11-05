param()
# 龙/刀波/虚影的"看得见"验证：真的起一个客户端里，按固定机位拍 7 张，然后自己退出。
# 和 test-warrior-ui.ps1 同一套做法：隔离工作目录 + 临时摘掉与本场景无关的 mixin。
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$java=$env:JAVA_HOME;$gradle=$env:GRADLE_USER_HOME
Push-Location $root
try {
    $env:JAVA_HOME=Join-Path $root '.jdk17'
    $env:GRADLE_USER_HOME=Join-Path $root '.gradle-user'
    & .\gradlew.bat classes -PwarriorAnimAudit -PwarriorMeshAudit -PbuildingGameTests --offline --console=plain
    if($LASTEXITCODE -ne 0){throw 'mesh audit compilation failed'}
    $config=Join-Path $root 'build/resources/main/tnc.equipment.mixins.json'
    $original=[IO.File]::ReadAllBytes($config)
    $started=Get-Date
    try {
        # 孤立的截图场景里没有那个模组的实体；不摘掉它的 ghost 渲染器，Forge 会在
        # 启动时因为 synthetic-Args 找不到类直接崩（见 crash-reports）。
        # 纯文本摘除：PS 5.1 里 ConvertFrom-Json 的对象属性赋值不可靠，别赌。
        $text=[Text.Encoding]::UTF8.GetString($original)
        $patched=[regex]::Replace($text,'\s*,\s*"ArcanaGhostRendererMixin"','')
        if($patched -eq $text){throw 'ArcanaGhostRendererMixin not found in mixin config'}
        [IO.File]::WriteAllText($config,$patched,[Text.UTF8Encoding]::new($false))
        & .\gradlew.bat runClient -PwarriorAnimAudit -PwarriorMeshAudit -PbuildingGameTests -x downloadAssets -x processResources --offline --console=plain *> work\mesh-audit.log
    } finally { [IO.File]::WriteAllBytes($config,$original) }
    $folder='work/warrior-mesh-audit-client/screenshots'
    $shots=@(Get-ChildItem -LiteralPath $folder -Filter 'warrior-mesh-*.png' -ErrorAction SilentlyContinue |
        Where-Object {$_.LastWriteTime -ge $started})
    if($shots.Count -lt 7){throw "expected 7 fresh screenshots, got $($shots.Count); see work/mesh-audit.log"}
    foreach($shot in $shots){Write-Output ("{0} {1} bytes" -f $shot.Name,$shot.Length)}
    Write-Output "PASS: $($shots.Count) warrior mesh screenshots rendered (incl. dragon)."
} finally {
    $env:JAVA_HOME=$java;$env:GRADLE_USER_HOME=$gradle;Pop-Location
}
