StartupEvents.registry('item', event => {
  const entityIds = [
    'eeeabsmobs:corpse_warlock',
    'twilightforest:alpha_yeti',
    'legendary_monsters:shulker_mimic',
    'legendary_monsters:withered_abomination',
    'legendary_monsters:lava_eater',
    'legendary_monsters:frostbitten_golem',
    'legendary_monsters:overgrown_colossus',
    'legendary_monsters:ancient_guardian',
    'dodosmobs:bone_chimera',
    'infinitygolem:infinity_golem',
    'dungeonnowloading:chaos_spawner',
    'soulsweapons:accursed_lord_boss',
    'hadean_breathe:hadean_enforcer',
    'hadean_breathe:hadean_blaster',
    'monsterexpansion:rakoth',
    'monsterexpansion:leivekilth',
    'mowziesmobs:umvuthi',
    'monsterexpansion:rhyza',
    'monsterexpansion:skrythe',
    'torchesbecomesunlight:pursuer',
    'bosses_of_mass_destruction:lich',
    'bosses_of_mass_destruction:void_blossom',
    'bosses_of_mass_destruction:obsidilith',
    'bosses_of_mass_destruction:gauntlet',
    'mutantmore:mutant_jungle_zombie'
  ]

  entityIds.forEach(entityId => {
    const id = entityId.split(':')[1]
    event.create(`${id}_zhaohuan2`)
      .texture('kubejs:item/2')
      .rarity('rare')
  })
})
