# TN-C mod jar verifier
#
#   Checks the built mod jar BEFORE the game is started, so that a
#   "compiles fine but silently does nothing" mistake cannot slip through.
#
#   Checks:
#     A. build artifact exists
#     B. META-INF/mods.toml carries the expected mod id / version / version ranges
#     C. all expected classes are inside the jar
#     D. @Mod.EventBusSubscriber is present on the classes that rely on it
#        (this is exactly the bug we hit: the event handlers compile, but are
#         never registered, so the capability silently never attaches)
#     E. class file bytecode version (61 = Java 17, 65 = Java 21)
#     F. the copy installed in the modpack is byte-identical to the build output
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File verify_mod_jar.ps1
#
# Exit code 0 = pass, 1 = problems found.

param(
    [string]$WorkPack = '',
    [string]$JarPath  = ''
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

# repo root / modpack / libs / live-instance discovery (no hardcoded D:\ModTest)
. (Join-Path $PSScriptRoot '_common.ps1')

if ([string]::IsNullOrWhiteSpace($WorkPack)) {
    $WorkPack = Find-TncWorkPack
    if (-not $WorkPack) { exit 1 }
}

if ([string]::IsNullOrWhiteSpace($JarPath)) {
    $jars = @(Get-ChildItem $TncBuildLibs -Filter 'tnc-*.jar' -ErrorAction SilentlyContinue |
              Where-Object { $_.Name -notmatch 'sources|javadoc' } | Sort-Object LastWriteTime -Descending)
    if ($jars.Count -eq 0) { Write-Host "PROBLEM: no build artifact in $TncBuildLibs"; exit 1 }
    $JarPath = $jars[0].FullName
}

$problems = 0
function Fail([string]$message) { Write-Host "  [PROBLEM] $message"; $script:problems++ }
function Ok([string]$message)   { Write-Host "  [ok]      $message" }
function Note([string]$message) { Write-Host "  [note]    $message" }

Write-Host "jar: $JarPath"
if (-not (Test-Path $JarPath)) { Write-Host "PROBLEM: jar not found"; exit 1 }
Ok ("artifact present ({0:N0} bytes)" -f (Get-Item $JarPath).Length)

$zip = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
try {
    # ---------------- B. mods.toml ----------------
    $tomlEntry = $zip.Entries | Where-Object { $_.FullName -eq 'META-INF/mods.toml' }
    if (-not $tomlEntry) {
        Fail "META-INF/mods.toml missing"
    } else {
        $reader = New-Object System.IO.StreamReader($tomlEntry.Open(), [System.Text.Encoding]::UTF8)
        $toml = $reader.ReadToEnd(); $reader.Close()

        foreach ($expected in @('modId="tnc"', 'loaderVersion="[47,)"', 'versionRange="[1.20.1,1.21)"')) {
            if ($toml -match [regex]::Escape($expected)) { Ok "mods.toml contains $expected" }
            else { Fail "mods.toml is missing $expected" }
        }
        if ($toml -match 'authors="YourNameHere') { Write-Host "  [note]    mods.toml still has the MDK placeholder author" }
    }

    # ---------------- C. expected classes ----------------
    $expectedClasses = @(
        'com/tnc/tnc/TNMod.class',
        'com/tnc/tnc/Config.class',
        'com/tnc/tnc/magic/Element.class',
        'com/tnc/tnc/magic/MagicStoneData.class',
        'com/tnc/tnc/magic/MagicStone.class',
        'com/tnc/tnc/magic/MagicStone$Provider.class',
        'com/tnc/tnc/magic/MagicStone$Registration.class',
        'com/tnc/tnc/magic/SpellCatalog.class',
        'com/tnc/tnc/magic/SpellCatalog$Entry.class',
        'com/tnc/tnc/magic/MagicStoneLearning.class',
        'com/tnc/tnc/magic/MagicStoneSelfTest.class',
        'com/tnc/tnc/magic/MagicStoneDiagnostics.class',
        'com/tnc/tnc/magic/ManaGate.class',
        'com/tnc/tnc/mixin/SpellHelperManaGateMixin.class',
        'com/tnc/tnc/magic/compat/SpellEngineBridge.class',
        'com/tnc/tnc/magic/compat/SpellEngineManaHook.class',
        'com/tnc/tnc/magic/compat/ManaGateProbe.class',
        'com/tnc/tnc/magic/compat/ManaGateProbe$Impl.class',
        'com/tnc/tnc/magic/compat/SpellEngineCaster.class',
        'com/tnc/tnc/network/MagicStoneNetwork.class',
        'com/tnc/tnc/network/MagicStoneClientSync.class',
        'com/tnc/tnc/network/MagicStoneActionPacket.class',
        'com/tnc/tnc/command/MagicStoneCommand.class',
        'com/tnc/tnc/client/MagicStoneButton.class',
        'com/tnc/tnc/client/MagicStoneScreen.class',
        'com/tnc/tnc/client/MagicStoneClientEvents.class',
        'com/tnc/tnc/client/MagicStoneHud.class',
        'com/tnc/tnc/client/MagicStoneKeys.class'
    )
    function Get-ClassText([string]$entryName) {
        $entry = $zip.Entries | Where-Object { $_.FullName -eq $entryName }
        if (-not $entry) { return $null }
        $ms = New-Object System.IO.MemoryStream
        $s = $entry.Open(); $s.CopyTo($ms); $s.Close()
        $bytes = $ms.ToArray(); $ms.Dispose()
        return [System.Text.Encoding]::ASCII.GetString($bytes)
    }

    foreach ($class in $expectedClasses) {
        if ($zip.Entries | Where-Object { $_.FullName -eq $class }) { Ok "class present: $class" }
        else { Fail "class missing from jar: $class" }
    }

    # ---------------- C2. the magic wand's data + assets ----------------
    # The wand is what makes casting possible now ("hold a wand, press a number key"),
    # and every one of these is a SILENT failure if missing:
    #   * no spell pool  -> the wand's container references a pool that does not exist
    #   * no item model  -> the item renders as the purple/black missing model
    #   * no lang entry  -> the item shows its raw translation key
    #   * no mana bar art -> the HUD bar draws as missing-texture black/purple
    foreach ($res in @('data/tnc/spell_pools/tnc_lightning.json',
                       'data/tnc/spell_assignments/magic_wand.json',
                       'assets/tnc/models/item/magic_wand.json',
                       'assets/tnc/textures/item/magic_wand.png',
                       'assets/tnc/textures/gui/mana_bar/thundermagicbar_empty.png',
                       'assets/tnc/textures/gui/mana_bar/thundermagicbar_fill.png')) {
        if ($zip.Entries | Where-Object { $_.FullName -eq $res }) { Ok "resource present: $res" }
        else { Fail "resource missing from jar: $res" }
    }

    # ---------------- D1b2. the two new lightning chains (orb / speed) ----------------
    # 15 spells live on 3 chains. Two silent failures to guard:
    #  * a spell id missing from the pool/assignment -> the wand can never hold it;
    #  * the spell JSON / icon missing from the PACK -> the spell does not exist
    #    (they live in the modpack's kubejs + its TN-C resource pack, not in the jar).
    foreach ($cls in @('com/tnc/tnc/magic/TNEffects.class',
                       'com/tnc/tnc/magic/TnSpellMechanics.class')) {
        if ($zip.Entries | Where-Object { $_.FullName -eq $cls }) { Ok "class present: $cls" }
        else { Fail "class missing from jar: $cls" }
    }

    $spellIds = @('tnc:spark', 'tnc:lightning_field', 'tnc:lightning_strike', 'tnc:lightning_storm',
                  'tnc:heavenly_thunder', 'tnc:thunder_orb', 'tnc:great_thunder_orb',
                  'tnc:orbiting_thunder_orb', 'tnc:explosive_thunder_orb', 'tnc:cataclysm_thunder_orb',
                  'tnc:lightning_haste', 'tnc:lightning_blink', 'tnc:lightning_wind',
                  'tnc:lightning_recharge', 'tnc:lightning_ascension',
                  # fire 15 (ray 5 + ball 5 + burn 5)
                  'tnc:fire_ray', 'tnc:thick_fire_ray', 'tnc:triple_fire_ray',
                  'tnc:explosive_fire_ray', 'tnc:cataclysm_fire_ray',
                  'tnc:fireball', 'tnc:great_fireball', 'tnc:giant_fireball',
                  'tnc:self_destruct', 'tnc:meteor_fireball',
                  'tnc:fire_aspect', 'tnc:ember_burn', 'tnc:blaze_burn',
                  'tnc:inferno_burn', 'tnc:total_burn',
                  # wind chain 1 (the other 10 wind spells get added when their json exists)
                  'tnc:wind_field', 'tnc:wind_speed', 'tnc:greater_wind_speed',
                  'tnc:super_wind_speed', 'tnc:wind_god_descent')
    # only the wand assignment must list everything; pools are per element and are
    # checked separately in the pack-side block below
    foreach ($asset in @('data/tnc/spell_assignments/magic_wand.json')) {
        $entry = $zip.Entries | Where-Object { $_.FullName -eq $asset }
        if (-not $entry) { Fail "missing from jar: $asset"; continue }
        $reader = New-Object System.IO.StreamReader($entry.Open())
        $text = $reader.ReadToEnd()
        $reader.Close()
        $missing = @($spellIds | Where-Object { $text -notmatch [regex]::Escape($_) })
        if ($missing.Count -eq 0) { Ok "$asset lists all 15 spells" }
        else { Fail ("$asset is missing: " + ($missing -join ', ')) }
    }

    # pack side: the spell JSONs and icons must exist SOMEWHERE, or the spells simply
    # do not exist in game (and the icons show as the missing-texture square).
    #
    # A spell may live in the pack's kubejs OR in the mod jar. Model-bearing spells
    # (projectiles) MUST be in the jar: if the same id also sits in kubejs, the kubejs
    # copy wins and silently reverts the spell to an older definition - which is how
    # the model_id was lost once and the projectile went purple-black.
    $packForSpells = Find-TncLivePack -WorkPack $WorkPack
    if ($packForSpells) {
        $spellDir = Join-Path $packForSpells 'kubejs\data\tnc\spells'
        $iconDir = Join-Path $packForSpells 'config\openloader\resources\TN-C\assets\tnc\textures\spell'
        $modSpellDir = Join-Path $TncRepoRoot 'src\main\resources\data\tnc\spells'
        $modAssetDir = Join-Path $TncRepoRoot 'src\main\resources\assets\tnc'
        $missingSpells = @()
        $missingIcons = @()
        $dupSpells = @()
        foreach ($id in $spellIds) {
            $path = $id.Substring(4)     # strip the "tnc:" prefix
            $inPack = Test-Path (Join-Path $spellDir "$path.json")
            $inMod = Test-Path (Join-Path $modSpellDir "$path.json")
            if (-not ($inPack -or $inMod)) { $missingSpells += $path }
            elseif ($inPack -and $inMod) { $dupSpells += $path }
            if (-not (Test-Path (Join-Path $iconDir "$path.png"))) { $missingIcons += $path }
        }
        if ($missingSpells.Count -eq 0) { Ok 'every chain spell json exists (pack kubejs or mod jar)' }
        else { Fail ('spell json exists in NEITHER pack kubejs NOR mod jar: ' + ($missingSpells -join ', ')) }
        if ($dupSpells.Count -gt 0) {
            Note ('spell id defined in BOTH kubejs and the mod jar - the kubejs copy wins, so the jar edits are ignored: ' + ($dupSpells -join ', '))
        }
        if ($missingIcons.Count -eq 0) { Ok 'pack has all chain spell icons' }
        else { Fail ('pack is missing spell icon: ' + ($missingIcons -join ', ')) }

        # ---- structural checks: valid json can still be a broken spell ----
        # Every one of these fails SILENTLY in game:
        #   * Gson drops unknown keys -> a typo'd field name just uses the default
        #   * an effect_id that is not registered -> the buff simply never applies
        #   * a model_id without a file -> the projectile renders as missing texture
        $registered = @('tnc:lightning_haste', 'tnc:lightning_wind',
                        'tnc:orbiting_thunder_orb', 'tnc:lightning_ascension',
                        # fire (registered in TNFireMechanics)
                        'tnc:fire_aspect', 'tnc:ember_burn', 'tnc:blaze_burn',
                        'tnc:inferno_burn', 'tnc:total_burn',
                        # wind (registered in TNWindMechanics)
                        'tnc:wind_flight', 'tnc:wind_speed_i', 'tnc:wind_speed_ii',
                        'tnc:wind_speed_iii', 'tnc:wind_power_i', 'tnc:wind_power_ii',
                        'tnc:wind_god',            # 风神降临 5 级专属标记
                        'tnc:gale_slow', 'tnc:gale_haste',
                        'tnc:wind_orb_three', 'tnc:wind_orb_five', 'tnc:wind_spirit')
        $badShape = @()
        $badTier = @()
        $badEffect = @()
        $badModel = @()
        foreach ($id in $spellIds) {
            $path = $id.Substring(4)
            # the json may live in either place - check whichever one actually has it
            $file = Join-Path $spellDir "$path.json"
            if (-not (Test-Path $file)) { $file = Join-Path $modSpellDir "$path.json" }
            if (-not (Test-Path $file)) { continue }
            $spell = $null
            try { $spell = ConvertFrom-Json ([System.IO.File]::ReadAllText($file)) }
            catch { $badShape += "$path(parse)"; continue }

            # school must be one of the engine's real school names.
            # NOTE: wind is "AIR", not "WIND" (verified against the pack's real spells)
            $school = "$($spell.school)"
            if ($school -notin @('LIGHTNING', 'FIRE', 'AIR', 'WATER', 'EARTH', 'LIGHT', 'DARK')) {
                $badShape += "$path(school=$school)"
            }
            if (-not $spell.release -or -not $spell.release.target -or -not $spell.release.target.type) {
                $badShape += "$path(release.target.type)"
            }
            if (-not $spell.impact -and -not $spell.area_impact) { $badShape += "$path(no impact)" }

            $jsonId = $spell.learn.tier
            if ($null -eq $jsonId) { $badTier += "$path(no learn.tier)" }
            elseif ($jsonId -lt 1 -or $jsonId -gt 5) { $badTier += "$path(tier=$jsonId)" }

            foreach ($impact in @($spell.impact)) {
                $eff = "$($impact.action.status_effect.effect_id)"
                # 只管 tnc: 自己的效果：别的命名空间是原版（minecraft:slowness）或别的 mod 的，
                # 我们既不该管也管不着
                if ($eff -like 'tnc:*' -and ($registered -notcontains $eff)) {
                    $badEffect += "$path -> $eff"
                }
            }

            $modelId = $null
            if ($spell.release.target.projectile) {
                $modelId = "$($spell.release.target.projectile.projectile.client_data.model.model_id)"
            } elseif ($spell.release.target.meteor) {
                $modelId = "$($spell.release.target.meteor.projectile.client_data.model.model_id)"
            }
            # 同理：只检查我们自己资源包里的模型（老法术用的是别的 mod 的模型）
            if ($modelId -like 'tnc:*') {
                $rel = $modelId -replace '^tnc:', ''
                $inPackModel = Join-Path $packForSpells "config\openloader\resources\TN-C\assets\tnc\models\$rel.json"
                $inModModel = Join-Path $modAssetDir "models\$rel.json"
                if (-not ((Test-Path $inPackModel) -or (Test-Path $inModModel))) {
                    $badModel += "$path -> $modelId (no model file in pack or jar)"
                }
                # the purple-black cube guard: the id must ALSO be in the single
                # canonical list, otherwise the client never bakes it and the
                # projectile renders as the missing model. Two half-lists used to
                # drift apart here; now TNModelBaking reads this one array.
                $modelListText = Get-ClassText 'com/tnc/tnc/client/TNProjectileModels.class'
                if (-not $modelListText -or $modelListText -notmatch [regex]::Escape($rel)) {
                    $badModel += "$path -> $modelId missing from TNProjectileModels.PROJECTILE_MODELS (never baked -> purple-black)"
                }
            }
        }
        if ($badShape.Count -eq 0) { Ok 'spell json shape ok (school / release.target / impact)' }
        else { Fail ('spell json shape broken: ' + ($badShape -join ', ')) }
        if ($badTier.Count -eq 0) { Ok 'spell json tiers all in 1..5' }
        else { Fail ('spell json bad tier: ' + ($badTier -join ', ')) }
        if ($badEffect.Count -eq 0) { Ok 'spell json effect ids all registered by TNEffects' }
        else { Fail ('spell references an unregistered effect: ' + ($badEffect -join ', ')) }
        if ($badModel.Count -eq 0) { Ok 'every projectile model referenced by a spell exists and is in the baked list' }
        else { Fail ('spell references a model that would render as a purple-black cube: ' + ($badModel -join ', ')) }

        # reverse direction: an id listed for baking with no model file is dead weight,
        # and usually means a half-finished edit of the model recipe
        $listedModels = @()
        $modelListText2 = Get-ClassText 'com/tnc/tnc/client/TNProjectileModels.class'
        if ($modelListText2) {
            foreach ($m in [regex]::Matches($modelListText2, 'projectile/[a-z0-9_]+')) { $listedModels += $m.Value }
            $listedModels = @($listedModels | Sort-Object -Unique)
        }
        $staleModels = @($listedModels | Where-Object {
            -not ((Test-Path (Join-Path $packForSpells "config\openloader\resources\TN-C\assets\tnc\models\$_.json")) -or
                  (Test-Path (Join-Path $modAssetDir "models\$_.json")))
        })
        if ($staleModels.Count -gt 0) {
            Note ('listed in PROJECTILE_MODELS but has no model file (dead entry): ' + ($staleModels -join ', '))
        }
    } else {
        Write-Host '  [note]    live pack not found - skipped the pack-side spell checks'
    }

    # ---------------- D1b3. the 6 lore scrolls (right-click reading) ----------------
    # All of these fail SILENTLY: a missing lang body shows the raw key, a missing
    # reader class makes right-click do nothing, and shipping the author-only notes
    # spoils the plot for every player at once.
    $scrollIds = @('canjuan_1a', 'canjuan_1b', 'canjuan_3', 'canjuan_4', 'canjuan_5', 'canjuan_6')
    foreach ($cls in @('com/tnc/tnc/magic/TNScrolls.class',
                       'com/tnc/tnc/magic/TNScrollItem.class',
                       'com/tnc/tnc/client/TNScrollScreen.class')) {
        if ($zip.Entries | Where-Object { $_.FullName -eq $cls }) { Ok "class present: $cls" }
        else { Fail "scroll class missing from jar: $cls" }
    }
    $scrollLang = $zip.Entries | Where-Object { $_.FullName -eq 'assets/tnc/lang/zh_cn.json' }
    if ($scrollLang) {
        $sr = New-Object System.IO.StreamReader($scrollLang.Open(), [System.Text.Encoding]::UTF8)
        $scrollLangText = $sr.ReadToEnd(); $sr.Close()
        $noBody = @()
        $noName = @()
        foreach ($sid in $scrollIds) {
            # the body must be a real multi-line text, not just the key echoed back
            if ($scrollLangText -notmatch [regex]::Escape("scroll.tnc.$sid")) { $noBody += $sid }
            if ($scrollLangText -notmatch [regex]::Escape("item.tnc.$sid")) { $noName += $sid }
        }
        if ($noBody.Count -eq 0) { Ok 'all 6 scroll bodies are in the lang file (right-click has something to show)' }
        else { Fail ('scroll body missing from lang (run tools\gen_scroll_lang.ps1): ' + ($noBody -join ', ')) }
        if ($noName.Count -eq 0) { Ok 'all 6 scroll item names are in the lang file' }
        else { Fail ('scroll item name missing from lang: ' + ($noName -join ', ')) }
        # author-only design notes must never ship. The words live in a UTF-8 json
        # side file on purpose: this script must stay ASCII-only, because Windows
        # PowerShell 5.1 reads BOM-less .ps1 files as ANSI and turns CJK literals
        # into mojibake (which also breaks the parsing outright).
        $leaks = @()
        $spoilerPath = Join-Path $TncToolsDir 'scroll_lang_extra.json'
        if (Test-Path -LiteralPath $spoilerPath) {
            $spoilerText = [System.IO.File]::ReadAllText($spoilerPath, [System.Text.Encoding]::UTF8)
            $block = [regex]::Match($spoilerText, '"spoilers"\s*:\s*\[(.*?)\]', 'Singleline')
            if ($block.Success) {
                foreach ($m in [regex]::Matches($block.Groups[1].Value, '"([^"]+)"')) { $leaks += $m.Groups[1].Value }
            }
        }
        $found = @($leaks | Where-Object { $scrollLangText -match [regex]::Escape($_) })
        if ($leaks.Count -eq 0) { Fail 'spoiler word list not found (tools\scroll_lang_extra.json -> spoilers) - leak check could not run' }
        elseif ($found.Count -eq 0) { Ok 'no author-only story notes leaked into the shipped lang' }
        else { Fail ('story spoilers shipped in lang: ' + ($found -join ', ')) }
        if ($scrollLangText -match [regex]::Escape('tooltip.tnc.scroll.read')) { Ok 'scroll has the right-click hint tooltip' }
        else { Fail 'scroll tooltip key missing (players would never know it is readable)' }
    }

    # ---------------- D1c. HUD entry point + its keybind ----------------    # Nothing HUD-side can be clicked (no cursor while playing), so the keybind IS
    # the entry. RegisterKeyMappingsEvent lives on the MOD bus - registering it on
    # the FORGE bus compiles fine and the key simply never appears in Options.
    # Guard both halves: the subscription exists, and the lang keys exist.
    # NOTE: the handler lives in the NESTED class TNMod$ClientModEvents, so that is
    # the class file that carries the reference - checking TNMod.class alone gives a
    # false alarm (which it did, once).
    $tnmodForKeys = Get-ClassText 'com/tnc/tnc/TNMod$ClientModEvents.class'
    if ($tnmodForKeys -and $tnmodForKeys.Contains('net/minecraftforge/client/event/RegisterKeyMappingsEvent')) {
        Ok "TNMod subscribes to RegisterKeyMappingsEvent (HUD keybind is registered)"
    } else {
        Fail "TNMod does not subscribe to RegisterKeyMappingsEvent - the HUD hotkey would silently never appear"
    }

    $keyText = Get-ClassText 'com/tnc/tnc/client/MagicStoneKeys.class'
    if ($keyText -and $keyText.Contains('key.tnc.open_magic_stone')) {
        Ok "MagicStoneKeys declares the open_magic_stone key mapping"
    } else {
        Fail "MagicStoneKeys does not declare the key mapping"
    }

    # a keybind with no lang entry shows its raw translation key in Options
    $langEntry = $zip.Entries | Where-Object { $_.FullName -eq 'assets/tnc/lang/zh_cn.json' }
    if ($langEntry) {
        $lr = New-Object System.IO.StreamReader($langEntry.Open(), [System.Text.Encoding]::UTF8)
        $langText = $lr.ReadToEnd(); $lr.Close()
        foreach ($k in @('key.tnc.open_magic_stone', 'key.categories.tnc')) {
            if ($langText -match [regex]::Escape($k)) { Ok "lang has $k" }
            else { Fail "lang is missing $k (Options would show a raw translation key)" }
        }
    }

    # The HUD must be a FORGE-bus subscriber; RenderGuiEvent is a game event.
    $hudAnno = Get-ClassText 'com/tnc/tnc/client/MagicStoneHud.class'
    if ($hudAnno -and $hudAnno.Contains('Lnet/minecraftforge/fml/common/Mod$EventBusSubscriber;')) {
        Ok "MagicStoneHud has @Mod.EventBusSubscriber"
    } else {
        Fail "MagicStoneHud is missing @Mod.EventBusSubscriber - the mana bar would never render"
    }


    # The assignment file is what tells the engine (BOTH sides) that this item is a
    # spell holder: SpellRegistry.loadContainers() reads data/<ns>/spell_assignments/
    # <item>.json into `containers`, and containerForItem() looks it up.
    # Without it the client never shows the spell hotbar / number keys, SILENTLY.
    # (This is exactly what happened: the wand existed and had NBT, but no assignment.)
    $assignEntry = $zip.Entries | Where-Object { $_.FullName -eq 'data/tnc/spell_assignments/magic_wand.json' }
    if ($assignEntry) {
        $ar = New-Object System.IO.StreamReader($assignEntry.Open(), [System.Text.Encoding]::UTF8)
        $assignText = $ar.ReadToEnd(); $ar.Close()
        if ($assignText -match '"is_proxy"\s*:\s*true') {
            Ok "wand assignment is a proxy container (same shape as the working mod's staffs)"
        } else {
            Fail "wand assignment should set is_proxy:true (SpellContainer.isValid() short-circuits on proxy)"
        }
        $missing2 = @()
        foreach ($spell in @('tnc:spark', 'tnc:lightning_field', 'tnc:lightning_strike',
                             'tnc:lightning_storm', 'tnc:heavenly_thunder')) {
            if ($assignText -notmatch [regex]::Escape($spell)) { $missing2 += $spell }
        }
        if ($missing2.Count -eq 0) { Ok "wand assignment lists all 5 TN-C spells" }
        else { Fail ("wand assignment is missing: " + ($missing2 -join ', ')) }
    }

    # our own pool must list exactly the 5 TN-C lightning spells (not the author's)
    $poolEntry = $zip.Entries | Where-Object { $_.FullName -eq 'data/tnc/spell_pools/tnc_lightning.json' }
    if ($poolEntry) {
        $pr = New-Object System.IO.StreamReader($poolEntry.Open(), [System.Text.Encoding]::UTF8)
        $poolText = $pr.ReadToEnd(); $pr.Close()
        $missing = @()
        foreach ($spell in @('tnc:spark', 'tnc:lightning_field', 'tnc:lightning_strike',
                             'tnc:lightning_storm', 'tnc:heavenly_thunder')) {
            if ($poolText -notmatch [regex]::Escape($spell)) { $missing += $spell }
        }
        if ($missing.Count -eq 0) { Ok "wand pool lists all 5 TN-C spells" }
        else { Fail ("wand pool is missing: " + ($missing -join ', ')) }
    }

    # the wand item must be registered (its id appears in TNMod's constant pool)
    $tnmodText = Get-ClassText 'com/tnc/tnc/TNMod.class'
    if ($tnmodText -and $tnmodText.Contains('magic_wand')) {
        Ok "TNMod registers the magic_wand item"
    } else {
        Fail "TNMod does not register magic_wand - /tnc wand would have nothing to give"
    }

    # ---------------- D. event bus annotations ----------------
    # Without this annotation the class' @SubscribeEvent methods are never
    # registered - the mod loads, compiles and simply does nothing at runtime.
    $annotation = 'Lnet/minecraftforge/fml/common/Mod$EventBusSubscriber;'
    foreach ($class in @('com/tnc/tnc/magic/MagicStone.class',
                         'com/tnc/tnc/magic/MagicStone$Registration.class',
                         'com/tnc/tnc/magic/MagicStoneDiagnostics.class',
                         'com/tnc/tnc/Config.class',
                         'com/tnc/tnc/command/MagicStoneCommand.class',
                         'com/tnc/tnc/client/MagicStoneClientEvents.class')) {
        $text = Get-ClassText $class
        if ($null -eq $text) { continue }
        if ($text.Contains($annotation)) { Ok "has @Mod.EventBusSubscriber: $class" }
        else { Fail "MISSING @Mod.EventBusSubscriber on $class (its event handlers will never run!)" }
    }

    # capability provider should really implement ICapabilitySerializable
    # (in the constant pool an implemented interface is stored as the plain
    #  internal name, without the L...; descriptor wrapper)
    $providerText = Get-ClassText 'com/tnc/tnc/magic/MagicStone$Provider.class'
    if ($providerText -and $providerText.Contains('net/minecraftforge/common/capabilities/ICapabilitySerializable')) {
        Ok "MagicStone Provider implements ICapabilitySerializable"
    } else {
        Fail "MagicStone Provider does not implement ICapabilitySerializable"
    }

    # ---------------- D1b. namespaced id arguments ----------------
    # Brigadier's StringArgumentType.string() only reads unquoted chars from
    # [0-9a-zA-Z_.+-] - ':' is NOT allowed, so "/tnc learn tnc:spark" fails with
    # "Expected whitespace to end one argument, but found trailing data".
    # Namespaced ids must go through ResourceLocationArgument.id().
    # This actually shipped once and broke /tnc learn | forget | gatetest.
    $cmdText = Get-ClassText 'com/tnc/tnc/command/MagicStoneCommand.class'
    if ($cmdText -and $cmdText.Contains('net/minecraft/commands/arguments/ResourceLocationArgument')) {
        Ok "command ids use ResourceLocationArgument (':' parses correctly)"
    } else {
        Fail "MagicStoneCommand does not use ResourceLocationArgument - namespaced ids like tnc:spark will fail to parse"
    }

    # ---------------- D2. mixin wiring ----------------
    # Forge discovers mixin configs through the MixinConfigs manifest attribute,
    # so a missing attribute silently disables every mixin we write.
    if ($zip.Entries | Where-Object { $_.FullName -eq 'tnc.mixins.json' }) {
        Ok "resource present: tnc.mixins.json"
        # Two traps that both fail SILENTLY at runtime, so guard them here:
        #  - a "plugin" entry: a mixin plugin runs during Mixin config prepare,
        #    where ModList is still null -> NPE -> InvalidMixinException ->
        #    "[net.minecraft.server.Main/FATAL]: Failed to start the minecraft server"
        #    (this actually happened once; dev never reproduces it)
        #  - "required": true: then a mismatch on a future engine version turns
        #    from "the gate stops working" into "the game will not start"
        $cfgEntry = $zip.Entries | Where-Object { $_.FullName -eq 'tnc.mixins.json' }
        $cfgReader = New-Object System.IO.StreamReader($cfgEntry.Open(), [System.Text.Encoding]::UTF8)
        $cfg = $cfgReader.ReadToEnd(); $cfgReader.Close()
        if ($cfg -match '"plugin"') {
            Fail "tnc.mixins.json declares a plugin - that crashes the server at startup (ModList is null during mixin prepare)"
        } else {
            Ok "tnc.mixins.json declares no plugin (the startup-crash trap)"
        }
        if ($cfg -match '"required"\s*:\s*false') {
            Ok "tnc.mixins.json is required:false (engine mismatch degrades instead of crashing)"
        } else {
            Fail "tnc.mixins.json is not required:false - an engine update would stop the game from starting"
        }
        # defaultRequire:0 => an injector that fails to match is a WARNING, not an
        # error. Belt and braces on top of required:false: the game must always
        # start. Silent failure is acceptable *because* /tnc gatetest and the
        # startup probe now report it loudly every launch.
        # (Matches ysjxteams in this very pack - a working Forge mod that injects
        #  into a Connector-loaded net.spell_engine class with the same setup.)
        if ($cfg -match '"defaultRequire"\s*:\s*0') {
            Ok "tnc.mixins.json uses defaultRequire:0 (a non-matching injector warns, never crashes)"
        } else {
            Fail "tnc.mixins.json should use defaultRequire:0 so a failed injection cannot break startup"
        }
    } else {
        Fail "tnc.mixins.json missing from the jar"
    }
    $manifestEntry = $zip.Entries | Where-Object { $_.FullName -eq 'META-INF/MANIFEST.MF' }
    if ($manifestEntry) {
        $reader = New-Object System.IO.StreamReader($manifestEntry.Open(), [System.Text.Encoding]::UTF8)
        $manifest = $reader.ReadToEnd(); $reader.Close()
        if ($manifest -match 'MixinConfigs:\s*tnc\.mixins\.json') {
            Ok "manifest declares MixinConfigs: tnc.mixins.json"
        } else {
            Fail "manifest has no MixinConfigs attribute (mixins would never load!)"
        }
    } else {
        Fail "META-INF/MANIFEST.MF missing from the jar"
    }

    # ---------------- D3. mixin injection targets match the engine ----------------
    # A single wrong character in a mixin method descriptor means the injection
    # silently never happens. So: pull the descriptors out of our mixin class and
    # compare them against javap of the real engine jar.
    $engineJar = Get-ChildItem $TncLibsDir -Filter 'spell_engine-*.jar' -ErrorAction SilentlyContinue | Select-Object -First 1
    $mixinText = Get-ClassText 'com/tnc/tnc/mixin/SpellHelperManaGateMixin.class'
    if (-not $engineJar) {
        Write-Host "  [note]    $TncLibsDir\spell_engine-*.jar not found, skipping mixin target check"
        Write-Host "  [note]    run tools\fetch-libs.ps1 to fetch it from the modpack"
    } elseif (-not $mixinText) {
        Write-Host "  [note]    mana gate mixin class not in jar, skipping mixin target check"
    } else {
        $ours = [regex]::Matches($mixinText, 'attemptCasting\([^()]*\)Lnet/spell_engine/internals/casting/SpellCast\$Attempt;') |
                ForEach-Object { $_.Value } | Sort-Object -Unique

        # The 3-arg attemptCasting is a pure forwarder (bytecode: iconst_1 +
        # invokestatic of the 4-arg), so injecting the 4-arg alone covers every
        # caller. The config is required:false + defaultRequire:1, which means
        # EVERY extra injection point is another way for the whole config to fail
        # silently. So pin the count at exactly one.
        $injectCount = ([regex]::Matches($mixinText, 'Lorg/spongepowered/asm/mixin/injection/Inject;')).Count
        if ($injectCount -eq 1) {
            Ok "mixin has exactly 1 @Inject (one silent-failure path, covers all callers)"
        } else {
            Fail "mixin has $injectCount @Inject annotations, expected exactly 1 (each extra one can silently disable the whole gate)"
        }

        $spellPower = Get-ChildItem $TncLibsDir -Filter 'spell_power-*.jar' -ErrorAction SilentlyContinue | Select-Object -First 1
        # javap comes from whatever JDK is around (JAVA_HOME / the launcher's bundled
        # runtime / PATH) - it used to be hardcoded to one user's folder.
        $javapExe = Find-TncJdkTool -Name 'javap.exe'
        if (-not $javapExe) {
            Write-Host "  [note]    javap.exe not found (set JAVA_HOME), skipping mixin target check"
        } else {
        $cp = if ($spellPower) { "$($engineJar.FullName);$($spellPower.FullName)" } else { $engineJar.FullName }
        $engineDump = & $javapExe -p -s -classpath $cp 'net.spell_engine.internals.SpellHelper' 2>&1 | Out-String

        if ($ours.Count -eq 0) {
            Fail "could not find any attemptCasting descriptor inside our mixin class"
        }
        foreach ($descriptor in $ours) {
            # javap -s prints "descriptor: (...)...;" without the method name,
            # so compare the descriptor body only.
            $body = $descriptor.Substring('attemptCasting'.Length)
            if ($engineDump.Contains($body)) {
                Ok "mixin target matches engine: $descriptor"
            } else {
                Fail "mixin target NOT found in engine (injection would silently do nothing): $descriptor"
            }
        }
        }
    }
    # ---------------- E. bytecode version ----------------
    $tnmod = $zip.Entries | Where-Object { $_.FullName -eq 'com/tnc/tnc/TNMod.class' }
    if ($tnmod) {
        $ms = New-Object System.IO.MemoryStream
        $s = $tnmod.Open(); $s.CopyTo($ms); $s.Close()
        $bytes = $ms.ToArray(); $ms.Dispose()
        $major = ($bytes[6] -shl 8) -bor $bytes[7]
        $name = switch ($major) { 61 { 'Java 17' } 65 { 'Java 21' } default { "unknown ($major)" } }
        Ok "bytecode major version $major ($name)"
    }
} finally {
    $zip.Dispose()
}

# ---------------- F. installed copy ----------------
# mods\ is never synced into the workspace copy, so the installed jar has to be
# looked up in the live game instance. That instance is discovered (its folder
# name is non-ASCII and every launcher nests it differently) instead of hardcoded.
$livePack = Find-TncLivePack -WorkPack $WorkPack
$installed = if ($livePack) { Join-Path $livePack ("mods\" + (Split-Path $JarPath -Leaf)) } else { $null }
if (-not $livePack) {
    Fail "live game instance not found (searched the usual launcher folders for '$(Split-Path $WorkPack -Leaf)')"
    Write-Host "  [note]    pass -WorkPack explicitly, or just ignore this when only building"
} elseif (-not (Test-Path $installed)) {
    Fail "not installed into the live game instance: $installed"
} else {
    if ((Get-FileHash $JarPath).Hash -eq (Get-FileHash $installed).Hash) {
        Ok "installed copy is identical to the build output"
    } else {
        Fail "installed copy differs from the build output (rebuild + copy again)"
    }
}
if ($livePack -and (Test-Path (Join-Path $livePack 'mods'))) {
    $modCount = (Get-ChildItem (Join-Path $livePack 'mods') -File -Filter *.jar).Count
    Ok "live instance mods\ now holds $modCount jars"
}

Write-Host ''
if ($problems -gt 0) { Write-Host "$problems problem(s) found."; exit 1 }
Write-Host "mod jar verification passed."
