$ErrorActionPreference='SilentlyContinue'
$log='D:\ModTest\final_pass.log'
function L([string]$m){ $s="{0}  {1}" -f (Get-Date -Format 'HH:mm:ss'),$m; Write-Host $s; Add-Content -LiteralPath $log -Value $s -Encoding UTF8 }
Set-Content -LiteralPath $log -Value "=== final pass @ $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ===" -Encoding UTF8

function ForceDel([string]$p){
  if([string]::IsNullOrWhiteSpace($p) -or $p -match '^[A-Za-z]:\\?$'){ L "  REFUSED unsafe: '$p'"; return }
  if(-not (Test-Path -LiteralPath $p)){ L ("  already gone: {0}" -f $p); return }
  $before=[math]::Round((Get-ChildItem -LiteralPath $p -Recurse -File -Force -ErrorAction SilentlyContinue | Measure-Object Length -Sum).Sum/1MB,1)
  try{ $acl=Get-Acl -LiteralPath $p; foreach($a in @($acl.Access)){ if($a.AccessControlType -eq 'Deny'){ [void]$acl.RemoveAccessRuleSpecific($a) } }; Set-Acl -LiteralPath $p -AclObject $acl }catch{}
  Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.Path -like "$p\*" } | ForEach-Object { L ("  killing pid {0} {1}" -f $_.Id,$_.ProcessName); Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue }
  Start-Sleep -Milliseconds 700
  takeown /F $p /R /D Y 2>&1 | Out-Null
  icacls $p /grant "*S-1-5-32-544:(F)" /T /C 2>&1 | Out-Null
  Remove-Item -LiteralPath $p -Recurse -Force -ErrorAction SilentlyContinue
  if(Test-Path -LiteralPath $p){
    $left=[math]::Round((Get-ChildItem -LiteralPath $p -Recurse -File -Force -ErrorAction SilentlyContinue | Measure-Object Length -Sum).Sum/1MB,1)
    L ("  PARTIAL: {0} ({1}MB -> {2}MB)" -f $p,$before,$left)
  } else { L ("  DELETED: {0} ({1} MB)" -f $p,$before) }
}

L "--- 1. adware residue ---"
foreach($p in @(
 'C:\Program Files (x86)\XDImageShow','C:\Program Files (x86)\XDZipApp',
 'C:\Users\FDCX\AppData\Local\{AA74A9F6-603B-4B0D-922D-36753FB491EE}',
 'C:\ProgramData\XDImageShow','C:\ProgramData\XDZipApp'
)){ ForceDel $p }

L "--- 2. finish Lenovo store removal ---"
foreach($p in @('C:\Program Files (x86)\Lenovo\LeAppStore','C:\ProgramData\Lenovo\LeAppStore',
                'C:\Users\FDCX\AppData\Roaming\Lenovo\LeAppStore','C:\Users\FDCX\AppData\Local\Lenovo\LeAppStore')){ ForceDel $p }
ForceDel 'E:\LeStoreDownload'
$lck='E:\LeStoreDownload\yuanbao_8004_x64.exe'
if(Test-Path -LiteralPath $lck){
  $acl=Get-Acl -LiteralPath $lck
  foreach($a in @($acl.Access)){ if($a.AccessControlType -eq 'Deny'){ [void]$acl.RemoveAccessRuleSpecific($a) } }
  Set-Acl -LiteralPath $lck -AclObject $acl
  Remove-Item -LiteralPath 'E:\LeStoreDownload' -Recurse -Force
  L ("  E:\LeStoreDownload removed: {0}" -f (-not (Test-Path -LiteralPath 'E:\LeStoreDownload')))
}

L "--- 3. remote control: stop + remove services and leftovers ---"
foreach($n in @('SunloginService','ToDesk_Service')){
  $s=Get-Service -Name $n -ErrorAction SilentlyContinue
  if($s){ Stop-Service -Name $n -Force -ErrorAction SilentlyContinue; Start-Sleep -Milliseconds 800; $r=& sc.exe delete $n 2>&1; L ("  sc delete {0} -> {1}" -f $n,($r -join ' ')) } else { L ("  not present: {0}" -f $n) }
}
foreach($p in @('D:\Program Files\SunloginClient','C:\Users\FDCX\ToDesk','C:\Program Files\ToDesk',
                'C:\ProgramData\ToDesk','C:\Users\FDCX\AppData\Roaming\ToDesk','C:\Users\FDCX\AppData\Local\ToDesk',
                'C:\ProgramData\Oray','C:\Program Files (x86)\Oray')){ ForceDel $p }
Get-ScheduledTask -ErrorAction SilentlyContinue | Where-Object { $_.TaskName -match 'ToDesk|Sunlogin|向日葵|Oray' } | ForEach-Object {
  Unregister-ScheduledTask -TaskPath $_.TaskPath -TaskName $_.TaskName -Confirm:$false -ErrorAction SilentlyContinue
  L ("  task removed: {0}{1}" -f $_.TaskPath,$_.TaskName)
}

L "--- 4. sweep stale uninstall registry entries pointing at deleted dirs ---"
$unKeys=@('HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*','HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*','HKCU:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*')
Get-ItemProperty $unKeys -ErrorAction SilentlyContinue | Where-Object {
  $_.DisplayName -match '迅读|Winz|夸克|Watt|UU加速|SakuraFrp|联想应用商店|ToDesk|向日葵|Sunlogin'
} | ForEach-Object {
  $rp=$_.PSPath -replace '^Microsoft\.PowerShell\.Core\\Registry::',''
  $loc=$_.InstallLocation
  $dead = (-not $loc) -or (-not (Test-Path -LiteralPath $loc))
  L ("  entry '{0}'  loc='{1}'  dead={2}" -f $_.DisplayName,$loc,$dead)
  if($dead -and $_.DisplayName -notmatch '夸克网盘'){ Remove-Item -LiteralPath $rp -Recurse -Force -ErrorAction SilentlyContinue; L "    -> removed" }
}

L "--- 5. restore QuarkCloudDrive (user asked to keep it) ---"
$url='https://pan.quark.cn/s/download'
$dest="$env:USERPROFILE\Downloads\QuarkCloudDrive_setup.exe"
try{
  Invoke-WebRequest -Uri $url -OutFile $dest -UseBasicParsing -TimeoutSec 120 -ErrorAction Stop
  $sz=[math]::Round((Get-Item -LiteralPath $dest).Length/1MB,2)
  L ("  downloaded installer: {0} ({1} MB)" -f $dest,$sz)
  if($sz -gt 5){
    L "  launching installer (silent)..."
    Start-Process -FilePath $dest -ArgumentList '/S' -ErrorAction SilentlyContinue
  } else {
    L "  installer too small - likely an HTML redirect page, NOT executed"
    Get-Content -LiteralPath $dest -TotalCount 5 | ForEach-Object { L ("    html: {0}" -f $_) }
  }
}catch{ L ("  download failed: {0}" -f $_.Exception.Message) }

L "--- 6. verification ---"
foreach($p in @('C:\Program Files (x86)\XDImageShow','C:\Program Files (x86)\XDZipApp',
 'C:\Users\FDCX\AppData\Local\{AA74A9F6-603B-4B0D-922D-36753FB491EE}',
 'C:\Program Files (x86)\Lenovo\LeAppStore','D:\Program Files\SunloginClient','C:\Users\FDCX\ToDesk',
 'E:\LeStoreDownload','C:\Users\FDCX\AppData\Local\Programs\QuarkCloudDrive')){
  L ("  {0,-62} {1}" -f $p,$(if(Test-Path -LiteralPath $p){'EXISTS'}else{'gone'}))
}
foreach($n in @('BaseJMI','CleanSvr','npzsvc','IShowServer','WinZips_Svr','WattAcceleratorSvc','UUAccelerator','SunloginService','ToDesk_Service','LenovoServiceAS','LISFService')){
  $s=Get-Service -Name $n -ErrorAction SilentlyContinue
  L ("  svc {0,-20} {1}" -f $n,$(if($s){"STILL [$($s.Status)]"}else{'gone'}))
}
L ("  C: free={0:N2}GB  D: free={1:N2}GB  E: free={2:N2}GB" -f ((Get-PSDrive C).Free/1GB),((Get-PSDrive D).Free/1GB),((Get-PSDrive E).Free/1GB))
L "FINAL PASS DONE"
