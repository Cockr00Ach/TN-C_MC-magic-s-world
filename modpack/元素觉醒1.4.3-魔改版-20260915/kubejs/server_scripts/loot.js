LootJS.modifiers(event => {
    // 避免 Champions 生成额外冠军战利品时重复执行实体掉落规则
    event.disableLootModification("champions:champion_loot")

    event
        .addLootTypeModifier(LootType.CHEST)
        .randomChance(0.8)
        .addWeightedLoot([
            LootEntry.of("kubejs:air_zhiguo"),
            LootEntry.of("kubejs:arcane_zhiguo"),
            LootEntry.of("kubejs:earth_zhiguo"),
            LootEntry.of("kubejs:fire_zhiguo"),
            LootEntry.of("kubejs:frost_zhiguo"),
            LootEntry.of("kubejs:healing_zhiguo"),
            LootEntry.of("kubejs:lightning_zhiguo"),
            LootEntry.of("kubejs:water_zhiguo"),
            LootEntry.of("kubejs:wuli_zhiguo"),
            LootEntry.of("kubejs:youxia_zhiguo")
        ])

    event
        .addLootTypeModifier(LootType.CHEST)
        .randomChance(0.08)
        .addWeightedLoot([1, 2], [
            LootEntry.of("ysjxspells:chujishuijing"),
            LootEntry.of("ysjxspells:fire_crystal"),
            LootEntry.of("ysjxspells:earth_crystal"),
            LootEntry.of("ysjxspells:water_crystal"),
            LootEntry.of("ysjxspells:air_crystal"),
            LootEntry.of("ysjxspells:arcane_crystal"),
            LootEntry.of("ysjxspells:frost_crystal"),
            LootEntry.of("ysjxspells:healing_crystal"),
            LootEntry.of("ysjxspells:lightning_crystal"),
            LootEntry.of("ysjxspells:wuli_crystal"),
            LootEntry.of("ysjxspells:sheshou_crystal")
        ])

    event
        .addLootTypeModifier(LootType.CHEST)
        .randomChance(0.25)
        .addWeightedLoot([1, 3], [
            LootEntry.of("ysjxspells:chujie_caoyao")
        ])

    event
        .addLootTypeModifier(LootType.CHEST)
        .randomChance(0.1)
        .addLoot(Item.of("ysjxspells:zhongjie_caoyao", 1))

    event
        .addLootTypeModifier(LootType.CHEST)
        .randomChance(0.08)
        .addLoot(Item.of("ysjxspells:max_health_food", 1))

    event
        .addLootTypeModifier(LootType.CHEST)
        .randomChance(0.2)
        .addLoot(Item.of("ysjxspells:restoration_potion_1", 1))

    event
        .addLootTypeModifier(LootType.CHEST)
        .randomChance(0.15)
        .addLoot(LootEntry.of("#kubejs:basic_scrolls"))

    event
        .addLootTypeModifier(LootType.CHEST)
        .randomChance(0.08)
        .addLoot(LootEntry.of("#kubejs:intermediate_scrolls"))

    event
        .addLootTypeModifier(LootType.CHEST)
        .randomChance(0.03)
        .addLoot(Item.of("kubejs:baozhu", 1))

    event
        .addLootTableModifier("minecraft:chests/nether_bridge")
        .randomChance(0.25)
        .addLoot(LootEntry.of("#ysjxspells:fruits"))

    event
        .addLootTableModifier("minecraft:chests/ancient_city")
        .randomChance(0.25)
        .addLoot(LootEntry.of("#ysjxspells:fruits"))

    event
        .addLootTableModifier("mansions:mansion_treasure")
        .randomChance(0.3)
        .addLoot(LootEntry.of("#ysjxspells:fruits"))

    event
        .addLootTableModifier("integrated_villages:chests/airship_village/airship_village")
        .randomChance(0.3)
        .addLoot(Item.of("whispering:grip_of_the_flowing_wind", 1))

    const aquamiraeShellHornChests = [
        "aquamirae:chests/frozen_chest",
        "aquamirae:chests/maze_camp_chest",
        "aquamirae:chests/maze_common_chest",
        "aquamirae:chests/ship_1",
        "aquamirae:chests/ship_2"
    ]

    aquamiraeShellHornChests.forEach(lootTableId => {
        event
            .addLootTableModifier(lootTableId)
            .randomChance(0.3)
            .addLoot(Item.of("aquamirae:shell_horn", 1))
    })

    const cataclysmTreasureChests = [
        "cataclysm:chests/desert_treasure",
        "cataclysm:chests/abandoned_treasure"
    ]

    cataclysmTreasureChests.forEach(lootTableId => {
        event
            .addLootTableModifier(lootTableId)
            .randomChance(0.2)
            .addLoot(LootEntry.of("#ysjxspells:fruits"))

        event
            .addLootTableModifier(lootTableId)
            .randomChance(0.1)
            .addLoot(Item.of("ysjxspells:max_health_food", 1))

        event
            .addLootTableModifier(lootTableId)
            .randomChance(0.08)
            .addLoot(Item.of("ysjxspells:restoration_potion_2", 1))

        event
            .addLootTableModifier(lootTableId)
            .randomChance(0.04)
            .addLoot(Item.of("ysjxspells:zhongjishuijing", 1))
    })

    const cataclysmBaozhuChests = [
        "cataclysm:chests/abandoned",
        "cataclysm:chests/abandoned_treasure",
        "cataclysm:chests/acropolis_treasure",
        "cataclysm:chests/amethyst_nest_chest",
        "cataclysm:chests/desert_treasure",
        "cataclysm:chests/frosted_prison_treasure"
    ]

    cataclysmBaozhuChests.forEach(lootTableId => {
        event
            .addLootTableModifier(lootTableId)
            .addLoot(Item.of("kubejs:baozhu", 1))
    })

    // 掠夺战利品箱
    event
        .addLootTableModifier("minecraft:chests/lueduo")
        .randomChance(0.3)
        .addLoot(Item.of("ysjxspells:chujishuijing", 1))

    event
        .addLootTableModifier("minecraft:chests/lueduo")
        .randomChance(0.15)
        .addLoot(LootEntry.of("#ysjxspells:crystals"))

    event
        .addLootTableModifier("minecraft:chests/lueduo")
        .addLoot(Item.of("kubejs:baozhu", 1))

    event
        .addLootTableModifier("minecraft:chests/lueduo2")
        .randomChance(0.15)
        .addLoot(LootEntry.of("#ysjxspells:fruits"))

    event
        .addLootTableModifier("minecraft:chests/lueduo2")
        .randomChance(0.05)
        .addLoot(Item.of("ysjxspells:zuanshibi", 1))

    event
        .addLootTableModifier("minecraft:chests/lueduo2")
        .randomChance(0.3)
        .addLoot(Item.of("dungeon_realm:dungeon_map", 1))

    event
        .addLootTableModifier("minecraft:chests/lueduo2")
        .randomChance(0.5)
        .addLoot(Item.of("ysjxspells:zhongjishuijing", 1))

    event
        .addLootTableModifier("minecraft:chests/lueduo2")
        .addLoot(Item.of("kubejs:baozhu", 1))

    event
        .addLootTableModifier("minecraft:chests/lueduo3")
        .addWeightedLoot([1, 2], [
            LootEntry.of("#ysjxspells:fruits")
        ])

    event
        .addLootTableModifier("minecraft:chests/lueduo3")
        .randomChance(0.05)
        .addLoot(Item.of("ysjxspells:gaojishuijing", 1))

    event
        .addLootTableModifier("minecraft:chests/lueduo3")
        .addWeightedLoot([1, 2], [
            LootEntry.of("dungeon_realm:dungeon_map")
        ])

    event
        .addLootTableModifier("minecraft:chests/lueduo3")
        .addWeightedLoot([1, 2], [
            LootEntry.of("ysjxspells:zhongjishuijing")
        ])

    event
        .addEntityLootModifier("eeeabsmobs:immortal")
        .addLoot(Item.of("whispering:immortal_amulet", 1))

    event
        .addEntityLootModifier("block_factorys_bosses:yeti")
        .addLoot(Item.of("endrem:cold_eye", 1))

    event
        .addEntityLootModifier("block_factorys_bosses:sandworm")
        .addLoot(Item.of("endrem:old_eye", 1))

    event
        .addEntityLootModifier("illageandspillage:magispeller")
        .addLoot(Item.of("endrem:evil_eye", 1))

    event
        .addEntityLootModifier("illageandspillage:spiritcaller")
        .addLoot(Item.of("illageandspillage:spellbound_book", 1))

    event
        .addEntityLootModifier("bosses_of_mass_destruction:gauntlet")
        .addLoot(Item.of("endrem:nether_eye", 1))

    event
        .addEntityLootModifier("mebahelcreaturesdraugr:draugr_overlord")
        .addLoot(Item.of("endrem:corrupted_eye", 1))

    event
        .addEntityLootModifier("soulsweapons:draugr_boss")
        .addLoot(Item.of("endrem:black_eye", 1))

    event
        .addEntityLootModifier("eeeabsmobs:immortal")
        .addLoot(Item.of("endrem:undead_eye", 1))

    event
        .addEntityLootModifier("jerotesvillage:purple_sand_hag")
        .addLoot(Item.of("endrem:witch_eye", 1))

    event
        .addEntityLootModifier("abyssal_corrupter:abyssal_corrupter")
        .addLoot(Item.of("endrem:cursed_eye", 1))

    event
        .addEntityLootModifier("eeeabsmobs:corpse_warlock")
        .addLoot(Item.of("endrem:lost_eye", 1))

    event
        .addEntityLootModifier("block_factorys_bosses:yeti")
        .addLoot(Item.of("whispering:shuang_han_zhan_quan", 1))

    event
        .addEntityLootModifier("block_factorys_bosses:infernal_dragon")
        .addLoot(Item.of("whispering:long_zhi_xin", 1))

    event
        .addEntityLootModifier("legendary_monsters:frostbitten_golem")
        .randomChance(0.3)
        .addLoot(Item.of("whispering:shuang_zhu_zhi_jie", 1))

    event
        .addEntityLootModifier("legendary_monsters:lava_eater")
        .randomChance(0.3)
        .addLoot(Item.of("whispering:zhigaoyanzhong", 1))

    event
        .addEntityLootModifier("cataclysm:scylla")
        .randomChance(0.1)
        .addLoot(Item.of("whispering:wmwzdkz", 1))

    event
        .addEntityLootModifier("minecraft:enderman")
        .randomChance(0.05)
        .addLoot(Item.of("whispering:arcane_ring", 1))

    event
        .addEntityLootModifier("royalvariations:royal_enderman")
        .randomChance(0.3)
        .addLoot(Item.of("whispering:yaohui", 1))

    event
        .addEntityLootModifier("mowziesmobs:frostmaw")
        .randomChance(0.3)
        .addLoot(Item.of("loot_n_explore:frostmonarch_upgrade_smithing_template", 1))

    event
        .addEntityLootModifier("alexscaves:gum_worm")
        .randomChance(0.2)
        .addLoot(Item.of("whispering:csdjl", 1))

    event
        .addEntityLootModifier("legendary_monsters:the_obliterator")
        .randomChance(0.5)
        .addLoot(Item.of("legendary_monsters:eye_of_annihilation", 1))

    event
        .addEntityLootModifier("legendary_monsters:posessed_paladin")
        .randomChance(0.5)
        .addLoot(Item.of("legendary_monsters:eye_of_ghost", 1))


    event
        .addEntityLootModifier("cataclysm:the_leviathan")
        .addLoot(Item.of("cataclysm:abyss_eye", 1))

    event
        .addEntityLootModifier("cataclysm:ancient_remnant")
        .addLoot(Item.of("cataclysm:desert_eye", 1))

    event
        .addEntityLootModifier("bosses_of_mass_destruction:void_blossom")
        .addLoot(Item.of("whispering:xingkui", 1))

    event
        .addEntityLootModifier("soulsweapons:draugr_boss")
        .addLoot(Item.of("whispering:qixue", 1))

    event
        .addEntityLootModifier("eeeabsmobs:corpse_warlock")
        .addLoot(Item.of("whispering:xingye", 1))

    event
        .addEntityLootModifier("royalvariations:royal_skeleton")
        .randomChance(0.05)
        .addLoot(Item.of("whispering:maodie", 1))

    event
        .addEntityLootModifier("mebahelcreaturesdraugr:draugr")
        .randomChance(0.03)
        .addLoot(Item.of("whispering:soul_grasp_ring", 1))

    event
        .addEntityLootModifier("mebahelcreaturesdraugr:draugr_archer")
        .randomChance(0.03)
        .addLoot(Item.of("whispering:soul_grasp_ring", 1))

    event
        .addEntityLootModifier("mebahelcreaturesdraugr:draugr_wight")
        .randomChance(0.03)
        .addLoot(Item.of("whispering:soul_grasp_ring", 1))

    event
        .addEntityLootModifier("mebahelcreaturesdraugr:draugr_scourge")
        .randomChance(0.03)
        .addLoot(Item.of("whispering:soul_grasp_ring", 1))

    event
        .addEntityLootModifier("aquamirae:captain_cornelia")
        .addLoot(Item.of("aquamirae:rune_of_the_storm", 1))

    event
        .addEntityLootModifier("cataclysm:maledictus")
        .addLoot(Item.of("cataclysm:cursed_eye", 1))

    event
        .addEntityLootModifier("cataclysm:the_harbinger")
        .addLoot(Item.of("cataclysm:mech_eye", 1))

    event
        .addEntityLootModifier("cataclysm:netherite_monstrosity")
        .addLoot(Item.of("cataclysm:monstrous_eye", 1))

    event
        .addEntityLootModifier("cataclysm:ender_guardian")
        .addLoot(Item.of("cataclysm:void_eye", 1))

    event
        .addEntityLootModifier("cataclysm:ignis")
        .addLoot(Item.of("cataclysm:flame_eye", 1))

    event
        .addEntityLootModifier("cataclysm:koboleton")
        .randomChance(0.1)
        .addLoot(Item.of("cataclysm:necklace_of_the_desert", 1))

    event
        .addEntityLootModifier("cataclysm:kobolediator")
        .addLoot(Item.of("cataclysm:necklace_of_the_desert", 1))

    const torchesEyeDropEntities = [
        "torchesbecomesunlight:war_phantom",
        "torchesbecomesunlight:fallen_snowpriest",
        "torchesbecomesunlight:shattered_champion"
    ]

    torchesEyeDropEntities.forEach(entityId => {
        event
            .addEntityLootModifier(entityId)
            .randomChance(0.1)
            .addLoot(Item.of("torchesbecomesunlight:sankta_statue_eye", 1))

        event
            .addEntityLootModifier(entityId)
            .randomChance(0.1)
            .addLoot(Item.of("torchesbecomesunlight:rhodes_island_eye", 1))
    })

    event
        .addEntityLootModifier("minecraft:wither")
        .addLoot(Item.of("kubejs:diaolingji", 1))

    event
        .addEntityLootModifier("minecraft:ender_dragon")
        .addLoot(Item.of("minecraft:dragon_egg", 1))

    event
        .addEntityLootModifier("minecraft:wither_skeleton")
        .addWeightedLoot([1, 3], [
            LootEntry.of("spellbladenext:thread")
        ])

    event
        .addEntityLootModifier("legendary_monsters:posessed_paladin")
        .addLoot(Item.of("torchesbecomesunlight:light", 1))

    event
        .addEntityLootModifier("legendary_monsters:the_obliterator")
        .addLoot(Item.of("torchesbecomesunlight:time", 1))

    event
        .addEntityLootModifier("torchesbecomesunlight:red")
        .addLoot(Item.of("torchesbecomesunlight:fire_steel", 1))

    event
        .addEntityLootModifier("minecraft:evoker")
        .addLoot(Item.of("endrem:magical_eye", 1))

    const dungeonBadgeEntities = [
        "torchesbecomesunlight:pursuer",
        "blackgoldalliance:the_black_gold_marshal",
        "mutantmore:mutant_wither_skeleton",
        "torchesbecomesunlight:patriot",
        "eeeabsmobs:nameless_guardian",
        "monsterexpansion:ignathos",
        "hadean_breathe:hadean_smasher"
    ]

    dungeonBadgeEntities.forEach(entityId => {
        event
            .addEntityLootModifier(entityId)
            .randomChance(0.03)
            .addLoot(LootEntry.of("#kubejs:dungeon_badges"))

        event
            .addEntityLootModifier(entityId)
            .addLoot(LootEntry.of("#ysjxspells:fruits"))

        event
            .addEntityLootModifier(entityId)
            .addLoot(Item.of("ysjxspells:yuansushuijing", 2))

        event
            .addEntityLootModifier(entityId)
            .addLoot(LootEntry.of("#ysjxspells:gems"))

        event
            .addEntityLootModifier(entityId)
            .randomChance(0.1)
            .addLoot(Item.of("ysjxspells:fashuhexin", 1))
    })

})
