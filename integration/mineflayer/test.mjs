import crypto from 'node:crypto'
import mineflayer from 'mineflayer'

const port = Number(process.env.CLIPBOARDCUI_E2E_PORT ?? 25565)
const timeoutMs = 20_000
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))

// Same as Java's UUID.nameUUIDFromBytes("ClipboardCUI:clipboard").
const REGION_ID = (() => {
  const hash = crypto.createHash('md5').update('ClipboardCUI:clipboard').digest()
  hash[6] = (hash[6] & 0x0f) | 0x30
  hash[8] = (hash[8] & 0x3f) | 0x80
  const hex = hash.toString('hex')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
})()

function assert(condition, message) {
  if (!condition) throw new Error(message)
}

function assertEqual(actual, expected, message) {
  const a = JSON.stringify(actual)
  const e = JSON.stringify(expected)
  if (a !== e) throw new Error(`${message}\n  expected: ${e}\n  actual:   ${a}`)
}

function createBot(username) {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port, username, version: '1.21.11', auth: 'offline' })
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error(`${username} did not spawn`)), timeoutMs)
    bot.once('spawn', () => { clearTimeout(timeout); resolve(bot) })
    bot.once('kicked', reason => reject(new Error(`${username} kicked: ${JSON.stringify(reason)}`)))
    bot.once('error', reject)
  })
}

/** Records preview (multi-region) CUI messages received by a bot with a CUI "mod". */
class CuiRecorder {
  constructor(bot) {
    this.messages = []
    bot._client.on('custom_payload', packet => {
      if (packet.channel !== 'worldedit:cui') return
      const text = packet.data.toString('utf8')
      if (text.startsWith('+')) this.messages.push({ at: Date.now(), text })
    })
  }

  mark() {
    return this.messages.length
  }

  /** Batches (each starting with +s) received since a mark. */
  batches(since = 0) {
    const result = []
    for (const { text } of this.messages.slice(since)) {
      if (text.startsWith('+s|')) result.push([])
      if (result.length) result[result.length - 1].push(text)
    }
    return result
  }

  async waitForBatch(since, predicate, description) {
    const deadline = Date.now() + timeoutMs
    while (Date.now() < deadline) {
      // A batch is complete once its +col (and for cuboids +grid) arrived; wait one more tick.
      const found = this.batches(since).find(batch => predicate(batch) && batch.some(m => m.startsWith('+col|')))
      if (found) {
        await sleep(100)
        return this.batches(since).find(batch => batch[0] === found[0] && predicate(batch))
      }
      await sleep(25)
    }
    throw new Error(`Timed out waiting for ${description}; got ${JSON.stringify(this.batches(since))}`)
  }
}

const MOVE_PACKETS = new Set(['entity_teleport', 'sync_entity_position', 'teleport_entity', 'rel_entity_move'])
const COUNTED_PACKETS = new Set(['spawn_entity', 'entity_destroy', 'entity_metadata', 'set_passengers',
  'bundle_delimiter', 'custom_payload', ...MOVE_PACKETS])

/**
 * Counts the clientbound packets that render the preview. Spawns and movement only count for
 * Text Display entities (lines and their root anchors), so a preview that is moved by teleporting
 * its entities is measured the same way as one that is respawned, and mobs are ignored.
 */
class PacketCounter {
  constructor(bot) {
    this.counts = new Map()
    this.spawns = 0
    this.textDisplaySpawns = 0
    this.moves = 0
    this.renderedIds = new Set()
    const textDisplayId = bot.registry.entitiesByName.text_display.id
    bot._client.on('packet', (data, meta) => {
      if (meta.name === 'spawn_entity' && data.type === textDisplayId) {
        this.renderedIds.add(data.entityId)
        this.spawns++
        this.textDisplaySpawns++
      }
      if (MOVE_PACKETS.has(meta.name)) {
        if (!this.renderedIds.has(data.entityId)) return
        this.moves++
      }
      if (COUNTED_PACKETS.has(meta.name)) this.counts.set(meta.name, (this.counts.get(meta.name) ?? 0) + 1)
    })
  }

  snapshot() {
    return { counts: new Map(this.counts), spawns: this.spawns, textDisplaySpawns: this.textDisplaySpawns, moves: this.moves }
  }

  since(snapshot) {
    const diff = {}
    let total = 0
    for (const [name, count] of this.counts) {
      const delta = count - (snapshot.counts.get(name) ?? 0)
      if (delta > 0) {
        diff[name] = delta
        total += delta
      }
    }
    const spawns = this.spawns - snapshot.spawns
    const moves = this.moves - snapshot.moves
    return {
      ...diff,
      entityAndCuiPackets: total,
      textDisplaySpawns: this.textDisplaySpawns - snapshot.textDisplaySpawns,
      previewSpawns: spawns,
      previewMoves: moves,
      previewUpdates: spawns + moves
    }
  }
}

async function command(bot, text) {
  bot.chat(text)
  await sleep(350) // stay well below the chat spam limit
}

function liveTextDisplays(bot) {
  return Object.values(bot.entities).filter(entity => entity.name === 'text_display').length
}

async function walk(bot, ms) {
  bot.setControlState('forward', true)
  bot.setControlState('sprint', true)
  await sleep(ms)
  bot.clearControlStates()
}

function cuboid(min, max) {
  const volume = (max[0] - min[0] + 1) * (max[1] - min[1] + 1) * (max[2] - min[2] + 1)
  return [
    `+s|cuboid|${REGION_ID}`,
    `+p|0|${min.join('|')}|${volume}`,
    `+p|1|${max.join('|')}|${volume}`,
    '+col|#55FFFFFF|#FFFFFF80|#00000000|#00000000',
    '+grid|0'
  ]
}

const results = {}

async function nativeCuiScenario() {
  const bot = await createBot('ClipNative')
  const cui = new CuiRecorder(bot)
  // Behave like WorldEditCUI: register the channel and send the protocol handshake.
  bot._client.write('custom_payload', { channel: 'minecraft:register', data: Buffer.from('worldedit:cui', 'utf8') })
  bot._client.write('custom_payload', { channel: 'worldedit:cui', data: Buffer.from('v|4', 'utf8') })
  await sleep(1500)

  await command(bot, '/tp ClipNative 12.5 -60 -24.5')
  await command(bot, '//pos1 10,-60,-20')
  await command(bot, '//pos2 14,-58,-12')
  let mark = cui.mark()
  await command(bot, '//copy')

  // Copied where the player stands, so the preview sits exactly on the copied region.
  let batch = await cui.waitForBatch(mark, b => b[0].startsWith('+s|cuboid'), 'preview after //copy')
  assertEqual(batch, cuboid([10, -60, -20], [14, -58, -12]), 'preview after //copy')

  mark = cui.mark()
  await command(bot, '//rotate 90')
  batch = await cui.waitForBatch(mark, b => b[0].startsWith('+s|cuboid'), 'preview after //rotate 90')
  // Offsets (dx, dz) become (-dz, dx): x [-13,-5] and z [-2,2] around (12,-60,-25).
  assertEqual(batch, cuboid([-1, -60, -27], [7, -58, -23]), 'preview after //rotate 90')

  mark = cui.mark()
  await command(bot, '//flip up')
  batch = await cui.waitForBatch(mark, b => b[0].startsWith('+s|cuboid'), 'preview after //flip up')
  assertEqual(batch, cuboid([-1, -62, -27], [7, -60, -23]), 'preview after //flip up (mirrored below the feet)')
  await command(bot, '//flip up')

  mark = cui.mark()
  await command(bot, '/tp ClipNative 30.5 -60 -24.5')
  batch = await cui.waitForBatch(mark, b => b[0].startsWith('+s|cuboid'), 'preview after teleport')
  assertEqual(batch, cuboid([17, -60, -27], [25, -58, -23]), 'preview follows the paste position')
  await sleep(1000)
  assertEqual(cui.batches(mark).length, 1, 'a teleport sends exactly one update')

  // settle (default): nothing while walking, one update after stopping.
  await sleep(500)
  mark = cui.mark()
  await walk(bot, 2000)
  const duringSettleWalk = cui.batches(mark).length
  await sleep(1200)
  const settleTotal = cui.batches(mark).length
  results.settle = { duringWalk: duringSettleWalk, afterStop: settleTotal - duringSettleWalk }
  // A server or bot stall of 6+ ticks mid-walk looks exactly like stopping, so allow one extra.
  assert(settleTotal >= 1 && settleTotal <= 2, `settle mode sent ${settleTotal} updates for one 2 s walk`)
  assert(settleTotal - duringSettleWalk >= 1, 'settle mode did not send after stopping')

  await command(bot, '/clipboardcui mode follow')
  await sleep(300)
  mark = cui.mark()
  await walk(bot, 2000)
  await sleep(600)
  const followBatches = cui.batches(mark).length
  results.follow = { batchesFor2sWalk: followBatches, messages: cui.messages.length - mark }
  // 40 ticks at a 5 tick minimum interval: at most 8 + the trailing update.
  assert(followBatches >= 3 && followBatches <= 9, `follow mode sent ${followBatches} updates for a 2 s walk`)
  await command(bot, '/clipboardcui mode settle')

  mark = cui.mark()
  await command(bot, '//rotate 45')
  batch = await cui.waitForBatch(mark, b => b[0].startsWith('+s|polyhedron'), 'polyhedron after //rotate 45')
  assertEqual(batch.length, 16, 'off-axis preview is a polyhedron with 8 vertices and 6 faces')
  assertEqual(batch.filter(m => m.startsWith('+poly|')).length, 6, 'six quad faces')
  results.offAxis = batch
  await command(bot, '//rotate -45')

  mark = cui.mark()
  await command(bot, '/clipboardcui off')
  const deadline = Date.now() + timeoutMs
  while (!cui.messages.slice(mark).some(m => m.text === `+s|clear|${REGION_ID}`)) {
    assert(Date.now() < deadline, 'no clear message after /clipboardcui off')
    await sleep(25)
  }
  assert(!cui.messages.slice(mark).some(m => m.text === '+s|clear'), 'must never clear all multi regions')

  mark = cui.mark()
  await command(bot, '/clipboardcui on')
  await cui.waitForBatch(mark, b => b[0].startsWith('+s|cuboid'), 'preview after /clipboardcui on')

  // A repeated handshake (WorldEditCUI does this on world change) drops client regions.
  await sleep(500)
  mark = cui.mark()
  bot._client.write('custom_payload', { channel: 'worldedit:cui', data: Buffer.from('v|4', 'utf8') })
  await cui.waitForBatch(mark, b => b[0].startsWith('+s|cuboid'), 'resend after handshake')

  if (process.env.CLIPBOARDCUI_E2E_EDITOR === 'fawe') await faweRandomRotation(bot, cui)

  mark = cui.mark()
  await command(bot, '/clearclipboard')
  const clearDeadline = Date.now() + timeoutMs
  while (!cui.messages.slice(mark).some(m => m.text === `+s|clear|${REGION_ID}`)) {
    assert(Date.now() < clearDeadline, 'no clear message after /clearclipboard')
    await sleep(25)
  }

  bot.quit()
}

/**
 * FAWE '//schem load -r -d' re-rolls a Y rotation on every paste. The preview must cover all
 * four outcomes and must not advance the roll itself (pastes are what change it).
 */
async function faweRandomRotation(bot, cui) {
  await command(bot, '/tp ClipNative 12.5 -60 -24.5')
  await command(bot, '//schem save clipboardcui-e2e -f')
  await sleep(1000)
  let mark = cui.mark()
  await command(bot, '//schem load clipboardcui-e2e -r -d')
  // Copy offsets x [-2,2], z [5,13] (in any quarter turn) sweep x and z over [-13,13].
  const batch = await cui.waitForBatch(mark, b => b[0].startsWith('+s|cuboid')
    && b[1] === `+p|0|-1|-60|-38|${27 * 3 * 27}`, 'random rotation union')
  assertEqual(batch, cuboid([-1, -60, -38], [25, -58, -12]), 'random rotation preview covers all four rotations')

  await sleep(500)
  mark = cui.mark()
  for (let i = 0; i < 3; i++) {
    await command(bot, '/tp ClipNative 12.5 -40 -24.5') // paste in the air, then come back
    await command(bot, '//paste')
    await command(bot, '/tp ClipNative 12.5 -60 -24.5')
  }
  await sleep(1500)
  // Teleporting moves the preview, but its shape must stay the four-rotation union.
  const volume = 27 * 3 * 27
  for (const update of cui.batches(mark)) {
    assert(update[0].startsWith('+s|cuboid') && update[1].endsWith(`|${volume}`),
      `random rotation preview changed shape after a paste: ${JSON.stringify(update)}`)
  }
  results.faweRandomRotation = batch
}

async function worldEditDisplayScenario() {
  const bot = await createBot('ClipWed')
  const packets = new PacketCounter(bot)
  const cui = new CuiRecorder(bot)
  await sleep(2500) // WorldEditDisplay emulates the CUI handshake one second after join
  const debugLog = []
  bot.on('messagestr', message => {
    if (message.includes('[Debug]')) debugLog.push(`${Date.now() % 100000} ${message.replace(/^.*\[Debug\] /, '')}`)
  })
  await command(bot, '/wedisplay debug')

  await command(bot, '/tp ClipWed 12.5 -60 40.5')
  await command(bot, '//pos1 10,-60,45')
  await command(bot, '//pos2 14,-58,53')
  await command(bot, '//copy')
  await command(bot, '//desel') // leave only the clipboard preview
  await sleep(2000)
  const previewEntities = liveTextDisplays(bot)
  assert(previewEntities > 0, 'WorldEditDisplay did not render the clipboard preview')
  assertEqual(cui.messages.length, 0, 'WorldEditDisplay must consume the CUI messages instead of forwarding them')

  let snapshot = packets.snapshot()
  await command(bot, '/tp ClipWed 30.5 -60 40.5')
  await sleep(2000)
  const teleportUpdate = packets.since(snapshot)
  assert(teleportUpdate.previewUpdates > 0, 'the preview did not move after a teleport')
  assertEqual(liveTextDisplays(bot), previewEntities, 'the moved preview has the same entity count')

  snapshot = packets.snapshot()
  await walk(bot, 2000)
  const duringWalk = packets.since(snapshot)
  // Generating new chunks can slow the server down, so wait for the update instead of a fixed time.
  for (let waited = 0; waited < 6000 && packets.since(snapshot).previewUpdates === 0; waited += 100) await sleep(100)
  await sleep(500)
  const walkTotal = packets.since(snapshot)

  results.worldEditDisplay = { previewEntities, teleportUpdate, settleWalk: { duringWalk, total: walkTotal } }
  results.worldEditDisplay.debugLog = debugLog
  results.worldEditDisplay.position = bot.entity.position.toString()
  assert(duringWalk.previewUpdates === 0, `settle mode updated the preview while walking: ${JSON.stringify(duringWalk)}`)
  assert(walkTotal.previewUpdates > 0, 'preview was not updated after the walk ended')

  snapshot = packets.snapshot()
  await command(bot, '/clipboardcui mode follow')
  await walk(bot, 2000)
  await sleep(1500)
  results.worldEditDisplay.followWalk = packets.since(snapshot)
  assert(results.worldEditDisplay.followWalk.previewUpdates > walkTotal.previewUpdates,
    'follow mode should update while walking (debounce must not starve)')

  bot.quit()
}

try {
  await nativeCuiScenario()
  await worldEditDisplayScenario()
  console.log('CLIPBOARDCUI_E2E_RESULTS ' + JSON.stringify(results, null, 2))
  console.log('ClipboardCUI e2e passed')
  process.exit(0)
} catch (error) {
  console.error(error)
  console.error('partial results: ' + JSON.stringify(results, null, 2))
  process.exit(1)
}
