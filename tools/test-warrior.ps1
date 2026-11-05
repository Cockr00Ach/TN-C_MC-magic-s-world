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
    $warriorErr = "$warriorLog.err"
    # javac notes ("deprecated API") go to stderr. With `*>` redirection and
    # $ErrorActionPreference='Stop' that stderr becomes a terminating error, so the
    # script died before checking the exit code. Let Start-Process capture each
    # stream and judge by the process exit code only.
    $warriorArgs = @('runGameTestServer', '-PwarriorTests', '-PbuildingGameTests', '-x', 'downloadAssets', '--offline', '--console=plain')
    $warriorProc = Start-Process -FilePath (Join-Path $warriorRoot 'gradlew.bat') -ArgumentList $warriorArgs -WorkingDirectory $warriorRoot -NoNewWindow -Wait -PassThru -RedirectStandardOutput $warriorLog -RedirectStandardError $warriorErr
    $warriorExit = $warriorProc.ExitCode
    if (Test-Path -LiteralPath $warriorErr) {
        Get-Content -LiteralPath $warriorErr | Add-Content -LiteralPath $warriorLog
        Remove-Item -LiteralPath $warriorErr -Force
    }
    if ($warriorExit -ne 0) { throw "Warrior server tests failed. See $warriorLog" }
    if (-not (Select-String -LiteralPath $warriorLog -Pattern '\d+ GAME TESTS COMPLETE' -Quiet) -or
        -not (Select-String -LiteralPath $warriorLog -Pattern 'All \d+ required tests passed' -Quiet)) {
        throw "Expected the warrior server tests to run; zero discovered tests is not success. See $warriorLog"
    }
    # 至少 22 条（19 条旧 + 3 条长剑连招）；别人继续加测试时不该把脚本改坏
    $warriorPassed = [int](Select-String -LiteralPath $warriorLog -Pattern 'All (\d+) required tests passed').Matches[0].Groups[1].Value
    if ($warriorPassed -lt 22) {
        throw "Expected at least 22 passing server tests, got $warriorPassed. See $warriorLog"
    }
    Write-Output "PASS: $warriorPassed warrior server tests executed. Log: $warriorLog"
} finally {
    $env:JAVA_HOME = $warriorOriginalJava
    $env:GRADLE_USER_HOME = $warriorOriginalGradle
    Pop-Location
}
