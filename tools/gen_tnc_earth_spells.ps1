# EARTH chain 3 (mud, slow field) + chain 4 (ring, defence) - 10 spells.
#
# Chain 3 = AREA + damage + the wind chain's slow (tnc:gale_slow) as a stand-in.
# Chain 4 = SELF + vanilla absorption (a shield ring), which needs no Java at all:
#           STATUS_EFFECT with effect_id "minecraft:absorption" is vanilla.
#
# Names come from the design doc: tu-ni line = tu-ni/tu-keng/ni-zhao/tu-long/di-bao-tian-xing,
# tu-huan line = tu-huan/tu-lao/yan-guo/wan-wu-sheng/da-di-shen-en.
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

$dustFx = '{ "particle_id": "crit", "shape": "SPHERE", "origin": "CENTER", "count": 30.0, "min_speed": 0.2, "max_speed": 1.2 }'
$castFx = '{ "particle_id": "crit", "shape": "CIRCLE", "origin": "FEET", "count": 12.0, "min_speed": 0.1, "max_speed": 0.4 }'

# ---------------- chain 3: mud (AREA + slow) ----------------
function Mud-Spell($name, $tier, $range, $coef, $seconds, $cooldown) {
    Write-Json $name @"
{
  "school": "EARTH",
  "group": "earth_mud",
  "range": $range,
  "learn": { "tier": $tier },
  "cast": {
    "duration": 0.8,
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
    "sound": { "id": "block.mud.place", "volume": 1.0 }
  },
  "impact": [
    {
      "action": { "type": "DAMAGE", "damage": { "spell_power_coefficient": $coef, "knockback": 0.0 } },
      "particles": [ $dustFx ]
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
      "particles": [ $dustFx ]
    }
  ],
  "cost": { "exhaust": 0.0, "cooldown_duration": $cooldown }
}
"@
}

#                id                 tier range coef  sec   cd
Mud-Spell 'mud'             1 4.0 0.6 4.0 3.0
Mud-Spell 'pit'             2 4.5 0.9 5.0 4.5
Mud-Spell 'mire'            3 5.5 1.2 6.0 6.0
Mud-Spell 'earth_flow'      4 6.5 1.5 7.0 8.0
Mud-Spell 'earth_core_burst' 5 8.0 2.0 9.0 12.0

# ---------------- chain 4: ring (SELF + vanilla absorption shield) ----------------
function Ring-Spell($name, $tier, $absorptionSeconds, $amplifier, $cooldown, $extra) {
    $extraImpact = ''
    if ($extra) {
        $extraImpact = @"
    {
      "action": {
        "type": "STATUS_EFFECT",
        "apply_to_caster": true,
        "status_effect": {
          "effect_id": "$extra",
          "duration": $absorptionSeconds,
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
  "school": "EARTH",
  "group": "earth_ring",
  "range": 8.0,
  "learn": { "tier": $tier },
  "cast": {
    "duration": 0.6,
    "animation": "spell_engine:one_handed_area_charge",
    "sound": { "id": "spell_engine:generic_arcane_casting", "randomness": 0 },
    "particles": [ $castFx ]
  },
  "release": {
    "target": { "type": "SELF" },
    "animation": "spell_engine:one_handed_area_release",
    "sound": { "id": "block.stone.place", "volume": 1.0 }
  },
  "impact": [
    {
      "action": {
        "type": "STATUS_EFFECT",
        "status_effect": {
          "effect_id": "minecraft:absorption",
          "duration": $absorptionSeconds,
          "amplifier": $amplifier,
          "apply_mode": "SET",
          "show_particles": true
        }
      },
      "particles": [ $castFx ]
    },
$extraImpact
    {
      "action": {
        "type": "STATUS_EFFECT",
        "status_effect": {
          "effect_id": "minecraft:resistance",
          "duration": $absorptionSeconds,
          "amplifier": 0,
          "apply_mode": "SET",
          "show_particles": false
        }
      },
      "particles": [ $castFx ]
    }
  ],
  "cost": { "exhaust": 0.0, "cooldown_duration": $cooldown }
}
"@
}

#                 id                 tier sec  amp cd   extra
Ring-Spell 'earth_ring'        1 10.0 0 12.0 $null
Ring-Spell 'earth_prison'      2 12.0 1 16.0 $null
Ring-Spell 'rock_kingdom'      3 15.0 2 22.0 $null
Ring-Spell 'all_things_grow'   4 20.0 3 30.0 'minecraft:regeneration'
Ring-Spell 'earth_god_blessing' 5 25.0 4 40.0 'minecraft:regeneration'

Write-Output ''
Write-Output ('done -> ' + $dest)