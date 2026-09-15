ServerEvents.recipes(event => {
    const itemsToRemove = [
      'armoroftheages:bamboo_hat',
      'armoroftheages:anubis_armor_head',
      'armoroftheages:anubis_armor_chest',
      'armoroftheages:anubis_armor_legs',
      'armoroftheages:anubis_armor_feet',
      'armoroftheages:centurion_armor_head',
      'armoroftheages:centurion_armor_chest',
      'armoroftheages:centurion_armor_legs',
      'armoroftheages:centurion_armor_feet',
      'armoroftheages:exalted_aurum_armor_head',
      'armoroftheages:exalted_aurum_armor_chest',
      'armoroftheages:exalted_aurum_armor_legs',
      'armoroftheages:exalted_aurum_armor_feet',
      'armoroftheages:holy_armor_head',
      'armoroftheages:holy_armor_chest',
      'armoroftheages:holy_armor_legs',
      'armoroftheages:holy_armor_feet',
      'armoroftheages:iron_plate_armor_head',
      'armoroftheages:iron_plate_armor_chest',
      'armoroftheages:iron_plate_armor_legs',
      'armoroftheages:iron_plate_armor_feet',
      'armoroftheages:japanese_light_armor_head',
      'armoroftheages:japanese_light_armor_chest',
      'armoroftheages:japanese_light_armor_legs',
      'armoroftheages:japanese_light_armor_feet',
      'armoroftheages:o_yoroi_armor_head',
      'armoroftheages:o_yoroi_armor_chest',
      'armoroftheages:o_yoroi_armor_legs',
      'armoroftheages:o_yoroi_armor_feet',
      'armoroftheages:pharaoh_armor_head',
      'armoroftheages:pharaoh_armor_chest',
      'armoroftheages:pharaoh_armor_legs',
      'armoroftheages:pharaoh_armor_feet',
      'armoroftheages:quetzalcoatl_armor_head',
      'armoroftheages:quetzalcoatl_armor_chest',
      'armoroftheages:quetzalcoatl_armor_legs',
      'armoroftheages:quetzalcoatl_armor_feet',
      'armoroftheages:raijin_armor_head',
      'armoroftheages:raijin_armor_chest',
      'armoroftheages:raijin_armor_legs',
      'armoroftheages:raijin_armor_feet',
      'dreadsteel:dreadsteel_scythe',
'ancient_obelisks:obelisk',
'the_harvest:harvest',
'elemental_wizards_rpg:elemental_head',
'elemental_wizards_rpg:elemental_chest',
'elemental_wizards_rpg:elemental_legs',
'elemental_wizards_rpg:elemental_feet',
'cataclysm:abyssal_sacrifice',
'cataclysm:mech_eye',
'cataclysm:flame_eye',
'cataclysm:void_eye',
'cataclysm:monstrous_eye',
'cataclysm:abyss_eye',
'cataclysm:desert_eye',
'cataclysm:cursed_eye',
'cataclysm:storm_eye',
'fdbosses:eye_of_chesed',
'fdbosses:eye_of_malkuth',
'fdbosses:eye_of_geburah',
'legendary_monsters:annihilator_upgrade_smithing_template',
'legendary_monsters:eye_of_soul',
'legendary_monsters:eye_of_sandstorm',
'legendary_monsters:eye_of_ghost',
'legendary_monsters:eye_of_many_ribs',
'legendary_monsters:eye_of_moss',
'legendary_monsters:eye_of_air',
'legendary_monsters:eye_of_frost',
'legendary_monsters:eye_of_magma',
'legendary_monsters:eye_of_annihilation',

'iceandfire:dragonsteel_fire_helmet',
'iceandfire:dragonsteel_fire_chestplate',
'iceandfire:dragonsteel_fire_leggings',
'iceandfire:dragonsteel_fire_boots',
'iceandfire:dragonsteel_fire_sword',

'iceandfire:dragonsteel_ice_helmet',
'iceandfire:dragonsteel_ice_chestplate',
'iceandfire:dragonsteel_ice_leggings',
'iceandfire:dragonsteel_ice_boots',
'iceandfire:dragonsteel_ice_sword',

'iceandfire:dragonsteel_lightning_helmet',
'iceandfire:dragonsteel_lightning_chestplate',
'iceandfire:dragonsteel_lightning_leggings',
'iceandfire:dragonsteel_lightning_boots',
'iceandfire:dragonsteel_lightning_sword',
'editenchanting:enchantment_edit_table',
'runes:soul_stone',
'endrem:undead_eye',
'endrem:witch_eye',
'mutantmore:compound_z',
'cflt:warp_pearl',
'cflt:custom_warp_pearl',
'alex_caves_dimensions:primordial_caves_key',
'alex_caves_dimensions:candy_cavity_key',
'alex_caves_dimensions:toxic_caves_key',
'alex_caves_dimensions:abyssal_chasm_key',
'alex_caves_dimensions:forlorn_hollows_key',
'alex_caves_dimensions:magnetic_caves_key',
'blackgoldalliance:nether_golden_veined_codex',
'blackgoldalliance:nether_siphon_core',
'blackgoldalliance:nether_siphon_link_station',
'blackgoldalliance:nether_siphon_deterrent_upgrade_scroll_i',
'blackgoldalliance:nether_siphon_deterrent_upgrade_scroll_ii',
'blackgoldalliance:nether_siphon_deterrent_upgrade_scroll_iii',
'blackgoldalliance:deterrent_expansion_tablet',
'blackgoldalliance:deterrent_intensification_tablet',
'blackgoldalliance:marshal_emblem',
'minecraft:netherite_upgrade_smithing_template'

    ]
  
    itemsToRemove.forEach(item => {
      event.remove({ output: item })
    })

    event.shaped(
      Item.of('minecraft:netherite_upgrade_smithing_template', 2),
      [
        ' A ',
        'ABA',
        ' A '
      ],
      {
        A: 'ysjxspells:chujishuijing',
        B: 'minecraft:netherite_ingot'
      }
    ).id('kubejs:netherite_upgrade_smithing_template_duplication')

    const recipeIdsToRemove = [
      'spellbladenext:arcaneherald_head',
      'spellbladenext:arcaneherald_chest',
      'spellbladenext:arcaneherald_legs',
      'spellbladenext:arcaneherald_feet',
      'spellbladenext:frostherald_head',
      'spellbladenext:frostherald_chest',
      'spellbladenext:frostherald_legs',
      'spellbladenext:frostherald_feet',
      'spellbladenext:ashherald_head',
      'spellbladenext:ashherald_chest',
      'spellbladenext:ashherald_legs',
      'spellbladenext:ashherald_feet',
      'spellbladenext:voidforge',
      'cataclysm:smithing/cursium_helmet',
      'cataclysm:smithing/cursium_chestplate',
      'cataclysm:smithing/cursium_leggings',
      'cataclysm:smithing/cursium_boots',
      'cataclysm:smithing/ignitium_helmet',
      'cataclysm:smithing/ignitium_chestplate',
      'cataclysm:smithing/ignitium_leggings',
      'cataclysm:smithing/ignitium_boots',
      'cataclysm:ignitium_upgrade_smithing_template',
      'cataclysm:cursium_upgrade_smithing_template',
      'mebahelcreaturesdraugr:golden_claw',
      'torchesbecomesunlight:rhodes_island_eye',
      'torchesbecomesunlight:sankta_statue_eye',
      'torchesbecomesunlight:sargon_hotel_eye',
      'torchesbecomesunlight:consciousness_wanted_tombstone',
      'torchesbecomesunlight:demand_from_violence'
    ]

    recipeIdsToRemove.forEach(id => {
      event.remove({ id: id })
    })

    event.shaped(
      'whispering:magnetic_badge',
      [
        'ABC'
      ],
      {
        A: 'alexscaves:azure_neodymium_ingot',
        B: 'jewelry:iron_ring',
        C: 'alexscaves:scarlet_neodymium_ingot'
      }
    ).id('kubejs:magnetic_badge')
  })
  
