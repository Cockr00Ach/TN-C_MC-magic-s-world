// ============================================================
// ZhangGuoYuan 的合成配方
// 服务端脚本：/reload 即可生效，不用重启游戏
// ============================================================
ServerEvents.recipes(event => {
  event.shaped('kubejs:zhangguoyuan', [
    ' A ',
    'ABA',
    ' A '
  ], {
    A: 'minecraft:gold_ingot',
    B: 'minecraft:diamond'
  })
})
