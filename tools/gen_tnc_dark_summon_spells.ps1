# DARK chain 3 (summon) - 5 spells.
#
# The doc wants real summoned creatures ("summoned shadows obey the caster, a lord
# commands nearby summons"), which needs custom entities + AI. This is the interim
# version: it hands out the SAME orb markers the wind chain uses, so the caster gets
# orbiting helpers that damage and pull nearby enemies (the mechanic is already
# implemented and tested), just with wind-coloured particles for now.
#
# COSMETIC GAP: the orb particles are hardcoded to CLOUD in TNWindMechanics.tickOrbs,
# so these look like pale wisps rather than dark shadows. Making it per-element is a
# small Java change (pass the particle in, add a dark marker) - left for the Java batch.
#
# Names from the doc: zhao-huan / zhao-huan-jing-bing / zhao-huan-tong-ling /
# an-zhi-guo-wang / xie-shen.
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
    Write-Output ("  {0,-22} {1,6:N0} B" -f "$name.json", (Get-Item $path).Length)
}

$castFx = '{ "particle_id": "soul_fire_flame", "shape": "CIRCLE", "origin": "FEET", "count": 18.0, "min_speed": 0.1, "max_speed": 0.5 }'

# $orbEffect: which orb marker to hand out (3 orbs / 5 orbs / spirit)
function Summon-Spell($name, $tier, $orbEffect, $seconds, $cooldown, $extraEffect) {
    $extraImpact = ''
    if ($extraEffect) {
        $extraImpact = @"
    {
      "action": {
        "type": "STATUS_EFFECT",
        "status_effect": {
          "effect_id": "$extraEffect",
          "duration": $seconds,
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
  "group": "dark_summon",
  "range": 10.0,
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
          "effect_id": "tnc:$orbEffect",
          "duration": $seconds,
          "amplifier": 0,
          "apply_mode": "SET",
          "show_particles": false
        }
      },
      "particles": [ $castFx ]
    },
$extraImpact
    {
      "action": { "type": "FIRE", "fire": { "burn_time": 0.0 } },
      "particles": [ $castFx ]
    }
  ],
  "cost": { "exhaust": 0.0, "cooldown_duration": $cooldown }
}
"@
}

#                 id                tier orb effect       sec  cd   extra
Summon-Spell 'summon_dark'     1 'wind_orb_three' 20.0 12.0 $null
Summon-Spell 'summon_elite'    2 'wind_orb_five'  25.0 16.0 'minecraft:resistance'
Summon-Spell 'summon_lord'     3 'wind_spirit'    30.0 25.0 'minecraft:resistance'
Summon-Spell 'dark_king'       4 'wind_spirit'    20.0 35.0 'minecraft:strength'
Summon-Spell 'evil_god'        5 'wind_spirit'    30.0 60.0 'minecraft:strength'

Write-Output ''
Write-Output ('done -> ' + $dest)