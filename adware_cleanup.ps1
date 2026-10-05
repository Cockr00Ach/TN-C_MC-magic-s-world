$ErrorActionPreference = 'SilentlyContinue'
$ProgressPreference = 'SilentlyContinue'
$log = 'D:\ModTest\adware_cleanup.log'
function L([string]$m){
  $line = "{0}  {1}" -f (Get-Date -Format 'HH:mm:ss'), $m
  Write-Host $line
  Add-Content -LiteralPath $log -Value $line -Encoding UTF8
}
Set-Content -LiteralPath $log -Value "=== adware cleanup @ $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ===" -Encoding UTF8

function DirMB([string]$p){
  if(-not (Test-Path -LiteralPath $p)){ return 0 }
  $s = Get-ChildItem -LiteralPath $p -Recurse -File -Force -ErrorAction SilentlyContinue | Measure-Object Length -Sum
  return [math]::Round($s.Sum/1MB,1)
}
function Nuke([string]$p){
  # safety: never operate on a drive root or short/empty path
  if([string]::IsNullOrWhiteSpace($p) -or $p.Length -lt 12 -or $p -match '^[A-Za-z]:\\?$'){ L ("    REFUSED unsafe path: '{0}'" -f $p); return }
  if(Test-Path -LiteralPath $p){
    Remove-Item -LiteralPath $p -Recurse -Force -ErrorAction SilentlyContinue
    if(Test-Path -LiteralPath $p){ L ("    ! still present: {0}" -f $p) } else { L ("    removed dir: {0}" -f $p) }
  }
}
function KillSvc([string]$name){
  $svc = Get-Service -Name $name -ErrorAction SilentlyContinue
  if(-not $svc){ L ("    service not present: {0}" -f $name); return }
  Stop-Service -Name $name -Force -ErrorAction SilentlyContinue
  Start-Sleep -Milliseconds 700
  $r = & sc.exe delete $name 2>&1
  if(Test-Path -LiteralPath 'HKLM:\SYSTEM\CurrentControlSet\Services\__dsh__'){ Remove-Item -LiteralPath 'HKLM:\SYSTEM\CurrentControlSet\Services\__dsh__' -Recurse -Force -ErrorAction SilentlyContinue }
  L ("    sc delete {0} -> {1}" -f $name, ($r -join ' '))
}
function KillReg([string]$path){
  if(Test-Path -LiteralPath $path){
    Remove-Item -LiteralPath $path -Recurse -Force -ErrorAction SilentlyContinue
    L ("    reg removed: {0} ({1})" -f $path, $(if(Test-Path -LiteralPath $path){'FAILED'}else{'ok'}))
  }
}
function RunUninst([string]$cmd,[string]$dir,[string]$tag){
  L ("  uninstalling {0}" -f $tag)
  if($cmd){
    $c = $cmd.Trim()
    $exe = $null; $arg = $null
    $q = [char]34
    if($c.StartsWith($q)){
      $end = $c.IndexOf($q,1)
      if($end -gt 0){ $exe = $c.Substring(1,$end-1); $arg = $c.Substring($end+1).Trim() }
    }
    if(-not $exe){
      # unquoted: longest leading token that is an existing file wins, so paths with spaces survive
      $tok = @(); $t = ''
      foreach($ch in $c.ToCharArray()){ if($ch -eq ' '){ if($t){$tok+=$t;$t=''} } else { $t += $ch } }
      if($t){ $tok += $t }
      for($i=$tok.Count; $i -ge 1; $i--){
        $cand = ($tok[0..($i-1)] -join ' ')
        if(Test-Path -LiteralPath $cand){ $exe = $cand; $arg = ($tok[$i..($tok.Count-1)] -join ' '); break }
      }
    }
    if($exe -and (Test-Path -LiteralPath $exe)){
      L ("    exec: {0} {1}" -f $exe,$arg)
      try {
        if($arg){ Start-Process -FilePath $exe -ArgumentList $arg -ErrorAction SilentlyContinue }
        else    { Start-Process -FilePath $exe -ErrorAction SilentlyContinue }
      } catch { L ("    launch failed: {0}" -f $_.Exception.Message) }
    } else { L ("    could not resolve uninstaller from: {0}" -f $cmd) }
    Start-Sleep -Seconds 6
    $deadline = (Get-Date).AddSeconds(70)
    while((Get-Date) -lt $deadline){
      if(-not $dir -or -not (Test-Path -LiteralPath $dir)){ break }
      Start-Sleep -Seconds 3
    }
  }
  if($dir -and (Test-Path -LiteralPath $dir)){ Nuke $dir } else { L "    uninstaller cleaned up on its own" }
}

# ---------------------------------------------------------------- 1. ADWARE
L "=== 1. adware family: Chengdu Aishang Office (迅读看图 / Winz解压缩 / BaseJMI) ==="
L ("  size before: XDImageShow={0}MB XDZipApp={1}MB BaseJMI={2}MB" -f (DirMB 'C:\Program Files (x86)\XDImageShow'),(DirMB 'C:\Program Files (x86)\XDZipApp'),(DirMB 'C:\Users\FDCX\AppData\Local\{AA74A9F6-603B-4B0D-922D-36753FB491EE}'))

KillSvc 'BaseJMI'
RunUninst 'C:\Program Files (x86)\XDImageShow\Uninstall.exe' 'C:\Program Files (x86)\XDImageShow' '迅读看图'
RunUninst 'C:\Program Files (x86)\XDZipApp\Uninstall.exe'   'C:\Program Files (x86)\XDZipApp'   'Winz解压缩'

L "  sweeping leftover adware artifacts..."
Nuke 'C:\Users\FDCX\AppData\Local\{AA74A9F6-603B-4B0D-922D-36753FB491EE}'
foreach($p in @(
  'C:\Program Files (x86)\XDImageShow','C:\Program Files (x86)\XDZipApp',
  'C:\ProgramData\XDImageShow','C:\ProgramData\XDZipApp',
  'C:\Users\FDCX\AppData\Roaming\XDImageShow','C:\Users\FDCX\AppData\Roaming\XDZipApp',
  'C:\Users\FDCX\AppData\Local\XDImageShow','C:\Users\FDCX\AppData\Local\XDZipApp'
)){ Nuke $p }

L "  removing adware registry keys..."
$adKeys = @(
  'HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\迅读看图',
  'HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\Winz解压缩'
)
$unKeys = @('HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*',
            'HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*',
            'HKCU:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*')
Get-ItemProperty $unKeys -ErrorAction SilentlyContinue |
  Where-Object { $_.Publisher -match 'Aishang|艾上' -or $_.DisplayName -match '迅读|Winz' } |
  ForEach-Object { if($_.PSPath){ KillReg ($_.PSPath -replace '^Microsoft\.PowerShell\.Core\\Registry::','') } }

# ------------------------------------------------- 2. ORPHAN SVC / STARTUP
L "=== 2. orphan services + dead startup entries ==="
KillSvc 'CleanSvr'
KillSvc 'npzsvc'
KillSvc 'IShowServer'
KillSvc 'WinZips_Svr'
KillSvc 'XDExtend'
L "  dead startup folder entries:"
Nuke 'C:\Users\FDCX\AppData\Roaming\Microsoft\Windows\Start Menu\Programs\Startup\disabled'

# ------------------------------------------------------------- 3. XUNLEI BHO
L "=== 3. Xunlei browser helper object (IE injection) ==="
$bho = '{004B0726-A010-4ABF-8556-FCDB7F1FCA1E}'
KillReg "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Explorer\Browser Helper Objects\$bho"
KillReg "HKLM:\SOFTWARE\Classes\CLSID\$bho"
KillReg "HKLM:\SOFTWARE\Classes\WOW6432Node\CLSID\$bho"
KillReg "HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Explorer\Browser Helper Objects\$bho"

# --------------------------------------------------------------- 4. HOMEPAGE
L "=== 4. homepage hijack repair (hao.360.com) ==="
$iem = 'HKCU:\Software\Microsoft\Internet Explorer\Main'
L ("  old Start Page: {0}" -f (Get-ItemProperty $iem -Name 'Start Page' -ErrorAction SilentlyContinue).'Start Page')
Set-ItemProperty -LiteralPath $iem -Name 'Start Page' -Value 'about:blank' -ErrorAction SilentlyContinue
Set-ItemProperty -LiteralPath $iem -Name 'Default_Page_URL' -Value 'about:blank' -ErrorAction SilentlyContinue
Set-ItemProperty -LiteralPath $iem -Name 'Search Page' -Value 'about:blank' -ErrorAction SilentlyContinue
L ("  new Start Page: {0}" -f (Get-ItemProperty $iem -Name 'Start Page' -ErrorAction SilentlyContinue).'Start Page')
$wl = Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Winlogon' -ErrorAction SilentlyContinue
L ("  Winlogon Shell: {0}   Userinit: {1}" -f $wl.Shell, $wl.Userinit)

# ------------------------------------------------------------------ 5. QUARK
L "=== 5. 夸克 / 夸克网盘 ==="
L ("  size before: Quark={0}MB QuarkCloudDrive={1}MB" -f (DirMB 'C:\Users\FDCX\AppData\Local\Programs\Quark'),(DirMB 'C:\Users\FDCX\AppData\Local\Programs\QuarkCloudDrive'))
RunUninst 'C:\Users\FDCX\AppData\Local\Programs\Quark\unins000.exe --brand-quark /SILENT --fake-silent' 'C:\Users\FDCX\AppData\Local\Programs\Quark' '夸克'
RunUninst 'C:\Users\FDCX\AppData\Local\Programs\QuarkCloudDrive\unins000.exe --brand-clouddrive /SILENT --fake-silent' 'C:\Users\FDCX\AppData\Local\Programs\QuarkCloudDrive' '夸克网盘'
foreach($p in @('C:\Users\FDCX\AppData\Local\Quark','C:\Users\FDCX\AppData\Local\QuarkCloudDrive',
                'C:\Users\FDCX\AppData\Local\QuarkUpdater','C:\Users\FDCX\AppData\Local\QuarkCloudDriveUpdater',
                'C:\Users\FDCX\AppData\Roaming\Quark')){ Nuke $p }
foreach($t in @('QuarkCloudDriveUpdaterUser','QuarkUpdaterUser')){
  Unregister-ScheduledTask -TaskName $t -Confirm:$false -ErrorAction SilentlyContinue
  schtasks /Delete /TN "\$t" /F 2>&1 | Out-Null
}
Get-ScheduledTask -ErrorAction SilentlyContinue | Where-Object { $_.TaskName -match 'Quark' } | ForEach-Object {
  Unregister-ScheduledTask -TaskPath $_.TaskPath -TaskName $_.TaskName -Confirm:$false -ErrorAction SilentlyContinue
  L ("    task removed: {0}{1}" -f $_.TaskPath, $_.TaskName)
}

# ------------------------------------------------------------ 6. ACCELERATORS
L "=== 6. accelerators: Watt / UU / SakuraFrp ==="
L ("  size before: Watt={0}MB" -f (DirMB 'C:\Program Files (x86)\WattAccelerator'))
RunUninst 'C:\Program Files (x86)\WattAccelerator\Uninstall.exe' 'C:\Program Files (x86)\WattAccelerator' 'Watt加速器'
RunUninst 'C:\Program Files (x86)\Netease\UU\uninstall.exe' 'C:\Program Files (x86)\Netease\UU' 'UU加速器'
RunUninst 'C:\Program Files\SakuraFrpLauncher\unins000.exe /SILENT' 'C:\Program Files\SakuraFrpLauncher' 'SakuraFrp'
foreach($p in @('C:\Program Files (x86)\WattAccelerator','C:\Program Files (x86)\Netease\UU',
                'C:\Program Files\SakuraFrpLauncher','C:\ProgramData\WattAccelerator',
                'C:\Users\FDCX\AppData\Roaming\WattAccelerator','C:\Users\FDCX\AppData\Local\SakuraFrpLauncher')){ Nuke $p }
KillSvc 'WattAcceleratorSvc'
KillSvc 'UUAccelerator'
Get-ScheduledTask -ErrorAction SilentlyContinue | Where-Object { $_.TaskName -match 'Watt|UU|Sakura' } | ForEach-Object {
  Unregister-ScheduledTask -TaskPath $_.TaskPath -TaskName $_.TaskName -Confirm:$false -ErrorAction SilentlyContinue
  L ("    task removed: {0}{1}" -f $_.TaskPath, $_.TaskName)
}

# ---------------------------------------------------------------- 7. LENOVO
L "=== 7. Lenovo app store + framework ==="
L ("  size before: LeAppStore={0}MB LISF={1}MB" -f (DirMB 'C:\Program Files (x86)\Lenovo\LeAppStore'),(DirMB 'C:\Program Files (x86)\Lenovo\LenovoInternetSoftwareFramework'))
RunUninst 'C:\Program Files (x86)\Lenovo\LeAppStore\StoreUninstaller.exe' 'C:\Program Files (x86)\Lenovo\LeAppStore' '联想应用商店'
KillSvc 'LenovoServiceAS'
KillSvc 'LISFService'
Nuke 'C:\Program Files (x86)\Lenovo\LeAppStore'
Nuke 'C:\Program Files (x86)\Lenovo\LenovoInternetSoftwareFramework'
Nuke 'C:\ProgramData\Lenovo\LeAppStore'
foreach($p in @('C:\Users\FDCX\AppData\Roaming\Lenovo\LeAppStore','C:\Users\FDCX\AppData\Local\Lenovo\LeAppStore')){ Nuke $p }
$bho2 = '{6533163f-1cb3-41b9-895f-ccc202f6e23d}'
KillReg "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Explorer\Browser Helper Objects\$bho2"
KillReg "HKLM:\SOFTWARE\Classes\CLSID\$bho2"
KillReg "HKLM:\SOFTWARE\Classes\WOW6432Node\CLSID\$bho2"
# release the delete-protected installer the store left behind
$lck = 'E:\LeStoreDownload\yuanbao_8004_x64.exe'
if(Test-Path -LiteralPath $lck){
  $acl = Get-Acl -LiteralPath $lck
  foreach($ace in @($acl.Access)){ if($ace.AccessControlType -eq 'Deny'){ [void]$acl.RemoveAccessRuleSpecific($ace) } }
  Set-Acl -LiteralPath $lck -AclObject $acl -ErrorAction SilentlyContinue
  L "  stripped Deny ACE from LeStoreDownload installer"
}
Nuke 'E:\LeStoreDownload'

# --------------------------------------------------------------- 8. REMOTE
L "=== 8. remote control: ToDesk / 向日葵 ==="
RunUninst 'D:\Program Files\SunloginClient\SunloginClient.exe --mod=uninstall' 'D:\Program Files\SunloginClient' '向日葵远程控制'
RunUninst 'C:\Users\FDCX\ToDesk\uninst.exe' 'C:\Users\FDCX\ToDesk' 'ToDesk'
foreach($p in @('D:\Program Files\SunloginClient','C:\Users\FDCX\ToDesk','C:\Program Files\ToDesk',
                'C:\ProgramData\ToDesk','C:\Users\FDCX\AppData\Roaming\ToDesk','C:\Users\FDCX\AppData\Local\ToDesk')){ Nuke $p }
KillSvc 'SunloginService'
KillSvc 'ToDesk_Service'
Get-ScheduledTask -ErrorAction SilentlyContinue | Where-Object { $_.TaskName -match 'ToDesk|Sunlogin|向日葵' } | ForEach-Object {
  Unregister-ScheduledTask -TaskPath $_.TaskPath -TaskName $_.TaskName -Confirm:$false -ErrorAction SilentlyContinue
  L ("    task removed: {0}{1}" -f $_.TaskPath, $_.TaskName)
}

# ------------------------------------------------------------ FINAL REPORT
L ""
L "=== VERIFICATION ==="
foreach($p in @(
 'C:\Program Files (x86)\XDImageShow','C:\Program Files (x86)\XDZipApp',
 'C:\Users\FDCX\AppData\Local\{AA74A9F6-603B-4B0D-922D-36753FB491EE}',
 'C:\Program Files (x86)\Lenovo\LeAppStore','C:\Program Files (x86)\Lenovo\LenovoInternetSoftwareFramework',
 'C:\Users\FDCX\AppData\Local\Programs\Quark','C:\Users\FDCX\AppData\Local\Programs\QuarkCloudDrive',
 'C:\Program Files (x86)\WattAccelerator','C:\Program Files (x86)\Netease\UU',
 'C:\Program Files\SakuraFrpLauncher','D:\Program Files\SunloginClient','C:\Users\FDCX\ToDesk',
 'E:\LeStoreDownload'
)){
  L ("  {0,-72} {1}" -f $p, $(if(Test-Path -LiteralPath $p){'STILL EXISTS'}else{'gone'}))
}
L "  remaining suspect services:"
Get-Service -ErrorAction SilentlyContinue | Where-Object { $_.Name -match 'BaseJMI|CleanSvr|npzsvc|IShowServer|WinZips|XDExtend|Watt|Sunlogin|ToDesk|LenovoServiceAS|LISFService' } | ForEach-Object { L ("    {0} [{1}]" -f $_.Name,$_.Status) }
L "  remaining Run-key entries:"
foreach($k in @('HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Run','HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Run','HKCU:\SOFTWARE\Microsoft\Windows\CurrentVersion\Run')){
  (Get-ItemProperty $k -ErrorAction SilentlyContinue).PSObject.Properties | Where-Object { $_.Name -notlike 'PS*' } | ForEach-Object { L ("    {0} = {1}" -f $_.Name,$_.Value) }
}
L ("  E: free now: {0:N2} GB" -f ((Get-PSDrive E).Free/1GB))
L "DONE"
