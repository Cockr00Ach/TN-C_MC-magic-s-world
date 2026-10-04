# Requires a creative player and operator permission to invoke /function.
# No world changes, inventory clearing, maturity changes or free survival rewards.
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:magic_forge 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:forge_core 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:forge_exhaust 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:forge_guide 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:mana_generator 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:mana_battery 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:mana_paper_press 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:mana_work_lamp 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:mana_bottle_base 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:mana_bottle 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:field_sound_relay 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:rainproof_seed_box 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:survey_archive_folder 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:sky_vine_survey 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:sky_canopy_rope 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:mana_cable 16
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:forge_firebrick 32
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:star_dew_fruit 8
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:farlight_fruit 8
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:focus_lens 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:warning_lens 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:hearth_oil 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:shell_glue 1
execute if entity @s[type=minecraft:player,gamemode=creative] run tellraw @s {"text":"魔力工坊测试包：24块炉砖＋炉口＋炉核＋排烟口。炉口位于中层正面，右键炉口打开界面。","color":"green"}
execute unless entity @s[type=minecraft:player,gamemode=creative] run tellraw @s {"text":"生态测试包仅供创造模式手动测试。","color":"yellow"}
