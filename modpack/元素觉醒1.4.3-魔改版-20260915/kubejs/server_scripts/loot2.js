LootJS.modifiers(e => {
  e.addLootTableModifier([
    "minecraft:chests/abandoned_mineshaft",
    "minecraft:chests/simple_dungeon",
    "minecraft:chests/stronghold_corridor",
    "minecraft:chests/stronghold_crossing",
    "minecraft:chests/stronghold_library",
    "minecraft:chests/desert_pyramid",
    "minecraft:chests/jungle_temple",
    "minecraft:chests/jungle_temple_dispenser",
    "minecraft:chests/igloo_chest",
    "minecraft:chests/cs1",
    "minecraft:chests/pillager_outpost",
    "minecraft:chests/woodland_mansion",
    "minecraft:chests/buried_treasure",
    "minecraft:chests/shipwreck_map",
    "minecraft:chests/shipwreck_supply",
    "minecraft:chests/shipwreck_treasure",
    "minecraft:chests/underwater_ruin_big",
    "minecraft:chests/underwater_ruin_small",
    "minecraft:chests/nether_bridge",
    "minecraft:chests/bastion_bridge",
    "minecraft:chests/bastion_hoglin_stable",
    "minecraft:chests/bastion_other",
    "minecraft:chests/bastion_treasure",
    "minecraft:chests/village/village_armorer",
    "minecraft:chests/village/village_butcher",
    "minecraft:chests/village/village_cartographer",
    "minecraft:chests/village/village_desert_house",
    "minecraft:chests/village/village_fisher",
    "minecraft:chests/village/village_fletcher",
    "minecraft:chests/village/village_mason",
    "minecraft:chests/village/village_plains_house",
    "minecraft:chests/village/village_savanna_house",
    "minecraft:chests/village/village_shepherd",
    "minecraft:chests/village/village_snowy_house",
    "minecraft:chests/village/village_taiga_house",
    "minecraft:chests/village/village_tannery",
    "minecraft:chests/village/village_temple",
    "minecraft:chests/village/village_toolsmith",
    "minecraft:chests/village/village_weaponsmith"
  ]).apply(ctx => {
    let scrollItems = [
      "kubejs:aqua_water_whip_scroll",
      "kubejs:terra_stone_spear_scroll",
      "kubejs:wind_air_cutter_scroll",
      "kubejs:arcane_missile_scroll",
      "kubejs:fireball_scroll",
      "kubejs:frostbolt_scroll",
      "kubejs:flash_heal_scroll",
      "kubejs:holy_shock_scroll",
      "kubejs:arcaneoverdrive_scroll",
      "kubejs:lightningoverdrive_scroll",
      "kubejs:frostoverdrive_scroll",
      "kubejs:fireoverdrive_scroll",
      "kubejs:starfall_scroll",
      "kubejs:flamelance_scroll",
      "kubejs:frostgrasp_scroll",
      "kubejs:smite_scroll",
      "kubejs:bolster_scroll"
    ];

    if (Math.random() < 0.9) {
      ctx.addLoot(Item.of(LootRandom(scrollItems), 1));
    }
  });
});

/**
 * 从列表中随机抽一个元素（修复了原来的越界问题）
 * @param {string[]} LootTable 
 * @returns {string}
 */
function LootRandom(LootTable) {
  return LootTable[Math.floor(Math.random() * LootTable.length)];
}
