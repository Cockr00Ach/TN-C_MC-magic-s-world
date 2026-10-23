$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$root=Split-Path $PSScriptRoot -Parent
$art=Join-Path $root 'art/generated/thunder-seal-20261023'
$target=Join-Path $root 'src/main/resources/assets/tnc/textures'
New-Item -ItemType Directory -Force -Path (Join-Path $target 'block/thunder_seal') | Out-Null
function Save-Tile($image,$rectangle,$size,$path){
    $tile=New-Object Drawing.Bitmap($size,$size,[Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics=[Drawing.Graphics]::FromImage($tile)
    try{
        $graphics.CompositingMode=[Drawing.Drawing2D.CompositingMode]::SourceCopy
        $graphics.InterpolationMode=[Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.DrawImage($image,[Drawing.Rectangle]::new(0,0,$size,$size),$rectangle,[Drawing.GraphicsUnit]::Pixel)
        $tile.Save($path,[Drawing.Imaging.ImageFormat]::Png)
    }finally{$graphics.Dispose();$tile.Dispose()}
}
$circle=[Drawing.Bitmap]::FromFile((Join-Path $art 'circle.png'))
try{Save-Tile $circle ([Drawing.Rectangle]::new(0,0,$circle.Width,$circle.Height)) 512 (Join-Path $target 'entity/thunder_seal_circle.png')}finally{$circle.Dispose()}
$atlas=[Drawing.Bitmap]::FromFile((Join-Path $art 'materials.png'))
try{
    $names=@('dark','gold','blue','gem');$width=[int]($atlas.Width/2);$height=[int]($atlas.Height/2)
    for($i=0;$i -lt 4;$i++){
        $rect=[Drawing.Rectangle]::new(($i%2)*$width,[math]::Floor($i/2)*$height,$width,$height)
        Save-Tile $atlas $rect 256 (Join-Path $target ('block/thunder_seal/'+$names[$i]+'.png'))
    }
}finally{$atlas.Dispose()}
