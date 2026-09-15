ItemEvents.tooltip(event => {
    event.addAdvanced("kubejs:beer_arcane", (item, advanced, text) => {
        text.add(Component.translate("食用后会获得").gray())

        text.add(Component.translate("attribute.modifier.plus.1", 5, Component.literal("法术急速（3:00）")).blue())
        text.add(Component.translate("attribute.modifier.plus.1", 10, Component.literal("奥术法术强度（3:00）")).blue())
    })

    event.addAdvanced("kubejs:beer_fire", (item, advanced, text) => {
        text.add(Component.translate("食用后会获得").gray())

        text.add(Component.translate("attribute.modifier.plus.1", 5, Component.literal("法术暴击几率（3:00）")).blue())
        text.add(Component.translate("attribute.modifier.plus.1", 10, Component.literal("火焰法术强度（3:00）")).blue())
    })

    event.addAdvanced("kubejs:beer_frost", (item, advanced, text) => {
        text.add(Component.translate("食用后会获得").gray())

        text.add(Component.translate("attribute.modifier.plus.1", 10, Component.literal("法术暴击伤害（3:00）")).blue())
        text.add(Component.translate("attribute.modifier.plus.1", 10, Component.literal("冰霜法术强度（3:00）")).blue())
    })

    event.addAdvanced("kubejs:beer_healing", (item, advanced, text) => {
        text.add(Component.translate("食用后会获得").gray())

        text.add(Component.translate("attribute.modifier.plus.1", 5, Component.literal("法术急速（3:00）")).blue())
        text.add(Component.translate("attribute.modifier.plus.1", 10, Component.literal("治疗法术强度（3:00）")).blue())
    })

    event.addAdvanced("kubejs:beer_soul", (item, advanced, text) => {
        text.add(Component.translate("食用后会获得").gray())

        text.add(Component.translate("attribute.modifier.plus.1", 10, Component.literal("灵魂法术强度（3:00）")).blue())
    })

    event.addAdvanced("kubejs:beer_blood", (item, advanced, text) => {
        text.add(Component.translate("食用后会获得").gray())

        text.add(Text.blue("§a+ 力量 I（3:00）"))
        text.add(Component.translate("attribute.modifier.plus.1", 10, Component.literal("鲜血法术强度（3:00）")).blue())
    })

    event.addAdvanced("kubejs:beer_unholy", (item, advanced, text) => {
        text.add(Component.translate("食用后会获得").gray())

        text.add(Text.blue("§a+ 力量 I（3:00）"))
        text.add(Component.translate("attribute.modifier.plus.1", 10, Component.literal("邪恶法术强度（3:00）")).blue())
    })

    event.addAdvanced("kubejs:beer_water", (item, advanced, text) => {
        text.add(Component.translate("食用后会获得").gray())

        text.add(Component.translate("attribute.modifier.plus.1", 5, Component.literal("法术急速（3:00）")).blue())
        text.add(Component.translate("attribute.modifier.plus.1", 10, Component.literal("海洋法术强度（3:00）")).blue())
    })

    event.addAdvanced("kubejs:beer_earth", (item, advanced, text) => {
        text.add(Component.translate("食用后会获得").gray())

        text.add(Component.translate("attribute.modifier.plus.1", 5, Component.literal("法术暴击几率（3:00）")).blue())
        text.add(Component.translate("attribute.modifier.plus.1", 10, Component.literal("大地法术强度（3:00）")).blue())
    })

    event.addAdvanced("kubejs:beer_lightning", (item, advanced, text) => {
        text.add(Component.translate("食用后会获得").gray())

        text.add(Component.translate("attribute.modifier.plus.1", 10, Component.literal("法术暴击伤害（3:00）")).blue())
        text.add(Component.translate("attribute.modifier.plus.1", 10, Component.literal("闪电法术强度（3:00）")).blue())
    })

    event.addAdvanced("kubejs:beer_air", (item, advanced, text) => {
        text.add(Component.translate("食用后会获得").gray())

        text.add(Component.translate("attribute.modifier.plus.1", 10, Component.literal("法术暴击伤害（3:00）")).blue())
        text.add(Component.translate("attribute.modifier.plus.1", 10, Component.literal("空气法术强度（3:00）")).blue())
    })

    event.addAdvanced("kubejs:beer_youxia", (item, advanced, text) => {
        text.add(Component.translate("食用后会获得").gray())

        text.add(Component.translate("attribute.modifier.plus.1", 10, Component.literal("远程伤害（3:00）")).blue())
    })

    event.addAdvanced("kubejs:beer_wuli", (item, advanced, text) => {
        text.add(Component.translate("食用后会获得").gray())

        text.add(Component.translate("attribute.modifier.plus.1", 10, Component.literal("攻击伤害（3:00）")).blue())
    })

    event.addAdvanced("twilightforest:meef_stroganoff", (item, advanced, text) => {
        text.add(Component.translate("食用后会获得").gray())

        text.add(Text.blue("§a+ 力量 I（0:30）"))
    })

    event.addAdvanced("iceandfire:fire_dragon_flesh", (item, advanced, text) => {
        text.add(Component.translate("食用后会获得").gray())
        text.add(Text.blue("§a+10% 火焰法术强度（0:10）"))
    })

    event.addAdvanced("iceandfire:ice_dragon_flesh", (item, advanced, text) => {
        text.add(Component.translate("食用后会获得").gray())
        text.add(Text.blue("§a+10% 冰霜法术强度（0:10）"))
    })

    event.addAdvanced("iceandfire:lightning_dragon_flesh", (item, advanced, text) => {
        text.add(Component.translate("食用后会获得").gray())
        text.add(Text.blue("§a+10% 闪电法术强度（0:10）"))
    })

    event.addAdvanced("alexscaves:deep_sea_sushi_roll", (item, advanced, text) => {
        text.add(Component.translate("食用后会获得").gray())
        text.add(Text.blue("§a+10% 海洋法术强度（0:30）"))
    })

    event.addAdvanced("alexscaves:darkened_apple", (item, advanced, text) => {
        text.add(Component.translate("食用后会获得").gray())
        text.add(Text.blue("§a+10% 鲜血法术强度（0:30）"))
        text.add(Text.blue("§a+10% 邪恶法术强度（0:30）"))
    })

        event.addAdvanced("culturaldelights:tropical_roll", (item, advanced, text) => {
          text.add(Text.gray("食用后会获得"))
          text.add(Text.blue("§a+10% 海洋法术强度（0:20）"))
        })

        event.addAdvanced("culturaldelights:rice_ball", (item, advanced, text) => {
          text.add(Text.gray("食用后会获得"))
          text.add(Text.blue("§a+10% 海洋法术强度（0:20）"))
        })

        event.addAdvanced("culturaldelights:calamari_roll", (item, advanced, text) => {
          text.add(Text.gray("食用后会获得"))
          text.add(Text.blue("§a+10% 海洋法术强度（0:20）"))
        })    
})
