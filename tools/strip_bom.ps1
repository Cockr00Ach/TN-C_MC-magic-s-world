# tools/strip_bom.ps1
#
# Removes a UTF-8 BOM (EF BB BF) from every .json under a folder.
#
# Why: Minecraft/Forge read most resources with a BOM-tolerant reader, but SpellEngine parses
# spell JSON with plain Gson, which chokes on the BOM:
#     IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1 path $
# => the spell never registers, and the magic-stone UI reports it as "not implemented"
#    (tnc:night_hand & friends, 2026-10-04).
#
# Byte surgery only -- files are rewritten as the exact same bytes minus the 3-byte prefix,
# so no re-encoding can corrupt the text (this repo has lost a source file to that once).
param(
    [string]$Path = 'src/main/resources'
)

$ErrorActionPreference = 'Stop'
$root = Join-Path (Split-Path -Parent $PSScriptRoot) $Path
$fixed = 0
$scanned = 0
foreach ($file in Get-ChildItem $root -Recurse -File -Filter *.json) {
    $scanned++
    $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        $clean = New-Object byte[] ($bytes.Length - 3)
        [Array]::Copy($bytes, 3, $clean, 0, $clean.Length)
        [System.IO.File]::WriteAllBytes($file.FullName, $clean)
        $fixed++
        Write-Host ("BOM removed: " + ($file.FullName -replace [regex]::Escape($root + '\'), ''))
    }
}
Write-Host ("scanned {0} json files, stripped {1} BOM(s)" -f $scanned, $fixed)
