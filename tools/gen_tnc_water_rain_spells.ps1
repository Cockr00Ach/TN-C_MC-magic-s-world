# WATER chain 4 (rain) - 5 spells, built on the engine's CLOUD target.
#
# CLOUD fields (javap: Spell$Release$Target$Cloud):
#   volume (AreaImpact, has radius) | time_to_live_seconds | impact_tick_interval
#   delay_ticks | placement | presence_sound | client_data{particles,model} | spawn{sound,particles}
# The impact list re-applies every impact_tick_interval ticks while the cloud lives,
# which is exactly "keep raining on this area": verified against archers' entangling_roots
# (radius 3.5, impact_tick_interval 15, a STATUS_EFFECT impact).
#
# Names from the doc: yu-di / chu-yu / yu-luo / bao-yu-luo / tian-hong.
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
    Write-Output ("  {0,-20} {1,6:N0} B" -f "$name.json", (Get-Item $path).Length)
}

$castFx  = '{ "particle_id": "splash", "shape": "CIRCLE", "origin": "FEET", "count": 10.0, "min_speed": 0.1, "max_speed": 0.4 }'
$rainFx  = '{ "particle_id": "falling_water", "shape": "SPHERE", "origin": "CENTER", "count": 12.0, "min_speed": 0.0, "max_speed": 0.2 }'
$spawnFx = '{ "particle_id": "splash", "shape": "SPHERE", "origin": "CENTER", "count": 25.0, "min_speed": 0.2, "max_speed": 0.8 }'

function Rain-Spell($name, $tier, $radius, $radiusGrowth, $life, $interval, $coef, $cooldown) {
    Write-Json $name @"
{
  "school": "WATER",
  "group": "water_rain",
  "range": 24.0,
  "learn": { "tier": $tier },
  "cast": {
    "duration": 0.8,
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
          "extra_radius": { "power_coefficient": $radiusGrowth },
          "area": { "distance_dropoff": "SQUARED" }
        },
        "time_to_live_seconds": $life,
        "impact_tick_interval": $interval,
        "delay_ticks": 10,
        "presence_sound": { "id": "weather.rain.above", "volume": 0.4 },
        "client_data": { "particles": [ $rainFx ] },
        "spawn": { "particles": [ $spawnFx ], "sound": { "id": "weather.rain.above", "volume": 0.8 } }
      }
    },
    "animation": "spell_engine:one_handed_area_release",
    "sound": { "id": "entity.player.splash", "volume": 1.0 }
  },
  "impact": [
    {
      "action": { "type": "DAMAGE", "damage": { "spell_power_coefficient": $coef, "knockback": 0.0 } },
      "particles": [ $rainFx ]
    }
  ],
  "cost": { "exhaust": 0.0, "cooldown_duration": $cooldown }
}
"@
}

#               id            tier radius growth life interval coef  cd
Rain-Spell 'raindrop'    1 3.0 0.1 15.0 20 0.35 4.0
Rain-Spell 'first_rain'  2 4.0 0.2 20.0 20 0.45 6.0
Rain-Spell 'rainfall'    3 5.0 0.3 25.0 18 0.55 9.0
Rain-Spell 'downpour'    4 6.0 0.4 30.0 16 0.65 13.0
Rain-Spell 'flood_of_heaven' 5 7.5 0.5 35.0 14 0.80 18.0

Write-Output ''
Write-Output ('done -> ' + $dest)