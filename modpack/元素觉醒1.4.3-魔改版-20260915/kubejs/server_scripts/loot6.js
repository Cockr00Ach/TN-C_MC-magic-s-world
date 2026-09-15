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
      const books = [
        Item.of('minecraft:enchanted_book', '{StoredEnchantments:[{lvl:1s,id:"combatroll:longfooted"}]}'),
        Item.of('minecraft:enchanted_book', '{StoredEnchantments:[{lvl:1s,id:"combatroll:acrobat"}]}'),
        Item.of('minecraft:enchanted_book', '{StoredEnchantments:[{lvl:1s,id:"combatroll:multi_roll"}]}'),
        Item.of('minecraft:enchanted_book', '{StoredEnchantments:[{lvl:1s,id:"witcher_rpg:signs"}]}'),
        Item.of('minecraft:enchanted_book', '{StoredEnchantments:[{lvl:1s,id:"more_rpg_classes:elemental"}]}'),
        Item.of('minecraft:enchanted_book', '{StoredEnchantments:[{lvl:1s,id:"death_knights:decaying"}]}'),
        Item.of('minecraft:enchanted_book', '{StoredEnchantments:[{lvl:1s,id:"extraspellattributes:battlerouse"}]}'),
        Item.of('minecraft:enchanted_book', '{StoredEnchantments:[{lvl:1s,id:"extraspellattributes:defiance"}]}'),
        Item.of('minecraft:enchanted_book', '{StoredEnchantments:[{lvl:1s,id:"extraspellattributes:glancingblow"}]}'),
        Item.of('minecraft:enchanted_book', '{StoredEnchantments:[{lvl:1s,id:"extraspellattributes:spellbreak"}]}'),
        Item.of('minecraft:enchanted_book', '{StoredEnchantments:[{lvl:1s,id:"extraspellattributes:suppression"}]}'),
        Item.of('minecraft:enchanted_book', '{StoredEnchantments:[{lvl:1s,id:"extraspellattributes:warding"}]}'),
        Item.of('minecraft:enchanted_book', '{StoredEnchantments:[{lvl:1s,id:"spell_power:haste"}]}'),
        Item.of('minecraft:enchanted_book', '{StoredEnchantments:[{lvl:1s,id:"spell_power:critical_chance"}]}'),
        Item.of('minecraft:enchanted_book', '{StoredEnchantments:[{lvl:1s,id:"spell_power:critical_damage"}]}'),
        Item.of('minecraft:enchanted_book', '{StoredEnchantments:[{lvl:1s,id:"spell_power:magic_protection"}]}'),
        Item.of('minecraft:enchanted_book', '{StoredEnchantments:[{lvl:1s,id:"spell_power:sunfire"}]}'),
        Item.of('minecraft:enchanted_book', '{StoredEnchantments:[{lvl:1s,id:"spell_power:energize"}]}'),
        Item.of('minecraft:enchanted_book', '{StoredEnchantments:[{lvl:1s,id:"spell_power:spell_power"}]}'),
        Item.of('minecraft:enchanted_book', '{StoredEnchantments:[{lvl:1s,id:"farmersdelight:backstabbing"}]}'),
        Item.of('minecraft:enchanted_book', '{StoredEnchantments:[{lvl:1s,id:"spell_power:soulfrost"}]}'),
        Item.of('minecraft:enchanted_book', '{StoredEnchantments:[{lvl:1s,id:"minecraft:mending"}]}'),
        Item.of('minecraft:enchanted_book', '{StoredEnchantments:[{lvl:1s,id:"minecraft:fortune"}]}'),
        Item.of('minecraft:enchanted_book', '{StoredEnchantments:[{lvl:1s,id:"minecraft:fortune"}]}')
      ];
  
      if (Math.random() < 0.4) {
        ctx.addLoot(Item.of(LootRandom(books), 1));
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
  