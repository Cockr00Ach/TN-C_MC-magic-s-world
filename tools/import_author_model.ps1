# Import an author-made Blockbench model into the mod resources.
#
#   powershell -ExecutionPolicy Bypass -File tools\import_author_model.ps1 `
#       -Json magic_modelandtexture\flash\flashinggg.json -Name flash
#
# Source (author workspace):  <json>  +  <same dir>\texture.png (override with -Texture)
# Target (shipped in the jar): assets\<ns>\models\projectile\<Name>.json
#                              assets\<ns>\textures\spell_projectile\<Name>.png
#
# What it does (text level, so the author's formatting / per-face UV data survives verbatim):
#   1. every texture slot whose value is a bare name (e.g. "texture") -> <ns>:spell_projectile/<Name>
#   2. drop the "texture_size" key (the Java block model format does not use it)
#   3. UV sanity check: this project's models use 0-16 UV space.  If the export is in
#      texture-pixel space (max uv > 16) the script FAILS loudly instead of shipping a model
#      whose faces sample the wrong part of the texture.
#   4. prints element count / bbox (blocks) / bbox center (blocks) / uv range -
#      the center and the box size are what the Java side needs (scale + centering).
#
# After importing, remember to add "projectile/<Name>" to TNProjectileModels.PROJECTILE_MODELS.
#
# NOTE: ASCII only.  PowerShell 5.1 reads BOM-less .ps1 as ANSI, so any Chinese in here
#       would be mojibake and break parsing.

param(
    [Parameter(Mandatory = $true)][string]$Json,
    [Parameter(Mandatory = $true)][string]$Name,
    [string]$Texture = '',
    [string]$Namespace = 'tnc',
    [string]$TextureFolder = 'spell_projectile'
)

$ErrorActionPreference = 'Stop'

$root    = Split-Path -Parent $PSScriptRoot
if (-not [IO.Path]::IsPathRooted($Json)) { $Json = Join-Path $root $Json }
if (-not (Test-Path -LiteralPath $Json)) { throw "missing source json: $Json" }
if ($Texture -eq '') {
    $Texture = Join-Path (Split-Path -Parent $Json) 'texture.png'
} elseif (-not [IO.Path]::IsPathRooted($Texture)) {
    $Texture = Join-Path $root $Texture
}
if (-not (Test-Path -LiteralPath $Texture)) { throw "missing source texture: $Texture" }

$dstJson = Join-Path $root ("src\main\resources\assets\{0}\models\projectile\{1}.json" -f $Namespace, $Name)
$dstTex  = Join-Path $root ("src\main\resources\assets\{0}\textures\{1}\{2}.png" -f $Namespace, $TextureFolder, $Name)
$texRef  = "$Namespace`:$TextureFolder/$Name"

# ---- 1. read the author's JSON as raw text (UTF8, no BOM) -------------------
$text   = [IO.File]::ReadAllText($Json, [Text.Encoding]::UTF8)
$before = $text

# every bare-name texture slot INSIDE the "textures" block -> our resource location.
# (Only the textures block must be touched: a document-wide regex would also rewrite
#  "axis": "y" inside element rotations and silently corrupt the model.)
$texKey = $text.IndexOf('"textures"')
if ($texKey -lt 0) { throw 'no "textures" block found' }
$texOpen = $text.IndexOf('{', $texKey)
$texClose = $text.IndexOf('}', $texOpen)
if ($texOpen -lt 0 -or $texClose -lt 0) { throw 'malformed "textures" block' }
$head = $text.Substring(0, $texOpen + 1)
$body = $text.Substring($texOpen + 1, $texClose - $texOpen - 1)
$tail = $text.Substring($texClose)

$bodyNew = [regex]::Replace($body, '("(?:[A-Za-z0-9_]+)"\s*:\s*")([^":]+)(")', {
    param($m)
    $value = $m.Groups[2].Value
    if ($value -match '^[A-Za-z0-9_./-]+$' -and $value -notmatch ':') {
        return $m.Groups[1].Value + $texRef + $m.Groups[3].Value
    }
    return $m.Value
})
if ($bodyNew -eq $body) { throw 'no bare-name texture slot found - did the export format change?' }
$text = $head + $bodyNew + $tail

# ---- 2. drop texture_size ---------------------------------------------------
$text = $text -replace '(?m)^[ \t]*"texture_size"\s*:\s*\[[^\]]*\][ \t]*,?[ \t]*\r?\n', ''

# ---- 3. stats + UV sanity check --------------------------------------------
$model = $text | ConvertFrom-Json
$minUv = 9999.0; $maxUv = -9999.0
$minX = 9999; $minY = 9999; $minZ = 9999; $maxX = -9999; $maxY = -9999; $maxZ = -9999
$rotated = 0
$angles = @{}
foreach ($el in $model.elements) {
    if ($el.rotation -and ($el.rotation.angle -ne 0)) {
        $rotated++
        $key = "$($el.rotation.axis) $($el.rotation.angle)"
        $angles[$key] = 1 + $(if ($angles.ContainsKey($key)) { $angles[$key] } else { 0 })
    }
    if ($el.from[0] -lt $minX) { $minX = $el.from[0] }
    if ($el.from[1] -lt $minY) { $minY = $el.from[1] }
    if ($el.from[2] -lt $minZ) { $minZ = $el.from[2] }
    if ($el.to[0]   -gt $maxX) { $maxX = $el.to[0] }
    if ($el.to[1]   -gt $maxY) { $maxY = $el.to[1] }
    if ($el.to[2]   -gt $maxZ) { $maxZ = $el.to[2] }
    foreach ($face in $el.faces.PSObject.Properties) {
        $uv = $face.Value.uv
        if ($null -eq $uv) { continue }
        foreach ($v in $uv) {
            if ($v -lt $minUv) { $minUv = $v }
            if ($v -gt $maxUv) { $maxUv = $v }
        }
    }
}

Write-Host ("model           : " + $Name + "  (" + $model.elements.Count + " elements, " + $rotated + " rotated)")
Write-Host ("bbox (units)    : x " + $minX + ".." + $maxX + "  y " + $minY + ".." + $maxY + "  z " + $minZ + ".." + $maxZ)
Write-Host ("bbox (blocks)   : " + (($maxX - $minX) / 16.0) + " x " + (($maxY - $minY) / 16.0) + " x " + (($maxZ - $minZ) / 16.0))
Write-Host ("center (blocks) : " + ((($minX + $maxX) / 2.0) / 16.0) + ", " + ((($minY + $maxY) / 2.0) / 16.0) + ", " + ((($minZ + $maxZ) / 2.0) / 16.0))
Write-Host ("uv range        : " + $minUv + " .. " + $maxUv)

if ($maxUv -gt 16.0) {
    throw ("uv max " + $maxUv + " > 16 -> this export uses texture-pixel space; divide every uv by (texture_size/16) before shipping")
}
if ($rotated -gt 0) {
    # vanilla block models only accept axis-aligned rotations that are multiples of 22.5
    # and |angle| <= 45; anything else makes the whole model fail to load.
    $bad = @()
    foreach ($key in $angles.Keys) {
        $angle = [double]($key -split ' ')[1]
        $ok = ([Math]::Abs($angle) -le 45.0) -and (([Math]::Abs($angle) % 22.5) -lt 0.001)
        if (-not $ok) { $bad += $key }
        Write-Host ("rotation        : " + $key + " x" + $angles[$key] + $(if ($ok) { "" } else { "   <-- ILLEGAL" }))
    }
    if ($bad.Count -gt 0) {
        throw ("illegal element rotation(s): " + ($bad -join ', ') + " - vanilla only accepts multiples of 22.5 within +-45")
    }
}

# ---- 4. write ---------------------------------------------------------------
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllText($dstJson, $text, $utf8NoBom)
Copy-Item -LiteralPath $Texture -Destination $dstTex -Force

Write-Host ("wrote " + $dstJson + " (" + (Get-Item -LiteralPath $dstJson).Length + " bytes)")
Write-Host ("wrote " + $dstTex  + " (" + (Get-Item -LiteralPath $dstTex).Length  + " bytes)")
