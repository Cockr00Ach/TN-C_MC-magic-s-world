# 带日志文件的 Gradle 包装器。
# 目的：构建输出写文件而不是走管道缓冲，便于随时 tail 判断进度/卡死。
# 用法：
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\build-with-log.ps1
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\build-with-log.ps1 -Task test
# 日志：build\build-<时间戳>.log
param(
    [string]$Task = "build"
)

$ErrorActionPreference = "Continue"
$repo = Split-Path -Parent $PSScriptRoot
$buildDir = Join-Path $repo "build"
New-Item -ItemType Directory -Force -Path $buildDir | Out-Null

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$log = Join-Path $buildDir "build-$stamp.log"

$env:GRADLE_USER_HOME = Join-Path $repo ".gradle-home"
if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = "D:\software\PyCharm 2024.1.4\jbr"
}

Write-Host "gradle $Task -> $log"
Write-Host ("[60-90s 后若 CPU 不动、daemon 日志停在 'started executing the build'，按卡死处理]")

& (Join-Path $repo "gradlew.bat") $Task --offline --console=plain --no-daemon *> $log
$code = $LASTEXITCODE

Get-Content $log -Tail 25
Write-Host "log: $log"
exit $code
