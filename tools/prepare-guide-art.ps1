param([string]$GeneratedRoot='C:\Users\宋志坤\.codex\generated_images\01a0e9a7-09c2-7b12-b9af-d76132668563')
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$taskRoot=Split-Path $PSScriptRoot -Parent
$out=Join-Path $taskRoot 'src/main/resources/assets/tnc/textures/guide'
$sourceNames=@('exec-ee84e189-ecb1-45d5-bce1-db02a504eb70.png','exec-627d0ee5-b28f-4cb1-877f-f4473d2a11e0.png','exec-16e66da2-d591-4554-b76b-3147cbc4c325.png','exec-1b650e24-1db9-4a78-9f67-94de6c833255.png','exec-9d9c43a3-2d9b-403f-9fe7-1c07bef21ecc.png')
$targets=@('water_focus_1.png','water_staff_2.png','water_staff_3.png','water_staff_4.png','water_staff_5.png')
$preview=[System.Drawing.Bitmap]::new(640,128)
$pg=[System.Drawing.Graphics]::FromImage($preview)
$pg.Clear([System.Drawing.Color]::FromArgb(255,24,44,66))
$pg.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$pg.PixelOffsetMode=[System.Drawing.Drawing2D.PixelOffsetMode]::Half
for($i=0;$i -lt 5;$i++) {
    $src=[System.Drawing.Bitmap]::new((Join-Path $GeneratedRoot $sourceNames[$i]))
    $minX=$src.Width;$minY=$src.Height;$maxX=0;$maxY=0
    for($y=0;$y -lt $src.Height;$y++){for($x=0;$x -lt $src.Width;$x++){if($src.GetPixel($x,$y).A -gt 32){$minX=[Math]::Min($minX,$x);$minY=[Math]::Min($minY,$y);$maxX=[Math]::Max($maxX,$x);$maxY=[Math]::Max($maxY,$y)}}}
    $sw=$maxX-$minX+1;$sh=$maxY-$minY+1;$scale=28.0/[Math]::Max($sw,$sh)
    $dw=[Math]::Max(1,[Math]::Round($sw*$scale));$dh=[Math]::Max(1,[Math]::Round($sh*$scale))
    $dst=[System.Drawing.Bitmap]::new(32,32,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g=[System.Drawing.Graphics]::FromImage($dst);$g.Clear([System.Drawing.Color]::Transparent)
    $g.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor;$g.PixelOffsetMode=[System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $g.DrawImage($src,[System.Drawing.Rectangle]::new([int]((32-$dw)/2),[int]((32-$dh)/2),$dw,$dh),$minX,$minY,$sw,$sh,[System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose();$dst.Save((Join-Path $out $targets[$i]),[System.Drawing.Imaging.ImageFormat]::Png)
    $pg.DrawImage($dst,[System.Drawing.Rectangle]::new($i*128,0,128,128),0,0,32,32,[System.Drawing.GraphicsUnit]::Pixel)
    $dst.Dispose();$src.Dispose()
}
$pg.Dispose();$preview.Save((Join-Path $taskRoot 'work/water-icons-preview.png'),[System.Drawing.Imaging.ImageFormat]::Png);$preview.Dispose()
Write-Output 'Five transparent 32px sprites prepared; source images preserved.'
