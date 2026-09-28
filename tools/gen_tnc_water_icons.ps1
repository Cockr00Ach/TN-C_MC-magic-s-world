# Original vector/pixel icon family. Matches the existing fire/wind code-native art pipeline.
# No third-party art is copied. Jar resources are authoritative; pack overrides checked by installer.
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$root=Split-Path -Parent $PSScriptRoot
$dir=Join-Path $root 'src\main\resources\assets\tnc\textures\spell'
New-Item -ItemType Directory -Force -Path $dir | Out-Null
function Color([string]$hex){return [System.Drawing.ColorTranslator]::FromHtml($hex)}
$names=@(
    @('water_ball','water_cannon','dragon_roar','dragon_howl','dragon_ruin'),
    @('water_ripple','water_wave','wave_slash','tsunami','world_ending_sea'),
    @('water_bind','water_prison','water_burial','abyss','sea_god_crypt'),
    @('raindrop','first_rain','rainfall','downpour','flood_of_heaven'))
function Icon([string]$name,[int]$family,[int]$tier){
    $bmp=[System.Drawing.Bitmap]::new(16,16)
    $g=[System.Drawing.Graphics]::FromImage($bmp)
    $g.Clear([System.Drawing.Color]::Transparent)
    $g.SmoothingMode=[System.Drawing.Drawing2D.SmoothingMode]::None
    $base=Color '#29BEEE'; $lite=Color '#CBFBFF'; $gold=Color '#F7D579'
    if($family -eq 3){
        $base=Color (@('#2F98FF','#44E4A9','#A3DCFF','#E6C367','#ECF8FF')[$tier-1])
    }
    if($family -eq 4){$base=Color '#A872EE';$lite=Color '#F1DBFF'}
    $pen=[System.Drawing.Pen]::new($base,1)
    $bright=[System.Drawing.Pen]::new($lite,1)
    $accent=[System.Drawing.Pen]::new($gold,1)
    $brush=[System.Drawing.SolidBrush]::new($base)
    $glow=[System.Drawing.SolidBrush]::new($lite)
    try {
        if($family -eq 0){
            if($tier -le 2){
                $g.FillEllipse($brush,5,5,8,8);$g.DrawEllipse($bright,5,5,7,7);$g.FillRectangle($glow,7,6,2,2)
                $g.DrawLine($pen,1,7,4,8);$g.DrawLine($bright,0,10,4,10)
                if($tier -eq 2){$g.DrawArc($accent,3,3,11,11,210,230);$g.DrawLine($pen,0,13,6,12)}
            } elseif($tier -eq 3){
                $g.DrawEllipse($accent,1,1,13,13)
                foreach($pt in @(@(5,3),@(3,8),@(8,10))){$g.FillEllipse($brush,[int]$pt[0],[int]$pt[1],3,3);$g.DrawLine($bright,[int]$pt[0]+2,[int]$pt[1]+1,14,7)}
            } else {
                $g.DrawEllipse($accent,0,2,7,11);$g.DrawEllipse($pen,2,4,3,7)
                $thick=if($tier -eq 5){5}else{3}
                $g.FillRectangle($brush,5,8-[int]($thick/2),11,$thick);$g.DrawLine($bright,4,8,15,8)
                if($tier -eq 5){$g.DrawLine($accent,5,3,15,5);$g.DrawLine($accent,5,13,15,11)}
            }
        } elseif($family -eq 1){
            if($tier -eq 1){$g.DrawEllipse($pen,1,5,13,6);$g.DrawEllipse($bright,5,7,5,2)}
            elseif($tier -le 3){
                $g.DrawArc($pen,2,1,11,13,70,250);$g.DrawArc($bright,3,2,10,11,90,220)
                if($tier -eq 3){$g.DrawLine($accent,0,8,15,8);$g.DrawLine($accent,8,0,8,15);$g.DrawLine($pen,1,1,14,14)}
            } else {
                for($y=4;$y -le 12;$y+=4){$g.DrawArc($pen,0,$y-3,9,8,180,160);$g.DrawLine($bright,4,$y,14,$y+1)}
                if($tier -eq 5){$g.DrawLine($accent,10,0,8,4);$g.DrawLine($accent,8,4,12,4);$g.DrawLine($accent,12,4,9,8)}
            }
        } elseif($family -eq 2){
            $g.DrawEllipse($pen,2,2,11,11);$g.DrawEllipse($bright,4,5,7,5)
            for($i=0;$i -lt $tier;$i++){$x=3+$i*2;$g.DrawLine($pen,$x,4,$x,12)}
            if($tier -ge 3){$g.DrawArc($accent,0,0,15,15,40,280);$g.FillEllipse($glow,7,7,2,2)}
        } elseif($family -eq 3){
            $g.DrawArc($pen,1,1,13,7,180,180);$g.DrawLine($pen,1,5,14,5)
            for($i=0;$i -lt $tier+1;$i++){$x=2+$i*2;$g.DrawLine($pen,$x,7,$x-1,10)}
            if($tier -eq 2){$g.DrawArc($bright,2,9,11,5,0,180)}
            if($tier -eq 3){$g.DrawArc($bright,1,8,7,7,0,180);$g.DrawArc($bright,7,8,7,7,0,180)}
            if($tier -ge 4){$g.DrawLine($accent,2,8,2,13);$g.DrawLine($accent,13,8,13,13);$g.DrawArc($accent,2,10,11,5,0,180)}
            if($tier -eq 5){$g.DrawEllipse($accent,3,0,9,3);$g.DrawLine($bright,8,6,8,14)}
        } else {
            $g.DrawEllipse($pen,1,1,13,13);$g.DrawEllipse($bright,4,4,7,7)
            $g.DrawLine($bright,3,3,12,12);$g.DrawLine($bright,12,3,3,12)
            $g.DrawLine($pen,8,0,8,15);$g.DrawLine($pen,0,8,15,8)
        }
        # Top tiers share a small gold crown, while lower tiers remain clean cyan.
        if($tier -ge 4 -and $family -ne 4){$bmp.SetPixel(7,0,$gold);$bmp.SetPixel(8,0,$gold)}
        $bmp.Save((Join-Path $dir ($name+'.png')),[System.Drawing.Imaging.ImageFormat]::Png)
    } finally {$pen.Dispose();$bright.Dispose();$accent.Dispose();$brush.Dispose();$glow.Dispose();$g.Dispose();$bmp.Dispose()}
}
for($f=0;$f -lt 4;$f++){for($t=1;$t -le 5;$t++){Icon $names[$f][$t-1] $f $t}}
Icon 'chaos_magic' 4 1
Write-Output 'Generated 21 original 16x16 spell icons in jar resources.'
