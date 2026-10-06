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

# Deep JSON -> PSCustomObject, for Windows PowerShell 5.1.
#
# WHY: PS 5.1's ConvertFrom-Json has no -Depth and defaults to a nesting limit of 2,
# and past it the value is SILENTLY replaced (a nested object becomes a string), so
# `$spell.release.target.cloud.entity_type_id` reads back as "" while the file is in
# fact correct. That silently disabled the model check for the black fog chain (the
# first spell whose interesting fields sit at depth 5).
#
# JavaScriptSerializer has a real RecursionLimit; its Dictionary output is then
# round-tripped through ConvertTo/From-Json so the dot-access below keeps working.
Add-Type -AssemblyName System.Web.Extensions
$script:TncJson = New-Object System.Web.Script.Serialization.JavaScriptSerializer
$script:TncJson.RecursionLimit = 100
function ConvertFrom-TncSpellJson([string]$text) {
    $raw = $script:TncJson.DeserializeObject($text)
    if ($null -eq $raw) { return $null }
    return ($script:TncJson.Serialize($raw) | ConvertFrom-Json)
}

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
                       'com/tnc/tnc/magic/TnSpellMechanics.class',
                       # black fog chain (2026-10-09): the cloud subclass + the cast listener.
                       # BOTH must be in the jar: without the entity class the fog cannot be
                       # created at all, and without the mechanics class the boundary circle
                       # is never placed (and that failure is silent in game).
                       'com/tnc/tnc/magic/DarkFogCloudEntity.class',
                       'com/tnc/tnc/magic/DarkFogMechanics.class')) {
        if ($zip.Entries | Where-Object { $_.FullName -eq $cls }) { Ok "class present: $cls" }
        else { Fail "class missing from jar: $cls" }
    }

    $spellIds = @('tnc:spark', 'tnc:lightning_field', 'tnc:lightning_strike', 'tnc:lightning_storm',
                  'tnc:heavenly_thunder', 'tnc:thunder_orb', 'tnc:great_thunder_orb',
                  'tnc:god_descent', 'tnc:explosive_thunder_orb', 'tnc:cataclysm_thunder_orb',
                  'tnc:lightning_field', 'tnc:lightning_storm',
                  'tnc:lightning_haste', 'tnc:lightning_blink', 'tnc:lightning_wind',
                  'tnc:lightning_recharge', 'tnc:lightning_ascension',
                  # fire 15 (ray 5 + ball 5 + burn 5)
                  'tnc:sun_ray', 'tnc:blast_ray', 'tnc:triple_fire_ray',
                  'tnc:explosive_fire_ray', 'tnc:cataclysm_fire_ray',
                  'tnc:fireball', 'tnc:great_fireball', 'tnc:lava_fireball',
                  'tnc:molten_skyfall', 'tnc:meteor_fall',
                  'tnc:fire_aspect', 'tnc:ember_burn', 'tnc:blaze_burn',
                  'tnc:inferno_burn', 'tnc:total_burn',
                  # wind chain 1 (the other 10 wind spells get added when their json exists)
                  'tnc:wind_field', 'tnc:wind_speed', 'tnc:greater_wind_speed',
                  'tnc:super_wind_speed', 'tnc:wind_god_descent',
                  # dark chain 4 "black fog" (2026-10-09: real fog particles, the
                  # tnc:projectile/dark_fog dome, tnc:dark_veil, the tnc:fog cloud entity)
                  'tnc:black_mist', 'tnc:night_grace', 'tnc:dark_city',
                  'tnc:where_light_cannot_reach', 'tnc:devour_light',
                  # dark chain 2 "dark sacrifice" (2026-10-09: it used to borrow the FIRE
                  # burn effects, which add spell_power:fire => the whole chain's reward
                  # was worth zero to a dark spell. Now tnc:blood_* = spell_power:soul.)
                  'tnc:trade_wounds', 'tnc:blood_burn', 'tnc:sacrifice',
                  'tnc:possess', 'tnc:i_am_god')
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
            $kubeIcon=Join-Path $packForSpells "kubejs\assets\tnc\textures\spell\$path.png"
            $modIcon=Join-Path $modAssetDir "textures\spell\$path.png"
            if (-not ((Test-Path (Join-Path $iconDir "$path.png")) -or (Test-Path $kubeIcon) -or (Test-Path $modIcon))) { $missingIcons += $path }
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
                        'tnc:god_descent', 'tnc:lightning_ascension',
                        # tnc:lightning_field / tnc:lightning_storm (primary field+storm markers, TNEffects)
                        'tnc:lightning_field', 'tnc:lightning_storm',
                        # fire (registered in TNFireMechanics)
                        'tnc:fire_aspect', 'tnc:ember_burn', 'tnc:blaze_burn',
                        'tnc:inferno_burn', 'tnc:total_burn',
                        # fire ball chain (2026-10-05)：施法标记 + 命中灼伤
                        #   fire_cast  —— 法术 JSON 里 release.target=SELF 的那条标记（TNEffects 注册）
                        #   burning_body —— 焚身，由自有实体在命中时施加（TNScorch 注册）
                        'tnc:fire_cast', 'tnc:burning_body',
                        # wind (registered in TNWindMechanics)
                        'tnc:wind_flight', 'tnc:wind_speed_i', 'tnc:wind_speed_ii',
                        'tnc:wind_speed_iii', 'tnc:wind_power_i', 'tnc:wind_power_ii',
                        'tnc:wind_god',            # 风神降临 5 级专属标记
                        'tnc:gale_slow', 'tnc:gale_haste',
                        'tnc:wind_orb_three', 'tnc:wind_orb_five', 'tnc:wind_spirit',
                        # dark chain 4 "black fog": our own debuff, replacing the borrowed
                        # tnc:gale_slow (a WIND effect on a dark spell). Registered in TNEffects.
                        'tnc:dark_veil',
                        # dark chain 4 blindness (2026-10-10): everyone standing in the fog,
                        # monsters included. Vanilla `minecraft:darkness` only darkens a
                        # PLAYER's screen, so it cannot blind a mob -- hence our own effect.
                        'tnc:dark_fog',
                        # dark chain 2 "dark sacrifice": the five soul-power steps, replacing the
                        # borrowed tnc:fire_* effects (which add spell_power:fire => zero reward
                        # for a dark spell). Registered in TNEffects.
                        'tnc:blood_mark', 'tnc:blood_burn', 'tnc:blood_sacrifice',
                        'tnc:blood_possess', 'tnc:blood_god',
                        # dark chain 2 lifesteal (2026-10-09): the temporary MAX HEALTH the
                        # caster gains from draining an enemy. Registered in TNEffects.
                        'tnc:blood_pool',
                        # dark chain 2/3 shared amplifier (+50% soul per amplifier step).
                        # Registered all along but MISSING from this whitelist -- the check
                        # only stayed green because no dark spell was in $spellIds yet.
                        'tnc:dark_power')

        # ★ SPAWN actions: the entity id must be one WE register, otherwise the engine
        # silently spawns nothing (or the vanilla default) and the whole mechanic is a
        # no-op in game with no error anywhere. Our own types live in TNOrbEntities /
        # TNNpcs; vanilla + the other mods' ids are not ours to police.
        $ourEntities = @()
        foreach ($sourceFile in @('com/tnc/tnc/magic/TNOrbEntities.class',
                                  'com/tnc/tnc/npc/TNNpcs.class')) {
            $text = Get-ClassText $sourceFile
            if ($text) { $ourEntities += [regex]::Matches($text, 'tnc:[a-z0-9_]+') | ForEach-Object { $_.Value } }
        }
        $ourEntities = @($ourEntities | Sort-Object -Unique)
        $badSpawn = @()
        $badShape = @()
        $badTier = @()
        $badEffect = @()
        $badModel = @()
        # area_impact 的"位置"陷阱，分成两份：
        #   jar 里的法术（我们自己维护）→ Fail；kubejs 里author手改的那份 → Note
        #   （规则本身对两边都成立，只是改 kubejs 的数值属于作者的地盘，工具不该挡装机 ✗）
        $badAreaJar = @()
        $badAreaPack = @()
        foreach ($id in $spellIds) {
            $path = $id.Substring(4)
            # the json may live in either place - check whichever one actually has it
            $file = Join-Path $spellDir "$path.json"
            if (-not (Test-Path $file)) { $file = Join-Path $modSpellDir "$path.json" }
            if (-not (Test-Path $file)) { continue }
            $fromJar = $file.StartsWith($modSpellDir, [System.StringComparison]::OrdinalIgnoreCase)
            $spell = $null
            # NOTE: no ConvertFrom-Json -Depth on PS 5.1 -- see ConvertFrom-TncSpellJson
            try { $spell = ConvertFrom-TncSpellJson ([System.IO.File]::ReadAllText($file)) }
            catch { $badShape += "$path(parse)"; continue }

            # school must be one of the engine's real school names.
            # NOTE: wind is "AIR", not "WIND" (verified against the pack's real spells)
            $school = "$($spell.school)"
            # dark is "SOUL", not "DARK" (all 20 of our dark spells say SOUL, and the
            # engine's dark school is spell_power:soul) -- "DARK" never matched anything,
            # which silently skipped every dark spell in this check until the black fog
            # chain was added to $spellIds below.
            if ($school -notin @('LIGHTNING', 'FIRE', 'AIR', 'WATER', 'EARTH', 'LIGHT', 'SOUL', 'ARCANE', 'HEALING')) {
                $badShape += "$path(school=$school)"
            }
            if (-not $spell.release -or -not $spell.release.target -or -not $spell.release.target.type) {
                $badShape += "$path(release.target.type)"
            }
            if (-not $spell.impact -and -not $spell.area_impact) { $badShape += "$path(no impact)" }

            # ---- area_impact 必须有一个"能给出坐标"的 release.target ----
            # SpellEngine 0.15.12 的 SpellHelper.lookupAndPerformAreaImpact 第一句就是
            #   point = context.position()          （反编译确认）
            # 然后把它丢给 TargetHelper.targetsFromArea(...) 做距离判定。而遍历
            # performSpell 的分支可以看到，**只有** AREA / BEAM / PROJECTILE / METEOR /
            # CLOUD / SHOOT_ARROW 会给这个 position 赋值；SELF 与 CURSOR 走的是
            # directImpact(...)，position 从头到尾是 null ⇒ 施法当场
            #   NullPointerException: Cannot read field "f_82479_" because "point" is null
            #   at net.spell_engine.utils.VectorHelper.distanceVector
            # 症状极具迷惑性：音效、施法粒子、impact 里的冲击波都出来了，然后**没有伤害、
            # 不扣魔力、Java 侧那套表现一个都不出现**（2026-09-29 god_descent 就是这么坏的）。
            if ($spell.area_impact) {
                $areaTarget = "$($spell.release.target.type)"
                if ($areaTarget -eq 'SELF' -or $areaTarget -eq 'CURSOR') {
                    # printed text stays ASCII (this script is BOM-less UTF-8; CJK would mojibake)
                    $entry = "$path (release.target=$areaTarget + area_impact -> engine NPE on every cast)"
                    if ($fromJar) { $badAreaJar += $entry } else { $badAreaPack += $entry }
                }
            }

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
                # dark sacrifice must never go back to borrowing the FIRE line: those add
                # spell_power:fire, so a dark spell gets no reward for burning its own HP.
                if ($path -in @('trade_wounds', 'blood_burn', 'sacrifice', 'possess', 'i_am_god') `
                        -and $eff -like 'tnc:fire_*') {
                    $badEffect += "$path -> $eff (FIRE effect on a dark chain: the reward would be spell_power:fire = 0 for this spell)"
                }
                # SPAWN: a `tnc:` entity id that we do not register makes the engine
                # spawn nothing and the mechanic becomes a silent no-op
                $spawnId = "$($impact.action.spawn.entity_type_id)"
                if ($spawnId -like 'tnc:*' -and ($ourEntities -notcontains $spawnId)) {
                    $badSpawn += "$path -> $spawnId (not registered by TNOrbEntities / TNNpcs)"
                }
                foreach ($extra in @($impact.action.spawns)) {
                    $spawnId2 = "$($extra.entity_type_id)"
                    if ($spawnId2 -like 'tnc:*' -and ($ourEntities -notcontains $spawnId2)) {
                        $badSpawn += "$path -> $spawnId2 (not registered by TNOrbEntities / TNNpcs)"
                    }
                }
            }

            $modelId = $null
            if ($spell.release.target.projectile) {
                $modelId = "$($spell.release.target.projectile.projectile.client_data.model.model_id)"
            } elseif ($spell.release.target.meteor) {
                $modelId = "$($spell.release.target.meteor.projectile.client_data.model.model_id)"
            } elseif ($spell.release.target.cloud) {
                # CLOUD targets can carry a model too (SpellCloudRenderer feeds it to
                # CustomModels.render, the same path projectiles use). The black fog chain
                # is the first user of that slot -- a wrong id here would draw the
                # purple-black cube in the middle of the fog.
                $modelId = "$($spell.release.target.cloud.client_data.model.model_id)"
                # the fog must use OUR cloud entity, otherwise it never follows the caster
                # and never hosts the boundary circle (that wiring lives in
                # DarkFogCloudEntity, and the engine creates it from this id)
                $cloudType = "$($spell.release.target.cloud.entity_type_id)"
                if ($path -match '^(black_mist|night_grace|dark_city|where_light_cannot_reach|devour_light)$' -and $cloudType -ne 'tnc:fog') {
                    $badModel += "$path -> cloud.entity_type_id=$cloudType (want tnc:fog, else no follow / no boundary circle)"
                }
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
        if ($badSpawn.Count -eq 0) { Ok "every SPAWN references an entity type we register ($($ourEntities.Count) known ids)" }
        else { Fail ('SPAWN references an entity type nobody registers (the engine then spawns nothing - silent no-op): ' + ($badSpawn -join ', ')) }
        if ($badModel.Count -eq 0) { Ok 'every projectile model referenced by a spell exists and is in the baked list' }
        else { Fail ('spell references a model that would render as a purple-black cube: ' + ($badModel -join ', ')) }
        if ($badAreaJar.Count -eq 0) {
            Ok 'no jar spell pairs a position-less release target (SELF/CURSOR) with area_impact'
        } else {
            Fail ('these spells throw a NullPointerException on every cast (SELF/CURSOR never fills the area-impact position): ' + ($badAreaJar -join ', '))
        }
        if ($badAreaPack.Count -gt 0) {
            Note ('pack kubejs spells with the same cast-time crash (author-owned data, fix when convenient): ' + ($badAreaPack -join ', '))
        }

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
        # The check is scoped to the CHEST-LEGEND SCROLLS on purpose. Those must never
        # name anyone - the 卷 -> character mapping is the whole reveal. The main-story
        # record sheets are the opposite: they are a chronicle written with full names,
        # so they are excluded here.
        $scrollBodies = ''
        foreach ($m in [regex]::Matches($scrollLangText, '"scroll\.tnc\.(?:canjuan_[a-z0-9_]+)"\s*:\s*"((?:[^"\\]|\\.)*)"')) {
            $scrollBodies += $m.Groups[1].Value
        }
        $found = @($leaks | Where-Object { $scrollBodies -match [regex]::Escape($_) })
        if ($leaks.Count -eq 0) { Fail 'spoiler word list not found (tools\scroll_lang_extra.json -> spoilers) - leak check could not run' }
        elseif ($found.Count -eq 0) { Ok 'no character names / author-only notes leaked into the chest-legend scroll text' }
        else { Fail ('story spoilers leaked into the scroll text: ' + ($found -join ', ')) }
        if ($scrollLangText -match [regex]::Escape('tooltip.tnc.scroll.read')) { Ok 'scroll has the right-click hint tooltip' }
        else { Fail 'scroll tooltip key missing (players would never know it is readable)' }
    }

    # ---------------- D1b4. the TN-C skin NPCs ----------------
    # Adding an NPC touches six places (entity type, attributes, spawn egg, renderer,
    # default placement, purge list). Missing the skin PNG or the lang entry does not
    # crash: the NPC is simply invisible / shows a raw translation key.
    #
    # The name on the left is the entity id; the name on the right is what
    # TnDialogueNpc.skinName() returns for it (usually the same, but an NPC whose
    # Bedrock model paints the body at non-vanilla UVs ships a separate
    # "<id>_humanoid.png", exactly like zhuangquerang).
    $npcSkins = [ordered]@{
        'self'           = 'self'
        'cava'           = 'cava'
        'huai'           = 'huai'
        'zhuangquerang'  = 'zhuangquerang_humanoid'
        'zuowang'        = 'zuowang_humanoid'
    }
    $npcLangText = $null
    $npcLangEntry = $zip.Entries | Where-Object { $_.FullName -eq 'assets/tnc/lang/zh_cn.json' }
    if ($npcLangEntry) {
        $nr = New-Object System.IO.StreamReader($npcLangEntry.Open(), [System.Text.Encoding]::UTF8)
        $npcLangText = $nr.ReadToEnd(); $nr.Close()
    }
    $missingSkin = @()
    $missingName = @()
    $missingScript = @()
    foreach ($npc in $npcSkins.Keys) {
        $skin = $npcSkins[$npc]
        if (-not ($zip.Entries | Where-Object { $_.FullName -eq "assets/tnc/textures/entity/$skin.png" })) { $missingSkin += "$npc ($skin.png)" }
        if ($npcLangText -and $npcLangText -notmatch [regex]::Escape("entity.tnc.$npc")) { $missingName += $npc }
        $found = $zip.Entries | Where-Object { $_.FullName -like "data/tnc/dialogues/$npc*.txt" }
        if (-not $found) { $missingScript += $npc }
    }
    if ($missingSkin.Count -eq 0) { Ok "every skin NPC has its skin texture in the jar ($($npcSkins.Count) npcs)" }
    else { Fail ('npc skin missing from jar (renders as a missing texture): ' + ($missingSkin -join ', ')) }
    if ($missingName.Count -eq 0) { Ok 'every skin NPC has a lang name' }
    else { Fail ('npc lang name missing (shows a raw key): ' + ($missingName -join ', ')) }
    if ($missingScript.Count -eq 0) { Ok 'every skin NPC has a dialogue script' }
    else { Fail ('npc dialogue script missing (right-click does nothing useful): ' + ($missingScript -join ', ')) }

    # ---- the script prefix must be derivable from dialogueId(), NOT skinName() ----
    # DialoguePicker resolves an NPC's scripts by the PREFIX of its dialogueId.
    # A skin name is a texture file name and may differ (zhuangquerang's fallback
    # skin is "zhuangquerang_humanoid", deliberately).  Passing skinName() there
    # once made her completely undialogueable: the log said
    #   "no script at all for npc 'zhuangquerang_humanoid'"
    # while every file it needed was sitting right there.  This gate pins the link.
    $prefixBad = @()
    foreach ($npc in $npcSkins.Keys) {
        $cls = $zip.Entries | Where-Object { $_.FullName -eq "com/tnc/tnc/npc/${npc}NpcEntity.class" }
        if (-not $cls) { continue }
        $cs = New-Object System.IO.StreamReader($cls.Open(), [System.Text.Encoding]::UTF8)
        $cbytes = New-Object System.IO.MemoryStream
        $cls.Open().CopyTo($cbytes) | Out-Null
        $ctext = [System.Text.Encoding]::ASCII.GetString($cbytes.ToArray())
        $cs.Close(); $cbytes.Dispose()
        # the first dialogue file that actually exists tells us the prefix in use
        $hasBase = $zip.Entries | Where-Object { $_.FullName -eq "data/tnc/dialogues/$npc.txt" }
        $hasNumbered = $zip.Entries | Where-Object { $_.FullName -like "data/tnc/dialogues/${npc}_*.txt" }
        if (-not ($hasBase -or $hasNumbered)) { continue }
        # the class must mention the prefix somewhere (its FIRST_DIALOGUE constant)
        if ($ctext -notmatch [regex]::Escape($npc)) {
            $prefixBad += "$npc (class does not reference prefix '$npc')"
        }
    }
    if ($prefixBad.Count -eq 0) { Ok 'every NPC dialogue prefix matches its shipped scripts' }
    else { Fail ('NPC dialogue prefix problem (right-click finds NO script): ' + ($prefixBad -join ', ')) }

    # ---- dialogue directive sanity ----
    # The loader tests @directives with startsWith, so a LONGER keyword must be
    # tested before any keyword that prefixes it (@activate before @act,
    # @quests before @quest).  Getting that wrong made five scripts fail to parse
    # entirely -- in game it just looked like "this NPC will not talk"
    # (2026-09-29: "@act" swallowed "@activate", leaving the action name
    # "ivate tnc:main/s1_leave").  A broken @act line is easy to spot: a real
    # action name is one word and never contains ':' or '/'.
    $badDirectives = @()
    $actCount = 0
    foreach ($e in $zip.Entries) {
        if ($e.FullName -notlike 'data/tnc/dialogues/*.txt') { continue }
        $dr = New-Object System.IO.StreamReader($e.Open(), [System.Text.Encoding]::UTF8)
        $dtext = $dr.ReadToEnd(); $dr.Close()
        foreach ($m in [regex]::Matches($dtext, '(?m)^\s*@act\s+(\S+)\s*$')) {
            $actCount++
            $value = $m.Groups[1].Value
            if ($value.Contains(':') -or $value.Contains('/')) {
                $badDirectives += "$($e.FullName) -> @act $value"
            }
        }
        # @id must match the path the loader will use as the key, or the script is
        # loaded under a wrong key (DialogueLoader.get() builds the file path as
        # `dialogues/<id.getPath()>.txt` and then asserts the file's @id == that id).
        #
        # ★ 2026-10-10 fix: the expectation used to be "namespace + file name only",
        # which is WRONG for files in a sub-directory. The tavern guests live in
        # `dialogues/tavern/guest_NN.txt` and are opened with
        #     ResourceLocation.fromNamespaceAndPath("tnc", "tavern/" + seatId)
        # (TavernGuestEntity.mobInteract) => the id is `tnc:tavern/guest_NN`, and the
        # loader then looks for `dialogues/tavern/guest_NN.txt` -- self-consistent.
        # The old rule demanded `tnc:guest_NN`, i.e. it would have "fixed" 49 correct
        # files into broken ones (the loader would then look for
        # `dialogues/guest_NN.txt`, which does not exist => genuinely mute NPCs).
        # So: expect `tnc:` + the path RELATIVE to data/tnc/dialogues, minus ".txt".
        $idMatch = [regex]::Match($dtext, '(?m)^\s*@id\s+(\S+)\s*$')
        if ($idMatch.Success) {
            $rel = $e.FullName.Substring('data/tnc/dialogues/'.Length)
            if ($rel.EndsWith('.txt')) { $rel = $rel.Substring(0, $rel.Length - 4) }
            $expect = 'tnc:' + $rel
            if ($idMatch.Groups[1].Value -ne $expect) {
                $badDirectives += "$($e.FullName) -> @id $($idMatch.Groups[1].Value) (expected $expect)"
            }
        } else {
            $badDirectives += "$($e.FullName) -> missing @id"
        }
    }
    if ($badDirectives.Count -eq 0) { Ok "dialogue directives are well-formed ($actCount @act, ids match file names)" }
    else { Fail ('dialogue directive problem (the script fails to load; the NPC looks mute): ' + ($badDirectives -join '; ')) }

    # ---- DialogueLoader must test @act LAST ----
    # The loader matches directives with startsWith, so `@activate` also matches
    # the `@act` prefix.  With @act tested first, "@activate tnc:main/s1_leave"
    # is read as the action name "ivate tnc:main/s1_leave" and the WHOLE script
    # fails to parse -- in game the NPC simply will not talk.  This shipped twice
    # (2026-09-29).  Checking the source order is crude but it is the real rule.
    $loaderPath = Join-Path $TncRepoRoot 'src\main\java\com\tnc\tnc\dialogue\DialogueLoader.java'
    if (Test-Path $loaderPath) {
        $loaderSrc = [IO.File]::ReadAllText($loaderPath, [System.Text.Encoding]::UTF8)
        $actAt = $loaderSrc.IndexOf('line.equals("@act")')
        if($actAt -lt 0){$actAt=$loaderSrc.IndexOf('line.startsWith("@act")')}
        $activateAt = $loaderSrc.IndexOf('line.startsWith("@activate")')
        $questsAt = $loaderSrc.IndexOf('line.startsWith("@quests")')
        $questAt = $loaderSrc.IndexOf('line.startsWith("@quest")')
        if ($actAt -lt 0) {
            Fail 'DialogueLoader: could not find the @act branch (did the parser change?)'
        } elseif ($activateAt -lt 0 -or $questsAt -lt 0 -or $questAt -lt 0) {
            Fail 'DialogueLoader: a directive branch is missing (@activate/@quests/@quest)'
        } elseif ($actAt -lt $activateAt -or $questsAt -gt $questAt) {
            # NOTE: keep this on ONE line -- a bare '+' at line start inside a
            # function call is a PowerShell parse error, not a continuation.
            Fail ("DialogueLoader directive order is wrong: @act is at $actAt but @activate is at $activateAt (and @quests must precede @quest). @act MUST be tested last, otherwise @activate parses as an action name and the whole script dies.")
        } else {
            Ok 'DialogueLoader tests @act after @activate/@quests/@quest (prefix-shadowing safe)'
        }
    }

    # ---- every Bedrock-model NPC must ship its geometry + texture ----
    # These two are HARD requirements: without the .geo.json nothing is drawn at all,
    # without the texture the model renders as the purple-black missing texture.
    # The ANIMATION file is deliberately NOT required: a model with no animation file
    # still renders fine (it just stands there), which is the intended state for any
    # NPC whose Blockbench work has not landed yet.
    $geoAssets = [ordered]@{
        'self'    = @('geo/entity/self.geo.json',    'textures/entity/self_bedrock.png')
        'zuowang' = @('geo/entity/zuowang.geo.json', 'textures/entity/zuowang_bedrock.png')
    }
    $missingGeo = @()
    foreach ($npc in $geoAssets.Keys) {
        foreach ($rel in $geoAssets[$npc]) {
            if (-not ($zip.Entries | Where-Object { $_.FullName -eq "assets/tnc/$rel" })) { $missingGeo += "$npc -> $rel" }
        }
    }
    if ($missingGeo.Count -eq 0) { Ok "every Bedrock-model NPC ships its geometry + texture ($($geoAssets.Count) npcs)" }
    else { Fail ('Bedrock-model NPC asset missing (invisible or purple-black in game): ' + ($missingGeo -join ', ')) }

    # ---- every quest id a dialogue references must name a real quest ----
    # A wrong id here fails SILENTLY in game: the dialogue still plays fine, the
    # quest just never completes (that is exactly how "talked to Self, quest did
    # not finish" happened).  Checked for all four directives:
    #   @quest / @quests  complete a quest   @activate  start one
    #   @requires / @excludes  gate which script the picker may choose
    # A bad id in @requires is just as silent: the script simply never plays.
    $questPack = Find-TncLivePack -WorkPack $WorkPack
    if ($questPack) {
        $questDir = Join-Path $questPack 'kubejs\data\tnc\whisperingquests\tasks'
        $badQuestRefs = @()
        $questRefCount = 0
        foreach ($e in $zip.Entries) {
            if ($e.FullName -notlike 'data/tnc/dialogues/*.txt') { continue }
            $dr = New-Object System.IO.StreamReader($e.Open(), [System.Text.Encoding]::UTF8)
            $dtext = $dr.ReadToEnd(); $dr.Close()
            $ids = @()
            foreach ($m in [regex]::Matches($dtext, '(?m)^\s*@quest\s+(\S+)\s*$'))  { $ids += $m.Groups[1].Value }
            foreach ($m in [regex]::Matches($dtext, '(?m)^\s*@quests\s+(.+)$'))     { $ids += ($m.Groups[1].Value.Trim() -split '\s+') }
            foreach ($m in [regex]::Matches($dtext, '(?m)^\s*@activate\s+(\S+)\s*$')) { $ids += $m.Groups[1].Value }
            foreach ($m in [regex]::Matches($dtext, '(?m)^\s*@requires\s+(\S+)\s*$')) { $ids += $m.Groups[1].Value }
            foreach ($m in [regex]::Matches($dtext, '(?m)^\s*@excludes\s+(\S+)\s*$')) { $ids += $m.Groups[1].Value }
            foreach ($qid in $ids) {
                $questRefCount++
                # tnc:main/s1_self -> <tasks>\main\s1_self.json
                # NOTE: strip the namespace, then keep the FULL path after it --
                # the file lives under tasks/<category>/, not directly under tasks/.
                $rel = if ($qid.Contains(':')) { $qid.Substring($qid.IndexOf(':') + 1) } else { $qid }
                $relPath = $rel -replace '/', '\'
                if (-not (Test-Path (Join-Path $questDir ($relPath + '.json')))) {
                    $badQuestRefs += "$($e.FullName) -> $qid"
                }
            }
        }
        if ($badQuestRefs.Count -eq 0) { Ok "every quest id a dialogue references exists ($questRefCount reference(s))" }
        else { Fail ('dialogue references a quest that does not exist (the dialogue plays, the quest silently never completes): ' + ($badQuestRefs -join ', ')) }
    }

    # ---- schema check on every quest WE author ----
    # The engine throws JsonParseException and DROPS the offending quest, which
    # also silently breaks the chapter it belongs to.  A whole quest book looked
    # empty this way (2026-09-29).  Cheap to check, expensive to debug in game.
    $questPack2 = Find-TncLivePack -WorkPack $WorkPack
    if ($questPack2) {
        $tasksRoot = Join-Path $questPack2 "kubejs\data\tnc\whisperingquests\tasks"
        $badSchema = @()
        $checked = 0
        if (Test-Path $tasksRoot) {
            foreach ($file in Get-ChildItem -Recurse $tasksRoot -Filter *.json -File) {
                $checked++
                $qtxt = [IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
                try { $qobj = $qtxt | ConvertFrom-Json }
                catch { $badSchema += "$($file.Name): not valid JSON"; continue }

                foreach ($obj in @($qobj.objectives)) {
                    if (-not $obj) { continue }
                    $t = [string]$obj.type
                    if ($t -eq 'location') {
                        # must carry a nested position object; flat x/y/z is rejected
                        if (-not $obj.position) {
                            $badSchema += "$($file.Name): location objective '$($obj.id)' has no 'position' object"
                        } elseif ($null -eq $obj.position.x -or $null -eq $obj.position.y -or $null -eq $obj.position.z) {
                            $badSchema += "$($file.Name): location objective '$($obj.id)' position needs x/y/z"
                        }
                    }
                    elseif ($t -eq 'kill' -and -not $obj.entity) {
                        $badSchema += "$($file.Name): kill objective '$($obj.id)' has no 'entity'"
                    }
                    elseif ($t -eq 'structure' -and -not $obj.structure) {
                        $badSchema += "$($file.Name): structure objective '$($obj.id)' has no 'structure'"
                    }
                    elseif ($t -eq 'dimension' -and -not $obj.dimension) {
                        $badSchema += "$($file.Name): dimension objective '$($obj.id)' has no 'dimension'"
                    }
                    elseif ($t -eq 'item' -and -not $obj.item -and -not $obj.item_tag) {
                        $badSchema += "$($file.Name): item objective '$($obj.id)' has neither 'item' nor 'item_tag'"
                    }
                    if ($obj.type -notin @('dialogue','kill','item','location','dimension','biome','structure','advancement')) {
                        $badSchema += "$($file.Name): objective '$($obj.id)' has unknown type '$($obj.type)'"
                    }
                }

                # ---- NO 'choice' REWARDS on a story quest (2026-09-30, from bytecode) ----
                # The dialogue bridge finishes a quest by calling claimReward(player, id),
                # which is the 2-arg overload -> the 3-arg one with List.of().
                # That overload begins with:
                #     if (selections.size() != <count of rewards with type=="choice">) return false;
                # With List.of() the size is 0, so ANY 'choice' reward makes claimReward
                # return false BEFORE finishQuest is ever reached -- finishQuest is the only
                # writer of completedQuests.  The quest then stays "objective done but not
                # finished" for ever, so the NEXT segment's @requires never passes and that
                # NPC looks permanently stuck on its first segment.
                # A choice reward must be picked by hand in the quest book, which is exactly
                # what we do not want on the main line.
                foreach ($rw in @($qobj.rewards)) {
                    if ($rw -and ([string]$rw.type) -eq 'choice') {
                        $badSchema += "$($file.Name): has a 'choice' reward -- claimReward(List.of()) returns false, so the dialogue bridge can NEVER finish this quest; split it or use item/xp rewards"
                    }
                }
            }
        }
        if ($badSchema.Count -eq 0) { Ok "every TN-C quest passes the objective schema + reward-type rule ($checked file(s))" }
        else { Fail ('quest schema problem - the engine DROPS bad quests silently and their chapter looks empty, and a choice reward makes the dialogue bridge unable to finish the quest: ' + ($badSchema -join '; ')) }
    }

    # ---- the maid-model NPC: GeckoLib renders it, so three files must ship together ----
    # Missing any of them is SILENT: no geometry = nothing drawn, wrong animation name =
    # frozen model, missing texture = the purple-black missing texture.
    $maidAssets = @(
        'assets/tnc/geo/entity/zhuangquerang.geo.json',
        'assets/tnc/animations/entity/zhuangquerang.animation.json',
        'assets/tnc/textures/entity/zhuangquerang.png',
        'assets/tnc/textures/entity/zhuangquerang_humanoid.png'
    )
    $missingMaid = @($maidAssets | Where-Object { -not ($zip.Entries | Where-Object { $_.FullName -eq $_ }) })
    if ($missingMaid.Count -eq 0) { Ok 'maid-model NPC ships geometry + animation + both textures' }
    else { Fail ('maid-model NPC asset missing (wrong or invisible render): ' + ($missingMaid -join ', ')) }
    foreach ($cls in @('com/tnc/tnc/npc/ZhuangquerangMaidNpcEntity.class',
                       'com/tnc/tnc/npc/client/ZhuangquerangMaidGeoModel.class',
                       'com/tnc/tnc/npc/client/ZhuangquerangMaidRenderer.class',
                       'com/tnc/tnc/npc/compat/MaidNpcSupport.class')) {
        if ($zip.Entries | Where-Object { $_.FullName -eq $cls }) { Ok "class present: $(Split-Path $cls -Leaf)" }
        else { Fail "maid NPC class missing from jar: $cls" }
    }
    # the entity asks for the animation named "idle" - if the file does not define it,
    # GeckoLib just plays nothing (no error anywhere)
    $animEntry = $zip.Entries | Where-Object { $_.FullName -eq 'assets/tnc/animations/entity/zhuangquerang.animation.json' }
    if ($animEntry) {
        $ar = New-Object System.IO.StreamReader($animEntry.Open(), [System.Text.Encoding]::UTF8)
        $animText = $ar.ReadToEnd(); $ar.Close()
        if ($animText -match '"idle"') { Ok 'maid animation file defines the "idle" clip the entity asks for' }
        else { Fail 'maid animation file has no "idle" clip - the entity would stand frozen (silent)' }
    }

    # ---------------- D1b5. the 5 main-story record papers (same reader) ----------------
    # Same silent-failure modes as the scrolls, plus one of its own: the text is
    # generated from the writer's summary doc, so a forgotten re-run ships stale
    # or missing text.
    $recordIds = @('zhengshi_qianqing', 'zhengshi_1', 'zhengshi_2', 'zhengshi_3', 'zhengshi_4')
    if ($zip.Entries | Where-Object { $_.FullName -eq 'com/tnc/tnc/magic/TNRecords.class' }) {
        Ok 'class present: com/tnc/tnc/magic/TNRecords.class'
    } else {
        Fail 'record class missing from jar: com/tnc/tnc/magic/TNRecords.class'
    }
    if ($scrollLang) {
        $noRecBody = @()
        $noRecName = @()
        $noRecModel = @()
        foreach ($rid in $recordIds) {
            if ($scrollLangText -notmatch [regex]::Escape("scroll.tnc.$rid")) { $noRecBody += $rid }
            if ($scrollLangText -notmatch [regex]::Escape("item.tnc.$rid")) { $noRecName += $rid }
            if (-not ($zip.Entries | Where-Object { $_.FullName -eq "assets/tnc/models/item/$rid.json" })) { $noRecModel += $rid }
        }
        if ($noRecBody.Count -eq 0) { Ok 'all 5 record bodies are in the lang file' }
        else { Fail ('record body missing from lang (run tools\gen_records_lang.ps1): ' + ($noRecBody -join ', ')) }
        if ($noRecName.Count -eq 0) { Ok 'all 5 record item names are in the lang file' }
        else { Fail ('record item name missing from lang: ' + ($noRecName -join ', ')) }
        if ($noRecModel.Count -eq 0) { Ok 'all 5 record item models are in the jar' }
        else { Fail ('record item model missing (renders as a missing model): ' + ($noRecModel -join ', ')) }
        if ($zip.Entries | Where-Object { $_.FullName -eq 'assets/tnc/textures/item/zhengshi.png' }) {
            Ok 'record sheet texture is in the jar'
        } else {
            Fail 'record sheet texture missing: assets/tnc/textures/item/zhengshi.png'
        }
    }

    # ---------------- D1b6. every one of our ITEMS must have an item model ----------------
    # An item without a model renders as the purple-black missing cube, and nothing logs
    # an ERROR for it unless you happen to read the resource-reload WARN lines.
    # This actually happened: zhuangquerang_spawn_egg was registered with no model.
    # The texture is resolved from the model's own layer0 (the record papers share one
    # texture, so "assets/tnc/textures/item/<id>.png" is NOT a valid assumption).
    $ourItems = @('sword', 'magic_wand', 'fireball',
                  # The three bank coins. Added 2026-10-09 after the author reported
                  # "我的金银铜币怎么还是紫黑方块啊": they HAD models, but the models were
                  # unloadable (-90 degree rotations), and they were missing from this list
                  # so nothing here checked them either.
                  'copper_coin', 'silver_coin', 'gold_coin',
                  'canjuan_1a', 'canjuan_1b', 'canjuan_3', 'canjuan_4', 'canjuan_5', 'canjuan_6',
                  'zhengshi_qianqing', 'zhengshi_1', 'zhengshi_2', 'zhengshi_3', 'zhengshi_4',
                  'self_spawn_egg', 'cava_spawn_egg', 'huai_spawn_egg', 'zhuangquerang_spawn_egg',
                  'zuowang_spawn_egg')
    $noModel = @()
    $noTexture = @()
    foreach ($item in $ourItems) {
        $modelEntry = $zip.Entries | Where-Object { $_.FullName -eq "assets/tnc/models/item/$item.json" }
        if (-not $modelEntry) { $noModel += $item; continue }
        $mr = New-Object System.IO.StreamReader($modelEntry.Open(), [System.Text.Encoding]::UTF8)
        $modelText = $mr.ReadToEnd(); $mr.Close()
        $ref = [regex]::Match($modelText, '"layer0"\s*:\s*"([^"]+)"')
        if (-not $ref.Success) { continue }      # 走 parent（如 template_spawn_egg）的不用查贴图 ✓
        # NOTE: do NOT write this as -replace '^([^:]+):', '$1:textures/'.
        # PowerShell parses '$1:' as a SCOPE-QUALIFIED variable name, so the
        # replacement never expands and you get "assets/tnc:textures/..." -
        # a path that can never match, so every item looked like it was missing
        # its texture (fixed 2026-09-29).
        $layer = $ref.Groups[1].Value
        $colon = $layer.IndexOf(':')
        if ($colon -lt 0) { continue }           # 没写命名空间 = 指向原版贴图，不在我们 jar 里查
        $texPath = "assets/" + $layer.Substring(0, $colon) + "/textures/" + $layer.Substring($colon + 1) + ".png"
        if (-not ($zip.Entries | Where-Object { $_.FullName -eq $texPath })) { $noTexture += "$item -> $layer" }
    }
    if ($noModel.Count -eq 0) { Ok "all $($ourItems.Count) of our items have an item model" }
    else { Fail ('item has NO model (renders as the purple-black cube): ' + ($noModel -join ', ')) }
    if ($noTexture.Count -eq 0) { Ok 'every item model points at a texture that ships' }
    else { Fail ('item model references a missing texture: ' + ($noTexture -join ', ')) }

    # ---------------- D1b7. every ITEM model must be LOADABLE by the vanilla loader --
    # 2026-10-09 (author: "我的金银铜币怎么还是紫黑方块啊"): the three coin models were
    # Blockbench block-format exports carrying four elements rotated **-90 degrees**.
    # Vanilla only accepts -45 / -22.5 / 0 / 22.5 / 45, and ONE bad angle makes the WHOLE
    # model fail to load:
    #   [ModelManager] Failed to load model tnc:models/item/copper_coin.json
    #   JsonParseException: Invalid rotation -90.0 found, only -45/-22.5/0/22.5/45 allowed
    # and the game then draws the purple-black missing-model cube.
    # The same trap bit the projectile models before (tools/import_author_model.ps1
    # validates those); this is the general net over the ITEM models.
    #
    # Scope = models/item/** ONLY, because that is the path the ordinary ModelBakery
    # deserializer walks (it is what logged the coin error). Our big prop models under
    # models/block/** (dragon_display_*, deliberately far outside -16..32) are loaded
    # through the engine/dynamic path instead, so holding them to these limits would be
    # a false alarm.
    #
    # What the loader enforces (BlockElement/BlockFace deserializers, 1.20.1):
    #   * rotation.angle in {-45,-22.5,0,22.5,45}
    #   * from/to within [-16, 32]
    #   * uv within [0, texture_size] (16 when the model does not declare one)
    $badModels = @()
    $legalAngles = @(-45.0, -22.5, 0.0, 22.5, 45.0)
    $faceNames = @('north', 'south', 'east', 'west', 'up', 'down')
    foreach ($entry in ($zip.Entries | Where-Object { $_.FullName -like 'assets/tnc/models/item/*.json' })) {
        $er = New-Object System.IO.StreamReader($entry.Open(), [System.Text.Encoding]::UTF8)
        $text = $er.ReadToEnd(); $er.Close()
        $model = $null
        try { $model = ConvertFrom-TncSpellJson $text } catch { $badModels += "$($entry.FullName) (unparsable)"; continue }
        $texSize = 16.0
        if ($model.texture_size) { $texSize = [double]@($model.texture_size)[0] }
        $angles = @()
        foreach ($el in @($model.elements)) {
            if ($null -eq $el) { continue }
            $angles += [double]$el.rotation.angle
            foreach ($key in @('from', 'to')) {
                foreach ($v in @($el.$key)) {
                    if ($null -eq $v) { continue }
                    $f = [double]$v
                    if ($f -lt -16.0001 -or $f -gt 32.0001) {
                        $badModels += "$($entry.FullName) $key=$f outside the loader limit -16..32"
                    }
                }
            }
            foreach ($faceName in $faceNames) {
                $face = $el.faces.$faceName
                if ($null -eq $face) { continue }
                if ($null -eq $face.uv) { continue }
                foreach ($c in @($face.uv)) {
                    $f = [double]$c
                    if ($f -lt -0.0001 -or $f -gt ($texSize + 0.0001)) {
                        $badModels += "$($entry.FullName) $faceName uv=$f outside the $texSize-pixel atlas"
                    }
                }
            }
        }
        # one illegal angle kills the WHOLE model, so report the model once
        $illegal = @($angles | Where-Object { $null -ne $_ -and ($legalAngles -notcontains $_) })
        if ($illegal.Count -gt 0) {
            $badModels += ("$($entry.FullName) illegal rotation angle(s) " + (($illegal | Select-Object -Unique) -join ',') +
                           " (only -45/-22.5/0/22.5/45 are allowed; the WHOLE model then fails to load)")
        }
    }
    if ($badModels.Count -eq 0) { Ok 'every item model can be loaded by the vanilla model loader (angles / extents / uv)' }
    else { Fail ('item model(s) the client would refuse to load (=> purple-black cube): ' + ($badModels -join '; ')) }

    # ---------------- D1c. HUD entry point + its keybind ----------------
    # Nothing HUD-side can be clicked (no cursor while playing), so the keybind IS
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

# ---------------- G. dialogue segment reachability + quest chain ----------------
# Both of these already existed as standalone python checkers, but they were only
# ever run by hand -- which is how a segment that can NEVER be selected shipped
# twice in a row while every file in the listing looked perfectly correct:
#   zuowang.txt (seq 0) had no @requires, so zuowang_02 (also ungated) outranked
#   it in EVERY state.  In game that is indistinguishable from "the NPC is mute",
#   and it cost the user several rounds of "他还是不切段" (2026-09-29 .. 30).
# A dialogue that can never play is a build failure, not a content detail.
$chkPython = Get-Command python -ErrorAction SilentlyContinue
if (-not $chkPython) {
    Note "python not on PATH -- SKIPPED the dialogue segment / quest chain checkers"
} else {
    $savedEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    foreach ($chkName in @('check_tnc_quest_chain.py', 'check_tnc_dialogue_segments.py')) {
        $chkPath = Join-Path $PSScriptRoot $chkName
        if (-not (Test-Path $chkPath)) { Fail "missing checker: tools\$chkName"; continue }
        $chkOut = @(& python $chkPath 2>&1)
        if ($LASTEXITCODE -eq 0) {
            Ok "$chkName passed"
        } else {
            Fail "$chkName failed -- do NOT install this build:"
            foreach ($chkLine in $chkOut) { Write-Host "            $chkLine" }
        }
    }
    $ErrorActionPreference = $savedEap
}

Write-Host ''
if ($problems -gt 0) { Write-Host "$problems problem(s) found."; exit 1 }
Write-Host "mod jar verification passed."
