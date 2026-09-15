const MapColor = Java.loadClass('net.minecraft.world.level.material.MapColor')

StartupEvents.registry('block', event => {
  const createObsidianLikeBlock = (name, displayName) => {
    event.create(name)
      .displayName(displayName)
      .soundType(SoundType.STONE)
      .mapColor(MapColor.STONE)
      .hardness(50.0)
      .resistance(1200.0)
      .requiresTool(true)
      .tagBlock("minecraft:mineable/pickaxe")
      .tagBlock("minecraft:needs_diamond_tool")
  }

  createObsidianLikeBlock('primordial_block', 'Primordial Block')
  createObsidianLikeBlock('abyssal_block', 'Abyssal Block')
  createObsidianLikeBlock('forlorn_block', 'Forlorn Block')
  createObsidianLikeBlock('candy_block', 'Candy Block')
  createObsidianLikeBlock('toxic_block', 'Toxic Block')
  createObsidianLikeBlock('magnetic_block', 'Magnetic Block')
})