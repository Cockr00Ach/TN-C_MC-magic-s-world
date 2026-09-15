ItemEvents.tooltip(event => {
  const dungeonBossSpawnItems = [
    'mutantmore:mutant_wither_skeleton_spawn_egg',
    'eeeabsmobs:nameless_guardian_egg',
    'blackgoldalliance:the_black_gold_marshal_spawn_egg',
    'monsterexpansion:ignathos_spawn_egg',
    'torchesbecomesunlight:patriot_egg',
    'torchesbecomesunlight:pursuer_egg'
  ]

  dungeonBossSpawnItems.forEach(itemId => {
    event.add(itemId, [
      Text.of('§f此 Boss 仅在§e§l副本地牢§r§f中生成')
    ])
  })

  const abandonedCityBossEggs = [
    'torchesbecomesunlight:war_phantom_egg',
    'torchesbecomesunlight:shattered_champion_egg',
    'torchesbecomesunlight:fallen_snowpriest_egg'
  ]

  abandonedCityBossEggs.forEach(itemId => {
    event.add(itemId, [
      Text.of('§f此生物在§e§l副本地牢§r§f中概率生成'),
      Text.of('§f在§e§l废弃都市§r§f生成')
    ])
  })
})
