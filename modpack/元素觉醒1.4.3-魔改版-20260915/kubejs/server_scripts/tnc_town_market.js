// RouchNao shop-only furniture. Production tools, seeds, fences and normal chests remain craftable.
ServerEvents.recipes(function(event) {
  event.remove({ mod: 'immersive_furniture' });
  var townColors = ['white','orange','magenta','light_blue','yellow','lime','pink','gray','light_gray','cyan','purple','blue','brown','green','red','black'];
  townColors.forEach(function(color) {
    event.remove({ output: 'minecraft:' + color + '_bed' });
    event.remove({ output: 'minecraft:' + color + '_carpet' });
    event.remove({ output: 'minecraft:' + color + '_candle' });
  });
  ['oak','spruce','birch','jungle','acacia','dark_oak','crimson','warped'].forEach(function(wood) { event.remove({ output: 'farmersdelight:' + wood + '_cabinet' }); });
  ['minecraft:bookshelf','minecraft:lantern','minecraft:flower_pot','minecraft:candle'].forEach(function(item) { event.remove({ output: item }); });
});
