# WATER chain 3 (bind, control) + EARTH chain 1 (shot, output) - 10 spells.
#
# Chain 3 reuses the wind chain's slow effect (tnc:gale_slow, -25% movement) as a
# stand-in: a dedicated water slow is nicer but this keeps the chain playable with
# zero new Java. Swap the effect_id when a water-specific one exists.
#
# Earth chain 1 is the same PROJECTILE + area_impact shape as the water ball line.
#
# ASCII only.

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

$castFx = '{ "particle_id": "splash", "shape": "CIRCLE", "origin": "FEET", "count": 10.0, "min_speed": 0.1, "max_speed": 0.4 }'
$trailFx = '{ "particle_id": "bubble", "shape": "CIRCLE", "rotation": "LOOK", "origin": "CENTER", "count": 3.0, "min_speed": 0.0, "max_speed": 0.1 }'
$boomFx = '{ "particle_id": "splash", "shape": "SPHERE", "origin": "CENTER", "count": 45.0, "min_speed": 0.3, "max_speed": 1.5 }'
$dustFx = '{ "particle_id": "crit", "shape": "SPHERE", "origin": "CENTER", "count": 25.0, "min_speed": 0.2, "max_speed": 1.0 }'

# ---------------- 水缚线：范围减速（控制）----------------
function Bind-Spell($name, $tier, $range, $coef, $seconds, $cooldown) {
    Write-Json $name @"
{
  "school": "WATER",
  "group": "water_bind",
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
    "sound": { "id": "entity.player.splash", "volume": 0.9 }
  },
  "impact": [
    {
      "action": { "type": "DAMAGE", "damage": { "spell_power_coefficient": $coef, "knockback": 0.0 } },
      "particles": [ $boomFx ]
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
    }
  ],
  "cost": { "exhaust": 0.0, "cooldown_duration": $cooldown }
}
"@
}

#                    id                tier range coef  sec   cd
Bind-Spell 'water_bind'      1 4.0 0.7 4.0 3.0
Bind-Spell 'water_prison'    2 4.5 1.0 5.0 4.5
Bind-Spell 'water_burial'    3 5.5 1.3 6.0 6.0
Bind-Spell 'abyss'           4 6.5 1.6 7.0 8.0
Bind-Spell 'sea_god_crypt'   5 8.0 2.0 9.0 12.0

# ---------------- 土弹线：投射物（输出）----------------
function Earth-Spell($name, $tier, $velocity, $coef, $scale, $radius, $cooldown, $launches) {
    Write-Json $name @"
{
  "school": "EARTH",
  "group": "earth_shot",
  "range": 36.0,
  "learn": { "tier": $tier },
  "cast": {
    "duration": 0.6,
    "animation": "spell_engine:one_handed_projectile_charge",
    "sound": { "id": "spell_engine:generic_arcane_casting", "randomness": 0 },
    "particles": [ $dustFx ]
  },
  "release": {
    "target": {
      "type": "PROJECTILE",
      "projectile": {
        "launch_properties": { "velocity": $velocity, "extra_launch_count": $launches, "extra_launch_delay": 3 },
        "projectile": {
          "homing_angle": 0.2,
          "client_data": {
            "travel_particles": [ $dustFx ],
            "model": { "model_id": "tnc:projectile/fire_ball", "scale": $scale }
          }
        }
      }
    },
    "animation": "spell_engine:one_handed_projectile_release",
    "sound": { "id": "spell_engine:generic_arcane_release" }
  },
  "impact": [
    {
      "action": { "type": "DAMAGE", "damage": { "spell_power_coefficient": $coef, "knockback": 0.4 } },
      "particles": [ $dustFx ],
      "sound": { "id": "block.stone.break", "volume": 0.8 }
    }
  ],
  "area_impact": {
    "radius": $radius,
    "area": { "distance_dropoff": "SQUARED" },
    "particles": [ $dustFx ],
    "sound": { "id": "block.stone.break", "volume": 1.0 }
  },
  "cost": { "exhaust": 0.0, "cooldown_duration": $cooldown }
}
"@
}

#                       id                    tier vel  coef scale radius cd  launches
Earth-Spell 'earth_shot'          1 1.2 1.1 0.7  2.0  1.5 0
Earth-Spell 'earth_ball'          2 1.3 1.6 0.9  2.5  2.5 0
Earth-Spell 'rock_cannon'         3 1.5 2.0 1.2  3.5  4.0 1
Earth-Spell 'ten_thousand_rocks'  4 1.2 2.4 1.5  5.0  6.0 3
Earth-Spell 'earth_god_spear'     5 1.6 2.9 1.8  6.0 10.0 2

Write-Output ''
Write-Output ('done -> ' + $dest)