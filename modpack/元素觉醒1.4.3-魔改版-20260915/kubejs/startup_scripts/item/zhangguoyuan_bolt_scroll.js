// ============================================================
// 「章国源之矢」卷轴 —— 物品注册
// 命名规则跟随 ELA：法术 ID 的 path + "_scroll"
//   wizards:zhangguoyuan_bolt  ->  kubejs:zhangguoyuan_bolt_scroll
// 贴图复用 ELA 已有的奥术卷轴，不需要新画
// ============================================================
StartupEvents.registry('item', event => {
  event.create('zhangguoyuan_bolt_scroll')
    .displayName('§6章国源之矢卷轴')
    .texture('kubejs:item/scroll_arcane')
    .maxStackSize(16)
})
