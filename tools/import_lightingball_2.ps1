# Import the author-made Blockbench model "lightingball_2" into the mod resources.
#
# Source (author workspace):  magic_modelandtexture\lightingball_2\lightingball_2.json + texture.png
# Target (shipped in jar):    assets\tnc\models\projectile\lightingball_2.json
#                             assets\tnc\textures\spell_projectile\lightingball_2.png
#
# What it changes (text level, so the author's formatting/UV data is preserved verbatim):
#   1. "textures"."0" and "textures"."particle"  ->  tnc:spell_projectile/lightingball_2
#   2. drop the "texture_size" key (the Java block model format does not use it)
#   3. UV sanity check: this project's models use 0-16 UV space.  If the export came out in
#      texture-pixel space (max uv > 16) the script FAILS loudly instead of shipping a model
#      whose faces sample the wrong part of the texture.
#
# Run: powershell -ExecutionPolicy Bypass -File tools\import_lightingball_2.ps1
# NOTE: ASCII only.  PowerShell 5.1 reads BOM-less .ps1 as ANSI, so any Chinese in here
#       would be mojibake and break parsing.

$ErrorActionPreference = 'Stop'

$root     = Split-Path -Parent $PSScriptRoot
$srcJson  = Join-Path $root 'magic_modelandtexture\lightingball_2\lightingball_2.json'
$srcTex   = Join-Path $root 'magic_modelandtexture\lightingball_2\texture.png'
$dstJson  = Join-Path $root 'src\main\resources\assets\tnc\models\projectile\lightingball_2.json'
$dstTex   = Join-Path $root 'src\main\resources\assets\tnc\textures\spell_projectile\lightingball_2.png'
$texRef   = 'tnc:spell_projectile/lightingball_2'

foreach ($p in @($srcJson, $srcTex)) {
    if (-not (Test-Path -LiteralPath $p)) { throw "missing source file: $p" }
}

# ---- 1. read the author's JSON as raw text (UTF8, no BOM) -------------------
$text = [IO.File]::ReadAllText($srcJson, [Text.Encoding]::UTF8)

# ---- 2. rewrite the two texture slots --------------------------------------
$before = $text
$text = $text -replace '"0"\s*:\s*"texture"',         ('"0": "' + $texRef + '"')
$text = $text -replace '"particle"\s*:\s*"texture"',  ('"particle": "' + $texRef + '"')
if ($text -eq $before) { throw 'texture slots were not found - did the export format change?' }

# ---- 3. drop texture_size --------------------------------------------------
$text = $text -replace '(?m)^[ \t]*"texture_size"\s*:\s*\[[^\]]*\][ \t]*,?[ \t]*\r?\n', ''

# ---- 4. UV sanity check ----------------------------------------------------
$model = $text | ConvertFrom-Json
$minUv = 9999.0; $maxUv = -9999.0
$minX = 9999; $minY = 9999; $minZ = 9999; $maxX = -9999; $maxY = -9999; $maxZ = -9999
$rotated = 0
foreach ($el in $model.elements) {
    if ($el.rotation -and ($el.rotation.angle -ne 0)) { $rotated++ }
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

Write-Host ("elements        : " + $model.elements.Count)
Write-Host ("rotated elements: " + $rotated)
Write-Host ("bbox (units)    : x " + $minX + ".." + $maxX + "  y " + $minY + ".." + $maxY + "  z " + $minZ + ".." + $maxZ)
Write-Host ("bbox (blocks)   : " + (($maxX - $minX) / 16.0) + " x " + (($maxY - $minY) / 16.0) + " x " + (($maxZ - $minZ) / 16.0))
Write-Host ("uv range        : " + $minUv + " .. " + $maxUv)
Write-Host ("center (blocks) : " + ((($minX + $maxX) / 2.0) / 16.0) + ", " + ((($minY + $maxY) / 2.0) / 16.0) + ", " + ((($minZ + $maxZ) / 2.0) / 16.0))

if ($maxUv -gt 16.0) {
    throw ("uv max " + $maxUv + " > 16 -> this export uses texture-pixel space; divide every uv by (texture_size/16) before shipping")
}
if ($rotated -gt 0) {
    Write-Host ("WARNING: " + $rotated + " element(s) carry a non-zero rotation - the code-side fallback model cannot follow it")
}

# ---- 5. write ---------------------------------------------------------------
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllText($dstJson, $text, $utf8NoBom)
Copy-Item -LiteralPath $srcTex -Destination $dstTex -Force

Write-Host ("wrote " + $dstJson + " (" + (Get-Item -LiteralPath $dstJson).Length + " bytes)")
Write-Host ("wrote " + $dstTex  + " (" + (Get-Item -LiteralPath $dstTex).Length  + " bytes)")
