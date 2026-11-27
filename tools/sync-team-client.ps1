param(
    [string]$GameDir,
    [switch]$VerifyOnly,
    [ValidateRange(0, 1440)][int]$WaitMinutes = 60
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_common.ps1')

function Get-RunningGameDirs {
    foreach ($proc in Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'") {
        # Launch arguments can contain access tokens. Extract only the instance directory.
        $match = [regex]::Match([string]$proc.CommandLine, '(?i)(?:^|\s)--gameDir(?:=|\s+)(?:"([^"]+)"|(\S+))')
        if ($match.Success) {
            $path = ($match.Groups[1].Value + $match.Groups[2].Value)
            if ([IO.Path]::IsPathRooted($path)) { [IO.Path]::GetFullPath($path).TrimEnd('\','/') }
        }
    }
}

function Resolve-TeamInstance([string]$explicit) {
    $workPack = Find-TncWorkPack
    if (!$workPack) { throw 'Cannot identify base pack in this checkout.' }
    $name = Split-Path $workPack -Leaf
    if (!$explicit -and $env:TNC_LIVE_ROOT) { $explicit = Join-Path $env:TNC_LIVE_ROOT $name }
    if ($explicit) {
        $dir = (Get-Item -LiteralPath $explicit).FullName
    } else {
        $active = @(Get-RunningGameDirs | Where-Object { (Split-Path $_ -Leaf) -eq $name } | Sort-Object -Unique)
        if ($active.Count -eq 1) { $dir = $active[0] }
        else {
            $candidates = @(
                $appDataVersion = Join-Path $env:APPDATA ".minecraft\versions\$name"
                if (Test-Path -LiteralPath $appDataVersion) { $appDataVersion }
                foreach ($drive in Get-PSDrive -PSProvider FileSystem | Where-Object { $_.Name.Length -eq 1 }) {
                    foreach ($place in @('.minecraft','*\.minecraft','*\*\.minecraft','*\*\*\.minecraft')) {
                        $pattern = Join-Path $drive.Root "$place\versions\$name"
                        Get-Item -Path $pattern -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }
                    }
                }
            )
            $candidates = @($candidates | Where-Object { Test-Path -LiteralPath (Join-Path $_ 'mods') -PathType Container } | Sort-Object -Unique)
            if ($candidates.Count -ne 1) {
                throw "Expected one installed PCL base pack. Candidates: $($candidates -join ', '). Re-run with -GameDir from the actual PCL profile."
            }
            $dir = $candidates[0]
        }
    }
    if ($dir -eq $workPack -or !(Test-Path -LiteralPath (Join-Path $dir 'mods') -PathType Container) -or
        !(Test-Path -LiteralPath (Join-Path $dir 'config') -PathType Container)) {
        throw "Expected an installed gameDir with mods and config, separate from the source mirror: $dir"
    }
    return $dir.TrimEnd('\','/')
}

function Confirm-ReleaseSource($lock) {
    # Tree IDs work for shallow clones. Documentation-only commits may follow the build.
    foreach ($entry in $lock.sourceTrees.PSObject.Properties) {
        $actual = & git -C $TncRepoRoot rev-parse --verify "HEAD:$($entry.Name)"
        if ($LASTEXITCODE -ne 0 -or $actual -ne $entry.Value) {
            throw "The pinned runtime predates the pulled source ($($entry.Name)). Update the release lock before installing this build."
        }
    }
}

function Sync-ModernFix([string]$dir) {
    $file = Join-Path $dir 'config\modernfix-mixins.properties'
    $exists = [IO.File]::Exists($file)
    $content = if ($exists) { [IO.File]::ReadAllText($file) } else { '' }
    $key = 'mixin.perf.dynamic_resources=true'
    $pattern = '(?m)^[ \t\f]*mixin\.perf\.dynamic_resources(?:(?:[ \t\f]*[=:][ \t\f]*|[ \t\f]+)[^\r\n]*)?(?:\r?\n|\z)'
    $hits = [regex]::Matches($content, $pattern)
    if ($hits.Count -eq 1 -and $hits[0].Value.Trim() -match '^mixin\.perf\.dynamic_resources\s*=\s*true$') { return }
    if ($VerifyOnly) { throw "Expected exactly one $key in $file" }
    $nl = if ($content.Contains("`n") -and !$content.Contains("`r`n")) { "`n" } else { "`r`n" }
    $updated = [regex]::Replace($content, $pattern, '')
    if ($updated.Length -and !$updated.EndsWith("`n")) { $updated += $nl }
    $updated += $key + $nl
    $id = [guid]::NewGuid().ToString('N')
    $stage = "$file.$id.tmp"
    try {
        [IO.File]::WriteAllText($stage, $updated, [Text.UTF8Encoding]::new($false))
        if ($exists) {
            $backup = Join-Path $dir "backups\tnc-team\$id\modernfix-mixins.properties"
            [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($backup))
            if ([IO.File]::ReadAllText($file) -cne $content) { throw "Config changed during sync: $file" }
            [IO.File]::Replace($stage, $file, $backup)
        } else { [IO.File]::Move($stage, $file) }
    } finally { if ([IO.File]::Exists($stage)) { [IO.File]::Delete($stage) } }
    $got = [regex]::Matches([IO.File]::ReadAllText($file), $pattern)
    if ($got.Count -ne 1 -or $got[0].Value.Trim() -ne $key) { throw "ModernFix verification failed: $file" }
}

function Move-OldWarriorOverlays([string]$dir) {
    $overlays = @{
        'tnc-warrior-arsenal.zip' = @(
            '09028EE7C5242D379A5B313C4B7178194810E6A26F04DD7C8BEE8D0DA7C9C635',
            '7E7C7176E26F6BFD4E17215361C4E2DB54B0A0D09D6B351A44D32B1CB6040045',
            '1AB38A80322C89C30F76DD1BB3F3EF2D8E4C358F22EC4B40550C5F49E1260534'
        )
        'tnc-warrior-inventory-icons.zip' = @('482C57E6601C6C966DDD6262A5C162FE3796A18C00E3B0F3DFAD234185BD5A94')
    }
    foreach ($name in $overlays.Keys) {
        $path = Join-Path $dir "config/openloader/resources/$name"
        if (![IO.File]::Exists($path)) { continue }
        if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -notin $overlays[$name]) {
            Write-Warning "A locally modified weapon overlay remains enabled: $name"
            continue
        }
        if ($VerifyOnly) { throw "Obsolete team weapon overlay is still active: $path" }
        $id = [guid]::NewGuid().ToString('N')
        $backup = Join-Path $dir "backups/tnc-team/$id/$name"
        [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($backup))
        [IO.File]::Move($path, $backup)
    }
}

function Sync-AcademyQuestChapter([string]$dir) {
    if (!(Get-ChildItem -LiteralPath (Join-Path $dir 'mods') -Filter 'ftb-quests*.jar' -File)) { return }
    $source = Join-Path $TncRepoRoot 'questbook/ftbquests/chapters/tnc_sky_academy.snbt'
    $target = Join-Path $dir 'config/ftbquests/quests/chapters/tnc_sky_academy.snbt'
    $wanted = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash
    $previous = if ([IO.File]::Exists($target)) { (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash }
    if ($previous -eq $wanted) { return }
    if ($VerifyOnly) { throw "Sky Academy quest chapter is out of sync: $target" }
    [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target))
    $id = [guid]::NewGuid().ToString('N')
    $stage = "$target.$id.tmp"
    try {
        [IO.File]::Copy($source, $stage)
        if ((Get-FileHash -LiteralPath $stage -Algorithm SHA256).Hash -ne $wanted) { throw "Staging failed: $target" }
        if ($previous) {
            $backup = Join-Path $dir "backups/tnc-team/$id/tnc_sky_academy.snbt"
            [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($backup))
            if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $previous) { throw "Chapter changed during sync: $target" }
            [IO.File]::Replace($stage, $target, $backup)
        } else { [IO.File]::Move($stage, $target) }
    } finally { if ([IO.File]::Exists($stage)) { [IO.File]::Delete($stage) } }
    if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $wanted) { throw "Quest chapter verification failed: $target" }
}

$lock = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $PSScriptRoot 'tnc-client-release.lock.json') | ConvertFrom-Json
Confirm-ReleaseSource $lock
$dir = Resolve-TeamInstance $GameDir
Write-Host "PCL gameDir: $dir"
if (!(Get-ChildItem -LiteralPath (Join-Path $dir 'mods') -Filter 'modernfix*.jar' -File)) {
    throw 'ModernFix is missing. This is not the expected installed base pack.'
}
if (!$VerifyOnly) {
    $deadline = (Get-Date).AddMinutes($WaitMinutes)
    $waiting = $false
    while (@(Get-RunningGameDirs) -contains $dir) {
        if ((Get-Date) -ge $deadline) { throw 'Target Minecraft instance is still running. Resume the same sync after the game closes.' }
        if (!$waiting) { Write-Host 'Waiting for this Minecraft instance to exit...'; $waiting = $true }
        Start-Sleep -Seconds 5
    }
    & (Join-Path $PSScriptRoot 'preserve-local-pack-assets.ps1') -GameDir $dir
    if (!$?) { throw 'Local pack asset migration failed before replacing the old TN-C jar.' }
}
$installer = Join-Path $PSScriptRoot 'sync-skyhaven-client.ps1'
& $installer -GameDir $dir -VerifyOnly:$VerifyOnly
if (!$?) { throw 'Client install or verification failed.' }
& (Join-Path $PSScriptRoot 'sync-team-wands.ps1') -GameDir $dir -VerifyOnly:$VerifyOnly
if (!$?) { throw 'Team wand resource verification failed.' }
Sync-ModernFix $dir
Move-OldWarriorOverlays $dir
Sync-AcademyQuestChapter $dir
Write-Host "Verified team runtime $($lock.sourceCommit) SHA256 $($lock.entry.sha256), dependencies, wands, ModernFix and the Academy chapter if FTB Quests is installed."
