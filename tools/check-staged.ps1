# Reject staged files that should never enter this repository.
#
#   Run by the pre-commit hook (tools/git-hooks/pre-commit), or by hand:
#     powershell -NoProfile -ExecutionPolicy Bypass -File tools\check-staged.ps1
#
#   Why this exists:
#     On 2026-09-16 the very first commit silently swallowed the 1.5 GB modpack
#     zip sitting in the repo root, and .git ballooned to 1545 MB. A single
#     `git add -A` is all it takes - .gitignore only helps for patterns someone
#     remembered to write down. So the rule is enforced mechanically instead.
#
#   Health baseline for this repo: ~309 files / ~1.6 MB total.
#   If a commit suddenly reports far more, something big got in.
#
#   This file is deliberately pure ASCII (no UTF-8 BOM needed on a GBK host).

param(
    # Any staged file larger than this (MB) is rejected.
    [double]$MaxMB = 2.0,

    # Report problems but exit 0 (used for a dry run).
    [switch]$WarnOnly
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path $PSScriptRoot -Parent

# ---------------------------------------------------------------- staged list
$staged = @(& git -C $repoRoot -c core.quotepath=false diff --cached --name-only --diff-filter=ACMR 2>$null)
if ($LASTEXITCODE -ne 0) {
    Write-Output 'check-staged: not a git repository (or git not on PATH) - skipping.'
    exit 0
}
if ($staged.Count -eq 0) { exit 0 }

# ---------------------------------------------------------------- path rules
# Anything matching one of these must never be committed. Each entry is
# (regex, why). The regex runs against the repo-relative path with '/'.
$forbidden = @(
    @('(^|/)mods/[^/]+\.jar$',          'third-party mod jar (copyright + size)'),
    @('\.(zip|7z|rar)$',                'archive - the modpack zip alone is 1.5 GB'),
    @('^libs/[^/]+\.jar$',              'third-party jar for compiling; fetched by tools/fetch-libs.ps1'),
    @('(^|/)\.connector/',              'Connector-remapped third-party mods'),
    @('(^|/)tlm_custom_pack/',          '37 MB maid model pack'),
    @('(^|/)build/',                    'build output'),
    @('(^|/)run/',                      'dev run directory (world saves, logs)'),
    @('(^|/)\.gradle/',                 'gradle cache'),
    @('(^|/)logs?/[^/]+\.log$',         'log file'),
    @('\.(class|dll|so|dylib)$',        'compiled binary')
)

$problems = New-Object System.Collections.Generic.List[string]

foreach ($f in $staged) {
    $path = $f -replace '\\', '/'
    foreach ($rule in $forbidden) {
        if ($path -match $rule[0]) {
            $problems.Add(("  [forbidden] {0}`n              -> {1}" -f $f, $rule[1]))
            break
        }
    }
}

# ---------------------------------------------------------------- size rules
$totalBytes = 0
foreach ($f in $staged) {
    $full = Join-Path $repoRoot ($f -replace '/', '\')
    if (-not (Test-Path -LiteralPath $full)) { continue }
    $len = (Get-Item -LiteralPath $full).Length
    $totalBytes += $len
    if ($len -gt ($MaxMB * 1MB)) {
        $problems.Add(("  [too big]   {0}`n              -> {1:N1} MB (limit {2} MB)" -f $f, ($len / 1MB), $MaxMB))
    }
}

# ---------------------------------------------------------------- verdict
if ($problems.Count -gt 0) {
    Write-Output ' '
    Write-Output '============================================================'
    Write-Output ' COMMIT REJECTED - staged files that must not be committed:'
    Write-Output '============================================================'
    foreach ($p in $problems) { Write-Output $p }
    Write-Output ' '
    Write-Output 'How to fix:'
    Write-Output '  unstage them      :  git reset HEAD <file>'
    Write-Output '  or unstage all    :  git reset'
    Write-Output '  keep the file on disk, just not in git - add a pattern to .gitignore'
    Write-Output '  (do NOT use "git add -f": that bypasses .gitignore on purpose)'
    Write-Output ' '
    Write-Output 'The modpack itself is shared separately (a 1.5 GB zip over a file host),'
    Write-Output 'never through this repository.'
    Write-Output ' '
    if ($WarnOnly) { Write-Output '(warn only - not blocking)'; exit 0 }
    exit 1
}

# ---------------------------------------------------------------- ok summary
Write-Output ("check-staged: {0} file(s), {1:N2} MB - ok" -f $staged.Count, ($totalBytes / 1MB))
exit 0
