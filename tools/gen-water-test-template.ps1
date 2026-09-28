param([ValidateRange(32,144)][int]$fixtureSize=48,[ValidateRange(0,64)][int]$fixtureHeight=0,[ValidateSet('water_test_large_empty','water_test_crypt_empty','water_test_ocean_empty')][string]$fixtureName='water_test_large_empty')
$ErrorActionPreference='Stop'
if($fixtureHeight -eq 0){$fixtureHeight=[Math]::Min(64,$fixtureSize)}
$target=Join-Path (Split-Path $PSScriptRoot -Parent) "src\main\resources\data\tnc\structures\$fixtureName.nbt"
$memory=New-Object IO.MemoryStream
$writer=New-Object IO.BinaryWriter($memory)
function Int32BE([int]$Value){$bytes=[BitConverter]::GetBytes($Value);[Array]::Reverse($bytes);$writer.Write($bytes)}
function NbtString([string]$Value){$bytes=[Text.Encoding]::UTF8.GetBytes($Value);$writer.Write([byte](($bytes.Length -shr 8) -band 255));$writer.Write([byte]($bytes.Length -band 255));$writer.Write($bytes)}
function Header([byte]$Type,[string]$Name){$writer.Write($Type);NbtString $Name}
Header 10 ''; Header 3 'DataVersion';Int32BE 3465
Header 9 'size';$writer.Write([byte]3);Int32BE 3;Int32BE $fixtureSize;Int32BE $fixtureHeight;Int32BE $fixtureSize
Header 9 'palette';$writer.Write([byte]10);Int32BE 1;Header 8 'Name';NbtString 'minecraft:air';$writer.Write([byte]0)
Header 9 'blocks';$writer.Write([byte]10);Int32BE ($fixtureSize*$fixtureHeight*$fixtureSize)
# A size declaration does not clear the test volume: explicitly replace every voxel with air.
for($x=0;$x -lt $fixtureSize;$x++){for($y=0;$y -lt $fixtureHeight;$y++){for($z=0;$z -lt $fixtureSize;$z++){
    Header 9 'pos';$writer.Write([byte]3);Int32BE 3;Int32BE $x;Int32BE $y;Int32BE $z
    Header 3 'state';Int32BE 0;$writer.Write([byte]0)
}}}
Header 9 'entities';$writer.Write([byte]10);Int32BE 0;$writer.Write([byte]0);$writer.Flush();$memory.Position=0
$file=[IO.File]::Create($target)
$gzip=New-Object IO.Compression.GZipStream($file,[IO.Compression.CompressionMode]::Compress)
try{$memory.CopyTo($gzip)}finally{$gzip.Dispose();$file.Dispose();$writer.Dispose();$memory.Dispose()}
Write-Output "Generated isolated ${fixtureSize}x${fixtureHeight}x${fixtureSize} water test fixture: $target"
