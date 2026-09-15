// TN-C 原创法术：雷系五级 —— 卷轴物品注册
//
// 设计文档 4.1：小闪电(冒险者) / 雷场(精英) / 雷击(王) / 雷暴(传说) / 天打五雷轰(神)
// 贴图复用本包现成的雷系卷轴：kubejs:item/scroll_lightning
//
// 这是【新增文件】，不改作者的 Scroll.js。KubeJS 会自动加载 startup_scripts 下的所有 .js。

StartupEvents.registry('item', event => {

    const SCROLL_TEXTURE = 'kubejs:item/scroll_lightning'

    // spell = 法术 id（data/tnc/spells/<path>.json）
    // item  = 卷轴物品 id（去掉 kubejs: 前缀）
    const TNC_LIGHTNING_SPELLS = [
        { spell: 'tnc:spark',            item: 'tnc_spark_scroll',            name: '小闪电',     rarity: null    },
        { spell: 'tnc:lightning_field',  item: 'tnc_lightning_field_scroll',  name: '雷场',       rarity: null    },
        { spell: 'tnc:lightning_strike', item: 'tnc_lightning_strike_scroll', name: '雷击',       rarity: 'rare'  },
        { spell: 'tnc:lightning_storm',  item: 'tnc_lightning_storm_scroll',  name: '雷暴',       rarity: 'epic'  },
        { spell: 'tnc:heavenly_thunder', item: 'tnc_heavenly_thunder_scroll', name: '天打五雷轰', rarity: 'epic'  }
    ]

    TNC_LIGHTNING_SPELLS.forEach(entry => {
        const builder = event.create(entry.item)
            .displayName(`§6${entry.name}卷轴`)
            .texture(SCROLL_TEXTURE)
            .maxStackSize(16)

        if (entry.rarity) {
            builder.rarity(entry.rarity)
        }
    })
})
