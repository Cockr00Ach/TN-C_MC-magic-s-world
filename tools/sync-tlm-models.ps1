# sync-tlm-models.ps1 -- give TN-C characters their own Touhou Little Maid (TLM)
# model ids, by copying an existing model out of a TLM model pack.
#
# WHY A SCRIPT: the maid model packs live in the game instance under
# tlm_custom_pack\ and are NOT in git (third-party content, ~37 MB). The only
# reproducible record is tools\maid_models.json, so after a reinstall / a new
# instance you re-run this and the models come back.
#
# What it does, per entry in the recipe:
#   1. copy <source pack>\assets\<ns>\models\entity\<source>.json  -> tnc_pet\...\<as>.json
#   2. copy <source pack>\assets\<ns>\textures\entity\<source>.png -> tnc_pet\...\<as>.png
#      (TLM resolves the texture by the MODEL FILE NAME, so the names must match)
#   3. copy the source entry out of that pack's maid_model.json, repoint its
#      model_id/name/description at our target, and insert it into
#      tnc_pet\assets\tnc_pet\maid_model.json
#
# Every optional field the source used (is_gecko / animation / render_entity_scale)
# is preserved verbatim, so the copy renders exactly like the original.
#
# ASCII ONLY on purpose: Windows PowerShell 5.1 reads BOM-less .ps1 as ANSI and
# would turn CJK literals into mojibake (and break the parse outright). The
# Chinese names live in tools\maid_models.json, read with explicit UTF-8.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File tools\sync-tlm-models.ps1
#   powershell -ExecutionPolicy Bypass -File tools\sync-tlm-models.ps1 -Check
#   powershell -ExecutionPolicy Bypass -File tools\sync-tlm-models.ps1 -Force

param(
    [string]$Recipe = '',
    [switch]$Check,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_common.ps1')

if ([string]::IsNullOrEmpty($Recipe)) { $Recipe = Join-Path $PSScriptRoot 'maid_models.json' }
if (-not (Test-Path -LiteralPath $Recipe)) { Write-Host "ERROR: recipe not found: $Recipe"; exit 1 }

# ---------------------------------------------------------------- json helpers
function Esc([string]$s) {
    $sb = New-Object System.Text.StringBuilder
    foreach ($c in $s.ToCharArray()) {
        $str = [string]$c
        if ($str -eq '"') { $null = $sb.Append('\"') }
        elseif ($str -eq '\') { $null = $sb.Append('\\') }
        elseif ($str -eq "`n") { $null = $sb.Append('\n') }
        elseif ($str -eq "`r") { $null = $sb.Append('\r') }
        elseif ($str -eq "`t") { $null = $sb.Append('\t') }
        else { $null = $sb.Append($str) }
    }
    return $sb.ToString()
}

Add-Type -AssemblyName System.Web.Extensions
$script:serializer = New-Object System.Web.Script.Serialization.JavaScriptSerializer

function Read-Json([string]$path) {
    return $script:serializer.DeserializeObject([System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8))
}

function Write-Utf8([string]$path, [string]$text) {
    [System.IO.File]::WriteAllText($path, $text, (New-Object System.Text.UTF8Encoding($false)))
}

<#
  Return the text of the JSON object that contains the given "model_id" value.
  String-aware, because descriptions embed lang keys that contain braces.
#>
function Get-JsonEntry([string]$text, [string]$modelId) {
    $key = '"model_id": "' + $modelId + '"'
    $i = $text.IndexOf($key)
    if ($i -lt 0) { return $null }
    $start = $text.LastIndexOf('{', $i)
    if ($start -lt 0) { return $null }
    $depth = 0; $inStr = $false; $esc = $false
    for ($j = $start; $j -lt $text.Length; $j++) {
        $ch = $text[$j]
        if ($inStr) {
            if ($esc) { $esc = $false }
            elseif ($ch -eq '\') { $esc = $true }
            elseif ($ch -eq '"') { $inStr = $false }
            continue
        }
        if ($ch -eq '"') { $inStr = $true; continue }
        if ($ch -eq '{') { $depth++ }
        elseif ($ch -eq '}') { $depth--; if ($depth -eq 0) { return $text.Substring($start, $j - $start + 1) } }
    }
    return $null
}

function Reindent([string]$entry, [string]$braceIndent, [string]$fieldIndent) {
    $lines = @($entry -split "`r?`n")
    $out = New-Object System.Collections.Generic.List[string]
    foreach ($l in $lines) {
        $t = $l.Trim()
        if ($t -eq '{' -or $t -eq '}' -or $t.StartsWith('}')) { $out.Add($braceIndent + $t) }
        else { $out.Add($fieldIndent + $t) }
    }
    return ($out -join "`r`n")
}

# ------------------------------------------------------------------ find packs
$WorkPack = Find-TncWorkPack
if (-not $WorkPack) { exit 1 }
$live = Find-TncLivePack -WorkPack $WorkPack
if (-not $live) { Write-Host 'ERROR: live game instance not found'; exit 1 }
Write-Host "  live : $live"

$recipeData = Read-Json $Recipe
$targetNs = [string]$recipeData.target_namespace
if ([string]::IsNullOrEmpty($targetNs)) { $targetNs = 'tnc_pet' }

$tlmRoot = Join-Path $live 'tlm_custom_pack'
if (-not (Test-Path $tlmRoot)) { Write-Host "ERROR: no tlm_custom_pack folder at $tlmRoot"; exit 1 }
$targetRoot = Join-Path $tlmRoot $targetNs
$targetAssets = Join-Path $targetRoot ('assets\' + $targetNs)
$targetManifest = Join-Path $targetAssets 'maid_model.json'
if (-not (Test-Path $targetManifest)) { Write-Host "ERROR: target pack manifest not found: $targetManifest"; exit 1 }

$sourcePacks = @(Get-ChildItem $tlmRoot -Directory | Where-Object { $_.Name -ne $targetNs })
Write-Host ("  source packs: " + (($sourcePacks | ForEach-Object { $_.Name }) -join ', '))

function Find-Source([string]$id) {
    foreach ($p in $sourcePacks) {
        $assetsDir = Join-Path $p.FullName 'assets'
        if (-not (Test-Path $assetsDir)) { continue }
        foreach ($nsDir in Get-ChildItem $assetsDir -Directory) {
            $modelFile = Join-Path $nsDir.FullName ("models\entity\$id.json")
            if (Test-Path $modelFile) {
                return [pscustomobject]@{
                    Model    = $modelFile
                    Texture  = Join-Path $nsDir.FullName ("textures\entity\$id.png")
                    Manifest = Join-Path $nsDir.FullName 'maid_model.json'
                    Namespace = $nsDir.Name
                }
            }
        }
    }
    return $null
}

# ------------------------------------------------------------------- do the work
$manifestText = [System.IO.File]::ReadAllText($targetManifest, [System.Text.Encoding]::UTF8)
$manifestChanged = $false
$problems = 0

foreach ($m in $recipeData.models) {
    $as = [string]$m.as
    $src = [string]$m.source
    Write-Host ""
    Write-Host "  [$as]  <- $src"

    $found = Find-Source $src
    if (-not $found) { Write-Host "    ERROR: source model '$src' not found in any TLM pack"; $problems++; continue }
    Write-Host ("    source: {0} ({1})" -f $found.Namespace, $src)

    $dstModel = Join-Path $targetAssets ("models\entity\$as.json")
    $dstTexture = Join-Path $targetAssets ("textures\entity\$as.png")

    if ($Check) {
        Write-Host "    [check] would write $dstModel"
        Write-Host "    [check] would write $dstTexture"
    }
    else {
        if ((Test-Path $dstModel) -and (-not $Force)) {
            Write-Host "    model file exists, kept (use -Force to overwrite)"
        }
        else {
            Copy-Item $found.Model $dstModel -Force
            Write-Host "    copied model   -> $(Split-Path $dstModel -Leaf)  ($((Get-Item $dstModel).Length) B)"
        }
        if (Test-Path $found.Texture) {
            if ((Test-Path $dstTexture) -and (-not $Force)) {
                Write-Host "    texture exists, kept (use -Force to overwrite)"
            }
            else {
                Copy-Item $found.Texture $dstTexture -Force
                Write-Host "    copied texture -> $(Split-Path $dstTexture -Leaf)  ($((Get-Item $dstTexture).Length) B)"
            }
        }
        else {
            Write-Host "    WARNING: source texture missing: $($found.Texture)"
            $problems++
        }
    }

    # ---- manifest entry -----------------------------------------------------
    $targetId = "$targetNs`:$as"
    $existing = Get-JsonEntry $manifestText $targetId
    if ($existing -and (-not $Force)) {
        Write-Host "    manifest already lists $targetId (use -Force to point it at another model)"
        continue
    }

    $srcEntry = Get-JsonEntry ([System.IO.File]::ReadAllText($found.Manifest, [System.Text.Encoding]::UTF8)) "$($found.Namespace):$src"
    if (-not $srcEntry) {
        Write-Host "    WARNING: source manifest has no entry for $src - building a minimal one"
        $srcEntry = '{' + "`n" + '    "model_id": "' + $found.Namespace + ':' + $src + '",' + "`n" + '    "description": []' + "`n" + '}'
    }

    # repoint the id, then overwrite name/description with ours
    $entry = $srcEntry.Replace('"model_id": "' + $found.Namespace + ':' + $src + '"', '"model_id": "' + $targetId + '"')
    $desc = @($m.description | ForEach-Object { '"' + (Esc ([string]$_)) + '"' }) -join ', '
    $descField = '"description": [' + $desc + ']'
    if ($entry -match '"description"\s*:\s*\[[^\]]*\]') {
        $entry = [regex]::Replace($entry, '"description"\s*:\s*\[[^\]]*\]', $descField, 1)
    }
    else {
        $entry = $entry.TrimEnd()
        $entry = $entry.Substring(0, $entry.Length - 1).TrimEnd().TrimEnd(',') + ',' + "`n" + '    ' + $descField + "`n" + '}'
    }
    $nameField = '"name": "' + (Esc ([string]$m.name)) + '",'
    if ($entry -match '"name"\s*:\s*"') {
        $entry = [regex]::Replace($entry, '"name"\s*:\s*"[^"]*"', ('"name": "' + (Esc ([string]$m.name)) + '"'), 1)
    }
    else {
        $entry = [regex]::Replace($entry, '"model_id"\s*:\s*"[^"]*",', ('$0' + "`n" + '    ' + $nameField), 1)
    }

    # -Force means "this target now points at a different model": drop the old
    # entry first, otherwise the pack would list the same model id twice.
    if ($existing) {
        $idx = $manifestText.IndexOf($existing)
        $before = $manifestText.Substring(0, $idx).TrimEnd()
        $after = $manifestText.Substring($idx + $existing.Length)
        if ($before.EndsWith(',')) { $before = $before.Substring(0, $before.Length - 1) }
        else {
            $after = $after.TrimStart()
            if ($after.StartsWith(',')) { $after = $after.Substring(1) }
        }
        $manifestText = $before + $after
        Write-Host "    replaced the existing $targetId entry ($($existing.Length) chars out)"
    }

    # insert before the closing bracket of model_list (the LAST ']' in the file)
    $close = $manifestText.LastIndexOf(']')
    if ($close -lt 0) { Write-Host "    ERROR: cannot find model_list end in the manifest"; $problems++; continue }
    $before = $manifestText.Substring(0, $close).TrimEnd()
    $after = $manifestText.Substring($close)
    $needsComma = -not $before.EndsWith('[')
    $block = (Reindent $entry '        ' '            ')
    if ($needsComma) { $before += ',' }
    $manifestText = $before + "`r`n" + $block + "`r`n    " + $after
    $manifestChanged = $true
    if ($Check) { Write-Host "    [check] would add manifest entry: $targetId  (name=$($m.name))" }
    else { Write-Host "    manifest entry added: $targetId  (name=$($m.name))" }
}

# --------------------------------------------------------------- write + verify
if ($manifestChanged -and (-not $Check)) {
    Write-Utf8 $targetManifest $manifestText
    Write-Host ""
    Write-Host "  manifest written: $targetManifest"
}

if (-not $Check) {
    try {
        $null = Read-Json $targetManifest
        Write-Host "  manifest is valid JSON"
    }
    catch {
        Write-Host "  ERROR: manifest is NOT valid JSON: $($_.Exception.Message)"
        $problems++
    }
    $ids = @()
    foreach ($m in $recipeData.models) {
        $p = Join-Path $targetAssets ("models\entity\" + $m.as + ".json")
        $t = Join-Path $targetAssets ("textures\entity\" + $m.as + ".png")
        $ok = (Test-Path $p) -and (Test-Path $t)
        if (-not $ok) { $problems++ }
        $ids += ('{0} ({1}) {2}' -f $m.as, $m.source, $(if ($ok) { 'model+texture ok' } else { 'INCOMPLETE' }))
    }
    Write-Host ""
    Write-Host "  tnc_pet models now declared:"
    foreach ($line in $ids) { Write-Host "    $line" }
}

Write-Host ""
if ($problems -gt 0) { Write-Host "  $problems problem(s)"; exit 1 }
Write-Host "  done. In game: right-click a maid -> Change Skin -> pick the name."
exit 0
