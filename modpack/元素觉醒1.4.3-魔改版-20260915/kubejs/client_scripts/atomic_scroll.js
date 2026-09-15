ItemEvents.tooltip(event => {
  const supremeSpellScrolls = [
    'kubejs:heidong_scroll',
    'kubejs:huoyun_gaojie_scroll',
    'kubejs:bingdongfazhen_scroll',
    'kubejs:malkuth_giant_sword_slash_scroll',
    'kubejs:mikaerzhufu_gao_scroll',
    'kubejs:haiyangjiejie_scroll',
    'kubejs:tianxing_scroll',
    'kubejs:fengshenzhishi_scroll',
    'kubejs:lingyuleiji_scroll',
    'kubejs:jianyu_gaojie_scroll'
  ]

  supremeSpellScrolls.forEach(scroll => {
    event.add(scroll, [Text.of('§6§l高阶至尊法术')])
  })

  event.add('kubejs:wangzhibaoku_scroll', [
    Text.of('§6§l至尊法术')
  ])

  const spellElements = [
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

  spellElements.forEach(element => {
    event.add(`kubejs:atomic_${element}_scroll`, [
      Text.of('§d§l超位法术'),
      Text.of('§f使用此法术将§e§l无视 Boss 限伤§r§f直接造成伤害'),
      Text.of('§c§l暂不支持光影')
    ])

    event.add(`kubejs:xushici_${element}_scroll`, [
      Text.of('§d§l超位法术'),
      Text.of('§f使用此法术将§e§l无视 Boss 限伤§r§f直接造成伤害')
    ])

    event.add(`kubejs:explosion_${element}_scroll`, [
      Text.of('§d§l超位法术'),
      Text.of('§f使用此法术将§e§l无视 Boss 限伤§r§f直接造成伤害')
    ])
  })
})
