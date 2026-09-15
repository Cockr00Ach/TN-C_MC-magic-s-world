// TN-C 原创法术：雷系五级 —— 法术注册台（spellanvil）绑定
//
// 告诉 spellanvil：这些卷轴里装的是哪个法术。
// 卷轴物品还必须出现在 data/spellanvil/tags/items/scroll.json 里，注册台才认得。
//
// 这是【新增文件】，不改作者的 scroll_1.js / scroll_2.js。

SpellAnvilEvents.getItemStackSpells(event => {
    if (event.itemStack == 'kubejs:tnc_spark_scroll')            event.addSpell('tnc:spark')
    if (event.itemStack == 'kubejs:tnc_lightning_field_scroll')  event.addSpell('tnc:lightning_field')
    if (event.itemStack == 'kubejs:tnc_lightning_strike_scroll') event.addSpell('tnc:lightning_strike')
    if (event.itemStack == 'kubejs:tnc_lightning_storm_scroll')  event.addSpell('tnc:lightning_storm')
    if (event.itemStack == 'kubejs:tnc_heavenly_thunder_scroll') event.addSpell('tnc:heavenly_thunder')
})
