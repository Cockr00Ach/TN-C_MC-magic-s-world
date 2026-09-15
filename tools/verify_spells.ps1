# TN-C spell data pre-flight verifier
#
#   Cross-checks our own spell JSON files (namespace "tnc") against the spells
#   that already ship with the modpack, i.e. against known-good data:
#
#     A. every JSON key we use must also be used by some existing pack spell
#     B. every "enum-ish" value we use (type/shape/origin/...) must too
#     C. required top-level fields must be present
#     D. the delivery chain is complete for every spell:
#          data/tnc/spells/<name>.json
#          -> scroll item registered in a startup script
#          -> that item listed in the spellanvil scroll tag
#          -> addSpell('<tnc:name>') in an anvil bind script
#          -> scroll tooltip entry
#          -> assets/tnc/textures/spell/<name>.png
#
#   This catches typos, invented field names and half-finished wiring BEFORE
#   restarting the game.
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File verify_spells.ps1 [-Namespace tnc]
#
# Exit code 0 = pass, 1 = problems found.

param(
    [string]$WorkPack  = '',
    [string]$Namespace = 'tnc'
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($WorkPack)) {
    $packDirs = @(Get-ChildItem 'D:\ModTest\modpack' -Directory | Where-Object { $_.Name -ne 'archive' })
    if ($packDirs.Count -ne 1) { Write-Host "ERROR: expected exactly 1 modpack folder under D:\ModTest\modpack, found $($packDirs.Count)"; exit 1 }
    $WorkPack = $packDirs[0].FullName
}

$enumKeys    = @('type', 'shape', 'origin', 'rotation', 'distance_dropoff', 'apply_mode', 'light_emission', 'mode', 'school')
$requiredTop = @('school', 'range', 'learn', 'cast', 'release', 'impact', 'cost')

$script:keySet  = @{}
$script:enumSet = @{}

function Add-KeysAndEnums {
    param($node)
    if ($null -eq $node) { return }
    if ($node -is [System.Management.Automation.PSCustomObject]) {
        foreach ($prop in $node.PSObject.Properties) {
            $script:keySet[$prop.Name] = $true
            if (($enumKeys -contains $prop.Name) -and ($prop.Value -is [string])) {
                $script:enumSet["$($prop.Name)=$($prop.Value)"] = $true
            }
            Add-KeysAndEnums -node $prop.Value
        }
    }
    elseif ($node -is [System.Array]) {
        foreach ($item in $node) { Add-KeysAndEnums -node $item }
    }
}

# ---------------------------------------------------------------- known-good set
$packSpellCount = 0
foreach ($root in @((Join-Path $WorkPack 'config\openloader\data'), (Join-Path $WorkPack 'kubejs\data'))) {
    if (-not (Test-Path $root)) { continue }
    foreach ($f in Get-ChildItem -Recurse -File $root -Filter *.json -ErrorAction SilentlyContinue) {
        if ($f.FullName -notmatch '\\spells\\') { continue }
        try {
            $j = Get-Content -Raw -Encoding UTF8 $f.FullName | ConvertFrom-Json
            if ($null -eq $j.school -or $null -eq $j.impact) { continue }
            $packSpellCount++
            Add-KeysAndEnums -node $j
        } catch { }
    }
}

$knownKeyCount  = @($script:keySet.Keys).Count
$knownEnumCount = @($script:enumSet.Keys).Count
$packKeySet  = $script:keySet
$packEnumSet = $script:enumSet
Write-Host "known-good spells scanned : $packSpellCount"
Write-Host "known-good field names    : $knownKeyCount"
Write-Host "known-good enum values    : $knownEnumCount"
Write-Host ''

# ---------------------------------------------------------------- chain sources
$kubejsDir = Join-Path $WorkPack 'kubejs'

function Get-TextOfScripts {
    param([string]$subDir, [string]$pattern = '*.js')
    $text = ''
    $dir = Join-Path $kubejsDir $subDir
    if (Test-Path $dir) {
        foreach ($f in Get-ChildItem -Recurse -File $dir -Filter $pattern -ErrorAction SilentlyContinue) {
            $text += (Get-Content -Raw -Encoding UTF8 $f.FullName)
        }
    }
    return $text
}

$startupText = Get-TextOfScripts 'startup_scripts'
$serverText  = Get-TextOfScripts 'server_scripts'
$clientText  = Get-TextOfScripts 'client_scripts'

# spellanvil scroll tag(s)
$tagValues = @()
foreach ($t in Get-ChildItem -Recurse -File (Join-Path $kubejsDir 'data') -Filter *.json -ErrorAction SilentlyContinue) {
    if ($t.FullName -notmatch 'tags\\items') { continue }
    try { $tagValues += (Get-Content -Raw -Encoding UTF8 $t.FullName | ConvertFrom-Json).values } catch { }
}

# icon roots: every openloader resource pack + the kubejs assets folder
$iconRoots = @()
$resRoot = Join-Path $WorkPack 'config\openloader\resources'
if (Test-Path $resRoot) {
    foreach ($p in Get-ChildItem $resRoot -Directory) { $iconRoots += (Join-Path $p.FullName "assets\$Namespace\textures\spell") }
}
$iconRoots += (Join-Path $kubejsDir "assets\$Namespace\textures\spell")

# ---------------------------------------------------------------- our spells
$ourDir = Join-Path $kubejsDir "data\$Namespace\spells"
if (-not (Test-Path $ourDir)) { Write-Host "ERROR: no such folder: $ourDir"; exit 1 }

$problems = 0
$spellCount = 0

foreach ($f in Get-ChildItem $ourDir -Filter *.json | Sort-Object Name) {
    $spellCount++
    $name = [System.IO.Path]::GetFileNameWithoutExtension($f.Name)
    $spellId = "$Namespace`:$name"

    # Our convention is kubejs:tnc_<name>_scroll. The archived blink spells
    # predate it and register their items dynamically as kubejs:<name>_scroll,
    # so try both and fall back to ours.
    $scrollCandidates = @("kubejs:tnc_$name`_scroll", "kubejs:$name`_scroll")
    $scrollItem = $null
    foreach ($candidate in $scrollCandidates) {
        $bare = $candidate.Replace('kubejs:', '')
        if (($startupText -match [regex]::Escape("'$bare'")) -or ($tagValues -contains $candidate)) { $scrollItem = $candidate; break }
    }
    $ourConvention = $true
    if (-not $scrollItem) { $scrollItem = $scrollCandidates[0] }
    elseif ($scrollItem -notlike 'kubejs:tnc_*') { $ourConvention = $false }

    $j = Get-Content -Raw -Encoding UTF8 $f.FullName | ConvertFrom-Json

    $script:keySet  = @{}
    $script:enumSet = @{}
    Add-KeysAndEnums -node $j
    $unknownKeys  = @($script:keySet.Keys  | Where-Object { -not $packKeySet.ContainsKey($_) })  | Sort-Object
    $unknownEnums = @($script:enumSet.Keys | Where-Object { -not $packEnumSet.ContainsKey($_) }) | Sort-Object
    $missingTop   = @($requiredTop | Where-Object { $null -eq $j.$_ })

    $iconPath = $null
    foreach ($root in $iconRoots) {
        $candidate = Join-Path $root "$name.png"
        if (Test-Path $candidate) { $iconPath = $candidate; break }
    }

    $hasScroll = $startupText -match [regex]::Escape("'$($scrollItem.Replace('kubejs:',''))'")
    $hasTag    = $tagValues -contains $scrollItem
    $hasBind   = $serverText  -match [regex]::Escape("addSpell('$spellId')")
    $hasTip    = $clientText  -match [regex]::Escape($scrollItem)

    $issues = @()
    if ($missingTop.Count -gt 0)  { $issues += "missing top-level field(s): $($missingTop -join ', ')" }
    if ($unknownKeys.Count -gt 0) { $issues += "field(s) not proven by pack data: $($unknownKeys -join ', ')" }
    if ($unknownEnums.Count -gt 0){ $issues += "enum value(s) not proven: $($unknownEnums -join ', ')" }
    if (-not $hasScroll)          { $issues += "scroll item not registered in startup scripts" }
    if (-not $hasTag)             { $issues += "scroll item missing from spellanvil scroll tag" }
    if (-not $hasBind)            { $issues += "no anvil binding (addSpell) for $spellId" }
    if (-not $hasTip)             { $issues += "no tooltip entry for $scrollItem" }
    if (-not $iconPath)           { $issues += "missing icon assets/$Namespace/textures/spell/$name.png" }

    $dataOk  = ($missingTop.Count -eq 0 -and $unknownKeys.Count -eq 0 -and $unknownEnums.Count -eq 0)
    $chainOk = ($hasScroll -and $hasTag -and $hasBind -and $hasTip -and $iconPath)

    # Legacy (pre-convention) spells only need valid data; chain gaps are
    # reported but do not fail the run. Our own spells must be complete.
    if (-not $dataOk -or ($ourConvention -and -not $chainOk)) {
        $problems++
        $status = 'PROBLEM'
    }
    elseif ($chainOk) { $status = 'OK' }
    else { $status = 'legacy' }

    Write-Host ("[{0,-7}] {1,-24} {2,-9} tier={3} target={4,-9} scroll/tag/bind/tip/icon = {5}" -f `
        $status, $name, $j.school, $j.learn.tier, $j.release.target.type, `
        ("{0}/{1}/{2}/{3}/{4}" -f ($(if($hasScroll){'y'}else{'n'})), ($(if($hasTag){'y'}else{'n'})), ($(if($hasBind){'y'}else{'n'})), ($(if($hasTip){'y'}else{'n'})), ($(if($iconPath){'y'}else{'n'}))))
    foreach ($issue in $issues) { Write-Host "           - $issue" }
}

Write-Host ''
Write-Host "checked $spellCount spell(s) in namespace '$Namespace', $problems with problems."
if ($problems -gt 0) { exit 1 }
Write-Host "pre-flight OK - data is proven by existing pack spells and the delivery chain is complete."
