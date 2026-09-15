# Build the dev-only "SpellEngine stub" fixture and drop it into run\mods\
#
#   Why: the real SpellEngine is a Fabric mod that only exists inside the modpack
#   (loaded through Sinytra Connector). That means our Mixin has NO target in the
#   dev environment, so we cannot tell whether the mixin wiring is even correct.
#
#   The stub gives us a class with the same name and byte-identical method
#   signatures (including the 3-arg -> 4-arg delegation direction, read out of the
#   real engine with javap -c), so the dev server can prove the whole chain:
#       config loads -> descriptor matches -> injection runs -> ManaGate executes
#       -> the gate distinguishes "no mana" from "enough mana"
#
#   ## Why the stub jar also carries our mixin class and config
#
#   Mixin resolves the mixin class (and the config resource) from the CONTAINER
#   THAT DECLARES THE CONFIG. The stub jar declares it via the MixinConfigs
#   manifest attribute, so Mixin looks for the mixin class and the config
#   *inside the stub jar*. Without those the config loads but the injection
#   silently never happens - the false negative that wasted a previous round.
#
#   ## Why the class is moved to a different package (com.tnc.xnc.mixin)
#
#   Simply copying the class in fails immediately: ModLauncher builds a JPMS
#   module layer, and two modules containing the same package is a split package:
#
#     java.lang.module.ResolutionException: Module spell_engine contains package
#     com.tnc.tnc.mixin, module tnc exports package com.tnc.tnc.mixin to spell_engine
#
#   So the stub gets its own copy of the mixin in a package only it owns.
#   The rewrite is a BYTE-level string swap of two SAME-LENGTH names
#   (com/tnc/tnc/mixin -> com/tnc/xnc/mixin, both 17 chars), so no class-file
#   UTF8 constant length has to be patched - the copy stays valid and is
#   byte-identical to the real mixin apart from its own package name.
#   Everything that matters (the @Mixin target, the @Inject descriptor, the
#   body calling ManaGate) is untouched, and the packaged descriptor is asserted
#   against the real one at the end of this script so the fixture cannot drift.
#
#   !!! The stub must NEVER be installed while playing the real modpack, and
#   !!! never be copied into the modpack folder. It is a dev-only fixture.
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File build-stub-engine.ps1          # build + install
#   powershell -NoProfile -ExecutionPolicy Bypass -File build-stub-engine.ps1 -Remove  # uninstall
#
# Exit code 0 = ok.

param(
    [switch]$Remove,
    [string]$ForgeJar = ''
)

$ErrorActionPreference = 'Stop'

$fixture   = $PSScriptRoot
$stubDir   = Join-Path $fixture 'stub-engine'
$outJar    = Join-Path $fixture 'spell_engine-0.0.0-devstub.jar'
$runMods   = 'D:\ModTest\run\mods'
$installed = Join-Path $runMods 'spell_engine-0.0.0-devstub.jar'

function Assert-NoGameRunning {
    $procs = @(Get-CimInstance Win32_Process -Filter "Name like '%java%'" -ErrorAction SilentlyContinue)
    foreach ($p in $procs) {
        $cl = [string]$p.CommandLine
        if ($cl -match 'forgeclient' -or $cl -match '\.minecraft\\versions') {
            Write-Host "ERROR: a Minecraft game process is running (pid $($p.ProcessId)) - refusing to touch mods\."
            Write-Host "       Close the game first (never kill it blindly - see tools\kill-dev-java.ps1)."
            exit 1
        }
    }
}

if ($Remove) {
    if (Test-Path $installed) { Remove-Item $installed -Force; Write-Host "removed $installed" }
    else { Write-Host "nothing to remove" }
    exit 0
}

Assert-NoGameRunning

# ---- pull our config + compiled mixin class out of the real build output ----
$modRoot   = 'D:\ModTest'
$configSrc = Join-Path $modRoot 'build\resources\main\tnc.mixins.json'
$mixinSrc  = Join-Path $modRoot 'build\classes\java\main\com\tnc\tnc\mixin'

if (-not (Test-Path $configSrc)) {
    Write-Host "ERROR: $configSrc not found - run '.\gradlew.bat build' first"
    exit 1
}
if (-not (Test-Path $mixinSrc)) {
    Write-Host "ERROR: $mixinSrc not found - run '.\gradlew.bat build' first"
    exit 1
}
$mixinClasses = @(Get-ChildItem $mixinSrc -Filter '*.class')
if ($mixinClasses.Count -eq 0) {
    Write-Host "ERROR: no compiled mixin classes in $mixinSrc"
    exit 1
}
Write-Host "config    : $configSrc"
Write-Host "mixins    : $($mixinClasses.Count) class file(s)"

# ---- locate the mapped Forge jar (net.minecraft.*) + FML language jar (net.minecraftforge.fml.*) ----
if ([string]::IsNullOrWhiteSpace($ForgeJar)) {
    $candidate = Get-ChildItem 'C:\Users\FDCX\.gradle\caches\forge_gradle\minecraft_user_repo\net\minecraftforge\forge' -Directory |
                 Where-Object { $_.Name -like '1.20.1-*_mapped_official_*' } |
                 Sort-Object Name -Descending | Select-Object -First 1
    if (-not $candidate) { Write-Host 'ERROR: no mapped Forge jar in the gradle cache'; exit 1 }
    $ForgeJar = (Get-ChildItem $candidate.FullName -Filter '*.jar' | Select-Object -First 1).FullName
}
# 把 Gradle 缓存里所有 Forge 47.4.22 的 artifact 都放上 classpath
# （eventbus / fmlloader / javafmllanguage / coremods / forgespi ... 一个个加太容易漏）
$fmlJars = @(Get-ChildItem 'C:\Users\FDCX\.gradle\caches\modules-2\files-2.1\net.minecraftforge' -Recurse -Filter '*1.20.1-47.4.22*.jar' -ErrorAction SilentlyContinue |
             ForEach-Object { $_.FullName })
if ($fmlJars.Count -eq 0) { Write-Host 'ERROR: no Forge 47.4.22 artifacts found in the gradle cache'; exit 1 }
$classpath = (@($ForgeJar) + $fmlJars) -join ';'
Write-Host "forge jar : $ForgeJar"
Write-Host "fml jars  : $($fmlJars.Count)"

$javac = 'C:\Users\FDCX\AppData\Roaming\.minecraft\runtime\java-runtime-beta\bin\javac.exe'
$jarExe = 'C:\Users\FDCX\AppData\Roaming\.minecraft\runtime\java-runtime-beta\bin\jar.exe'
if (-not (Test-Path $javac)) { Write-Host "ERROR: javac not found at $javac"; exit 1 }

# ---- compile the stub engine ----
$classes = Join-Path $fixture 'build\classes'
if (Test-Path $classes) { Remove-Item $classes -Recurse -Force }
New-Item -ItemType Directory -Force -Path $classes | Out-Null

$sources = Get-ChildItem -Recurse -File (Join-Path $stubDir 'src') -Filter *.java | ForEach-Object { $_.FullName }
Write-Host "compiling $($sources.Count) source file(s)"
& $javac -nowarn -encoding UTF-8 -source 17 -target 17 -cp $classpath -d $classes @sources
if ($LASTEXITCODE -ne 0) { Write-Host 'ERROR: javac failed'; exit 1 }

# ---- stage: mods.toml, mixin config, mixin classes, manifest ----
New-Item -ItemType Directory -Force -Path (Join-Path $classes 'META-INF') | Out-Null
Copy-Item (Join-Path $stubDir 'mods.toml') (Join-Path $classes 'META-INF\mods.toml')

# The declaring container must own the config resource and the mixin class.
# Latin-1 is byte-preserving for all 256 byte values, so GetString/GetBytes round
# trips the class file exactly and the ASCII identifiers can be swapped in place.
$latin1 = [System.Text.Encoding]::GetEncoding(28591)
$fromPkg = 'com/tnc/tnc/mixin'
$toPkg   = 'com/tnc/xnc/mixin'
if ($fromPkg.Length -ne $toPkg.Length) {
    Write-Host 'ERROR: package rewrite must be same-length to keep class-file constants valid'
    exit 1
}

$mixinDest = Join-Path $classes 'com\tnc\xnc\mixin'
New-Item -ItemType Directory -Force -Path $mixinDest | Out-Null
foreach ($c in $mixinClasses) {
    $bytes = [System.IO.File]::ReadAllBytes($c.FullName)
    $text  = $latin1.GetString($bytes)
    $hits  = ([regex]::Matches($text, [regex]::Escape($fromPkg))).Count
    if ($hits -eq 0) {
        Write-Host "  [PROBLEM] $($c.Name) does not reference $fromPkg - unexpected mixin class?"
        exit 1
    }
    $moved  = $text.Replace($fromPkg, $toPkg)
    $outCls = Join-Path $mixinDest $c.Name
    [System.IO.File]::WriteAllBytes($outCls, $latin1.GetBytes($moved))
    Write-Host ("  rewrote {0} -> {1} ({2} occurrence(s), {3} bytes)" -f $c.Name, $toPkg, $hits, $bytes.Length)
}

# same rewrite for the config's "package" field, under a dev-only file name so it
# can never be confused with the real tnc.mixins.json
$configText = Get-Content $configSrc -Raw
if ($configText -notmatch [regex]::Escape('com.tnc.tnc.mixin')) {
    Write-Host "ERROR: $configSrc does not mention com.tnc.tnc.mixin"
    exit 1
}
$configText = $configText.Replace('com.tnc.tnc.mixin', 'com.tnc.xnc.mixin')
Set-Content -Path (Join-Path $classes 'tnc-devstub.mixins.json') -Value $configText -Encoding UTF8
Write-Host '  staged tnc-devstub.mixins.json (package com.tnc.xnc.mixin)'

$manifest = Join-Path $classes 'MANIFEST.MF'
@(
    'Manifest-Version: 1.0',
    'MixinConfigs: tnc-devstub.mixins.json',
    ''
) | Set-Content -Path $manifest -Encoding ASCII

# ---- package ----
if (Test-Path $outJar) { Remove-Item $outJar -Force }
Push-Location $classes
try {
    & $jarExe cfm $outJar $manifest -C $classes .
    if ($LASTEXITCODE -ne 0) { Write-Host 'ERROR: jar failed'; exit 1 }
} finally {
    Pop-Location
}
Remove-Item $manifest -Force

# ---- sanity check the packaged contents ----
$verify = @(& $jarExe tf $outJar)
foreach ($needed in @('tnc-devstub.mixins.json',
                      'com/tnc/xnc/mixin/SpellHelperManaGateMixin.class',
                      'net/spell_engine/internals/SpellHelper.class',
                      'META-INF/mods.toml')) {
    if ($verify -contains $needed) { Write-Host "  [ok] packaged: $needed" }
    else { Write-Host "  [PROBLEM] missing from jar: $needed"; exit 1 }
}
# the real class must NOT be in there, or the JPMS module layer rejects the build
if ($verify -contains 'com/tnc/tnc/mixin/SpellHelperManaGateMixin.class') {
    Write-Host '  [PROBLEM] the original package leaked into the stub -> split package, game will not start'
    exit 1
}
Write-Host '  [ok] original com/tnc/tnc/mixin absent (no JPMS split package)'

# ---- the fixture must not drift from the real mixin ----
# Compare the @Inject target descriptor inside the stub's copy against the real
# class file. If the real mixin is ever retargeted, this fails loudly instead of
# silently testing yesterday's wiring.
$realBytes = [System.IO.File]::ReadAllBytes((Join-Path $mixinSrc 'SpellHelperManaGateMixin.class'))
$realText  = $latin1.GetString($realBytes)
$pattern   = 'attemptCasting\([^()]*\)Lnet/spell_engine/internals/casting/SpellCast\$Attempt;'
$realDesc  = ([regex]::Matches($realText, $pattern) | ForEach-Object { $_.Value } | Sort-Object -Unique)
$stubBytes = [System.IO.File]::ReadAllBytes((Join-Path $mixinDest 'SpellHelperManaGateMixin.class'))
$stubText  = $latin1.GetString($stubBytes)
$stubDesc  = ([regex]::Matches($stubText, $pattern) | ForEach-Object { $_.Value } | Sort-Object -Unique)
if (($realDesc -join '|') -ne ($stubDesc -join '|')) {
    Write-Host '  [PROBLEM] stub mixin descriptor differs from the real mixin (fixture drifted)'
    exit 1
}
Write-Host "  [ok] stub descriptor matches the real mixin ($($realDesc.Count) target)"
Write-Host "built     : $outJar"

# ---- install into run\mods ----
New-Item -ItemType Directory -Force -Path $runMods | Out-Null
Copy-Item $outJar $installed -Force
Write-Host "installed : $installed"
Write-Host ''
Write-Host 'Now run:  .\gradlew.bat runServer'
Write-Host 'Expect   : "TN-C mana gate probe [server start]: 3/3 passed"'
Write-Host '           (plus self-test 20/20). Without the stub the probe reports "skipped".'
