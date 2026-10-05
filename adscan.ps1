$ErrorActionPreference='SilentlyContinue'
$ProgressPreference='SilentlyContinue'

Write-Host "===== RUN KEYS ====="
$runKeys = @(
 'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Run',
 'HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Run',
 'HKCU:\SOFTWARE\Microsoft\Windows\CurrentVersion\Run',
 'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\RunOnce',
 'HKCU:\SOFTWARE\Microsoft\Windows\CurrentVersion\RunOnce'
)
foreach($k in $runKeys){
  if(Test-Path $k){
    Write-Host "--- $k ---"
    $p = Get-ItemProperty -Path $k
    $p.PSObject.Properties | Where-Object { $_.Name -notlike 'PS*' } |
      ForEach-Object { "{0,-32} = {1}" -f $_.Name, $_.Value }
  }
}

Write-Host "`n===== STARTUP FOLDERS ====="
foreach($s in @("$env:APPDATA\Microsoft\Windows\Start Menu\Programs\Startup",
                "$env:ProgramData\Microsoft\Windows\Start Menu\Programs\Startup")){
  Write-Host "--- $s ---"
  Get-ChildItem -LiteralPath $s -Force -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Name
}

Write-Host "`n===== SCHEDULED TASKS (non-Microsoft, ready/running) ====="
Get-ScheduledTask -ErrorAction SilentlyContinue |
  Where-Object { $_.TaskPath -notlike '\Microsoft\*' } |
  Select-Object State, TaskPath, TaskName |
  Sort-Object TaskPath, TaskName | Format-Table -AutoSize | Out-String -Width 220

Write-Host "`n===== AUTO SERVICES (non-Microsoft paths) ====="
Get-CimInstance Win32_Service -ErrorAction SilentlyContinue |
  Where-Object { $_.StartMode -eq 'Auto' -and $_.PathName -notmatch 'system32|SysWOW64' } |
  Select-Object State, Name, DisplayName, PathName |
  Sort-Object DisplayName | Format-Table -AutoSize | Out-String -Width 250

Write-Host "`n===== BROWSER EXTENSIONS / HOMEPAGE HIJACK CHECK ====="
$ie = Get-ItemProperty 'HKCU:\Software\Microsoft\Internet Explorer\Main' -ErrorAction SilentlyContinue
"Start Page  : $($ie.'Start Page')"
$chromePrefs = @(
  "$env:LOCALAPPDATA\Google\Chrome\User Data\Default\Preferences",
  "$env:LOCALAPPDATA\Microsoft\Edge\User Data\Default\Preferences"
)
foreach($c in $chromePrefs){
  if(Test-Path $c){
    $j = Get-Content -LiteralPath $c -Raw -ErrorAction SilentlyContinue | ConvertFrom-Json
    Write-Host "--- $c ---"
    "  homepage: $($j.homepage)  startup_urls: $($j.session.startup_urls -join ', ')"
    $ext = $j.extensions.settings.PSObject.Properties | Where-Object { $_.Value.manifest -and $_.Value.manifest.name }
    if($ext){ $ext | ForEach-Object { "  EXT: $($_.Value.manifest.name)  ($($_.Name))" } }
  }
}
Write-Host "`n===== PROXY SETTINGS ====="
$ie2 = Get-ItemProperty 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings' -ErrorAction SilentlyContinue
"ProxyEnable: $($ie2.ProxyEnable)  ProxyServer: $($ie2.ProxyServer)  AutoConfigURL: $($ie2.AutoConfigURL)"
