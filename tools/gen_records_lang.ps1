# gen_records_lang.ps1 -- build the in-game reading text of the 5 main-story
# record papers (the backstory sheet + the four main-story segments) out of the
#
# writer's summary document.
# single source of truth. Re-typing it into lang by hand WILL drift.
# Run this after they edit the doc, then rebuild the jar.
#
# WHY: the text belongs to the writing topic and that doc is the
# (the doc is a handoff summary, so a few sentences in it are addressed to the
#  team rather than to the player; those fragments are listed in
#  tools\records_lang_extra.json -> "strip" and removed here. Everything removed
#  is printed, so it is never silent.)
#
# ASCII ONLY on purpose (repo rule: tools/*.ps1 must contain zero non-ASCII
# bytes; PowerShell 5.1 reads a BOM-less .ps1 as ANSI and would mangle CJK).
# That is why the doc path is found structurally and all Chinese text lives in
# tools\records_lang_extra.json.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File tools\gen_records_lang.ps1 -Dump
#   powershell -ExecutionPolicy Bypass -File tools\gen_records_lang.ps1
#   powershell -ExecutionPolicy Bypass -File tools\gen_records_lang.ps1 -Check

param(
    [string]$Doc = '',
    [switch]$Dump,
    [switch]$Check
)

$ErrorActionPreference = 'Stop'

$repo = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrEmpty($repo)) { $repo = (Get-Location).Path }

# the five papers, in reading order (id -> which section of the doc)
$ids = @('zhengshi_qianqing', 'zhengshi_1', 'zhengshi_2', 'zhengshi_3', 'zhengshi_4')

$langZh = Join-Path $repo 'src\main\resources\assets\tnc\lang\zh_cn.json'
$langEn = Join-Path $repo 'src\main\resources\assets\tnc\lang\en_us.json'
$extraPath = Join-Path $PSScriptRoot 'records_lang_extra.json'

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

function Read-Lang([string]$path) {
    if (-not (Test-Path -LiteralPath $path)) { return @{} }
    return (Parse-Lang ([System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)))
}

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

# ------------------------------------------------------- side file (names + strips)
$names = @{}
$descs = @{}
$strip = @()
$docFromExtra = ''
if (Test-Path -LiteralPath $extraPath) {
    $ex = [System.IO.File]::ReadAllText($extraPath, [System.Text.Encoding]::UTF8)
    $docBlock = [regex]::Match($ex, '"doc"\s*:\s*"((?:[^"\\]|\\.)*)"')
    if ($docBlock.Success) { $docFromExtra = (Unesc $docBlock.Groups[1].Value) }
    # names: "<id>": "<display name>"
    $nameBlock = [regex]::Match($ex, '"names"\s*:\s*\{(.*?)\}', 'Singleline')
    if ($nameBlock.Success) {
        foreach ($m in [regex]::Matches($nameBlock.Groups[1].Value, '"([^"]+)"\s*:\s*"((?:[^"\\]|\\.)*)"')) {
            $names[$m.Groups[1].Value] = (Unesc $m.Groups[2].Value)
        }
    }
    $stripBlock = [regex]::Match($ex, '"strip"\s*:\s*\[(.*?)\]', 'Singleline')
    if ($stripBlock.Success) {
        foreach ($m in [regex]::Matches($stripBlock.Groups[1].Value, '"((?:[^"\\]|\\.)*)"')) {
            $strip += (Unesc $m.Groups[1].Value)
        }
    }
}

# ------------------------------------------------------------------ find the doc
# The path comes from the side file (Chinese is fine there) so this script stays
# ASCII-only; -Doc overrides it.
if ([string]::IsNullOrEmpty($Doc) -and $docFromExtra -ne '') {
    $Doc = Join-Path $repo $docFromExtra
}
if ([string]::IsNullOrEmpty($Doc)) {
    $dirs = Get-ChildItem -Path $repo -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -notmatch '^[\x20-\x7E]+$' }
    $cands = @()
    foreach ($d in $dirs) {
        foreach ($f in Get-ChildItem -Path $d.FullName -Recurse -File -Filter *.md -ErrorAction SilentlyContinue) {
            $t = [System.IO.File]::ReadAllText($f.FullName, [System.Text.Encoding]::UTF8)
            # the summary doc: one '## ' section plus four '### ' segments, and it
            # is the only such file in that folder
            $h2 = ([regex]::Matches($t, '(?m)^## ')).Count
            $h3 = ([regex]::Matches($t, '(?m)^### ')).Count
            if ($h2 -ge 2 -and $h3 -ge 4 -and $t -notmatch '(?m)^#### ') { $cands += $f.FullName }
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
# section 0 = the first '## ' section (backstory); sections 1..4 = '### ' segments
$lines = [System.IO.File]::ReadAllLines($Doc, [System.Text.Encoding]::UTF8)
$sections = @{}
$cur = -1
$collecting = $false
foreach ($l in $lines) {
    if ($l.StartsWith('## ')) {
        if ($collecting -and $cur -ge 1) { $collecting = $false }
        if ($cur -lt 0) {
            # the first '## ' after the title is the backstory section
            $cur = 0
            $collecting = $true
            $sections[0] = New-Object System.Collections.Generic.List[string]
        }
        continue
    }
    if ($l.StartsWith('### ')) {
        if ($cur -eq 0) { $cur = 1 } else { $cur++ }
        if ($cur -ge 1 -and $cur -le 4) {
            $collecting = $true
            $sections[$cur] = New-Object System.Collections.Generic.List[string]
        } else {
            $collecting = $false
        }
        continue
    }
    if ($collecting) { $sections[$cur].Add($l) }
}

# ------------------------------------------------------------------- clean up
$result = @{}
$i = 0
foreach ($key in @(0, 1, 2, 3, 4)) {
    if (-not $sections.ContainsKey($key)) { throw "section $key not found in the doc" }
    $text = ($sections[$key] -join "`n")
    # raw text of the whole section (kept for the Dump report)
    $raw = $text
    foreach ($frag in $strip) {
        if ($frag -ne '' -and $text.Contains($frag)) {
            $text = $text.Replace($frag, '')
            Write-Host ("  stripped from {0}: {1}" -f $ids[$i], $frag)
        }
    }
    $clean = New-Object System.Collections.Generic.List[string]
    $prevBlank = $true
    foreach ($l in ($text -split "`n")) {
        $s = $l
        if ($s.StartsWith('>')) { $s = $s.Substring(1) }
        if ($s.StartsWith(' ')) { $s = $s.TrimStart(' ') }
        $s = $s.Replace('**', '')
        if ($s.EndsWith('>')) { $s = $s.TrimEnd('>').TrimEnd() }
        if ($s.StartsWith('---')) { $s = '' }
        $s = $s.TrimEnd()
        # a dagger marks an author-note lead-in in the source doc
        if ($s.StartsWith([string][char]0x2020)) { continue }
        if ($s -eq '') {
            if (-not $prevBlank) { $clean.Add(''); $prevBlank = $true }
        } else {
            $clean.Add($s)
            $prevBlank = $false
        }
    }
    while ($clean.Count -gt 0 -and $clean[$clean.Count - 1] -eq '') { $clean.RemoveAt($clean.Count - 1) }
    $result[$ids[$i]] = ($clean -join "`n")
    if ($Dump) {
        Write-Host ("=== {0} ({1} chars) ===" -f $ids[$i], $result[$ids[$i]].Length)
        Write-Host $result[$ids[$i]]
        Write-Host ''
    }
    $i++
}

if ($Dump) { Write-Host '  DUMP mode: nothing written.'; exit 0 }
if ($Check) {
    $total = 0
    foreach ($id in $ids) { $total += $result[$id].Length }
    Write-Host "  CHECK mode: nothing written. total $total chars."
    exit 0
}

# --------------------------------------------------------- write lang
$zh = Read-Lang $langZh
$en = Read-Lang $langEn
$total = 0
foreach ($id in $ids) {
    $zh["scroll.tnc.$id"] = $result[$id]
    $en["scroll.tnc.$id"] = '(A record sheet from the main story - no translation in this build.)'
    if ($names.ContainsKey($id)) { $zh["item.tnc.$id"] = $names[$id] }
    $total += $result[$id].Length
    Write-Host ("  {0,-20} {1,4} chars" -f $id, $result[$id].Length)
}
Write-Lang $langZh $zh
Write-Lang $langEn $en
Write-Host "  wrote 5 record texts, total $total chars -> $langZh"
