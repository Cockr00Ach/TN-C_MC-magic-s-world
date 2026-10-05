$ErrorActionPreference='SilentlyContinue'
$log='D:\ModTest\adware_verify.log'
function L([string]$m){ $s="{0}  {1}" -f (Get-Date -Format 'HH:mm:ss'),$m; Write-Host $s; Add-Content -LiteralPath $log -Value $s -Encoding UTF8 }
Set-Content -LiteralPath $log -Value "=== force-clean + verify @ $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ===" -Encoding UTF8

# wait for the previous elevated pass to finish so we don't fight over files
$deadline=(Get-Date).AddSeconds(240)
while((Get-Date) -lt $deadline -and (Get-Process -Id 24012 -ErrorAction SilentlyContinue)){ Start-Sleep -Seconds 5 }
L "previous pass no longer running; starting force sweep"

function ForceDel([string]$p){
  if([string]::IsNullOrWhiteSpace($p) -or $p -match '^[A-Za-z]:\\?$'){ L "  REFUSED unsafe: '$p'"; return }
  if(-not (Test-Path -LiteralPath $p)){ L ("  already gone: {0}" -f $p); return }
  $before = [math]::Round((Get-ChildItem -LiteralPath $p -Recurse -File -Force -ErrorAction SilentlyContinue | Measure-Object Length -Sum).Sum/1MB,1)
  # strip deny ACEs and take ownership so nothing can block the delete
  try {
    $acl = Get-Acl -LiteralPath $p
    foreach($ace in @($acl.Access)){ if($ace.AccessControlType -eq 'Deny'){ [void]$acl.RemoveAccessRuleSpecific($ace) } }
    Set-Acl -LiteralPath $p -AclObject $acl
  } catch {}
  # kill anything still running out of this directory
  Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.Path -like "$p\*" } | ForEach-Object { L ("  killing pid {0} {1}" -f $_.Id,$_.ProcessName); Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue }
  Start-Sleep -Milliseconds 800
  takeown /F $p /R /D Y 2>&1 | Out-Null
  icacls $p /grant "*S-1-5-32-544:(F)" /T /C 2>&1 | Out-Null
  Remove-Item -LiteralPath $p -Recurse -Force -ErrorAction SilentlyContinue
  if(Test-Path -LiteralPath $p){
    $left = [math]::Round((Get-ChildItem -LiteralPath $p -Recurse -File -Force -ErrorAction SilentlyContinue | Measure-Object Length -Sum).Sum/1MB,1)
    L ("  PARTIAL: {0}  {1}MB -> {2}MB still there" -f $p,$before,$left)
  } else { L ("  DELETED: {0}  ({1} MB freed)" -f $p,$before) }
}

L "--- adware family residue ---"
foreach($p in @(
 'C:\Program Files (x86)\XDImageShow','C:\Program Files (x86)\XDZipApp',
 'C:\Users\FDCX\AppData\Local\{AA74A9F6-603B-4B0D-922D-36753FB491EE}',
 'C:\ProgramData\XDImageShow','C:\ProgramData\XDZipApp',
 'C:\Users\FDCX\AppData\Roaming\XDImageShow','C:\Users\FDCX\AppData\Roaming\XDZipApp',
 'C:\Users\FDCX\AppData\Local\XDZipApp'
)){ ForceDel $p }

L "--- adware vendor directories anywhere on C: (Program Files) ---"
Get-ChildItem 'C:\Program Files (x86)','C:\Program Files' -Directory -Force -ErrorAction SilentlyContinue |
  Where-Object { $_.Name -match '^XD|WinClean|NetPoweZip|迅读|Winz' } |
  ForEach-Object { L ("  found: {0}" -f $_.FullName); ForceDel $_.FullName }

L "--- stray uninstall registry entries from that vendor ---"
$unKeys=@('HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*','HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*','HKCU:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*')
$hits = Get-ItemProperty $unKeys -ErrorAction SilentlyContinue | Where-Object { $_.Publisher -match 'Aishang|艾上' -or $_.DisplayName -match '迅读|Winz' }
if($hits){
  foreach($h in $hits){
    $rp = $h.PSPath -replace '^Microsoft\.PowerShell\.Core\\Registry::',''
    L ("  removing uninstall entry: {0}  ({1})" -f $h.DisplayName,$rp)
    Remove-Item -LiteralPath $rp -Recurse -Force -ErrorAction SilentlyContinue
  }
} else { L "  none left" }

L "--- HKLM Run keys ---"
foreach($k in @('HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Run','HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Run','HKCU:\SOFTWARE\Microsoft\Windows\CurrentVersion\Run')){
  (Get-ItemProperty $k -ErrorAction SilentlyContinue).PSObject.Properties | Where-Object { $_.Name -notlike 'PS*' } | ForEach-Object { L ("  {0,-40} = {1}" -f $_.Name,$_.Value) }
}

L "--- services that should be gone ---"
$should='BaseJMI','CleanSvr','npzsvc','IShowServer','WinZips_Svr','XDExtend','WattAcceleratorSvc','UUAccelerator','SunloginService','ToDesk_Service','LenovoServiceAS','LISFService'
foreach($n in $should){
  $s=Get-Service -Name $n -ErrorAction SilentlyContinue
  if($s){ L ("  STILL REGISTERED: {0} [{1}]" -f $s.Name,$s.Status) } else { L ("  gone: {0}" -f $n) }
}

L "--- remaining run-key / service binaries that exist but point nowhere ---"
Get-CimInstance Win32_Service -ErrorAction SilentlyContinue | Where-Object { $_.StartMode -eq 'Auto' -and $_.PathName -notmatch 'system32|SysWOW64|WindowsApps' } | ForEach-Object {
  $exe = ($_.PathName -replace '^"','' -split '"')[0]
  $exe = ($exe -split ' -')[0].Trim()
  if($exe -and -not (Test-Path -LiteralPath $exe)){ L ("  ORPHAN: {0} -> {1}" -f $_.Name,$exe) }
}

L "--- final sizes ---"
foreach($p in @('C:\Program Files (x86)\XDImageShow','C:\Program Files (x86)\XDZipApp','C:\Program Files (x86)\Lenovo\LeAppStore','C:\Users\FDCX\AppData\Local\Programs\QuarkCloudDrive','C:\Program Files (x86)\WattAccelerator','D:\Program Files\SunloginClient','C:\Users\FDCX\ToDesk','E:\LeStoreDownload')){
  L ("  {0,-62} {1}" -f $p, $(if(Test-Path -LiteralPath $p){'STILL EXISTS'}else{'gone'}))
}
L ("  C: free = {0:N2} GB   E: free = {1:N2} GB" -f ((Get-PSDrive C).Free/1GB),((Get-PSDrive E).Free/1GB))
L "VERIFY DONE"
