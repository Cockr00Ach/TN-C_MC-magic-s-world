const registry_multiply_base_Effectss = [
    {
        effect: "kubejs:rangers_focus",
        attributes: [
            { attribute: "ranged_weapon:damage", identifier: "rangers_focus_ranged_damage", value: 0.3, operation: "multiply_total" },
            { attribute: "minecraft:generic.movement_speed", identifier: "rangers_focus_movement_speed", value: 0.1, operation: "multiply_total" }
        ]
    },
    {
        effect: "kubejs:multiply_base_fire",
        attributes: [
            { attribute: "spell_power:fire", identifier: "multiply_base_fire", value: 0.3, operation: "multiply_base" }
        ]
    },
    {
        effect: "kubejs:fire1",
        attributes: [
            { attribute: "spell_power:fire", identifier: "kubejs:fire1", value: 0.05, operation: "multiply_base" }
        ]
    },
    {
        effect: "kubejs:zhiguo_air",
        attributes: [
            { attribute: "spell_power:air", identifier: "zhiguo_air", value: 0.02, operation: "multiply_base" }
        ]
    },
    {
        effect: "kubejs:zhiguo_arcane",
        attributes: [
            { attribute: "spell_power:arcane", identifier: "zhiguo_arcane", value: 0.02, operation: "multiply_base" }
        ]
    },
    {
        effect: "kubejs:zhiguo_earth",
        attributes: [
            { attribute: "spell_power:earth", identifier: "zhiguo_earth", value: 0.02, operation: "multiply_base" }
        ]
    },
    {
        effect: "kubejs:zhiguo_fire",
        attributes: [
            { attribute: "spell_power:fire", identifier: "zhiguo_fire", value: 0.02, operation: "multiply_base" }
        ]
    },
    {
        effect: "kubejs:zhiguo_frost",
        attributes: [
            { attribute: "spell_power:frost", identifier: "zhiguo_frost", value: 0.02, operation: "multiply_base" }
        ]
    },
    {
        effect: "kubejs:zhiguo_healing",
        attributes: [
            { attribute: "spell_power:healing", identifier: "zhiguo_healing", value: 0.02, operation: "multiply_base" }
        ]
    },
    {
        effect: "kubejs:zhiguo_lightning",
        attributes: [
            { attribute: "spell_power:lightning", identifier: "zhiguo_lightning", value: 0.02, operation: "multiply_base" }
        ]
    },
    // [REMOVED] simplyskills:marksmanship
    // Simply Skills was removed from the pack, so this puffish effect no longer exists.
    // Re-create it as a "kubejs:" effect if you still want this ranged damage bonus.
    {
        effect: "kubejs:zhiguo_water",
        attributes: [
            { attribute: "spell_power:water", identifier: "zhiguo_water", value: 0.02, operation: "multiply_base" }
        ]
    },
    {
        effect: "kubejs:zhiguo_wuli",
        attributes: [
            { attribute: "minecraft:generic.attack_damage", identifier: "zhiguo_wuli", value: 0.02, operation: "multiply_base" }
        ]
    },
    {
        effect: "kubejs:wuli_beer",
        attributes: [
            { attribute: "minecraft:generic.attack_damage", identifier: "wuli_beer_attack_damage", value: 0.10, operation: "multiply_base" }
        ]
    },
    {
        effect: "kubejs:zhiguo_youxia",
        attributes: [
            { attribute: "ranged_weapon:damage", identifier: "zhiguo_youxia", value: 0.02, operation: "multiply_base" }
        ]
    },
    // [REMOVED] simplyskills:magic_circle
    // Same reason as marksmanship above. This one granted +0.3 multiply_base to every
    // spell_power element; rebuild it as a "kubejs:" effect if you want it back.
    {
        effect: "kubejs:quanneng",
        attributes: [
  { attribute: "spell_power:arcane", identifier: "quanneng_arcane", value: 0.04, operation: "multiply_base" },
  { attribute: "spell_power:fire", identifier: "quanneng_fire", value: 0.04, operation: "multiply_base" },
  { attribute: "spell_power:frost", identifier: "quanneng_frost", value: 0.04, operation: "multiply_base" },
  { attribute: "spell_power:healing", identifier: "quanneng_healing", value: 0.04, operation: "multiply_base" },
  { attribute: "spell_power:water", identifier: "quanneng_water", value: 0.04, operation: "multiply_base" },
  { attribute: "spell_power:earth", identifier: "quanneng_earth", value: 0.04, operation: "multiply_base" },
  { attribute: "spell_power:lightning", identifier: "quanneng_lightning", value: 0.04, operation: "multiply_base" },
  { attribute: "spell_power:air", identifier: "quanneng_air", value: 0.04, operation: "multiply_base" },
  { attribute: "minecraft:generic.attack_damage", identifier: "quanneng_attack", value: 0.04, operation: "multiply_base" },
  { attribute: "ranged_weapon:damage", identifier: "quanneng_ranged", value: 0.04, operation: "multiply_base" }
        ]
      },
      {
        effect: "kubejs:nengliyazhi",
        attributes: [
  { attribute: "spell_power:arcane", identifier: "nengliyazhi_arcane", value: -0.15, operation: "multiply_base" },
  { attribute: "spell_power:fire", identifier: "nengliyazhi_fire", value: -0.15, operation: "multiply_base" },
  { attribute: "spell_power:frost", identifier: "nengliyazhi_frost", value: -0.15, operation: "multiply_base" },
  { attribute: "spell_power:healing", identifier: "nengliyazhi_healing", value: -0.15, operation: "multiply_base" },
  { attribute: "spell_power:water", identifier: "nengliyazhi_water", value: -0.15, operation: "multiply_base" },
  { attribute: "spell_power:earth", identifier: "nengliyazhi_earth", value: -0.15, operation: "multiply_base" },
  { attribute: "spell_power:lightning", identifier: "nengliyazhi_lightning", value: -0.15, operation: "multiply_base" },
  { attribute: "spell_power:air", identifier: "nengliyazhi_air", value: -0.15, operation: "multiply_base" },
  { attribute: "minecraft:generic.attack_damage", identifier: "nengliyazhi_attack", value: -0.15, operation: "multiply_base" },
  { attribute: "ranged_weapon:damage", identifier: "nengliyazhi_ranged", value: -0.15, operation: "multiply_base" },
  { attribute: "minecraft:generic.max_health", identifier: "nengliyazhi_hp", value: -0.15, operation: "multiply_base" }
        ]
      },
      {
        effect: "kubejs:yuansukongzhi",
        attributes: [
  { attribute: "spell_power:arcane", identifier: "yuansukongzhi_arcane", value: -0.5, operation: "multiply_base" },
  { attribute: "spell_power:fire", identifier: "yuansukongzhi_fire", value: -0.5, operation: "multiply_base" },
  { attribute: "spell_power:frost", identifier: "yuansukongzhi_frost", value: -0.5, operation: "multiply_base" },
  { attribute: "spell_power:healing", identifier: "yuansukongzhi_healing", value: -0.5, operation: "multiply_base" },
  { attribute: "spell_power:water", identifier: "yuansukongzhi_water", value: -0.5, operation: "multiply_base" },
  { attribute: "spell_power:earth", identifier: "yuansukongzhi_earth", value: -0.5, operation: "multiply_base" },
  { attribute: "spell_power:lightning", identifier: "yuansukongzhi_lightning", value: -0.5, operation: "multiply_base" },
  { attribute: "spell_power:air", identifier: "yuansukongzhi_air", value: -0.5, operation: "multiply_base" },
  { attribute: "minecraft:generic.attack_damage", identifier: "yuansukongzhi_attack", value: -0.5, operation: "multiply_base" },
  { attribute: "ranged_weapon:damage", identifier: "yuansukongzhi_ranged", value: -0.5, operation: "multiply_base" },
  { attribute: "minecraft:generic.max_health", identifier: "yuansuyazhi_hp", value: -0.5, operation: "multiply_base" }
        ]
      },
      {
        effect: "kubejs:lightning2",
        attributes: [
            { attribute: "spell_power:lightning", identifier: "multiply_base_fire", value: 0.2, operation: "multiply_base" },
            { attribute: "spell_power:air", identifier: "multiply_base_fire", value: 0.2, operation: "multiply_base" },
            { attribute: "spell_power:critical_damage", identifier: "multiply_base_fire", value: 0.15, operation: "multiply_base" }
        ]
    },
]

StartupEvents.registry("minecraft:mob_effect", event => {
    registry_multiply_base_Effectss.forEach(Effectss => {
        let { effect, attributes } = Effectss
        let builder = event.create(effect)
        attributes.forEach(attributed => {
            let { attribute, identifier, value, operation } = attributed
            builder.modifyAttribute(attribute, identifier, value, operation).beneficial()
        })
    })
})


const FoodEffect = [
    //物品id，效果id，持续时间，等级，获得buff概率
    { food: "kubejs:air_zhiguo", mobEffectId: "kubejs:zhiguo_air", duration: 20 * 5, amplifier: 0, probability: 1.0 },
    { food: "kubejs:arcane_zhiguo", mobEffectId: "kubejs:zhiguo_arcane", duration: 20 * 5, amplifier: 0, probability: 1.0 },
    { food: "kubejs:earth_zhiguo", mobEffectId: "kubejs:zhiguo_earth", duration: 20 * 5, amplifier: 0, probability: 1.0 },
    { food: "kubejs:fire_zhiguo", mobEffectId: "kubejs:zhiguo_fire", duration: 20 * 5, amplifier: 0, probability: 1.0 },
    { food: "kubejs:frost_zhiguo", mobEffectId: "kubejs:zhiguo_frost", duration: 20 * 5, amplifier: 0, probability: 1.0 },
    { food: "kubejs:healing_zhiguo", mobEffectId: "kubejs:zhiguo_healing", duration: 20 * 5, amplifier: 0, probability: 1.0 },
    { food: "kubejs:lightning_zhiguo", mobEffectId: "kubejs:zhiguo_lightning", duration: 20 * 5, amplifier: 0, probability: 1.0 },
    { food: "kubejs:water_zhiguo", mobEffectId: "kubejs:zhiguo_water", duration: 20 * 5, amplifier: 0, probability: 1.0 },
    { food: "kubejs:wuli_zhiguo", mobEffectId: "kubejs:zhiguo_wuli", duration: 20 * 5, amplifier: 0, probability: 1.0 },
    { food: "kubejs:youxia_zhiguo", mobEffectId: "kubejs:zhiguo_youxia", duration: 20 * 5, amplifier: 0, probability: 1.0 },
    { food: "farmersdelight:glow_berry_custard", mobEffectId: "kubejs:quanneng", duration: 20 * 15, amplifier: 0, probability: 1.0 },
    { food: "farmersdelight:salmon_roll", mobEffectId: "kubejs:quanneng", duration: 20 * 15, amplifier: 0, probability: 1.0 },
    { food: "farmersdelight:cod_roll", mobEffectId: "kubejs:quanneng", duration: 20 * 15, amplifier: 0, probability: 1.0 },
    { food: "farmersdelight:kelp_roll_slice", mobEffectId: "kubejs:quanneng", duration: 20 * 15, amplifier: 0, probability: 1.0 },
    { food: "farmersdelight:mixed_salad", mobEffectId: "kubejs:quanneng", duration: 20 * 15, amplifier: 0, probability: 1.0 },
    { food: "farmersdelight:cooked_rice", mobEffectId: "kubejs:quanneng", duration: 20 * 15, amplifier: 0, probability: 1.0 },
    { food: "farmersdelight:fruit_salad", mobEffectId: "kubejs:quanneng", duration: 20 * 15, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_cookery:pork_bone_soup", mobEffectId: "kubejs:quanneng", duration: 20 * 15, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_cookery:seafood_miso_soup", mobEffectId: "kubejs:quanneng", duration: 20 * 15, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_cookery:borscht", mobEffectId: "kubejs:quanneng", duration: 20 * 15, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_cookery:lamb_and_radish_soup", mobEffectId: "kubejs:quanneng", duration: 20 * 15, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_cookery:mantou", mobEffectId: "kubejs:quanneng", duration: 20 * 15, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_cookery:cooked_rice", mobEffectId: "kubejs:quanneng", duration: 20 * 15, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_cookery:sticky_candy", mobEffectId: "kubejs:quanneng", duration: 20 * 15, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_cookery:beef_meatball_soup", mobEffectId: "kubejs:quanneng", duration: 20 * 15, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_cookery:braised_beef_with_potatoes", mobEffectId: "kubejs:quanneng", duration: 20 * 15, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_cookery:chicken_and_mushroom_stew", mobEffectId: "kubejs:quanneng", duration: 20 * 15, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_cookery:wild_mushroom_rabbit_soup", mobEffectId: "kubejs:quanneng", duration: 20 * 15, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_cookery:scramble_egg_with_tomatoes", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_cookery:chorus_fried_egg", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_cookery:braised_beef", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_cookery:slime_ball_meal", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_cookery:stir_fried_pork_with_peppers", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_cookery:sweet_and_sour_pork", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_cookery:fish_flavored_shredded_pork", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_cookery:sweet_and_sour_ender_pearls", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "farmersdelight:bone_broth", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "farmersdelight:egg_sandwich", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "farmersdelight:dumplings", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "farmersdelight:barbecue_stick", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_cookery:baozi", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_cookery:sticky_rice_cake", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_cookery:egg_fried_rice", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_cookery:fondant_spider_eye", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_cookery:dough_drop_soup", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_cookery:dumpling", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_cookery:samsa", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_cookery:cooked_meatball", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_cookery:zongzi", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_cookery:shengjian_mantou", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_cookery:meat_pie", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "farmersdelight:baked_cod_stew", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "farmersdelight:roasted_mutton_chops", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "farmersdelight:noodle_soup", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "farmersdelight:pumpkin_soup", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "farmersdelight:roast_chicken", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "farmersdelight:honey_glazed_ham", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "farmersdelight:shepherds_pie", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "farmersdelight:vegetable_noodles", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "farmersdelight:gleaming_salad", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "farmersdelight:stuffed_pumpkin", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "farmersdelight:grilled_salmon", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "farmersdelight:squid_ink_pasta", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:scramble_egg_with_tomatoes_rice_bowl", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:braised_beef_rice_bowl", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:beef_noodle", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:stir_fried_pork_with_peppers_rice_bowl", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:hot_dry_noodles", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:sweet_and_sour_pork_rice_bowl", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:udon_noodle", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:hui_noodle", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:fish_flavored_shredded_pork_rice_bowl", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:stuffed_tiger_skin_pepper", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:numbing_spicy_chicken", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:blaze_lamb_chop", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:frost_lamb_chop", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:spicy_rabbit_head", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:spicy_blood_stew", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:crystal_lamb_chop", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:four_joy_meatball_soup", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:pan_seared_knight_steak", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:stargazy_pie", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:oil_splashed_fish", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:fried_spring_roll", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "farmersdelight:fried_rice", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "farmersdelight:kelp_roll", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "farmersdelight:chicken_soup", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "farmersdelight:mushroom_rice", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "farmersdelight:steak_and_potatoes", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "farmersdelight:beef_stew", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "farmersdelight:pasta_with_meatballs", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "farmersdelight:vegetable_soup", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "farmersdelight:pasta_with_mutton_chop", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "farmersdelight:onion_soup", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "farmersdelight:fish_stew", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:laba_congee", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:donkey_burger", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:fried_caterpillar", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "farmersdelight:hamburger", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "farmersdelight:chicken_sandwich", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "farmersdelight:bacon_and_eggs", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "farmersdelight:bacon_sandwich", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "farmersdelight:ratatouille", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "farmersdelight:stuffed_potato", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "farmersdelight:smoked_ham", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "farmersdelight:mutton_wrap", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:bamboo_tube_rice", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:crimson_fungus_pot_soup", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:warped_fungus_pot_soup", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:red_mushroom_pot_soup", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:caterpillar", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:brown_mushroom_pot_soup", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:cold_style_sashimi", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:braised_pork_ribs", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:cold_roasted_meat", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:end_style_sashimi", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:desert_style_sashimi", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:tundra_style_sashimi", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:nether_style_sashimi", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_cookery:candied_potato", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 2, probability: 1.0 },
    { food: "kaleidoscope_cookery:dongpo_pork", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 2, probability: 1.0 },
    { food: "kaleidoscope_cookery:fondant_pie", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 2, probability: 1.0 },
    { food: "kaleidoscope_cookery:buddha_jumps_over_the_wall", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 2, probability: 1.0 },
    { food: "kaleidoscope_cookery:golden_salad", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 2, probability: 1.0 },
    { food: "kaleidoscope_cookery:spicy_chicken", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 2, probability: 1.0 },
    // 森罗物语：末地／下界（8-9 点：全能 I，30 秒）
    { food: "kaleidoscope_end:chorus_flower_cake", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_end:chorus_pasta", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_end:dragon_souffle", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_end:mint_chorus_mousse", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_end:mint_sauce_shulker_meat", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_end:raw_ender_dragon_meat", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_end:shulker_shell_stew", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_end:stir_fried_endermite_meat", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_nether:blazing_kabob", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_nether:braised_strider", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_nether:cooked_piglin_meat", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_nether:crimson_magma_stew", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_nether:everlasting_flame_steak", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_nether:forgetfulness_soup", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_nether:ghast_kabob", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_nether:ghast_pasta", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_nether:ghast_pudding", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_nether:glowing_pudding", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_nether:lava_jelly", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_nether:magma_cream_pudding", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_nether:magma_cream_stir_fry", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_nether:mapo_tofu", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_nether:poisonous_ghast_roast", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_nether:roujiamo", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_nether:soul_pepper_stir_fry", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_nether:soul_stir_fry_meat", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_nether:soul_strider_kabob", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_nether:spicy_pot", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_nether:warped_cake", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "kaleidoscope_nether:wither_bone_soup", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },

    // 10／12／13／14 点：全能 II，30 秒
    { food: "kaleidoscope_end:end_salad", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_end:mint_noodle_soup", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_end:mint_sauce_shulker_meat_rice_bowl", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_end:optic_nerve_sweet_and_sour_pork", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_end:stir_fried_endermite_meat_rice_bowl", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_end:stuffed_shulker", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_end:stuffed_void_conch", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_end:void_conch_noodle_soup", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_nether:braised_pork_rice", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_nether:chongqing_noodles", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_nether:couples_lung_slice", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_nether:fruit_platter", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_nether:garlic_oysters", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_nether:golden_kabob", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_nether:ham_yogurt", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_nether:lava_roasted_chicken", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_nether:luosifen", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_nether:magma_cream_stir_fry_rice", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_nether:mapo_tofu_rice", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_nether:roasted_ham", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_nether:soul_glazed_roast", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_nether:soul_lamb_chop", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_nether:soul_stir_fry_meat_rice", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_nether:spicy_hoglin_ramen", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_nether:spicy_pot_rice", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_nether:strider_nether_wart_stew", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_nether:strider_shell_stir_fry", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_nether:warped_hoglin_tenderloin_stew", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },

    // 16／18 点：全能 II，1 分钟
    { food: "kaleidoscope_end:dark_dragon_egg_stew", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_end:dragon_breath_chorus_soup", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_end:dragon_egg_ice_cream", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_end:end_caterpillar", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_end:end_caterpillar_sashimi", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_end:fried_dragon_egg", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_end:void_mutton_steak", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_nether:braised_lion_head", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_nether:caramel_nether_caterpillar", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_nether:nether_caterpillar", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_nether:soul_return_rice", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_nether:star_ghast_pasta", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_nether:star_stew", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 1, probability: 1.0 },
    { food: "kaleidoscope_nether:star_stew_meat", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 1, probability: 1.0 },

    // 19／20 点：全能 III，1 分钟
    { food: "kaleidoscope_end:dragon_breath_mixed_stew", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 2, probability: 1.0 },
    { food: "kaleidoscope_end:dragon_egg_custard", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 2, probability: 1.0 },
    { food: "kaleidoscope_nether:caramel_nether_caterpillar_rice", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 2, probability: 1.0 },
    { food: "kaleidoscope_nether:corn_carrot_pork_rib_soup", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 2, probability: 1.0 },
    { food: "kaleidoscope_nether:golden_roast", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 2, probability: 1.0 },
    { food: "kaleidoscope_nether:hoglin_tusk_braised_meat", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 2, probability: 1.0 },
    { food: "kaleidoscope_nether:nether_caterpillar_sashimi", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 2, probability: 1.0 },
    { food: "kaleidoscope_nether:nether_fries_platter", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 2, probability: 1.0 },
    { food: "kaleidoscope_nether:pepper_pork_belly_chicken_soup", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 2, probability: 1.0 },
    { food: "kaleidoscope_nether:ruby_steak", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 2, probability: 1.0 },

    // 24 点：全能 IV，1 分钟
    { food: "kaleidoscope_end:cooked_ender_dragon_meat", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 3, probability: 1.0 },
    { food: "kaleidoscope_end:dark_dragon_steak", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 3, probability: 1.0 },
    { food: "kaleidoscope_end:dragon_head_with_sauce", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 3, probability: 1.0 },
    { food: "kaleidoscope_nether:gilded_barbaric_roast", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 3, probability: 1.0 },

    // “新建文件夹 (3)”6 Mod 食物（8／9 点：全能 I，30 秒）
    { food: "bosses_delight:anima_popsicle", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "bosses_delight:crystal_cup_cake", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "bosses_delight:nectar_jelly", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "fruitsdelight:jelly_bread", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "fruitsdelight:mango_salad", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "fruitsdelight:pear_with_rock_sugar", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "legendary_delicacies:bloody_steamed_bun", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "legendary_delicacies:cloud_bread", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "legendary_delicacies:cloud_pancake", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "legendary_delicacies:cumulonimbus_bread", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "legendary_delicacies:cumulonimbus_pancake", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "legendary_delicacies:flying_food", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "fruitsdelight:mangosteen_cake", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "legendary_delicacies:haunted_soup", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "twilightdelight:chocolate_wafer", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "twilightdelight:hydra_piece", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 0, probability: 1.0 },

    // 10／12／14 点：全能 II，30 秒
    { food: "bosses_delight:obsidian_rune_puree", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "legendary_delicacies:annihilation_eye_soup", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "legendary_delicacies:cloud_sauce_rice_cake", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "legendary_delicacies:cumulonimbus_sauce_rice_cake", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "legendary_delicacies:haunted_stick", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "pineapple_delight:pineapple_old", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "twilightdelight:fried_insect", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "twilightdelight:grilled_ghast", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "twilightdelight:liveroot_venison_noodle_soup", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "twilightdelight:meef_wrap", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "twilightdelight:thousand_plant_stew", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "twilightdelight:torchberry_venison_sandwich", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "bosses_delight:bowl_of_crystal_fruit_cube", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "bosses_delight:bowl_of_lich_smoothies", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "fruitsdelight:fig_chicken_stew", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "fruitsdelight:lychee_chicken", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "fruitsdelight:orange_chicken", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "fruitsdelight:orange_marinated_pork", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "fruitsdelight:pineapple_marinated_pork", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "legendary_delicacies:bloodbloom", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "legendary_delicacies:bowl_of_blood_feast", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "legendary_delicacies:haunted_sandwich", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "pineapple_delight:pineapple_fried_rice", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "twilightdelight:cooked_tomahawk_smeak", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "twilightdelight:experiment_110", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "twilightdelight:glow_venison_rib_with_pasta", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "twilightdelight:liveroot_pork_fried_rice", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "twilightdelight:mushgloom_meef_pasta", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "bosses_delight:bowl_of_ham_above_palm", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "bosses_delight:bowl_of_magic_frozen_noodles", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "fruitsdelight:bowl_of_pineapple_fried_rice", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "legendary_delicacies:annihilation_stew", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "twilightdelight:grilled_tomahawk_smeak", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "twilightdelight:plate_of_meef_wellington", mobEffectId: "kubejs:quanneng", duration: 20 * 30, amplifier: 1, probability: 1.0 },

    // 16／18 点：全能 II，1 分钟
    { food: "bosses_delight:bowl_of_obsidian_glazed_dragon_brain", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 1, probability: 1.0 },
    { food: "bosses_delight:bowl_of_obsidian_glazed_dragon_head", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 1, probability: 1.0 },
    { food: "bosses_delight:obsidian_glazed_dragon_tongue", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 1, probability: 1.0 },
    { food: "legendary_delicacies:plate_of_haunted_knight_steak", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 1, probability: 1.0 },
    { food: "twilightdelight:plate_of_lily_chicken", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 1, probability: 1.0 },
    { food: "twilightdelight:hydra_burger", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 1, probability: 1.0 },

    // 20 点：全能 III，1 分钟
    { food: "bosses_delight:bosses_hodgepodge", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 2, probability: 1.0 },
    { food: "legendary_delicacies:annihilation_bloom_eye", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 2, probability: 1.0 },
    { food: "twilightdelight:plate_of_fiery_snakes", mobEffectId: "kubejs:quanneng", duration: 20 * 60, amplifier: 2, probability: 1.0 },
    { food: "twilightforest:hydra_chop", mobEffectId: "spell_power:fire", duration: 20 * 60, amplifier: 0, probability: 1.0 },
    { food: "cavedelight:tectonic_cheesecake", mobEffectId: "kubejs:fire1", duration: 20 * 60, amplifier: 0, probability: 1.0 },
    { food: "kubejs:beer_arcane", mobEffectId: "spell_power:arcane", duration: 20 * 180, amplifier: 0, probability: 1.0 },
    { food: "kubejs:beer_fire", mobEffectId: "spell_power:fire", duration: 20 * 180, amplifier: 0, probability: 1.0 },
    { food: "kubejs:beer_frost", mobEffectId: "spell_power:frost", duration: 20 * 180, amplifier: 0, probability: 1.0 },
    { food: "kubejs:beer_healing", mobEffectId: "spell_power:healing", duration: 20 * 180, amplifier: 0, probability: 1.0 },
    { food: "kubejs:beer_water", mobEffectId: "spell_power:water", duration: 20 * 180, amplifier: 0, probability: 1.0 },
    { food: "kubejs:beer_earth", mobEffectId: "spell_power:earth", duration: 20 * 180, amplifier: 0, probability: 1.0 },
    { food: "kubejs:beer_lightning", mobEffectId: "spell_power:lightning", duration: 20 * 180, amplifier: 0, probability: 1.0 },
    { food: "kubejs:beer_air", mobEffectId: "spell_power:air", duration: 20 * 180, amplifier: 0, probability: 1.0 },
    { food: "kubejs:beer_youxia", mobEffectId: "ranged_weapon:damage", duration: 20 * 180, amplifier: 0, probability: 1.0 },
    { food: "kubejs:beer_wuli", mobEffectId: "kubejs:wuli_beer", duration: 20 * 180, amplifier: 0, probability: 1.0 },
    { food: "kubejs:beer_arcane", mobEffectId: "spell_power:haste", duration: 20 * 180, amplifier: 0, probability: 1.0 },
    { food: "kubejs:beer_fire", mobEffectId: "spell_power:critical_chance", duration: 20 * 180, amplifier: 0, probability: 1.0 },
    { food: "kubejs:beer_frost", mobEffectId: "spell_power:critical_damage", duration: 20 * 180, amplifier: 0, probability: 1.0 },
    { food: "kubejs:beer_healing", mobEffectId: "spell_power:haste", duration: 20 * 180, amplifier: 0, probability: 1.0 },
    { food: "kubejs:beer_water", mobEffectId: "spell_power:haste", duration: 20 * 180, amplifier: 0, probability: 1.0 },
    { food: "kubejs:beer_earth", mobEffectId: "spell_power:critical_chance", duration: 20 * 180, amplifier: 0, probability: 1.0 },
    { food: "kubejs:beer_lightning", mobEffectId: "spell_power:critical_damage", duration: 20 * 180, amplifier: 0, probability: 1.0 },
    { food: "kubejs:beer_air", mobEffectId: "spell_power:critical_damage", duration: 20 * 180, amplifier: 0, probability: 1.0 },
    { food: "twilightforest:meef_stroganoff", mobEffectId: "minecraft:strength", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "culturaldelights:tropical_roll", mobEffectId: "spell_power:water", duration: 20 * 20, amplifier: 0, probability: 1.0 },
    { food: "culturaldelights:rice_ball", mobEffectId: "spell_power:water", duration: 20 * 20, amplifier: 0, probability: 1.0 },
    { food: "culturaldelights:calamari_roll", mobEffectId: "spell_power:water", duration: 20 * 20, amplifier: 0, probability: 1.0 },
    { food: "mynethersdelight:spicy_skewer", mobEffectId: "spell_power:fire", duration: 20 * 10, amplifier: 0, probability: 1.0 },
    { food: "mynethersdelight:chilidog", mobEffectId: "spell_power:fire", duration: 20 * 20, amplifier: 0, probability: 1.0 },
    { food: "mynethersdelight:hot_wings", mobEffectId: "spell_power:fire", duration: 20 * 10, amplifier: 0, probability: 1.0 },
    { food: "mynethersdelight:hot_wings_bucket", mobEffectId: "spell_power:fire", duration: 20 * 30, amplifier: 1, probability: 1.0 },
    { food: "mynethersdelight:spicy_curry", mobEffectId: "spell_power:fire", duration: 20 * 60, amplifier: 0, probability: 1.0 },
    { food: "twilightforest:hydra_chop", mobEffectId: "spell_power:fire", duration: 20 * 10, amplifier: 0, probability: 1.0 },
    { food: "twilightdelight:glacier_ice_tea", mobEffectId: "spell_power:frost", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "fruitsdelight:kiwi_popsicle", mobEffectId: "spell_power:frost", duration: 20 * 10, amplifier: 0, probability: 1.0 },
    { food: "fruitsdelight:hamimelon_popsicle", mobEffectId: "spell_power:frost", duration: 20 * 10, amplifier: 0, probability: 1.0 },
    { food: "fruitsdelight:hamimelon_shaved_ice", mobEffectId: "spell_power:frost", duration: 20 * 10, amplifier: 0, probability: 1.0 },
    { food: "iceandfire:fire_dragon_flesh", mobEffectId: "spell_power:fire", duration: 20 * 10, amplifier: 0, probability: 1.0 },
    { food: "iceandfire:ice_dragon_flesh", mobEffectId: "spell_power:frost", duration: 20 * 10, amplifier: 0, probability: 1.0 },
    { food: "iceandfire:lightning_dragon_flesh", mobEffectId: "spell_power:lightning", duration: 20 * 10, amplifier: 0, probability: 1.0 },
    { food: "alexscaves:deep_sea_sushi_roll", mobEffectId: "spell_power:water", duration: 20 * 30, amplifier: 0, probability: 1.0 },
    { food: "fruitsdelight:bayberry_soup", mobEffectId: "spell_power:arcane", duration: 20 * 20, amplifier: 0, probability: 1.0 },
    { food: "fruitsdelight:mangosteen_tea", mobEffectId: "spell_power:earth", duration: 20 * 20, amplifier: 0, probability: 1.0 },
    { food: "fruitsdelight:lychee_cherry_tea", mobEffectId: "spell_power:haste", duration: 20 * 20, amplifier: 0, probability: 1.0 },
    { food: "fruitsdelight:peach_tea", mobEffectId: "minecraft:strength", duration: 20 * 20, amplifier: 0, probability: 1.0 },
    { food: "fruitsdelight:pear_juice", mobEffectId: "ranged_weapon:damage", duration: 20 * 20, amplifier: 0, probability: 1.0 },
    { food: "cavedelight:plate_of_grotto_burnt_ends", mobEffectId: "spell_power:haste", duration: 20 * 40, amplifier: 1, probability: 1.0 },
    { food: "cavedelight:plate_of_roasted_dino_chop", mobEffectId: "ranged_weapon:damage", duration: 20 * 20, amplifier: 0, probability: 1.0 },
    { food: "cavedelight:plate_of_tail_cut", mobEffectId: "spell_power:critical_damage", duration: 20 * 60, amplifier: 2, probability: 1.0 },

    { food: "farmersdelight:cabbage_rolls", mobEffectId: "ranged_weapon:haste", duration: 20 * 60, amplifier: 1, probability: 1.0 }
]
ItemEvents.modification(event => {
    FoodEffect.forEach(config => {
        let { food, mobEffectId, duration, amplifier, probability } = config
        event.modify(Item.of(food), item => {
            item.setFoodProperties(FoodBuilder => {
                FoodBuilder.effect(mobEffectId, duration, amplifier, probability)
            })
        })
    })
})
