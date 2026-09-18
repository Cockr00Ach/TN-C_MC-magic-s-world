# WATER chain 1 (water ball, output) + chain 2 (water wave, area) - 10 spells.
#
# Both use patterns already proven in game:
#   * PROJECTILE + area_impact   (same as the fire ball chain)
#   * AREA target + knockback    (AREA radius = the top level "range" field,
#                                 verified against elemental_wizards_rpg's aqua_springwater)
# Chain 3 (water bind, control) and chain 4 (rain) need new effects / the engine's
# Cloud target, so they are next, not here.
#
# Names come straight from the design doc (//// ...).
#
# ASCII only (tools/*.ps1 convention).

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$pack = Get-ChildItem (Join-Path $root 'modpack') -Directory |
        Where-Object { Test-Path (Join-Path $_.FullName 'kubejs\data\tnc\spells') } |
        Select-Object -First 1
if (-not $pack) { throw 'modpack kubejs\data\tnc\spells not found' }
$dest = Join-Path $pack.FullName 'kubejs\data\tnc\spells'

function Write-Json($name, $json) {
    $path = Join-Path $dest "$name.json"
    [System.IO.File]::WriteAllText($path, $json, (New-Object System.Text.UTF8Encoding($false)))
    $null = ConvertFrom-Json $json
    Write-Output ("  {0,-26} {1,6:N0} B" -f "$name.json", (Get-Item $path).Length)
}

$castFx  = '{ "particle_id": "splash", "shape": "CIRCLE", "origin": "FEET", "count": 10.0, "min_speed": 0.1, "max_speed": 0.4 }'
$trailFx = '{ "particle_id": "bubble", "shape": "CIRCLE", "rotation": "LOOK", "origin": "CENTER", "count": 3.0, "min_speed": 0.0, "max_speed": 0.1 }'
$boomFx  = '{ "particle_id": "splash", "shape": "SPHERE", "origin": "CENTER", "count": 45.0, "min_speed": 0.3, "max_speed": 1.5 }'

# ---------------- 1  ----------------
function Ball-Spell($name, $tier, $velocity, $coef, $scale, $radius, $cooldown, $pierce, $launches) {
    $model = if ($tier -le 2) { 'tnc:projectile/fire_ball' } else { 'tnc:projectile/fire_ball' }
    Write-Json $name @"
{
  "school": "WATER",
  "group": "water_ball",
  "range": 40.0,
  "learn": { "tier": $tier },
  "cast": {
    "duration": 0.6,
    "animation": "spell_engine:one_handed_projectile_charge",
    "sound": { "id": "spell_engine:generic_arcane_casting", "randomness": 0 },
    "particles": [ $castFx ]
  },
  "release": {
    "target": {
      "type": "PROJECTILE",
      "projectile": {
        "launch_properties": { "velocity": $velocity, "extra_launch_count": $launches, "extra_launch_delay": 3 },
        "projectile": {
          "homing_angle": 0.3,
          "perks": { "pierce": $pierce },
          "client_data": {
            "travel_particles": [ $trailFx ],
            "model": { "model_id": "$model", "scale": $scale }
          }
        }
      }
    },
    "animation": "spell_engine:one_handed_projectile_release",
    "sound": { "id": "spell_engine:generic_arcane_release" }
  },
  "impact": [
    {
      "action": { "type": "DAMAGE", "damage": { "spell_power_coefficient": $coef, "knockback": 0.3 } },
      "particles": [ $boomFx ],
      "sound": { "id": "entity.generic.splash", "volume": 0.8 }
    }
  ],
  "area_impact": {
    "radius": $radius,
    "area": { "distance_dropoff": "SQUARED" },
    "particles": [ $boomFx ],
    "sound": { "id": "entity.generic.splash", "volume": 1.0 }
  },
  "cost": { "exhaust": 0.0, "cooldown_duration": $cooldown }
}
"@
}

#                      id                  tier vel  coef scale radius cd  pierce launches
Ball-Spell 'water_ball'          1 1.3 1.0 0.7  2.0  1.5 0 0
Ball-Spell 'water_cannon'        2 1.7 1.6 0.9  2.5  2.5 1 0
Ball-Spell 'dragon_roar'         3 1.4 2.0 1.3  4.0  4.0 1 2
Ball-Spell 'dragon_howl'         4 1.3 2.4 1.6  5.5  6.0 1 2
Ball-Spell 'dragon_ruin'         5 1.2 3.0 2.0  7.0 10.0 1 3

# ---------------- 2 AREA  =  range----------------
function Wave-Spell($name, $tier, $range, $coef, $knockback, $cooldown, $effect, $seconds) {
    $status = ''
    if ($effect) {
        $status = @"
    {
      "action": {
        "type": "STATUS_EFFECT",
        "status_effect": {
          "effect_id": "tnc:$effect",
          "duration": $seconds,
          "amplifier": 0,
          "apply_mode": "SET",
          "show_particles": true
        }
      },
      "particles": [ $castFx ]
    },
"@
    }
    Write-Json $name @"
{
  "school": "WATER",
  "group": "water_wave",
  "range": $range,
  "learn": { "tier": $tier },
  "cast": {
    "duration": 0.7,
    "animation": "spell_engine:one_handed_area_charge",
    "sound": { "id": "spell_engine:generic_arcane_casting", "randomness": 0 },
    "particles": [ $castFx ]
  },
  "release": {
    "target": {
      "type": "AREA",
      "area": { "include_caster": false, "distance_dropoff": "SQUARED" }
    },
    "animation": "spell_engine:one_handed_area_release",
    "sound": { "id": "entity.player.splash", "volume": 1.0 }
  },
  "impact": [
    {
      "action": { "type": "DAMAGE", "damage": { "spell_power_coefficient": $coef, "knockback": $knockback } },
      "particles": [ $boomFx ],
      "sound": { "id": "entity.generic.splash", "volume": 1.0 }
    },
$status
    {
      "action": { "type": "FIRE", "fire": { "burn_time": 0.0 } },
      "particles": [ $trailFx ]
    }
  ],
  "cost": { "exhaust": 0.0, "cooldown_duration": $cooldown }
}
"@
}

#                      id              tier range coef knock cd  effect seconds
Wave-Spell 'water_ripple'    1 4.0 0.9 0.6  2.0 $null 0
Wave-Spell 'water_wave'      2 5.0 1.3 0.9  3.0 $null 0
Wave-Spell 'wave_slash'      3 6.0 1.7 1.2  4.5 $null 0
Wave-Spell 'tsunami'         4 7.5 2.1 1.5  6.5 $null 0
Wave-Spell 'world_ending_sea' 5 9.0 2.6 2.0 11.0 $null 0

Write-Output ''
Write-Output ('done -> ' + $dest)
