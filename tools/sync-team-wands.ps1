[CmdletBinding()]
param([Parameter(Mandatory)][string]$GameDir, [switch]$VerifyOnly)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
$game = (Get-Item -LiteralPath $GameDir).FullName
$pack = Join-Path $game 'config/openloader/resources/TN-C'
$lock = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'tnc-client-release.lock.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if ($lock.entry.id -ne 'tnc' -or $lock.entry.name -ne 'tnc-1.0.0.jar' -or $lock.entry.sha256 -notmatch '^[a-f0-9]{64}$') {
    throw 'Invalid first-party release entry.'
}
$jar = Join-Path $game 'mods/tnc-1.0.0.jar'
$hasher = [Security.Cryptography.SHA256]::Create()
function Get-StreamHash([IO.Stream]$stream) {
    return [BitConverter]::ToString($hasher.ComputeHash($stream)).Replace('-','')
}

# An external junction under the managed pack may be a personal asset collection.
function Assert-LocalPath([string]$path) {
    for ($p = $path; $p -ne $game; $p = [IO.Path]::GetDirectoryName($p)) {
        if (!$p) { throw "Path outside target instance: $path" }
        if (![IO.File]::Exists($p) -and ![IO.Directory]::Exists($p)) { continue }
        if ([IO.File]::GetAttributes($p) -band [IO.FileAttributes]::ReparsePoint) {
            throw "Review linked pack or backup path before synchronizing: $p"
        }
    }
}

$mismatches = [Collections.Generic.List[object]]::new()
$moved = [Collections.Generic.List[object]]::new()
$backup = Join-Path $game ('backups/tnc-team-wands/' + (Get-Date -Format yyyyMMdd-HHmmss) + '-' + [guid]::NewGuid().ToString('N'))
$matched = 0
$bundle = $null
$archive = $null
try {
    $bundle = [IO.File]::Open($jar, 'Open', 'Read', 'Read')
    if ((Get-StreamHash $bundle) -ne $lock.entry.sha256) {
        throw "Install the pinned TN-C JAR before checking OpenLoader wands: $jar"
    }
    $bundle.Position = 0
    $archive = [IO.Compression.ZipArchive]::new($bundle, [IO.Compression.ZipArchiveMode]::Read, $true)
    Assert-LocalPath $pack
    $paths = foreach ($element in 'water','fire','wind','earth','light','dark','lightning') {
        foreach ($tier in 1..5) {
            $id = "${element}_wand_$tier"
            "models/item/$id.json"
            "models/item/wands/held/$id.json"
            "models/item/wands/icons/$id.json"
            "textures/item/wands/$id.png"
            "textures/item/wands/held/$id.png"
            "textures/item/wands/held/${id}_emission.png"
        }
    }
    # Fire's highest tier also draws two dedicated animation atlases from this pack.
    $paths += 'textures/effect/cyan_fire_flow.png','textures/effect/cyan_fire_body.png'
    foreach ($relative in $paths) {
        $name = "assets/tnc/$relative"
        $entry = $archive.GetEntry($name)
        if ($null -eq $entry) { throw "Pinned TN-C JAR is missing $name" }
        $target = Join-Path $pack $name
        if (![IO.File]::Exists($target)) { continue }
        Assert-LocalPath $target
        $stream = $entry.Open()
        try { $expected = Get-StreamHash $stream } finally { $stream.Dispose() }
        $current = (Get-FileHash -LiteralPath $target).Hash
        if ($current -eq $expected) { $matched++; continue }
        $mismatches.Add([pscustomobject]@{Target=$target; Old=(Join-Path $backup $name); Hash=$current})
    }
    if ($mismatches.Count -gt 0) {
        if ($VerifyOnly) { throw "Stale team OpenLoader wand overrides ($($mismatches.Count)). First: $($mismatches[0].Target)" }
        Assert-LocalPath $backup
        foreach ($process in Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'") {
            if ($process.CommandLine -match '(?i)(?:^|\s)--gameDir(?:\s+|=)(?:"([^"]+)"|([^\s"]+))') {
                if ([IO.Path]::GetFullPath($Matches[1]+$Matches[2]).TrimEnd('\','/') -eq $game.TrimEnd('\','/')) {
                    throw 'Close this Minecraft instance before changing its OpenLoader wand resources.'
                }
            }
        }
        foreach ($change in $mismatches) {
            if ((Get-FileHash -LiteralPath $change.Target).Hash -ne $change.Hash) { throw "Concurrent edit: $($change.Target)" }
            [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($change.Old))
            [IO.File]::Move($change.Target,$change.Old)
            $moved.Add($change)
            if ((Get-FileHash -LiteralPath $change.Old).Hash -ne $change.Hash -or [IO.File]::Exists($change.Target)) {
                throw "Could not verify retired override: $($change.Target)"
            }
        }
        Write-Host "Previous team overrides saved to $backup"
    }
    Write-Host "Verified 35 wands ($($paths.Count) JAR or team OpenLoader resources). Matching overrides: $matched. Retired: $($moved.Count)."
} catch {
    for ($i = $moved.Count-1; $i -ge 0; $i--) {
        $change = $moved[$i]
        if ([IO.File]::Exists($change.Target)) {
            Write-Warning "Concurrent file retained at $($change.Target). Previous version: $($change.Old)"
        } else { [IO.File]::Move($change.Old,$change.Target) }
    }
    throw
} finally {
    if ($archive) { $archive.Dispose() }
    if ($bundle) { $bundle.Dispose() }
    $hasher.Dispose()
}
