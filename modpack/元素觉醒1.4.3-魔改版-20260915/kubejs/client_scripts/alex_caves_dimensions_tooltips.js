ItemEvents.tooltip(event => {
  const alexCavesKeys = [
    'alex_caves_dimensions:primordial_caves_key',
    'alex_caves_dimensions:candy_cavity_key',
    'alex_caves_dimensions:toxic_caves_key',
    'alex_caves_dimensions:abyssal_chasm_key',
    'alex_caves_dimensions:forlorn_hollows_key',
    'alex_caves_dimensions:magnetic_caves_key'
  ]

  alexCavesKeys.forEach(itemId => {
    event.add(itemId, [
      Text.of('§e§l右键物品§r§f后直接前往§e§lAlex洞穴维度')
    ])
  })
})
