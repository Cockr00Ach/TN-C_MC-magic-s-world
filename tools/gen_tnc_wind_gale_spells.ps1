# WIND chain 3 (GALE): 5 spells = wind blades + an area of wind.
#
# Shape per tier: a PROJECTILE (the blade) whose impact also applies:
#   * DAMAGE              - the area damage (area_impact re-applies impacts to
#                           everything in the radius, that is how fireball AoE works)
#   * STATUS_EFFECT slow  - "monsters around are slowed 25%"
#   * STATUS_EFFECT haste - apply_to_caster:true, so only the caster gets it
#                           (allies hasting needs Java: the engine has no
#                            ally/enemy split inside one area)
# Tier 5 launches three blades (extra_launch_count) and pierces.
#
# Blades "ignore armour" note: spell damage goes through spell_power, not vanilla
# armour, so this is already closer to "ignores defence" than a sword hit. A true
# bypass needs Java and is NOT done.
#
# ASCII only (tools/*.ps1 convention - non-ASCII gets read as GBK and eats newlines).

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot

# ---- WHY THIS TARGET IS THE MOD JAR AND NOT kubejs (fixed 2026-10-07) --------
# These five spells used to be written into the pack's kubejs folder. Two later
# commits changed the shipped files and this generator was never updated, which
# made running it a REGRESSION:
#   * 6b29d3af  moved every spell that references a tnc projectile model out of
#               kubejs and into the mod jar's data/ -> the real files now live
#               in src/main/resources/data/tnc/spells. Writing them to kubejs
#               again creates same-id copies that SHADOW the jar version
#               (for identical ids, the kubejs data pack wins).
#   * 56a77813  pinned every model-using spell to the one model id known to
#               render, tnc:projectile/lightingball. This template still said
#               tnc:projectile/sun_ray, and no such model file exists -> all
#               five spells would have rendered purple/black.
# Keep this script honest: after ANY edit here, run it and then confirm
#     git diff -- src/main/resources/data/tnc/spells
# is EMPTY. If it is not empty, the generator and the shipped files disagree.
$dest = Join-Path $root 'src\main\resources\data\tnc\spells'
if (-not (Test-Path $dest)) { throw "spell dir not found: $dest" }

function Write-Json($name, $json) {
    $path = Join-Path $dest "$name.json"
    [System.IO.File]::WriteAllText($path, $json, (New-Object System.Text.UTF8Encoding($false)))
    $null = ConvertFrom-Json $json
    Write-Output ("  {0,-22} {1,6:N0} B" -f "$name.json", (Get-Item $path).Length)
}

$castFx = '{ "particle_id": "cloud", "shape": "PIPE", "origin": "FEET", "count": 6.0, "min_speed": 0.1, "max_speed": 0.5 }'
$trailFx = '{ "particle_id": "sweep_attack", "shape": "CIRCLE", "rotation": "LOOK", "origin": "CENTER", "count": 3.0, "min_speed": 0.0, "max_speed": 0.1 }'
$boomFx = '{ "particle_id": "cloud", "shape": "SPHERE", "origin": "CENTER", "count": 40.0, "min_speed": 0.3, "max_speed": 1.2 }'

# tier 1..5
function Gale-Spell($name, $tier, $radius, $seconds, $blades, $coef, $scale, $cooldown, $pierce) {
    # * 2026-10-10 (author): "from t3 on, casting the gale spell summons a tornado that
    #   keeps spinning and keeps firing wind blades outward".
    #   -> t3/t4/t5 stop being an engine PROJECTILE and become SELF: the engine spawns
    #      nothing, and Java (TNWindGaleMechanics, hooked on SPELL_CAST) summons the
    #      tornado instead. This is the SAME shape as the night-hand chain, which is the
    #      already-verified template for "tier-dependent summoning".
    #      Reason it cannot be data-only: the engine's SPAWN action takes a fixed
    #      entity_type_id and has NO tier field (checked with javap), and the author wants
    #      t3/t4/t5 to differ (lifetime 8/12/16s, 3/5/7 blades per volley, size 1.0/1.3/1.6).
    #   -> t1/t2 are untouched: they are still the single flying crescent.
    $isTornado = ($tier -ge 3)

    if ($isTornado) {
        $targetJson = @"
    "target": { "type": "SELF" },
"@
    } else {
        $targetJson = @"
    "target": {
      "type": "PROJECTILE",
      "projectile": {
        "launch_properties": { "velocity": 1.3, "extra_launch_count": $blades, "extra_launch_delay": 2 },
        "projectile": {
          "homing_angle": 0.0,
          "divergence": 12.0,
          "perks": { "pierce": $pierce },
          "client_data": {
            "travel_particles": [ $trailFx ],
            "model": { "model_id": "tnc:projectile/windblade", "orientation": "TOWARDS_MOTION", "scale": $scale }
          }
        }
      }
    },
"@
    }

    # SELF spells run their impact ON THE CASTER. The night-hand chain (this repo's
    # verified SELF template) deliberately has NO DAMAGE action for exactly that reason:
    # a DAMAGE action on a SELF impact would hurt the player. So for t3+ the damage moves
    # to the summoned tornado (TNTornadoEntity + the blades it fires), and the impact
    # keeps only the status effects -- the caster's own gale_haste, plus gale_slow which
    # the tornado re-applies to enemies around it.
    if ($isTornado) {
        $damageAction = ''
    } else {
        $damageAction = @"
    {
      "action": { "type": "DAMAGE", "damage": { "spell_power_coefficient": $coef, "knockback": 0.2 } },
      "particles": [ $boomFx ],
      "sound": { "id": "spell_engine:generic_wind_charging", "volume": 0.6 }
    },
"@
    }

    # * area_impact MUST BE OMITTED for SELF spells.
    #   The verify gate caught this: "SELF/CURSOR never fills the area-impact position"
    #   -> the engine NPEs on EVERY cast. Checked the whole repo: every other SELF spell
    #   has area_impact=false; these three were the only ones setting it.
    #   It also makes sense for the design: the tornado IS the area effect now, so the
    #   spell itself no longer needs an engine-side area burst.
    if ($isTornado) {
        $areaImpact = ''
    } else {
        $areaImpact = @"
  "area_impact": {
    "radius": $radius,
    "area": { "distance_dropoff": "SQUARED" },
    "particles": [ $boomFx ],
    "sound": { "id": "spell_engine:generic_wind_charging", "volume": 0.9 }
  },
"@ + "`n"
    }

    Write-Json $name @"
{
  "school": "AIR",
  "group": "gale",
  "range": 26.0,
  "learn": { "tier": $tier },
  "cast": {
    "duration": 0.5,
    "animation": "spell_engine:one_handed_projectile_charge",
    "sound": { "id": "spell_engine:generic_wind_charging", "randomness": 0 },
    "particles": [ $castFx ]
  },
  "release": {
$targetJson
    "animation": "spell_engine:one_handed_projectile_release",
    "sound": { "id": "spell_engine:generic_wind_charging" }
  },
  "impact": [
$damageAction
    {
      "action": {
        "type": "STATUS_EFFECT",
        "apply_to_caster": false,
        "status_effect": {
          "effect_id": "tnc:gale_slow",
          "duration": $seconds,
          "amplifier": 0,
          "apply_mode": "SET",
          "show_particles": true
        }
      },
      "particles": [ $trailFx ]
    },
    {
      "action": {
        "type": "STATUS_EFFECT",
        "apply_to_caster": true,
        "status_effect": {
          "effect_id": "tnc:gale_haste",
          "duration": $seconds,
          "amplifier": 0,
          "apply_mode": "SET",
          "show_particles": false
        }
      },
      "particles": [ $castFx ]
    }
  ],
$areaImpact  "cost": { "exhaust": 0.0, "cooldown_duration": $cooldown }
}
"@
}

#                       id              tier radius sec blades coef scale cd   pierce
#
# scale is measured against WHATEVER model is in the template above. The model
# changed on 2026-10-10 (lightingball, 0.375 blocks -> windblade, 2.16 blocks tall,
# i.e. 5.76x bigger), and these numbers were deliberately LEFT ALONE, so the blades
# went from 0.19..0.53 blocks to 1.08..3.02 blocks tall. Recompute them if you swap
# the model again:  final size = model size * scale.
Gale-Spell 'gale'            1 3.0 15.0 0 1.0 0.5 2.5 0
Gale-Spell 'great_gale'      2 4.0 20.0 0 1.4 0.7 3.5 0
Gale-Spell 'vast_gale'       3 5.0 30.0 0 1.8 0.9 5.0 0
Gale-Spell 'giant_gale'      4 6.0 30.0 0 2.2 1.2 7.0 0
Gale-Spell 'wind_god_gale'   5 7.0 30.0 2 2.6 1.4 10.0 1

Write-Output ''
Write-Output ('done -> ' + $dest)