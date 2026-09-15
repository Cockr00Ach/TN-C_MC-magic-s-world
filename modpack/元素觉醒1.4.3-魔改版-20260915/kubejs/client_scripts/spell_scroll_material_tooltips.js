ItemEvents.tooltip(event => {
  const spellScrollMaterials = [
    'minecraft:totem_of_undying',
    'legendary_monsters:frozen_rune',
    'fdbosses:eye_of_chesed',
    'fdbosses:eye_of_geburah',
    'torchesbecomesunlight:sankta_statue_eye',
    'torchesbecomesunlight:rhodes_island_eye',
    'cataclysm:flame_eye',
    'legendary_monsters:eye_of_annihilation',
    'legendary_monsters:eye_of_ghost',
    'cataclysm:cursed_eye',
    'cataclysm:lacrima',
    'cataclysm:essence_of_the_storm',
    'cataclysm:ignitium_ingot',
    'cataclysm:sandstorm_in_a_bottle',
    'cataclysm:cursium_ingot',
    'legendary_monsters:corrupted_soul',
    'eeeabsmobs:guardian_cube',
    'cataclysm:lava_power_cell',
    'cataclysm:witherite_ingot',
    'legendary_monsters:portal_shard',
    'cataclysm:abyss_eye',
    'cataclysm:void_eye',
    'kubejs:diaolingji',
    'kubejs:dilaohuizhang_atomic',
    'kubejs:dilaohuizhang_explosion',
    'kubejs:dilaohuizhang_xushici'
  ]

  spellScrollMaterials.forEach(itemId => {
    event.add(itemId, [
      Text.of('§f用途：§e§l可合成法术卷轴')
    ])
  })
})
