ItemEvents.tooltip(event => {
    event.addAdvanced("iceandfire:dragonsteel_fire_sword", (item, advanced, text) => {
        text.add(Component.literal("+8 火焰法术强度").withStyle("red"))
        text.add(Component.literal("+25%攻击力转化为火焰法术伤害").withStyle("red"))
    })

    event.addAdvanced("iceandfire:dragonsteel_ice_sword", (item, advanced, text) => {
        text.add(Component.literal("+8 冰霜法术强度").withStyle("blue"))
        text.add(Component.literal("+25%攻击力转化为冰霜法术伤害").withStyle("blue"))
    })

event.addAdvanced("iceandfire:dragonsteel_lightning_sword", (item, advanced, text) => {
    text.add(Component.literal("+8 奥术法术强度").withStyle("light_purple"))
    text.add(Component.literal("+25% 攻击力转化为奥术法术伤害").withStyle("light_purple"))
})

    event.addAdvanced("kubejs:heart_container", (item, advanced, text) => {
        text.add(Component.literal("右键使用后永久增加2点最大生命值").withStyle("green"))
    })

        event.addAdvanced("building_blueprint:modern_building", (item, advanced, text) => {
        text.add(Component.literal("请勿在水边放置（不然地下室会被淹）").withStyle("green"))
    })
    event.addAdvanced("building_blueprint:antiquity_building", (item, advanced, text) => {
        text.add(Component.literal("请勿在水边放置（不然地下室会被淹）").withStyle("green"))
    })
    event.addAdvanced("bosses_delight:obsidian_onion", (item, advanced, text) => {
        text.add(Component.literal("击败黑曜巨石柱后在宝箱中生成").withStyle("green"))
    })
})