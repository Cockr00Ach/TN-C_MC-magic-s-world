$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'

function Get-DirBytes([string]$path){
  if(-not (Test-Path -LiteralPath $path)){ return 0 }
  $s = Get-ChildItem -LiteralPath $path -Recurse -File -Force -ErrorAction SilentlyContinue |
       Measure-Object -Property Length -Sum
  if($null -eq $s.Sum){ return 0 }
  return [int64]$s.Sum
}
function Report([string]$label,[int64]$bytes){
  Write-Host ("{0,-42} {1,12:N1} MB" -f $label, ($bytes/1MB))
}

$before = (Get-PSDrive E).Free
Write-Host "==== E: free before: {0:N2} GB ====" -f ($before/1GB)
Write-Host ""

# --- A1: Recycle Bin (E:) ---
$rb = 'E:\$RECYCLE.BIN'
$sz = Get-DirBytes $rb
Get-ChildItem -LiteralPath $rb -Force -ErrorAction SilentlyContinue |
  Where-Object { $_.Name -ne 'desktop.ini' } |
  ForEach-Object { Remove-Item -LiteralPath $_.FullName -Recurse -Force -ErrorAction SilentlyContinue }
Report "A1 Recycle Bin (E:)" $sz

# --- A2: LeStoreDownload (Lenovo store partial downloads) ---
$le = 'E:\LeStoreDownload'
$sz = Get-DirBytes $le
Remove-Item -LiteralPath $le -Recurse -Force -ErrorAction SilentlyContinue
Report "A2 LeStoreDownload" $sz

# --- A3: empty leftover cache dirs ---
$empties = @('E:\5EDemocache','E:\KDubaSoftDownloads','E:\Wins优化文件搬家')
$tot = 0
foreach($d in $empties){
  if(Test-Path -LiteralPath $d){
    $tot += Get-DirBytes $d
    Remove-Item -LiteralPath $d -Recurse -Force -ErrorAction SilentlyContinue
    if(Test-Path -LiteralPath $d){ Write-Host "  (kept, not empty: $d)" }
  }
}
Report "A3 empty cache dirs" $tot

# --- A4: system + user temp ---
$tot = 0
foreach($t in @('C:\Windows\Temp', $env:TEMP)){
  if(-not (Test-Path -LiteralPath $t)){ continue }
  $tot += Get-DirBytes $t
  Get-ChildItem -LiteralPath $t -Force -ErrorAction SilentlyContinue |
    ForEach-Object { Remove-Item -LiteralPath $_.FullName -Recurse -Force -ErrorAction SilentlyContinue }
}
Report "A4 Windows + user Temp (in-use skipped)" $tot

# --- B1: duplicate installer, keep oldest-named original ---
$keep = '字由 Setup 4.1.2.exe'
$tot = 0
Get-ChildItem -LiteralPath 'E:\download' -File -Filter '字由*' -Force -ErrorAction SilentlyContinue |
  Where-Object { $_.Name -ne $keep } |
  ForEach-Object { $tot += $_.Length; Remove-Item -LiteralPath $_.FullName -Force -ErrorAction SilentlyContinue }
Report ("B1 duplicate installers (kept '{0}')" -f $keep) $tot

# --- B2: obsolete installers in E:\download ---
$files = @(
  'idea-2026.2.2.exe',
  'kimi_3.2.11.exe',
  'CloudCompare_v2.14.beta_setup_x64.exe',
  'QuarkPC_V2.6.0.313_pc_pf30002_(zh-cn)_release_(Build2169902-250424213124-x64).exe',
  'blender.pdb'
)
$tot = 0
foreach($f in $files){
  $p = Join-Path 'E:\download' $f
  if(Test-Path -LiteralPath $p){
    $tot += (Get-Item -LiteralPath $p -Force).Length
    Remove-Item -LiteralPath $p -Force -ErrorAction SilentlyContinue
  }
}
Report "B2 obsolete installers / blender.pdb" $tot

# --- C1: Minecraft auto-backups, keep newest per version ---
$versRoot = 'E:\download\正式版 2.12.6.1\.minecraft\versions'
$tot = 0
if(Test-Path -LiteralPath $versRoot){
  foreach($v in (Get-ChildItem -LiteralPath $versRoot -Directory -Force -ErrorAction SilentlyContinue)){
    $b = Join-Path $v.FullName 'backups'
    if(-not (Test-Path -LiteralPath $b)){ continue }
    $zips = Get-ChildItem -LiteralPath $b -File -Force -ErrorAction SilentlyContinue |
            Where-Object { $_.Extension -eq '.zip' } | Sort-Object LastWriteTime -Descending
    if($zips.Count -le 1){ continue }
    Write-Host ("  {0}: keeping {1}" -f $v.Name, $zips[0].Name)
    $zips | Select-Object -Skip 1 | ForEach-Object {
      $tot += $_.Length
      Remove-Item -LiteralPath $_.FullName -Force -ErrorAction SilentlyContinue
    }
  }
}
Report "C1 Minecraft old auto-backups" $tot

# --- D1: Linux/WSL trash on E: ---
$tr = 'E:\.Trash-1000'
$sz = Get-DirBytes $tr
Remove-Item -LiteralPath $tr -Recurse -Force -ErrorAction SilentlyContinue
Report "D1 .Trash-1000 (Linux trash)" $sz

Write-Host ""
$after = (Get-PSDrive E).Free
Write-Host "==== E: free after:  {0:N2} GB ====" -f ($after/1GB)
Write-Host "==== reclaimed:      {0:N2} GB ====" -f (($after-$before)/1GB)
