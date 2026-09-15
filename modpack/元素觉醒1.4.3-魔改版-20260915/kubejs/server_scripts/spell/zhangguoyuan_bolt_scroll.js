// ============================================================
// 把法术绑定到卷轴物品上（ELA 在 scroll_1.js 里用的同一套写法）
// 玩家拿这个卷轴去法术台，就能把它学进法术书
// ============================================================
SpellAnvilEvents.getItemStackSpells(event => {
  if (event.itemStack == 'kubejs:zhangguoyuan_bolt_scroll') event.addSpell('wizards:zhangguoyuan_bolt')
})
