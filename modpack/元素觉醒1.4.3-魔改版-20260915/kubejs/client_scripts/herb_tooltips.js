ItemEvents.tooltip(event => {
  const commonHerbTooltip = [
    Text.of('§6§l【获取方式】§r'),
    Text.of(''),
    Text.of('§f• 可在§e§l战利品箱§r§f中获取'),
    Text.of('§f• 佩戴§e§l糖果心§r§f后，可在§e§l糖果洞穴维度§r§f击杀生物获取')
  ]

  event.add('ysjxspells:chujie_caoyao', [
    Text.of('§f食用后恢复§a§l1%生命值'),
    Text.of(''),
    Text.of('§6§l【获取方式】§r'),
    Text.of(''),
    Text.of('§f• 可在§e§l战利品箱§r§f中获取'),
    Text.of('§f• 佩戴§e§l糖果心§r§f后，可在§e§l糖果洞穴维度§r§f击杀生物获取')
  ])
  event.add('ysjxspells:zhongjie_caoyao', commonHerbTooltip)

  event.add('ysjxspells:gaojie_caoyao', [
    Text.of('§6§l【获取方式】§r'),
    Text.of(''),
    Text.of('§f• 佩戴§e§l糖果心§r§f后，可在§e§l糖果洞穴维度§r§f击杀生物获取')
  ])
})
