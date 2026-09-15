// TN-C 原创法术：闪现（三级）—— 卷轴提示文字
//
// 作者的方法是在 tvt.js 里维护一份硬编码的卷轴 ID 列表，然后逐条 addAdvanced。
// 我们不改他的文件，用自己的 client script 加同样的提示。

ItemEvents.tooltip(event => {

    const TNC_SCROLLS = [
        { id: 'kubejs:blink_1_scroll', label: '§5奥术法术' },
        { id: 'kubejs:blink_2_scroll', label: '§5奥术法术' },
        { id: 'kubejs:blink_3_scroll', label: '§5奥术法术' }
    ]

    TNC_SCROLLS.forEach(entry => {
        event.addAdvanced(entry.id, (item, advanced, text) => {
            text.add(1, Text.of('§f使用§e法术注册台§f将法术与法术书进行绑定'))
            text.add(2, Text.of(entry.label))
        })
    })
})
