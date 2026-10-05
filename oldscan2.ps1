$ErrorActionPreference='SilentlyContinue'
$ProgressPreference='SilentlyContinue'
$today = Get-Date
Write-Host "TODAY: $($today.ToString('yyyy-MM-dd'))"

$exclude = @(
  'C:\Windows','C:\ProgramData','C:\Program Files','C:\Program Files (x86)',
  'C:\$Recycle.Bin','C:\$WinREAgent','C:\Recovery','C:\PerfLogs','C:\System Volume Information',
  'C:\Users\FDCX\AppData','C:\Users\FDCX\Desktop','C:\Users\FDCX\Downloads','C:\Users\FDCX\Documents',
  'C:\Users\FDCX\OneDrive','D:\Program Files','D:\Program Files (x86)','D:\$RECYCLE.BIN',
  'E:\Program Files','E:\programfiles','E:\$RECYCLE.BIN','E:\System Volume Information',
  'E:\Genshin Impact Game','E:\Star Rail Game','E:\ZenlessZoneZero Game','E:\SteamLibrary',
  'E:\sw','E:\toolbox','E:\solid','E:\SolidWorks 2024 SP4.0','E:\download\正式版 2.12.6.1',
  'D:\Xilinx','D:\SteamLibrary','D:\Program Files'
)

$results = New-Object System.Collections.ArrayList

function Walk([string]$path,[string]$root,[int]$depth){
  if($depth -gt 3){ return }
  $items = Get-ChildItem -LiteralPath $path -Force -ErrorAction SilentlyContinue
  foreach($d in ($items | Where-Object { $_.PSIsContainer -and -not ($_.Attributes -band [IO.FileAttributes]::ReparsePoint) })){
    $full = $d.FullName
    if($exclude -contains $full){ continue }
    # measure this subtree
    $files = Get-ChildItem -LiteralPath $full -Recurse -File -Force -ErrorAction SilentlyContinue |
             Where-Object { -not ($_.Attributes -band [IO.FileAttributes]::ReparsePoint) }
    $rel = $full.Substring($root.Length).TrimStart('\')
    if($files){
      $agg = $files | Measure-Object -Property Length -Sum
      $newest = ($files | Sort-Object LastWriteTime -Descending | Select-Object -First 1).LastWriteTime
      $age = [int]($today - $newest).TotalDays
      [void]$results.Add([pscustomobject]@{
        Root=$root; Path=$rel; MB=[math]::Round($agg.Sum/1MB,1)
        Files=$agg.Count; Newest=$newest.ToString('yyyy-MM-dd'); AgeDays=$age
      })
      if($depth -le 2){
        Write-Host ("  {0,-45} {1,9:N1} MB  newest {2} ({3}d)" -f $rel,[math]::Round($agg.Sum/1MB,1),$newest.ToString('yyyy-MM-dd'),$age)
      }
    }
    Walk $full $root ($depth+1)
  }
}

foreach($r in @('C:\','D:\','E:\')){
  if(-not (Test-Path -LiteralPath $r)){ continue }
  Write-Host "=== $r ==="
  Walk $r $r 1
}

$results | Export-Csv -LiteralPath 'D:\ModTest\old_dirs.csv' -NoTypeInformation -Encoding UTF8
Write-Host "`n=== STALE DIRS (newest file >= 180 days ago), by size ==="
$results | Where-Object { $_.AgeDays -ge 180 } | Sort-Object MB -Descending |
  Select-Object -First 50 Root,Path,MB,Files,Newest,AgeDays | Format-Table -AutoSize | Out-String -Width 200
Write-Host "`n=== TOP 30 LARGEST top-level-ish dirs (any age) ==="
$results | Sort-Object MB -Descending | Select-Object -First 30 Root,Path,MB,Files,Newest,AgeDays | Format-Table -AutoSize | Out-String -Width 200
Write-Host "`nTOTAL stale reclaimable: {0:N1} GB" -f ((($results | Where-Object { $_.AgeDays -ge 180 } | Measure-Object MB -Sum).Sum)/1024)
