StartupEvents.registry('item', event => {

  const beers = [
    { id: 'arcane',    name: '奥术啤酒' },
    { id: 'fire',      name: '火焰啤酒' },
    { id: 'frost',     name: '冰霜啤酒' },
    { id: 'healing',   name: '治疗啤酒' },
    { id: 'water',     name: '海洋啤酒' },
    { id: 'earth',     name: '大地啤酒' },
    { id: 'lightning', name: '闪电啤酒' },
    { id: 'air',       name: '空气啤酒' },
    { id: 'youxia',       name: '游侠啤酒' },
    { id: 'wuli',       name: '物理啤酒' }
  ]

  for (const beer of beers) {
    event.create(`beer_${beer.id}`)
      .displayName(beer.name)
      .rarity('epic') 
      .food(food => {
        food.hunger(4)
        food.saturation(2.4)
        food.alwaysEdible()
      })
  }

})