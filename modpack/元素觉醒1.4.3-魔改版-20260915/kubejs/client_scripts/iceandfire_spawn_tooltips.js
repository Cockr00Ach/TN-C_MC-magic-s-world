ItemEvents.tooltip(event => {
  event.add('iceandfire:spawn_egg_fire_dragon', [
    Text.of('§f该生物在§e§l原始洞穴§r§f生成')
  ])

  event.add('iceandfire:spawn_egg_ice_dragon', [
    Text.of('§f该生物在§e§l异寂空谷维度§r§f生成')
  ])

  event.add('iceandfire:spawn_egg_lightning_dragon', [
    Text.of('§f该生物在§e§l磁铁洞穴维度§r§f生成')
  ])
})
