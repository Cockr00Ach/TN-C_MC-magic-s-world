// ============================================================
// ZhangGuoYuan  —— 整合包第一个原创物品
// 注册阶段脚本：游戏启动时执行一次
// 注册出的物品 ID = kubejs:zhangguoyuan
// ============================================================
StartupEvents.registry('item', event => {
  event.create('zhangguoyuan')
    .displayName('ZhangGuoYuan')
    .texture('kubejs:item/zhangguoyuan')  // 贴图 -> kubejs/assets/kubejs/textures/item/zhangguoyuan.png
    .rarity('epic')
    .maxStackSize(1)
})
