# Scale the alpha channel of one or more PNGs (make a texture more / less transparent).
#
#   Author 2026-09-30: "god 的模型还有球的模型透明度都太低了" -> fade the dark god and dark
#   ball textures so the models look ghostly. The arrow is reversible: re-running
#   tools/gen_dark_projectile_models.ps1 copies the ORIGINAL pngs back from
#   magic_modelandtexture/, so nothing is lost.
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools/fade_textures.ps1 -Alpha 0.55 -Files a.png,b.png
#   (Alpha is a multiplier on the existing alpha: 1.0 = unchanged, 0.55 = 55% as opaque)
#
# ASCII only on purpose (see docs: tools/*.ps1 must stay pure ASCII).

param(
    [double]$Alpha = 0.55,
    [string[]]$Files = @()
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

# `powershell -File` hands over one string per argument, so "-Files a,b,c" arrives as a
# single comma-joined element. Split again so both forms work.
$expanded = @()
foreach ($f in $Files) { $expanded += ($f -split ',') }
$Files = @($expanded | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' })

if ($Files.Count -eq 0) {
    Write-Host 'nothing to do: pass -Files <path>[,<path>...]'
    exit 1
}
if ($Alpha -lt 0.0 -or $Alpha -gt 1.0) {
    Write-Host "alpha must be between 0.0 and 1.0 (got $Alpha)"
    exit 1
}

foreach ($file in $Files) {
    if (-not (Test-Path $file)) {
        Write-Host "MISSING: $file"
        exit 1
    }
    $bytes = [System.IO.File]::ReadAllBytes((Resolve-Path $file))
    $stream = New-Object System.IO.MemoryStream(, $bytes)
    $bmp = New-Object System.Drawing.Bitmap($stream)
    $rect = New-Object System.Drawing.Rectangle(0, 0, $bmp.Width, $bmp.Height)
    $data = $bmp.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadWrite,
            [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        $count = [Math]::Abs($data.Stride) * $bmp.Height
        $pixels = New-Object byte[] $count
        [System.Runtime.InteropServices.Marshal]::Copy($data.Scan0, $pixels, 0, $count)
        $changed = 0
        for ($i = 3; $i -lt $count; $i += 4) {          # BGRA -> alpha is every 4th byte
            $a = $pixels[$i]
            if ($a -eq 0) { continue }                  # keep fully transparent pixels as-is
            $new = [int][Math]::Round($a * $Alpha)
            if ($new -lt 1) { $new = 1 }                # never fade to "invisible" by accident
            if ($new -ne $a) { $changed++ }
            $pixels[$i] = [byte]$new
        }
        [System.Runtime.InteropServices.Marshal]::Copy($pixels, 0, $data.Scan0, $count)
    } finally {
        $bmp.UnlockBits($data)
    }
    $bmp.Save((Resolve-Path $file), [System.Drawing.Imaging.ImageFormat]::Png)
    $w = $bmp.Width; $h = $bmp.Height
    $bmp.Dispose(); $stream.Dispose()
    Write-Host ("{0,-46} {1}x{2}  alpha x{3}  ({4} px changed)" -f (Split-Path $file -Leaf), $w, $h, $Alpha, $changed)
}

Write-Host "done: $($Files.Count) file(s)"
exit 0
