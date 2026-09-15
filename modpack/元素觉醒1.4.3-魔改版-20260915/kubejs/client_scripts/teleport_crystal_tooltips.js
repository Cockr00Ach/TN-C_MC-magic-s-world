ItemEvents.tooltip(event => {
  const portalFrameTeleportCrystals = [
    'kubejs:magnetic_teleport_crystal',
    'kubejs:toxic_teleport_crystal',
    'kubejs:candy_teleport_crystal',
    'kubejs:forlorn_teleport_crystal',
    'kubejs:abyssal_teleport_crystal',
    'kubejs:primordial_teleport_crystal'
  ]

  portalFrameTeleportCrystals.forEach(itemId => {
    event.add(itemId, [
      Text.of('§f对应的传送门框架位于§e§l【元素觉醒】别墅§r§f的§e§l地下室§r§f。'),
      Text.of('§f若需要额外的传送门框架，也可以前往§e§l魔法协会大厅§r§f，向§e§l冰霜酒狐§r§f购买。')
    ])
  })
})
