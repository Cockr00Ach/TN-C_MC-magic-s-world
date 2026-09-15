ItemEvents.tooltip(event => {
  const dungeonBadges = [
    ['kubejs:dilaohuizhang_atomic', 'atomic'],
    ['kubejs:dilaohuizhang_explosion', 'explosion'],
    ['kubejs:dilaohuizhang_xushici', 'xushici']
  ]

  const acquisitionTooltip = [
    Text.of('§6§l【获取方式】§r'),
    Text.of(''),
    Text.of('§f击败 §c魔王级 Boss§f 时，有 §e1%§f 的概率获得。'),
    Text.of(''),
    Text.of('§f击败 §5觉醒魔王级 Boss§f 时，有 §e5%§f 的概率获得。'),
    Text.of(''),
    Text.of('§f此外，击败部分§e副本与地牢 Boss§f 时，也有一定概率获得。')
  ]

  dungeonBadges.forEach(badge => {
    const tooltip = [
      Text.of(`§f徽章类型：§d§l${badge[1]}`),
      Text.of('')
    ].concat(acquisitionTooltip)

    event.add(badge[0], tooltip)
  })
})
