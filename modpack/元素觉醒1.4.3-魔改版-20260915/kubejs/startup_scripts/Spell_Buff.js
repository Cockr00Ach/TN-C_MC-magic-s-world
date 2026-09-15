const weapen = [
  { id: 'cataclysm:ignitium_helmet', attribute: "spell_power:fire", mber: 0.4, operation: "multiply_total" },
  { id: 'cataclysm:ignitium_helmet', attribute: "minecraft:generic.attack_damage", mber: 0.1, operation: "multiply_total" },
  { id: 'cataclysm:ignitium_helmet', attribute: "minecraft:generic.max_health", mber: 0.1, operation: "multiply_total" },
  { id: 'cataclysm:ignitium_helmet', attribute: "minecraft:generic.armor", mber: 5.0, operation: "addition" },

  { id: 'cataclysm:ignitium_elytra_chestplate', attribute: "spell_power:fire", mber: 0.4, operation: "multiply_total" },
  { id: 'cataclysm:ignitium_elytra_chestplate', attribute: "minecraft:generic.attack_damage", mber: 0.1, operation: "multiply_total" },
  { id: 'cataclysm:ignitium_elytra_chestplate', attribute: "minecraft:generic.max_health", mber: 0.1, operation: "multiply_total" },

  { id: 'cataclysm:ignitium_chestplate', attribute: "spell_power:fire", mber: 0.4, operation: "multiply_total" },
  { id: 'cataclysm:ignitium_chestplate', attribute: "minecraft:generic.attack_damage", mber: 0.1, operation: "multiply_total" },
  { id: 'cataclysm:ignitium_chestplate', attribute: "minecraft:generic.max_health", mber: 0.1, operation: "multiply_total" },
  { id: 'cataclysm:ignitium_chestplate', attribute: "minecraft:generic.armor", mber: 17.0, operation: "addition" },

  { id: 'cataclysm:ignitium_leggings', attribute: "spell_power:fire", mber: 0.4, operation: "multiply_total" },
  { id: 'cataclysm:ignitium_leggings', attribute: "minecraft:generic.attack_damage", mber: 0.1, operation: "multiply_total" },
  { id: 'cataclysm:ignitium_leggings', attribute: "minecraft:generic.max_health", mber: 0.1, operation: "multiply_total" },
  { id: 'cataclysm:ignitium_leggings', attribute: "minecraft:generic.armor", mber: 12.0, operation: "addition" },

  { id: 'cataclysm:ignitium_boots', attribute: "spell_power:fire", mber: 0.4, operation: "multiply_total" },
  { id: 'cataclysm:ignitium_boots', attribute: "minecraft:generic.attack_damage", mber: 0.1, operation: "multiply_total" },
  { id: 'cataclysm:ignitium_boots', attribute: "minecraft:generic.max_health", mber: 0.1, operation: "multiply_total" },
  { id: 'cataclysm:ignitium_boots', attribute: "minecraft:generic.armor", mber: 5.0, operation: "addition" },

  { id: 'cataclysm:cursium_helmet', attribute: "spell_power:frost", mber: 0.4, operation: "multiply_total" },
  { id: 'cataclysm:cursium_helmet', attribute: "minecraft:generic.attack_damage", mber: 0.1, operation: "multiply_total" },
  { id: 'cataclysm:cursium_helmet', attribute: "minecraft:generic.max_health", mber: 0.1, operation: "multiply_total" },
  { id: 'cataclysm:cursium_helmet', attribute: "minecraft:generic.armor", mber: 6.0, operation: "addition" },

  { id: 'cataclysm:cursium_chestplate', attribute: "spell_power:frost", mber: 0.4, operation: "multiply_total" },
  { id: 'cataclysm:cursium_chestplate', attribute: "minecraft:generic.attack_damage", mber: 0.1, operation: "multiply_total" },
  { id: 'cataclysm:cursium_chestplate', attribute: "minecraft:generic.max_health", mber: 0.1, operation: "multiply_total" },
  { id: 'cataclysm:cursium_chestplate', attribute: "minecraft:generic.armor", mber: 18.0, operation: "addition" },

  { id: 'cataclysm:cursium_leggings', attribute: "spell_power:frost", mber: 0.4, operation: "multiply_total" },
  { id: 'cataclysm:cursium_leggings', attribute: "minecraft:generic.attack_damage", mber: 0.1, operation: "multiply_total" },
  { id: 'cataclysm:cursium_leggings', attribute: "minecraft:generic.max_health", mber: 0.1, operation: "multiply_total" },
  { id: 'cataclysm:cursium_leggings', attribute: "minecraft:generic.armor", mber: 13.0, operation: "addition" },

  { id: 'cataclysm:cursium_boots', attribute: "spell_power:frost", mber: 0.4, operation: "multiply_total" },
  { id: 'cataclysm:cursium_boots', attribute: "minecraft:generic.attack_damage", mber: 0.1, operation: "multiply_total" },
  { id: 'cataclysm:cursium_boots', attribute: "minecraft:generic.max_health", mber: 0.1, operation: "multiply_total" },
  { id: 'cataclysm:cursium_boots', attribute: "minecraft:generic.armor", mber: 6.0, operation: "addition" },

  { id: 'blackgoldalliance:the_black_gold_marshal_helmet', attribute: "spell_power:arcane", mber: 0.4, operation: "multiply_total" },
  { id: 'blackgoldalliance:the_black_gold_marshal_helmet', attribute: "minecraft:generic.attack_damage", mber: 0.1, operation: "multiply_total" },
  { id: 'blackgoldalliance:the_black_gold_marshal_helmet', attribute: "minecraft:generic.max_health", mber: 0.1, operation: "multiply_total" },
  { id: 'blackgoldalliance:the_black_gold_marshal_helmet', attribute: "minecraft:generic.armor", mber: 7.0, operation: "addition" },

  { id: 'blackgoldalliance:the_black_gold_marshal_chestplate', attribute: "spell_power:arcane", mber: 0.4, operation: "multiply_total" },
  { id: 'blackgoldalliance:the_black_gold_marshal_chestplate', attribute: "minecraft:generic.attack_damage", mber: 0.1, operation: "multiply_total" },
  { id: 'blackgoldalliance:the_black_gold_marshal_chestplate', attribute: "minecraft:generic.max_health", mber: 0.1, operation: "multiply_total" },
  { id: 'blackgoldalliance:the_black_gold_marshal_chestplate', attribute: "minecraft:generic.armor", mber: 20.0, operation: "addition" },

  { id: 'blackgoldalliance:the_black_gold_marshal_leggings', attribute: "spell_power:arcane", mber: 0.4, operation: "multiply_total" },
  { id: 'blackgoldalliance:the_black_gold_marshal_leggings', attribute: "minecraft:generic.attack_damage", mber: 0.1, operation: "multiply_total" },
  { id: 'blackgoldalliance:the_black_gold_marshal_leggings', attribute: "minecraft:generic.max_health", mber: 0.1, operation: "multiply_total" },
  { id: 'blackgoldalliance:the_black_gold_marshal_leggings', attribute: "minecraft:generic.armor", mber: 14.0, operation: "addition" },

  { id: 'blackgoldalliance:the_black_gold_marshal_boots', attribute: "spell_power:arcane", mber: 0.4, operation: "multiply_total" },
  { id: 'blackgoldalliance:the_black_gold_marshal_boots', attribute: "minecraft:generic.attack_damage", mber: 0.1, operation: "multiply_total" },
  { id: 'blackgoldalliance:the_black_gold_marshal_boots', attribute: "minecraft:generic.max_health", mber: 0.1, operation: "multiply_total" },
  { id: 'blackgoldalliance:the_black_gold_marshal_boots', attribute: "minecraft:generic.armor", mber: 7.0, operation: "addition" },

  { id: 'armoroftheages:holy_armor_head', attribute: "spell_power:healing", mber: 0.3, operation: "multiply_total" },
  { id: 'armoroftheages:holy_armor_head', attribute: "minecraft:generic.attack_damage", mber: 0.1, operation: "multiply_total" },
  { id: 'armoroftheages:holy_armor_head', attribute: "minecraft:generic.max_health", mber: 0.1, operation: "multiply_total" },
  { id: 'armoroftheages:holy_armor_head', attribute: "minecraft:generic.armor", mber: 0.05, operation: "multiply_total" },

  { id: 'armoroftheages:holy_armor_chest', attribute: "spell_power:healing", mber: 0.3, operation: "multiply_total" },
  { id: 'armoroftheages:holy_armor_chest', attribute: "minecraft:generic.attack_damage", mber: 0.1, operation: "multiply_total" },
  { id: 'armoroftheages:holy_armor_chest', attribute: "minecraft:generic.max_health", mber: 0.1, operation: "multiply_total" },
  { id: 'armoroftheages:holy_armor_chest', attribute: "minecraft:generic.armor", mber: 0.05, operation: "multiply_total" },

  { id: 'armoroftheages:holy_armor_legs', attribute: "spell_power:healing", mber: 0.3, operation: "multiply_total" },
  { id: 'armoroftheages:holy_armor_legs', attribute: "minecraft:generic.attack_damage", mber: 0.1, operation: "multiply_total" },
  { id: 'armoroftheages:holy_armor_legs', attribute: "minecraft:generic.max_health", mber: 0.1, operation: "multiply_total" },
  { id: 'armoroftheages:holy_armor_legs', attribute: "minecraft:generic.armor", mber: 0.05, operation: "multiply_total" },

  { id: 'armoroftheages:holy_armor_feet', attribute: "spell_power:healing", mber: 0.3, operation: "multiply_total" },
  { id: 'armoroftheages:holy_armor_feet', attribute: "minecraft:generic.attack_damage", mber: 0.1, operation: "multiply_total" },
  { id: 'armoroftheages:holy_armor_feet', attribute: "minecraft:generic.max_health", mber: 0.1, operation: "multiply_total" },
  { id: 'armoroftheages:holy_armor_feet', attribute: "minecraft:generic.armor", mber: 0.05, operation: "multiply_total" },

  { id: 'armoroftheages:anubis_armor_head', attribute: "more_rpg_classes:rage_modifier", mber: 0.1, operation: "MULTIPLY_BASE" },
  { id: 'armoroftheages:anubis_armor_head', attribute: "minecraft:generic.attack_damage", mber: 0.15, operation: "MULTIPLY_BASE" },
  { id: 'armoroftheages:anubis_armor_head', attribute: "minecraft:generic.max_health", mber: 0.1, operation: "MULTIPLY_BASE" },
  { id: 'armoroftheages:anubis_armor_head', attribute: "minecraft:generic.attack_speed", mber: 0.05, operation: "MULTIPLY_BASE" },
  
  { id: 'armoroftheages:anubis_armor_chest', attribute: "more_rpg_classes:rage_modifier", mber: 0.1, operation: "MULTIPLY_BASE" },
  { id: 'armoroftheages:anubis_armor_chest', attribute: "minecraft:generic.attack_damage", mber: 0.15, operation: "MULTIPLY_BASE" },
  { id: 'armoroftheages:anubis_armor_chest', attribute: "minecraft:generic.max_health", mber: 0.1, operation: "MULTIPLY_BASE" },
  { id: 'armoroftheages:anubis_armor_chest', attribute: "minecraft:generic.attack_speed", mber: 0.05, operation: "MULTIPLY_BASE" },
  
  { id: 'armoroftheages:anubis_armor_legs', attribute: "more_rpg_classes:rage_modifier", mber: 0.1, operation: "MULTIPLY_BASE" },
  { id: 'armoroftheages:anubis_armor_legs', attribute: "minecraft:generic.attack_damage", mber: 0.15, operation: "MULTIPLY_BASE" },
  { id: 'armoroftheages:anubis_armor_legs', attribute: "minecraft:generic.max_health", mber: 0.1, operation: "MULTIPLY_BASE" },
  { id: 'armoroftheages:anubis_armor_legs', attribute: "minecraft:generic.attack_speed", mber: 0.05, operation: "MULTIPLY_BASE" },
  
  { id: 'armoroftheages:anubis_armor_feet', attribute: "more_rpg_classes:rage_modifier", mber: 0.1, operation: "MULTIPLY_BASE" },
  { id: 'armoroftheages:anubis_armor_feet', attribute: "minecraft:generic.attack_damage", mber: 0.15, operation: "MULTIPLY_BASE" },
  { id: 'armoroftheages:anubis_armor_feet', attribute: "minecraft:generic.max_health", mber: 0.1, operation: "MULTIPLY_BASE" },
  { id: 'armoroftheages:anubis_armor_feet', attribute: "minecraft:generic.attack_speed", mber: 0.05, operation: "MULTIPLY_BASE" },

  { id: 'armoroftheages:iron_plate_armor_head', attribute: "minecraft:generic.attack_damage", mber: 5.0, operation: "addition" },
  { id: 'armoroftheages:iron_plate_armor_head', attribute: "minecraft:generic.attack_speed", mber: 0.03, operation: "multiply_base" },
  { id: 'armoroftheages:iron_plate_armor_head', attribute: "minecraft:generic.knockback_resistance", mber: 0.02, operation: "addition" },

  { id: 'armoroftheages:iron_plate_armor_chest', attribute: "minecraft:generic.attack_damage", mber: 5.0, operation: "addition" },
  { id: 'armoroftheages:iron_plate_armor_chest', attribute: "minecraft:generic.attack_speed", mber: 0.03, operation: "multiply_base" },
  { id: 'armoroftheages:iron_plate_armor_chest', attribute: "minecraft:generic.knockback_resistance", mber: 0.02, operation: "addition" },

  { id: 'armoroftheages:iron_plate_armor_legs', attribute: "minecraft:generic.attack_damage", mber: 5.0, operation: "addition" },
  { id: 'armoroftheages:iron_plate_armor_legs', attribute: "minecraft:generic.attack_speed", mber: 0.03, operation: "multiply_base" },
  { id: 'armoroftheages:iron_plate_armor_legs', attribute: "minecraft:generic.knockback_resistance", mber: 0.02, operation: "addition" },

  { id: 'armoroftheages:iron_plate_armor_feet', attribute: "minecraft:generic.attack_damage", mber: 5.0, operation: "addition" },
  { id: 'armoroftheages:iron_plate_armor_feet', attribute: "minecraft:generic.attack_speed", mber: 0.03, operation: "multiply_base" },
  { id: 'armoroftheages:iron_plate_armor_feet', attribute: "minecraft:generic.knockback_resistance", mber: 0.02, operation: "addition" },

  { id: 'wizards:wizard_robe_head', attribute: "spell_power:arcane", mber: 4.0, operation: "addition" },
  { id: 'wizards:wizard_robe_head', attribute: "spell_power:fire", mber: 4.0, operation: "addition" },
  { id: 'wizards:wizard_robe_head', attribute: "spell_power:frost", mber: 4.0, operation: "addition" },
  { id: 'wizards:wizard_robe_chest', attribute: "spell_power:arcane", mber: 4.0, operation: "addition" },
  { id: 'wizards:wizard_robe_chest', attribute: "spell_power:fire", mber: 4.0, operation: "addition" },
  { id: 'wizards:wizard_robe_chest', attribute: "spell_power:frost", mber: 4.0, operation: "addition" },
  { id: 'wizards:wizard_robe_legs', attribute: "spell_power:arcane", mber: 4.0, operation: "addition" },
  { id: 'wizards:wizard_robe_legs', attribute: "spell_power:fire", mber: 4.0, operation: "addition" },
  { id: 'wizards:wizard_robe_legs', attribute: "spell_power:frost", mber: 4.0, operation: "addition" },
  { id: 'wizards:wizard_robe_feet', attribute: "spell_power:arcane", mber: 4.0, operation: "addition" },
  { id: 'wizards:wizard_robe_feet', attribute: "spell_power:fire", mber: 4.0, operation: "addition" },
  { id: 'wizards:wizard_robe_feet', attribute: "spell_power:frost", mber: 4.0, operation: "addition" },
  { id: 'wizards:arcane_robe_head', attribute: "spell_power:arcane", mber: 4.0, operation: "addition" },
  { id: 'wizards:arcane_robe_chest', attribute: "spell_power:arcane", mber: 4.0, operation: "addition" },
  { id: 'wizards:arcane_robe_legs', attribute: "spell_power:arcane", mber: 4.0, operation: "addition" },
  { id: 'wizards:arcane_robe_feet', attribute: "spell_power:arcane", mber: 4.0, operation: "addition" },
  { id: 'wizards:fire_robe_head', attribute: "spell_power:fire", mber: 4.0, operation: "addition" },
  { id: 'wizards:fire_robe_chest', attribute: "spell_power:fire", mber: 4.0, operation: "addition" },
  { id: 'wizards:fire_robe_legs', attribute: "spell_power:fire", mber: 4.0, operation: "addition" },
  { id: 'wizards:fire_robe_feet', attribute: "spell_power:fire", mber: 4.0, operation: "addition" },
  { id: 'wizards:frost_robe_head', attribute: "spell_power:frost", mber: 4.0, operation: "addition" },
  { id: 'wizards:frost_robe_chest', attribute: "spell_power:frost", mber: 4.0, operation: "addition" },
  { id: 'wizards:frost_robe_legs', attribute: "spell_power:frost", mber: 4.0, operation: "addition" },
  { id: 'wizards:frost_robe_feet', attribute: "spell_power:frost", mber: 4.0, operation: "addition" },
  { id: 'wizards:netherite_arcane_robe_head', attribute: "spell_power:arcane", mber: 7.0, operation: "addition" },
  { id: 'wizards:netherite_arcane_robe_chest', attribute: "spell_power:arcane", mber: 7.0, operation: "addition" },
  { id: 'wizards:netherite_arcane_robe_legs', attribute: "spell_power:arcane", mber: 7.0, operation: "addition" },
  { id: 'wizards:netherite_arcane_robe_feet', attribute: "spell_power:arcane", mber: 7.0, operation: "addition" },
  { id: 'wizards:netherite_fire_robe_head', attribute: "spell_power:fire", mber: 7.0, operation: "addition" },
  { id: 'wizards:netherite_fire_robe_chest', attribute: "spell_power:fire", mber: 7.0, operation: "addition" },
  { id: 'wizards:netherite_fire_robe_legs', attribute: "spell_power:fire", mber: 7.0, operation: "addition" },
  { id: 'wizards:netherite_fire_robe_feet', attribute: "spell_power:fire", mber: 7.0, operation: "addition" },
  { id: 'wizards:netherite_frost_robe_head', attribute: "spell_power:frost", mber: 7.0, operation: "addition" },
  { id: 'wizards:netherite_frost_robe_chest', attribute: "spell_power:frost", mber: 7.0, operation: "addition" },
  { id: 'wizards:netherite_frost_robe_legs', attribute: "spell_power:frost", mber: 7.0, operation: "addition" },
  { id: 'wizards:netherite_frost_robe_feet', attribute: "spell_power:frost", mber: 7.0, operation: "addition" },

  { id: 'elemental_wizards_rpg:elemental_head', attribute: "spell_power:air", mber: 4.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:elemental_head', attribute: "spell_power:earth", mber: 4.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:elemental_head', attribute: "spell_power:water", mber: 4.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:elemental_head', attribute: "spell_power:fire", mber: 4.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:elemental_chest', attribute: "spell_power:air", mber: 4.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:elemental_chest', attribute: "spell_power:earth", mber: 4.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:elemental_chest', attribute: "spell_power:water", mber: 4.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:elemental_chest', attribute: "spell_power:fire", mber: 4.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:elemental_legs', attribute: "spell_power:air", mber: 4.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:elemental_legs', attribute: "spell_power:earth", mber: 4.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:elemental_legs', attribute: "spell_power:water", mber: 4.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:elemental_legs', attribute: "spell_power:fire", mber: 4.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:elemental_feet', attribute: "spell_power:air", mber: 4.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:elemental_feet', attribute: "spell_power:earth", mber: 4.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:elemental_feet', attribute: "spell_power:water", mber: 4.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:elemental_feet', attribute: "spell_power:fire", mber: 4.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:kelp_head', attribute: "spell_power:water", mber: 4.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:kelp_chest', attribute: "spell_power:water", mber: 4.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:kelp_legs', attribute: "spell_power:water", mber: 4.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:kelp_feet', attribute: "spell_power:water", mber: 4.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:dripstone_head', attribute: "spell_power:earth", mber: 4.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:dripstone_chest', attribute: "spell_power:earth", mber: 4.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:dripstone_legs', attribute: "spell_power:earth", mber: 4.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:dripstone_feet', attribute: "spell_power:earth", mber: 4.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:wind_head', attribute: "spell_power:air", mber: 4.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:wind_chest', attribute: "spell_power:air", mber: 4.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:wind_legs', attribute: "spell_power:air", mber: 4.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:wind_feet', attribute: "spell_power:air", mber: 4.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:netherite_kelp_head', attribute: "spell_power:water", mber: 7.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:netherite_kelp_chest', attribute: "spell_power:water", mber: 7.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:netherite_kelp_legs', attribute: "spell_power:water", mber: 7.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:netherite_kelp_feet', attribute: "spell_power:water", mber: 7.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:netherite_dripstone_head', attribute: "spell_power:earth", mber: 7.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:netherite_dripstone_chest', attribute: "spell_power:earth", mber: 7.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:netherite_dripstone_legs', attribute: "spell_power:earth", mber: 7.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:netherite_dripstone_feet', attribute: "spell_power:earth", mber: 7.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:netherite_wind_head', attribute: "spell_power:air", mber: 7.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:netherite_wind_chest', attribute: "spell_power:air", mber: 7.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:netherite_wind_legs', attribute: "spell_power:air", mber: 7.0, operation: "addition" },
  { id: 'elemental_wizards_rpg:netherite_wind_feet', attribute: "spell_power:air", mber: 7.0, operation: "addition" },

  { id: 'paladins:priest_robe_head', attribute: "spell_power:healing", mber: 4.0, operation: "addition" },
  { id: 'paladins:priest_robe_chest', attribute: "spell_power:healing", mber: 4.0, operation: "addition" },
  { id: 'paladins:priest_robe_legs', attribute: "spell_power:healing", mber: 4.0, operation: "addition" },
  { id: 'paladins:priest_robe_feet', attribute: "spell_power:healing", mber: 4.0, operation: "addition" },
  { id: 'paladins:prior_robe_head', attribute: "spell_power:healing", mber: 4.0, operation: "addition" },
  { id: 'paladins:prior_robe_chest', attribute: "spell_power:healing", mber: 4.0, operation: "addition" },
  { id: 'paladins:prior_robe_legs', attribute: "spell_power:healing", mber: 4.0, operation: "addition" },
  { id: 'paladins:prior_robe_feet', attribute: "spell_power:healing", mber: 4.0, operation: "addition" },
  { id: 'paladins:netherite_prior_robe_head', attribute: "spell_power:healing", mber: 7.0, operation: "addition" },
  { id: 'paladins:netherite_prior_robe_chest', attribute: "spell_power:healing", mber: 7.0, operation: "addition" },
  { id: 'paladins:netherite_prior_robe_legs', attribute: "spell_power:healing", mber: 7.0, operation: "addition" },
  { id: 'paladins:netherite_prior_robe_feet', attribute: "spell_power:healing", mber: 7.0, operation: "addition" }
]

/*
属性
"spell_power:critical_damage"    "法术强度：暴击伤害"
"spell_power:critical_chance"    "法术强度：暴击几率"
"spell_power:haste"    "法术强度：急速"
"spell_power:arcane"    "法术强度：奥术"
"spell_power:fire"    "法术强度：火焰"
"spell_power:frost"    "法术强度：冰霜"
"spell_power:healing"    "法术强度：治疗"
"spell_power:lightning"    "法术强度：闪电"
"spell_power:soul"    "法术强度：灵魂"
"spell_power:resistance.generic"    "法术强度：通用抗性"

加成方式
"addition"/基础值加法
"multiply_base"/同乘区加法
"multiply_total"/独立乘区
*/

function generateUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
      let r = Math.random() * 16 | 0;
      let v = c === 'x' ? r : (r & 0x3 | 0x8);
      return v.toString(16);
  });
}
ItemEvents.modification(event => {
  weapen.forEach((config) => {
      event.modify(config.id, consumer => {
          let UUID = generateUUID()
          consumer.addAttribute(config.attribute, UUID, UUID, config.mber, config.operation)
      })
  })
})
