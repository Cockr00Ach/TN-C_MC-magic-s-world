// Reliable one-time Medieval Town placement near the randomized Overworld spawn.

const TOWN_GENERATION_VERSION = 2
const TOWN_VERSION_KEY = 'tnc_initial_town_generation_version'
const PLAYER_ARRIVAL_VERSION_KEY = 'tnc_initial_town_arrival_version'
const TOWN_LOG = '[TN-C Town]'

// The exported structure bounding box is 281 x 136 x 241. Its minimum corner
// is kept close to spawn, while the arrival point targets the dense main build.
const TOWN_ORIGIN_OFFSET = { x: 20, z: 8 }
const TOWN_CENTER_LOCAL = { x: 153, z: 127 }
const TOWN_ARRIVAL_LOCAL = { x: 247, y: 21, z: 84 }

const TOWN_PIECES = [
  ['piece_0_0_2', 0, 0, 96],
  ['piece_0_0_3', 0, 0, 144],
  ['piece_1_0_0', 48, 0, 0],
  ['piece_1_0_1', 48, 0, 48],
  ['piece_1_0_4', 48, 0, 192],
  ['piece_2_0_0', 96, 0, 0],
  ['piece_2_0_1', 96, 0, 48],
  ['piece_2_0_4', 96, 0, 192],
  ['piece_3_0_0', 144, 0, 0],
  ['piece_3_0_1', 144, 0, 48],
  ['piece_4_0_0', 192, 0, 0],
  ['piece_4_0_1', 192, 0, 48],
  ['piece_4_0_2', 192, 0, 96],
  ['piece_5_0_0', 240, 0, 0],
  ['piece_5_0_1', 240, 0, 48],
  ['piece_5_0_2', 240, 0, 96],
  ['piece_5_1_1', 240, 48, 48]
]

// Force-load only the sparse 48x48 horizontal cells that actually contain
// structure pieces. The full 281x241 bounding box can cover 288 chunks and
// exceed vanilla's 256 forced-chunk limit; these cells use at most 187.
const TOWN_FORCELOAD_AREAS = [
  [0, 96, 47, 143],
  [0, 144, 47, 191],
  [48, 0, 95, 47],
  [48, 48, 95, 95],
  [48, 192, 95, 239],
  [96, 0, 143, 47],
  [96, 48, 143, 95],
  [96, 192, 143, 239],
  [144, 0, 191, 47],
  [144, 48, 191, 95],
  [192, 0, 239, 47],
  [192, 48, 239, 95],
  [192, 96, 239, 143],
  [240, 0, 280, 47],
  [240, 48, 280, 95],
  [240, 96, 280, 143]
]

let townGenerationPending = false

function townCommand(server, command) {
  return server.runCommandSilent(`execute in minecraft:overworld run ${command}`)
}

function townLayout(overworld) {
  const spawn = overworld.getSharedSpawnPos()
  const originX = spawn.x + TOWN_ORIGIN_OFFSET.x
  const originY = spawn.y - 1
  const originZ = spawn.z + TOWN_ORIGIN_OFFSET.z

  return {
    originX: originX,
    originY: originY,
    originZ: originZ,
    centerX: originX + TOWN_CENTER_LOCAL.x,
    centerY: originY,
    centerZ: originZ + TOWN_CENTER_LOCAL.z,
    arrivalX: originX + TOWN_ARRIVAL_LOCAL.x,
    arrivalY: originY + TOWN_ARRIVAL_LOCAL.y,
    arrivalZ: originZ + TOWN_ARRIVAL_LOCAL.z
  }
}

function setTownChunksForced(server, layout, forced) {
  for (const area of TOWN_FORCELOAD_AREAS) {
    const [x0, z0, x1, z1] = area
    const action = forced ? 'add' : 'remove'
    const result = townCommand(
      server,
      `forceload ${action} ${layout.originX + x0} ${layout.originZ + z0} ${layout.originX + x1} ${layout.originZ + z1}`
    )
    console.info(`${TOWN_LOG} forceload ${action} area ${x0},${z0} -> ${x1},${z1}: result=${result}`)
  }
}

function saveTownCoordinates(server, layout) {
  const data = server.persistentData
  data.putInt(TOWN_VERSION_KEY, TOWN_GENERATION_VERSION)
  data.putInt('tnc_initial_town_x', layout.centerX)
  data.putInt('tnc_initial_town_y', layout.centerY)
  data.putInt('tnc_initial_town_z', layout.centerZ)
  data.putInt('tnc_initial_town_arrival_x', layout.arrivalX)
  data.putInt('tnc_initial_town_arrival_y', layout.arrivalY)
  data.putInt('tnc_initial_town_arrival_z', layout.arrivalZ)
}

function placeTown(server, layout) {
  const failed = []

  for (const piece of TOWN_PIECES) {
    const [name, x, y, z] = piece
    const px = layout.originX + x
    const py = layout.originY + y
    const pz = layout.originZ + z
    const result = townCommand(server, `place template tnc:medieval_town/${name} ${px} ${py} ${pz}`)
    console.info(`${TOWN_LOG} place ${name} at ${px} ${py} ${pz}: result=${result}`)
    if (result <= 0) failed.push(name)
  }

  if (failed.length > 0) {
    console.error(`${TOWN_LOG} generation failed: ${failed.join(', ')}`)
    return failed
  }

  // Guarantee a safe 3x3 landing point in the main structure cluster.
  townCommand(server, `fill ${layout.arrivalX - 1} ${layout.arrivalY - 1} ${layout.arrivalZ - 1} ${layout.arrivalX + 1} ${layout.arrivalY - 1} ${layout.arrivalZ + 1} minecraft:stone_bricks`)
  townCommand(server, `fill ${layout.arrivalX - 1} ${layout.arrivalY} ${layout.arrivalZ - 1} ${layout.arrivalX + 1} ${layout.arrivalY + 2} ${layout.arrivalZ + 1} minecraft:air`)

  saveTownCoordinates(server, layout)
  console.info(`${TOWN_LOG} generation complete; center=${layout.centerX},${layout.centerY},${layout.centerZ}; arrival=${layout.arrivalX},${layout.arrivalY},${layout.arrivalZ}`)
  return []
}

function tellTownCoordinates(player, server) {
  const data = server.persistentData
  const centerX = data.getInt('tnc_initial_town_x')
  const centerY = data.getInt('tnc_initial_town_y')
  const centerZ = data.getInt('tnc_initial_town_z')
  const arrivalX = data.getInt('tnc_initial_town_arrival_x')
  const arrivalY = data.getInt('tnc_initial_town_arrival_y')
  const arrivalZ = data.getInt('tnc_initial_town_arrival_z')

  player.tell(`新手村中心坐标：X ${centerX} / Y ${centerY} / Z ${centerZ}`)
  player.tell(`新手村传送点：X ${arrivalX} / Y ${arrivalY} / Z ${arrivalZ}`)
}

function sendPlayerToTown(player, server) {
  tellTownCoordinates(player, server)

  if (player.persistentData.getInt(PLAYER_ARRIVAL_VERSION_KEY) >= TOWN_GENERATION_VERSION) return

  const data = server.persistentData
  const x = data.getInt('tnc_initial_town_arrival_x')
  const y = data.getInt('tnc_initial_town_arrival_y')
  const z = data.getInt('tnc_initial_town_arrival_z')
  const username = player.profile.name

  player.teleportTo('minecraft:overworld', x + 0.5, y, z + 0.5, 0, 0)
  const spawnResult = server.runCommandSilent(`spawnpoint ${username} ${x} ${y} ${z}`)
  player.persistentData.putInt(PLAYER_ARRIVAL_VERSION_KEY, TOWN_GENERATION_VERSION)
  player.tell(`已传送至新手村，并设置重生点。spawnpoint result=${spawnResult}`)
  console.info(`${TOWN_LOG} sent ${username} to ${x},${y},${z}; spawnpoint result=${spawnResult}`)
}

function tellOnlinePlayers(server, message) {
  for (const onlinePlayer of server.players) {
    try {
      onlinePlayer.tell(message)
    } catch (error) {
      console.error(`${TOWN_LOG} could not notify an online player: ${error}`)
    }
  }
}

function sendOnlinePlayersToTown(server) {
  for (const onlinePlayer of server.players) {
    try {
      sendPlayerToTown(onlinePlayer, server)
    } catch (error) {
      console.error(`${TOWN_LOG} could not send an online player to town: ${error}`)
    }
  }
}

PlayerEvents.loggedIn(event => {
  const server = event.server
  const player = event.player

  if (server.persistentData.getInt(TOWN_VERSION_KEY) >= TOWN_GENERATION_VERSION) {
    sendPlayerToTown(player, server)
    return
  }

  if (townGenerationPending) {
    player.tell('新手村正在生成，请稍候；完成后会显示精确坐标。')
    return
  }

  const overworld = server.getLevel('minecraft:overworld')
  if (!overworld) {
    player.tell('新手村生成失败：主世界尚未加载。请重新进入世界重试。')
    console.error(`${TOWN_LOG} generation aborted: Overworld is unavailable`)
    return
  }

  const layout = townLayout(overworld)
  townGenerationPending = true
  try {
    setTownChunksForced(server, layout, true)
    player.tell('正在加载新手村区域并放置 17 个结构分块，请稍候……')

    // KubeJS 2001.6.5 exposes scheduleInTicks(ticks, callback).
    // Give forced chunks two seconds to finish loading before placement.
    server.scheduleInTicks(40, callback => {
      try {
        const failed = placeTown(server, layout)
        if (failed.length > 0) {
          tellOnlinePlayers(server, `新手村生成失败，未成功放置：${failed.join(', ')}。未写入成功标记，下次进入会重试。`)
          return
        }

        tellOnlinePlayers(server, '新手村 17 个结构分块已全部生成成功。')
        sendOnlinePlayersToTown(server)
      } catch (error) {
        console.error(`${TOWN_LOG} unexpected generation error: ${error}`)
        tellOnlinePlayers(server, `新手村生成发生异常：${error}。未写入成功标记，下次进入会重试。`)
      } finally {
        setTownChunksForced(server, layout, false)
        townGenerationPending = false
      }
    })
  } catch (error) {
    console.error(`${TOWN_LOG} could not schedule generation: ${error}`)
    tellOnlinePlayers(server, `新手村生成任务启动失败：${error}。请重新进入世界重试。`)
    setTownChunksForced(server, layout, false)
    townGenerationPending = false
  }
})
