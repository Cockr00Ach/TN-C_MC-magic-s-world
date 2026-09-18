# EARTH chain 2 (move / terrain) - 5 spells.
#
# The doc describes this line as "make the ground bulge, knock the target off
# balance, damage and control; big AoE ground structures later". Implemented as
# a CLOUD quake field: damage + knockback (off balance) + vanilla slowness,
# re-applied every 20 ticks, so the ground keeps erupting for its duration.
#
# Names from the doc: tu-dong / tu-ci / yan-tu / da-di-zhi-lie / zhen-xing.
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
    Write-Output ("  {0,-18} {1,6:N0} B" -f "$name.json", (Get-Item $path).Length)
}

$castFx  = '{ "particle_id": "crit", "shape": "CIRCLE", "origin": "FEET", "count": 12.0, "min_speed": 0.1, "max_speed": 0.4 }'
$quakeFx = '{ "particle_id": "crit", "shape": "SPHERE", "origin": "CENTER", "count": 20.0, "min_speed": 0.2, "max_speed": 0.9 }'

function Move-Spell($name, $tier, $radius, $coef, $knockback, $slowSec, $life, $cooldown) {
    Write-Json $name @"
{
  "school": "EARTH",
  "group": "earth_move",
  "range": 16.0,
  "learn": { "tier": $tier },
  "cast": {
    "duration": 0.7,
    "animation": "spell_engine:one_handed_area_charge",
    "sound": { "id": "spell_engine:generic_arcane_casting", "randomness": 0 },
    "particles": [ $castFx ]
  },
  "release": {
    "target": {
      "type": "CLOUD",
      "cloud": {
        "volume": {
          "radius": $radius,
          "area": { "distance_dropoff": "SQUARED" }
        },
        "time_to_live_seconds": $life,
        "impact_tick_interval": 20,
        "delay_ticks": 4,
        "presence_sound": { "id": "block.stone.step", "volume": 0.3 },
        "client_data": { "particles": [ $quakeFx ] },
        "spawn": { "particles": [ $quakeFx ], "sound": { "id": "block.stone.break", "volume": 1.0 } }
      }
    },
    "animation": "spell_engine:one_handed_area_release",
    "sound": { "id": "block.stone.break", "volume": 1.0 }
  },
  "impact": [
    {
      "action": { "type": "DAMAGE", "damage": { "spell_power_coefficient": $coef, "knockback": $knockback } },
      "particles": [ $quakeFx ],
      "sound": { "id": "block.stone.break", "volume": 0.7 }
    },
    {
      "action": {
        "type": "STATUS_EFFECT",
        "apply_to_caster": false,
        "status_effect": {
          "effect_id": "minecraft:slowness",
          "duration": $slowSec,
          "amplifier": 0,
          "apply_mode": "SET",
          "show_particles": true
        }
      },
      "particles": [ $castFx ]
    }
  ],
  "cost": { "exhaust": 0.0, "cooldown_duration": $cooldown }
}
"@
}

#             id              tier radius coef knock slow life cd
Move-Spell 'earth_stir'    1 4.0 0.7 0.5 3.0 8.0 3.0
Move-Spell 'earth_spike'   2 4.5 1.1 0.7 4.0 10.0 4.5
Move-Spell 'rock_burst'    3 5.5 1.4 0.9 5.0 12.0 6.5
Move-Spell 'earth_rift'    4 7.0 1.8 1.1 6.0 14.0 9.0
Move-Spell 'star_quake'    5 9.0 2.3 1.4 8.0 16.0 14.0

Write-Output ''
Write-Output ('done -> ' + $dest)