// Generate the complete Medieval Town once near the randomized Overworld spawn.

const TOWN_VERSION_KEY = 'tnc_initial_town_generation_version'
const PLAYER_ARRIVAL_VERSION_KEY = 'tnc_initial_town_arrival_version'
const TOWN_LOG = '[TN-C Town]'
const TOWN_CANDIDATE_DISTANCE = 384
const TOWN_SAMPLE_STEP = 48
const TOWN_SURVEY_WAIT_TICKS = 40
const $HeightmapTypes = Java.loadClass('net.minecraft.world.level.levelgen.Heightmap$Types')
const $BlockPos = Java.loadClass('net.minecraft.core.BlockPos')

let townGenerationPending = false
let townActiveForceArea = null
let townSurveyForcePositions = []

function townManifest() {
  return global.TNC_TOWN_MANIFEST
}

function townVersion() {
  var manifest = townManifest()
  return manifest ? manifest.version : 4
}

function townCommand(server, command) {
  return server.runCommandSilent(`execute in minecraft:overworld run ${command}`)
}

function townSamplePositions(originX, originZ, manifest) {
  var positions = []
  var seenChunks = {}

  function addPosition(x, z) {
    var chunkX = Math.floor(x / 16)
    var chunkZ = Math.floor(z / 16)
    var key = `${chunkX},${chunkZ}`
    if (seenChunks[key]) return
    seenChunks[key] = true
    positions.push([x, z])
  }

  for (var localX = 0; localX < manifest.dimensions[0]; localX += TOWN_SAMPLE_STEP) {
    for (var localZ = 0; localZ < manifest.dimensions[2]; localZ += TOWN_SAMPLE_STEP) {
      addPosition(originX + localX, originZ + localZ)
    }
  }

  // Include the far corner when the dimensions are not multiples of the sample step.
  addPosition(originX + manifest.dimensions[0] - 1, originZ + manifest.dimensions[2] - 1)
  return positions
}

function forceTownSurveyPositions(server, positions, forced) {
  var action = forced ? 'add' : 'remove'
  if (forced) townSurveyForcePositions = []
  for (var positionIndex = 0; positionIndex < positions.length; positionIndex++) {
    var position = positions[positionIndex]
    var result = townCommand(server, `forceload ${action} ${position[0]} ${position[1]}`)
    if (forced && result <= 0) {
      throw new Error(`could not load survey chunk at ${position[0]},${position[1]} (result=${result})`)
    }
    if (forced) townSurveyForcePositions.push(position)
  }
  if (!forced) townSurveyForcePositions = []
}

function releaseTownSurveyPositions(server) {
  if (townSurveyForcePositions.length <= 0) return
  var positions = townSurveyForcePositions
  townSurveyForcePositions = []
  for (var positionIndex = 0; positionIndex < positions.length; positionIndex++) {
    var position = positions[positionIndex]
    townCommand(server, `forceload remove ${position[0]} ${position[1]}`)
  }
}

function sampleTownCandidate(overworld, originX, originZ, manifest) {
  var minimumHeight = 320
  var maximumHeight = -64
  var waterSamples = 0
  var sampleCount = 0

  for (var localX = 0; localX < manifest.dimensions[0]; localX += TOWN_SAMPLE_STEP) {
    for (var localZ = 0; localZ < manifest.dimensions[2]; localZ += TOWN_SAMPLE_STEP) {
      var sampleX = originX + localX
      var sampleZ = originZ + localZ
      // getHeight returns the minimum build height for an unloaded chunk. The survey
      // force-loads first; getChunk is a final synchronous guard before reading it.
      overworld.getChunk(Math.floor(sampleX / 16), Math.floor(sampleZ / 16))
      var surfaceAirY = overworld.getHeight($HeightmapTypes.MOTION_BLOCKING_NO_LEAVES, sampleX, sampleZ)
      var groundY = surfaceAirY - 1
      minimumHeight = Math.min(minimumHeight, groundY)
      maximumHeight = Math.max(maximumHeight, groundY)
      sampleCount++

      var fluid = overworld.getFluidState(new $BlockPos(sampleX, groundY, sampleZ))
      if (!fluid.isEmpty()) waterSamples++
    }
  }

  // Sample the far edge when the dimensions are not a multiple of 48.
  var edgeX = originX + manifest.dimensions[0] - 1
  var edgeZ = originZ + manifest.dimensions[2] - 1
  overworld.getChunk(Math.floor(edgeX / 16), Math.floor(edgeZ / 16))
  var edgeAirY = overworld.getHeight($HeightmapTypes.MOTION_BLOCKING_NO_LEAVES, edgeX, edgeZ)
  var edgeGroundY = edgeAirY - 1
  minimumHeight = Math.min(minimumHeight, edgeGroundY)
  maximumHeight = Math.max(maximumHeight, edgeGroundY)
  sampleCount++
  var edgeFluid = overworld.getFluidState(new $BlockPos(edgeX, edgeGroundY, edgeZ))
  if (!edgeFluid.isEmpty()) waterSamples++

  var heightRange = maximumHeight - minimumHeight
  return {
    originX: originX,
    originZ: originZ,
    minimumHeight: minimumHeight,
    maximumHeight: maximumHeight,
    heightRange: heightRange,
    waterSamples: waterSamples,
    sampleCount: sampleCount,
    score: heightRange * 20 + waterSamples * 50
  }
}

function townCandidates(overworld, manifest) {
  var spawn = overworld.getSharedSpawnPos()
  var centerLocal = manifest.centerLocal
  var offsets = [
    [TOWN_CANDIDATE_DISTANCE, 0],
    [-TOWN_CANDIDATE_DISTANCE, 0],
    [0, TOWN_CANDIDATE_DISTANCE],
    [0, -TOWN_CANDIDATE_DISTANCE],
    [TOWN_CANDIDATE_DISTANCE, TOWN_CANDIDATE_DISTANCE],
    [TOWN_CANDIDATE_DISTANCE, -TOWN_CANDIDATE_DISTANCE],
    [-TOWN_CANDIDATE_DISTANCE, TOWN_CANDIDATE_DISTANCE],
    [-TOWN_CANDIDATE_DISTANCE, -TOWN_CANDIDATE_DISTANCE]
  ]
  var candidates = []
  for (var candidateIndex = 0; candidateIndex < offsets.length; candidateIndex++) {
    var candidateOffset = offsets[candidateIndex]
    candidates.push({
      originX: spawn.x + candidateOffset[0] - centerLocal[0],
      originZ: spawn.z + candidateOffset[1] - centerLocal[2]
    })
  }
  return candidates
}

function finishTownSurvey(overworld, manifest, best) {
  var minimumBuildHeight = overworld.getMinBuildHeight()
  var maximumBuildHeight = overworld.getMaxBuildHeight()
  if (!best.buildHeightValid) {
    throw new Error(`no candidate fits build height ${minimumBuildHeight}..${maximumBuildHeight - 1}`)
  }

  var originY = best.maximumHeight - manifest.sourceGroundLocalY
  return {
    originX: best.originX,
    originY: originY,
    originZ: best.originZ,
    centerX: best.originX + manifest.centerLocal[0],
    centerY: originY + manifest.centerLocal[1],
    centerZ: best.originZ + manifest.centerLocal[2],
    arrivalX: best.originX + manifest.arrivalLocal[0],
    arrivalY: originY + manifest.arrivalLocal[1],
    arrivalZ: best.originZ + manifest.arrivalLocal[2],
    terrainRange: best.heightRange,
    waterSamples: best.waterSamples,
    sampleCount: best.sampleCount
  }
}

function surveyTownCandidates(server, overworld, manifest, candidates, candidateIndex, best, onComplete) {
  if (candidateIndex >= candidates.length) {
    try {
      onComplete(finishTownSurvey(overworld, manifest, best))
    } catch (surveyFinishError) {
      failTownGeneration(server, { originX: 0, originY: 0, originZ: 0 }, 'could not finish terrain survey', surveyFinishError)
    }
    return
  }

  var definition = candidates[candidateIndex]
  var positions = townSamplePositions(definition.originX, definition.originZ, manifest)
  try {
    forceTownSurveyPositions(server, positions, true)
  } catch (loadError) {
    releaseTownSurveyPositions(server)
    throw loadError
  }

  server.scheduleInTicks(TOWN_SURVEY_WAIT_TICKS, scheduledSurvey => {
    try {
      var candidate = sampleTownCandidate(overworld, definition.originX, definition.originZ, manifest)
      var minimumBuildHeight = overworld.getMinBuildHeight()
      var maximumBuildHeight = overworld.getMaxBuildHeight()
      var candidateOriginY = candidate.maximumHeight - manifest.sourceGroundLocalY
      var candidateTopY = candidateOriginY + manifest.dimensions[1] - 1
      candidate.buildHeightValid = candidateOriginY >= minimumBuildHeight && candidateTopY < maximumBuildHeight
      if (!candidate.buildHeightValid) candidate.score += 1000000
      console.info(`${TOWN_LOG} candidate ${candidateIndex + 1}/${candidates.length} origin=${candidate.originX},${candidateOriginY},${candidate.originZ} top=${candidateTopY} height=${candidate.minimumHeight}..${candidate.maximumHeight} water=${candidate.waterSamples}/${candidate.sampleCount} valid=${candidate.buildHeightValid} score=${candidate.score}`)
      if (best == null || candidate.score < best.score) best = candidate
      releaseTownSurveyPositions(server)
      server.scheduleInTicks(1, scheduledNextCandidate => {
        surveyTownCandidates(server, overworld, manifest, candidates, candidateIndex + 1, best, onComplete)
      })
    } catch (surveyError) {
      releaseTownSurveyPositions(server)
      failTownGeneration(server, { originX: 0, originY: 0, originZ: 0 }, `candidate ${candidateIndex + 1} survey failed`, surveyError)
    }
  })
}

function forceTownArea(server, layout, operation, forced) {
  var localX = operation[1]
  var localZ = operation[2]
  var sizeX = operation[3]
  var sizeZ = operation[4]
  var x0 = layout.originX + localX
  var z0 = layout.originZ + localZ
  var x1 = x0 + sizeX - 1
  var z1 = z0 + sizeZ - 1
  var action = forced ? 'add' : 'remove'
  var result = townCommand(server, `forceload ${action} ${x0} ${z0} ${x1} ${z1}`)
  console.info(`${TOWN_LOG} forceload ${action} ${x0},${z0} -> ${x1},${z1}: result=${result}`)
  townActiveForceArea = forced ? operation : null
}

function releaseActiveTownArea(server, layout) {
  if (townActiveForceArea != null) {
    forceTownArea(server, layout, townActiveForceArea, false)
    townActiveForceArea = null
  }
}

function saveTownCoordinates(server, layout, manifest) {
  var data = server.persistentData
  data.putInt(TOWN_VERSION_KEY, manifest.version)
  data.putInt('tnc_initial_town_x', layout.centerX)
  data.putInt('tnc_initial_town_y', layout.centerY)
  data.putInt('tnc_initial_town_z', layout.centerZ)
  data.putInt('tnc_initial_town_arrival_x', layout.arrivalX)
  data.putInt('tnc_initial_town_arrival_y', layout.arrivalY)
  data.putInt('tnc_initial_town_arrival_z', layout.arrivalZ)
}

function tellTownCoordinates(player, server) {
  var data = server.persistentData
  var centerX = data.getInt('tnc_initial_town_x')
  var centerY = data.getInt('tnc_initial_town_y')
  var centerZ = data.getInt('tnc_initial_town_z')
  var arrivalX = data.getInt('tnc_initial_town_arrival_x')
  var arrivalY = data.getInt('tnc_initial_town_arrival_y')
  var arrivalZ = data.getInt('tnc_initial_town_arrival_z')

  player.tell(`新手村中心坐标：X ${centerX} / Y ${centerY} / Z ${centerZ}`)
  player.tell(`新手村南门坐标：X ${arrivalX} / Y ${arrivalY} / Z ${arrivalZ}`)
}

function sendPlayerToTown(player, server) {
  tellTownCoordinates(player, server)
  if (player.persistentData.getInt(PLAYER_ARRIVAL_VERSION_KEY) >= townVersion()) return

  var data = server.persistentData
  var x = data.getInt('tnc_initial_town_arrival_x')
  var y = data.getInt('tnc_initial_town_arrival_y')
  var z = data.getInt('tnc_initial_town_arrival_z')
  var username = player.profile.name

  player.teleportTo('minecraft:overworld', x + 0.5, y, z + 0.5, 180, 0)
  var spawnResult = server.runCommandSilent(`spawnpoint ${username} ${x} ${y} ${z}`)
  player.persistentData.putInt(PLAYER_ARRIVAL_VERSION_KEY, townVersion())
  player.tell(`已传送至完整新手村南门，并设置重生点。spawnpoint result=${spawnResult}`)
  console.info(`${TOWN_LOG} sent ${username} to ${x},${y},${z}; spawnpoint result=${spawnResult}`)
}

function tellOnlinePlayers(server, message) {
  var players = server.players
  for (var playerIndex = 0; playerIndex < players.size(); playerIndex++) {
    try {
      players.get(playerIndex).tell(message)
    } catch (notifyError) {
      console.error(`${TOWN_LOG} could not notify an online player: ${notifyError}`)
    }
  }
}

function sendOnlinePlayersToTown(server) {
  var players = server.players
  for (var playerIndex = 0; playerIndex < players.size(); playerIndex++) {
    try {
      sendPlayerToTown(players.get(playerIndex), server)
    } catch (arrivalError) {
      console.error(`${TOWN_LOG} could not send an online player to town: ${arrivalError}`)
    }
  }
}

function failTownGeneration(server, layout, message, error) {
  console.error(`${TOWN_LOG} ${message}: ${error}`)
  tellOnlinePlayers(server, `完整新手村生成失败：${error}。未写入成功标记，下次进入会重试。`)
  releaseTownSurveyPositions(server)
  releaseActiveTownArea(server, layout)
  townGenerationPending = false
}

function finishTownGeneration(server, layout, manifest) {
  releaseActiveTownArea(server, layout)
  townCommand(server, `fill ${layout.arrivalX - 1} ${layout.arrivalY - 1} ${layout.arrivalZ - 1} ${layout.arrivalX + 1} ${layout.arrivalY - 1} ${layout.arrivalZ + 1} minecraft:grass_block`)
  townCommand(server, `fill ${layout.arrivalX - 1} ${layout.arrivalY} ${layout.arrivalZ - 1} ${layout.arrivalX + 1} ${layout.arrivalY + 2} ${layout.arrivalZ + 1} minecraft:air`)

  saveTownCoordinates(server, layout, manifest)
  townGenerationPending = false
  console.info(`${TOWN_LOG} generation complete; pieces=${manifest.pieceCount}; blocks=${manifest.nonAirBlocks}; blockEntities=${manifest.blockEntities}; center=${layout.centerX},${layout.centerY},${layout.centerZ}; arrival=${layout.arrivalX},${layout.arrivalY},${layout.arrivalZ}`)
  tellOnlinePlayers(server, `完整新手村 ${manifest.pieceCount} 个结构分块已全部生成成功。`)
  sendOnlinePlayersToTown(server)
}

function runTownOperation(server, layout, manifest, operationIndex, placedPieces, lastProgress) {
  if (operationIndex >= manifest.operations.length) {
    finishTownGeneration(server, layout, manifest)
    return
  }

  try {
    var operation = manifest.operations[operationIndex]
    var kind = operation[0]
    var nextPlacedPieces = placedPieces
    var nextProgress = lastProgress

    if (kind == 'load') {
      forceTownArea(server, layout, operation, true)
    } else if (kind == 'wait') {
      server.scheduleInTicks(operation[1], scheduledWait => {
        runTownOperation(server, layout, manifest, operationIndex + 1, nextPlacedPieces, nextProgress)
      })
      return
    } else if (kind == 'clear') {
      var clearResult = townCommand(server, `fill ${layout.originX + operation[1]} ${layout.originY + operation[2]} ${layout.originZ + operation[3]} ${layout.originX + operation[4]} ${layout.originY + operation[5]} ${layout.originZ + operation[6]} minecraft:air`)
      console.info(`${TOWN_LOG} clear ${operationIndex + 1}/${manifest.operations.length}: result=${clearResult}`)
    } else if (kind == 'place') {
      var pieceName = operation[1]
      var pieceX = layout.originX + operation[2]
      var pieceY = layout.originY + operation[3]
      var pieceZ = layout.originZ + operation[4]
      var placeResult = townCommand(server, `place template tnc:medieval_town/${pieceName} ${pieceX} ${pieceY} ${pieceZ}`)
      nextPlacedPieces++
      console.info(`${TOWN_LOG} place ${nextPlacedPieces}/${manifest.pieceCount} ${pieceName} at ${pieceX} ${pieceY} ${pieceZ}: result=${placeResult}`)
      if (placeResult <= 0) {
        failTownGeneration(server, layout, `piece ${pieceName} failed`, `command result=${placeResult}`)
        return
      }
      var progress = Math.floor(nextPlacedPieces * 10 / manifest.pieceCount) * 10
      if (progress > nextProgress && progress < 100) {
        tellOnlinePlayers(server, `完整新手村生成进度：${progress}%（${nextPlacedPieces}/${manifest.pieceCount}）`)
        nextProgress = progress
      }
    } else if (kind == 'unload') {
      forceTownArea(server, layout, operation, false)
    } else {
      failTownGeneration(server, layout, 'unknown operation', kind)
      return
    }

    server.scheduleInTicks(1, scheduledStep => {
      runTownOperation(server, layout, manifest, operationIndex + 1, nextPlacedPieces, nextProgress)
    })
  } catch (operationError) {
    failTownGeneration(server, layout, `operation ${operationIndex + 1} failed`, operationError)
  }
}

PlayerEvents.loggedIn(event => {
  var server = event.server
  var player = event.player
  var manifest = townManifest()

  if (!manifest || !manifest.operations || manifest.pieceCount <= 0) {
    player.tell('完整新手村生成失败：运行清单未加载。请完整重启客户端后重试。')
    console.error(`${TOWN_LOG} runtime manifest is unavailable`)
    return
  }

  if (server.persistentData.getInt(TOWN_VERSION_KEY) >= manifest.version) {
    sendPlayerToTown(player, server)
    return
  }

  if (townGenerationPending) {
    player.tell('完整新手村正在生成，请稍候；完成后会显示精确坐标。')
    return
  }

  var overworld = server.getLevel('minecraft:overworld')
  if (!overworld) {
    player.tell('完整新手村生成失败：主世界尚未加载。请重新进入世界重试。')
    console.error(`${TOWN_LOG} generation aborted: Overworld is unavailable`)
    return
  }

  townGenerationPending = true
  player.tell('正在加载并勘测出生点附近的新手村候选地形，请稍候约 20 秒。')
  try {
    var candidates = townCandidates(overworld, manifest)
    surveyTownCandidates(server, overworld, manifest, candidates, 0, null, layout => {
      try {
        player.tell(`正在分批生成完整新手村：${manifest.pieceCount} 个结构分块、${manifest.nonAirBlocks} 个方块，预计约 40 秒，配置较低时可能需要数分钟。`)
        player.tell(`已选择附近地形：高度差 ${layout.terrainRange}，水面采样 ${layout.waterSamples}/${layout.sampleCount}。`)
        console.info(`${TOWN_LOG} generation start; version=${manifest.version}; origin=${layout.originX},${layout.originY},${layout.originZ}; operations=${manifest.operations.length}`)
        runTownOperation(server, layout, manifest, 0, 0, 0)
      } catch (generationStartError) {
        failTownGeneration(server, layout, 'could not start generation after survey', generationStartError)
      }
    })
  } catch (startError) {
    failTownGeneration(server, { originX: 0, originY: 0, originZ: 0 }, 'could not start generation', startError)
  }
})
