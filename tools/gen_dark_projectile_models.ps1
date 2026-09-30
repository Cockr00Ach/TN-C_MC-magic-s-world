# Generate the DARK projectile models for the dark boss (tnc:yan_dark).
#
# Input : magic_modelandtexture/**/ *_dark.bbmodel   (Blockbench java_block project)
#         + the shipped LIGHT model json (template: element order, groups, format_version,
#           credit - it is the copy the author already verified in game)
# Output: src/main/resources/assets/tnc/models/projectile/<out>.json
#         + the textures copied into assets/tnc/textures/spell_projectile/
#
# Why this way:
#   The dark .bbmodel files are texture-only variants of the light ones. Verified at
#   generation time: same element count, same geometry (from/to) in the same outliner
#   order, only face UVs and texture indices differ. So the safest conversion is
#   "shipped light json + the dark model's faces + our own texture paths" - no element
#   re-ordering guesswork, no UV re-mapping guesswork.
#
#   -SelfTest regenerates flash.json from flashinggg.bbmodel and compares it with the
#   shipped file byte for byte: if that passes, the whole pipeline (outliner order,
#   face mapping, number/indent formatting) is proven.
#
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File tools/gen_dark_projectile_models.ps1 [-SelfTest]
# ASCII only on purpose (see docs: tools/*.ps1 must stay pure ASCII).

param([switch]$SelfTest)

$ErrorActionPreference = 'Stop'
$Inv = [System.Globalization.CultureInfo]::InvariantCulture

$RepoRoot = Split-Path $PSScriptRoot -Parent
$BBRoot = Join-Path $RepoRoot 'magic_modelandtexture'
$ModelDir = Join-Path $RepoRoot 'src\main\resources\assets\tnc\models\projectile'
$TexDir = Join-Path $RepoRoot 'src\main\resources\assets\tc\textures\spell_projectile'
$TexDir = Join-Path $RepoRoot 'src\main\resources\assets\tnc\textures\spell_projectile'

$FaceOrder = @('north', 'east', 'south', 'west', 'up', 'down')

$Jobs = @(
    @{
        Out      = 'flash_dark'
        Bb       = 'flash\flashinggg_dark.bbmodel'
        Template = 'flash.json'
        Textures = [ordered]@{
            '0'        = 'tnc:spell_projectile/flash_dark'
            'particle' = 'tnc:spell_projectile/flash_dark'
        }
        Copies   = @(
            @{ Src = 'flash\flashing_dark.png'; Dst = 'flash_dark.png' }
        )
    },
    @{
        Out      = 'god_dark'
        Bb       = 'flash\LINGTINGGOD_dark.bbmodel'
        Template = 'lightning_god.json'
        Textures = [ordered]@{
            '0'        = 'tnc:spell_projectile/god_dark_body'
            '1'        = 'tnc:spell_projectile/god_dark'
            'particle' = 'tnc:spell_projectile/god_dark_body'
        }
        Copies   = @(
            @{ Src = 'flash\goddarkbody.png';     Dst = 'god_dark_body.png' }
            @{ Src = 'flash\goddarklighting.png'; Dst = 'god_dark.png' }
        )
    },
    @{
        Out      = 'lightingball_dark'
        Bb       = 'lightingball_2\lightingball_2_dark.bbmodel'
        Template = 'lightingball_2.json'
        Textures = [ordered]@{
            '0'        = 'tnc:spell_projectile/lightingball_dark'
            'particle' = 'tnc:spell_projectile/lightingball_dark'
        }
        Copies   = @(
            @{ Src = 'lightingball_2\darklightingball.png'; Dst = 'lightingball_dark.png' }
        )
    }
)

function Get-OutlinerOrder {
    # Depth-first walk of the Blockbench outliner: element uuids are plain strings,
    # groups are objects with a "children" array. Returns the elements in export order.
    param($Bb)
    $byUuid = @{}
    foreach ($e in $Bb.elements) { $byUuid[$e.uuid] = $e }
    $ordered = New-Object System.Collections.Generic.List[object]
    function Walk($nodes) {
        foreach ($n in $nodes) {
            if ($n -is [string]) { $ordered.Add($byUuid[$n]) }
            elseif ($n -and $n.children) { Walk $n.children }
        }
    }
    Walk $Bb.outliner
    return , $ordered
}

function Fmt {
    # Blockbench style numbers: integers bare, decimals trimmed (22.5 / 0.75 / 2.25).
    param($Value)
    return ([double]$Value).ToString('0.####', $Inv)
}

function Fmt-Array {
    param($Values)
    return '[' + (($Values | ForEach-Object { Fmt $_ }) -join ', ') + ']'
}

function Scale-Uv {
    # Blockbench exports Java model UVs in a 0..16 space regardless of the project's
    # texture resolution (verified against the shipped models: flash 32 -> /2,
    # lightning god 16 -> /1, lightingball_2 64 -> /4).
    param($Uv, $Resolution)
    $sx = 16.0 / [double]($Resolution.width)
    $sy = 16.0 / [double]($Resolution.height)
    $u0 = [double]($Uv[0]); $v0 = [double]($Uv[1])
    $u1 = [double]($Uv[2]); $v1 = [double]($Uv[3])
    return @(($u0 * $sx), ($v0 * $sy), ($u1 * $sx), ($v1 * $sy))
}

function New-ModelText {
    # Emits the same style as the shipped models: LF, one tab per level, inline arrays.
    param($TemplateRaw, $Template, $DarkOrder, $Textures, $Resolution)

    $nl = "`n"
    $sb = New-Object System.Text.StringBuilder

    [void]$sb.Append('{' + $nl)
    [void]$sb.Append("`t" + '"format_version": "' + $Template.format_version + '",' + $nl)
    [void]$sb.Append("`t" + '"credit": "' + $Template.credit + '",' + $nl)
    [void]$sb.Append("`t" + '"textures": {' + $nl)
    $texKeys = @($Textures.Keys)
    for ($i = 0; $i -lt $texKeys.Count; $i++) {
        $comma = if ($i -lt $texKeys.Count - 1) { ',' } else { '' }
        [void]$sb.Append("`t`t" + '"' + $texKeys[$i] + '": "' + $Textures[$texKeys[$i]] + '"' + $comma + $nl)
    }
    [void]$sb.Append("`t" + '},' + $nl)
    [void]$sb.Append("`t" + '"elements": [' + $nl)
    for ($i = 0; $i -lt $DarkOrder.Count; $i++) {
        $d = $DarkOrder[$i]
        $t = $Template.elements[$i]
        [void]$sb.Append("`t`t{" + $nl)
        [void]$sb.Append("`t`t`t" + '"from": ' + (Fmt-Array $t.from) + ',' + $nl)
        [void]$sb.Append("`t`t`t" + '"to": ' + (Fmt-Array $t.to) + ',' + $nl)
        if ($t.rotation) {
            [void]$sb.Append("`t`t`t" + '"rotation": {"angle": ' + (Fmt $t.rotation.angle) +
                    ', "axis": "' + $t.rotation.axis + '", "origin": ' + (Fmt-Array $t.rotation.origin) + '},' + $nl)
        }
        [void]$sb.Append("`t`t`t" + '"faces": {' + $nl)
        $present = @($FaceOrder | Where-Object { $d.faces.$_ })
        for ($k = 0; $k -lt $present.Count; $k++) {
            $faceName = $present[$k]
            $f = $d.faces.$faceName
            $comma = if ($k -lt $present.Count - 1) { ',' } else { '' }
            [void]$sb.Append("`t`t`t`t" + '"' + $faceName + '": {"uv": ' + (Fmt-Array (Scale-Uv $f.uv $Resolution)) +
                    ', "texture": "#' + [string]$f.texture + '"}' + $comma + $nl)
        }
        [void]$sb.Append("`t`t`t}" + $nl)
        $comma = if ($i -lt $DarkOrder.Count - 1) { ',' } else { '' }
        [void]$sb.Append("`t`t}" + $comma + $nl)
    }
    [void]$sb.Append("`t]," + $nl)

    # "groups" is Blockbench metadata (Minecraft ignores it) - splice the template's copy verbatim.
    $g = $TemplateRaw.IndexOf('"groups"')
    if ($g -ge 0) {
        $tail = $TemplateRaw.Substring($g).TrimEnd()
        if ($tail.EndsWith('}')) { $tail = $tail.Substring(0, $tail.Length - 1).TrimEnd() }
        [void]$sb.Append("`t" + $tail.TrimStart() + $nl)
    }
    [void]$sb.Append('}' + $nl)
    return $sb.ToString()
}

function New-Model {
    param($Job, [switch]$CompareOnly)
    $bbPath = Join-Path $BBRoot $Job.Bb
    $tplPath = Join-Path $ModelDir $Job.Template
    if (-not (Test-Path $bbPath)) { throw "missing bbmodel: $bbPath" }
    if (-not (Test-Path $tplPath)) { throw "missing template: $tplPath" }
    $bb = Get-Content -LiteralPath $bbPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $tplRaw = [System.IO.File]::ReadAllText($tplPath, [System.Text.UTF8Encoding]::new($false))
    $tpl = $tplRaw | ConvertFrom-Json
    $dark = Get-OutlinerOrder $bb
    if ($dark.Count -ne $tpl.elements.Count) {
        throw "$($Job.Out): element count differs (dark=$($dark.Count) template=$($tpl.elements.Count))"
    }
    for ($i = 0; $i -lt $dark.Count; $i++) {
        $d = $dark[$i]; $t = $tpl.elements[$i]
        if (($d.from -join ',') -ne ($t.from -join ',') -or ($d.to -join ',') -ne ($t.to -join ',')) {
            throw "$($Job.Out): element $i geometry differs from the template (dark=$($d.from -join ',')/$($d.to -join ',') template=$($t.from -join ',')/$($t.to -join ','))"
        }
    }
    return (New-ModelText -TemplateRaw $tplRaw -Template $tpl -DarkOrder $dark -Textures $Job.Textures -Resolution $bb.resolution)
}

$failures = 0

if ($SelfTest) {
    # Regenerate every LIGHT model from its bbmodel and compare with the shipped file:
    # if all three match, the whole pipeline (outliner order, UV normalisation, face
    # mapping, number/indent formatting) is proven and the dark conversions are safe.
    $probes = @(
        @{
            Out = 'flash'; Bb = 'flash\flashinggg.bbmodel'; Template = 'flash.json'
            Textures = [ordered]@{ '0' = 'tnc:spell_projectile/flash'; 'particle' = 'tnc:spell_projectile/flash' }
        },
        @{
            Out = 'god'; Bb = 'flash\LINGTINGGOD.bbmodel'; Template = 'lightning_god.json'
            Textures = [ordered]@{
                '0'        = 'tnc:spell_projectile/lightning_god_body'
                '1'        = 'tnc:spell_projectile/lightning_god'
                'particle' = 'tnc:spell_projectile/lightning_god_body'
            }
        },
        @{
            Out = 'ball'; Bb = 'lightingball_2\lightingball_2.bbmodel'; Template = 'lightingball_2.json'
            Textures = [ordered]@{ '0' = 'tnc:spell_projectile/lightingball_2'; 'particle' = 'tnc:spell_projectile/lightingball_2' }
        }
    )
    foreach ($probe in $probes) {
        try {
            $text = New-Model -Job $probe
            $shipped = [System.IO.File]::ReadAllText((Join-Path $ModelDir $probe.Template), [System.Text.UTF8Encoding]::new($false))
            if ($text -eq $shipped) {
                Write-Host "SelfTest $($probe.Out): regenerated == shipped, byte for byte - pipeline OK"
            }
            elseif (($text -replace '\s+', '') -eq ($shipped -replace '\s+', '')) {
                Write-Host "SelfTest $($probe.Out): regenerated == shipped except whitespace - pipeline OK"
            }
            else {
                $a = ($text -replace '\s+', '')
                $b = ($shipped -replace '\s+', '')
                Write-Host "SelfTest $($probe.Out): MISMATCH"
                $n = [Math]::Min($a.Length, $b.Length)
                for ($i = 0; $i -lt $n; $i++) {
                    if ($a[$i] -ne $b[$i]) {
                        Write-Host ("  first diff at $i : gen='" + $a.Substring([Math]::Max(0, $i - 40), 80) + "' shipped='" + $b.Substring([Math]::Max(0, $i - 40), 80) + "'")
                        break
                    }
                }
                $failures++
            }
        } catch {
            Write-Host "SelfTest $($probe.Out) FAILED: $_"
            $failures++
        }
    }
}

foreach ($job in $Jobs) {
    try {
        $text = New-Model -Job $job
        $outPath = Join-Path $ModelDir ($job.Out + '.json')
        [System.IO.File]::WriteAllText($outPath, $text, [System.Text.UTF8Encoding]::new($false))
        foreach ($copy in $job.Copies) {
            $src = Join-Path $BBRoot $copy.Src
            $dst = Join-Path $TexDir $copy.Dst
            if (-not (Test-Path $src)) { throw "missing texture: $src" }
            Copy-Item -LiteralPath $src -Destination $dst -Force
        }
        Write-Host ("{0,-20} elements={1}  textures={2}  bytes={3}" -f $job.Out, (($text -split '"from":').Count - 1), (($job.Textures.Values | Select-Object -Unique) -join ' + '), $text.Length)
    } catch {
        Write-Host "FAILED $($job.Out): $_"
        $failures++
    }
}

if ($failures -gt 0) { Write-Host "FAILED: $failures problem(s)"; exit 1 }
Write-Host 'all dark projectile models generated'
exit 0
