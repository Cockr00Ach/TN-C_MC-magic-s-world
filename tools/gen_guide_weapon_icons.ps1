# Original code-native pixel art; extends the existing spell icon production pipeline.
# Names and descriptions live in guide/gameplay.json, not in this ASCII script.
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$taskRoot=Split-Path -Parent $PSScriptRoot
$assetDir=Join-Path $taskRoot 'src/main/resources/assets/tnc/textures/guide'
New-Item -ItemType Directory -Path $assetDir -Force | Out-Null
$previewDir=Join-Path $taskRoot 'docs/previews'
New-Item -ItemType Directory -Path $previewDir -Force | Out-Null
function Color([string]$hex){[Drawing.ColorTranslator]::FromHtml($hex)}
function Point([int]$x,[int]$y){[Drawing.Point]::new($x,$y)}
$names=@('water_focus_1','water_staff_2','water_staff_3','water_staff_4','water_staff_5')
$preview=[Drawing.Bitmap]::new(660,170)
$previewGraphics=[Drawing.Graphics]::FromImage($preview)
$previewGraphics.Clear((Color '#101C2A'))
$previewGraphics.InterpolationMode=[Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$previewGraphics.PixelOffsetMode=[Drawing.Drawing2D.PixelOffsetMode]::Half
$font=[Drawing.Font]::new('Consolas',11)
$label=[Drawing.SolidBrush]::new((Color '#EAE3D6'))
try {
    for($tier=1;$tier -le 5;$tier++) {
        $bitmap=[Drawing.Bitmap]::new(32,32)
        $graphics=[Drawing.Graphics]::FromImage($bitmap)
        $graphics.SmoothingMode=[Drawing.Drawing2D.SmoothingMode]::None
        $graphics.Clear([Drawing.Color]::Transparent)
        $outline=[Drawing.Pen]::new((Color '#132547'),3)
        $body=[Drawing.Pen]::new((Color '#436BB1'),2)
        $light=[Drawing.Pen]::new((Color '#ACEEFF'),1)
        $ring=[Drawing.Pen]::new((Color '#416AAB'),1)
        $cyan=[Drawing.SolidBrush]::new((Color '#35C5ED'))
        $dark=[Drawing.SolidBrush]::new((Color '#284B83'))
        $white=[Drawing.SolidBrush]::new((Color '#DEFFFF'))
        try {
            if($tier -eq 1) {
                $graphics.DrawEllipse($outline,3,3,25,25)
                $graphics.DrawEllipse($ring,3,3,25,25)
                $graphics.DrawEllipse($light,7,7,17,17)
                foreach($pt in @(@(16,1),@(30,16),@(16,30),@(1,16))){$graphics.FillRectangle($white,[int]$pt[0]-1,[int]$pt[1]-1,2,2)}
                $drop=[Drawing.Point[]]@((Point 16 9),(Point 11 17),(Point 12 21),(Point 16 23),(Point 20 21),(Point 21 17))
                $graphics.FillPolygon($dark,$drop);$graphics.FillPolygon($cyan,[Drawing.Point[]]@((Point 16 11),(Point 13 18),(Point 16 21),(Point 19 18)))
                $graphics.DrawLine($light,16,14,14,18)
            } else {
                $graphics.DrawLine($outline,7,29,20,10);$graphics.DrawLine($body,7,29,20,10)
                $graphics.DrawLine($light,8,27,18,12)
                $graphics.DrawLine($ring,7,28,10,28)
                if($tier -eq 2) {
                    $graphics.DrawLine($outline,16,13,13,7);$graphics.DrawLine($body,16,13,13,7)
                    $graphics.DrawLine($outline,22,12,27,6);$graphics.DrawLine($body,22,12,27,6)
                    $graphics.FillPolygon($cyan,[Drawing.Point[]]@((Point 21 2),(Point 17 7),(Point 20 12),(Point 24 7)))
                    $graphics.DrawLine($light,21,4,19,7)
                } elseif($tier -eq 3) {
                    $graphics.DrawLine($body,14,13,10,6);$graphics.DrawLine($body,10,6,12,2)
                    $graphics.DrawLine($body,21,13,28,6);$graphics.DrawLine($body,28,6,27,2)
                    $graphics.DrawLine($light,20,10,20,1)
                    $graphics.FillPolygon($cyan,[Drawing.Point[]]@((Point 20 2),(Point 16 8),(Point 20 14),(Point 24 8)))
                    $graphics.FillRectangle($white,10,4,2,3);$graphics.FillRectangle($white,27,4,2,3)
                    $graphics.DrawLine($light,20,4,18,8)
                } elseif($tier -eq 4) {
                    $graphics.DrawEllipse($outline,5,6,24,9);$graphics.DrawEllipse($ring,5,6,24,9)
                    $graphics.DrawEllipse($body,12,1,13,21)
                    $graphics.FillPolygon($dark,[Drawing.Point[]]@((Point 19 1),(Point 13 9),(Point 18 18),(Point 25 9)))
                    $graphics.FillPolygon($cyan,[Drawing.Point[]]@((Point 19 3),(Point 16 10),(Point 19 15),(Point 22 9)))
                    $graphics.DrawLine($light,19,5,17,10);$graphics.DrawArc($light,5,6,24,9,10,125)
                    $graphics.FillRectangle($white,7,9,2,2);$graphics.FillRectangle($white,26,9,2,2)
                } else {
                    $graphics.FillPolygon($dark,[Drawing.Point[]]@((Point 7 9),(Point 10 3),(Point 17 5),(Point 21 3),(Point 27 8),(Point 24 14),(Point 18 16),(Point 12 13)))
                    $graphics.FillPolygon($cyan,[Drawing.Point[]]@((Point 10 9),(Point 13 6),(Point 18 7),(Point 21 5),(Point 25 9),(Point 20 12),(Point 16 11),(Point 13 12)))
                    $graphics.DrawLine($outline,10,7,6,1);$graphics.DrawLine($light,10,7,6,1)
                    $graphics.DrawLine($outline,23,6,29,1);$graphics.DrawLine($light,23,6,29,1)
                    $graphics.FillRectangle($white,20,8,2,2)
                    $graphics.FillPolygon($white,[Drawing.Point[]]@((Point 17 13),(Point 14 17),(Point 17 21),(Point 20 17)))
                    $graphics.DrawArc($body,6,15,19,12,0,210)
                    $graphics.DrawArc($light,6,15,19,12,30,130)
                    $graphics.FillRectangle($cyan,4,21,2,2);$graphics.FillRectangle($white,27,17,2,2)
                }
            }
            $bitmap.Save((Join-Path $assetDir ($names[$tier-1]+'.png')),[Drawing.Imaging.ImageFormat]::Png)
            $previewGraphics.DrawImage($bitmap,[Drawing.Rectangle]::new(($tier-1)*130+20,12,96,96))
            $previewGraphics.DrawString(('TIER '+$tier),$font,$label,($tier-1)*130+34,125)
        } finally {
            $outline.Dispose();$body.Dispose();$light.Dispose();$ring.Dispose()
            $cyan.Dispose();$dark.Dispose();$white.Dispose();$graphics.Dispose();$bitmap.Dispose()
        }
    }
    $preview.Save((Join-Path $previewDir 'water-weapon-guide.png'),[Drawing.Imaging.ImageFormat]::Png)
} finally {$font.Dispose();$label.Dispose();$previewGraphics.Dispose();$preview.Dispose()}
Write-Output 'Generated five original 32x32 guide weapon concepts and one preview.'
