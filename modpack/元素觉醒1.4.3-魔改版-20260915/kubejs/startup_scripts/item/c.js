StartupEvents.registry('item', event => {
  const tokens = [
    'evoker',
  ]

  tokens.forEach(id => {
    event.create(`${id}_zhaohuan1`)
      .texture('kubejs:item/3')
      .rarity('uncommon')
  })
})
