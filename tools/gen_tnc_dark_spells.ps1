# DARK chain 1 (night hand, output) + chain 4 (black fog, area) - 10 spells.
#
# Everything here uses VANILLA effects only (no new Java):
#   minecraft:blindness  - the signature dark debuff ("has a chance to blind")
#   tnc:gale_slow        - borrowed slow (a dark-specific one can replace it later)
# Chain 2 (sacrifice) will reuse the fire chain's burn effects, chain 3 (summon)
# needs Java for a proper look (the orb mechanic hardcodes wind particles), so
# both are next, not here.
#
# Names from the design doc: hei-ye-zhi-shou / hei-ye-zhi-xi / hei-ye-zhi-yong /
# hei-zhi-po-mie / lu-guang and hei-wu / hei-ye-juan-gu / an-zhi-dou /
# guang-wu-fa-dao-da-zhi-di / tun-guang-ling-yu.
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
    Write-Output ("  {0,-30} {1,6:N0} B" -f "$name.json", (Get-Item $path).Length)
}

$smokeFx = '{ "particle_id": "smoke", "shape": "SPHERE", "origin": "CENTER", "count": 35.0, "min_speed": 0.2, "max_speed": 1.0 }'
$soulFx  = '{ "particle_id": "soul", "shape": "CIRCLE", "rotation": "LOOK", "origin": "CENTER", "count": 4.0, "min_speed": 0.0, "max_speed": 0.1 }'
$castFx  = '{ "particle_id": "soul_fire_flame", "shape": "CIRCLE", "origin": "FEET", "count": 14.0, "min_speed": 0.1, "max_speed": 0.4 }'

# ---------------- chain 1: projectiles + blindness ----------------
function Hand-Spell($name, $tier, $velocity, $coef, $scale, $radius, $cooldown, $launches, $blindAmp, $blindSec) {
    Write-Json $name @"
{
  "school": "SOUL",
  "group": "dark_hand",
  "range": 34.0,
  "learn": { "tier": $tier },
  "cast": {
    "duration": 0.6,
    "animation": "spell_engine:one_handed_projectile_charge",
    "sound": { "id": "spell_engine:generic_soul_casting", "randomness": 0 },
    "particles": [ $castFx ]
  },
  "release": {
    "target": {
      "type": "PROJECTILE",
      "projectile": {
        "launch_properties": { "velocity": $velocity, "extra_launch_count": $launches, "extra_launch_delay": 3 },
        "projectile": {
          "homing_angle": 0.5,
          "client_data": {
            "travel_particles": [ $soulFx ],
            "model": { "model_id": "tnc:projectile/fire_ball", "scale": $scale }
          }
        }
      }
    },
    "animation": "spell_engine:one_handed_projectile_release",
    "sound": { "id": "spell_engine:generic_soul_release" }
  },
  "impact": [
    {
      "action": { "type": "DAMAGE", "damage": { "spell_power_coefficient": $coef, "knockback": 0.2 } },
      "particles": [ $smokeFx ],
      "sound": { "id": "entity.wither.shoot", "volume": 0.7 }
    },
    {
      "action": {
        "type": "STATUS_EFFECT",
        "apply_to_caster": false,
        "status_effect": {
          "effect_id": "minecraft:blindness",
          "duration": $blindSec,
          "amplifier": $blindAmp,
          "apply_mode": "SET",
          "show_particles": true
        }
      },
      "particles": [ $soulFx ]
    }
  ],
  "area_impact": {
    "radius": $radius,
    "area": { "distance_dropoff": "SQUARED" },
    "particles": [ $smokeFx ],
    "sound": { "id": "entity.wither.shoot", "volume": 1.0 }
  },
  "cost": { "exhaust": 0.0, "cooldown_duration": $cooldown }
}
"@
}

#                 id                tier vel  coef scale radius cd  launches blindAmp blindSec
Hand-Spell 'night_hand'     1 1.3 1.0 0.7  2.0  2.0 0 0 3.0
Hand-Spell 'night_raid'     2 1.3 1.5 0.9  2.5  3.0 2 0 4.0
Hand-Spell 'night_embrace'  3 1.2 1.9 1.2  4.0  4.5 2 0 5.0
Hand-Spell 'black_ruin'     4 1.2 2.3 1.5  5.5  7.0 3 0 7.0
Hand-Spell 'slay_light'     5 1.1 2.8 1.8  7.0 11.0 4 1 9.0

# ---------------- chain 4: fog (AREA + slow + blindness) ----------------
function Fog-Spell($name, $tier, $range, $coef, $slowSec, $blindSec, $cooldown, $hasteToCaster) {
    $haste = ''
    if ($hasteToCaster) {
        $haste = @"
    {
      "action": {
        "type": "STATUS_EFFECT",
        "apply_to_caster": true,
        "status_effect": {
          "effect_id": "minecraft:speed",
          "duration": $slowSec,
          "amplifier": 0,
          "apply_mode": "SET",
          "show_particles": false
        }
      },
      "particles": [ $castFx ]
    },
"@
    }
    Write-Json $name @"
{
  "school": "SOUL",
  "group": "dark_fog",
  "range": $range,
  "learn": { "tier": $tier },
  "cast": {
    "duration": 0.8,
    "animation": "spell_engine:one_handed_area_charge",
    "sound": { "id": "spell_engine:generic_soul_casting", "randomness": 0 },
    "particles": [ $smokeFx ]
  },
  "release": {
    "target": {
      "type": "CLOUD",
      "cloud": {
        "volume": {
          "radius": $range,
          "area": { "distance_dropoff": "SQUARED" }
        },
        "time_to_live_seconds": 15.0,
        "impact_tick_interval": 20,
        "delay_ticks": 5,
        "presence_sound": { "id": "entity.wither.ambient", "volume": 0.25 },
        "client_data": { "particles": [ $smokeFx ] },
        "spawn": { "particles": [ $smokeFx ], "sound": { "id": "entity.wither.ambient", "volume": 1.0 } }
      }
    },
    "animation": "spell_engine:one_handed_area_release",
    "sound": { "id": "entity.wither.ambient", "volume": 1.0 }
  },
  "impact": [
    {
      "action": { "type": "DAMAGE", "damage": { "spell_power_coefficient": $coef, "knockback": 0.0 } },
      "particles": [ $smokeFx ]
    },
    {
      "action": {
        "type": "STATUS_EFFECT",
        "apply_to_caster": false,
        "status_effect": {
          "effect_id": "tnc:gale_slow",
          "duration": $slowSec,
          "amplifier": 0,
          "apply_mode": "SET",
          "show_particles": true
        }
      },
      "particles": [ $smokeFx ]
    },
    {
      "action": {
        "type": "STATUS_EFFECT",
        "apply_to_caster": false,
        "status_effect": {
          "effect_id": "minecraft:blindness",
          "duration": $blindSec,
          "amplifier": 0,
          "apply_mode": "SET",
          "show_particles": true
        }
      },
      "particles": [ $soulFx ]
    },
$haste
    {
      "action": { "type": "FIRE", "fire": { "burn_time": 0.0 } },
      "particles": [ $soulFx ]
    }
  ],
  "cost": { "exhaust": 0.0, "cooldown_duration": $cooldown }
}
"@
}

#                id                            tier range coef slow blind cd   hasteToCaster
Fog-Spell 'black_mist'                1 4.5 0.6 4.0 2.0  2.5 $false
Fog-Spell 'night_grace'               2 5.0 0.9 5.0 3.0  4.0 $true
Fog-Spell 'dark_city'                 3 6.0 1.2 6.0 3.0  6.0 $false
Fog-Spell 'where_light_cannot_reach'  4 7.5 1.6 8.0 4.0  9.0 $true
Fog-Spell 'devour_light'              5 9.0 2.1 10.0 5.0 13.0 $false

Write-Output ''
Write-Output ('done -> ' + $dest)