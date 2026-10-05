# tools/gen_dark_giant_walk.ps1
#
# Adds a real WALK cycle ("walk") to the dark giant's animation file.
#
# Why: the giant was wired to play the author's `treadon` clip while walking, and the author
# objected -- "who told you to use the stomp as walking". `treadon` is a stomp/move clip; it is
# now triggered on melee hits instead (TNDarkGiantEntity.playStomp) and this script provides the
# actual locomotion cycle.
#
# SIGN CONVENTION (measured, not guessed -- tools/preview_geo_anim.py + probe renders):
#   the model faces -Z, and for this skeleton a NEGATIVE X rotation swings a limb FORWARD
#   (probe: legright -40 pushed the geometry to z=-27, i.e. in front; +40 pushed it to z=+29).
#   So: leg forward = -X, leg back = +X. Arms hang from the same frame => same rule.
#
# CYCLE (2.0 s = 40 ticks = one full stride, two steps):
#   t=0.0  right leg forward / left leg back   (arms counter-swing)
#   t=0.5  legs pass under the body            (torso rolls to the other side)
#   t=1.0  mirrored
#   t=1.5  pass again
#   t=2.0  == t=0.0  => loops seamlessly
# The legs are one rigid bone each (no knee), so the foot traces an arc around the hip and never
# dips below its rest height => no ground penetration, and no need for a vertical body bob.
#
# Usage:  powershell -NoProfile -ExecutionPolicy Bypass -File tools\gen_dark_giant_walk.ps1
#         ... -DryRun    (print the clip, do not touch the file)
#
# ASCII only on purpose (no BOM needed on a GBK host). The author's other clips are left
# byte-identical: only one "walk" key is inserted/replaced, by brace matching.
param(
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$animPath = Join-Path $root 'src\main\resources\assets\tnc\animations\entity\dark_giant.animation.json'

# keyframe times (seconds) -- must be the same for every track so the loop closes cleanly
# 1.4 s per stride (two steps). Tuned against the boss's movement speed so the feet do not slide:
#   hip->sole = 34 model units = 2.125 blocks, swing -32 / +26 deg
#   => ~2.06 blocks per step, 4.12 per cycle, 4.12 / 1.4 s = 2.94 blocks/s = 0.147 b/tick
#   => TNDarkGiantEntity walks at 0.16 b/tick (3.2 b/s): ~9% slide, which reads as a heavy stomp
#      instead of an ice-skating giant.
$t = @(0.0, 0.35, 0.7, 1.05, 1.4)

# bone -> 5 keyframes of [x, y, z] degrees
#   index:            0       1      2       3      4
$tracks = [ordered]@{
    'legright'     = @(@(-32, 0, 0), @(0, 0, 0), @(26, 0, 0),  @(0, 0, 0),  @(-32, 0, 0))
    'legleft'      = @(@(26, 0, 0),  @(0, 0, 0), @(-32, 0, 0), @(0, 0, 0),  @(26, 0, 0))
    'armright'     = @(@(18, 0, 0),  @(0, 0, 0), @(-14, 0, 0), @(0, 0, 0),  @(18, 0, 0))
    'armleft'      = @(@(-14, 0, 0), @(0, 0, 0), @(18, 0, 0),  @(0, 0, 0),  @(-14, 0, 0))
    'pauldronright'= @(@(4, 0, 0),   @(0, 0, 0), @(-4, 0, 0),  @(0, 0, 0),  @(4, 0, 0))
    'pauldronleft' = @(@(-4, 0, 0),  @(0, 0, 0), @(4, 0, 0),   @(0, 0, 0),  @(-4, 0, 0))
    # torso: constant slight forward lean (-4) + weight shift (roll) + shoulder counter-rotation
    'body'         = @(@(-4, 6, 5), @(-2, 0, 0), @(-4, -6, -5), @(-2, 0, 0), @(-4, 6, 5))
    'head'         = @(@(4, -4, -3),  @(2, 0, 0), @(4, 4, 3),   @(2, 0, 0),  @(4, -4, -3))
    'cape'         = @(@(5, 0, 0),   @(0, 0, 0), @(-8, 0, 0),  @(0, 0, 0),  @(5, 0, 0))
    'tail'         = @(@(-4, -8, 0),  @(0, 0, 0), @(3, 8, 0),   @(0, 0, 0),  @(-4, -8, 0))
}

function Fmt([double]$v) {
    if ($v -eq [math]::Floor($v)) { return ([int]$v).ToString([Globalization.CultureInfo]::InvariantCulture) }
    return $v.ToString('0.###', [Globalization.CultureInfo]::InvariantCulture)
}

$tab = [string][char]9
$lines = @()
$lines += '"walk": {'
$lines += "$tab$tab$tab`"loop`": true,"
$lines += "$tab$tab$tab`"animation_length`": 1.4,"
$lines += "$tab$tab$tab`"bones`": {"
$boneNames = @($tracks.Keys)
for ($b = 0; $b -lt $boneNames.Count; $b++) {
    $bone = $boneNames[$b]
    $frames = @()
    for ($k = 0; $k -lt $t.Count; $k++) {
        $rot = $tracks[$bone][$k]
        $frames += ('"{0}": [{1}, {2}, {3}]' -f (Fmt $t[$k]), (Fmt $rot[0]), (Fmt $rot[1]), (Fmt $rot[2]))
    }
    $comma = if ($b -lt $boneNames.Count - 1) { ',' } else { '' }
    # NOTE: single braces here -- this is plain string interpolation, not a -f format string
    $lines += "$tab$tab$tab$tab`"$bone`": { `"rotation`": { $($frames -join ', ') } }$comma"
}
$lines += "$tab$tab$tab}"
$lines += "$tab$tab}"
$walkJson = ($lines -join "`n")

# ---- self checks ----
foreach ($bone in $boneNames) {
    $first = $tracks[$bone][0]
    $last = $tracks[$bone][$t.Count - 1]
    if ($first[0] -ne $last[0] -or $first[1] -ne $last[1] -or $first[2] -ne $last[2]) {
        throw "loop does not close on '$bone' (first $($first -join ',') vs last $($last -join ','))"
    }
    # the two legs must be in opposite phase, otherwise this is not a walk:
    # keyframe 0 of one leg has to equal keyframe 2 (half a cycle later) of the other
}
for ($c = 0; $c -lt 3; $c++) {
    if ($tracks['legright'][0][$c] -ne $tracks['legleft'][2][$c] -or
        $tracks['legleft'][0][$c] -ne $tracks['legright'][2][$c]) {
        throw 'legs are not in opposite phase'
    }
}
Write-Host ("walk: {0} bones x {1} keyframes, {2}s loop" -f $boneNames.Count, $t.Count, $t[$t.Count - 1])
foreach ($bone in $boneNames) {
    Write-Host ("  {0,-14} {1}" -f $bone, (($tracks[$bone] | ForEach-Object { '(' + ($_ -join ',') + ')' }) -join ' '))
}

if ($DryRun) {
    Write-Host '----- walk clip (not written) -----'
    Write-Host $walkJson
    return
}

$text = [System.IO.File]::ReadAllText($animPath)

# replace an existing "walk" (brace matching, nothing else is reformatted)
$marker = '"walk"'
$at = $text.IndexOf($marker)
if ($at -ge 0) {
    $open = $text.IndexOf('{', $at)
    $depth = 0
    $end = -1
    for ($i = $open; $i -lt $text.Length; $i++) {
        $ch = $text[$i]
        if ($ch -eq '{') { $depth++ }
        elseif ($ch -eq '}') {
            $depth--
            if ($depth -eq 0) { $end = $i; break }
        }
    }
    if ($end -lt 0) { throw 'existing walk clip has unbalanced braces' }
    $after = $end + 1
    while ($after -lt $text.Length -and ($text[$after] -eq ',' -or $text[$after] -eq ' ' -or $text[$after] -eq $tab -or $text[$after] -eq "`r" -or $text[$after] -eq "`n")) { $after++ }
    $text = $text.Substring(0, $at) + $text.Substring($after)
    Write-Host '(replaced the previous walk clip)'
}

$anchor = '"animations"'
$ai = $text.IndexOf($anchor)
if ($ai -lt 0) { throw 'no "animations" section found' }
$brace = $text.IndexOf('{', $ai)
$head = $text.Substring(0, $brace + 1)
$tail = $text.Substring($brace + 1)
$text = $head + "`n$tab$tab" + $walkJson + ',' + $tail
$text = $text.Replace("`r`n", "`n")   # the author's file is LF only

[System.IO.File]::WriteAllText($animPath, $text, [System.Text.UTF8Encoding]::new($false))
Write-Host ("written {0}" -f $animPath)
