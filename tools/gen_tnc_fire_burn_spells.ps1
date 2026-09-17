# One-off bootstrap for the FIRE burning chain (5 self buffs) + self_destruct.
# These are the spells that actually HAND OUT the effects registered by
# TNFireMechanics, without them the 5 effects are unreachable.
#
# Self buff shape (SELF target + STATUS_EFFECT impact) is the SAME one the
# lightning chain uses and which is already confirmed working in game.
#
# Durations are NOT free choices:
#   blaze_burn   15s = the "die within 15s and revive" window
#   inferno_burn 20s = the "die within 20s and revive" window
#   total_burn   15s = the invincibility window (Java pins hp to 1 meanwhile)
# self_destruct is AREA targeted: the radius is the TOP LEVEL "range" field
# (verified against elemental_wizards_rpg aqua_springwater: range 6 + AREA).
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
    Write-Output ("  {0,-24} {1,6:N0} B" -f "$name.json", (Get-Item $path).Length)
}

$castBuff = '{ "particle_id": "flame", "shape": "CIRCLE", "origin": "FEET", "count": 12.0, "min_speed": 0.1, "max_speed": 0.4 }'

# ---------------------------------------------------------------------------
#  BURN chain: 5 self buffs
# ---------------------------------------------------------------------------
function Burn-Spell($name, $tier, $effect, $duration, $cooldown) {
    Write-Json $name @"
{
  "school": "FIRE",
  "group": "burn",
  "range": 8.0,
  "learn": { "tier": $tier },
  "cast": {
    "duration": 0.6,
    "animation": "spell_engine:one_handed_area_charge",
    "sound": { "id": "spell_engine:generic_fire_casting", "randomness": 0 },
    "particles": [ $castBuff ]
  },
  "release": {
    "target": { "type": "SELF" },
    "animation": "spell_engine:one_handed_area_release",
    "sound": { "id": "spell_engine:generic_fire_release" }
  },
  "impact": [
    {
      "action": {
        "type": "STATUS_EFFECT",
        "status_effect": {
          "effect_id": "tnc:$effect",
          "duration": $duration,
          "amplifier": 0,
          "apply_mode": "SET",
          "show_particles": true
        }
      },
      "particles": [ $castBuff ]
    }
  ],
  "cost": { "exhaust": 0.0, "cooldown_duration": $cooldown }
}
"@
}

#                       id              tier effect         dur  cd
Burn-Spell 'fire_aspect'   1 'fire_aspect'   45.0 20.0
Burn-Spell 'ember_burn'    2 'ember_burn'    20.0 25.0
Burn-Spell 'blaze_burn'    3 'blaze_burn'    15.0 40.0     # 15s = revive window
Burn-Spell 'inferno_burn'  4 'inferno_burn'  20.0 60.0     # 20s = revive window
Burn-Spell 'total_burn'    5 'total_burn'    15.0 120.0    # 15s = invincible window

# ---------------------------------------------------------------------------
#  BALL 4/5: self destruct - AREA around the caster (radius = top level range).
#  The 10% max hp self cost is NOT here: the engine has no hp cost field, it is
#  applied in TNFireMechanics.onSpellCast.
# ---------------------------------------------------------------------------
Write-Json 'self_destruct' @"
{
  "school": "FIRE",
  "group": "ball",
  "range": 7.0,
  "learn": { "tier": 4 },
  "cast": {
    "duration": 1.2,
    "animation": "spell_engine:one_handed_area_charge",
    "sound": { "id": "spell_engine:generic_fire_casting", "randomness": 0 },
    "particles": [ { "particle_id": "flame", "shape": "SPHERE", "origin": "FEET", "count": 30.0, "min_speed": 0.2, "max_speed": 0.8 } ]
  },
  "release": {
    "target": {
      "type": "AREA",
      "area": { "include_caster": false, "distance_dropoff": "SQUARED" }
    },
    "animation": "spell_engine:one_handed_area_release",
    "sound": { "id": "spell_engine:generic_fire_release" }
  },
  "impact": [
    {
      "action": { "type": "DAMAGE", "damage": { "spell_power_coefficient": 4.0, "knockback": 1.5 } },
      "particles": [ { "particle_id": "lava", "shape": "SPHERE", "origin": "CENTER", "count": 80.0, "min_speed": 0.6, "max_speed": 3.0 } ],
      "sound": { "id": "entity.generic.explode", "volume": 1.5 }
    },
    {
      "action": { "type": "FIRE", "fire": { "burn_time": 8.0 } },
      "particles": [ { "particle_id": "flame", "shape": "SPHERE", "origin": "CENTER", "count": 40.0, "min_speed": 0.3, "max_speed": 1.5 } ]
    }
  ],
  "cost": { "exhaust": 0.0, "cooldown_duration": 30.0 }
}
"@

Write-Output ''
Write-Output ('done -> ' + $dest)