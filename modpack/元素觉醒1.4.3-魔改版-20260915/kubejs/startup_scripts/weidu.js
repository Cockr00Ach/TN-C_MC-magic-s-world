//解锁物品，维度，维度名，条件
const Key = [
    { id: "endrem:evil_eye", dimension: "the_nether", name: "下界", condition: "获得邪恶之眼" },
    { id: "legendary_monsters:atmospheric_boots", dimension: "twilight_forest", name: "暮色森林", condition: "获得大气之靴" },
    { id: "whispering:immortal_amulet", dimension: "the_end", name: "末地", condition: "获得不朽护符" },
    { id: "twilightforest:hydra_trophy", dimension: "lueduodushi", name: "掠夺都市", condition: "获得九头蛇奖杯" },
    { id: "endrem:wither_eye", dimension: "undead_fortress", name: "亡灵堡垒", condition: "获得凋零之眼" },
    { id: "loot_n_explore:ender_dragon_scales", dimension: "abandoned_city", name: "废弃都市", condition: "获得末影龙鳞" }
]

const $EntityTravelToDimensionEvent = Java.loadClass("net.minecraftforge.event.entity.EntityTravelToDimensionEvent")
const $ServerPlayer = Java.loadClass("net.minecraft.server.level.ServerPlayer")
ForgeEvents.onEvent($EntityTravelToDimensionEvent,/** @param {Internal.EntityTravelToDimensionEvent} event */ event => {
    let resourceKey = event.dimension.getPath()
    /**
     * @type {Internal.ServerPlayer}
     */
    let player = event.entity;
    Key.forEach(config => {
        let { dimension, name, condition } = config
        if (player instanceof $ServerPlayer && resourceKey === dimension) {
            if (!player.persistentData.getBoolean(dimension)) {
                event.setCanceled(true)
                player.setStatusMessage(Component.of("你没有解锁维度:").append(name).append(",你需要").append(condition))
            }
        }
    })
})
