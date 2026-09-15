StartupEvents.registry('item', event => {
  const fruits = [
    { id: 'air_zhiguo', name: '空气之果' },
    { id: 'arcane_zhiguo', name: '奥术之果' },
    { id: 'earth_zhiguo', name: '大地之果' },
    { id: 'fire_zhiguo', name: '火焰之果' },
    { id: 'frost_zhiguo', name: '冰霜之果' },
    { id: 'healing_zhiguo', name: '神圣之果' },
    { id: 'lightning_zhiguo', name: '闪电之果' },
    { id: 'water_zhiguo', name: '海洋之果' },
    { id: 'wuli_zhiguo', name: '物理之果' },
    { id: 'youxia_zhiguo', name: '游侠之果' }
  ]

  for (const fruit of fruits) {
    event.create(fruit.id)
      .displayName(fruit.name)
      .texture(`kubejs:item/${fruit.id}`)
      .food(food => {
        food.hunger(4)
        food.saturation(0.3)
      })
  }
})
