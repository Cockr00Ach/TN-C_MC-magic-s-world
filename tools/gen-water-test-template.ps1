$ErrorActionPreference='Stop'
$target=Join-Path (Split-Path $PSScriptRoot -Parent) 'src\main\resources\data\tnc\structures\water_test_large_empty.nbt'
$memory=New-Object IO.MemoryStream
$writer=New-Object IO.BinaryWriter($memory)
function Int32BE([int]$Value){$bytes=[BitConverter]::GetBytes($Value);[Array]::Reverse($bytes);$writer.Write($bytes)}
function NbtString([string]$Value){$bytes=[Text.Encoding]::UTF8.GetBytes($Value);$writer.Write([byte](($bytes.Length -shr 8) -band 255));$writer.Write([byte]($bytes.Length -band 255));$writer.Write($bytes)}
function Header([byte]$Type,[string]$Name){$writer.Write($Type);NbtString $Name}
Header 10 ''; Header 3 'DataVersion';Int32BE 3465
Header 9 'size';$writer.Write([byte]3);Int32BE 3;Int32BE 32;Int32BE 32;Int32BE 32
Header 9 'palette';$writer.Write([byte]10);Int32BE 1;Header 8 'Name';NbtString 'minecraft:air';$writer.Write([byte]0)
Header 9 'blocks';$writer.Write([byte]10);Int32BE (32*32*32)
# A size declaration does not clear the test volume: explicitly replace every voxel with air.
for($x=0;$x -lt 32;$x++){for($y=0;$y -lt 32;$y++){for($z=0;$z -lt 32;$z++){
    Header 9 'pos';$writer.Write([byte]3);Int32BE 3;Int32BE $x;Int32BE $y;Int32BE $z
    Header 3 'state';Int32BE 0;$writer.Write([byte]0)
}}}
Header 9 'entities';$writer.Write([byte]10);Int32BE 0;$writer.Write([byte]0);$writer.Flush();$memory.Position=0
$file=[IO.File]::Create($target)
$gzip=New-Object IO.Compression.GZipStream($file,[IO.Compression.CompressionMode]::Compress)
try{$memory.CopyTo($gzip)}finally{$gzip.Dispose();$file.Dispose();$writer.Dispose();$memory.Dispose()}
Write-Output "Generated isolated 32x32x32 water test fixture: $target"
