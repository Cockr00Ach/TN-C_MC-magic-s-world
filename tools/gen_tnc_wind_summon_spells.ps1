# WIND chain 2 (SUMMON) - orbit-ball version.
#
# Per the user: "wind spirits should ideally be independent, but orbiting balls
# are fine to start with". So each spell just hands out a marker effect and
# TNWindMechanics draws/attacks with the balls.
#
# ---- LADDER REPAIRED 2026-10-07 --------------------------------------------
# Tiers 3, 4 and 5 ALL handed out the same marker (wind_spirit), which made the
# ladder broken:
#   * TNWindMechanics.orbCount() returned 5 orbs for wind_spirit, so tier 4
#     ("summon three wind spirits") did not actually summon anything different;
#   * worse, tier 4's duration was 15s against tier 3's 30s and its cooldown was
#     longer, so TIER 4 WAS STRICTLY WORSE THAN TIER 3 in every dimension;
#   * tier 5 was a straight copy of tier 3 (the old comment even said so).
# Fixed by giving tiers 4 and 5 their own markers, and by turning the ladder in
# TNWindMechanics into one explicit table. Current ladder (monotonic on every
# axis: orbs, damage, duration):
#   t1 wind_orb_three   3 orbs  x1
#   t2 wind_orb_five    5 orbs  x1
#   t3 wind_spirit      5 orbs  x2
#   t4 wind_spirit_ii   5 orbs  x3
#   t5 wind_spirit_lord 5 orbs  x4
#
# Durations come from the user's spec where given:
#   3/5 "summon orbs"   - no duration specified, 20s / 25s chosen
#   spirit "lasts 30s"  - 30s
#   3 spirits           - the spec said "each exist 15s", but with the shared
#                         marker that only ever made tier 4 weaker; it is now a
#                         stronger tier with 40s. Revisit when the spirits become
#                         real entities (see docs/spells/<wind redesign>).
#   tier 5              - was a PLACEHOLDER; it now has its own marker so the
#                         slot is at least a real step up. Still needs the real
#                         design.
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

#                         id                  tier effect             sec   cd
Orb-Spell 'wind_orb'              1 'wind_orb_three'  20.0 12.0
Orb-Spell 'wind_orb_swarm'        2 'wind_orb_five'   25.0 16.0
Orb-Spell 'wind_spirit'           3 'wind_spirit'     30.0 25.0
Orb-Spell 'triple_wind_spirit'    4 'wind_spirit_ii'  40.0 35.0
Orb-Spell 'wind_spirit_lord'      5 'wind_spirit_lord' 45.0 60.0

Write-Output ''
Write-Output ('done -> ' + $dest)