param([switch]$ServerOnly)
$ErrorActionPreference = 'Stop'
$warriorRoot = Split-Path -Parent $PSScriptRoot
$warriorOriginalJava = $env:JAVA_HOME
$warriorOriginalGradle = $env:GRADLE_USER_HOME
Push-Location $warriorRoot
try {
    $env:JAVA_HOME = Join-Path $warriorRoot '.jdk17'
    $env:GRADLE_USER_HOME = Join-Path $warriorRoot '.gradle-user'
    if (-not $ServerOnly) {
        & .\gradlew.bat test --tests 'com.tnc.tnc.warrior.*' --offline --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Warrior unit tests failed.' }
    }
    New-Item -ItemType Directory -Force -Path (Join-Path $warriorRoot 'work') | Out-Null
    $warriorLog = Join-Path $warriorRoot 'work/warrior-gametest.log'
    & .\gradlew.bat runGameTestServer -PwarriorTests -PbuildingGameTests -x downloadAssets --offline --console=plain *> $warriorLog
    if ($LASTEXITCODE -ne 0) { throw "Warrior server tests failed. See $warriorLog" }
    if (-not (Select-String -LiteralPath $warriorLog -Pattern '19 GAME TESTS COMPLETE' -Quiet) -or
        -not (Select-String -LiteralPath $warriorLog -Pattern 'All 19 required tests passed' -Quiet)) {
        throw "Expected nineteen passing server tests; zero discovered tests is not success. See $warriorLog"
    }
    Write-Output "PASS: all nineteen warrior server tests executed. Log: $warriorLog"
} finally {
    $env:JAVA_HOME = $warriorOriginalJava
    $env:GRADLE_USER_HOME = $warriorOriginalGradle
    Pop-Location
}
