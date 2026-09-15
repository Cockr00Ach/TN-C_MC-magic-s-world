ItemEvents.tooltip(event => {
  event.add('find_me:name_paper', [
    Text.of('§6§l【宠物绑定】§r'),
    Text.of('§f手持此物品，对准宠物按下§e鼠标右键§f，即可完成绑定。'),
    Text.of('§f绑定成功后，按下§e§l G 键§r§f打开§b宠物管理器§f。'),
    Text.of('§f选择已经绑定的宠物，即可进行§a召唤§f或§c收回§f。'),
  ])
})
