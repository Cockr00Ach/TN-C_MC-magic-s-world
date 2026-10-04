param(
    [string]$LiveRoot = 'D:\垃圾桶\PCL 正式版 2.9.3\.minecraft\versions\元素觉醒1.4.3-魔改版-20260915'
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $repoRoot
$mirrorRoot = Join-Path $repoRoot 'modpack/元素觉醒1.4.3-魔改版-20260915'
$jarSource = Join-Path $repoRoot 'build/libs/tnc-1.0.0.jar'
$testLog = Get-Content -LiteralPath 'work/living-ecology-gametest-final.log' -Raw
if ($testLog -notmatch 'All \d+ required tests passed' -or $testLog -match 'failed!') { throw 'Final GameTests have not all passed; refusing installation.' }
$audit = Get-Content -LiteralPath 'work/living-ecology-client-audit.json' -Raw | ConvertFrom-Json
if (@($audit.failures).Count -gt 0 -or $audit.registered_animal_renderers -ne 23) { throw 'Client asset audit has not passed.' }
if (-not (Test-Path -LiteralPath $jarSource)) { throw 'Missing production build.' }
$gameProcesses = @(Get-CimInstance Win32_Process | Where-Object { $_.Name -in @('java.exe','javaw.exe') -and $_.CommandLine -match '元素觉醒1\.4\.3-魔改版-20260915' })
if ($gameProcesses.Count -gt 0) { throw 'Live Minecraft instance is running; refusing to replace its files.' }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backup = Join-Path $repoRoot ('work/backups/living-ecology-' + $stamp)
$staging = Join-Path $repoRoot 'work/living-ecology-guide-staging'
New-Item -ItemType Directory -Path $backup,$staging -Force | Out-Null
$chapterNames = @('tnc_field_01_expeditions.snbt','tnc_field_02_botany.snbt','tnc_field_03_pasture.snbt','tnc_field_04_workshop.snbt')
$liveChapters = Join-Path $LiveRoot 'config/ftbquests/quests/chapters'
$protectedBefore = @{}
Get-ChildItem -LiteralPath $liveChapters -Filter '*.snbt' | Where-Object { $_.Name -notin $chapterNames } | ForEach-Object { $protectedBefore[$_.Name] = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash }
& python (Join-Path $PSScriptRoot 'merge-field-redesign-guide.py') (Join-Path $liveChapters $chapterNames[0]) (Join-Path $staging $chapterNames[0])
if ($LASTEXITCODE -ne 0) { throw 'Existing field chapter could not be merged safely.' }
foreach ($name in $chapterNames | Select-Object -Skip 1) {
    if (Test-Path -LiteralPath (Join-Path $liveChapters $name)) { throw ('New chapter already exists and requires an identity-preserving merge: ' + $name) }
    Copy-Item -LiteralPath (Join-Path $repoRoot ('questbook/ftbquests/chapters/' + $name)) -Destination (Join-Path $staging $name)
}
# Only these four owned chapters and the TN-C jar are installed.
$installed = @()
foreach ($target in @(@{Name='live';Root=$LiveRoot},@{Name='mirror';Root=$mirrorRoot})) {
    $targetBackup = Join-Path $backup $target.Name
    New-Item -ItemType Directory -Path $targetBackup -Force | Out-Null
    $jarTarget = Join-Path $target.Root 'mods/tnc-1.0.0.jar'
    Copy-Item -LiteralPath $jarTarget -Destination (Join-Path $targetBackup 'tnc-1.0.0.jar')
    $chapterRoot = Join-Path $target.Root 'config/ftbquests/quests/chapters'
    New-Item -ItemType Directory -Path $chapterRoot -Force | Out-Null
    foreach ($name in $chapterNames) {
        $path = Join-Path $chapterRoot $name
        if (Test-Path -LiteralPath $path) { Copy-Item -LiteralPath $path -Destination (Join-Path $targetBackup $name) }
    }
    Copy-Item -LiteralPath $jarSource -Destination $jarTarget -Force
    foreach ($name in $chapterNames) { Copy-Item -LiteralPath (Join-Path $staging $name) -Destination (Join-Path $chapterRoot $name) -Force }
    $installed += [pscustomobject]@{target=$target.Name;jar=$jarTarget;sha256=(Get-FileHash -LiteralPath $jarTarget -Algorithm SHA256).Hash}
}
$builtHash = (Get-FileHash -LiteralPath $jarSource -Algorithm SHA256).Hash
foreach ($target in $installed) { if ($target.sha256 -ne $builtHash) { throw ('Installed jar differs from build: ' + $target.target) } }
foreach ($name in $protectedBefore.Keys) { if ((Get-FileHash -LiteralPath (Join-Path $liveChapters $name) -Algorithm SHA256).Hash -ne $protectedBefore[$name]) { throw ('Unrelated chapter changed: ' + $name) } }
$receipt = [ordered]@{installed_at=(Get-Date).ToString('o');live_pack=$LiveRoot;backup=$backup;sha256=$builtHash;plants=29;animals=24;new_animal_types=23;chapters=$chapterNames;unchanged_chapters=$protectedBefore.Count;unchanged_chapter_hashes=$protectedBefore;installed=$installed;gameplay_visual_review='Pending actual full modpack play; client resource audit passed';save_files_changed=$false;island_templates_changed=$false}
$receipt | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $repoRoot 'work/living-ecology-install-receipt.json') -Encoding utf8
$receipt | ConvertTo-Json -Depth 3
