const fs = require('fs')
const vm = require('vm')

if (process.argv.length < 4 || process.argv.length > 5) {
  throw new Error('usage: node test_initial_town_runtime.js <manifest.js> <initial_town.js> [failed-place-number]')
}

const manifestPath = process.argv[2]
const scriptPath = process.argv[3]
const failedPlaceNumber = process.argv[4] ? Number(process.argv[4]) : 0
const loginHandlers = []
const scheduled = []
const commands = []
const errors = []
const messages = []
const teleports = []
const forcedSurveyChunks = new Set()

class BlockPos {
  constructor(x, y, z) {
    this.x = x
    this.y = y
    this.z = z
  }
}

function integerData() {
  const values = new Map()
  return {
    getInt: key => values.get(key) || 0,
    putInt: (key, value) => values.set(key, value),
    values
  }
}

const playerData = integerData()
const serverData = integerData()
const player = {
  profile: { name: 'RuntimeTestPlayer' },
  persistentData: playerData,
  tell: message => messages.push(String(message)),
  teleportTo: (...args) => teleports.push(args)
}
const overworld = {
  getSharedSpawnPos: () => ({ x: 100, y: 70, z: -40 }),
  getMinBuildHeight: () => -64,
  getMaxBuildHeight: () => 320,
  getChunk: (x, z) => {
    const key = `${x},${z}`
    if (!forcedSurveyChunks.has(key)) throw new Error(`sampled unloaded chunk ${key}`)
    return { x, z }
  },
  getHeight: (_type, x, z) => forcedSurveyChunks.has(`${Math.floor(x / 16)},${Math.floor(z / 16)}`)
    ? 70 + Math.abs((x * 7 + z * 11) % 5)
    : -64,
  getFluidState: _pos => ({ isEmpty: () => true })
}
const server = {
  persistentData: serverData,
  players: { size: () => 1, get: () => player },
  getLevel: id => id === 'minecraft:overworld' ? overworld : null,
  runCommandSilent: command => {
    commands.push(command)
    const surveyForce = command.match(/forceload (add|remove) (-?\d+) (-?\d+)$/)
    if (surveyForce) {
      const key = `${Math.floor(Number(surveyForce[2]) / 16)},${Math.floor(Number(surveyForce[3]) / 16)}`
      if (surveyForce[1] === 'add') forcedSurveyChunks.add(key)
      else forcedSurveyChunks.delete(key)
    }
    if (failedPlaceNumber > 0 && command.includes('place template tnc:medieval_town/')) {
      const attemptedPlaces = commands.filter(item => item.includes('place template tnc:medieval_town/')).length
      if (attemptedPlaces === failedPlaceNumber) return 0
    }
    return 1
  },
  scheduleInTicks: (_ticks, callback) => scheduled.push(callback)
}

const sandbox = {
  global: {},
  Math,
  Java: {
    loadClass: name => {
      if (name.endsWith('Heightmap$Types')) return { MOTION_BLOCKING_NO_LEAVES: 'MOTION_BLOCKING_NO_LEAVES' }
      if (name.endsWith('BlockPos')) return BlockPos
      throw new Error(`unexpected Java class: ${name}`)
    }
  },
  PlayerEvents: { loggedIn: callback => loginHandlers.push(callback) },
  console: {
    info: () => {},
    error: message => errors.push(String(message))
  }
}

vm.createContext(sandbox)
vm.runInContext(fs.readFileSync(manifestPath, 'utf8'), sandbox, { filename: manifestPath })
vm.runInContext(fs.readFileSync(scriptPath, 'utf8'), sandbox, { filename: scriptPath })

if (loginHandlers.length !== 1) throw new Error(`expected one login handler, found ${loginHandlers.length}`)
loginHandlers[0]({ server, player })

let scheduledSteps = 0
while (scheduled.length > 0) {
  const callback = scheduled.shift()
  callback()
  scheduledSteps++
  if (scheduledSteps > 2000) throw new Error('runtime did not finish within 2000 scheduled callbacks')
}

const places = commands.filter(command => command.includes('place template tnc:medieval_town/'))
const pieceNames = new Set(places.map(command => command.match(/medieval_town\/(\S+)/)[1]))
const loads = commands.filter(command => command.includes('forceload add'))
const unloads = commands.filter(command => command.includes('forceload remove'))
const buildLoads = loads.filter(command => /forceload add -?\d+ -?\d+ -?\d+ -?\d+$/.test(command))
const buildUnloads = unloads.filter(command => /forceload remove -?\d+ -?\d+ -?\d+ -?\d+$/.test(command))
const clears = commands.filter(command => command.includes(' run fill ') && command.endsWith(' minecraft:air'))
const manifest = sandbox.global.TNC_TOWN_MANIFEST

const checks = failedPlaceNumber > 0 ? [
  [errors.some(message => message.includes('command result=0')), `expected failure log, got: ${errors.join('; ')}`],
  [places.length === failedPlaceNumber, `places=${places.length}, expected failed attempt=${failedPlaceNumber}`],
  [pieceNames.size === failedPlaceNumber, `unique pieces=${pieceNames.size}`],
  [loads.length === unloads.length, `force-load leak: loads=${loads.length}, unloads=${unloads.length}`],
  [serverData.getInt('tnc_initial_town_generation_version') === 0, 'failed generation wrote a success version'],
  [teleports.length === 0, `failed generation teleported ${teleports.length} time(s)`],
  [messages.some(message => message.includes('完整新手村生成失败')), 'failure message missing']
] : [
  [errors.length === 0, `runtime errors: ${errors.join('; ')}`],
  [places.length === manifest.pieceCount, `places=${places.length}, expected=${manifest.pieceCount}`],
  [pieceNames.size === manifest.pieceCount, `unique pieces=${pieceNames.size}`],
  [buildLoads.length === manifest.operationCounts.load, `build loads=${buildLoads.length}`],
  [buildUnloads.length === manifest.operationCounts.unload, `build unloads=${buildUnloads.length}`],
  [loads.length === unloads.length, `force-load leak: loads=${loads.length}, unloads=${unloads.length}`],
  [forcedSurveyChunks.size === 0, `survey force-load leak: ${forcedSurveyChunks.size} chunk(s)`],
  [clears.length === manifest.operationCounts.clear + 1, `air fills=${clears.length}`],
  [serverData.getInt('tnc_initial_town_generation_version') === manifest.version, 'generation version was not saved'],
  [teleports.length === 1, `teleports=${teleports.length}`],
  [messages.some(message => message.includes('完整新手村 100 个结构分块已全部生成成功')), 'completion message missing']
]
const failed = checks.filter(([passed]) => !passed).map(([, message]) => message)
if (failed.length > 0) throw new Error(failed.join('\n'))

console.log(JSON.stringify({
  scheduledSteps,
  places: places.length,
  uniquePieces: pieceNames.size,
  loads: loads.length,
  unloads: unloads.length,
  clears: clears.length,
  teleports: teleports.length,
  version: serverData.getInt('tnc_initial_town_generation_version'),
  failedPlaceNumber
}))
