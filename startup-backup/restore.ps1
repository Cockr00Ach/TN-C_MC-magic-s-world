# Restore everything that was removed/disabled.  Run as administrator.
$ErrorActionPreference = 'Continue'
$bk = 'D:\ModTest\startup-backup'
$log = Join-Path $bk 'restore-log.txt'
$out = New-Object System.Collections.Generic.List[string]

$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
$out.Add("admin = $admin  ($(Get-Date -Format s))")

# 1) registry
foreach ($f in @('HKCU_Run.reg','HKLM_Run.reg','HKLM_Run32.reg','HKCU_StartupApproved_Run.reg','HKLM_StartupApproved_Run.reg','HKLM_StartupApproved_Run32.reg')) {
  $p = Join-Path $bk $f
  if (Test-Path $p) {
    $r = reg import $p 2>&1
    $out.Add("import $f -> $r")
  }
}

# 2) startup folder shortcuts
$sf = "$env:ProgramData\Microsoft\Windows\Start Menu\Programs\Startup"
Get-ChildItem -LiteralPath (Join-Path $bk 'lnk') -Filter 'removed_*.lnk' -ErrorAction SilentlyContinue | ForEach-Object {
  $target = Join-Path $sf ($_.Name -replace '^removed_', '')
  try { Copy-Item $_.FullName $target -Force -ErrorAction Stop; $out.Add("restored lnk: $($target)") }
  catch { $out.Add("FAILED restore lnk: $($_.Exception.Message)") }
}

# 3) scheduled tasks
$tasks = @(
  @{n='OneDrive Startup Task-S-1-5-21-620344409-4013399299-2564790692-1001'; p='\'},
  @{n='WpsUpdateLogonTask_FDCX'; p='\'},
  @{n='WpsWakeWnsLogonTask'; p='\'},
  @{n='QuarkCloudDriveUpdaterTaskUser1.0.0.11{B3860D4C-D474-4EB6-98AE-0C108EE70435}'; p='\QuarkCloudDriveUpdaterUser\QuarkCloudDriveUpdater\'},
  @{n='QuarkUpdaterTaskUser1.0.0.21{F41B57EE-26B3-4B64-B94D-B911E76CAD97}'; p='\QuarkUpdaterUser\QuarkUpdater\'}
)
foreach ($t in $tasks) {
  try { Enable-ScheduledTask -TaskName $t.n -TaskPath $t.p -ErrorAction Stop | Out-Null; $out.Add("enabled task: $($t.n)") }
  catch { $out.Add("FAILED enable: $($t.n) -> $($_.Exception.Message)") }
}

$out -join "`r`n" | Set-Content $log -Encoding UTF8
$out | ForEach-Object { Write-Host $_ }
Write-Host ''
Write-Host 'Restore done. Log: $log'
