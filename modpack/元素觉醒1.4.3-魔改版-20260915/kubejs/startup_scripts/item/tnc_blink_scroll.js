// TN-C 原创法术：闪现（三级）
// 注册三个卷轴物品。命名规则沿用本包约定：'tnc:blink_1' -> 'blink_1_scroll'
//
// 注意：这是【新增文件】，不修改作者的 Scroll.js。
// KubeJS 会自动加载 startup_scripts 下的所有 .js。

StartupEvents.registry('item', event => {

    const SCROLL_TEXTURE = 'kubejs:item/scroll_arcane'   // 复用本包现成的奥术卷轴贴图

    const TNC_BLINK_SPELLS = [
        { spell: 'tnc:blink_1', name: '闪现 I'   },
        { spell: 'tnc:blink_2', name: '闪现 II'  },
        { spell: 'tnc:blink_3', name: '闪现 III' }
    ]

    TNC_BLINK_SPELLS.forEach(entry => {
        const path = entry.spell.split(':')[1]           // 'blink_1'
        event.create(`${path}_scroll`)                   // -> kubejs:blink_1_scroll
            .displayName(`§6${entry.name}卷轴`)
            .texture(SCROLL_TEXTURE)
            .maxStackSize(16)
    })
})
