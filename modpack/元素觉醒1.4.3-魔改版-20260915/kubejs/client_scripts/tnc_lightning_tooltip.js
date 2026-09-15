// TN-C 原创法术：雷系五级 —— 卷轴提示文字
//
// 作者把提示硬编码在 tvt.js 里，我们不改他的文件，用自己的 client script 加。

ItemEvents.tooltip(event => {

    const TNC_LIGHTNING_SCROLLS = [
        { id: 'kubejs:tnc_spark_scroll',            tier: '冒险者级', element: '雷' },
        { id: 'kubejs:tnc_lightning_field_scroll',  tier: '精英级',   element: '雷' },
        { id: 'kubejs:tnc_lightning_strike_scroll', tier: '王级',     element: '雷' },
        { id: 'kubejs:tnc_lightning_storm_scroll',  tier: '传说级',   element: '雷' },
        { id: 'kubejs:tnc_heavenly_thunder_scroll', tier: '神级',     element: '雷' }
    ]

    TNC_LIGHTNING_SCROLLS.forEach(entry => {
        event.addAdvanced(entry.id, (item, advanced, text) => {
            text.add(1, Text.of('§f使用§e法术注册台§f将法术与法术书进行绑定'))
            text.add(2, Text.of(`§7${entry.element}系法术 · ${entry.tier}`))
        })
    })
})
