# One-off bootstrap: writes the 10 pure-data FIRE spell JSONs (ray chain + ball
# chain) into the modpack's kubejs data folder. Burning chain is NOT here - it
# needs Java-registered effects first.
#
#   Run once, then EDIT THE JSON FILES DIRECTLY - they are the source of truth.
#
# Design notes (why it looks like this):
#  * RAY chain uses a FAST PIERCING PROJECTILE instead of the engine's BEAM.
#    BEAM needs its own beam texture (texture_id) and its pierce semantics are
#    unverified; the projectile path is one we already proved works (orb chain).
#    Swap to BEAM later if you want a real light beam.
#  * BALL chain is the same shape as the lightning orb chain: PROJECTILE /
#    METEOR + top level area_impact.
#  * cost.exhaust = hunger (vanilla addExhaustion); there is NO hp cost field,
#    so "self destruct costs 10% max hp" is done in Java, not here.
#  * cooldown_duration is in SECONDS.
#
# ASCII only (tools/*.ps1 convention).

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$packDir = Get-ChildItem (Join-Path $root 'modpack') -Directory |
           Where-Object { Test-Path (Join-Path $_.FullName 'kubejs\data\tnc\spells') } |
           Select-Object -First 1
if (-not $packDir) { throw 'modpack kubejs\data\tnc\spells not found' }
$dest = Join-Path $packDir.FullName 'kubejs\data\tnc\spells'

function Write-Json($name, $json) {
    $path = Join-Path $dest "$name.json"
    [System.IO.File]::WriteAllText($path, $json, (New-Object System.Text.UTF8Encoding($false)))
    $null = ConvertFrom-Json $json          # a broken json silently disables the spell
    Write-Output ("  wrote {0,-28} {1,6:N0} B" -f "$name.json", (Get-Item $path).Length)
}

$castFire = '{ "particle_id": "flame", "shape": "PIPE", "origin": "FEET", "count": 1.0, "min_speed": 0.05, "max_speed": 0.1 }'
$trailFire = '{ "particle_id": "flame", "shape": "CIRCLE", "rotation": "LOOK", "origin": "CENTER", "count": 4.0, "min_speed": 0.0, "max_speed": 0.1 }'
$hitFire = '{ "particle_id": "flame", "shape": "SPHERE", "origin": "CENTER", "count": 40.0, "min_speed": 0.2, "max_speed": 0.8 }'
$boomFire = '{ "particle_id": "lava", "shape": "SPHERE", "origin": "CENTER", "count": 60.0, "min_speed": 0.5, "max_speed": 2.5 }'

# ---------------------------------------------------------------------------
#  RAY CHAIN - fast piercing projectile ("ray")
# ---------------------------------------------------------------------------
function Ray-Spell($name, $tier, $velocity, $coef, $scale, $cooldown, $pierce, $radius, $castSeconds) {
    $area = ''
    if ($radius -gt 0) {
        $area = @"
  "area_impact": {
    "radius": $radius,
    "area": { "distance_dropoff": "SQUARED" },
    "particles": [ $boomFire ],
    "sound": { "id": "entity.blaze.shoot", "volume": 0.8 }
  },
"@
    }
    $json = @"
{
  "school": "FIRE",
  "group": "ray",
  "range": 32.0,
  "learn": { "tier": $tier },
  "cast": {
    "duration": $castSeconds,
    "animation": "spell_engine:one_handed_projectile_charge",
    "sound": { "id": "spell_engine:generic_fire_casting", "randomness": 0 },
    "particles": [ $castFire ]
  },
  "release": {
    "target": {
      "type": "PROJECTILE",
      "projectile": {
        "launch_properties": { "velocity": $velocity },
        "projectile": {
          "homing_angle": 0.0,
          "perks": { "pierce": $pierce },
          "client_data": {
            "travel_particles": [ $trailFire ],
            "model": { "model_id": "tnc:projectile/fire_ray", "scale": $scale }
          }
        }
      }
    },
    "animation": "spell_engine:one_handed_projectile_release",
    "sound": { "id": "spell_engine:generic_fire_release" }
  },
  "impact": [
    {
      "action": { "type": "DAMAGE", "damage": { "spell_power_coefficient": $coef, "knockback": 0.1 } },
      "particles": [ $hitFire ]
    }
  ],
$area
  "cost": { "exhaust": 0.0, "cooldown_duration": $cooldown }
}
"@
    Write-Json $name $json
}

#                                    id                    tier  vel  coef scale cd  pierce radius cast
Ray-Spell 'fire_ray'            1 1.6 1.0 0.7  1.0 2  0.0 0.4
Ray-Spell 'thick_fire_ray'      2 1.5 1.5 1.1  1.5 3  0.0 0.5
# (note moved to PROJECT-STATE; kept ASCII for GBK safety)
Ray-Spell 'triple_fire_ray'     3 1.5 1.2 0.8  2.5 2  0.0 0.6
Ray-Spell 'explosive_fire_ray'  4 1.4 1.6 1.0  3.5 2  3.0 0.7
Ray-Spell 'cataclysm_fire_ray'  5 1.3 2.4 1.4  8.0 2  6.0 1.0

# ---------------------------------------------------------------------------
#  BALL CHAIN - fireball / big / giant / meteor (self_destruct is Java)
# ---------------------------------------------------------------------------
function Ball-Spell($name, $tier, $velocity, $coef, $scale, $cooldown, $radius, $castSeconds) {
    $json = @"
{
  "school": "FIRE",
  "group": "ball",
  "range": 40.0,
  "learn": { "tier": $tier },
  "cast": {
    "duration": $castSeconds,
    "animation": "spell_engine:one_handed_projectile_charge",
    "sound": { "id": "spell_engine:generic_fire_casting", "randomness": 0 },
    "particles": [ $castFire ]
  },
  "release": {
    "target": {
      "type": "PROJECTILE",
      "projectile": {
        "launch_properties": { "velocity": $velocity },
        "projectile": {
          "homing_angle": 0.4,
          "client_data": {
            "travel_particles": [ $trailFire ],
            "model": { "model_id": "tnc:projectile/fire_ball", "scale": $scale }
          }
        }
      }
    },
    "animation": "spell_engine:one_handed_projectile_release",
    "sound": { "id": "spell_engine:generic_fire_release" }
  },
  "impact": [
    {
      "action": { "type": "DAMAGE", "damage": { "spell_power_coefficient": $coef, "knockback": 0.4 } },
      "particles": [ $hitFire ],
      "sound": { "id": "entity.blaze.shoot", "volume": 0.6 }
    }
  ],
  "area_impact": {
    "radius": $radius,
    "area": { "distance_dropoff": "SQUARED" },
    "particles": [ $boomFire ],
    "sound": { "id": "entity.generic.explode", "volume": 0.9 }
  },
  "cost": { "exhaust": 0.0, "cooldown_duration": $cooldown }
}
"@
    Write-Json $name $json
}

Ball-Spell 'fireball'       1 1.2 1.0 0.8 1.5 2.0 0.5
Ball-Spell 'great_fireball' 2 1.1 1.8 1.2 3.0 3.0 0.7
Ball-Spell 'giant_fireball' 3 1.0 2.2 1.8 4.5 5.0 0.9

# (note moved to PROJECT-STATE; kept ASCII for GBK safety)
Write-Json 'meteor_fireball' @"
{
  "school": "FIRE",
  "group": "ball",
  "range": 48.0,
  "learn": { "tier": 5 },
  "cast": {
    "duration": 1.5,
    "animation": "spell_engine:one_handed_projectile_charge",
    "sound": { "id": "spell_engine:generic_fire_casting", "randomness": 0 },
    "particles": [ $castFire ]
  },
  "release": {
    "target": {
      "type": "METEOR",
      "meteor": {
        "launch_height": 14,
        "launch_radius": 5,
        "launch_properties": { "velocity": 0.9, "extra_launch_count": 3, "extra_launch_delay": 4 },
        "projectile": {
          "client_data": {
            "travel_particles": [ $trailFire ],
            "model": { "model_id": "tnc:projectile/fire_ball", "scale": 2.6 }
          }
        }
      }
    },
    "animation": "spell_engine:one_handed_area_release",
    "sound": { "id": "spell_engine:generic_fire_release" }
  },
  "impact": [
    {
      "action": { "type": "DAMAGE", "damage": { "spell_power_coefficient": 2.5, "knockback": 1.0 } },
      "particles": [ $hitFire ],
      "sound": { "id": "entity.generic.explode", "volume": 1.0 }
    }
  ],
  "area_impact": {
    "radius": 8.0,
    "extra_radius": { "power_coefficient": 0.5 },
    "area": { "distance_dropoff": "SQUARED" },
    "particles": [ $boomFire ],
    "sound": { "id": "entity.generic.explode", "volume": 1.5 }
  },
  "cost": { "exhaust": 0.0, "cooldown_duration": 12.0 }
}
"@

Write-Output ''
Write-Output ('done -> ' + $dest)
Write-Output 'NOTE: self_destruct (4/5, ball chain) is Java-only (costs 10% max hp) - not written here.'
