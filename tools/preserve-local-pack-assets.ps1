[CmdletBinding()]
param([Parameter(Mandatory)][string]$GameDir)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$game = (Get-Item -LiteralPath $GameDir).FullName
if (-not (Test-Path -LiteralPath (Join-Path $game 'mods') -PathType Container)) { throw "Invalid game directory: $game" }
$repo = Split-Path $PSScriptRoot -Parent
$lock = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'skyhaven-local-assets.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$pending = @{}
$staged = @{}
$destinations = @{}
$added = 0
$custom = 0
$legacyCustom = @{}
foreach ($item in $lock.PSObject.Properties) {
    $key = $item.Name
    if ($key -notmatch '^(data/tnc/(structures/dungeon_gates|dungeon_gate_assets)|assets/tnc/sounds/(music|casino|tavern))/[a-z0-9_/]+\.(nbt|json|ogg)$') { throw "Unexpected local manifest entry: $key" }
    $target = Join-Path (Join-Path $game 'kubejs') $key
    $destinations[$key] = $target
    if (Test-Path -LiteralPath $target -PathType Leaf) {
        if ((Get-FileHash -LiteralPath $target).Hash -ne $item.Value) { $custom++ }
    } else {
        $pending[$key] = $item.Value
    }
}

function Save-VerifiedStream([string]$Key, [System.IO.Stream]$InputStream, [bool]$FromActiveMod = $false) {
    $target = $destinations[$Key]
    [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target))
    $part = "$target.$([guid]::NewGuid().ToString('N')).tmp"
    try {
        $output = [IO.File]::Open($part, 'CreateNew', 'Write', 'None')
        try { $InputStream.CopyTo($output) } finally { $output.Dispose() }
        $observed = (Get-FileHash -LiteralPath $part).Hash
        if ($FromActiveMod -and $observed -ne $pending[$Key]) { $legacyCustom[$Key] = $observed }
        if ($FromActiveMod -or $observed -eq $pending[$Key]) {
            $staged[$Key] = $part
            $pending.Remove($Key)
            $part = $null
        }
    } finally {
        if ($part -and [IO.File]::Exists($part)) { [IO.File]::Delete($part) }
    }
}

function Read-LocalJar([IO.FileInfo]$File, [bool]$RequireTnc = $false, [bool]$FromActiveMod = $false) {
    try { $zip = [IO.Compression.ZipFile]::OpenRead($File.FullName) }
    catch [IO.InvalidDataException] {
        Write-Warning "Skipping unreadable archive: $($File.Name); existing files and backups are preserved."
        return
    }
    try {
        if ($RequireTnc) {
            $toml = $zip.GetEntry('META-INF/mods.toml')
            if ($null -eq $toml) { return }
            $reader = [IO.StreamReader]::new($toml.Open())
            try { $meta = $reader.ReadToEnd() } finally { $reader.Dispose() }
            $declaresTnc = $false
            foreach ($section in [regex]::Matches($meta, '(?ms)^\s*\[\[mods\]\][^\S\r\n]*(?:#[^\r\n]*)?\r?\n(?<body>.*?)(?=^\s*\[|\z)')) {
                if ($section.Groups['body'].Value -match '(?m)^\s*modId\s*=\s*["'']tnc["'']') { $declaresTnc = $true; break }
            }
            if (!$declaresTnc) { return }
        }
        foreach ($key in @($pending.Keys)) {
            $entry = $zip.GetEntry($key)
            if ($null -eq $entry) { continue }
            $stream = $entry.Open()
            try { Save-VerifiedStream $key $stream $FromActiveMod } finally { $stream.Dispose() }
        }
    } catch [IO.InvalidDataException] {
        Write-Warning "Skipping unreadable entries in $($File.Name); trying other local sources."
    } finally { $zip.Dispose() }
}

try {
    $liveJar = Join-Path $game 'mods/tnc-1.0.0.jar'
    if ($pending.Count -and [IO.File]::Exists($liveJar)) { Read-LocalJar (Get-Item -LiteralPath $liveJar) $true $true }
    if ($pending.Count) {
        foreach ($jar in Get-ChildItem -LiteralPath (Join-Path $game 'mods') -File -Filter '*.jar' | Sort-Object Name) {
            if ($jar.Name -ine 'tnc-1.0.0.jar') { Read-LocalJar $jar $true $true }
            if (!$pending.Count) { break }
        }
    }
    foreach ($key in @($pending.Keys)) {
        foreach ($folder in @('src/main/resources','work/dungeon-gate-resources')) {
            $source = Join-Path (Join-Path $repo $folder) $key
            if ([IO.File]::Exists($source)) {
                $stream = [IO.File]::OpenRead($source)
                try { Save-VerifiedStream $key $stream } finally { $stream.Dispose() }
                if (!$pending.ContainsKey($key)) { break }
            }
        }
    }
    $backups = Join-Path $game 'backups'
    if ($pending.Count -and [IO.Directory]::Exists($backups)) {
        $jars = Get-ChildItem -LiteralPath $backups -File -Recurse -Filter '*.jar' |
            Sort-Object LastWriteTime -Descending
        foreach ($jar in $jars) {
            try { Read-LocalJar $jar $true } catch [IO.InvalidDataException] { Write-Warning "Skipping unreadable backup: $($jar.FullName)" }
            if (!$pending.Count) { break }
        }
    }
    $missingGates = @($pending.Keys | Where-Object { $_ -like 'data/*' }).Count
    foreach ($key in @($staged.Keys)) {
        # Keep a complete set of four gates: a partial manifest refers to absent templates.
        if ($missingGates -and $key -like 'data/*') { continue }
        $target = $destinations[$key]
        if (![IO.File]::Exists($target)) {
            try { [IO.File]::Move($staged[$key], $target); $added++ }
            catch [IO.IOException] { if (![IO.File]::Exists($target)) { throw } }
        }
    }
    if ($missingGates) { Write-Warning "Private gate assets unavailable ($missingGates missing). Built-in starter gates remain available." }
    $missingMusic = @($pending.Keys | Where-Object { $_ -like 'assets/*' }).Count
    if ($missingMusic) { Write-Warning "$missingMusic optional soundtrack file(s) unavailable locally." }
    if ($legacyCustom.Count) { Write-Warning ("Kept customized assets from the previous installation: " + (($legacyCustom.Keys | Sort-Object) -join ', ')) }
    Write-Host "Local resources recovered: $added. Existing customized files kept: $custom."
} finally {
    foreach ($part in $staged.Values) { if ([IO.File]::Exists($part)) { [IO.File]::Delete($part) } }
}
