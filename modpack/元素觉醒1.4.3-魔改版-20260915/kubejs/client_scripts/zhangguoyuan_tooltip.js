// ============================================================
// ZhangGuoYuan 的物品提示文本
// 客户端脚本：F3+T 即可生效
// § 是 Minecraft 的颜色代码：§6=金 §c=红 §7=灰 §l=加粗 §r=重置
// ============================================================
ItemEvents.tooltip(event => {
  event.add('kubejs:zhangguoyuan', [
    Text.of('§6§l【 原创物品 】'),
    Text.of(''),
    Text.of('§f这是本章整合包的第一个原创内容'),
    Text.of('§7—— 由 KubeJS 驱动，没有写一行 Java')
  ])
})
