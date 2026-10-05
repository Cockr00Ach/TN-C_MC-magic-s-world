# Elevated cleanup: needs "Run as administrator".
# Removes the HKLM startup entries and disables the logon scheduled tasks.
$ErrorActionPreference = 'Continue'
$bk  = 'D:\ModTest\startup-backup'
$log = Join-Path $bk 'elevated-log.txt'
$out = New-Object System.Collections.Generic.List[string]

$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
$out.Add("admin = $admin  ($(Get-Date -Format s))")
if (-not $admin) { $out.Add('NOT ELEVATED - nothing done.'); $out -join "`r`n" | Set-Content $log -Encoding UTF8; exit 1 }

# 1) HKLM Run (32-bit)
$hklm = 'HKLM:\SOFTWARE\Wow6432Node\Microsoft\Windows\CurrentVersion\Run'
foreach ($n in @('SunJavaUpdateSched','DesktopPortal','RadminVPN','Adobe CCXProcess','ControlCenter4','BrStsMon00','M15A')) {
  try { Remove-ItemProperty -Path $hklm -Name $n -ErrorAction Stop; $out.Add("removed  HKLM32\$n") }
  catch { $out.Add("FAILED   HKLM32\$n -> $($_.Exception.Message)") }
}

# 2) HKLM Run (64-bit, if present)
$hklm64 = 'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Run'
foreach ($n in @('SecurityHealth','RtkAudUService')) {
  $out.Add("kept     HKLM64\$n (system / audio - not touched)")
}

# 3) Scheduled tasks -> disable
$tasks = @(
  @{n='OneDrive Startup Task-S-1-5-21-620344409-4013399299-2564790692-1001'; p='\'},
  @{n='WpsUpdateLogonTask_FDCX'; p='\'},
  @{n='WpsWakeWnsLogonTask'; p='\'}
)
foreach ($t in $tasks) {
  try { Disable-ScheduledTask -TaskName $t.n -TaskPath $t.p -ErrorAction Stop | Out-Null; $out.Add("disabled task: $($t.n)") }
  catch { $out.Add("FAILED disable: $($t.n) -> $($_.Exception.Message)") }
}

# 4) verify
$out.Add('--- verify ---')
$out.Add('[HKLM Run32]')
(Get-Item $hklm).Property | ForEach-Object { $out.Add('  ' + $_) }
$out.Add('[HKLM Run]')
(Get-Item $hklm64).Property | ForEach-Object { $out.Add('  ' + $_) }
foreach ($t in $tasks) {
  $x = Get-ScheduledTask -TaskName $t.n -TaskPath $t.p -ErrorAction SilentlyContinue
  if ($x) { $out.Add("  $($t.n) -> $($x.State)") }
}
$out -join "`r`n" | Set-Content $log -Encoding UTF8
$out | ForEach-Object { Write-Host $_ }
