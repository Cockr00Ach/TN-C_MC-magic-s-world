# Requires a creative player and operator permission to invoke /function.
# No world changes, inventory clearing, maturity changes or free survival rewards.
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:road_bell_seed 2
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:night_gourd_seed 2
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:tide_reed_seed 2
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:homeward_flower_seed 2
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:warning_moss_spore 2
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:hushcap_spore 2
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:rainletter_seed 2
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:mana_root_seed 2
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:verdant_vein_seed 2
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:dawn_disk_seed 2
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:hearth_pepper_seed 2
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:mist_cotton_seed 2
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:stone_fern_seed 2
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:mirror_lotus_seed 2
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:wish_puff_seed 2
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:echo_bean_seed 2
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:ladder_vine_seed 2
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:frost_chime_seed 2
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:salt_ink_seed 2
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:wind_sail_seed 2
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:sleep_clock_seed 2
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:shadow_cut_seed 2
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:paper_tree_seed 2
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:flight_pod_seed 2
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:honey_cluster_seed 2
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:star_dew_seed 2
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:dance_bell_seed 2
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:star_rest_seed 2
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:sky_vine_seed 2
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:plant_sample_clip 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:rain_watering_flask 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:pollination_brush 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:field_tuning_bell 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:field_frame 8
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:salt_basin 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s minecraft:glass_bottle 16
execute if entity @s[type=minecraft:player,gamemode=creative] run tellraw @s {"text":"已给29种种源与栽培工具。普通野外即可种植；先查看物品说明。","color":"green"}
execute unless entity @s[type=minecraft:player,gamemode=creative] run tellraw @s {"text":"生态测试包仅供创造模式手动测试。","color":"yellow"}
