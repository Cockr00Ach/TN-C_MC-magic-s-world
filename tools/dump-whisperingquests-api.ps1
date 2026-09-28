#!/usr/bin/env pwsh
# ============================================================================
#  TN-C - dump the WhisperingQuests API so quest gating rules can be re-checked
#
#  Why: "our quest does not show up in the quest book" has twice been caused by
#  rules that only exist inside whisperingquests-3.2.jar (the mod is closed
#  source, no javadoc).  Decompiling by hand every time is a waste, so this
#  script extracts the interesting classes and prints them with javap.
#
#  ASCII only on purpose: this machine's PowerShell 5.1 reads non-BOM .ps1 as
#  ANSI, and a stray CJK byte breaks the whole script (project iron rule 7).
#  The pack folder is auto-detected so no CJK path has to be written here.
#
#  Usage:
#     powershell -NoProfile -ExecutionPolicy Bypass -File tools\dump-whisperingquests-api.ps1
#     powershell -NoProfile -ExecutionPolicy Bypass -File tools\dump-whisperingquests-api.ps1 -LiveRoot "X:\...\.minecraft\versions"
#
#  Output: build\tmp\wq\...            (classes)
#          build\tmp\wq\*.txt          (javap dumps)
# ============================================================================
param(
    [string]$LiveRoot = ''
)
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot

# --- resolve the live versions root ------------------------------------------
# The default lives in a UTF-8 JSON file, NOT in this script: PowerShell 5.1
# would read this file as ANSI and mangle any CJK literal.  See iron rule 7.
$cfgPath = Join-Path $PSScriptRoot 'whisperingquests_paths.json'
$cfg = $null
if (Test-Path $cfgPath) {
    $cfg = [IO.File]::ReadAllText($cfgPath, [Text.Encoding]::UTF8) | ConvertFrom-Json
}
if (-not $LiveRoot) {
    if ($cfg -and $cfg.live_root) { $LiveRoot = $cfg.live_root }
}
if (-not $LiveRoot) {
    Write-Host 'no live root given and none in whisperingquests_paths.json'
    Write-Host 'Pass -LiveRoot "<path to .minecraft\versions>".'
    exit 1
}
$jarGlob = 'whisperingquests-*.jar'
if ($cfg -and $cfg.jar_glob) { $jarGlob = $cfg.jar_glob }

if (-not (Test-Path $LiveRoot)) {
    Write-Host "live versions root not found: $LiveRoot"
    Write-Host 'Pass -LiveRoot "<path to .minecraft\versions>".'
    exit 1
}

$jar = Get-ChildItem -Path $LiveRoot -Directory -ErrorAction SilentlyContinue |
    ForEach-Object { Join-Path $_.FullName 'mods' } |
    Where-Object { Test-Path $_ } |
    ForEach-Object { Get-ChildItem -Path $_ -Filter $jarGlob -ErrorAction SilentlyContinue } |
    Select-Object -First 1 -ExpandProperty FullName

if (-not $jar) {
    Write-Host "no $jarGlob found under $LiveRoot"
    exit 1
}
Write-Host "jar: $jar"

# javap ships with the JDK the project already requires
$javap = Join-Path $root '.jdk17\bin\javap.exe'
if (-not (Test-Path $javap)) { $javap = 'javap' }

$out = Join-Path $root 'build\tmp\wq'
if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Force -Path $out | Out-Null

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($jar)
$wanted = @($zip.Entries | Where-Object {
    $_.FullName -like '*.class' -and
    $_.FullName -match '^com/lirxowo/whisperingquests/(api|data|quest)/'
})
foreach ($e in $wanted) {
    $dest = Join-Path $out $e.FullName
    New-Item -ItemType Directory -Force -Path (Split-Path $dest) | Out-Null
    [IO.Compression.ZipFileExtensions]::ExtractToFile($e, $dest, $true)
}
$zip.Dispose()
Write-Host ("extracted {0} class files -> {1}" -f $wanted.Count, $out)

# --- the three places the gating logic lives --------------------------------
$targets = @(
    @{ cls = 'com.lirxowo.whisperingquests.api.WhisperingQuestsApi'; name = 'api' },
    @{ cls = 'com.lirxowo.whisperingquests.data.QuestDataManager';   name = 'datamanager' },
    @{ cls = 'com.lirxowo.whisperingquests.quest.QuestManager';      name = 'questmanager' }
)
foreach ($t in $targets) {
    $file = Join-Path $out ("{0}.txt" -f $t.name)
    & $javap -p -c -classpath $out $t.cls > $file 2>&1
    Write-Host ("dumped {0} -> {1}" -f $t.cls, $file)
}

Write-Host ''
Write-Host 'How to read the result'
Write-Host '----------------------'
Write-Host 'QuestManager.startQuest -> activateQuest -> canPlayerTake is the gate.'
Write-Host 'canPlayerTake returns false when ANY of these holds:'
Write-Host '  1. player experience level < QuestDefinition.minPlayerLevel'
Write-Host '  2. the quest declares start_triggers AND its id is NOT yet in'
Write-Host '     TeamQuestState.triggeredQuests        <-- the one that bit us'
Write-Host '  3. any entry of requiredCompleted is not completed for this player'
Write-Host '  4. any published prerequisite (another quest listing this id in'
Write-Host '     required_completed) is not completed'
Write-Host '  5. any entry of blockedByActive is currently active'
Write-Host ''
Write-Host 'The quest book UI only lists: active, claimable-reward and refreshed'
Write-Host 'entries - a definition that was never started stays invisible.'
Write-Host ''
Write-Host 'Useful strings to grep in questmanager.txt:'
Write-Host '  canPlayerTake / isTriggerCandidate / activateQuest / isGloballyVisible'
Write-Host ''
Write-Host 'Reminder: nothing here is an API contract we control.  After updating'
Write-Host 'the pack, re-run this and re-read the gate before trusting old notes.'
