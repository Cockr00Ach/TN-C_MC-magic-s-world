# WIND chain 2 (SUMMON) - orbit-ball version.
#
# Per the user: "wind spirits should ideally be independent, but orbiting balls
# are fine to start with". So each spell just hands out a marker effect and
# TNWindMechanics draws/attacks with the balls:
#   wind_orb_three -> 3 balls, wind_orb_five -> 5 balls, wind_spirit -> 5 balls
#   that hit twice as hard.
#
# Durations come from the user's spec where given:
#   3/5 "summon orbs"   - no duration specified, 20s / 25s chosen
#   spirit "lasts 30s"  - 30s
#   3 spirits "each exist 15s after the big ball" - 15s
#   tier 5 is a PLACEHOLDER: the user never defined it, so it reuses the spirit
#   marker with a longer duration (better than an empty slot that can be learned
#   but never cast - that bug already happened once with the earlier 10 spells).
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
    Write-Output ("  {0,-24} {1,6} B" -f "$name.json", (Get-Item $path).Length)
}

$castFx = '{ "particle_id": "cloud", "shape": "CIRCLE", "origin": "FEET", "count": 10.0, "min_speed": 0.1, "max_speed": 0.4 }'

function Orb-Spell($name, $tier, $effect, $seconds, $cooldown) {
    Write-Json $name @"
{
  "school": "AIR",
  "group": "summon",
  "range": 12.0,
  "learn": { "tier": $tier },
  "cast": {
    "duration": 0.6,
    "animation": "spell_engine:one_handed_area_charge",
    "sound": { "id": "spell_engine:generic_wind_charging", "randomness": 0 },
    "particles": [ $castFx ]
  },
  "release": {
    "target": { "type": "SELF" },
    "animation": "spell_engine:one_handed_area_release",
    "sound": { "id": "spell_engine:generic_wind_charging" }
  },
  "impact": [
    {
      "action": {
        "type": "STATUS_EFFECT",
        "status_effect": {
          "effect_id": "tnc:$effect",
          "duration": $seconds,
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

#                         id                  tier effect            sec   cd
Orb-Spell 'wind_orb'              1 'wind_orb_three' 20.0 12.0
Orb-Spell 'wind_orb_swarm'        2 'wind_orb_five'  25.0 16.0
Orb-Spell 'wind_spirit'           3 'wind_spirit'    30.0 25.0
Orb-Spell 'triple_wind_spirit'    4 'wind_spirit'    15.0 35.0
Orb-Spell 'wind_spirit_lord'      5 'wind_spirit'    30.0 60.0   # PLACEHOLDER tier

Write-Output ''
Write-Output ('done -> ' + $dest)