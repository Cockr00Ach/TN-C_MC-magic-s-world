ServerEvents.recipes(event => {
  event.remove({ output: 'dungeon_realm:map_device' })

  event.shapeless(
    Item.of('runes:lightning_stone', 2),
    [
      'minecraft:cobblestone',
      'minecraft:copper_ingot'
    ]
  ).id('kubejs:lightning_stone')

  event.shaped(
    'kubejs:shelong_zhaohuanqi',
    [
      'ABC'
    ],
    {
      A: 'jerotesvillage:sediment_lord_robe_pieces',
      B: 'minecraft:ender_eye',
      C: 'jerotesvillage:purple_sand_hag_hair'
    }
  ).id('kubejs:shelong_zhaohuanqi')

  event.shaped(
    'kubejs:abyssal_teleport_crystal',
    [
      'AAA',
      'ABA',
      'AAA'
    ],
    {
      A: 'minecraft:kelp',
      B: 'minecraft:ender_eye'
    }
  ).id('kubejs:abyssal_teleport_crystal')

  event.shaped(
    'cataclysm:abyssal_sacrifice',
    [
      'AAA',
      'ABA',
      'AAA'
    ],
    {
      A: 'minecraft:prismarine_shard',
      B: 'endrem:guardian_eye'
    }
  ).id('kubejs:cataclysm_abyssal_sacrifice')

  event.shaped(
    'ysjxspells:zhongjie_caoyao',
    [
      'AAA',
      'ABA',
      'AAA'
    ],
    {
      A: 'ysjxspells:chujie_caoyao',
      B: 'elemental_wizards_rpg:elemental_essence'
    }
  ).id('kubejs:zhongjie_caoyao')

  event.shaped(
    'dungeon_realm:map_device',
    [
      'BCB',
      'DDD'
    ],
    {
      B: 'ysjxspells:zhongjishuijing',
      C: 'illageandspillage:spellbound_book',
      D: 'minecraft:obsidian'
    }
  ).id('kubejs:dungeon_realm_map_device')

  event.shaped(
    'kubejs:tubian_zhaohuan_zhuangzhi',
    [
      ' A ',
      'B B',
      'CCC'
    ],
    {
      A: 'alexscaves:sulfur_dust',
      B: 'alexscaves:polymer_plate',
      C: 'alexscaves:charred_remnant'
    }
  ).id('kubejs:tubian_zhaohuan_zhuangzhi')

  event.shaped(
    'kubejs:shenghua_zhuangzhi',
    [
      'AAA',
      'ABA',
      'AAA'
    ],
    {
      A: 'alexscaves:charred_remnant',
      B: 'alexscaves:fissile_core'
    }
  ).id('kubejs:shenghua_zhuangzhi')

  event.shaped(
    'ysjxspells:scroll',
    [
      'ABA'
    ],
    {
      A: 'minecraft:paper',
      B: 'minecraft:stick'
    }
  ).id('kubejs:ysjxspells_scroll')

  event.shaped(
    'rune_pocket:rune_pocket',
    [
      'LLL',
      'LEL',
      'LLL'
    ],
    {
      L: 'minecraft:leather',
      E: 'elemental_wizards_rpg:elemental_essence'
    }
  ).id('kubejs:rune_pocket')

  event.shaped(
    'rune_pocket:arrow_quiver',
    [
      'LAL'
    ],
    {
      L: 'minecraft:leather',
      A: 'minecraft:arrow'
    }
  ).id('kubejs:arrow_quiver')

  event.shaped(
    'spellanvil:anvil',
    [
      ' A ',
      'ABA',
      'CCC'
    ],
    {
      A: 'minecraft:lapis_lazuli',
      B: 'minecraft:book',
      C: 'minecraft:obsidian'
    }
  ).id('kubejs:spellanvil_anvil')

  event.shaped(
    'ysjx_weapons:lightning_robe_helmet',
    [
      '  W',
      ' W ',
      'WLW'
    ],
    {
      W: '#minecraft:wool',
      L: 'minecraft:lightning_rod'
    }
  ).id('kubejs:lightning_robe_helmet')

  event.shaped(
    'ysjx_weapons:lightning_robe_chestplate',
    [
      'L L',
      'WLW',
      'WWW'
    ],
    {
      W: '#minecraft:wool',
      L: 'minecraft:lightning_rod'
    }
  ).id('kubejs:lightning_robe_chestplate')

  event.shaped(
    'ysjx_weapons:lightning_robe_leggings',
    [
      'LLL',
      'W W',
      'W W'
    ],
    {
      W: '#minecraft:wool',
      L: 'minecraft:lightning_rod'
    }
  ).id('kubejs:lightning_robe_leggings')

  event.shaped(
    'ysjx_weapons:lightning_robe_boots',
    [
      'L L',
      'W W'
    ],
    {
      W: '#minecraft:wool',
      L: 'minecraft:lightning_rod'
    }
  ).id('kubejs:lightning_robe_boots')

  event.smithing(
    'ysjx_weapons:netherite_lightning_robe_helmet',
    'minecraft:netherite_upgrade_smithing_template',
    'ysjx_weapons:lightning_robe_helmet',
    'ysjxspells:zhongjishuijing'
  ).id('kubejs:netherite_lightning_robe_helmet')

  event.smithing(
    'ysjx_weapons:netherite_lightning_robe_chestplate',
    'minecraft:netherite_upgrade_smithing_template',
    'ysjx_weapons:lightning_robe_chestplate',
    'ysjxspells:zhongjishuijing'
  ).id('kubejs:netherite_lightning_robe_chestplate')

  event.smithing(
    'ysjx_weapons:netherite_lightning_robe_leggings',
    'minecraft:netherite_upgrade_smithing_template',
    'ysjx_weapons:lightning_robe_leggings',
    'ysjxspells:zhongjishuijing'
  ).id('kubejs:netherite_lightning_robe_leggings')

  event.smithing(
    'ysjx_weapons:netherite_lightning_robe_boots',
    'minecraft:netherite_upgrade_smithing_template',
    'ysjx_weapons:lightning_robe_boots',
    'ysjxspells:zhongjishuijing'
  ).id('kubejs:netherite_lightning_robe_boots')

  // 水流至尊法师套
  event.smithing(
    'ysjx_weapons:water_mage_head',
    'ysjxspells:fashuhexin',
    'elemental_wizards_rpg:netherite_kelp_head',
    'ysjxspells:water_baoshi'
  ).id('kubejs:water_mage_head')

  event.smithing(
    'ysjx_weapons:water_mage_chest',
    'ysjxspells:fashuhexin',
    'elemental_wizards_rpg:netherite_kelp_chest',
    'ysjxspells:water_baoshi'
  ).id('kubejs:water_mage_chest')

  event.smithing(
    'ysjx_weapons:water_mage_legs',
    'ysjxspells:fashuhexin',
    'elemental_wizards_rpg:netherite_kelp_legs',
    'ysjxspells:water_baoshi'
  ).id('kubejs:water_mage_legs')

  event.smithing(
    'ysjx_weapons:water_mage_feet',
    'ysjxspells:fashuhexin',
    'elemental_wizards_rpg:netherite_kelp_feet',
    'ysjxspells:water_baoshi'
  ).id('kubejs:water_mage_feet')

  // 空气至尊法师套
  event.smithing(
    'ysjx_weapons:air_mage_head',
    'ysjxspells:fashuhexin',
    'elemental_wizards_rpg:netherite_wind_head',
    'ysjxspells:air_baoshi'
  ).id('kubejs:air_mage_head')

  event.smithing(
    'ysjx_weapons:air_mage_chest',
    'ysjxspells:fashuhexin',
    'elemental_wizards_rpg:netherite_wind_chest',
    'ysjxspells:air_baoshi'
  ).id('kubejs:air_mage_chest')

  event.smithing(
    'ysjx_weapons:air_mage_legs',
    'ysjxspells:fashuhexin',
    'elemental_wizards_rpg:netherite_wind_legs',
    'ysjxspells:air_baoshi'
  ).id('kubejs:air_mage_legs')

  event.smithing(
    'ysjx_weapons:air_mage_feet',
    'ysjxspells:fashuhexin',
    'elemental_wizards_rpg:netherite_wind_feet',
    'ysjxspells:air_baoshi'
  ).id('kubejs:air_mage_feet')

  // 大地至尊法师套
  event.smithing(
    'ysjx_weapons:earth_mage_head',
    'ysjxspells:fashuhexin',
    'elemental_wizards_rpg:netherite_dripstone_head',
    'ysjxspells:earth_baoshi'
  ).id('kubejs:earth_mage_head')

  event.smithing(
    'ysjx_weapons:earth_mage_chest',
    'ysjxspells:fashuhexin',
    'elemental_wizards_rpg:netherite_dripstone_chest',
    'ysjxspells:earth_baoshi'
  ).id('kubejs:earth_mage_chest')

  event.smithing(
    'ysjx_weapons:earth_mage_legs',
    'ysjxspells:fashuhexin',
    'elemental_wizards_rpg:netherite_dripstone_legs',
    'ysjxspells:earth_baoshi'
  ).id('kubejs:earth_mage_legs')

  event.smithing(
    'ysjx_weapons:earth_mage_feet',
    'ysjxspells:fashuhexin',
    'elemental_wizards_rpg:netherite_dripstone_feet',
    'ysjxspells:earth_baoshi'
  ).id('kubejs:earth_mage_feet')

  // 神圣至尊法师套
  event.smithing(
    'ysjx_weapons:healing_head',
    'ysjxspells:fashuhexin',
    'paladins:netherite_prior_robe_head',
    'ysjxspells:bealing_baoshi'
  ).id('kubejs:healing_head')

  event.smithing(
    'ysjx_weapons:healing_chest',
    'ysjxspells:fashuhexin',
    'paladins:netherite_prior_robe_chest',
    'ysjxspells:bealing_baoshi'
  ).id('kubejs:healing_chest')

  event.smithing(
    'ysjx_weapons:healing_legs',
    'ysjxspells:fashuhexin',
    'paladins:netherite_prior_robe_legs',
    'ysjxspells:bealing_baoshi'
  ).id('kubejs:healing_legs')

  event.smithing(
    'ysjx_weapons:healing_feet',
    'ysjxspells:fashuhexin',
    'paladins:netherite_prior_robe_feet',
    'ysjxspells:bealing_baoshi'
  ).id('kubejs:healing_feet')

  // 皇家骑士团套
  event.smithing(
    'ysjx_weapons:royal_knights_head',
    'ysjxspells:fashuhexin',
    'rogues:netherite_berserker_armor_head',
    'ysjxspells:wuli_baoshi'
  ).id('kubejs:royal_knights_head')

  event.smithing(
    'ysjx_weapons:royal_knights_chest',
    'ysjxspells:fashuhexin',
    'rogues:netherite_berserker_armor_chest',
    'ysjxspells:wuli_baoshi'
  ).id('kubejs:royal_knights_chest')

  event.smithing(
    'ysjx_weapons:royal_knights_legs',
    'ysjxspells:fashuhexin',
    'rogues:netherite_berserker_armor_legs',
    'ysjxspells:wuli_baoshi'
  ).id('kubejs:royal_knights_legs')

  event.smithing(
    'ysjx_weapons:royal_knights_feet',
    'ysjxspells:fashuhexin',
    'rogues:netherite_berserker_armor_feet',
    'ysjxspells:wuli_baoshi'
  ).id('kubejs:royal_knights_feet')

  // 猎星套
  event.smithing(
    'ysjx_weapons:liexing_head',
    'ysjxspells:fashuhexin',
    'archers:netherite_ranger_armor_head',
    'ysjxspells:sheshou_baoshi'
  ).id('kubejs:liexing_head')

  event.smithing(
    'ysjx_weapons:liexing_chest',
    'ysjxspells:fashuhexin',
    'archers:netherite_ranger_armor_chest',
    'ysjxspells:sheshou_baoshi'
  ).id('kubejs:liexing_chest')

  event.smithing(
    'ysjx_weapons:liexing_legs',
    'ysjxspells:fashuhexin',
    'archers:netherite_ranger_armor_legs',
    'ysjxspells:sheshou_baoshi'
  ).id('kubejs:liexing_legs')

  event.smithing(
    'ysjx_weapons:liexing_feet',
    'ysjxspells:fashuhexin',
    'archers:netherite_ranger_armor_feet',
    'ysjxspells:sheshou_baoshi'
  ).id('kubejs:liexing_feet')

  // 奥术至尊法师套
  event.smithing(
    'ysjx_weapons:diamond_mage_head',
    'ysjxspells:fashuhexin',
    'wizards:netherite_arcane_robe_head',
    'ysjxspells:arcane_baoshi'
  ).id('kubejs:diamond_mage_head')

  event.smithing(
    'ysjx_weapons:diamond_mage_chest',
    'ysjxspells:fashuhexin',
    'wizards:netherite_arcane_robe_chest',
    'ysjxspells:arcane_baoshi'
  ).id('kubejs:diamond_mage_chest')

  event.smithing(
    'ysjx_weapons:diamond_mage_legs',
    'ysjxspells:fashuhexin',
    'wizards:netherite_arcane_robe_legs',
    'ysjxspells:arcane_baoshi'
  ).id('kubejs:diamond_mage_legs')

  event.smithing(
    'ysjx_weapons:diamond_mage_feet',
    'ysjxspells:fashuhexin',
    'wizards:netherite_arcane_robe_feet',
    'ysjxspells:arcane_baoshi'
  ).id('kubejs:diamond_mage_feet')

  // 火焰至尊法师套
  event.smithing(
    'ysjx_weapons:fire_mage_head',
    'ysjxspells:fashuhexin',
    'wizards:netherite_fire_robe_head',
    'ysjxspells:fire_baoshi'
  ).id('kubejs:fire_mage_head')

  event.smithing(
    'ysjx_weapons:fire_mage_chest',
    'ysjxspells:fashuhexin',
    'wizards:netherite_fire_robe_chest',
    'ysjxspells:fire_baoshi'
  ).id('kubejs:fire_mage_chest')

  event.smithing(
    'ysjx_weapons:fire_mage_legs',
    'ysjxspells:fashuhexin',
    'wizards:netherite_fire_robe_legs',
    'ysjxspells:fire_baoshi'
  ).id('kubejs:fire_mage_legs')

  event.smithing(
    'ysjx_weapons:fire_mage_feet',
    'ysjxspells:fashuhexin',
    'wizards:netherite_fire_robe_feet',
    'ysjxspells:fire_baoshi'
  ).id('kubejs:fire_mage_feet')

  // 冰霜至尊法师套
  event.smithing(
    'ysjx_weapons:frost_mage_head',
    'ysjxspells:fashuhexin',
    'wizards:netherite_frost_robe_head',
    'ysjxspells:frost_baoshi'
  ).id('kubejs:frost_mage_head')

  event.smithing(
    'ysjx_weapons:frost_mage_chest',
    'ysjxspells:fashuhexin',
    'wizards:netherite_frost_robe_chest',
    'ysjxspells:frost_baoshi'
  ).id('kubejs:frost_mage_chest')

  event.smithing(
    'ysjx_weapons:frost_mage_legs',
    'ysjxspells:fashuhexin',
    'wizards:netherite_frost_robe_legs',
    'ysjxspells:frost_baoshi'
  ).id('kubejs:frost_mage_legs')

  event.smithing(
    'ysjx_weapons:frost_mage_feet',
    'ysjxspells:fashuhexin',
    'wizards:netherite_frost_robe_feet',
    'ysjxspells:frost_baoshi'
  ).id('kubejs:frost_mage_feet')

  // 闪电至尊法师套
  event.smithing(
    'ysjx_weapons:lightning_mage_head',
    'ysjxspells:fashuhexin',
    'ysjx_weapons:netherite_lightning_robe_helmet',
    'ysjxspells:lightning_baoshi'
  ).id('kubejs:lightning_mage_head')

  event.smithing(
    'ysjx_weapons:lightning_mage_chest',
    'ysjxspells:fashuhexin',
    'ysjx_weapons:netherite_lightning_robe_chestplate',
    'ysjxspells:lightning_baoshi'
  ).id('kubejs:lightning_mage_chest')

  event.smithing(
    'ysjx_weapons:lightning_mage_legs',
    'ysjxspells:fashuhexin',
    'ysjx_weapons:netherite_lightning_robe_leggings',
    'ysjxspells:lightning_baoshi'
  ).id('kubejs:lightning_mage_legs')

  event.smithing(
    'ysjx_weapons:lightning_mage_feet',
    'ysjxspells:fashuhexin',
    'ysjx_weapons:netherite_lightning_robe_boots',
    'ysjxspells:lightning_baoshi'
  ).id('kubejs:lightning_mage_feet')

  // 猎魔套
  event.smithing(
    'ysjx_weapons:liemo_head',
    'ysjxspells:fashuhexin',
    'rogues:netherite_assassin_armor_head',
    'ysjxspells:wuli_baoshi'
  ).id('kubejs:liemo_head')

  event.smithing(
    'ysjx_weapons:liemo_chest',
    'ysjxspells:fashuhexin',
    'rogues:netherite_assassin_armor_chest',
    'ysjxspells:wuli_baoshi'
  ).id('kubejs:liemo_chest')

  event.smithing(
    'ysjx_weapons:liemo_legs',
    'ysjxspells:fashuhexin',
    'rogues:netherite_assassin_armor_legs',
    'ysjxspells:wuli_baoshi'
  ).id('kubejs:liemo_legs')

  event.smithing(
    'ysjx_weapons:liemo_feet',
    'ysjxspells:fashuhexin',
    'rogues:netherite_assassin_armor_feet',
    'ysjxspells:wuli_baoshi'
  ).id('kubejs:liemo_feet')

  event.shaped(
    'ysjx_weapons:lightning_staff',
    [
      'LLL',
      ' A ',
      'W  '
    ],
    {
      W: 'minecraft:gold_ingot',
      L: 'minecraft:lightning_rod',
      A: 'minecraft:stick'
    }
  ).id('kubejs:lightning_staff')

  event.smithing(
    'ysjx_weapons:netherite_lightning_staff',
    'minecraft:netherite_upgrade_smithing_template',
    'ysjx_weapons:lightning_staff',
    'ysjxspells:zhongjishuijing'
  ).id('kubejs:netherite_lightning_staff')

    event.shaped(
    'ysjx_weapons:original',
    [
      'LLL',
      'LAL',
      'LLL'
    ],
    {
      L: 'elemental_wizards_rpg:elemental_essence',
      A: 'minecraft:book'
    }
  ).id('kubejs:original')

  // 原初武器的元素锻造分支
  event.smithing(
    'ysjx_weapons:air',
    'ysjxspells:fashuhexin',
    'ysjx_weapons:original',
    'ysjxspells:air_baoshi'
  ).id('kubejs:air')

  event.smithing(
    'ysjx_weapons:arcane',
    'ysjxspells:fashuhexin',
    'ysjx_weapons:original',
    'ysjxspells:arcane_baoshi'
  ).id('kubejs:arcane')

  event.smithing(
    'ysjx_weapons:earth',
    'ysjxspells:fashuhexin',
    'ysjx_weapons:original',
    'ysjxspells:earth_baoshi'
  ).id('kubejs:earth')

  event.smithing(
    'ysjx_weapons:fire',
    'ysjxspells:fashuhexin',
    'ysjx_weapons:original',
    'ysjxspells:fire_baoshi'
  ).id('kubejs:fire')

  event.smithing(
    'ysjx_weapons:frost',
    'ysjxspells:fashuhexin',
    'ysjx_weapons:original',
    'ysjxspells:frost_baoshi'
  ).id('kubejs:frost')

  event.smithing(
    'ysjx_weapons:healing',
    'ysjxspells:fashuhexin',
    'ysjx_weapons:original',
    'ysjxspells:bealing_baoshi'
  ).id('kubejs:healing')

  event.smithing(
    'ysjx_weapons:lighting',
    'ysjxspells:fashuhexin',
    'ysjx_weapons:original',
    'ysjxspells:lightning_baoshi'
  ).id('kubejs:lighting')

  event.smithing(
    'ysjx_weapons:water',
    'ysjxspells:fashuhexin',
    'ysjx_weapons:original',
    'ysjxspells:water_baoshi'
  ).id('kubejs:water')

  // 阿努比斯套
  event.smithing(
    'armoroftheages:anubis_armor_head',
    'ysjxspells:fashuhexin',
    'berserker_rpg:netherite_northling_head',
    'ysjxspells:wuli_baoshi'
  ).id('kubejs:anubis_armor_head')

  event.smithing(
    'armoroftheages:anubis_armor_chest',
    'ysjxspells:fashuhexin',
    'berserker_rpg:netherite_northling_chest',
    'ysjxspells:wuli_baoshi'
  ).id('kubejs:anubis_armor_chest')

  event.smithing(
    'armoroftheages:anubis_armor_legs',
    'ysjxspells:fashuhexin',
    'berserker_rpg:netherite_northling_legs',
    'ysjxspells:wuli_baoshi'
  ).id('kubejs:anubis_armor_legs')

  event.smithing(
    'armoroftheages:anubis_armor_feet',
    'ysjxspells:fashuhexin',
    'berserker_rpg:netherite_northling_feet',
    'ysjxspells:wuli_baoshi'
  ).id('kubejs:anubis_armor_feet')

  // 神圣铠甲套
  event.smithing(
    'armoroftheages:holy_armor_head',
    'ysjxspells:fashuhexin',
    'paladins:netherite_crusader_armor_head',
    'ysjxspells:wuli_baoshi'
  ).id('kubejs:holy_armor_head')

  event.smithing(
    'armoroftheages:holy_armor_chest',
    'ysjxspells:fashuhexin',
    'paladins:netherite_crusader_armor_chest',
    'ysjxspells:wuli_baoshi'
  ).id('kubejs:holy_armor_chest')

  event.smithing(
    'armoroftheages:holy_armor_legs',
    'ysjxspells:fashuhexin',
    'paladins:netherite_crusader_armor_legs',
    'ysjxspells:wuli_baoshi'
  ).id('kubejs:holy_armor_legs')

  event.smithing(
    'armoroftheages:holy_armor_feet',
    'ysjxspells:fashuhexin',
    'paladins:netherite_crusader_armor_feet',
    'ysjxspells:wuli_baoshi'
  ).id('kubejs:holy_armor_feet')

  // 唤魔尖牙初阶卷轴
  event.smithing(
    'kubejs:huanmo1_arcane_scroll',
    'ysjxspells:scroll',
    'minecraft:totem_of_undying',
    'ysjxspells:arcane_crystal'
  ).id('kubejs:huanmo1_arcane_scroll')

  event.smithing(
    'kubejs:huanmo1_fire_scroll',
    'ysjxspells:scroll',
    'minecraft:totem_of_undying',
    'ysjxspells:fire_crystal'
  ).id('kubejs:huanmo1_fire_scroll')

  event.smithing(
    'kubejs:huanmo1_frost_scroll',
    'ysjxspells:scroll',
    'minecraft:totem_of_undying',
    'ysjxspells:frost_crystal'
  ).id('kubejs:huanmo1_frost_scroll')

  event.smithing(
    'kubejs:huanmo1_wuli_scroll',
    'ysjxspells:scroll',
    'minecraft:totem_of_undying',
    'ysjxspells:wuli_crystal'
  ).id('kubejs:huanmo1_wuli_scroll')

  event.smithing(
    'kubejs:huanmo1_healing_scroll',
    'ysjxspells:scroll',
    'minecraft:totem_of_undying',
    'ysjxspells:healing_crystal'
  ).id('kubejs:huanmo1_healing_scroll')

  event.smithing(
    'kubejs:huanmo1_water_scroll',
    'ysjxspells:scroll',
    'minecraft:totem_of_undying',
    'ysjxspells:water_crystal'
  ).id('kubejs:huanmo1_water_scroll')

  event.smithing(
    'kubejs:huanmo1_earth_scroll',
    'ysjxspells:scroll',
    'minecraft:totem_of_undying',
    'ysjxspells:earth_crystal'
  ).id('kubejs:huanmo1_earth_scroll')

  event.smithing(
    'kubejs:huanmo1_air_scroll',
    'ysjxspells:scroll',
    'minecraft:totem_of_undying',
    'ysjxspells:air_crystal'
  ).id('kubejs:huanmo1_air_scroll')

  event.smithing(
    'kubejs:huanmo1_lightning_scroll',
    'ysjxspells:scroll',
    'minecraft:totem_of_undying',
    'ysjxspells:lightning_crystal'
  ).id('kubejs:huanmo1_lightning_scroll')

  event.smithing(
    'kubejs:huanmo1_ranged_scroll',
    'ysjxspells:scroll',
    'minecraft:totem_of_undying',
    'ysjxspells:sheshou_crystal'
  ).id('kubejs:huanmo1_ranged_scroll')

  // 冰刺初阶卷轴
  event.smithing(
    'kubejs:bingci1_arcane_scroll',
    'ysjxspells:scroll',
    'legendary_monsters:frozen_rune',
    'ysjxspells:arcane_crystal'
  ).id('kubejs:bingci1_arcane_scroll')

  event.smithing(
    'kubejs:bingci1_fire_scroll',
    'ysjxspells:scroll',
    'legendary_monsters:frozen_rune',
    'ysjxspells:fire_crystal'
  ).id('kubejs:bingci1_fire_scroll')

  event.smithing(
    'kubejs:bingci1_frost_scroll',
    'ysjxspells:scroll',
    'legendary_monsters:frozen_rune',
    'ysjxspells:frost_crystal'
  ).id('kubejs:bingci1_frost_scroll')

  event.smithing(
    'kubejs:bingci1_wuli_scroll',
    'ysjxspells:scroll',
    'legendary_monsters:frozen_rune',
    'ysjxspells:wuli_crystal'
  ).id('kubejs:bingci1_wuli_scroll')

  event.smithing(
    'kubejs:bingci1_healing_scroll',
    'ysjxspells:scroll',
    'legendary_monsters:frozen_rune',
    'ysjxspells:healing_crystal'
  ).id('kubejs:bingci1_healing_scroll')

  event.smithing(
    'kubejs:bingci1_water_scroll',
    'ysjxspells:scroll',
    'legendary_monsters:frozen_rune',
    'ysjxspells:water_crystal'
  ).id('kubejs:bingci1_water_scroll')

  event.smithing(
    'kubejs:bingci1_earth_scroll',
    'ysjxspells:scroll',
    'legendary_monsters:frozen_rune',
    'ysjxspells:earth_crystal'
  ).id('kubejs:bingci1_earth_scroll')

  event.smithing(
    'kubejs:bingci1_air_scroll',
    'ysjxspells:scroll',
    'legendary_monsters:frozen_rune',
    'ysjxspells:air_crystal'
  ).id('kubejs:bingci1_air_scroll')

  event.smithing(
    'kubejs:bingci1_lightning_scroll',
    'ysjxspells:scroll',
    'legendary_monsters:frozen_rune',
    'ysjxspells:lightning_crystal'
  ).id('kubejs:bingci1_lightning_scroll')

  event.smithing(
    'kubejs:bingci1_ranged_scroll',
    'ysjxspells:scroll',
    'legendary_monsters:frozen_rune',
    'ysjxspells:sheshou_crystal'
  ).id('kubejs:bingci1_ranged_scroll')

  // 多元素卷轴共用的元素版本
  const scrollElements = [
    'arcane',
    'fire',
    'frost',
    'wuli',
    'healing',
    'water',
    'earth',
    'air',
    'lightning',
    'ranged'
  ]

  // 慈悲射线初阶卷轴
  scrollElements.forEach(element => {
    const crystal = element === 'ranged' ? 'sheshou' : element

    event.smithing(
      `kubejs:cibeishexian_${element}_scroll`,
      'ysjxspells:scroll',
      'fdbosses:eye_of_chesed',
      `ysjxspells:${crystal}_crystal`
    ).id(`kubejs:cibeishexian_${element}_scroll`)

    // 神圣齿轮卷轴
    event.smithing(
      `kubejs:divine_gear_${element}_scroll`,
      'ysjxspells:scroll',
      'fdbosses:eye_of_geburah',
      `ysjxspells:${crystal}_crystal`
    ).id(`kubejs:divine_gear_${element}_scroll`)

    // 破片无人机初阶卷轴
    event.smithing(
      `kubejs:scrap_drone_basic_${element}_scroll`,
      'ysjxspells:scroll',
      'torchesbecomesunlight:sankta_statue_eye',
      `ysjxspells:${crystal}_crystal`
    ).id(`kubejs:scrap_drone_basic_${element}_scroll`)

    // 圣城炮塔初阶卷轴
    event.smithing(
      `kubejs:turret_basic_${element}_scroll`,
      'ysjxspells:scroll',
      'torchesbecomesunlight:rhodes_island_eye',
      `ysjxspells:${crystal}_crystal`
    ).id(`kubejs:turret_basic_${element}_scroll`)

    // 慈悲射线升级卷轴
    event.smithing(
      `kubejs:cibeishexian2_${element}_scroll`,
      'ysjxspells:fashuhexin',
      `kubejs:cibeishexian_${element}_scroll`,
      'ysjxspells:zhongjishuijing'
    ).id(`kubejs:cibeishexian2_${element}_scroll`)

    // 魂焰打击卷轴
    event.smithing(
      `kubejs:soul_flame_strike_${element}_scroll`,
      'cataclysm:flame_eye',
      `kubejs:flame_strike_${element}_scroll`,
      'ysjxspells:gaojishuijing'
    ).id(`kubejs:soul_flame_strike_${element}_scroll`)

    if (element !== 'arcane') {
      // 湮灭传送门卷轴
      event.smithing(
        `kubejs:yanmiechuansongmen_${element}_scroll`,
        'legendary_monsters:eye_of_annihilation',
        `kubejs:lvyanqiangxi_${element}_scroll`,
        'ysjxspells:gaojishuijing'
      ).id(`kubejs:yanmiechuansongmen_${element}_scroll`)

    }

    if (element !== 'wuli' && element !== 'ranged') {
      // 落魂刃升级卷轴
      event.smithing(
        `kubejs:luohunren_${element}_scroll`,
        'legendary_monsters:eye_of_ghost',
        `kubejs:cj_luohunren_${element}_scroll`,
        'ysjxspells:gaojishuijing'
      ).id(`kubejs:luohunren_${element}_scroll`)
    }

    if (element !== 'ranged') {
      // 海浪打击升级卷轴
      event.smithing(
        `kubejs:water_wave_${element}_scroll`,
        'ysjxspells:fashuhexin',
        `kubejs:cj_water_wave_${element}_scroll`,
        'ysjxspells:zhongjishuijing'
      ).id(`kubejs:water_wave_${element}_scroll`)
    }
  })

  // 湮灭传送门奥术分支（奥术绿焰强袭的物品 ID 无元素后缀）
  event.smithing(
    'kubejs:yanmiechuansongmen_arcane_scroll',
    'legendary_monsters:eye_of_annihilation',
    'kubejs:lvyanqiangxi_scroll',
    'ysjxspells:gaojishuijing'
  ).id('kubejs:yanmiechuansongmen_arcane_scroll')

  // 落魂刃物理分支：灵跃斩
  event.smithing(
    'kubejs:tiaozhan_scroll',
    'legendary_monsters:eye_of_ghost',
    'kubejs:cj_luohunren_wuli_scroll',
    'ysjxspells:gaojishuijing'
  ).id('kubejs:tiaozhan_scroll')

  // 落魂刃游侠分支：亡灵盾（游侠）
  event.smithing(
    'kubejs:wanglingdun_ranged_scroll',
    'legendary_monsters:eye_of_ghost',
    'ysjxspells:sheshou_crystal',
    'ysjxspells:gaojishuijing'
  ).id('kubejs:wanglingdun_ranged_scroll')

  // 落魂刃物理分支：亡灵盾
  event.smithing(
    'kubejs:wanglingdun_scroll',
    'legendary_monsters:eye_of_ghost',
    'ysjxspells:wuli_crystal',
    'ysjxspells:gaojishuijing'
  ).id('kubejs:wanglingdun_scroll')

  // 王之宝库终极卷轴
  event.smithing(
    'kubejs:wangzhibaoku_scroll',
    'ysjxspells:gaojishuijing',
    'kubejs:wangzhibaoku_2_scroll',
    'cataclysm:cursed_eye'
  ).id('kubejs:wangzhibaoku_scroll')

  // 冰刺与唤魔尖牙卷轴升级：初阶卷轴 -> 二阶卷轴
  scrollElements.forEach(element => {
    event.smithing(
      `kubejs:bingci2_${element}_scroll`,
      'kubejs:baozhu',
      `kubejs:bingci1_${element}_scroll`,
      'ysjxspells:zhongjishuijing'
    ).id(`kubejs:bingci2_${element}_scroll`)

    event.smithing(
      `kubejs:huanmo2_${element}_scroll`,
      'kubejs:baozhu',
      `kubejs:huanmo1_${element}_scroll`,
      'ysjxspells:zhongjishuijing'
    ).id(`kubejs:huanmo2_${element}_scroll`)
  })

  // 雷击升级卷轴
  event.smithing(
    'kubejs:leiji_scroll',
    'kubejs:baozhu',
    'kubejs:leiji_basic_scroll',
    'ysjxspells:zhongjishuijing'
  ).id('kubejs:leiji_scroll')

  // 火球术、飞弹与斩击中阶卷轴
  const basicCrystalScrollUpgrades = [
    ['kubejs:huoyanfeidan_upgraded_scroll', 'kubejs:fireball_scroll'],
    ['kubejs:aoshufeidan_upgraded_scroll', 'kubejs:arcane_missile_scroll'],
    ['kubejs:bingshuangfeidan_upgraded_scroll', 'kubejs:frostbolt_scroll'],
    ['kubejs:haiyangshuangfeidan_upgraded_scroll', 'kubejs:haiyangfeidan_scroll'],
    ['kubejs:kongqishuangfeidan_upgraded_scroll', 'kubejs:kongqifeidan_scroll'],
    ['kubejs:shimao_upgraded_scroll', 'kubejs:terra_stone_spear_scroll'],
    ['kubejs:wulislash_2_scroll', 'kubejs:wulislash_scroll'],
    ['kubejs:waterslash_2_scroll', 'kubejs:waterslash_scroll'],
    ['kubejs:airslash_2_scroll', 'kubejs:airslash_scroll']
  ]

  basicCrystalScrollUpgrades.forEach(recipe => {
    event.smithing(
      recipe[0],
      'kubejs:baozhu',
      recipe[1],
      'ysjxspells:chujishuijing'
    ).id(recipe[0])
  })

  // 海浪打击初阶卷轴
  event.smithing(
    'kubejs:cj_water_wave_arcane_scroll',
    'ysjxspells:scroll',
    'cataclysm:lacrima',
    'ysjxspells:arcane_crystal'
  ).id('kubejs:cj_water_wave_arcane_scroll')

  event.smithing(
    'kubejs:cj_water_wave_fire_scroll',
    'ysjxspells:scroll',
    'cataclysm:lacrima',
    'ysjxspells:fire_crystal'
  ).id('kubejs:cj_water_wave_fire_scroll')

  event.smithing(
    'kubejs:cj_water_wave_frost_scroll',
    'ysjxspells:scroll',
    'cataclysm:lacrima',
    'ysjxspells:frost_crystal'
  ).id('kubejs:cj_water_wave_frost_scroll')

  event.smithing(
    'kubejs:cj_water_wave_wuli_scroll',
    'ysjxspells:scroll',
    'cataclysm:lacrima',
    'ysjxspells:wuli_crystal'
  ).id('kubejs:cj_water_wave_wuli_scroll')

  event.smithing(
    'kubejs:cj_water_wave_healing_scroll',
    'ysjxspells:scroll',
    'cataclysm:lacrima',
    'ysjxspells:healing_crystal'
  ).id('kubejs:cj_water_wave_healing_scroll')

  event.smithing(
    'kubejs:cj_water_wave_water_scroll',
    'ysjxspells:scroll',
    'cataclysm:lacrima',
    'ysjxspells:water_crystal'
  ).id('kubejs:cj_water_wave_water_scroll')

  event.smithing(
    'kubejs:cj_water_wave_earth_scroll',
    'ysjxspells:scroll',
    'cataclysm:lacrima',
    'ysjxspells:earth_crystal'
  ).id('kubejs:cj_water_wave_earth_scroll')

  event.smithing(
    'kubejs:cj_water_wave_air_scroll',
    'ysjxspells:scroll',
    'cataclysm:lacrima',
    'ysjxspells:air_crystal'
  ).id('kubejs:cj_water_wave_air_scroll')

  event.smithing(
    'kubejs:cj_water_wave_lightning_scroll',
    'ysjxspells:scroll',
    'cataclysm:lacrima',
    'ysjxspells:lightning_crystal'
  ).id('kubejs:cj_water_wave_lightning_scroll')

  // 风暴巨蛇（物理）
  event.smithing(
    'kubejs:fengbaojushe_scroll',
    'ysjxspells:scroll',
    'cataclysm:essence_of_the_storm',
    'ysjxspells:wuli_crystal'
  ).id('kubejs:fengbaojushe_scroll')

  // 烈焰打击初阶卷轴
  event.smithing(
    'kubejs:cj_flame_strike_arcane_scroll',
    'ysjxspells:scroll',
    'cataclysm:ignitium_ingot',
    'ysjxspells:arcane_crystal'
  ).id('kubejs:cj_flame_strike_arcane_scroll')

  event.smithing(
    'kubejs:cj_flame_strike_fire_scroll',
    'ysjxspells:scroll',
    'cataclysm:ignitium_ingot',
    'ysjxspells:fire_crystal'
  ).id('kubejs:cj_flame_strike_fire_scroll')

  event.smithing(
    'kubejs:cj_flame_strike_frost_scroll',
    'ysjxspells:scroll',
    'cataclysm:ignitium_ingot',
    'ysjxspells:frost_crystal'
  ).id('kubejs:cj_flame_strike_frost_scroll')

  event.smithing(
    'kubejs:cj_flame_strike_wuli_scroll',
    'ysjxspells:scroll',
    'cataclysm:ignitium_ingot',
    'ysjxspells:wuli_crystal'
  ).id('kubejs:cj_flame_strike_wuli_scroll')

  event.smithing(
    'kubejs:cj_flame_strike_healing_scroll',
    'ysjxspells:scroll',
    'cataclysm:ignitium_ingot',
    'ysjxspells:healing_crystal'
  ).id('kubejs:cj_flame_strike_healing_scroll')

  event.smithing(
    'kubejs:cj_flame_strike_water_scroll',
    'ysjxspells:scroll',
    'cataclysm:ignitium_ingot',
    'ysjxspells:water_crystal'
  ).id('kubejs:cj_flame_strike_water_scroll')

  event.smithing(
    'kubejs:cj_flame_strike_earth_scroll',
    'ysjxspells:scroll',
    'cataclysm:ignitium_ingot',
    'ysjxspells:earth_crystal'
  ).id('kubejs:cj_flame_strike_earth_scroll')

  event.smithing(
    'kubejs:cj_flame_strike_air_scroll',
    'ysjxspells:scroll',
    'cataclysm:ignitium_ingot',
    'ysjxspells:air_crystal'
  ).id('kubejs:cj_flame_strike_air_scroll')

  event.smithing(
    'kubejs:cj_flame_strike_lightning_scroll',
    'ysjxspells:scroll',
    'cataclysm:ignitium_ingot',
    'ysjxspells:lightning_crystal'
  ).id('kubejs:cj_flame_strike_lightning_scroll')

  event.smithing(
    'kubejs:cj_flame_strike_ranged_scroll',
    'ysjxspells:scroll',
    'cataclysm:ignitium_ingot',
    'ysjxspells:sheshou_crystal'
  ).id('kubejs:cj_flame_strike_ranged_scroll')

  // 沙暴缠绕卷轴
  event.smithing(
    'kubejs:sandstorm_arcane_scroll',
    'ysjxspells:scroll',
    'cataclysm:sandstorm_in_a_bottle',
    'ysjxspells:arcane_crystal'
  ).id('kubejs:sandstorm_arcane_scroll')

  event.smithing(
    'kubejs:sandstorm_fire_scroll',
    'ysjxspells:scroll',
    'cataclysm:sandstorm_in_a_bottle',
    'ysjxspells:fire_crystal'
  ).id('kubejs:sandstorm_fire_scroll')

  event.smithing(
    'kubejs:sandstorm_frost_scroll',
    'ysjxspells:scroll',
    'cataclysm:sandstorm_in_a_bottle',
    'ysjxspells:frost_crystal'
  ).id('kubejs:sandstorm_frost_scroll')

  event.smithing(
    'kubejs:sandstorm_wuli_scroll',
    'ysjxspells:scroll',
    'cataclysm:sandstorm_in_a_bottle',
    'ysjxspells:wuli_crystal'
  ).id('kubejs:sandstorm_wuli_scroll')

  event.smithing(
    'kubejs:sandstorm_healing_scroll',
    'ysjxspells:scroll',
    'cataclysm:sandstorm_in_a_bottle',
    'ysjxspells:healing_crystal'
  ).id('kubejs:sandstorm_healing_scroll')

  event.smithing(
    'kubejs:sandstorm_water_scroll',
    'ysjxspells:scroll',
    'cataclysm:sandstorm_in_a_bottle',
    'ysjxspells:water_crystal'
  ).id('kubejs:sandstorm_water_scroll')

  event.smithing(
    'kubejs:sandstorm_earth_scroll',
    'ysjxspells:scroll',
    'cataclysm:sandstorm_in_a_bottle',
    'ysjxspells:earth_crystal'
  ).id('kubejs:sandstorm_earth_scroll')

  event.smithing(
    'kubejs:sandstorm_air_scroll',
    'ysjxspells:scroll',
    'cataclysm:sandstorm_in_a_bottle',
    'ysjxspells:air_crystal'
  ).id('kubejs:sandstorm_air_scroll')

  event.smithing(
    'kubejs:sandstorm_lightning_scroll',
    'ysjxspells:scroll',
    'cataclysm:sandstorm_in_a_bottle',
    'ysjxspells:lightning_crystal'
  ).id('kubejs:sandstorm_lightning_scroll')

  event.smithing(
    'kubejs:sandstorm_ranged_scroll',
    'ysjxspells:scroll',
    'cataclysm:sandstorm_in_a_bottle',
    'ysjxspells:sheshou_crystal'
  ).id('kubejs:sandstorm_ranged_scroll')

  // 幻影戟初阶卷轴
  event.smithing(
    'kubejs:cj_huanyingji_arcane_scroll',
    'ysjxspells:scroll',
    'cataclysm:cursium_ingot',
    'ysjxspells:arcane_crystal'
  ).id('kubejs:cj_huanyingji_arcane_scroll')

  event.smithing(
    'kubejs:cj_huanyingji_fire_scroll',
    'ysjxspells:scroll',
    'cataclysm:cursium_ingot',
    'ysjxspells:fire_crystal'
  ).id('kubejs:cj_huanyingji_fire_scroll')

  event.smithing(
    'kubejs:cj_huanyingji_frost_scroll',
    'ysjxspells:scroll',
    'cataclysm:cursium_ingot',
    'ysjxspells:frost_crystal'
  ).id('kubejs:cj_huanyingji_frost_scroll')

  event.smithing(
    'kubejs:cj_huanyingji_wuli_scroll',
    'ysjxspells:scroll',
    'cataclysm:cursium_ingot',
    'ysjxspells:wuli_crystal'
  ).id('kubejs:cj_huanyingji_wuli_scroll')

  event.smithing(
    'kubejs:cj_huanyingji_healing_scroll',
    'ysjxspells:scroll',
    'cataclysm:cursium_ingot',
    'ysjxspells:healing_crystal'
  ).id('kubejs:cj_huanyingji_healing_scroll')

  event.smithing(
    'kubejs:cj_huanyingji_water_scroll',
    'ysjxspells:scroll',
    'cataclysm:cursium_ingot',
    'ysjxspells:water_crystal'
  ).id('kubejs:cj_huanyingji_water_scroll')

  event.smithing(
    'kubejs:cj_huanyingji_earth_scroll',
    'ysjxspells:scroll',
    'cataclysm:cursium_ingot',
    'ysjxspells:earth_crystal'
  ).id('kubejs:cj_huanyingji_earth_scroll')

  event.smithing(
    'kubejs:cj_huanyingji_air_scroll',
    'ysjxspells:scroll',
    'cataclysm:cursium_ingot',
    'ysjxspells:air_crystal'
  ).id('kubejs:cj_huanyingji_air_scroll')

  event.smithing(
    'kubejs:cj_huanyingji_lightning_scroll',
    'ysjxspells:scroll',
    'cataclysm:cursium_ingot',
    'ysjxspells:lightning_crystal'
  ).id('kubejs:cj_huanyingji_lightning_scroll')

  // 落魂刃初阶卷轴
  event.smithing(
    'kubejs:cj_luohunren_arcane_scroll',
    'ysjxspells:scroll',
    'legendary_monsters:corrupted_soul',
    'ysjxspells:arcane_crystal'
  ).id('kubejs:cj_luohunren_arcane_scroll')

  event.smithing(
    'kubejs:cj_luohunren_fire_scroll',
    'ysjxspells:scroll',
    'legendary_monsters:corrupted_soul',
    'ysjxspells:fire_crystal'
  ).id('kubejs:cj_luohunren_fire_scroll')

  event.smithing(
    'kubejs:cj_luohunren_frost_scroll',
    'ysjxspells:scroll',
    'legendary_monsters:corrupted_soul',
    'ysjxspells:frost_crystal'
  ).id('kubejs:cj_luohunren_frost_scroll')

  event.smithing(
    'kubejs:cj_luohunren_wuli_scroll',
    'ysjxspells:scroll',
    'legendary_monsters:corrupted_soul',
    'ysjxspells:wuli_crystal'
  ).id('kubejs:cj_luohunren_wuli_scroll')

  event.smithing(
    'kubejs:cj_luohunren_healing_scroll',
    'ysjxspells:scroll',
    'legendary_monsters:corrupted_soul',
    'ysjxspells:healing_crystal'
  ).id('kubejs:cj_luohunren_healing_scroll')

  event.smithing(
    'kubejs:cj_luohunren_water_scroll',
    'ysjxspells:scroll',
    'legendary_monsters:corrupted_soul',
    'ysjxspells:water_crystal'
  ).id('kubejs:cj_luohunren_water_scroll')

  event.smithing(
    'kubejs:cj_luohunren_earth_scroll',
    'ysjxspells:scroll',
    'legendary_monsters:corrupted_soul',
    'ysjxspells:earth_crystal'
  ).id('kubejs:cj_luohunren_earth_scroll')

  event.smithing(
    'kubejs:cj_luohunren_air_scroll',
    'ysjxspells:scroll',
    'legendary_monsters:corrupted_soul',
    'ysjxspells:air_crystal'
  ).id('kubejs:cj_luohunren_air_scroll')

  event.smithing(
    'kubejs:cj_luohunren_lightning_scroll',
    'ysjxspells:scroll',
    'legendary_monsters:corrupted_soul',
    'ysjxspells:lightning_crystal'
  ).id('kubejs:cj_luohunren_lightning_scroll')

  // 歼击者导弹卷轴
  event.smithing(
    'kubejs:jianjizhedaodan_arcane_scroll',
    'ysjxspells:scroll',
    'eeeabsmobs:guardian_cube',
    'ysjxspells:arcane_crystal'
  ).id('kubejs:jianjizhedaodan_arcane_scroll')

  event.smithing(
    'kubejs:jianjizhedaodan_fire_scroll',
    'ysjxspells:scroll',
    'eeeabsmobs:guardian_cube',
    'ysjxspells:fire_crystal'
  ).id('kubejs:jianjizhedaodan_fire_scroll')

  event.smithing(
    'kubejs:jianjizhedaodan_frost_scroll',
    'ysjxspells:scroll',
    'eeeabsmobs:guardian_cube',
    'ysjxspells:frost_crystal'
  ).id('kubejs:jianjizhedaodan_frost_scroll')

  event.smithing(
    'kubejs:jianjizhedaodan_wuli_scroll',
    'ysjxspells:scroll',
    'eeeabsmobs:guardian_cube',
    'ysjxspells:wuli_crystal'
  ).id('kubejs:jianjizhedaodan_wuli_scroll')

  event.smithing(
    'kubejs:jianjizhedaodan_healing_scroll',
    'ysjxspells:scroll',
    'eeeabsmobs:guardian_cube',
    'ysjxspells:healing_crystal'
  ).id('kubejs:jianjizhedaodan_healing_scroll')

  event.smithing(
    'kubejs:jianjizhedaodan_water_scroll',
    'ysjxspells:scroll',
    'eeeabsmobs:guardian_cube',
    'ysjxspells:water_crystal'
  ).id('kubejs:jianjizhedaodan_water_scroll')

  event.smithing(
    'kubejs:jianjizhedaodan_earth_scroll',
    'ysjxspells:scroll',
    'eeeabsmobs:guardian_cube',
    'ysjxspells:earth_crystal'
  ).id('kubejs:jianjizhedaodan_earth_scroll')

  event.smithing(
    'kubejs:jianjizhedaodan_air_scroll',
    'ysjxspells:scroll',
    'eeeabsmobs:guardian_cube',
    'ysjxspells:air_crystal'
  ).id('kubejs:jianjizhedaodan_air_scroll')

  event.smithing(
    'kubejs:jianjizhedaodan_lightning_scroll',
    'ysjxspells:scroll',
    'eeeabsmobs:guardian_cube',
    'ysjxspells:lightning_crystal'
  ).id('kubejs:jianjizhedaodan_lightning_scroll')

  event.smithing(
    'kubejs:jianjizhedaodan_ranged_scroll',
    'ysjxspells:scroll',
    'eeeabsmobs:guardian_cube',
    'ysjxspells:sheshou_crystal'
  ).id('kubejs:jianjizhedaodan_ranged_scroll')

  // 下界炮弹卷轴
  event.smithing(
    'kubejs:xiajiepaodan_arcane_scroll',
    'ysjxspells:scroll',
    'cataclysm:lava_power_cell',
    'ysjxspells:arcane_crystal'
  ).id('kubejs:xiajiepaodan_arcane_scroll')

  event.smithing(
    'kubejs:xiajiepaodan_fire_scroll',
    'ysjxspells:scroll',
    'cataclysm:lava_power_cell',
    'ysjxspells:fire_crystal'
  ).id('kubejs:xiajiepaodan_fire_scroll')

  event.smithing(
    'kubejs:xiajiepaodan_frost_scroll',
    'ysjxspells:scroll',
    'cataclysm:lava_power_cell',
    'ysjxspells:frost_crystal'
  ).id('kubejs:xiajiepaodan_frost_scroll')

  event.smithing(
    'kubejs:xiajiepaodan_wuli_scroll',
    'ysjxspells:scroll',
    'cataclysm:lava_power_cell',
    'ysjxspells:wuli_crystal'
  ).id('kubejs:xiajiepaodan_wuli_scroll')

  event.smithing(
    'kubejs:xiajiepaodan_healing_scroll',
    'ysjxspells:scroll',
    'cataclysm:lava_power_cell',
    'ysjxspells:healing_crystal'
  ).id('kubejs:xiajiepaodan_healing_scroll')

  event.smithing(
    'kubejs:xiajiepaodan_water_scroll',
    'ysjxspells:scroll',
    'cataclysm:lava_power_cell',
    'ysjxspells:water_crystal'
  ).id('kubejs:xiajiepaodan_water_scroll')

  event.smithing(
    'kubejs:xiajiepaodan_earth_scroll',
    'ysjxspells:scroll',
    'cataclysm:lava_power_cell',
    'ysjxspells:earth_crystal'
  ).id('kubejs:xiajiepaodan_earth_scroll')

  event.smithing(
    'kubejs:xiajiepaodan_air_scroll',
    'ysjxspells:scroll',
    'cataclysm:lava_power_cell',
    'ysjxspells:air_crystal'
  ).id('kubejs:xiajiepaodan_air_scroll')

  event.smithing(
    'kubejs:xiajiepaodan_lightning_scroll',
    'ysjxspells:scroll',
    'cataclysm:lava_power_cell',
    'ysjxspells:lightning_crystal'
  ).id('kubejs:xiajiepaodan_lightning_scroll')

  event.smithing(
    'kubejs:xiajiepaodan_ranged_scroll',
    'ysjxspells:scroll',
    'cataclysm:lava_power_cell',
    'ysjxspells:sheshou_crystal'
  ).id('kubejs:xiajiepaodan_ranged_scroll')

  // 先驱导弹卷轴
  event.smithing(
    'kubejs:xianqudaodan_arcane_scroll',
    'ysjxspells:scroll',
    'cataclysm:witherite_ingot',
    'ysjxspells:arcane_crystal'
  ).id('kubejs:xianqudaodan_arcane_scroll')

  event.smithing(
    'kubejs:xianqudaodan_fire_scroll',
    'ysjxspells:scroll',
    'cataclysm:witherite_ingot',
    'ysjxspells:fire_crystal'
  ).id('kubejs:xianqudaodan_fire_scroll')

  event.smithing(
    'kubejs:xianqudaodan_frost_scroll',
    'ysjxspells:scroll',
    'cataclysm:witherite_ingot',
    'ysjxspells:frost_crystal'
  ).id('kubejs:xianqudaodan_frost_scroll')

  event.smithing(
    'kubejs:xianqudaodan_wuli_scroll',
    'ysjxspells:scroll',
    'cataclysm:witherite_ingot',
    'ysjxspells:wuli_crystal'
  ).id('kubejs:xianqudaodan_wuli_scroll')

  event.smithing(
    'kubejs:xianqudaodan_healing_scroll',
    'ysjxspells:scroll',
    'cataclysm:witherite_ingot',
    'ysjxspells:healing_crystal'
  ).id('kubejs:xianqudaodan_healing_scroll')

  event.smithing(
    'kubejs:xianqudaodan_water_scroll',
    'ysjxspells:scroll',
    'cataclysm:witherite_ingot',
    'ysjxspells:water_crystal'
  ).id('kubejs:xianqudaodan_water_scroll')

  event.smithing(
    'kubejs:xianqudaodan_earth_scroll',
    'ysjxspells:scroll',
    'cataclysm:witherite_ingot',
    'ysjxspells:earth_crystal'
  ).id('kubejs:xianqudaodan_earth_scroll')

  event.smithing(
    'kubejs:xianqudaodan_air_scroll',
    'ysjxspells:scroll',
    'cataclysm:witherite_ingot',
    'ysjxspells:air_crystal'
  ).id('kubejs:xianqudaodan_air_scroll')

  event.smithing(
    'kubejs:xianqudaodan_lightning_scroll',
    'ysjxspells:scroll',
    'cataclysm:witherite_ingot',
    'ysjxspells:lightning_crystal'
  ).id('kubejs:xianqudaodan_lightning_scroll')

  event.smithing(
    'kubejs:xianqudaodan_ranged_scroll',
    'ysjxspells:scroll',
    'cataclysm:witherite_ingot',
    'ysjxspells:sheshou_crystal'
  ).id('kubejs:xianqudaodan_ranged_scroll')

  // 绿焰强袭卷轴
  event.smithing(
    'kubejs:lvyanqiangxi_scroll',
    'ysjxspells:scroll',
    'legendary_monsters:portal_shard',
    'ysjxspells:arcane_crystal'
  ).id('kubejs:lvyanqiangxi_scroll')

  event.smithing(
    'kubejs:lvyanqiangxi_fire_scroll',
    'ysjxspells:scroll',
    'legendary_monsters:portal_shard',
    'ysjxspells:fire_crystal'
  ).id('kubejs:lvyanqiangxi_fire_scroll')

  event.smithing(
    'kubejs:lvyanqiangxi_frost_scroll',
    'ysjxspells:scroll',
    'legendary_monsters:portal_shard',
    'ysjxspells:frost_crystal'
  ).id('kubejs:lvyanqiangxi_frost_scroll')

  event.smithing(
    'kubejs:lvyanqiangxi_wuli_scroll',
    'ysjxspells:scroll',
    'legendary_monsters:portal_shard',
    'ysjxspells:wuli_crystal'
  ).id('kubejs:lvyanqiangxi_wuli_scroll')

  event.smithing(
    'kubejs:lvyanqiangxi_healing_scroll',
    'ysjxspells:scroll',
    'legendary_monsters:portal_shard',
    'ysjxspells:healing_crystal'
  ).id('kubejs:lvyanqiangxi_healing_scroll')

  event.smithing(
    'kubejs:lvyanqiangxi_water_scroll',
    'ysjxspells:scroll',
    'legendary_monsters:portal_shard',
    'ysjxspells:water_crystal'
  ).id('kubejs:lvyanqiangxi_water_scroll')

  event.smithing(
    'kubejs:lvyanqiangxi_earth_scroll',
    'ysjxspells:scroll',
    'legendary_monsters:portal_shard',
    'ysjxspells:earth_crystal'
  ).id('kubejs:lvyanqiangxi_earth_scroll')

  event.smithing(
    'kubejs:lvyanqiangxi_air_scroll',
    'ysjxspells:scroll',
    'legendary_monsters:portal_shard',
    'ysjxspells:air_crystal'
  ).id('kubejs:lvyanqiangxi_air_scroll')

  event.smithing(
    'kubejs:lvyanqiangxi_lightning_scroll',
    'ysjxspells:scroll',
    'legendary_monsters:portal_shard',
    'ysjxspells:lightning_crystal'
  ).id('kubejs:lvyanqiangxi_lightning_scroll')

  event.smithing(
    'kubejs:lvyanqiangxi_ranged_scroll',
    'ysjxspells:scroll',
    'legendary_monsters:portal_shard',
    'ysjxspells:sheshou_crystal'
  ).id('kubejs:lvyanqiangxi_ranged_scroll')

  // 深渊水涌卷轴
  event.smithing(
    'kubejs:abyss_blast_portal_arcane_scroll',
    'ysjxspells:scroll',
    'cataclysm:abyss_eye',
    'ysjxspells:arcane_crystal'
  ).id('kubejs:abyss_blast_portal_arcane_scroll')

  event.smithing(
    'kubejs:abyss_blast_portal_fire_scroll',
    'ysjxspells:scroll',
    'cataclysm:abyss_eye',
    'ysjxspells:fire_crystal'
  ).id('kubejs:abyss_blast_portal_fire_scroll')

  event.smithing(
    'kubejs:abyss_blast_portal_frost_scroll',
    'ysjxspells:scroll',
    'cataclysm:abyss_eye',
    'ysjxspells:frost_crystal'
  ).id('kubejs:abyss_blast_portal_frost_scroll')

  event.smithing(
    'kubejs:abyss_blast_portal_wuli_scroll',
    'ysjxspells:scroll',
    'cataclysm:abyss_eye',
    'ysjxspells:wuli_crystal'
  ).id('kubejs:abyss_blast_portal_wuli_scroll')

  event.smithing(
    'kubejs:abyss_blast_portal_healing_scroll',
    'ysjxspells:scroll',
    'cataclysm:abyss_eye',
    'ysjxspells:healing_crystal'
  ).id('kubejs:abyss_blast_portal_healing_scroll')

  event.smithing(
    'kubejs:abyss_blast_portal_water_scroll',
    'ysjxspells:scroll',
    'cataclysm:abyss_eye',
    'ysjxspells:water_crystal'
  ).id('kubejs:abyss_blast_portal_water_scroll')

  event.smithing(
    'kubejs:abyss_blast_portal_earth_scroll',
    'ysjxspells:scroll',
    'cataclysm:abyss_eye',
    'ysjxspells:earth_crystal'
  ).id('kubejs:abyss_blast_portal_earth_scroll')

  event.smithing(
    'kubejs:abyss_blast_portal_air_scroll',
    'ysjxspells:scroll',
    'cataclysm:abyss_eye',
    'ysjxspells:air_crystal'
  ).id('kubejs:abyss_blast_portal_air_scroll')

  event.smithing(
    'kubejs:abyss_blast_portal_lightning_scroll',
    'ysjxspells:scroll',
    'cataclysm:abyss_eye',
    'ysjxspells:lightning_crystal'
  ).id('kubejs:abyss_blast_portal_lightning_scroll')

  event.smithing(
    'kubejs:abyss_blast_portal_ranged_scroll',
    'ysjxspells:scroll',
    'cataclysm:abyss_eye',
    'ysjxspells:sheshou_crystal'
  ).id('kubejs:abyss_blast_portal_ranged_scroll')

  // 虚空符文卷轴
  event.smithing(
    'kubejs:xukongfuwen_arcane_scroll',
    'ysjxspells:scroll',
    'cataclysm:void_eye',
    'ysjxspells:arcane_crystal'
  ).id('kubejs:xukongfuwen_arcane_scroll')

  event.smithing(
    'kubejs:xukongfuwen_fire_scroll',
    'ysjxspells:scroll',
    'cataclysm:void_eye',
    'ysjxspells:fire_crystal'
  ).id('kubejs:xukongfuwen_fire_scroll')

  event.smithing(
    'kubejs:xukongfuwen_frost_scroll',
    'ysjxspells:scroll',
    'cataclysm:void_eye',
    'ysjxspells:frost_crystal'
  ).id('kubejs:xukongfuwen_frost_scroll')

  event.smithing(
    'kubejs:xukongfuwen_wuli_scroll',
    'ysjxspells:scroll',
    'cataclysm:void_eye',
    'ysjxspells:wuli_crystal'
  ).id('kubejs:xukongfuwen_wuli_scroll')

  event.smithing(
    'kubejs:xukongfuwen_healing_scroll',
    'ysjxspells:scroll',
    'cataclysm:void_eye',
    'ysjxspells:healing_crystal'
  ).id('kubejs:xukongfuwen_healing_scroll')

  event.smithing(
    'kubejs:xukongfuwen_water_scroll',
    'ysjxspells:scroll',
    'cataclysm:void_eye',
    'ysjxspells:water_crystal'
  ).id('kubejs:xukongfuwen_water_scroll')

  event.smithing(
    'kubejs:xukongfuwen_earth_scroll',
    'ysjxspells:scroll',
    'cataclysm:void_eye',
    'ysjxspells:earth_crystal'
  ).id('kubejs:xukongfuwen_earth_scroll')

  event.smithing(
    'kubejs:xukongfuwen_air_scroll',
    'ysjxspells:scroll',
    'cataclysm:void_eye',
    'ysjxspells:air_crystal'
  ).id('kubejs:xukongfuwen_air_scroll')

  event.smithing(
    'kubejs:xukongfuwen_lightning_scroll',
    'ysjxspells:scroll',
    'cataclysm:void_eye',
    'ysjxspells:lightning_crystal'
  ).id('kubejs:xukongfuwen_lightning_scroll')

  event.smithing(
    'kubejs:xukongfuwen_ranged_scroll',
    'ysjxspells:scroll',
    'cataclysm:void_eye',
    'ysjxspells:sheshou_crystal'
  ).id('kubejs:xukongfuwen_ranged_scroll')

  // Whispering 饰品与装备
  event.smithing(
    'whispering:molten_emblem',
    'legendary_monsters:withered_bone',
    'jewelry:iron_ring',
    'legendary_monsters:lava_eaters_skin'
  ).id('kubejs:whispering_molten_emblem')

  event.shaped(
    'whispering:candy_heart',
    [
      'AAA',
      'BCB',
      'AAA'
    ],
    {
      A: 'alexscaves:block_of_chocolate',
      B: 'alexscaves:gumball_pile',
      C: 'alexscaves:candy_cane'
    }
  ).id('kubejs:whispering_candy_heart')

  event.shaped(
    'whispering:plunder_totem',
    [
      'ABC'
    ],
    {
      A: 'soulsweapons:lord_soul_night_prowler',
      B: 'jewelry:gold_ring',
      C: 'soulsweapons:lord_soul_day_stalker'
    }
  ).id('kubejs:whispering_plunder_totem')

  event.shaped(
    'whispering:feng_bao_wang_guan',
    [
      'AEB',
      'GCH',
      'DIF'
    ],
    {
      A: 'alexscaves:pure_darkness',
      B: 'alexscaves:sweet_tooth',
      C: 'aquamirae:rune_of_the_storm',
      D: 'alexscaves:uranium',
      E: 'twilightforest:hydra_trophy',
      F: 'alexscaves:tectonic_shard',
      G: 'twilightforest:snow_queen_trophy',
      H: 'twilightforest:ur_ghast_trophy',
      I: 'twilightforest:lich_trophy'
    }
  ).id('kubejs:feng_bao_wang_guan')

      event.shaped('whispering:sixiang', [
      ' A ',
      'BCE',
      ' D '
    ], {
      A: 'whispering:yaohui',
      B: 'whispering:cuijie',
      C: 'minecraft:dragon_egg',
      D: 'whispering:jiubiao',
      E: 'whispering:wangzhi'
    }).id('kubejs:sixiang')

  event.shaped(
    'cataclysm:ignitium_upgrade_smithing_template',
    [
      ' A ',
      'ABA',
      ' A '
    ],
    {
      A: 'iceandfire:dragonsteel_fire_ingot',
      B: 'ysjxspells:fashuhexin'
    }
  ).id('kubejs:ignitium_upgrade_smithing_template')

  event.shaped(
    'cataclysm:cursium_upgrade_smithing_template',
    [
      ' A ',
      'ABA',
      ' A '
    ],
    {
      A: 'iceandfire:dragonsteel_ice_ingot',
      B: 'ysjxspells:fashuhexin'
    }
  ).id('kubejs:cursium_upgrade_smithing_template')

  event.shaped(
    'kubejs:moyingshushi',
    [
      ' A ',
      'ABA',
      ' A '
    ],
    {
      A: 'iceandfire:dragonsteel_lightning_ingot',
      B: 'ysjxspells:fashuhexin'
    }
  ).id('kubejs:moyingshushi')

  const armorUpgradeParts = [
    ['helmet', 'head'],
    ['chestplate', 'chest'],
    ['leggings', 'legs'],
    ['boots', 'feet']
  ]

  // 灰烬先驱套升级为 Ignitium 套
  armorUpgradeParts.forEach(upgrade => {
    event.smithing(
      `cataclysm:ignitium_${upgrade[0]}`,
      'cataclysm:ignitium_upgrade_smithing_template',
      `spellbladenext:ashherald_${upgrade[1]}`,
      'ysjxspells:fire_baoshi'
    ).id(`kubejs:ignitium_${upgrade[0]}`)
  })

  // 冰霜先驱套升级为 Cursium 套
  armorUpgradeParts.forEach(upgrade => {
    event.smithing(
      `cataclysm:cursium_${upgrade[0]}`,
      'cataclysm:cursium_upgrade_smithing_template',
      `spellbladenext:frostherald_${upgrade[1]}`,
      'ysjxspells:frost_baoshi'
    ).id(`kubejs:cursium_${upgrade[0]}`)
  })

  // 奥术先驱套升级为黑金元帅套
  armorUpgradeParts.forEach(upgrade => {
    event.smithing(
      `blackgoldalliance:the_black_gold_marshal_${upgrade[0]}`,
      'kubejs:moyingshushi',
      `spellbladenext:arcaneherald_${upgrade[1]}`,
      'ysjxspells:arcane_baoshi'
    ).id(`kubejs:black_gold_marshal_${upgrade[0]}`)
  })

  // 龙钢剑锻造升级
  event.smithing(
    'iceandfire:dragonsteel_lightning_sword',
    'minecraft:netherite_upgrade_smithing_template',
    'spellbladenext:arcane_claymore',
    'iceandfire:dragonsteel_lightning_ingot'
  ).id('kubejs:dragonsteel_lightning_sword')

  event.smithing(
    'iceandfire:dragonsteel_fire_sword',
    'minecraft:netherite_upgrade_smithing_template',
    'spellbladenext:fire_claymore',
    'iceandfire:dragonsteel_fire_ingot'
  ).id('kubejs:dragonsteel_fire_sword')

  event.smithing(
    'iceandfire:dragonsteel_ice_sword',
    'minecraft:netherite_upgrade_smithing_template',
    'spellbladenext:frost_claymore',
    'iceandfire:dragonsteel_ice_ingot'
  ).id('kubejs:dragonsteel_ice_sword')

  // 龙血、下界合金锭与对应符文板锻造成龙钢锭
  event.smithing(
    'iceandfire:dragonsteel_fire_ingot',
    'iceandfire:fire_dragon_blood',
    'minecraft:netherite_ingot',
    'spellbladenext:runeblaze_plate'
  ).id('kubejs:dragonsteel_fire_ingot')

  event.smithing(
    'iceandfire:dragonsteel_ice_ingot',
    'iceandfire:ice_dragon_blood',
    'minecraft:netherite_ingot',
    'spellbladenext:runefrost_plate'
  ).id('kubejs:dragonsteel_ice_ingot')

  event.smithing(
    'iceandfire:dragonsteel_lightning_ingot',
    'iceandfire:lightning_dragon_blood',
    'minecraft:netherite_ingot',
    'spellbladenext:runegleam_plate'
  ).id('kubejs:dragonsteel_lightning_ingot')

  // 虚空锻造剑的三种元素升级路线
  event.smithing(
    'spellbladenext:voidforge',
    'ysjxspells:fashuhexin',
    'iceandfire:dragonsteel_lightning_sword',
    'ysjxspells:arcane_baoshi'
  ).id('kubejs:voidforge_arcane')

  event.smithing(
    'spellbladenext:voidforge',
    'ysjxspells:fashuhexin',
    'iceandfire:dragonsteel_fire_sword',
    'ysjxspells:fire_baoshi'
  ).id('kubejs:voidforge_fire')

  event.smithing(
    'spellbladenext:voidforge',
    'ysjxspells:fashuhexin',
    'iceandfire:dragonsteel_ice_sword',
    'ysjxspells:frost_baoshi'
  ).id('kubejs:voidforge_frost')

  const heraldArmorUpgrades = [
    ['runegleaming', 'arcaneherald'],
    ['runefrosted', 'frostherald'],
    ['runeblazing', 'ashherald']
  ]
  const heraldArmorParts = ['head', 'chest', 'legs', 'feet']

  heraldArmorUpgrades.forEach(upgrade => {
    heraldArmorParts.forEach(part => {
      event.smithing(
        `spellbladenext:${upgrade[1]}_${part}`,
        'minecraft:netherite_upgrade_smithing_template',
        `spellbladenext:${upgrade[0]}_${part}`,
        'ysjxspells:zhongjishuijing'
      ).id(`kubejs:${upgrade[1]}_${part}`)
    })
  })

  event.smithing(
    'ysjx_weapons:thunder_wand',
    'ysjxspells:fashuhexin',
    'ysjx_weapons:netherite_lightning_staff',
    'ysjxspells:gaojishuijing'
  ).id('kubejs:ysjx_thunder_wand')

  event.smithing(
    'ysjx_weapons:arcane_staff',
    'ysjxspells:fashuhexin',
    'wizards:staff_netherite_arcane',
    'ysjxspells:gaojishuijing'
  ).id('kubejs:ysjx_arcane_staff')

  event.smithing(
    'ysjx_weapons:air_staff',
    'ysjxspells:fashuhexin',
    'elemental_wizards_rpg:staff_netherite_wind',
    'ysjxspells:gaojishuijing'
  ).id('kubejs:ysjx_air_staff')

  event.smithing(
    'ysjx_weapons:earth_staff',
    'ysjxspells:fashuhexin',
    'elemental_wizards_rpg:staff_netherite_terra',
    'ysjxspells:gaojishuijing'
  ).id('kubejs:ysjx_earth_staff')

  event.smithing(
    'ysjx_weapons:water_staff',
    'ysjxspells:fashuhexin',
    'elemental_wizards_rpg:staff_netherite_aqua',
    'ysjxspells:gaojishuijing'
  ).id('kubejs:ysjx_water_staff')

  event.smithing(
    'ysjx_weapons:healing_staff',
    'ysjxspells:fashuhexin',
    'paladins:netherite_holy_staff',
    'ysjxspells:gaojishuijing'
  ).id('kubejs:ysjx_healing_staff')

  event.smithing(
    'ysjx_weapons:frost_staff',
    'ysjxspells:fashuhexin',
    'wizards:staff_netherite_frost',
    'ysjxspells:gaojishuijing'
  ).id('kubejs:ysjx_frost_staff')

  event.smithing(
    'ysjx_weapons:fire_staff',
    'ysjxspells:fashuhexin',
    'wizards:staff_netherite_fire',
    'ysjxspells:gaojishuijing'
  ).id('kubejs:ysjx_fire_staff')

  event.smithing(
    'ysjx_weapons:battle_axe',
    'ysjxspells:fashuhexin',
    'berserker_rpg:netherite_berserker_axe',
    'ysjxspells:gaojishuijing'
  ).id('kubejs:ysjx_battle_axe')

  event.smithing(
    'ysjx_weapons:paladin_sword',
    'ysjxspells:fashuhexin',
    'paladins:netherite_claymore',
    'ysjxspells:gaojishuijing'
  ).id('kubejs:ysjx_paladin_sword')

  event.smithing(
    'ysjx_weapons:oath_sword',
    'ysjxspells:fashuhexin',
    'minecraft:netherite_sword',
    'ysjxspells:gaojishuijing'
  ).id('kubejs:ysjx_oath_sword')

  event.smithing(
    'ysjx_weapons:oath_sword',
    'ysjxspells:fashuhexin',
    'rogues:netherite_glaive',
    'ysjxspells:gaojishuijing'
  ).id('kubejs:ysjx_oath_sword_from_netherite_glaive')

  event.smithing(
    'ysjx_weapons:oath_sword',
    'ysjxspells:fashuhexin',
    'rogues:netherite_double_axe',
    'ysjxspells:gaojishuijing'
  ).id('kubejs:ysjx_oath_sword_from_netherite_double_axe')

  event.smithing(
    'ysjx_weapons:liemo_dagger',
    'ysjxspells:fashuhexin',
    'rogues:netherite_dagger',
    'ysjxspells:gaojishuijing'
  ).id('kubejs:ysjx_liemo_dagger')

  event.smithing(
    'ysjx_weapons:liemo_dagger',
    'ysjxspells:fashuhexin',
    'rogues:netherite_sickle',
    'ysjxspells:gaojishuijing'
  ).id('kubejs:ysjx_liemo_dagger_from_netherite_sickle')

  event.smithing(
    'ysjx_weapons:phoenix_god_bow',
    'ysjxspells:fashuhexin',
    'archers:netherite_longbow',
    'ysjxspells:gaojishuijing'
  ).id('kubejs:ysjx_phoenix_god_bow')

  event.smithing(
    'ysjx_weapons:tulong_shield',
    'ysjxspells:fashuhexin',
    'paladins:netherite_kite_shield',
    'iceandfire:dragonsteel_fire_ingot'
  ).id('kubejs:ysjx_tulong_shield')

  const witherSpineScrollRecipes = [
    ['kubejs:heidong_scroll', 'ysjxspells:arcane_baoshi'],
    ['kubejs:huoyun_gaojie_scroll', 'ysjxspells:fire_baoshi'],
    ['kubejs:bingdongfazhen_scroll', 'ysjxspells:frost_baoshi'],
    ['kubejs:malkuth_giant_sword_slash_scroll', 'ysjxspells:wuli_baoshi'],
    ['kubejs:mikaerzhufu_gao_scroll', 'ysjxspells:bealing_baoshi'],
    ['kubejs:haiyangjiejie_scroll', 'ysjxspells:water_baoshi'],
    ['kubejs:tianxing_scroll', 'ysjxspells:earth_baoshi'],
    ['kubejs:fengshenzhishi_scroll', 'ysjxspells:air_baoshi'],
    ['kubejs:lingyuleiji_scroll', 'ysjxspells:lightning_baoshi'],
    ['kubejs:jianyu_gaojie_scroll', 'ysjxspells:sheshou_baoshi']
  ]

  witherSpineScrollRecipes.forEach(recipe => {
    event.smithing(
      recipe[0],
      'ysjxspells:gaojishuijing',
      'kubejs:diaolingji',
      recipe[1]
    ).id(recipe[0])
  })

  const dungeonBadgeSpellFamilies = [
    ['atomic', 'kubejs:dilaohuizhang_atomic'],
    ['explosion', 'kubejs:dilaohuizhang_explosion'],
    ['xushici', 'kubejs:dilaohuizhang_xushici']
  ]
  const dungeonBadgeElementGems = [
    ['arcane', 'ysjxspells:arcane_baoshi'],
    ['fire', 'ysjxspells:fire_baoshi'],
    ['frost', 'ysjxspells:frost_baoshi'],
    ['wuli', 'ysjxspells:wuli_baoshi'],
    ['healing', 'ysjxspells:bealing_baoshi'],
    ['water', 'ysjxspells:water_baoshi'],
    ['earth', 'ysjxspells:earth_baoshi'],
    ['air', 'ysjxspells:air_baoshi'],
    ['lightning', 'ysjxspells:lightning_baoshi'],
    ['ranged', 'ysjxspells:sheshou_baoshi']
  ]

  dungeonBadgeSpellFamilies.forEach(family => {
    dungeonBadgeElementGems.forEach(element => {
      const output = `kubejs:${family[0]}_${element[0]}_scroll`
      event.smithing(
        output,
        'ysjxspells:fashuhexin',
        family[1],
        element[1]
      ).id(output)
    })
  })

  event.shaped(
    'ysjxspells:diji',
    ['ABA'],
    {
      A: '#minecraft:planks',
      B: 'minecraft:iron_ingot'
    }
  ).id('kubejs:diji')

  event.shaped(
    'kubejs:immortal_zhaohuan3',
    ['ABA'],
    {
      A: 'eeeabsmobs:immortal_bone',
      B: 'ysjxspells:diji'
    }
  ).id('kubejs:immortal_zhaohuan3')

      event.shaped('ysjx_weapons:golden_mage_chest', [
      'A A',
      'BAB',
      'BBB'
    ], {
      A: 'elemental_wizards_rpg:elemental_essence',
      B: '#minecraft:wool'
    }).id('kubejs:robe_chestplate')

    event.shaped('ysjx_weapons:golden_mage_feet', [
      'A A',
      'B B'
    ], {
      A: 'elemental_wizards_rpg:elemental_essence',
      B: '#minecraft:wool'
    }).id('kubejs:robe_boots')

    event.shaped('ysjx_weapons:golden_mage_head', [
      '  A',
      ' A ',
      'BBB'
    ], {
      A: 'elemental_wizards_rpg:elemental_essence',
      B: '#minecraft:wool'
    }).id('kubejs:robe_helmet')

    event.shaped('ysjx_weapons:golden_mage_legs', [
      'AAA',
      'B B',
      'B B'
    ], {
      A: 'elemental_wizards_rpg:elemental_essence',
      B: '#minecraft:wool'
    }).id('kubejs:robe_leggings')

  event.shaped(
    'aether:aether_portal_frame',
    [
      'AAA',
      'ABA',
      'AAA'
    ],
    {
      A: 'minecraft:gold_ingot',
      B: 'minecraft:water_bucket'
    }
  ).id('kubejs:aether_portal_frame')

  event.shaped(
    'abyssal_corrupter:abyssal_summoning_altar',
    [
      'AAA',
      'ABA',
      'AAA'
    ],
    {
      A: 'minecraft:sculk_catalyst',
      B: 'minecraft:netherite_ingot'
    }
  ).id('kubejs:abyssal_corrupter_abyssal_summoning_altar')

  event.shapeless(
    Item.of('minecraft:string', 4),
    ['#minecraft:wool']
  ).id('kubejs:string_from_wool')

  event.shaped(
    'kubejs:zhouye_zhaohuanqi',
    [
      'ABC',
      ' D '
    ],
    {
      A: 'twilightforest:naga_trophy',
      B: 'ysjxspells:diji',
      C: 'twilightforest:alpha_yeti_trophy',
      D: 'twilightforest:knight_phantom_trophy'
    }
  ).id('kubejs:zhouye_zhaohuanqi')

    event.shaped(
    'editenchanting:enchantment_edit_table',
    [
      ' A ',
      'ABA',
      'CCC'
    ],
    {
      A: 'alexscaves:tectonic_shard',
      B: 'alexscaves:telecore',
      C: 'minecraft:obsidian'
    }
  ).id('kubejs:enchantment')
})
