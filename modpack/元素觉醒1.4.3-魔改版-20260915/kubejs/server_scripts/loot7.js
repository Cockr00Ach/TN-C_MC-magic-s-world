LootJS.modifiers(e => {
    e.addLootTableModifier([
        "minecraft:chests/cs1",
        "minecraft:chests/nether_bridge",
        "dungeon_realm:chests/tier_1_dungeon",
        "dungeon_realm:chests/tier_2_dungeon",
        "dungeon_realm:chests/tier_3_dungeon",
        "dungeon_realm:chests/tier_4_dungeon",
        "dungeon_realm:chests/tier_5_dungeon"
    ]).apply(ctx => {
      let scrr = [
        "kubejs:airshexian_scroll",
        "kubejs:dadishexian_scroll",
        "kubejs:shandianshexian_scroll",
        "kubejs:xianxueshexian_scroll",
        "kubejs:xieeshexian_scroll",
        "kubejs:arcane_beam_scroll",
        "kubejs:lne_frost_ray_scroll"
      ];
  
      if (Math.random() < 0.25) {
        ctx.addLoot(Item.of(LootRandom(scrr), 1));
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
  