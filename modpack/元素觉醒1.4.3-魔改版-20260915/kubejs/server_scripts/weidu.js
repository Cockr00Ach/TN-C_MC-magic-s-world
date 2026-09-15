PlayerEvents.inventoryChanged(event => {
    let { item, player } = event
    Key.forEach(config => {
        let { id, dimension } = config
        if (item.id === id && !player.persistentData.getBoolean(dimension)) {
            player.persistentData.putBoolean(dimension, true)
        }
    })
})
//解锁物品，维度，维度名，条件
const Key = [
    { id: "endrem:evil_eye", dimension: "the_nether", name: "下界", condition: "获得邪恶之眼" },
    { id: "legendary_monsters:atmospheric_boots", dimension: "twilight_forest", name: "暮色森林", condition: "获得大气之靴" },
    { id: "whispering:immortal_amulet", dimension: "the_end", name: "末地", condition: "获得不朽护符" },
    { id: "twilightforest:hydra_trophy", dimension: "lueduodushi", name: "掠夺都市", condition: "获得九头蛇奖杯" },
    { id: "endrem:wither_eye", dimension: "undead_fortress", name: "亡灵堡垒", condition: "获得凋零之眼" },
    { id: "loot_n_explore:ender_dragon_scales", dimension: "abandoned_city", name: "废弃都市", condition: "获得末影龙鳞" }
]
