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
$pack = Get-ChildItem (Join-Path $root 'modpack') -Directory |
        Where-Object { Test-Path (Join-Path $_.FullName 'kubejs\data\tnc\spells') } |
        Select-Object -First 1
if (-not $pack) { throw 'modpack kubejs\data\tnc\spells not found' }
$dest = Join-Path $pack.FullName 'kubejs\data\tnc\spells'

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
            "model": { "model_id": "tnc:projectile/fire_ray", "scale": $scale }
          }
        }
      }
    },
    "animation": "spell_engine:one_handed_projectile_release",
    "sound": { "id": "spell_engine:generic_wind_charging" }
  },
  "impact": [
    {
      "action": { "type": "DAMAGE", "damage": { "spell_power_coefficient": $coef, "knockback": 0.2 } },
      "particles": [ $boomFx ],
      "sound": { "id": "spell_engine:generic_wind_charging", "volume": 0.6 }
    },
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
  "area_impact": {
    "radius": $radius,
    "area": { "distance_dropoff": "SQUARED" },
    "particles": [ $boomFx ],
    "sound": { "id": "spell_engine:generic_wind_charging", "volume": 0.9 }
  },
  "cost": { "exhaust": 0.0, "cooldown_duration": $cooldown }
}
"@
}

#                       id              tier radius sec blades coef scale cd   pierce
Gale-Spell 'gale'            1 3.0 15.0 0 1.0 0.5 2.5 0
Gale-Spell 'great_gale'      2 4.0 20.0 0 1.4 0.7 3.5 0
Gale-Spell 'vast_gale'       3 5.0 30.0 0 1.8 0.9 5.0 0
Gale-Spell 'giant_gale'      4 6.0 30.0 0 2.2 1.2 7.0 0
Gale-Spell 'wind_god_gale'   5 7.0 30.0 2 2.6 1.4 10.0 1

Write-Output ''
Write-Output ('done -> ' + $dest)