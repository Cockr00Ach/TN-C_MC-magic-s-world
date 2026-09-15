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
      "minecraft:chests/tongyong",
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
      let apples = [
        "minecraft:cooked_beef",
        "minecraft:carrot",
        "minecraft:glow_berries",
        "minecraft:baked_potato",
        "minecraft:cooked_cod",
        "minecraft:cooked_rabbit",
        "minecraft:cooked_chicken",
        "kubejs:water_apple",
        "kubejs:earth_apple",
        "kubejs:lightning_apple",
        "kubejs:air_apple"
      ];
  
      if (Math.random() < 0.4) {
        ctx.addLoot(Item.of(LootRandom(apples), 2));
      }
    });
  });
  
  /**
   * 
   * @param {string[]} LootTable 
   * @returns {string}
   */
  function LootRandom(LootTable) {
    return LootTable[Math.floor(Math.random() * LootTable.length)];
  }
  