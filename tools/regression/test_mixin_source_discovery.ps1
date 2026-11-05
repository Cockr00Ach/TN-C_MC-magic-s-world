$ErrorActionPreference = 'Stop'
$repo = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$verifier = Join-Path $repo 'tools/verify_mod_jar.ps1'
$parseErrors = $null; $parseTokens = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile($verifier, [ref]$parseTokens, [ref]$parseErrors)
if ($parseErrors.Count) { throw 'Verifier has syntax errors' }
$assignment = $ast.FindAll({ param($node)
    $node -is [System.Management.Automation.Language.AssignmentStatementAst] -and
    $node.Left -is [System.Management.Automation.Language.VariableExpressionAst] -and
    $node.Left.VariablePath.UserPath -eq 'mixinSources'
}, $true) | Select-Object -First 1
if (-not $assignment) { throw 'Actual source-discovery expression not found' }
$expression = $assignment.Right.Extent.Text
$parent = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\')
$fixture = [System.IO.Path]::GetFullPath((Join-Path $parent ('tnc-mixin-discovery-' + [guid]::NewGuid().ToString('N'))))
if ((Split-Path $fixture -Parent) -ne $parent -or (Split-Path $fixture -Leaf) -notlike 'tnc-mixin-discovery-*') { throw 'Unsafe fixture location' }
try {
    $mixinSourceRoot = $fixture
    $namespace = Join-Path $fixture 'com/tnc/tnc'
    New-Item -ItemType Directory -Path $namespace -Force | Out-Null
    foreach ($name in @('FixtureMixin.java','FixtureInvoker.java','FixtureAccessor.java','PlainHelper.java')) {
        [System.IO.File]::WriteAllText((Join-Path $namespace $name),'// source-discovery fixture')
    }
    $actual = @(Invoke-Expression $expression | ForEach-Object { $_.Name })
    foreach ($expected in @('FixtureMixin.java','FixtureInvoker.java','FixtureAccessor.java')) {
        if ($actual -notcontains $expected) { throw "Actual verifier failed to discover $expected" }
    }
    if ($actual -contains 'PlainHelper.java') { throw 'Ordinary helper incorrectly treated as a mixin' }
    $mixinSourceRoot = Join-Path $repo 'src/main/java'
    $actual = @(Invoke-Expression $expression | ForEach-Object { $_.Name })
    if ($actual -notcontains 'BronzeHandInvoker.java') { throw 'Real paired-hand invoker is still undiscoverable' }
    Write-Output 'PASS: actual verifier discovers Mixin/Invoker/Accessor sources and excludes ordinary helpers'
} finally {
    # The absolute fixture and its parent were checked before creating this directory.
    if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture -Recurse -Force }
}
