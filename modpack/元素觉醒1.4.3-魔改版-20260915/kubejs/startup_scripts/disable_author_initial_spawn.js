// ysjx_dimension creates this file as true for a fresh world and then moves
// the first login to ysjx_dimension:magic_association.  Our pack uses the
// Overworld as the continuous world, so force the author's optional tutorial
// spawn switch off before ysjx_dimension reads it during ServerStartedEvent.

const $ServerAboutToStartEvent = Java.loadClass('net.minecraftforge.event.server.ServerAboutToStartEvent')
const $LevelResource = Java.loadClass('net.minecraft.world.level.storage.LevelResource')
const $InitialSpawnFlag = Java.loadClass('com.tnc.tnc.integration.InitialSpawnFlag')

ForgeEvents.onEvent($ServerAboutToStartEvent, event => {
  // All path and file operations live in Java so Rhino never has to choose
  // between overloaded Path/FileWriter methods.
  const worldRoot = event.server.getWorldPath($LevelResource.ROOT)
  const flag = $InitialSpawnFlag.disable(worldRoot)
  console.info('[TN-C] Disabled ysjx_dimension magic-association initial spawn: ' + flag)
})
