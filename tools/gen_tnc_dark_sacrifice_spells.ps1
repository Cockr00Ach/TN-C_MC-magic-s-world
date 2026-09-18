# DARK chain 2 (sacrifice) - 5 spells.
#
# "Burn your own blood for power" - the self-drain REUSES the fire chain's burn
# effects (tnc:ember_burn / blaze_burn / inferno_burn / total_burn) because those
# are already implemented in Java with the two properties this line needs:
#   * percentage self-drain per second
#   * NEVER lethal (health is floored at 1)
# tier 5 reuses tnc:total_burn, which is exactly the doc's "wo-wei-shen":
# health pinned to 1, invulnerable for the duration.
#
# KNOWN GAP: those four fire effects boost spell_power:fire, not soul. So the
# DAMAGE part of this line currently only comes from vanilla strength (melee).
# A soul-specific effect is needed for the full design; swapping the effect_id
# later is a one-line change per tier.
#
# Names from the doc: yi-shang-huan-shang / ran-xue / xian-ji / duo-she / wo-wei-shen.
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

$castFx = '{ "particle_id": "soul_fire_flame", "shape": "CIRCLE", "origin": "FEET", "count": 16.0, "min_speed": 0.1, "max_speed": 0.4 }'

function Sacrifice-Spell($name, $tier, $burnEffect, $seconds, $strengthAmp, $cooldown) {
    Write-Json $name @"
{
  "school": "SOUL",
  "group": "dark_sacrifice",
  "range": 8.0,
  "learn": { "tier": $tier },
  "cast": {
    "duration": 0.7,
    "animation": "spell_engine:one_handed_area_charge",
    "sound": { "id": "spell_engine:generic_soul_casting", "randomness": 0 },
    "particles": [ $castFx ]
  },
  "release": {
    "target": { "type": "SELF" },
    "animation": "spell_engine:one_handed_area_release",
    "sound": { "id": "spell_engine:generic_soul_release" }
  },
  "impact": [
    {
      "action": {
        "type": "STATUS_EFFECT",
        "status_effect": {
          "effect_id": "tnc:$burnEffect",
          "duration": $seconds,
          "amplifier": 0,
          "apply_mode": "SET",
          "show_particles": true
        }
      },
      "particles": [ $castFx ]
    },
    {
      "action": {
        "type": "STATUS_EFFECT",
        "status_effect": {
          "effect_id": "minecraft:strength",
          "duration": $seconds,
          "amplifier": $strengthAmp,
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

#                       id             tier burn effect      sec  strengthAmp cd
Sacrifice-Spell 'trade_wounds'   1 'fire_aspect'   20.0 0 15.0
Sacrifice-Spell 'blood_burn'     2 'ember_burn'    25.0 0 20.0
Sacrifice-Spell 'sacrifice'      3 'blaze_burn'    30.0 1 30.0
Sacrifice-Spell 'possess'        4 'inferno_burn'  25.0 2 45.0
Sacrifice-Spell 'i_am_god'       5 'total_burn'    15.0 3 90.0

Write-Output ''
Write-Output ('done -> ' + $dest)