// TN-C 原创法术：闪现（三级）—— 法术铁砧绑定
// 告诉 spellanvil：这些卷轴里装的是哪些法术。
//
// 注意：这是【新增文件】，不修改作者的 scroll_1.js / scroll_2.js。

SpellAnvilEvents.getItemStackSpells(event => {
    if (event.itemStack == 'kubejs:blink_1_scroll') event.addSpell('tnc:blink_1')
    if (event.itemStack == 'kubejs:blink_2_scroll') event.addSpell('tnc:blink_2')
    if (event.itemStack == 'kubejs:blink_3_scroll') event.addSpell('tnc:blink_3')
})
