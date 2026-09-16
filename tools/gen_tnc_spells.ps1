# One-off bootstrap: writes the 10 TN-C lightning spell JSONs (orb chain + speed
# chain) into the modpack's kubejs data folder, which is the tracked source.
#
#   Run once, then EDIT THE JSON FILES DIRECTLY - they are the source of truth.
#   This script only exists so the first version of all 10 is structurally
#   identical (and so the numbers are documented in one table).
#
# Spell format facts behind every field here (from decompiling spell_engine
# 0.15.12 and from real spells in wizards / spellbladenext):
#   * projectile : release.target.type = "PROJECTILE"
#                  -> target.projectile.launch_properties.velocity
#                     target.projectile.projectile.client_data.travel_particles[]
#                     target.projectile.projectile.client_data.model{model_id, scale}
#   * meteor     : release.target.type = "METEOR"
#                  -> target.meteor{launch_height, launch_radius, launch_properties, projectile{...}}
#   * explosion  : top level "area_impact"{radius, area{distance_dropoff}, particles[], sound{}}
#   * self buff  : release.target.type = "SELF" + impact[].action.type = "STATUS_EFFECT"
#   * teleport   : impact[].action.type = "TELEPORT" + teleport{mode:"FORWARD", forward{distance}}
#   * cost       : exhaust = hunger (vanilla addExhaustion), cooldown_duration = SECONDS
#   * icon       : assets/<ns>/textures/spell/<spell path>.png (16x16), not settable
#
# ASCII only (repo convention for tools/).

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$packDir = Get-ChildItem (Join-Path $root 'modpack') -Directory |
           Where-Object { Test-Path (Join-Path $_.FullName 'kubejs\data\tnc\spells') } |
           Select-Object -First 1
if (-not $packDir) { throw 'modpack kubejs\data\tnc\spells not found' }
$dest = Join-Path $packDir.FullName 'kubejs\data\tnc\spells'
if (-not (Test-Path $dest)) { throw "spell folder not found: $dest" }

function Write-Json($name, $json) {
    $path = Join-Path $dest "$name.json"
    # UTF-8 without BOM: kubejs/data files are read as UTF-8 by the game
    [System.IO.File]::WriteAllText($path, $json, (New-Object System.Text.UTF8Encoding($false)))
    # validate: a broken json here would silently disable the spell in game
    $null = ConvertFrom-Json $json
    Write-Output ("  wrote {0,-28} {1,6:N0} B" -f "$name.json", (Get-Item $path).Length)
}

# ---- shared particle blocks -------------------------------------------------
$arcA = '{ "particle_id": "spell_engine:electric_arc_a", "shape": "CIRCLE", "rotation": "LOOK", "origin": "CENTER", "count": 4.0, "min_speed": 0.0, "max_speed": 0.15 }'
$arcB = '{ "particle_id": "spell_engine:electric_arc_b", "shape": "SPHERE", "origin": "CENTER", "count": 40.0, "min_speed": 0.2, "max_speed": 0.8 }'
$castArc = '{ "particle_id": "spell_engine:electric_arc_b", "shape": "PIPE", "origin": "FEET", "count": 1.0, "min_speed": 0.05, "max_speed": 0.1 }'

# ---------------------------------------------------------------------------
#  ORB CHAIN (orb chain)
# ---------------------------------------------------------------------------

# 1/5 thunder_orb - one straight-flying orb
Write-Json 'thunder_orb' @"
{
  "school": "LIGHTNING",
  "group": "orb",
  "range": 32.0,
  "learn": { "tier": 1 },
  "cast": {
    "duration": 0.5,
    "animation": "spell_engine:one_handed_projectile_charge",
    "sound": { "id": "spell_engine:generic_lightning_casting", "randomness": 0 },
    "particles": [ $castArc ]
  },
  "release": {
    "target": {
      "type": "PROJECTILE",
      "projectile": {
        "launch_properties": { "velocity": 1.3 },
        "projectile": {
          "homing_angle": 0.5,
          "client_data": {
            "travel_particles": [ $arcA ],
            "model": { "model_id": "tnc:projectile/thunder_orb", "scale": 0.8 }
          }
        }
      }
    },
    "animation": "spell_engine:one_handed_projectile_release",
    "sound": { "id": "spell_engine:generic_lightning_release" }
  },
  "impact": [
    {
      "action": { "type": "DAMAGE", "damage": { "spell_power_coefficient": 1.0, "knockback": 0.2 } },
      "particles": [ $arcB ],
      "sound": { "id": "entity.lightning_bolt.impact", "volume": 0.5 }
    }
  ],
  "cost": { "exhaust": 0.0, "cooldown_duration": 1.0 }
}
"@

# 2/5 great_thunder_orb - bigger, hit harder, a bit slower
Write-Json 'great_thunder_orb' @"
{
  "school": "LIGHTNING",
  "group": "orb",
  "range": 40.0,
  "learn": { "tier": 2 },
  "cast": {
    "duration": 0.7,
    "animation": "spell_engine:one_handed_projectile_charge",
    "sound": { "id": "spell_engine:generic_lightning_casting", "randomness": 0 },
    "particles": [ $castArc ]
  },
  "release": {
    "target": {
      "type": "PROJECTILE",
      "projectile": {
        "launch_properties": { "velocity": 1.1 },
        "projectile": {
          "homing_angle": 0.8,
          "client_data": {
            "travel_particles": [ $arcA ],
            "model": { "model_id": "tnc:projectile/thunder_orb", "scale": 1.4 }
          }
        }
      }
    },
    "animation": "spell_engine:one_handed_projectile_release",
    "sound": { "id": "spell_engine:generic_lightning_release" }
  },
  "impact": [
    {
      "action": { "type": "DAMAGE", "damage": { "spell_power_coefficient": 1.8, "knockback": 0.4 } },
      "particles": [ $arcB ],
      "sound": { "id": "entity.lightning_bolt.impact", "volume": 0.6 }
    }
  ],
  "cost": { "exhaust": 0.0, "cooldown_duration": 2.0 }
}
"@

# 3/5 orbiting_thunder_orb - self aura (the actual orbit is driven by the tnc:orbiting_thunder_orb effect in Java)
Write-Json 'orbiting_thunder_orb' @"
{
  "school": "LIGHTNING",
  "group": "orb",
  "range": 0.0,
  "learn": { "tier": 3 },
  "cast": {
    "duration": 0.6,
    "animation": "spell_engine:one_handed_area_charge",
    "sound": { "id": "spell_engine:generic_lightning_casting", "randomness": 0 },
    "particles": [ $castArc ]
  },
  "release": {
    "target": { "type": "SELF" },
    "animation": "spell_engine:one_handed_area_release",
    "sound": { "id": "spell_engine:generic_lightning_release" }
  },
  "impact": [
    {
      "action": {
        "type": "STATUS_EFFECT",
        "status_effect": {
          "effect_id": "tnc:orbiting_thunder_orb",
          "duration": 10.0,
          "amplifier": 0,
          "apply_mode": "SET",
          "show_particles": false
        }
      },
      "particles": [ $arcB ]
    }
  ],
  "cost": { "exhaust": 0.0, "cooldown_duration": 14.0 }
}
"@

# 4/5 explosive_thunder_orb - explodes on impact
Write-Json 'explosive_thunder_orb' @"
{
  "school": "LIGHTNING",
  "group": "orb",
  "range": 40.0,
  "learn": { "tier": 4 },
  "cast": {
    "duration": 0.8,
    "animation": "spell_engine:one_handed_projectile_charge",
    "sound": { "id": "spell_engine:generic_lightning_casting", "randomness": 0 },
    "particles": [ $castArc ]
  },
  "release": {
    "target": {
      "type": "PROJECTILE",
      "projectile": {
        "launch_properties": { "velocity": 1.0 },
        "projectile": {
          "homing_angle": 0.8,
          "client_data": {
            "travel_particles": [ $arcA ],
            "model": { "model_id": "tnc:projectile/thunder_orb", "scale": 1.6 }
          }
        }
      }
    },
    "animation": "spell_engine:one_handed_projectile_release",
    "sound": { "id": "spell_engine:generic_lightning_release" }
  },
  "impact": [
    {
      "action": { "type": "DAMAGE", "damage": { "spell_power_coefficient": 1.6, "knockback": 0.5 } },
      "particles": [ $arcB ]
    }
  ],
  "area_impact": {
    "radius": 3.5,
    "area": { "distance_dropoff": "SQUARED" },
    "particles": [ $arcB ],
    "sound": { "id": "entity.lightning_bolt.thunder", "volume": 0.8 }
  },
  "cost": { "exhaust": 0.0, "cooldown_duration": 3.5 }
}
"@

# 5/5 cataclysm_thunder_orb - METEOR + huge area
Write-Json 'cataclysm_thunder_orb' @"
{
  "school": "LIGHTNING",
  "group": "orb",
  "range": 48.0,
  "learn": { "tier": 5 },
  "cast": {
    "duration": 1.5,
    "animation": "spell_engine:one_handed_projectile_charge",
    "sound": { "id": "spell_engine:generic_lightning_casting", "randomness": 0 },
    "particles": [ $castArc ]
  },
  "release": {
    "target": {
      "type": "METEOR",
      "meteor": {
        "launch_height": 14,
        "launch_radius": 5,
        "launch_properties": {
          "velocity": 0.9,
          "extra_launch_count": 3,
          "extra_launch_delay": 4
        },
        "projectile": {
          "client_data": {
            "travel_particles": [ $arcA ],
            "model": { "model_id": "tnc:projectile/thunder_orb", "scale": 2.6 }
          }
        }
      }
    },
    "animation": "spell_engine:one_handed_area_release",
    "sound": { "id": "spell_engine:generic_lightning_release" }
  },
  "impact": [
    {
      "action": { "type": "DAMAGE", "damage": { "spell_power_coefficient": 2.5, "knockback": 1.0 } },
      "particles": [ $arcB ],
      "sound": { "id": "entity.lightning_bolt.impact", "volume": 1.0 }
    }
  ],
  "area_impact": {
    "radius": 8.0,
    "extra_radius": { "power_coefficient": 0.5 },
    "area": { "distance_dropoff": "SQUARED" },
    "particles": [ $arcB ],
    "sound": { "id": "entity.lightning_bolt.thunder", "volume": 1.5 }
  },
  "cost": { "exhaust": 0.0, "cooldown_duration": 12.0 }
}
"@

# ---------------------------------------------------------------------------
#  SPEED CHAIN (speed chain)
# ---------------------------------------------------------------------------

$selfRelease = @'
    "target": { "type": "SELF" },
    "animation": "spell_engine:one_handed_area_release",
    "sound": { "id": "spell_engine:generic_lightning_release" }
'@

function Buff-Spell($name, $tier, $castSeconds, $effectId, $amplifier, $effectSeconds, $cooldown, $radius) {
    $json = @"
{
  "school": "LIGHTNING",
  "group": "speed",
  "range": 0.0,
  "learn": { "tier": $tier },
  "cast": {
    "duration": $castSeconds,
    "animation": "spell_engine:one_handed_area_charge",
    "sound": { "id": "spell_engine:generic_lightning_casting", "randomness": 0 },
    "particles": [ $castArc ]
  },
  "release": {
$selfRelease
  },
  "impact": [
    {
      "action": {
        "type": "STATUS_EFFECT",
        "status_effect": {
          "effect_id": "$effectId",
          "duration": $effectSeconds,
          "amplifier": $amplifier,
          "apply_mode": "SET",
          "show_particles": false
        }
      },
      "particles": [ $arcB ]
    }
  ],
  "cost": { "exhaust": 0.0, "cooldown_duration": $cooldown }
}
"@
    Write-Json $name $json
}

# 1/5 lightning_haste - +25% speed
Buff-Spell 'lightning_haste' 1 0.4 'tnc:lightning_haste' 0 12.0 8.0

# 3/5 lightning_wind - +70% speed (trail damage handled in Java)
Buff-Spell 'lightning_wind' 3 0.6 'tnc:lightning_wind' 0 15.0 16.0

# 4/5 lightning_recharge - +50% speed (same effect, amp 1) + half mana back (Java)
Buff-Spell 'lightning_recharge' 4 0.5 'tnc:lightning_haste' 1 12.0 20.0

# 5/5 lightning_ascension - damage buff (effect) + no cooldown (Java)
Buff-Spell 'lightning_ascension' 5 1.0 'tnc:lightning_ascension' 0 12.0 45.0

# 2/5 lightning_blink - teleport along the look vector (the only non-buff)
Write-Json 'lightning_blink' @"
{
  "school": "LIGHTNING",
  "group": "speed",
  "range": 0.0,
  "learn": { "tier": 2 },
  "cast": {
    "duration": 0.0,
    "animation": "spell_engine:one_handed_area_charge",
    "sound": { "id": "spell_engine:generic_lightning_casting", "randomness": 0 },
    "particles": [ $castArc ]
  },
  "release": {
    "target": { "type": "SELF" },
    "animation": "spell_engine:one_handed_area_release",
    "sound": { "id": "spell_engine:generic_lightning_release" }
  },
  "impact": [
    {
      "action": {
        "type": "TELEPORT",
        "teleport": {
          "mode": "FORWARD",
          "forward": { "distance": 12.0 },
          "depart_particles": [ $arcB ],
          "arrive_particles": [ $arcB ]
        }
      }
    }
  ],
  "cost": { "exhaust": 0.0, "cooldown_duration": 4.0 }
}
"@

Write-Output ''
Write-Output ('done -> ' + $dest)
