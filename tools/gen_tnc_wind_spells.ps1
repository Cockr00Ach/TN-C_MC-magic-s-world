# FIRE... no: WIND chain 1 (flight / speed) - 5 pure-data self buffs.
#
# IMPORTANT: the wind school is "AIR", NOT "WIND" (verified across the pack's
# real spells: 6 use AIR; the Element enum hint is spell_power:air).
#
# These hand out the effects registered by TNWindMechanics:
#   wind_flight   (marker: while present the player may fly)
#   wind_speed_i/ii/iii  (+100% / +150% / +300% movement speed)
#   wind_power_i/ii      (+50% / +100% wind spell power)
#
# Tiers 4/5 do NOT need wind_flight: from FLIGHT chain progress >= 4 the Java
# side grants permanent flight on its own ("unlock it and you can always fly").
#
# Not in JSON (Java, still to do): wind_god_descent's "no cooldown" and
# "half mana cost" - both tied to the buff being active, see TNWindMechanics.
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
    Write-Output ("  {0,-26} {1,6:N0} B" -f "$name.json", (Get-Item $path).Length)
}

$castFx = '{ "particle_id": "cloud", "shape": "CIRCLE", "origin": "FEET", "count": 14.0, "min_speed": 0.1, "max_speed": 0.5 }'

# $effects: list of @(effect_path, seconds)
function Wind-Spell($name, $tier, $cooldown, $effects) {
    $blocks = @()
    foreach ($e in $effects) {
        $blocks += @"
    {
      "action": {
        "type": "STATUS_EFFECT",
        "status_effect": {
          "effect_id": "tnc:$($e[0])",
          "duration": $($e[1]),
          "amplifier": 0,
          "apply_mode": "SET",
          "show_particles": true
        }
      },
      "particles": [ $castFx ]
    }
"@
    }
    $impact = ($blocks -join ",`n")
    Write-Json $name @"
{
  "school": "AIR",
  "group": "flight",
  "range": 8.0,
  "learn": { "tier": $tier },
  "cast": {
    "duration": 0.4,
    "animation": "spell_engine:one_handed_area_charge",
    "sound": { "id": "spell_engine:generic_wind_casting", "randomness": 0 },
    "particles": [ $castFx ]
  },
  "release": {
    "target": { "type": "SELF" },
    "animation": "spell_engine:one_handed_area_release",
    "sound": { "id": "spell_engine:generic_wind_release" }
  },
  "impact": [
$impact
  ],
  "cost": { "exhaust": 0.0, "cooldown_duration": $cooldown }
}
"@
}

#                          id                   tier cd   effects (effect, seconds)
Wind-Spell 'wind_field'          1 15.0 @(, @('wind_flight', 5.0))
Wind-Spell 'wind_speed'          2 20.0 @(@('wind_flight', 5.0), @('wind_speed_i', 5.0))
Wind-Spell 'greater_wind_speed'  3 30.0 @(@('wind_flight', 15.0), @('wind_speed_ii', 15.0), @('wind_power_i', 15.0))
# 4/5  wind_flight >= 4  Java 
Wind-Spell 'super_wind_speed'    4 45.0 @(@('wind_speed_iii', 30.0), @('wind_power_ii', 30.0))
Wind-Spell 'wind_god_descent'    5 90.0 @(@('wind_speed_iii', 30.0), @('wind_power_ii', 30.0))

Write-Output ''
Write-Output ('done -> ' + $dest)
Write-Output 'NOTE: wind_god_descent no-cooldown + half mana are Java (not done yet) - see TNWindMechanics.'
