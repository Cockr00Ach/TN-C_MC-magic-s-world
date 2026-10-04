# Requires a creative player and operator permission to invoke /function.
# No world changes, inventory clearing, maturity changes or free survival rewards.
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:bellwool_sheep_spawn_egg 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:stonebarrow_boar_spawn_egg 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:emberback_hog_spawn_egg 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:tideback_newt_spawn_egg 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:froststride_fowl_spawn_egg 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:apiary_toad_spawn_egg 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:starfelt_hare_spawn_egg 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:dusk_lantern_deer_spawn_egg 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:pattern_shell_snail_spawn_egg 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:post_heron_spawn_egg 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:watch_mantis_spawn_egg 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:mirrorwing_moth_spawn_egg 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:forgegill_tapir_spawn_egg 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:mistbelly_otter_spawn_egg 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:ringstone_tortoise_spawn_egg 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:papersail_ray_spawn_egg 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:wirecall_lizard_spawn_egg 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:dewbound_whale_spawn_egg 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:satchelback_runner_spawn_egg 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:bowlhorn_rhino_spawn_egg 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:pageforage_raccoon_spawn_egg 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:patternbuild_beaver_spawn_egg 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:springhoof_strider_spawn_egg 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:pillowlight_marten_spawn_egg 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:pasture_book 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:pasture_staff 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:pasture_cage 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:pasture_scraper 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:mana_bottle 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:refined_mana_bottle 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:pasture_trough 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:habitat_marker 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:pasture_tray 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:egg_rack 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:charging_perch 1
execute if entity @s[type=minecraft:player,gamemode=creative] run give @s tnc:mana_bottle_base 1
execute if entity @s[type=minecraft:player,gamemode=creative] run tellraw @s {"text":"已给24种独立动物的测试蛋和牧养设施。牧养册对动物右键可查喜食与实际状态。","color":"green"}
execute unless entity @s[type=minecraft:player,gamemode=creative] run tellraw @s {"text":"生态测试包仅供创造模式手动测试。","color":"yellow"}
