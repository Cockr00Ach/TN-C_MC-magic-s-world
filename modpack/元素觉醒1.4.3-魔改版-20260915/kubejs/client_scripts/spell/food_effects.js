ItemEvents.tooltip(event => {
  const fruitEffects = [
    { id: 'air_zhiguo', attribute: '空气法术强度' },
    { id: 'arcane_zhiguo', attribute: '奥术法术强度' },
    { id: 'earth_zhiguo', attribute: '大地法术强度' },
    { id: 'fire_zhiguo', attribute: '火焰法术强度' },
    { id: 'frost_zhiguo', attribute: '冰霜法术强度' },
    { id: 'healing_zhiguo', attribute: '治疗法术强度' },
    { id: 'lightning_zhiguo', attribute: '闪电法术强度' },
    { id: 'water_zhiguo', attribute: '海洋法术强度' },
    { id: 'wuli_zhiguo', attribute: '近战攻击伤害' },
    { id: 'youxia_zhiguo', attribute: '远程伤害' }
  ]

  for (const fruit of fruitEffects) {
    event.add(`kubejs:${fruit.id}`, [
      Text.gray('食用后会获得'),
      Text.blue(`§a+2% ${fruit.attribute}（0:05）`)
    ])
  }

  const quannengFoodGroups = [
    {
      level: 'I',
      duration: '0:15',
      bonus: 4,
      items: [
        'beef_meatball_soup',
        'borscht',
        'braised_beef_with_potatoes',
        'chicken_and_mushroom_stew',
        'cooked_rice',
        'lamb_and_radish_soup',
        'mantou',
        'pork_bone_soup',
        'seafood_miso_soup',
        'sticky_candy',
        'wild_mushroom_rabbit_soup'
      ]
    },
    {
      level: 'I',
      duration: '0:30',
      bonus: 4,
      items: [
        'baozi',
        'braised_beef',
        'chorus_fried_egg',
        'cooked_meatball',
        'dough_drop_soup',
        'dumpling',
        'egg_fried_rice',
        'fish_flavored_shredded_pork',
        'fondant_spider_eye',
        'meat_pie',
        'samsa',
        'scramble_egg_with_tomatoes',
        'shengjian_mantou',
        'slime_ball_meal',
        'sticky_rice_cake',
        'stir_fried_pork_with_peppers',
        'sweet_and_sour_ender_pearls',
        'sweet_and_sour_pork',
        'zongzi'
      ]
    },
    {
      level: 'II',
      duration: '0:30',
      bonus: 8,
      items: [
        'bamboo_tube_rice',
        'beef_noodle',
        'blaze_lamb_chop',
        'braised_beef_rice_bowl',
        'crystal_lamb_chop',
        'donkey_burger',
        'fish_flavored_shredded_pork_rice_bowl',
        'four_joy_meatball_soup',
        'fried_caterpillar',
        'fried_spring_roll',
        'frost_lamb_chop',
        'hot_dry_noodles',
        'hui_noodle',
        'laba_congee',
        'numbing_spicy_chicken',
        'oil_splashed_fish',
        'pan_seared_knight_steak',
        'scramble_egg_with_tomatoes_rice_bowl',
        'spicy_blood_stew',
        'spicy_rabbit_head',
        'stargazy_pie',
        'stir_fried_pork_with_peppers_rice_bowl',
        'stuffed_tiger_skin_pepper',
        'sweet_and_sour_pork_rice_bowl',
        'udon_noodle'
      ]
    },
    {
      level: 'II',
      duration: '1:00',
      bonus: 8,
      items: [
        'braised_pork_ribs',
        'brown_mushroom_pot_soup',
        'caterpillar',
        'cold_roasted_meat',
        'cold_style_sashimi',
        'crimson_fungus_pot_soup',
        'desert_style_sashimi',
        'end_style_sashimi',
        'nether_style_sashimi',
        'red_mushroom_pot_soup',
        'tundra_style_sashimi',
        'warped_fungus_pot_soup'
      ]
    },
    {
      level: 'III',
      duration: '1:00',
      bonus: 12,
      items: [
        'buddha_jumps_over_the_wall',
        'candied_potato',
        'dongpo_pork',
        'fondant_pie',
        'golden_salad',
        'spicy_chicken'
      ]
    }
  ]

  const expansionFoodGroups = [
    {
      level: 'I',
      duration: '0:30',
      bonus: 4,
      items: [
        'kaleidoscope_end:chorus_flower_cake',
        'kaleidoscope_end:chorus_pasta',
        'kaleidoscope_end:dragon_souffle',
        'kaleidoscope_end:mint_chorus_mousse',
        'kaleidoscope_end:mint_sauce_shulker_meat',
        'kaleidoscope_end:raw_ender_dragon_meat',
        'kaleidoscope_end:shulker_shell_stew',
        'kaleidoscope_end:stir_fried_endermite_meat',
        'kaleidoscope_nether:blazing_kabob',
        'kaleidoscope_nether:braised_strider',
        'kaleidoscope_nether:cooked_piglin_meat',
        'kaleidoscope_nether:crimson_magma_stew',
        'kaleidoscope_nether:everlasting_flame_steak',
        'kaleidoscope_nether:forgetfulness_soup',
        'kaleidoscope_nether:ghast_kabob',
        'kaleidoscope_nether:ghast_pasta',
        'kaleidoscope_nether:ghast_pudding',
        'kaleidoscope_nether:glowing_pudding',
        'kaleidoscope_nether:lava_jelly',
        'kaleidoscope_nether:magma_cream_pudding',
        'kaleidoscope_nether:magma_cream_stir_fry',
        'kaleidoscope_nether:mapo_tofu',
        'kaleidoscope_nether:poisonous_ghast_roast',
        'kaleidoscope_nether:roujiamo',
        'kaleidoscope_nether:soul_pepper_stir_fry',
        'kaleidoscope_nether:soul_stir_fry_meat',
        'kaleidoscope_nether:soul_strider_kabob',
        'kaleidoscope_nether:spicy_pot',
        'kaleidoscope_nether:warped_cake',
        'kaleidoscope_nether:wither_bone_soup'
      ]
    },
    {
      level: 'II',
      duration: '0:30',
      bonus: 8,
      items: [
        'kaleidoscope_end:end_salad',
        'kaleidoscope_end:mint_noodle_soup',
        'kaleidoscope_end:mint_sauce_shulker_meat_rice_bowl',
        'kaleidoscope_end:optic_nerve_sweet_and_sour_pork',
        'kaleidoscope_end:stir_fried_endermite_meat_rice_bowl',
        'kaleidoscope_end:stuffed_shulker',
        'kaleidoscope_end:stuffed_void_conch',
        'kaleidoscope_end:void_conch_noodle_soup',
        'kaleidoscope_nether:braised_pork_rice',
        'kaleidoscope_nether:chongqing_noodles',
        'kaleidoscope_nether:couples_lung_slice',
        'kaleidoscope_nether:fruit_platter',
        'kaleidoscope_nether:garlic_oysters',
        'kaleidoscope_nether:golden_kabob',
        'kaleidoscope_nether:ham_yogurt',
        'kaleidoscope_nether:lava_roasted_chicken',
        'kaleidoscope_nether:luosifen',
        'kaleidoscope_nether:magma_cream_stir_fry_rice',
        'kaleidoscope_nether:mapo_tofu_rice',
        'kaleidoscope_nether:roasted_ham',
        'kaleidoscope_nether:soul_glazed_roast',
        'kaleidoscope_nether:soul_lamb_chop',
        'kaleidoscope_nether:soul_stir_fry_meat_rice',
        'kaleidoscope_nether:spicy_hoglin_ramen',
        'kaleidoscope_nether:spicy_pot_rice',
        'kaleidoscope_nether:strider_nether_wart_stew',
        'kaleidoscope_nether:strider_shell_stir_fry',
        'kaleidoscope_nether:warped_hoglin_tenderloin_stew'
      ]
    },
    {
      level: 'II',
      duration: '1:00',
      bonus: 8,
      items: [
        'kaleidoscope_end:dark_dragon_egg_stew',
        'kaleidoscope_end:dragon_breath_chorus_soup',
        'kaleidoscope_end:dragon_egg_ice_cream',
        'kaleidoscope_end:end_caterpillar',
        'kaleidoscope_end:end_caterpillar_sashimi',
        'kaleidoscope_end:fried_dragon_egg',
        'kaleidoscope_end:void_mutton_steak',
        'kaleidoscope_nether:braised_lion_head',
        'kaleidoscope_nether:caramel_nether_caterpillar',
        'kaleidoscope_nether:nether_caterpillar',
        'kaleidoscope_nether:soul_return_rice',
        'kaleidoscope_nether:star_ghast_pasta',
        'kaleidoscope_nether:star_stew',
        'kaleidoscope_nether:star_stew_meat'
      ]
    },
    {
      level: 'III',
      duration: '1:00',
      bonus: 12,
      items: [
        'kaleidoscope_end:dragon_breath_mixed_stew',
        'kaleidoscope_end:dragon_egg_custard',
        'kaleidoscope_nether:caramel_nether_caterpillar_rice',
        'kaleidoscope_nether:corn_carrot_pork_rib_soup',
        'kaleidoscope_nether:golden_roast',
        'kaleidoscope_nether:hoglin_tusk_braised_meat',
        'kaleidoscope_nether:nether_caterpillar_sashimi',
        'kaleidoscope_nether:nether_fries_platter',
        'kaleidoscope_nether:pepper_pork_belly_chicken_soup',
        'kaleidoscope_nether:ruby_steak'
      ]
    },
    {
      level: 'IV',
      duration: '1:00',
      bonus: 16,
      items: [
        'kaleidoscope_end:cooked_ender_dragon_meat',
        'kaleidoscope_end:dark_dragon_steak',
        'kaleidoscope_end:dragon_head_with_sauce',
        'kaleidoscope_nether:gilded_barbaric_roast'
      ]
    }
  ]

  for (const group of quannengFoodGroups) {
    for (const id of group.items) {
      event.add(`kaleidoscope_cookery:${id}`, [
        Text.gray('食用后会获得'),
        Text.blue(`§a+ 全能 ${group.level}（${group.duration}）`),
        Text.blue(`§9八系法术强度、近战与远程伤害 +${group.bonus}%`)
      ])
    }
  }

  for (const group of expansionFoodGroups) {
    for (const id of group.items) {
      event.add(id, [
        Text.gray('食用后会获得'),
        Text.blue(`§a+ 全能 ${group.level}（${group.duration}）`),
        Text.blue(`§9八系法术强度、近战与远程伤害 +${group.bonus}%`)
      ])
    }
  }
})
