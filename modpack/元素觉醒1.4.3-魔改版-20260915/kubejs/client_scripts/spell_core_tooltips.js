ItemEvents.tooltip(event => {
  event.add('ysjxspells:fashuhexin', [
    Text.of('§6§l【获取方式】§r'),
    Text.of(''),
    Text.of('§f• 击败 §c§l魔王级§r§f 或 §5§l觉醒魔王级§r§f 生物时，有概率获得'),
    Text.of('§f• 击败§e§l副本及地牢§r§f中的 §e§lBoss§r§f 时，有概率获得')
  ])

  event.add('kubejs:baozhu', [
    Text.of('§6§l【获取方式】§r'),
    Text.of(''),
    Text.of('§f• 可在§e§l战利品箱§r§f中获取'),
    Text.of('§f• 佩戴§e§l撼地源晶§r§f后，可在§e§l生化洞穴维度§r§f击杀生物获取')
  ])

  event.add('ysjxspells:gaojishuijing', [
    Text.of('§6§l【获取方式】§r'),
    Text.of(''),
    Text.of('§f• 在§e§l掠夺都市/亡灵堡垒§r§f开箱时概率获取'),
    Text.of('§f• 击杀§c§l魔王级生物§r§f时概率获取')
  ])

  event.add('ysjxspells:chujishuijing', [
    Text.of('§6§l【获取方式】§r'),
    Text.of(''),
    Text.of('§f• 可在§e§l战利品箱§r§f中获取'),
    Text.of('§f• 可在§e§l磁铁维度§r§f获取')
  ])

  event.add('ysjxspells:zhongjishuijing', [
    Text.of('§6§l【获取方式】§r'),
    Text.of(''),
    Text.of('§f• 可在§e§l战利品箱§r§f中获取'),
    Text.of('§f• 佩戴§e§l烬灭之核§r§f，在§e§l原始洞穴维度§r§f击杀生物时§e§l概率获取'),
    Text.of('§f• 佩戴§e§l撼地源晶§r§f，在§e§l生化洞穴维度§r§f击杀生物时§e§l概率获取')
  ])

  event.add('ysjxspells:yuansushuijing', [
    Text.of('§7施放§6至尊法术§7与§5超位法术§7时所需的特殊消耗品。'),
    Text.of(''),
    Text.of('§6§l【获取方式】§r'),
    Text.of(''),
    Text.of('§f• 击杀 §6§l魔王种§r§f / §c§l魔王级§r§f / §5§l觉醒魔王级§r§f / §4§l天灾§r§f 生物时§e§l固定掉落'),
    Text.of('§f• 可在§e§l副本地牢§r§f中获取')
  ])

  event.add('ysjxspells:xiajiehejinbi', [
    Text.of('§7可通过开启§3副本地牢§7中的§6战利品箱§7获得。')
  ])

  const elementalCrystals = [
    'ysjxspells:fire_crystal',
    'ysjxspells:earth_crystal',
    'ysjxspells:water_crystal',
    'ysjxspells:air_crystal',
    'ysjxspells:arcane_crystal',
    'ysjxspells:frost_crystal',
    'ysjxspells:healing_crystal',
    'ysjxspells:lightning_crystal',
    'ysjxspells:wuli_crystal',
    'ysjxspells:sheshou_crystal'
  ]

  elementalCrystals.forEach(itemId => {
    event.add(itemId, [
      Text.of('§6§l【获取方式】§r'),
      Text.of('§f可通过开启§e战利品箱§f获得。'),
      Text.of('§f佩戴§c「烬灭之核」§f后，在§6原始洞穴维度§f击杀生物获得。'),
      Text.of('§f佩戴§3「深渊逆鳞」§f后，在§9渊海洞穴维度§f击杀生物获得。')
    ])
  })
})
