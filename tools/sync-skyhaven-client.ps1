# Synchronize the pinned Forge client profile. Run with Windows PowerShell 5.1 or PowerShell 7.
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$GameDir,
    [switch]$VerifyOnly
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
Add-Type -AssemblyName System.IO.Compression.FileSystem
$utf8 = New-Object Text.UTF8Encoding($false)
$profile = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'skyhaven-client.lock.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$release = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'tnc-client-release.lock.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$entries = @($profile.entries) + @($release.entry)
$root = (Get-Item -LiteralPath $GameDir).FullName
if (-not [IO.Directory]::Exists((Join-Path $root 'mods'))) { throw "Expected an existing game directory with mods: $root" }
$managedNames = @{}
$modIds = @{}
foreach ($entry in $entries) {
    if ($entry.folder -notin 'mods','shaderpacks' -or [IO.Path]::GetFileName($entry.name) -cne $entry.name -or
        $entry.name -notmatch '^[A-Za-z0-9+_.-]+\.(jar|zip)$' -or $entry.sha256 -notmatch '^[a-fA-F0-9]{64}$' -or
        $entry.url -notmatch '^https://' -or -not $entry.id -or $managedNames.ContainsKey($entry.name)) {
        throw "Invalid client lock entry: $($entry.name)"
    }
    $managedNames[$entry.name] = $true
    if ($entry.folder -eq 'mods') { $modIds[$entry.id] = $true }
}

$stateDir = Join-Path $root '.tnc-client-sync'
$receipt = Join-Path $stateDir 'profile-defaults.txt'
$runKey = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N')
$staging = Join-Path $stateDir $runKey
$backup = Join-Path (Join-Path $root 'backups/tnc-client-sync') $runKey
$changes = [Collections.ArrayList]::new()
$applied = [Collections.ArrayList]::new()

function Get-Digest($Path) {
    if ([IO.File]::Exists($Path)) { return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash }
    return ''
}

function Read-ZipText($Archive, $Name) {
    $member = $Archive.GetEntry($Name)
    if ($null -eq $member) { return '' }
    $reader = [IO.StreamReader]::new($member.Open())
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}

# Detect registered mod IDs even if somebody has renamed a jar. Prefix matching
# alone would accidentally retire add-ons such as Connector Extras and VoxyGen.
function Find-ManagedId($Path) {
    $archive = [IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $manifest = Read-ZipText $archive 'META-INF/MANIFEST.MF'
        if ($manifest -match '(?im)^Automatic-Module-Name: org\.sinytra\.connector\s*$') { return 'connector' }
        $toml = Read-ZipText $archive 'META-INF/mods.toml'
        foreach ($section in [regex]::Matches($toml,'(?ims)^[ \t]*\[\[mods\]\][^\r\n]*\r?\n.*?(?=^[ \t]*\[|\z)')) {
            if ($section.Value -match '(?im)^\s*modId\s*=\s*["'']([^"'']+)["'']' -and $modIds.ContainsKey($Matches[1])) {
                return $Matches[1]
            }
        }
        $fabric = Read-ZipText $archive 'fabric.mod.json'
        if ($fabric) {
            # Some third-party metadata contains comments accepted by its loader.
            try { $id = ($fabric | ConvertFrom-Json).id } catch { $id = $null }
            if ($id -and $modIds.ContainsKey($id)) { return $id }
            if ($id -eq 'fabric-api') { return 'fabric_api' }
        }
    } finally { $archive.Dispose() }
}

function Assert-GameClosed {
    $java = Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'"
    foreach ($item in $java) {
        if ($item.CommandLine -match '(?i)--gameDir(?:\s+|=)(?:"([^"]+)"|([^\s"]+))') {
            $dir = ($Matches[1] + $Matches[2]).Replace('/','\').TrimEnd('\')
            if ($dir -ieq $root.Replace('/','\').TrimEnd('\')) { throw "Exit Minecraft in $root and retry. PID $($item.ProcessId)." }
        }
    }
}

# Resolve the short-lived CDN URL once per attempt; the lock always records the
# permanent GitHub URL. Outside hosts and old clients keep the original route.
# curl.exe does not read the Windows user proxy automatically.
function Get-ReleaseCurlProxy([uri]$address) {
    if ($env:HTTPS_PROXY -or $env:ALL_PROXY) { return }
    try {
        $systemProxy = [Net.WebRequest]::GetSystemWebProxy()
        if ($systemProxy -and !$systemProxy.IsBypassed($address)) {
            $proxy = $systemProxy.GetProxy($address)
            if ($proxy -and $proxy -ne $address -and $proxy.Scheme -in 'http','https') { return $proxy.AbsoluteUri }
        }
    } catch {
        # Let curl use its normal environment and direct connection when proxy discovery fails.
    }
}
function Get-GitHubAsset($Entry, $Target) {
    $length = [long]$Entry.size
    $step = if ($length -gt 64MB) { 2MB } else { 512KB }
    $partDir = Join-Path (Join-Path $stateDir 'partial') $Entry.sha256
    [void][IO.Directory]::CreateDirectory($partDir)
    $url = $Entry.url
    $headProxy = Get-ReleaseCurlProxy ([uri]$url)
    $headOptions = if ($headProxy) { @('--proxy', $headProxy) } else { @() }
    $redirect = & curl.exe @headOptions --silent --head --ssl-revoke-best-effort --connect-timeout 10 --max-time 20 --output NUL --write-out '%{redirect_url}' "$($Entry.url)?tnc=$([DateTime]::UtcNow.Ticks)"
    if ($LASTEXITCODE -eq 0 -and $redirect -cmatch '^https://release-assets\.githubusercontent\.com/[^"\s\\]+$') {
        $url = $redirect
    }

    $rangeProxy = Get-ReleaseCurlProxy ([uri]$url)
    $pieces = [Collections.Generic.List[object]]::new()
    for ($first = 0L; $first -lt $length; $first += $step) {
        $last = [Math]::Min($first + $step - 1, $length - 1)
        $part = "$($Entry.name).$first.part"
        $pieces.Add(@{ File=(Join-Path $partDir $part); Name=$part; First=$first; Last=$last; Count=($last-$first+1) })
    }
    $configPath = Join-Path $staging 'ranges.txt'
    $stalled = 0
    for ($round=0; $round -lt 12; $round++) {
        $options = [Collections.Generic.List[string]]::new()
        $pending = @()
        foreach ($part in $pieces) {
            $have = if ([IO.File]::Exists($part.File)) { (Get-Item -LiteralPath $part.File).Length } else { 0L }
            if ($have -eq $part.Count) { continue }
            if ($have -gt $part.Count) { [IO.File]::Delete($part.File); $have = 0L }
            $part.From = $part.First + $have
            $pending += $part
            $delta = "$($part.Name).new"
            $headers = "$($part.Name).headers"
            [IO.File]::Delete((Join-Path $staging $delta))
            [IO.File]::Delete((Join-Path $staging $headers))
            if ($options.Count) { $options.Add('next') }
            if ($rangeProxy) { $options.Add("proxy = `"$rangeProxy`"") }
            foreach ($line in @('location','fail','silent','show-error','ssl-revoke-best-effort','connect-timeout = 20','speed-limit = 1024','speed-time = 35','max-time = 120',
                                "range = `"$($part.From)-$($part.Last)`"","dump-header = `"$headers`"","output = `"$delta`"","url = `"$url`"")) {
                $options.Add($line)
            }
        }
        if (-not $options.Count) { break }
        [IO.File]::WriteAllLines($configPath,$options,$utf8)
        Push-Location -LiteralPath $staging
        try {
            # A reset can still leave a useful prefix. Check each actual HTTP
            # range below, even when curl reports a failed parallel transfer.
            & curl.exe --parallel --parallel-max 8 --config $configPath
        } finally { Pop-Location }
        $added = 0L
        foreach ($part in $pending) {
            $delta = Join-Path $staging "$($part.Name).new"
            $headers = Join-Path $staging "$($part.Name).headers"
            if (-not ([IO.File]::Exists($delta) -and [IO.File]::Exists($headers))) { continue }
            $size = (Get-Item -LiteralPath $delta).Length
            $expected = "bytes $($part.From)-$($part.Last)/$length"
            $range = [regex]::Matches([IO.File]::ReadAllText($headers),'(?im)^Content-Range:[ \t]*([^\r\n]+)')
            if (-not $range.Count -or $range[$range.Count-1].Groups[1].Value.Trim() -ne $expected -or
                $size -le 0 -or $size -gt ($part.Last-$part.From+1)) { continue }
            $to = [IO.File]::Open($part.File,[IO.FileMode]::Append)
            try {
                $from = [IO.File]::OpenRead($delta)
                try { $from.CopyTo($to) } finally { $from.Dispose() }
            } finally { $to.Dispose() }
            $added += $size
            [IO.File]::Delete($delta)
        }
        if ($added) {
            $stalled = 0
        } else {
            $stalled++
            if ($stalled -ge 2) { throw 'No progress fetching HTTP ranges.' }
            Start-Sleep -Seconds 2
        }
    }
    $output = [IO.File]::Create($Target)
    try {
        foreach ($part in $pieces) {
            if ((Get-Item -LiteralPath $part.File).Length -ne $part.Count) { throw 'Incomplete HTTP range.' }
            $stream = [IO.File]::OpenRead($part.File)
            try { $stream.CopyTo($output) } finally { $stream.Dispose() }
        }
    } finally { $output.Dispose() }
    if ((Get-Digest $Target) -ne $Entry.sha256) {
        foreach ($part in $pieces) { [IO.File]::Delete($part.File) }
        throw 'SHA256 mismatch after reassembly.'
    }
}

function Download-Verified($Entry, $Target) {
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        try {
            Write-Host "Downloading $($Entry.name) ($attempt/3)"
            if ($attempt -lt 3 -and $Entry.size -ge 4MB -and $Entry.url -like 'https://github.com/*/releases/download/*' -and (Get-Command curl.exe -ErrorAction SilentlyContinue)) {
                Get-GitHubAsset $Entry $Target
            } else {
                Invoke-WebRequest -UseBasicParsing -Uri $Entry.url -OutFile $Target -TimeoutSec 1800 -UserAgent 'TN-C-client-sync/1.0'
            }
            if ((Get-Digest $Target) -ne $Entry.sha256) { throw "SHA256 mismatch for $($Entry.name)" }
            $partDir = Join-Path (Join-Path $stateDir 'partial') $Entry.sha256
            if ([IO.Directory]::Exists($partDir)) {
                foreach ($part in [IO.Directory]::EnumerateFiles($partDir, "$($Entry.name).*.part")) { [IO.File]::Delete($part) }
                if (![IO.Directory]::GetFileSystemEntries($partDir).Length) { [IO.Directory]::Delete($partDir) }
            }
            return
        } catch {
            if ($attempt -eq 3) { throw "Unable to stage $($Entry.name) from $($Entry.url): $($_.Exception.Message)" }
            Write-Warning $_.Exception.Message
            Start-Sleep -Seconds $attempt
        }
    }
}

function Register-Change($Target, $Stage, $Before, $After) {
    $relative = $Target.Substring($root.TrimEnd('\','/').Length+1)
    [void]$changes.Add([pscustomobject]@{ Path=$Target; Stage=$Stage; Old=(Join-Path $backup $relative); Before=$Before; After=$After })
}

function Stage-Text($Target, $Content, $Encoding) {
    $stage = Join-Path $staging ([IO.Path]::GetRandomFileName())
    [IO.File]::WriteAllText($stage,$Content,$Encoding)
    $before = Get-Digest $Target
    $after = Get-Digest $stage
    if ($before -ne $after) { Register-Change $Target $stage $before $after }
}

function Stage-Defaults {
    $voxyConfig = Join-Path $root 'config/voxy-config.json'
    $settings = if ([IO.File]::Exists($voxyConfig)) { [IO.File]::ReadAllText($voxyConfig) | ConvertFrom-Json } else { [pscustomobject]@{} }
    if ($null -eq $settings -or $settings -isnot [pscustomobject]) { throw "Expected JSON object in $voxyConfig" }
    $settings | Add-Member -MemberType NoteProperty -Name 'enabled' -Value $false -Force
    foreach ($name in 'enable_rendering','ingest_enabled') { $settings | Add-Member -MemberType NoteProperty -Name $name -Value $true -Force }
    if (-not $settings.PSObject.Properties['section_render_distance']) { $settings | Add-Member -MemberType NoteProperty -Name 'section_render_distance' -Value 16 }
    Stage-Text $voxyConfig (($settings | ConvertTo-Json -Depth 50) + "`n") $utf8
    Stage-Text $receipt ($profile.profile + "`n") $utf8
}

$guard = $null
try {
    if (-not $VerifyOnly) {
        [void][IO.Directory]::CreateDirectory($stateDir)
        $guard = [IO.File]::Open((Join-Path $stateDir 'sync.lock'),'OpenOrCreate','ReadWrite','None')
        [void][IO.Directory]::CreateDirectory($staging)
    }
    foreach ($entry in $entries) {
        $target = Join-Path (Join-Path $root $entry.folder) $entry.name
        $before = Get-Digest $target
        if ($before -eq $entry.sha256) { continue }
        if ($VerifyOnly) { throw "Missing or mismatched $target (expected $($entry.sha256)). Run sync." }
        $stage = Join-Path $staging $entry.name
        Download-Verified $entry $stage
        Register-Change $target $stage $before $entry.sha256
    }
    foreach ($jar in (Get-ChildItem -LiteralPath (Join-Path $root 'mods') -File -Filter '*.jar')) {
        if ($managedNames.ContainsKey($jar.Name)) { continue }
        $id = Find-ManagedId $jar.FullName
        if ($id) {
            if ($VerifyOnly) { throw "Duplicate $id in $($jar.FullName). Run sync." }
            Register-Change $jar.FullName $null (Get-Digest $jar.FullName) ''
        }
    }
    $defaultsSet = [IO.File]::Exists($receipt) -and [IO.File]::ReadAllText($receipt).Trim() -ceq $profile.profile
    if (-not $defaultsSet) {
        if ($VerifyOnly) { throw "Client profile has not completed initialization: $root" }
        Stage-Defaults
    }
    if ($changes.Count -gt 0) {
        Assert-GameClosed
        foreach ($change in $changes) {
            if ((Get-Digest $change.Path) -ne $change.Before) { throw "Concurrent edit: $($change.Path)" }
            [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($change.Path))
            if ($change.Before) { [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($change.Old)) }
            if (-not $change.Stage) { [IO.File]::Move($change.Path,$change.Old) }
            elseif ($change.Before) { [IO.File]::Replace($change.Stage,$change.Path,$change.Old) }
            else { [IO.File]::Move($change.Stage,$change.Path) }
            [void]$applied.Add($change)
            if ((Get-Digest $change.Path) -ne $change.After) { throw "Verification failed: $($change.Path)" }
        }
        if ([IO.Directory]::Exists($backup)) { Write-Host "Previous files saved in $backup" }
    }
    Write-Host "Verified $($entries.Count) pinned files; $($changes.Count) changes. Game directory: $root"
} catch {
    for ($i=$applied.Count-1; $i -ge 0; $i--) {
        $change = $applied[$i]
        if ((Get-Digest $change.Path) -ne $change.After) { Write-Warning "Concurrent edit preserved: $($change.Path)"; continue }
        if (-not $change.Before) { [IO.File]::Delete($change.Path) }
        elseif (-not $change.Stage) { [IO.File]::Move($change.Old,$change.Path) }
        else { [IO.File]::Replace($change.Old,$change.Path,(Join-Path $staging ([IO.Path]::GetRandomFileName()))) }
    }
    throw
} finally {
    if ($guard) {
        # Only known temporary files directly inside this run's private directory.
        if ([IO.Directory]::Exists($staging)) {
            foreach ($file in [IO.Directory]::EnumerateFiles($staging)) { [IO.File]::Delete($file) }
            [IO.Directory]::Delete($staging)
        }
        $guard.Dispose()
    }
}
