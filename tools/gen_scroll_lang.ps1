# gen_scroll_lang.ps1 -- build the in-game reading text of the 6 lore scrolls
# into the mod lang file, straight from the story document.
#
# WHY: the story doc is owned by another topic (writing/quest) and is the single
# source of truth for the text. Re-typing it into lang by hand WILL drift.
# Run this after the author edits the doc, then rebuild the jar.
#
# ONLY the first '## ' section of each volume heading is shipped (that is the
# in-game body text). The second section of each volume holds author-only notes
# and must NEVER end up in the jar.
#
# ASCII ONLY on purpose (repo rule: tools/*.ps1 must contain zero non-ASCII
# bytes). That is why the doc path is auto-detected structurally, or passed in
# with -Doc by the caller.
#
# Usage:
#   pwsh -File tools\gen_scroll_lang.ps1
#   pwsh -File tools\gen_scroll_lang.ps1 -Doc '<path to the story md>'
#   pwsh -File tools\gen_scroll_lang.ps1 -Check      # report only, write nothing

param(
    [string]$Doc = '',
    [switch]$Check
)

$ErrorActionPreference = 'Stop'

$repo = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrEmpty($repo)) { $repo = (Get-Location).Path }

# volume order in the doc -> item id suffix. The first volume is split in two
# by the author, so it maps to two items.
$ids = @('canjuan_1a', 'canjuan_1b', 'canjuan_3', 'canjuan_4', 'canjuan_5', 'canjuan_6')

$langZh = Join-Path $repo 'src\main\resources\assets\tnc\lang\zh_cn.json'
$langEn = Join-Path $repo 'src\main\resources\assets\tnc\lang\en_us.json'

# ---------------------------------------------------------------- find the doc
if ([string]::IsNullOrEmpty($Doc)) {
    $dirs = Get-ChildItem -Path $repo -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -notmatch '^[\x20-\x7E]+$' }
    $cands = @()
    foreach ($d in $dirs) {
        foreach ($f in Get-ChildItem -Path $d.FullName -Recurse -File -Filter *.md -ErrorAction SilentlyContinue) {
            $t = [System.IO.File]::ReadAllText($f.FullName, [System.Text.Encoding]::UTF8)
            $h1 = ([regex]::Matches($t, '(?m)^# ')).Count
            $h2 = ([regex]::Matches($t, '(?m)^## ')).Count
            # 1 title + 6 volumes + appendix headings, and 2 sections per volume
            if ($h1 -ge 6 -and $h2 -ge 12) { $cands += $f.FullName }
        }
    }
    if ($cands.Count -ne 1) {
        throw "auto-detect found $($cands.Count) candidate doc(s); pass -Doc <path> explicitly"
    }
    $Doc = $cands[0]
}
if (-not (Test-Path -LiteralPath $Doc)) { throw "doc not found: $Doc" }
Write-Host "  doc : $Doc"

# ------------------------------------------------------------------- extract
$lines = [System.IO.File]::ReadAllLines($Doc, [System.Text.Encoding]::UTF8)
$texts = @{}
$h1 = -1                       # how many '# ' headings seen
$h2 = -1                       # how many '## ' headings inside current '# '
$body = $null
$collecting = $false

foreach ($l in $lines) {
    if ($l.StartsWith('# ')) {
        if ($collecting) { break }
        $h1++
        $h2 = -1
        continue
    }
    if ($l.StartsWith('## ')) {
        $h2++
        if ($h1 -ge 1 -and $h1 -le 6 -and $h2 -eq 0) {
            # first section of a volume == the text the player reads
            $collecting = $true
            $body = New-Object System.Collections.Generic.List[string]
        }
        elseif ($collecting) {
            # second section starts == stop (author-only notes)
            $collecting = $false
            $texts[$ids[$h1 - 1]] = $body
        }
        continue
    }
    if ($collecting) { $body.Add($l) }
}

# ------------------------------------------------------------------- clean up
$result = @{}
foreach ($id in $ids) {
    if (-not $texts.ContainsKey($id)) { throw "volume for $id not found in the doc" }
    $clean = New-Object System.Collections.Generic.List[string]
    $prevBlank = $true
    foreach ($l in $texts[$id]) {
        $s = $l
        if ($s.StartsWith('>')) { $s = $s.Substring(1) }     # strip blockquote marker
        if ($s.StartsWith(' ')) { $s = $s.TrimStart(' ') }
        $s = $s.Replace('**', '')                            # strip bold markers
        $s = $s.TrimEnd()
        if ($s -eq '') {
            if (-not $prevBlank) { $clean.Add(''); $prevBlank = $true }
        }
        else {
            $clean.Add($s)
            $prevBlank = $false
        }
    }
    while ($clean.Count -gt 0 -and $clean[$clean.Count - 1] -eq '') { $clean.RemoveAt($clean.Count - 1) }
    $result[$id] = ($clean -join "`n")
}

# --------------------------------------------------------- read + merge lang
function Esc([string]$s) {
    $sb = New-Object System.Text.StringBuilder
    foreach ($c in $s.ToCharArray()) {
        $str = [string]$c
        if ($str -eq '"') { $null = $sb.Append('\"') }
        elseif ($str -eq '\') { $null = $sb.Append('\\') }
        elseif ($str -eq "`n") { $null = $sb.Append('\n') }
        elseif ($str -eq "`r") { $null = $sb.Append('\r') }
        elseif ($str -eq "`t") { $null = $sb.Append('\t') }
        else { $null = $sb.Append($str) }
    }
    return $sb.ToString()
}

function Unesc([string]$s) {
    $sb = New-Object System.Text.StringBuilder
    for ($i = 0; $i -lt $s.Length; $i++) {
        $c = $s[$i]
        if ($c -eq '\' -and ($i + 1) -lt $s.Length) {
            $i++
            $n = $s[$i]
            if ($n -eq 'n') { $null = $sb.Append("`n") }
            elseif ($n -eq 't') { $null = $sb.Append("`t") }
            elseif ($n -eq 'r') { $null = $sb.Append("`r") }
            else { $null = $sb.Append($n) }
        }
        else { $null = $sb.Append($c) }
    }
    return $sb.ToString()
}

function Parse-Lang([string]$text) {
    $map = @{}
    foreach ($m in [regex]::Matches($text, '"((?:[^"\\]|\\.)*)"\s*:\s*"((?:[^"\\]|\\.)*)"')) {
        $map[(Unesc $m.Groups[1].Value)] = (Unesc $m.Groups[2].Value)
    }
    return $map
}

# Flat "key": "value" parser. ConvertFrom-Json is NOT used on purpose: Windows
# PowerShell 5.1 chokes on perfectly valid files here, and our lang files are
# always flat maps of strings anyway.
function Read-Lang([string]$path) {
    if (-not (Test-Path -LiteralPath $path)) { return @{} }
    return (Parse-Lang ([System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)))
}

function Write-Lang([string]$path, $map) {
    $sb = New-Object System.Text.StringBuilder
    $null = $sb.AppendLine('{')
    $keys = @($map.Keys | Sort-Object)
    for ($i = 0; $i -lt $keys.Count; $i++) {
        $null = $sb.Append('  "' + $keys[$i] + '": "' + (Esc $map[$keys[$i]]) + '"')
        if ($i -lt $keys.Count - 1) { $null = $sb.Append(',') }
        $null = $sb.AppendLine()
    }
    $null = $sb.AppendLine('}')
    [System.IO.File]::WriteAllText($path, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
}

$zh = Read-Lang $langZh
$en = Read-Lang $langEn

$total = 0
foreach ($id in $ids) {
    $txt = $result[$id]
    $zh["scroll.tnc.$id"] = $txt
    $en["scroll.tnc.$id"] = '(A fragment in ancient script - no translation in this build.)'
    $total += $txt.Length
    Write-Host ("  {0}  {1,4} chars, {2,3} lines" -f $id, $txt.Length, ($txt.Split("`n").Count))
}

# keys used by the code itself (hint line + fallback when text is missing).
# Kept in a JSON side file because this script must stay ASCII-only; PowerShell
# 5.1 reads BOM-less .ps1 as ANSI and would mangle CJK literals.
$extraPath = Join-Path $PSScriptRoot 'scroll_lang_extra.json'
if (Test-Path -LiteralPath $extraPath) {
    $extraText = [System.IO.File]::ReadAllText($extraPath, [System.Text.Encoding]::UTF8)
    $split = $extraText.IndexOf('"en_us"')
    $zhPart = $extraText
    $enPart = ''
    if ($split -gt 0) { $zhPart = $extraText.Substring(0, $split); $enPart = $extraText.Substring($split) }
    foreach ($k in (Parse-Lang $zhPart).Keys) { $zh[$k] = (Parse-Lang $zhPart)[$k] }
    if ($enPart -ne '') { foreach ($k in (Parse-Lang $enPart).Keys) { $en[$k] = (Parse-Lang $enPart)[$k] } }
    Write-Host '  extra keys merged from scroll_lang_extra.json'
}

if ($Check) {
    Write-Host "  CHECK mode: nothing written. total $total chars."
    exit 0
}

Write-Lang $langZh $zh
Write-Lang $langEn $en
Write-Host "  wrote $($ids.Count) scroll text(s), total $total chars -> $langZh"
