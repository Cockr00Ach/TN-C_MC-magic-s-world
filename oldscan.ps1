$ErrorActionPreference='SilentlyContinue'
$ProgressPreference='SilentlyContinue'
$today = Get-Date
Write-Host "TODAY: $($today.ToString('yyyy-MM-dd'))"

$roots = @('C:\','D:\','E:\','G:\')
$results = New-Object System.Collections.ArrayList

function Scan-Root([string]$root){
  $top = Get-ChildItem -LiteralPath $root -Directory -Force -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notin @('$RECYCLE.BIN','System Volume Information','$Recycle.Bin') }
  foreach($d in $top){
    $files = Get-ChildItem -LiteralPath $d.FullName -Recurse -File -Force -ErrorAction SilentlyContinue
    if(-not $files){ continue }
    $agg = $files | Measure-Object -Property Length -Sum
    $newest = ($files | Sort-Object LastWriteTime -Descending | Select-Object -First 1).LastWriteTime
    $oldest = ($files | Sort-Object LastWriteTime | Select-Object -First 1).LastWriteTime
    $age = [int]($today - $newest).TotalDays
    [void]$results.Add([pscustomobject]@{
      Root=$root; Name=$d.Name; MB=[math]::Round($agg.Sum/1MB,1)
      Files=$agg.Count; Newest=$newest.ToString('yyyy-MM-dd'); AgeDays=$age
      Oldest=$oldest.ToString('yyyy-MM-dd')
    })
    Write-Host ("  scanned {0}{1} -> {2} MB, newest {3} ({4}d)" -f $root,$d.Name,[math]::Round($agg.Sum/1MB,1),$newest.ToString('yyyy-MM-dd'),$age)
  }
}
foreach($r in $roots){ if(Test-Path -LiteralPath $r){ Write-Host "=== $r ==="; Scan-Root $r } }

$results | Export-Csv -LiteralPath 'D:\ModTest\old_dirs.csv' -NoTypeInformation -Encoding UTF8
Write-Host "`n=== STALE (>=120 days since newest file), sorted by size ==="
$results | Where-Object { $_.AgeDays -ge 120 } | Sort-Object MB -Descending |
  Select-Object -First 60 Root,Name,MB,Files,Newest,AgeDays | Format-Table -AutoSize | Out-String -Width 200
Write-Host "`n=== TOP 40 LARGEST dirs (any age) ==="
$results | Sort-Object MB -Descending | Select-Object -First 40 Root,Name,MB,Files,Newest,AgeDays | Format-Table -AutoSize | Out-String -Width 200
