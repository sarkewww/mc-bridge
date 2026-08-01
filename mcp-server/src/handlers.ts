import { z } from 'zod'
import * as s from './schemas.js'
import type { McBridgeClient } from './index.js'
import * as fs from 'fs'
import * as path from 'path'
import * as os from 'os'
import { execSync } from 'child_process'

const CONFIG_DIR = path.join(os.homedir(), '.config', 'mc-mcp-server')

const DEFAULT_MODELS: Record<string, string> = {
  deepseek: 'deepseek-chat',
  openai: 'gpt-4o',
  claude: 'claude-3-opus-20240229',
}

export function createHandlers(
  bridge: McBridgeClient,
  callDeepSeek: (text: string, target: string, source?: string) => Promise<string>,
  callDeepSeekAnalysis: (system: string, user: string, temp?: number, maxTokens?: number) => Promise<string>,
  requireBridge: () => Promise<void>,
  deps: {
    getDeepseekKey: () => string
    setDeepseekKey: (key: string) => void
    saveKey: (key: string) => void
    ANALYSIS_CACHE_DIR: string
    MINECRAFT_MERGED_JAR: string
    MAPPINGS_TINY: string
  }
) {
  const buildHistory: s.BuildAction[] = []

  function pushBuild(label: string, commands: string[]) {
    buildHistory.push({ type: 'build', label, commands, timestamp: Date.now() })
    if (buildHistory.length > 20) buildHistory.shift()
  }

  const workflowTimers = new Map<string, ReturnType<typeof setInterval>>()

  // QQ Bridge state
  let qqConfig: { endpoint: string; group_id: number } | null = null
  let qqTimer: ReturnType<typeof setInterval> | null = null
  let qqLastMessageId = 0

  // Discord bidirectional state
  let discordBotConfig: { bot_token: string; channel_id: string } | null = null
  let discordPollTimer: ReturnType<typeof setInterval> | null = null
  let discordLastMessageId: string | null = null

  // AFK standin state
  let afkTimer: ReturnType<typeof setInterval> | null = null
  let afkStyleDescription = ''
  let afkRepliedIds = new Set<string>()

  // Autonomous social agent state
  let socialAgentTimer: ReturnType<typeof setInterval> | null = null
  let socialAgentConfig: { aggressiveness: number; focusPlayers: string } | null = null
  let socialAgentServerContext = ''
  let socialAgentMemory: string[] = []
  let socialAgentLastAction = 'none'
  let socialStateDir: string | null = null
  async function ensureSocialStateDir() {
    if (socialStateDir && fs.existsSync(socialStateDir)) return
    socialStateDir = path.join(CONFIG_DIR, 'social')
    if (!fs.existsSync(socialStateDir!)) fs.mkdirSync(socialStateDir!, { recursive: true })
  }

  // Safe JSON.parse: returns fallback on parse failure
  function safeParse(text: string, fallback: any = {}): any {
    try { return JSON.parse(text) } catch { return fallback }
  }

  function cronMatch(parts: string[], d: Date): boolean {
    const [min, hour, dom, mon, dow] = parts
    const match = (pattern: string, val: number): boolean => {
      if (pattern === '*') return true
      for (const segment of pattern.split(',')) {
        if (segment.includes('/')) {
          const [base, step] = segment.split('/')
          const b = base === '*' ? 0 : parseInt(base, 10)
          if ((val - b) % parseInt(step, 10) === 0) return true
        } else if (segment.includes('-')) {
          const [lo, hi] = segment.split('-').map(s => parseInt(s, 10))
          if (val >= lo && val <= hi) return true
        } else if (parseInt(segment, 10) === val) return true
      }
      return false
    }
    return match(min, d.getMinutes()) && match(hour, d.getHours())
        && match(dom, d.getDate()) && match(mon, d.getMonth() + 1)
        && match(dow, d.getDay())
  }

  async function runWorkflowSteps(name: string, steps: any[], bridgeInstance: McBridgeClient) {
    try {
      for (const step of steps || []) {
        if (step.type === 'command' && step.cmd) {
          await bridgeInstance.send('exec', { cmd: step.cmd.replace('/', '') })
        } else if (step.type === 'chat' && step.msg) {
          await bridgeInstance.send('chat', { msg: step.msg })
        } else if (step.type === 'wait' && step.ms) {
          await new Promise(r => setTimeout(r, step.ms))
        }
      }
    } catch (err) {
      console.error(`[workflow] "${name}" step failed:`, err)
    }
  }

  function startWorkflowTimer(workflow: any, bridgeInstance: McBridgeClient) {
    const { name, schedule, steps } = workflow
    if (!name || !steps || steps.length === 0) return

    if (schedule === 'once') {
      setTimeout(() => runWorkflowSteps(name, steps, bridgeInstance), 0)
      return
    }

    if (schedule.startsWith('interval:')) {
      const intervalMs = parseInt(schedule.substring(9)) * 1000
      if (intervalMs <= 0) return
      const timer = setInterval(() => runWorkflowSteps(name, steps, bridgeInstance), intervalMs)
      workflowTimers.set(name, timer)
      return
    }

    if (schedule.startsWith('cron:')) {
      const expr = schedule.substring(5).trim()
      const parts = expr.split(/\s+/)
      if (parts.length === 5) {
        const timer = setInterval(async () => {
          if (cronMatch(parts, new Date())) {
            await runWorkflowSteps(name, steps, bridgeInstance)
          }
        }, 60_000)
        workflowTimers.set(name, timer)
      }
      return
    }

    console.error(`[workflow] Unknown schedule: ${schedule}`)
  }

  function stopWorkflowTimer(name: string) {
    const timer = workflowTimers.get(name)
    if (timer) {
      clearInterval(timer)
      workflowTimers.delete(name)
    }
  }

  // Restore persisted workflows on startup
  try {
    const workflowsPath = path.join(CONFIG_DIR, 'workflows.json')
    if (fs.existsSync(workflowsPath)) {
      const saved = JSON.parse(fs.readFileSync(workflowsPath, 'utf-8'))
      if (Array.isArray(saved)) {
        for (const wf of saved) {
          if (wf.enabled !== false && wf.name && wf.schedule && wf.steps) {
            startWorkflowTimer(wf, bridge)
          }
        }
      }
    }
  } catch { /* workflow restoration skipped */ }

  return {
    'mc_connect': async (args: any) => {
      bridge.stopReconnect()
      ;(bridge as any)['autoReconnect'] = true
      ;(bridge as any)['reconnectAttempts'] = 0
      const result = await bridge.connect()
      bridge.setOnDisconnect(() => {
        console.error('[mc-mcp] Bridge disconnected, auto-reconnecting...')
      })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_disconnect': async (args: any) => {
      bridge.disconnect()
      return { content: [{ type: 'text', text: 'Disconnected from bridge' }] }
    },

    'mc_ping': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('ping')
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_command': async (args: any) => {
      await requireBridge()
      const { cmd } = s.ExecSchema.parse(args)
      const result = await bridge.send('exec', { cmd })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_chat': async (args: any) => {
      await requireBridge()
      const { msg } = s.ChatSchema.parse(args)
      const result = await bridge.send('chat', { msg })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_position': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('pos')
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_direction': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('direction')
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_f3': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('f3')
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_entities': async (args: any) => {
      await requireBridge()
      const { r } = s.EntitiesSchema.parse(args ?? {})
      const result = await bridge.send('entities', r ? { r } : {})
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_nearby_players': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('nearby_players')
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_inventory': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('inv')
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_item_detail': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('item_detail', args ?? {})
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_info': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('info')
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_look_at': async (args: any) => {
      await requireBridge()
      const { x, y, z } = s.PositionSchema.parse(args)
      const result = await bridge.send('lookat', { x, y, z })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_mods': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('mods')
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_baritone': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('baritone')
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_wurst': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('wurst')
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_chatlog': async (args: any) => {
      await requireBridge()
      const params = s.ChatlogSchema.parse(args ?? {})
      const payload: Record<string, unknown> = {}
      if (params.count !== undefined) payload.count = params.count
      if (params.player !== undefined) payload.player = params.player
      if (params.keyword !== undefined) payload.keyword = params.keyword
      const result = await bridge.send('chatlog', payload)
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_clear_chat': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('clear_chat')
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_logs': async (args: any) => {
      await requireBridge()
      const { lines } = s.LogsSchema.parse(args ?? {})
      const result = await bridge.send('logs', lines ? { lines } : {})
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_baritone': async (args: any) => {
      await requireBridge()
      const { cmd } = s.BaritoneSchema.parse(args)
      const baritoneCmd = '#' + cmd
      const result = await bridge.send('chat', { msg: baritoneCmd })
      return { content: [{ type: 'text', text: `Sent baritone command: ${baritoneCmd}\n${result}` }] }
    },

    'mc_analyze_logs': async (args: any) => {
      await requireBridge()
      const { lines } = s.AnalyzeLogsSchema.parse(args ?? {})
      const logResult = await bridge.send('logs', { lines: lines ?? 200 })
      const data: { lines?: string[] } = JSON.parse(logResult)
      const allLines = data.lines ?? []
      const errors = allLines.filter((l) => /(ERROR|FATAL|Exception|at\s+|Caused by|Stacktrace)/i.test(l))
      const warnings = allLines.filter((l) => /WARN/i.test(l))
      return {
        content: [{ type: 'text', text: JSON.stringify({
          totalLines: allLines.length,
          errors: errors.slice(0, 30),
          warnings: warnings.slice(0, 30),
          hasCrash: allLines.some((l) => /CRASH|FATAL|Unrecoverable/i.test(l)),
        }, null, 2) }],
      }
    },

    'mc_mod_info': async (args: any) => {
      await requireBridge()
      const { mod_id } = s.ModInfoSchema.parse(args ?? {})
      const result = await bridge.send('mods')
      const data: { mods?: Array<{ id: string; name: string; version: string }> } = JSON.parse(result)
      const mods = data.mods ?? []
      if (mod_id) {
        const filtered = mods.filter((m) => m.id === mod_id || m.name.toLowerCase().includes(mod_id.toLowerCase()))
        return { content: [{ type: 'text', text: JSON.stringify({ count: filtered.length, mods: filtered }, null, 2) }] }
      }
      return { content: [{ type: 'text', text: JSON.stringify({ count: mods.length, mods }, null, 2) }] }
    },

    'mc_highlight_block': async (args: any) => {
      await requireBridge()
      const params = s.HighlightBlockSchema.parse(args ?? {})
      const payload: Record<string, unknown> = { action: 'add' }
      if (params.x !== undefined) payload.x = params.x
      if (params.y !== undefined) payload.y = params.y
      if (params.z !== undefined) payload.z = params.z
      if (params.r !== undefined) payload.r = params.r
      if (params.g !== undefined) payload.g = params.g
      if (params.b !== undefined) payload.b = params.b
      if (params.duration !== undefined) payload.duration = params.duration
      const result = await bridge.send('highlight', payload)
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_highlight_clear': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('highlight', { action: 'clear' })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_container': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('container')
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_entity': async (args: any) => {
      await requireBridge()
      const params = s.EntitySchema.parse(args ?? {})
      const payload: Record<string, unknown> = {}
      if (params.id !== undefined) payload.id = params.id
      if (params.look) payload.look = true
      const result = await bridge.send('entity', payload)
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_block': async (args: any) => {
      await requireBridge()
      const params = s.BlockSchema.parse(args ?? {})
      const payload: Record<string, unknown> = {}
      if (params.x !== undefined) payload.x = params.x
      if (params.y !== undefined) payload.y = params.y
      if (params.z !== undefined) payload.z = params.z
      if (params.look) payload.look = true
      const result = await bridge.send('block', payload)
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_set_intercept': async (args: any) => {
      await requireBridge()
      const params = s.InterceptSchema.parse(args ?? {})
      const payload: Record<string, unknown> = {}
      if (params.enable !== undefined) payload.enable = params.enable
      if (params.toggle) payload.toggle = true
      if (params.mode) payload.mode = params.mode
      const result = await bridge.send('intercept', payload)
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_send': async (args: any) => {
      await requireBridge()
      const { msg } = s.SendSchema.parse(args)
      const result = await bridge.send('send', { msg })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_configure_translate': async (args: any) => {
      await requireBridge()
      const params = s.ConfigureTranslateSchema.parse(args ?? {})
      if (params.api_key) deps.setDeepseekKey(params.api_key)
      const payload: Record<string, unknown> = {}
      if (params.enable !== undefined) payload.enable = params.enable
      if (params.api_key) payload.api_key = params.api_key
      if (params.target) payload.target = params.target
      const result = await bridge.send('translate', payload)
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_configure_translate_received': async (args: any) => {
      await requireBridge()
      const { enable } = args as { enable?: boolean }
      const payload: Record<string, unknown> = {}
      if (enable !== undefined) payload.enable = enable
      const result = await bridge.send('translate_received', payload)
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_translate': async (args: any) => {
      const params = s.TranslateSchema.parse(args)
      const sourceArg = params.source ? params.source : undefined
      const result = await callDeepSeek(params.text, params.target ?? 'Japanese', sourceArg)
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_entity_detail': async (args: any) => {
      await requireBridge()
      const params = s.EntitySchema.parse(args ?? {})
      const payload: Record<string, unknown> = {}
      if (params.id !== undefined) payload.id = params.id
      if (params.look) payload.look = true
      const result = await bridge.send('entity_detail', payload)
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_screen': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('screen')
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_take_screenshot': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('screenshot')
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_list_classes': async (args: any) => {
      const params = s.ListClassesSchema.parse(args ?? {})
      const query = (params.query || '').toLowerCase()
      const limit = params.limit ?? 50

      if (!fs.existsSync(deps.MAPPINGS_TINY)) {
        return { content: [{ type: 'text', text: JSON.stringify({ error: 'Mappings file not found at ' + deps.MAPPINGS_TINY }, null, 2) }], isError: true }
      }

      try {
        const content = fs.readFileSync(deps.MAPPINGS_TINY, 'utf8')
        const lines = content.split('\n')
        const results: Array<{ official: string; yarn: string; intermediary: string }> = []

        for (const line of lines) {
          if (results.length >= limit) break
          if (!line.startsWith('c\t')) continue

          const parts = line.split('\t')
          if (parts.length < 4) continue

          const official = parts[1]
          const intermediary = parts[2]
          const yarn = parts[3]
          const matchTarget = query ? yarn.toLowerCase() : ''
          if (query && !matchTarget.includes(query) && !intermediary.toLowerCase().includes(query) && !official.toLowerCase().includes(query)) continue

          results.push({ official, yarn, intermediary })
        }

        return {
          content: [{ type: 'text', text: JSON.stringify({
            query: query || '(all)',
            total: results.length,
            classes: results,
          }, null, 2) }],
        }
      } catch (e) {
        const msg = e instanceof Error ? e.message : String(e)
        return { content: [{ type: 'text', text: JSON.stringify({ error: msg }, null, 2) }], isError: true }
      }
    },

    'mc_inspect_class': async (args: any) => {
      const params = s.InspectClassSchema.parse(args)
      const className = params.class
      const verbose = params.detail ? '-verbose' : ''

      if (!fs.existsSync(deps.MINECRAFT_MERGED_JAR)) {
        return { content: [{ type: 'text', text: JSON.stringify({ error: 'Minecraft merged jar not found at ' + deps.MINECRAFT_MERGED_JAR }, null, 2) }], isError: true }
      }

      try {
        let targetOfficial = ''
        const targetYarn = className.replace(/\./g, '/')
        if (fs.existsSync(deps.MAPPINGS_TINY)) {
          const content = fs.readFileSync(deps.MAPPINGS_TINY, 'utf8')
          const lines = content.split('\n')
          for (const line of lines) {
            if (line.startsWith('c\t')) {
              const parts = line.split('\t')
              if (parts.length >= 4 && parts[3] === targetYarn) {
                targetOfficial = parts[1]
                break
              }
            }
          }
        }

        if (!targetOfficial) {
          return { content: [{ type: 'text', text: JSON.stringify({ error: 'Class ' + className + ' not found in mappings' }, null, 2) }], isError: true }
        }

        const javaHome = process.env.JAVA_HOME || 'C:\\Program Files\\Zulu\\zulu-21'
        const jarPath = deps.MINECRAFT_MERGED_JAR
        const output = execSync(
          `"${javaHome}\\bin\\javap" -cp "${jarPath}" ${verbose} "${targetOfficial}"`,
          { encoding: 'utf8', timeout: 15000 }
        )
        const yarnDotted = className
        let pretty = output.split(targetOfficial).join(yarnDotted)
        return { content: [{ type: 'text', text: pretty.substring(0, 10000) }] }
      } catch (e) {
        const msg = e instanceof Error ? e.message : String(e)
        return { content: [{ type: 'text', text: JSON.stringify({ error: msg }, null, 2) }], isError: true }
      }
    },

    'mc_search_source': async (args: any) => {
      const params = s.SearchSourceSchema.parse(args)
      const query = params.query.toLowerCase()
      const searchType = params.type || 'all'

      if (!fs.existsSync(deps.MAPPINGS_TINY)) {
        return { content: [{ type: 'text', text: JSON.stringify({ error: 'Mappings file not found at ' + deps.MAPPINGS_TINY }, null, 2) }], isError: true }
      }

      try {
        const content = fs.readFileSync(deps.MAPPINGS_TINY, 'utf8')
        const lines = content.split('\n')
        const results: string[] = []
        const limit = 50

        for (const line of lines) {
          if (results.length >= limit) break

          const lowerLine = line.toLowerCase()
          if (!lowerLine.includes(query)) continue

          if (searchType === 'all' || searchType === 'class') {
            if (line.startsWith('c\t') && lowerLine.includes(query)) {
              results.push(line.substring(0, 300))
              continue
            }
          }
          if (searchType === 'all' || searchType === 'method') {
            if ((line.startsWith('\tm\t') || line.startsWith('\tf\t')) && lowerLine.includes(query)) {
              results.push(line.substring(0, 300))
            }
          }
        }

        return {
          content: [{ type: 'text', text: JSON.stringify({
            query,
            type: searchType,
            total: results.length,
            results,
          }, null, 2) }],
        }
      } catch (e) {
        const msg = e instanceof Error ? e.message : String(e)
        return { content: [{ type: 'text', text: JSON.stringify({ error: msg }, null, 2) }], isError: true }
      }
    },

    'mc_build_template': async (args: any) => {
      await requireBridge()
      const params = s.BuildTemplateSchema.parse(args)

      const template = params.template
      const x = params.x, y = params.y, z = params.z
      const w = params.width ?? 5, h = params.height ?? 4, d = params.depth ?? 5
      const mat = params.material ?? 'minecraft:stone_bricks'
      const floorMat = params.floor_material ?? mat
      const roofMat = params.roof_material ?? mat
      const includeDoor = params.door ?? (template === 'house')
      const includeWindows = params.windows ?? (template === 'house')
      const execute = params.execute ?? true

      const commands: string[] = []
      const label = `${template} at (${x},${y},${z}) ${w}×${h}×${d}`

      commands.push(`/fill ${x} ${y} ${z} ${x + w - 1} ${y} ${z + d - 1} ${floorMat}`)

      switch (template) {
        case 'house':
        case 'room': {
          commands.push(`/fill ${x} ${y + 1} ${z} ${x + w - 1} ${y + h - 1} ${z} ${mat}`)
          commands.push(`/fill ${x} ${y + 1} ${z + d - 1} ${x + w - 1} ${y + h - 1} ${z + d - 1} ${mat}`)
          commands.push(`/fill ${x} ${y + 1} ${z} ${x} ${y + h - 1} ${z + d - 1} ${mat}`)
          commands.push(`/fill ${x + w - 1} ${y + 1} ${z} ${x + w - 1} ${y + h - 1} ${z + d - 1} ${mat}`)
          commands.push(`/fill ${x} ${y + h} ${z} ${x + w - 1} ${y + h} ${z + d - 1} ${roofMat}`)
          if (includeDoor) {
            const dx = Math.floor(w / 2)
            commands.push(`/setblock ${x + dx} ${y + 1} ${z} minecraft:oak_door[facing=south,half=lower]`)
            commands.push(`/setblock ${x + dx} ${y + 2} ${z} minecraft:oak_door[facing=south,half=upper]`)
          }
          if (includeWindows) {
            const dx = Math.floor(w / 2)
            if (dx > 1) {
              commands.push(`/setblock ${x + 1} ${y + 2} ${z} minecraft:glass_pane`)
              commands.push(`/setblock ${x + w - 2} ${y + 2} ${z} minecraft:glass_pane`)
            }
          }
          break
        }
        case 'wall': {
          commands.push(`/fill ${x} ${y + 1} ${z} ${x + w - 1} ${y + h - 1} ${z} ${mat}`)
          for (let bx = 0; bx < w; bx += 2) {
            commands.push(`/setblock ${x + bx} ${y + h} ${z} ${mat}`)
          }
          break
        }
        case 'tower': {
          commands.push(`/fill ${x} ${y + 1} ${z} ${x + w - 1} ${y + h - 1} ${z + d - 1} ${mat} replace air`)
          commands.push(`/fill ${x + 1} ${y + 1} ${z + 1} ${x + w - 2} ${y + h - 2} ${z + d - 2} minecraft:air replace ${mat}`)
          commands.push(`/fill ${x} ${y + h - 1} ${z} ${x + w - 1} ${y + h - 1} ${z + d - 1} ${mat}`)
          for (let bx = 0; bx < w; bx += 2) {
            commands.push(`/setblock ${x + bx} ${y + h} ${z} ${mat}`)
            commands.push(`/setblock ${x + bx} ${y + h} ${z + d - 1} ${mat}`)
          }
          for (let bz = 0; bz < d; bz += 2) {
            commands.push(`/setblock ${x} ${y + h} ${z + bz} ${mat}`)
            commands.push(`/setblock ${x + w - 1} ${y + h} ${z + bz} ${mat}`)
          }
          break
        }
        case 'bridge': {
          commands.push(`/fill ${x} ${y} ${z} ${x + w - 1} ${y} ${z + d - 1} ${mat}`)
          commands.push(`/fill ${x} ${y + 1} ${z} ${x + w - 1} ${y + 1} ${z} ${mat}`)
          commands.push(`/fill ${x} ${y + 1} ${z + d - 1} ${x + w - 1} ${y + 1} ${z + d - 1} ${mat}`)
          for (let sx = 0; sx < w; sx += Math.max(1, w - 1)) {
            commands.push(`/fill ${x + sx} ${y - 1} ${z} ${x + sx} ${y - 1} ${z + d - 1} ${mat}`)
          }
          break
        }
        case 'staircase': {
          for (let i = 0; i < h && i < Math.max(w, d); i++) {
            commands.push(`/fill ${x + i} ${y + i} ${z + i} ${x + w - 1} ${y + i} ${z + d - 1} ${mat}`)
          }
          break
        }
        case 'platform': {
          commands.push(`/fill ${x} ${y + 1} ${z} ${x + w - 1} ${y + 1} ${z + d - 1} ${mat}`)
          break
        }
        case 'pillar': {
          commands.push(`/fill ${x} ${y} ${z} ${x} ${y + h} ${z} ${mat}`)
          break
        }
        case 'arch': {
          commands.push(`/fill ${x} ${y} ${z} ${x} ${y + h} ${z} ${mat}`)
          commands.push(`/fill ${x + w - 1} ${y} ${z} ${x + w - 1} ${y + h} ${z} ${mat}`)
          commands.push(`/fill ${x} ${y + h} ${z} ${x + w - 1} ${y + h} ${z} ${mat}`)
          break
        }
        case 'pyramid': {
          for (let i = 0; i < h; i++) {
            const layerSize = Math.max(1, Math.min(w, d) - i * 2)
            const cx = x + i, cz = z + i
            commands.push(`/fill ${cx} ${y + i} ${cz} ${cx + layerSize - 1} ${y + i} ${cz + layerSize - 1} ${mat}`)
          }
          break
        }
      }

      const results: Array<{ cmd: string; ok: boolean; error?: string }> = []
      if (execute) {
        pushBuild(label, commands)
        for (const cmd of commands) {
          try {
            const resp = await bridge.send('exec', { cmd: cmd.replace('/', '') })
            results.push({ cmd: cmd.substring(0, 60), ok: true })
          } catch (e) {
            results.push({ cmd: cmd.substring(0, 60), ok: false, error: e instanceof Error ? e.message : String(e) })
          }
        }
      }

      return {
        content: [{ type: 'text', text: JSON.stringify({
          template,
          label,
          commandCount: commands.length,
          executed: execute ? results.length : 0,
          preview: !execute ? commands : undefined,
          results: execute ? results : undefined,
        }, null, 2) }],
      }
    },

    'mc_mirror_build': async (args: any) => {
      await requireBridge()
      const params = s.MirrorBuildSchema.parse(args)
      const payload: Record<string, unknown> = {
        axis: params.axis,
        source_min: params.source_min,
        source_max: params.source_max,
        y1: params.y1,
        y2: params.y2,
        z1: params.z1,
        z2: params.z2,
      }
      if (params.center !== undefined) payload.center = params.center
      if (params.center1 !== undefined) payload.center1 = params.center1
      if (params.center2 !== undefined) payload.center2 = params.center2
      if (params.x1 !== undefined) payload.x1 = params.x1
      if (params.x2 !== undefined) payload.x2 = params.x2
      const result = await bridge.send('mirror', payload)
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_move_item': async (args: any) => {
      await requireBridge()
      const { from, to } = s.MoveItemSchema.parse(args)
      const result = await bridge.send('move_item', { from, to })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_drop_item': async (args: any) => {
      await requireBridge()
      const { slot: dropSlot } = s.DropItemSchema.parse(args)
      const result = await bridge.send('drop_item', { slot: dropSlot })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_equip_item': async (args: any) => {
      await requireBridge()
      const { slot: equipSlot, equipment_slot } = s.EquipItemSchema.parse(args)
      const payload: Record<string, unknown> = { slot: equipSlot }
      if (equipment_slot) payload.equipment_slot = equipment_slot
      const result = await bridge.send('equip_item', payload)
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_bossbar': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('bossbar')
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_advancements': async (args: any) => {
      await requireBridge()
      const { only_done } = s.AdvancementSchema.parse(args ?? {})
      const result = await bridge.send('advancements', { only_done: only_done ?? false })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_weather': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('weather')
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_gamemode': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('gamemode')
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_xp': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('xp')
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_time': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('time')
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_hotbar_select': async (args: any) => {
      await requireBridge()
      const { slot } = args as { slot: number }
      const result = await bridge.send('hotbar_select', { slot })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_recipes': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('recipes')
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_recipe_for_item': async (args: any) => {
      await requireBridge()
      const { item } = args as { item: string }
      const result = await bridge.send('recipe_for_item', { item })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_light_level': async (args: any) => {
      await requireBridge()
      const llArgs = args as Record<string, unknown> ?? {}
      const payload: Record<string, unknown> = {}
      if (llArgs.x !== undefined) payload.x = llArgs.x
      if (llArgs.y !== undefined) payload.y = llArgs.y
      if (llArgs.z !== undefined) payload.z = llArgs.z
      const result = await bridge.send('light_level', payload)
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_scoreboard': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('scoreboard')
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_seed': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('seed')
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_summon_entity': async (args: any) => {
      await requireBridge()
      const { entity, x: sx, y: sy, z: sz, nbt } = s.SummonEntitySchema.parse(args)
      const payload: Record<string, unknown> = { entity, x: sx, y: sy, z: sz }
      if (nbt) payload.nbt = nbt
      const result = await bridge.send('summon', payload)
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_break_block': async (args: any) => {
      await requireBridge()
      const { x: bx, y: by, z: bz } = s.BreakBlockSchema.parse(args)
      const result = await bridge.send('break_block', { x: bx, y: by, z: bz })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_place_block': async (args: any) => {
      await requireBridge()
      const { x: px, y: py, z: pz, block } = s.PlaceBlockSchema.parse(args)
      const result = await bridge.send('place_block', { x: px, y: py, z: pz, block })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_biome': async (args: any) => {
      await requireBridge()
      const params = s.BiomeSchema.parse(args ?? {})
      const payload: Record<string, unknown> = {}
      if (params.x !== undefined) payload.x = params.x
      if (params.y !== undefined) payload.y = params.y
      if (params.z !== undefined) payload.z = params.z
      const result = await bridge.send('biome', payload)
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_locate_structure': async (args: any) => {
      await requireBridge()
      const { structure } = args as { structure: string }
      const result = await bridge.send('locate_structure', { structure })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_locate_biome': async (args: any) => {
      await requireBridge()
      const { biome } = args as { biome: string }
      const result = await bridge.send('locate_biome', { biome })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_find_blocks': async (args: any) => {
      await requireBridge()
      const { block, blocks, radius, x1, y1, z1: z1val, x2, y2, z2: z2val } = args as z.infer<typeof s.FindBlocksSchema>
      const payload: Record<string, unknown> = {}
      if (block) payload.block = block
      if (blocks && blocks.length > 0) payload.blocks = blocks
      if (radius !== undefined) payload.radius = radius
      if (x1 !== undefined) payload.x1 = x1; if (x2 !== undefined) payload.x2 = x2
      if (y1 !== undefined) payload.y1 = y1; if (y2 !== undefined) payload.y2 = y2
      if (z1val !== undefined) payload.z1 = z1val; if (z2val !== undefined) payload.z2 = z2val
      const result = await bridge.send('find_blocks', payload)
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_find_slime_chunks': async (args: any) => {
      await requireBridge()
      const { cx, cz, radius, seed } = s.SlimeChunkSchema.parse(args)
      if (seed !== undefined) {
        const payload: Record<string, unknown> = { seed, cx, cz }
        if (radius !== undefined) payload.radius = radius
        const result = await bridge.send('is_slime_chunk', payload)
        return { content: [{ type: 'text', text: result }] }
      } else {
        await bridge.send('exec', { cmd: 'seed' })
        return { content: [{ type: 'text', text: JSON.stringify({ ok: true, info: 'Seed requested. Please call mc_find_slime_chunks again with the seed from chat output.' }) }] }
      }
    },

    'mc_find_spawners': async (args: any) => {
      await requireBridge()
      const radius = (args as Record<string, unknown>).radius as number | undefined
      const payload: Record<string, unknown> = {}
      if (radius !== undefined) payload.radius = radius
      const result = await bridge.send('find_spawners', payload)
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_find_item': async (args: any) => {
      await requireBridge()
      const params = s.FindItemSchema.parse(args)
      const result = await bridge.send('find_item', { name: params.name, container: params.container ?? false })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_relationship_graph': async (args: any) => {
      const { names, format } = s.RelationshipGraphSchema.parse(args)
      await requireBridge()

      let relationships = 'No relationship data available'
      try {
        const payload: Record<string, unknown> = {}
        if (names) payload.names = names
        const raw = await bridge.send('analyze_relationships', payload)
        relationships = raw
      } catch { /* ignore */ }

      if (format === 'json') {
        return { content: [{ type: 'text', text: relationships }] }
      }

      let playerList = 'unknown'
      try {
        const raw = await bridge.send('info')
        const info = JSON.parse(raw)
        if (info.playerList) playerList = JSON.stringify(info.playerList.map((p: any) => p.name || p))
      } catch { /* ignore */ }

      const graph = await callDeepSeekAnalysis(
        `You are a social network analyst for a Minecraft server. Given player relationship data, generate a mermaid.js graph (flowchart TD).

Rules:
- Use flowchart TD format
- Each player is a node: P1["Alex"]
- Relationships are edges: P1-- "trusts" -->P2
- Use different line styles:
  ---> for positive (trust, friendly, trades)
  -.-x for negative (distrust, rivalry, conflict)
  --- for neutral (knows each other)
- Group related players with subgraphs if there are clear factions
- Keep labels concise (1-3 words)
- If no relationship data exists, generate a simple graph based on the player list

OUTPUT ONLY the mermaid code block:
\`\`\`mermaid
flowchart TD
  P1["Alex"] --> P2["Bob"]
\`\`\``,
        `PLAYERS: ${playerList}\nRELATIONSHIPS:\n${relationships}`,
        0.3, 4096
      )

      return { content: [{ type: 'text', text: graph }] }
    },

    'mc_enchant_simulate': async (args: any) => {
      const { target, max_xp, strategy } = s.EnchantSimSchema.parse(args)
      await requireBridge()

      let inventory = 'unknown'
      try {
        const invRaw = await bridge.send('inv')
        inventory = invRaw.substring(0, 1500)
      } catch { /* ignore */ }

      let xpInfo = 'unknown'
      try {
        const xpRaw = await bridge.send('xp')
        xpInfo = xpRaw.substring(0, 500)
      } catch { /* ignore */ }

      let recipes = 'unknown'
      try {
        const recipesRaw = await bridge.send('recipes')
        recipes = recipesRaw.substring(0, 1500)
      } catch { /* ignore */ }

      const plan = await callDeepSeekAnalysis(
        `You are a Minecraft enchanting expert. Given the player's inventory, XP, available recipes, and their goal, suggest the optimal enchanting strategy.

Available operations:
- Enchant an item at the enchantment table (costs lapis + XP levels)
- Combine enchanted books at anvil (costs XP levels)
- Apply enchanted book to item at anvil (costs XP levels)
- Disenchant by grinding

Rules:
- Max enchantment table cost is 30 levels
- Anvil cost increases with prior work penalty
- Some enchantments are incompatible (e.g., Sharpness and Smite)
- Prioritize efficiency: best outcome for available resources

Output a JSON object with:
{
  "recommendation": "brief summary of the best strategy",
  "steps": [
    { "order": 1, "action": "enchant diamond sword at table", "xp_cost": 30, "expected": "Sharpness III + something" },
    { "order": 2, "action": "combine book with item at anvil", "xp_cost": 5, "expected": "Sharpness IV" }
  ],
  "total_xp_estimate": 35,
  "alternative_strategies": ["..."]
}
OUTPUT ONLY THE JSON OBJECT.`,
        `GOAL: ${target || 'best overall gear improvement'}
STRATEGY: ${strategy || 'balanced'}
${max_xp ? `MAX XP: ${max_xp}` : ''}
INVENTORY: ${inventory}
XP: ${xpInfo}
RECIPES: ${recipes}`,
        0.3, 4096
      )

      let result
      try {
        result = JSON.parse(plan)
      } catch {
        result = { recommendation: 'AI analysis failed', raw: plan }
      }

      return { content: [{ type: 'text', text: JSON.stringify(result, null, 2) }] }
    },

    'mc_get_player_effects': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('player_effects', {})
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_statistics': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('statistics', {})
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_sign': async (args: any) => {
      await requireBridge()
      const params = s.SignSchema.parse(args)
      const payload: Record<string, unknown> = {}
      if (params.x !== undefined) { payload.x = params.x; payload.y = params.y ?? 0; payload.z = params.z ?? 0 }
      if (params.look) payload.look = true
      const result = await bridge.send('sign', payload)
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_world_border': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('world_border', {})
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_player_abilities': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('player_abilities', {})
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_last_death': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('last_death', {})
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_press_key': async (args: any) => {
      await requireBridge()
      const params = s.PressKeySchema.parse(args)
      const payload: Record<string, unknown> = { key: params.key }
      if (params.state !== undefined) payload.state = params.state
      if (params.duration !== undefined) payload.duration = params.duration
      const result = await bridge.send('press_key', payload)
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_use_item': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('use_item', {})
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_walk_to': async (args: any) => {
      await requireBridge()
      const params = s.WalkToSchema.parse(args)
      const payload: Record<string, unknown> = { x: params.x, y: params.y, z: params.z }
      if (params.threshold !== undefined) payload.threshold = params.threshold
      if (params.sprint !== undefined) payload.sprint = params.sprint
      const result = await bridge.send('walk_to', payload)
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_chunk': async (args: any) => {
      await requireBridge()
      const params = s.ChunkSchema.parse(args)
      const result = await bridge.send('chunk', { x: params.x, z: params.z })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_gui_click': async (args: any) => {
      await requireBridge()
      const params = s.GuiClickSchema.parse(args)
      const payload: Record<string, unknown> = { slot: params.slot, button: params.button ?? 0 }
      if (params.action) payload.action = params.action
      const result = await bridge.send('gui_click', payload)
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_trade': async (args: any) => {
      await requireBridge()
      const params = s.TradeSchema.parse(args)
      const result = await bridge.send('trade', { index: params.index })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_set_deepseek_key': async (args: any) => {
      const { key } = s.SetDeepSeekKeySchema.parse(args)
      deps.setDeepseekKey(key)
      deps.saveKey(key)
      return { content: [{ type: 'text', text: 'DeepSeek API key set and saved' }] }
    },

    'mc_build_llm': async (args: any) => {
      const params = s.BuildLlmSchema.parse(args)
      const execute = params.execute ?? false

      if (!deps.getDeepseekKey()) {
        return { content: [{ type: 'text', text: JSON.stringify({ error: 'DeepSeek API key not set. Use mc_configure_translate or mc_set_deepseek_key first.' }, null, 2) }], isError: true }
      }

      const systemPrompt = `You are a Minecraft building planner. Given a natural language description and start coordinates, output ONLY a JSON array of Minecraft /fill and /setblock commands to build the structure. Use appropriate block types. Keep it reasonable (max 50 commands). Output ONLY valid JSON, no explanation.

Example response format:
["/fill 100 64 100 104 64 104 minecraft:stone_bricks","/setblock 102 65 100 minecraft:oak_door[facing=south,half=lower]"]

Use coordinates relative to the start position. Place floor at y. Make walls h-1 blocks tall. The player will be at the given coordinates.`

      const userPrompt = `Description: ${params.description}\nStart position: x=${params.x}, y=${params.y}, z=${params.z}\n\nGenerate the building commands.`

      let planCommands: string[] = []
      try {
        const apiKey = deps.getDeepseekKey()
        const resp = await fetch('https://api.deepseek.com/v1/chat/completions', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${apiKey}`,
          },
          body: JSON.stringify({
            model: 'deepseek-chat',
            messages: [
              { role: 'system', content: systemPrompt },
              { role: 'user', content: userPrompt },
            ],
            max_tokens: 4096,
            temperature: 0.7,
          }),
        })

        if (!resp.ok) {
          return { content: [{ type: 'text', text: JSON.stringify({ error: 'DeepSeek API error: ' + resp.status }, null, 2) }], isError: true }
        }

        const data: s.DeepSeekResponse = await resp.json() as s.DeepSeekResponse
        let text = data.choices?.[0]?.message?.content?.trim() ?? '[]'
        const finishReason = data.choices?.[0]?.finish_reason
        if (finishReason === 'length') {
          const lastQuote = text.lastIndexOf('"')
          if (lastQuote >= 0) {
            text = text.substring(0, lastQuote + 1) + ']'
          }
        }
        const jsonMatch = text.match(/\[[\s\S]*\]/)
        if (jsonMatch) {
          try {
            planCommands = JSON.parse(jsonMatch[0])
          } catch (parseErr) {
            const msg = parseErr instanceof Error ? parseErr.message : String(parseErr)
            return { content: [{ type: 'text', text: JSON.stringify({ error: 'JSON parse error: ' + msg, raw: jsonMatch[0].substring(0, 500) }, null, 2) }], isError: true }
          }
        } else {
          return { content: [{ type: 'text', text: JSON.stringify({ error: 'Could not find JSON array in LLM response', raw: text.substring(0, 1000) }, null, 2) }], isError: true }
        }
      } catch (e) {
        const msg = e instanceof Error ? e.message : String(e)
        return { content: [{ type: 'text', text: JSON.stringify({ error: 'Failed to generate plan: ' + msg }, null, 2) }], isError: true }
      }

      if (!Array.isArray(planCommands)) {
        return { content: [{ type: 'text', text: JSON.stringify({ error: 'LLM did not return valid command array' }) }], isError: true }
      }

      const results: Array<{ cmd: string; ok: boolean; error?: string }> = []
      if (execute) {
        pushBuild(`LLM: ${params.description.substring(0, 40)}`, planCommands)
        for (const cmd of planCommands) {
          try {
            const cmdClean = cmd.replace('/', '')
            await bridge.send('exec', { cmd: cmdClean })
            results.push({ cmd: cmd.substring(0, 60), ok: true })
          } catch (e) {
            results.push({ cmd: cmd.substring(0, 60), ok: false, error: e instanceof Error ? e.message : String(e) })
          }
        }
      }

      return {
        content: [{ type: 'text', text: JSON.stringify({
          description: params.description,
          planCommandCount: planCommands.length,
          plan: planCommands,
          executed: execute ? results.length : 0,
          results: execute ? results : undefined,
        }, null, 2) }],
      }
    },

    'mc_run_script': async (args: any) => {
      await requireBridge()
      const params = s.RunScriptSchema.parse(args)
      const result = await bridge.send('run_script', { steps: params.steps })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_auto_fish': async (args: any) => {
      await requireBridge()
      const { action } = s.AutoFishSchema.parse(args ?? {})
      const result = await bridge.send('auto_fish', { action: action ?? 'status' })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_screenshot_repeat': async (args: any) => {
      await requireBridge()
      const params = s.ScreenshotRepeatSchema.parse(args ?? {})
      const payload: Record<string, unknown> = { action: params.action ?? 'status' }
      if (params.interval !== undefined) payload.interval = params.interval
      if (params.count !== undefined) payload.count = params.count
      const result = await bridge.send('screenshot_repeat', payload)
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_build_geometry': async (args: any) => {
      await requireBridge()
      const params = s.BuildGeometrySchema.parse(args)
      const shape = params.shape, x = params.x, y = params.y, z = params.z
      const radius = params.radius, height = params.height ?? radius
      const mat = params.material ?? 'minecraft:stone_bricks'
      const hollow = params.hollow ?? false
      const execute = params.execute ?? true

      const commands: string[] = []
      const label = `${shape} at (${x},${y},${z}) r=${radius} h=${height}`

      if (shape === 'cylinder') {
        for (let dy = 0; dy < height; dy++) {
          for (let dx = -radius; dx <= radius; dx++) {
            for (let dz = -radius; dz <= radius; dz++) {
              const dist = Math.sqrt(dx * dx + dz * dz)
              if (dist > radius + 0.5) continue
              if (hollow && dist < radius - 0.5) continue
              commands.push(`/setblock ${x + dx} ${y + dy} ${z + dz} ${mat}`)
            }
          }
        }
      } else if (shape === 'sphere') {
        for (let dx = -radius; dx <= radius; dx++) {
          for (let dy = -radius; dy <= radius; dy++) {
            for (let dz = -radius; dz <= radius; dz++) {
              const dist = Math.sqrt(dx * dx + dy * dy + dz * dz)
              if (dist > radius + 0.5) continue
              if (hollow && dist < radius - 0.5) continue
              commands.push(`/setblock ${x + dx} ${y + dy} ${z + dz} ${mat}`)
            }
          }
        }
      } else if (shape === 'dome') {
        for (let dx = -radius; dx <= radius; dx++) {
          for (let dy = 0; dy <= height; dy++) {
            for (let dz = -radius; dz <= radius; dz++) {
              const dist = Math.sqrt(dx * dx + dy * dy + dz * dz)
              if (dist > radius + 0.5) continue
              if (hollow && dist < radius - 0.5) continue
              commands.push(`/setblock ${x + dx} ${y + dy} ${z + dz} ${mat}`)
            }
          }
        }
      }

      pushBuild(label, commands)

      const results: Array<{ cmd: string; ok: boolean; error?: string }> = []
      if (execute) {
        for (const cmd of commands) {
          try {
            await bridge.send('exec', { cmd: cmd.replace('/', '') })
            results.push({ cmd: cmd.substring(0, 60), ok: true })
          } catch (e) {
            results.push({ cmd: cmd.substring(0, 60), ok: false, error: e instanceof Error ? e.message : String(e) })
          }
        }
      }

      return {
        content: [{ type: 'text', text: JSON.stringify({
          shape, label,
          commandCount: commands.length,
          executed: execute ? results.length : 0,
          preview: !execute ? commands.slice(0, 100) : undefined,
          results: execute ? results : undefined,
        }, null, 2) }],
      }
    },

    'mc_undo_build': async (args: any) => {
      const last = buildHistory.pop()
      if (!last) throw new Error('No build history to undo')
      const reverseCmds: string[] = []
      for (const cmd of last.commands) {
        if (cmd.startsWith('/setblock ')) {
          const parts = cmd.split(' ')
          if (parts.length >= 5) {
            reverseCmds.push(`/setblock ${parts[1]} ${parts[2]} ${parts[3]} minecraft:air`)
          }
        } else if (cmd.startsWith('/fill ')) {
          const parts = cmd.split(' ')
          if (parts.length >= 6) {
            reverseCmds.push(`/fill ${parts[1]} ${parts[2]} ${parts[3]} ${parts[4]} ${parts[5]} ${parts[6]} minecraft:air replace ${parts[7] ?? parts[6]}`)
          }
        }
      }
      const results: Array<{ cmd: string; ok: boolean; error?: string }> = []
      for (const cmd of reverseCmds) {
        try {
          await requireBridge()
          await bridge.send('exec', { cmd: cmd.replace('/', '') })
          results.push({ cmd: cmd.substring(0, 60), ok: true })
        } catch (e) {
          results.push({ cmd: cmd.substring(0, 60), ok: false, error: e instanceof Error ? e.message : String(e) })
        }
      }
      return {
        content: [{ type: 'text', text: JSON.stringify({
          undone: last.label,
          reverseCommandCount: reverseCmds.length,
          results,
        }, null, 2) }],
      }
    },

    'mc_set_reconnect': async (args: any) => {
      const { interval, max_attempts } = s.SetReconnectSchema.parse(args ?? {})
      const current = bridge.getReconnectConfig()
      bridge.setReconnectConfig(
        interval ?? current.delay,
        max_attempts ?? current.maxAttempts
      )
      const updated = bridge.getReconnectConfig()
      return {
        content: [{ type: 'text', text: JSON.stringify({
          interval: updated.delay,
          max_attempts: updated.maxAttempts,
        }, null, 2) }],
      }
    },

    'mc_find_villager': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('find_villager')
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_sort_inventory': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('sort_inventory')
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_refill': async (args: any) => {
      await requireBridge()
      const { threshold, container } = s.RefillSchema.parse(args ?? {})
      const result = await bridge.send('refill', { threshold, container })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_craft_item': async (args: any) => {
      await requireBridge()
      const { item, count } = s.CraftItemSchema.parse(args ?? {})
      const result = await bridge.send('craft_item', { item, count })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_scan_terrain': async (args: any) => {
      await requireBridge()
      const params = s.ScanTerrainSchema.parse(args ?? {})
      const { radius, yRange, ai_suggest } = params
      const result = await bridge.send('scan_terrain', { radius, yRange })
      const parsed = JSON.parse(result)
      if (ai_suggest) {
        try {
          const suggestion = await callDeepSeekAnalysis(
            'You are a Minecraft building advisor. Given the terrain composition data, suggest the best structures to build, what materials are available locally, and any building tips. Be concise (2-3 sentences).',
            `Terrain scan data:\n${JSON.stringify(parsed, null, 2)}`
          )
          parsed.aiSuggestion = suggestion
        } catch {
          parsed.aiSuggestion = 'AI suggestion unavailable'
        }
      }
      return { content: [{ type: 'text', text: JSON.stringify(parsed, null, 2) }] }
    },

    'mc_explain_screen': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('explain_screen')
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_analyze_inventory': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('analyze_inventory')
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_batch_build': async (args: any) => {
      await requireBridge()
      const { commands } = s.BatchBuildSchema.parse(args ?? {})
      const result = await bridge.send('batch_build', { commands })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_entity_highlight': async (args: any) => {
      await requireBridge()
      const params = s.EntityHighlightSchema.parse(args ?? {})
      const { type: eType, radius, max, auto_highlight } = params
      const result = await bridge.send('entity_highlight', { type: eType, radius, max })
      const parsed = JSON.parse(result)
      if (auto_highlight && parsed.entities) {
        for (const ent of parsed.entities) {
          try {
            await bridge.send('highlight', { action: 'add', x: ent.x, y: ent.y, z: ent.z, r: 1, g: 0, b: 0, duration: 60000 })
          } catch { /* skip highlight errors */ }
        }
        parsed.autoHighlighted = parsed.entities.length
      }
      return { content: [{ type: 'text', text: JSON.stringify(parsed, null, 2) }] }
    },

    'mc_damage_display': async (args: any) => {
      await requireBridge()
      const { id } = s.DamageDisplaySchema.parse(args ?? {})
      const result = await bridge.send('damage_display', { id })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_tps': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('tps')
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_reach': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('reach')
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_ping_info': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('ping_info')
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_packet_logger': async (args: any) => {
      await requireBridge()
      const { action } = s.PacketLoggerSchema.parse(args ?? {})
      const result = await bridge.send('packet_logger', { action })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_packet_logger_detail': async (args: any) => {
      await requireBridge()
      const { action, filter } = s.PacketLoggerDetailSchema.parse(args ?? {})
      const result = await bridge.send('packet_logger_detail', { action, filter })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_packet_logger_find': async (args: any) => {
      await requireBridge()
      const { query, direction, limit } = s.PacketLoggerFindSchema.parse(args ?? {})
      const result = await bridge.send('packet_logger_find', { query, direction, limit })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_bedrock_breaker': async (args: any) => {
      await requireBridge()
      const { x, y, z, attempts } = s.BedrockBreakerSchema.parse(args ?? {})
      const result = await bridge.send('bedrock_breaker', { x, y, z, attempts })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_scan_containers': async (args: any) => {
      await requireBridge()
      const { radius } = s.ScanContainersSchema.parse(args ?? {})
      const result = await bridge.send('scan_containers', { radius })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_shulker_peek': async (args: any) => {
      await requireBridge()
      const { slot } = s.ShulkerPeekSchema.parse(args ?? {})
      const result = await bridge.send('shulker_peek', { slot })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_waypoints': async (args: any) => {
      await requireBridge()
      const { action, name, x, y, z } = s.WaypointsSchema.parse(args ?? {})
      const result = await bridge.send('waypoints', { action, name, x, y, z })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_travel_log': async (args: any) => {
      await requireBridge()
      const { action, limit } = s.TravelLogSchema.parse(args ?? {})
      const result = await bridge.send('travel_log', { action, limit })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_scan_crops': async (args: any) => {
      await requireBridge()
      const { radius } = s.ScanCropsSchema.parse(args ?? {})
      const result = await bridge.send('scan_crops', { radius })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_block_counter': async (args: any) => {
      await requireBridge()
      const { cx, cz } = s.BlockCounterSchema.parse(args ?? {})
      const result = await bridge.send('block_counter', { cx, cz })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_get_player_profile': async (args: any) => {
      await requireBridge()
      const { name } = s.GetPlayerProfileSchema.parse(args ?? {})
      const result = await bridge.send('get_player_profile', { name })
      const profileData = JSON.parse(result)

      const targetName = name || profileData.profile?.name || ''
      let analysis = null
      if (targetName) {
        const cachePath = path.join(deps.ANALYSIS_CACHE_DIR, `${targetName}.json`)
        try {
          if (fs.existsSync(cachePath)) {
            analysis = JSON.parse(fs.readFileSync(cachePath, 'utf8'))
          }
        } catch { /* ignore */ }
      }

      profileData.analysis = analysis
      return { content: [{ type: 'text', text: JSON.stringify(profileData, null, 2) }] }
    },

    'mc_analyze_player_profile': async (args: any) => {
      await requireBridge()
      const { name: ppName, focus: ppFocus } = s.AnalyzePlayerProfileSchema.parse(args ?? {})
      const rawResult = await bridge.send('get_player_profile', { name: ppName })
      const profile = JSON.parse(rawResult)
      const analysis = await callDeepSeekAnalysis(
        `You are a social psychology analyst in Minecraft. Analyze this player's profile data and provide insights on their ${ppFocus || 'personality, behavior patterns, communication style, trust level, and potential social influence vectors'}. Be specific and evidence-based. Format as a concise analysis report.`,
        `Player profile data:\n${JSON.stringify(profile, null, 2)}`
      )
      return { content: [{ type: 'text', text: JSON.stringify({ profile, analysis }) }] }
    },

    'mc_learn_my_style': async (args: any) => {
      await requireBridge()
      const { messages: msgCount } = s.LearnMyStyleSchema.parse(args ?? {})
      const raw = await bridge.send('get_player_profile', { name: '' })
      const selfProfile = JSON.parse(raw)
      const recentMsgs = (selfProfile.chatHistory || []).slice(-(msgCount || 50))
      const analysis = await callDeepSeekAnalysis(
        `You are a communication style analyst. Analyze these chat messages (from the AI's player) and identify: 1) Common phrases and mannerisms 2) Typical sentence structure 3) Emotional tone patterns 4) Response tendencies 5) Unique speaking habits. Be specific and reference actual message examples.`,
        `Chat messages to analyze:\n${recentMsgs.map((m: any) => `[${m.from || 'self'}] ${m.text}`).join('\n')}`
      )
      return { content: [{ type: 'text', text: analysis }] }
    },

    'mc_generate_message': async (args: any) => {
      await requireBridge()
      const { goal, target, tone, context } = s.GenerateMessageSchema.parse(args ?? {})
      const raw = await bridge.send('get_player_profile', { name: target })
      const targetProfile = JSON.parse(raw)
      const selfRaw = await bridge.send('get_player_profile', { name: '' })
      const selfProfile = JSON.parse(selfRaw)
      const myRecentMsgs = (selfProfile.chatHistory || []).slice(-30).map((m: any) => m.text).join(' | ')
      const message = await callDeepSeekAnalysis(
        `You are a social engineering strategist in Minecraft. Generate a single chat message that achieves the stated goal, optimized for the target player's psychological profile. Consider their communication patterns, known interests, and interaction history. Match the AI's established speaking style. Output ONLY the message text, no explanations.`,
        `GOAL: ${goal}\nTARGET PROFILE: ${JSON.stringify(targetProfile, null, 2)}\nAI'S STYLE: ${myRecentMsgs}\n${context ? `CONTEXT: ${context}` : ''}\n${tone ? `TONE: ${tone}` : ''}`
      )
      return { content: [{ type: 'text', text: message }] }
    },

    'mc_analyze_relationships': async (args: any) => {
      await requireBridge()
      const { names } = s.AnalyzeRelationshipsSchema.parse(args ?? {})
      const nameList = names ? names.split(',').map((n: string) => n.trim()) : []
      const profiles: any[] = []
      if (nameList.length > 0) {
        for (const n of nameList) {
          try {
            const r = await bridge.send('get_player_profile', { name: n })
            profiles.push(JSON.parse(r))
          } catch { /* skip */ }
        }
      } else {
        const selfRaw = await bridge.send('get_player_profile', { name: '' })
        return { content: [{ type: 'text', text: 'No specific player names provided. Use mc_analyze_player_profile for individual analysis or specify names with the "names" parameter.' }] }
      }
      const analysis = await callDeepSeekAnalysis(
        `You are a social network analyst in Minecraft. Analyze the relationship dynamics between these players based on their profile data. Identify: alliances and rivalries, trust levels, communication patterns between them, influence hierarchies, potential conflicts or collaborations. Be evidence-based.`,
        `Player profiles:\n${profiles.map(p => JSON.stringify(p, null, 2)).join('\n\n---\n\n')}`
      )
      return { content: [{ type: 'text', text: analysis }] }
    },

    'mc_analyze_chat_stream': async (args: any) => {
      await requireBridge()
      const { duration: dur, focus: chatFocus } = s.AnalyzeChatStreamSchema.parse(args ?? {})
      const chatRaw = await bridge.send('chatlog', { count: 200 })
      const chatLog = JSON.parse(chatRaw)
      const messages = (chatLog.messages || chatLog || []).slice(-200)
      const analysis = await callDeepSeekAnalysis(
        `You are a chat intelligence analyst. Analyze this Minecraft chat stream and report on: ${chatFocus || 'overall sentiment, key topics being discussed, emerging social tensions, influence opportunities, and notable player interactions'}. Be concise and specific.`,
        `Chat messages:\n${messages.map((m: any) => `[${m.from || '?'}] ${m.text}`).join('\n')}`
      )
      return { content: [{ type: 'text', text: analysis }] }
    },

    'mc_analyze_chat_sentiment': async (args: any) => {
      await requireBridge()
      const { count = 20, player } = s.ChatSentimentSchema.parse(args)

      let chatLog = 'No chat data'
      try {
        const payload: Record<string, unknown> = { count }
        if (player) payload.player = player
        const raw = await bridge.send('chatlog', payload)
        chatLog = raw.substring(0, 3000)
      } catch { /* ignore */ }

      const analysis = await callDeepSeekAnalysis(
        `You are a chat sentiment analyst for a Minecraft server. Given recent chat messages, analyze:

1. Overall mood (positive/neutral/negative/tension)
2. Current topics of discussion
3. Social dynamics (alliances, conflicts, cooperation)
4. Notable players (who is leading, who is quiet, who is causing drama)
5. Risk rating (green/yellow/red)

Output JSON:
{
  "mood": "positive",
  "tension_level": "low",
  "topics": ["building", "exploring"],
  "key_players": [
    { "name": "Alex", "role": "leader", "sentiment": "positive", "message_count": 5 }
  ],
  "risk": "green",
  "summary": "Friendly conversation about building projects"
}`,
        `RECENT CHAT:\n${chatLog}`,
        0.3, 2048
      )

      let result
      try { result = JSON.parse(analysis) } catch { result = { raw: analysis } }

      return { content: [{ type: 'text', text: JSON.stringify(result, null, 2) }] }
    },

    'mc_simulate_outcome': async (args: any) => {
      await requireBridge()
      const { message: simMsg, target: simTarget, context: simContext } = s.SimulateOutcomeSchema.parse(args ?? {})
      const raw = await bridge.send('get_player_profile', { name: simTarget })
      const targetProfile = JSON.parse(raw)
      const simulation = await callDeepSeekAnalysis(
        `You are a social outcome simulator in Minecraft. Given the target player's psychological profile and the planned message, simulate the most likely outcomes. Consider: immediate reaction, emotional impact, trust implications, relationship changes, potential follow-up behaviors. Assess probability (high/medium/low) for each likely outcome. Be realistic and evidence-based.`,
        `TARGET PROFILE: ${JSON.stringify(targetProfile, null, 2)}\nPLANNED MESSAGE: ${simMsg}\n${simContext ? `CONTEXT: ${simContext}` : ''}`
      )
      return { content: [{ type: 'text', text: simulation }] }
    },

    'mc_analyze_current_server': async (args: any) => {
      await requireBridge()
      const infoRaw = await bridge.send('get_server_info')
      const info = JSON.parse(infoRaw)
      if (!info.needsAnalysis) {
        return { content: [{ type: 'text', text: `Server already known: ${info.brand}` }] }
      }
      const analysis = await callDeepSeekAnalysis(
        `You are a Minecraft server identifier. Given the server MOTD message, identify the server brand/network name and determine its type.
Respond with JSON only:
{
  "brand": "short_brand_name_hypixel_etc",
  "networkType": "major_network" or "small_server",
  "displayName": "Full Server Name"
}
major_network = large public server networks (max > 8 players typical)
small_server = private/LAN/small servers (max ≤ 8 players typical)`,
        `Server address: ${info.address}\nMOTD: ${info.motd || '(empty)'}`
      )
      let serverInfo
      try {
        serverInfo = JSON.parse(analysis)
      } catch {
        serverInfo = { brand: info.address.replace(/[^a-zA-Z0-9]/g, '_'), networkType: 'small_server', displayName: info.address }
      }
      await bridge.send('set_server_brand', {
        address: info.address,
        brand: serverInfo.brand,
        networkType: serverInfo.networkType,
        displayName: serverInfo.displayName,
      })
      return { content: [{ type: 'text', text: JSON.stringify(serverInfo, null, 2) }] }
    },

    'mc_list_known_servers': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('get_all_servers')
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_auto_explore': async (args: any) => {
      await requireBridge()
      const { action: aeAction, radius: aeRadius, mode: aeMode } = s.AutoExploreSchema.parse(args ?? {})
      const payload: Record<string, unknown> = { action: aeAction }
      if (aeRadius !== undefined) payload.radius = aeRadius
      if (aeMode !== undefined) payload.mode = aeMode
      const result = await bridge.send('auto_explore', payload)
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_save_memory': async (args: any) => {
      await requireBridge()
      const { content: memContent, category, importance, tags } = s.MemoryAddSchema.parse(args)
      const result = await bridge.send('memory_add', { content: memContent, category, importance, tags })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_recall_memory': async (args: any) => {
      await requireBridge()
      const params = s.MemoryRecallSchema.parse(args ?? {})
      const result = await bridge.send('memory_recall', {
        query: params.query || '',
        category: params.category || '',
        limit: params.limit || 20,
      })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_list_memories': async (args: any) => {
      await requireBridge()
      const { category: listCat, limit: listLimit } = z.object({
        category: z.string().optional(),
        limit: z.number().optional(),
      }).parse(args ?? {})
      const result = await bridge.send('memory_list', { category: listCat || '', limit: listLimit || 50 })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_memory_near': async (args: any) => {
      await requireBridge()
      const { x: nx, y: ny, z: nz, radius: nr, limit: nl } = s.MemoryNearSchema.parse(args ?? {})
      const result = await bridge.send('memory_near', { x: nx, y: ny, z: nz, radius: nr ?? 32, limit: nl ?? 20 })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_forget_memory': async (args: any) => {
      await requireBridge()
      const { id } = s.MemoryDeleteSchema.parse(args)
      const result = await bridge.send('memory_delete', { id })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_auto_trade': async (args: any) => {
      const { index: tradeIndex, count, villager_name } = s.AutoTradeSchema.parse(args)
      await requireBridge()
      const results: any[] = []
      let tradesDone = 0
      const maxTrades = count || 64

      try {
        let villagerPos: { x: number; y: number; z: number } | null = null
        try {
          const raw = await bridge.send('find_villager')
          const data = JSON.parse(raw)
          if (data.villagers && data.villagers.length > 0) {
            const target = data.villagers[0]
            villagerPos = { x: target.x, y: target.y, z: target.z }
            results.push({ step: 'find_villager', status: 'ok', pos: villagerPos })
          }
        } catch { /* ignore */ }

        if (!villagerPos) {
          return { content: [{ type: 'text', text: JSON.stringify({ ok: false, error: 'No villagers found nearby' }) }] }
        }

        await bridge.send('walk_to', villagerPos)
        await new Promise(r => setTimeout(r, 2000))
        results.push({ step: 'walk_to_villager', status: 'ok', pos: villagerPos })

        await bridge.send('look_at', villagerPos)
        await new Promise(r => setTimeout(r, 500))
        await bridge.send('use_item')
        await new Promise(r => setTimeout(r, 1000))
        results.push({ step: 'open_gui', status: 'ok' })

        const screenRaw = await bridge.send('screen')
        let screenData: any
        try { screenData = JSON.parse(screenRaw) } catch { screenData = {} }
        results.push({ step: 'get_trades', status: 'ok', tradesAvailable: screenData.trades?.length || 'unknown' })

        const idx = (tradeIndex !== undefined && tradeIndex >= 0) ? tradeIndex : 0
        const actualCount = Math.min(maxTrades, 64)
        for (let i = 0; i < actualCount; i++) {
          try {
            await bridge.send('trade', { index: idx })
            tradesDone++
            await new Promise(r => setTimeout(r, 1000))
          } catch (err) {
            break
          }
        }
        results.push({ step: 'execute_trades', status: 'ok', tradesDone })

      } catch (err) {
        results.push({ step: 'error', error: err instanceof Error ? err.message : String(err) })
      }

      return { content: [{ type: 'text', text: JSON.stringify({ tradesDone, steps: results }, null, 2) }] }
    },

    'mc_auto_farm': async (args: any) => {
      await requireBridge()
      const { radius = 16, action = 'full', crop } = s.AutoFarmSchema.parse(args)
      const results: any[] = []

      try {
        const raw = await bridge.send('scan_crops', { radius })
        const data = JSON.parse(raw)
        const crops = data.crops || []
        results.push({ step: 'scan', cropsFound: crops.length, rawStatus: data.status || 'ok' })

        if (action === 'scan') {
          return { content: [{ type: 'text', text: JSON.stringify(results, null, 2) }] }
        }

        const targetCrops = crop
          ? crops.filter((c: any) => c.type?.toLowerCase().includes(crop.toLowerCase()) || c.name?.toLowerCase().includes(crop.toLowerCase()))
          : crops

        let harvested = 0
        let replanted = 0

        for (const cropPos of targetCrops) {
          try {
            await bridge.send('break_block', { x: cropPos.x, y: cropPos.y, z: cropPos.z })
            harvested++
            await new Promise(r => setTimeout(r, 300))

            if (action === 'full') {
              const invRaw = await bridge.send('find_item', { name: cropPos.type?.replace('_crop', '_seeds') || 'seeds' })
              try {
                const inv = JSON.parse(invRaw)
                if (inv.slots && inv.slots.length > 0) {
                  await bridge.send('hotbar_select', { slot: 0 })
                  await bridge.send('place_block', { x: cropPos.x, y: cropPos.y, z: cropPos.z, block: cropPos.block_id || 'minecraft:wheat' })
                  replanted++
                }
              } catch { /* no seeds */ }
              await new Promise(r => setTimeout(r, 300))
            }
          } catch { /* skip this crop */ }
        }

        results.push({ step: action, harvested, replanted })
      } catch (err) {
        results.push({ step: 'error', error: err instanceof Error ? err.message : String(err) })
      }

      return { content: [{ type: 'text', text: JSON.stringify(results, null, 2) }] }
    },

    'mc_analyze_terrain_heightmap': async (args: any) => {
      await requireBridge()
      const { radius = 3 } = s.TerrainHeightmapSchema.parse(args ?? {})

      let playerX = 0, playerZ = 0
      try {
        const posRaw = await bridge.send('pos')
        const pos = JSON.parse(posRaw)
        playerX = pos.x || 0
        playerZ = pos.z || 0
      } catch { /* ignore */ }

      const cx = Math.floor(playerX / 16)
      const cz = Math.floor(playerZ / 16)

      const heights: number[] = []
      const chunkData: any[] = []

      for (let dx = -radius; dx <= radius; dx++) {
        for (let dz = -radius; dz <= radius; dz++) {
          try {
            const raw = await bridge.send('chunk', { x: cx + dx, z: cz + dz })
            const data = JSON.parse(raw)
            if (data.heightmap) {
              for (const key in data.heightmap) {
                if (typeof data.heightmap[key] === 'number') {
                  heights.push(data.heightmap[key])
                }
              }
            }
            chunkData.push({ cx: cx + dx, cz: cz + dz, samples: data.heightmap?.samples || [] })
          } catch { /* skip chunk */ }
        }
      }

      if (heights.length === 0) {
        return { content: [{ type: 'text', text: JSON.stringify({ error: 'No heightmap data available', chunksScanned: chunkData.length }) }] }
      }

      const min = Math.min(...heights)
      const max = Math.max(...heights)
      const avg = Math.round(heights.reduce((a, b) => a + b, 0) / heights.length * 10) / 10

      const slopes: number[] = []
      for (let i = 0; i < chunkData.length - 1; i++) {
        for (let j = i + 1; j < chunkData.length; j++) {
          const a = chunkData[i]
          const b = chunkData[j]
          if (a.samples.length > 0 && b.samples.length > 0) {
            const diff = Math.abs(a.samples[0] - b.samples[0])
            if (diff > 0) slopes.push(diff)
          }
        }
      }

      const maxSlope = slopes.length > 0 ? Math.max(...slopes) : 0
      const avgSlope = slopes.length > 0 ? Math.round(slopes.reduce((a, b) => a + b, 0) / slopes.length * 10) / 10 : 0

      return {
        content: [{
          type: 'text',
          text: JSON.stringify({
            chunksScanned: chunkData.length,
            elevation: { min, max, avg, range: max - min },
            slope: { max: maxSlope, avg: avgSlope },
            terrainType: max - min <= 3 ? 'flat' : max - min <= 10 ? 'gently_rolling' : max - min <= 20 ? 'hilly' : 'mountainous',
          }, null, 2)
        }]
      }
    },

    'mc_set_model': async (args: any) => {
      const { provider, api_key, model } = s.SetModelSchema.parse(args)

      const config = { provider, apiKey: api_key, model: model || DEFAULT_MODELS[provider] }
      const configPath = path.join(CONFIG_DIR, 'model-config.json')

      try {
        fs.mkdirSync(CONFIG_DIR, { recursive: true })
        fs.writeFileSync(configPath, JSON.stringify(config, null, 2))
        return { content: [{ type: 'text', text: JSON.stringify({ ok: true, provider, model: config.model }) }] }
      } catch (err) {
        return { content: [{ type: 'text', text: JSON.stringify({ ok: false, error: err instanceof Error ? err.message : String(err) }) }] }
      }
    },

    'mc_set_discord_webhook': async (args: any) => {
      const params = s.DiscordWebhookSchema.parse(args)
      const { action = 'set' } = params
      const configPath = path.join(CONFIG_DIR, 'discord-webhook.json')

      if (action === 'set') {
        if (!params.url) {
          return { content: [{ type: 'text', text: JSON.stringify({ error: 'url is required for action=set' }) }], isError: true }
        }
        const config = { url: params.url, bridgeChat: params.bridge_chat !== false, botToken: params.bot_token, channelId: params.channel_id }
        try {
          fs.mkdirSync(CONFIG_DIR, { recursive: true })
          fs.writeFileSync(configPath, JSON.stringify(config, null, 2))
          return { content: [{ type: 'text', text: JSON.stringify({ ok: true, webhook: params.url, bridgeChat: config.bridgeChat }) }] }
        } catch (err) {
          return { content: [{ type: 'text', text: JSON.stringify({ ok: false, error: err instanceof Error ? err.message : String(err) }) }], isError: true }
        }
      }

      if (action === 'status') {
        let config = null
        try {
          if (fs.existsSync(configPath)) { config = JSON.parse(fs.readFileSync(configPath, 'utf-8')) }
        } catch {}
        return { content: [{ type: 'text', text: JSON.stringify({ ok: true, config, pollingActive: discordPollTimer !== null, botConfig: discordBotConfig }) }] }
      }

      if (action === 'start') {
        if (!params.bot_token && !discordBotConfig?.bot_token) {
          return { content: [{ type: 'text', text: JSON.stringify({ error: 'bot_token required for start' }) }], isError: true }
        }
        if (!params.channel_id && !discordBotConfig?.channel_id) {
          return { content: [{ type: 'text', text: JSON.stringify({ error: 'channel_id required for start' }) }], isError: true }
        }
        discordBotConfig = {
          bot_token: params.bot_token || discordBotConfig!.bot_token,
          channel_id: params.channel_id || discordBotConfig!.channel_id,
        }
        try {
          let existing = {}
          try { if (fs.existsSync(configPath)) existing = JSON.parse(fs.readFileSync(configPath, 'utf-8')) } catch {}
          fs.writeFileSync(configPath, JSON.stringify({ ...existing, botToken: discordBotConfig.bot_token, channelId: discordBotConfig.channel_id }, null, 2))
        } catch {}
        if (discordPollTimer) { clearInterval(discordPollTimer); discordPollTimer = null }
        discordPollTimer = setInterval(async () => {
          try {
            const resp = await fetch(`https://discord.com/api/v10/channels/${discordBotConfig!.channel_id}/messages?limit=5`, {
              headers: { 'Authorization': `Bot ${discordBotConfig!.bot_token}`, 'Content-Type': 'application/json' },
            })
            if (resp.ok) {
              const messages = await resp.json() as any[]
              if (!discordLastMessageId) {
                const maxId = messages.reduce((max: string, m: any) => m.id > max ? m.id : max, '0')
                discordLastMessageId = maxId || '0'
                return
              }
              for (const msg of messages.reverse()) {
                if (BigInt(msg.id) > BigInt(discordLastMessageId!)) {
                  discordLastMessageId = msg.id
                  const author = msg.author?.global_name || msg.author?.username || 'Discord'
                  const content = msg.content || ''
                  if (content && !msg.author?.bot) {
                    try { await bridge.send('chat', { msg: `[Discord] ${author}: ${content}` }) } catch {}
                  }
                }
              }
            }
          } catch { /* polling error */ }
        }, 3000)
        return { content: [{ type: 'text', text: JSON.stringify({ ok: true, status: 'started', channel_id: discordBotConfig.channel_id }) }] }
      }

      if (action === 'stop') {
        if (discordPollTimer) { clearInterval(discordPollTimer); discordPollTimer = null }
        discordLastMessageId = null
        return { content: [{ type: 'text', text: JSON.stringify({ ok: true, status: 'stopped' }) }] }
      }

      return { content: [{ type: 'text', text: JSON.stringify({ ok: false, error: 'Invalid action' }) }], isError: true }
    },

    'mc_qq_bridge': async (args: any) => {
      const { action, endpoint, group_id } = s.QQBridgeSchema.parse(args)

      if (action === 'connect') {
        if (!endpoint || !group_id) {
          return { content: [{ type: 'text', text: JSON.stringify({ error: 'endpoint and group_id are required for connect' }) }], isError: true }
        }
        qqConfig = { endpoint, group_id }
        const configPath = path.join(CONFIG_DIR, 'qq-bridge.json')
        fs.mkdirSync(CONFIG_DIR, { recursive: true })
        fs.writeFileSync(configPath, JSON.stringify(qqConfig, null, 2))
        if (qqTimer) { clearInterval(qqTimer); qqTimer = null }
        qqTimer = setInterval(async () => {
          try {
            const resp = await fetch(`${qqConfig!.endpoint}/get_group_msg_history?group_id=${qqConfig!.group_id}`, {
              headers: { 'Content-Type': 'application/json' },
            })
            if (resp.ok) {
              const data = await resp.json() as any
              const messages = data.data?.messages || data.messages || []
              if (qqLastMessageId === 0 && messages.length > 0) {
                const ids = messages.map((m: any) => parseInt(m.message_id || m.id || '0')).filter((id: number) => id > 0)
                if (ids.length > 0) qqLastMessageId = Math.max(...ids)
                return
              }
              for (const msg of messages) {
                const mid = parseInt(msg.message_id || msg.id || '0')
                if (mid > qqLastMessageId) {
                  qqLastMessageId = mid
                  const sender = msg.sender?.nickname || msg.user_id || 'QQ'
                  const content = msg.message || ''
                  if (content) {
                    try { await bridge.send('chat', { msg: `[QQ] ${sender}: ${content}` }) } catch {}
                  }
                }
              }
            }
          } catch { /* polling error */ }
        }, 2000)
        return { content: [{ type: 'text', text: JSON.stringify({ ok: true, endpoint, group_id }) }] }
      }

      if (action === 'disconnect') {
        if (qqTimer) { clearInterval(qqTimer); qqTimer = null }
        qqConfig = null; qqLastMessageId = 0
        return { content: [{ type: 'text', text: JSON.stringify({ ok: true, status: 'disconnected' }) }] }
      }

      return { content: [{ type: 'text', text: JSON.stringify({ ok: true, connected: qqConfig !== null, config: qqConfig, pollingActive: qqTimer !== null }) }] }
    },

    'mc_qq_send': async (args: any) => {
      const { message, group_id } = s.QQSendSchema.parse(args)
      const targetGroup = group_id || qqConfig?.group_id
      if (!qqConfig?.endpoint || !targetGroup) {
        return { content: [{ type: 'text', text: JSON.stringify({ error: 'QQ bridge not connected. Use mc_qq_bridge first.' }) }], isError: true }
      }
      try {
        const resp = await fetch(`${qqConfig.endpoint}/send_group_msg`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ group_id: targetGroup, message }),
        })
        if (resp.ok) {
          return { content: [{ type: 'text', text: JSON.stringify({ ok: true, sent: true }) }] }
        }
        const errText = await resp.text()
        return { content: [{ type: 'text', text: JSON.stringify({ ok: false, error: `HTTP ${resp.status}: ${errText}` }) }], isError: true }
      } catch (err) {
        return { content: [{ type: 'text', text: JSON.stringify({ ok: false, error: err instanceof Error ? err.message : String(err) }) }], isError: true }
      }
    },

    'mc_auto_brew': async (args: any) => {
      await requireBridge()
      const { action, ingredient, slot } = s.AutoBrewSchema.parse(args)
      const results: any[] = []

      try {
        const screenRaw = await bridge.send('screen')
        const screen = JSON.parse(screenRaw)

        if (action === 'status') {
          results.push({ screenType: screen.type || 'unknown' })
          results.push({ fuel: screen.fuel || 0, fuelTotal: screen.fuelTotal || 0 })
          results.push({ brewTime: screen.brewTime || 0, brewTimeTotal: screen.brewTimeTotal || 0 })
          results.push({ slots: (screen.slots || []).length })
          if (screen.ingredient) results.push({ ingredient: screen.ingredient })
          if (screen.bottles) results.push({ bottles: screen.bottles })
        }

        if (action === 'brew' && ingredient) {
          const findRaw = await bridge.send('find_item', { name: ingredient })
          const found = JSON.parse(findRaw)
          const targetSlot = slot ?? 0
          const INGREDIENT_SLOT = 3
          if (found.slots && found.slots.length > 0) {
            const invSlot = found.slots[0].inventorySlot ?? found.slots[0].slot
            await bridge.send('move_item', { from: invSlot, to: INGREDIENT_SLOT })
            await new Promise(r => setTimeout(r, 500))
            results.push({ action: 'added_ingredient', from: invSlot, to: INGREDIENT_SLOT })
            await bridge.send('gui_click', { slot: INGREDIENT_SLOT, button: 0, action: 'PICKUP' })
            await new Promise(r => setTimeout(r, 200))
            await bridge.send('gui_click', { slot: INGREDIENT_SLOT, button: 0, action: 'PICKUP' })
            results.push({ action: 'brewing_started' })
          } else {
            results.push({ error: `No ${ingredient} found in inventory` })
          }
        }

        if (action === 'collect') {
          for (let i = 0; i < 3; i++) {
            try {
              await bridge.send('gui_click', { slot: i, button: 0, action: 'QUICK_MOVE' })
              await new Promise(r => setTimeout(r, 200))
            } catch { /* skip */ }
          }
          results.push({ action: 'collected_potions' })
        }
      } catch (err) {
        results.push({ error: err instanceof Error ? err.message : String(err) })
      }

      return { content: [{ type: 'text', text: JSON.stringify(results, null, 2) }] }
    },

    'mc_auto_cook': async (args: any) => {
      await requireBridge()
      const { action, fuel, input, count } = s.AutoCookSchema.parse(args)
      const results: any[] = []

      try {
        const screenRaw = await bridge.send('screen')
        const screen = JSON.parse(screenRaw)

        if (action === 'status') {
          results.push({ screenType: screen.type || 'unknown' })
          results.push({ fuel: screen.fuel || 0, fuelTotal: screen.fuelTotal || 0 })
          results.push({ progress: screen.progress || 0, progressTotal: screen.progressTotal || 0 })
          results.push({ slots: (screen.slots || []).length })
          if (screen.input) results.push({ input: screen.input })
          if (screen.output) results.push({ output: screen.output })
        }

        if (action === 'cook') {
          if (fuel) {
            const fuelRaw = await bridge.send('find_item', { name: fuel })
            const fuelFound = JSON.parse(fuelRaw)
            if (fuelFound.slots && fuelFound.slots.length > 0) {
              const fuelSlot = fuelFound.slots[0].inventorySlot ?? fuelFound.slots[0].slot
              await bridge.send('move_item', { from: fuelSlot, to: 1 })
              await new Promise(r => setTimeout(r, 500))
              results.push({ action: 'added_fuel', from: fuelSlot })
            }
          }

          if (input) {
            const inputRaw = await bridge.send('find_item', { name: input })
            const inputFound = JSON.parse(inputRaw)
            if (inputFound.slots && inputFound.slots.length > 0) {
              const maxCount = count || 64
              let moved = 0
              for (const slotItem of inputFound.slots) {
                if (moved >= maxCount) break
                const invSlot = slotItem.inventorySlot ?? slotItem.slot
                await bridge.send('move_item', { from: invSlot, to: 0 })
                await new Promise(r => setTimeout(r, 300))
                moved++
              }
              results.push({ action: 'added_input', count: moved })
            }
          }

          results.push({ action: 'cooking_started' })
        }

        if (action === 'collect') {
          try {
            await bridge.send('gui_click', { slot: 2, button: 0, action: 'QUICK_MOVE' })
            results.push({ action: 'collected_output' })
          } catch {
            results.push({ action: 'no_output' })
          }
        }
      } catch (err) {
        results.push({ error: err instanceof Error ? err.message : String(err) })
      }

      return { content: [{ type: 'text', text: JSON.stringify(results, null, 2) }] }
    },

    'mc_session_report': async (args: any) => {
      await requireBridge()
      const opts = s.SessionReportSchema.parse(args ?? {})
      const report: Record<string, any> = {
        generated: new Date().toISOString(),
        session: {}
      }

      try {
        const posRaw = await bridge.send('pos')
        const pos = JSON.parse(posRaw)
        report.session.position = { x: pos.x, y: pos.y, z: pos.z, dimension: pos.dimension }
      } catch {}

      try {
        const timeRaw = await bridge.send('time')
        const time = JSON.parse(timeRaw)
        report.session.time = time
      } catch {}

      if (opts.include_stats !== false) {
        try {
          const statsRaw = await bridge.send('statistics')
          report.stats = JSON.parse(statsRaw)
        } catch {}
        try {
          const xpRaw = await bridge.send('xp')
          report.xp = JSON.parse(xpRaw)
        } catch {}
      }

      if (opts.include_chat !== false) {
        try {
          const chatRaw = await bridge.send('chatlog', { count: 30 })
          report.chat = JSON.parse(chatRaw)
        } catch {}
      }

      if (opts.include_inventory) {
        try {
          const invRaw = await bridge.send('inv')
          report.inventory = JSON.parse(invRaw)
        } catch {}
      }

      if (opts.include_memories !== false) {
        try {
          const memRaw = await bridge.send('memory_list', { limit: 50 })
          report.memories = JSON.parse(memRaw)
        } catch {}
      }

      if (opts.ai_summary) {
        try {
          const summary = await callDeepSeekAnalysis(
            'Summarize this Minecraft session report. What was accomplished, notable events, current state:',
            JSON.stringify(report, null, 2)
          )
          report.ai_summary = summary
        } catch {}
      }

      return { content: [{ type: 'text', text: JSON.stringify(report, null, 2) }] }
    },

    'mc_set_alias': async (args: any) => {
      const { alias, command, remove } = s.SetAliasSchema.parse(args)
      const aliasesPath = path.join(CONFIG_DIR, 'aliases.json')

      let aliases: Record<string, string> = {}
      try {
        if (fs.existsSync(aliasesPath)) {
          aliases = JSON.parse(fs.readFileSync(aliasesPath, 'utf-8'))
        }
      } catch {}

      if (remove) {
        delete aliases[alias]
      } else if (command) {
        aliases[alias] = command
      }

      try {
        fs.mkdirSync(path.dirname(aliasesPath), { recursive: true })
        fs.writeFileSync(aliasesPath, JSON.stringify(aliases, null, 2))
      } catch (err) {
        return { content: [{ type: 'text', text: JSON.stringify({ ok: false, error: String(err) }) }] }
      }

      return { content: [{ type: 'text', text: JSON.stringify({ ok: true, alias, command: command || '(removed)', total: Object.keys(aliases).length }) }] }
    },

    'mc_aliases': async (args: any) => {
      const aliasesPath = path.join(CONFIG_DIR, 'aliases.json')
      let aliases: Record<string, string> = {}
      try {
        if (fs.existsSync(aliasesPath)) {
          aliases = JSON.parse(fs.readFileSync(aliasesPath, 'utf-8'))
        }
      } catch {}

      return { content: [{ type: 'text', text: JSON.stringify({ aliases, count: Object.keys(aliases).length }, null, 2) }] }
    },

    'mc_memory_dedup': async (args: any) => {
      await requireBridge()
      const { action, strategy = 'content' } = s.MemoryDedupSchema.parse(args)

      const raw = await bridge.send('memory_list', { limit: 200 })
      const data = JSON.parse(raw)
      const memories = data.memories || data || []

      if (!Array.isArray(memories) || memories.length === 0) {
        return { content: [{ type: 'text', text: JSON.stringify({ error: 'No memories found' }) }] }
      }

      const duplicates: Array<{ original: any; duplicate: any; reason: string }> = []
      const seen = new Map<string, any>()

      for (const mem of memories) {
        let key = ''
        if (strategy === 'content') {
          key = (mem.content || '').toLowerCase().trim()
        } else if (strategy === 'location') {
          key = `${mem.x || 0},${mem.y || 0},${mem.z || 0}`
        }

        if (!key) continue

        if (seen.has(key)) {
          duplicates.push({ original: seen.get(key), duplicate: mem, reason: strategy })
        } else {
          seen.set(key, mem)
        }
      }

      if (action === 'remove' && duplicates.length > 0) {
        let removed = 0
        for (const dup of duplicates) {
          const keep = (dup.original.importance || 0) >= (dup.duplicate.importance || 0) ? dup.original : dup.duplicate
          const remove = keep === dup.original ? dup.duplicate : dup.original

          try {
            await bridge.send('memory_delete', { id: remove.id })
            removed++
          } catch {}
        }
        return { content: [{ type: 'text', text: JSON.stringify({ action: 'removed', count: removed, remaining: memories.length - removed }) }] }
      }

      return { content: [{ type: 'text', text: JSON.stringify({ action: 'scan', duplicatesFound: duplicates.length, duplicates }, null, 2) }] }
    },

    'mc_entity_density_map': async (args: any) => {
      await requireBridge()
      const { radius = 3, type } = s.EntityDensityMapSchema.parse(args ?? {})

      let playerX = 0, playerZ = 0
      try {
        const posRaw = await bridge.send('pos'); const pos = JSON.parse(posRaw)
        playerX = pos.x || 0; playerZ = pos.z || 0
      } catch {}

      const baseCx = Math.floor(playerX / 16); const baseCz = Math.floor(playerZ / 16)

      const raw = await bridge.send('entities', { r: radius })
      const data = JSON.parse(raw)
      const allEntities = data.entities || []
      const filtered = type ? allEntities.filter((e: any) => (e.name || '').toLowerCase().includes(type.toLowerCase()) || (e.type || '').toLowerCase().includes(type.toLowerCase())) : allEntities

      const entries = filtered.map((e: any) => ({
        name: e.name || e.type || '?',
        distance: Math.round(Math.sqrt(((e.x || 0) - playerX) ** 2 + ((e.z || 0) - playerZ) ** 2)),
      }))
      entries.sort((a: any, b: any) => a.distance - b.distance)

      return { content: [{ type: 'text', text: JSON.stringify({
        entityCount: entries.length,
        entities: entries,
        note: 'Density based on player-centered scan (per-chunk query not available)',
        radius,
      }, null, 2) }] }
    },

    'mc_ore_distribution': async (args: any) => {
      await requireBridge()
      const { ores, radius = 16 } = s.OreDistributionSchema.parse(args ?? {})
      const oreTypes = ores || ['diamond_ore', 'iron_ore', 'coal_ore', 'copper_ore', 'gold_ore', 'redstone_ore', 'lapis_ore', 'emerald_ore']

      const distribution: Record<string, Record<number, number>> = {}
      for (const ore of oreTypes) { distribution[ore] = {} }

      for (const ore of oreTypes) {
        try {
          const result = await bridge.send('find_blocks', { block: ore, radius })
          const data = JSON.parse(result)
          const blocks = data.blocks || []
          for (const b of blocks) {
            const y = Math.floor((b.y || 0) / 5) * 5
            if (!distribution[ore][y]) distribution[ore][y] = 0
            distribution[ore][y]++
          }
        } catch {}
      }

      return { content: [{ type: 'text', text: JSON.stringify({ distribution, totalByOre: Object.fromEntries(Object.entries(distribution).map(([k, v]) => [k, Object.values(v).reduce((a, b) => a + b, 0)])) }, null, 2) }] }
    },

    'mc_screenshot_analyze': async (args: any) => {
      await requireBridge()
      const { question } = s.ScreenshotAnalyzeSchema.parse(args ?? {})

      try {
        await bridge.send('screenshot')
        await new Promise(r => setTimeout(r, 2000))
      } catch {
        return { content: [{ type: 'text', text: JSON.stringify({ error: 'Failed to take screenshot' }) }] }
      }

      let screenshotPath = 'unknown'
      try {
        const logsRaw = await bridge.send('logs', { lines: 20 })
        const logs = JSON.parse(logsRaw)
        const lines = logs.lines || []
        for (const line of lines.reverse()) {
          const match = (typeof line === 'string' ? line : line.text || '').match(/saved as (.+\.png)/i)
          if (match) { screenshotPath = match[1]; break }
        }
      } catch {}

      return { content: [{ type: 'text', text: JSON.stringify({ screenshot_taken: true, file: screenshotPath, note: 'DeepSeek API does not support image analysis. The screenshot was saved to disk for manual review.' }) }] }
    },

    'mc_entity_track': async (args: any) => {
      await requireBridge()
      const { id, duration = 10, interval = 1 } = s.EntityTrackSchema.parse(args)

      const path: Array<{ time: number, x: number, y: number, z: number }> = []
      const startTime = Date.now()
      const maxDuration = duration * 1000
      const pollInterval = interval * 1000

      while (Date.now() - startTime < maxDuration) {
        try {
          const raw = await bridge.send('entity', { id })
          const entity = JSON.parse(raw)
          if (entity.x !== undefined) {
            path.push({ time: Date.now() - startTime, x: entity.x, y: entity.y || entity.y || 0, z: entity.z })
          }
        } catch {}
        await new Promise(r => setTimeout(r, pollInterval))
      }

      let totalDistance = 0
      for (let i = 1; i < path.length; i++) {
        const dx = path[i].x - path[i-1].x
        const dz = path[i].z - path[i-1].z
        totalDistance += Math.sqrt(dx * dx + dz * dz)
      }

      return { content: [{ type: 'text', text: JSON.stringify({ trackedId: id, duration: `${duration}s`, samples: path.length, totalDistance: Math.round(totalDistance * 10) / 10, path }, null, 2) }] }
    },

    'mc_auto_shear': async (args: any) => {
      await requireBridge()
      const { radius = 10, max = 5 } = s.AutoShearSchema.parse(args ?? {})

      const raw = await bridge.send('entities', { r: radius })
      const data = JSON.parse(raw)
      const sheep = (data.entities || []).filter((e: any) => (e.name || '').toLowerCase().includes('sheep'))

      if (sheep.length === 0) {
        return { content: [{ type: 'text', text: JSON.stringify({ error: 'No sheep found nearby' }) }] }
      }

      try { await bridge.send('find_item', { name: 'shears' }) } catch {}

      let sheared = 0
      const results: any[] = []

      for (let i = 0; i < Math.min(sheep.length, max); i++) {
        const s = sheep[i]
        try {
          await bridge.send('look_at', { x: s.x, y: s.y + 1, z: s.z })
          await new Promise(r => setTimeout(r, 300))
          await bridge.send('interact_entity', { id: s.id })
          await new Promise(r => setTimeout(r, 500))
          sheared++
          results.push({ id: s.id, status: 'sheared' })
        } catch (err) {
          results.push({ id: s.id, error: err instanceof Error ? err.message : String(err) })
        }
      }

      return { content: [{ type: 'text', text: JSON.stringify({ sheared, total: results.length, results }, null, 2) }] }
    },

    'mc_autonomous_goal': async (args: any) => {
      const { goal: autoGoal, context: autoCtx, preview } = s.AutonomousGoalSchema.parse(args)
      await requireBridge()

      let playerPos = 'unknown'
      try {
        const posRaw = await bridge.send('pos')
        const pos = JSON.parse(posRaw)
        playerPos = `${pos.x?.toFixed(1) || 0} ${pos.y?.toFixed(1) || 64} ${pos.z?.toFixed(1) || 0}`
      } catch { /* ignore */ }

      let inventory = 'unknown'
      try {
        const invRaw = await bridge.send('inv')
        inventory = invRaw.substring(0, 2000)
      } catch { /* ignore */ }

      let nearbyBlocks = 'unknown'
      try {
        const blocksRaw = await bridge.send('find_blocks', { block: '*', radius: 10 })
        nearbyBlocks = blocksRaw.substring(0, 1000)
      } catch { /* ignore */ }

      const plan = await callDeepSeekAnalysis(
        `You are a Minecraft AI agent that breaks down goals into step-by-step plans.
You have access to these tools through the mc-bridge mod:
- /setblock <x> <y> <z> <block>: place a block
- /fill <x1> <y1> <z1> <x2> <y2> <z2> <block>: fill area
- /give <player> <item> [count]: give items
- /tp <x> <y> <z>: teleport
- /time set <day/night>: set time
- /weather <clear/rain/thunder>: set weather
- mc_walk_to: walk to position
- mc_chat: send chat message
- mc_command: execute any command
- mc_press_key: press keys
- mc_break_block: break a block
- mc_place_block: place a block
- mc_build_template: build a predefined structure
- mc_run_script: execute a sequence of steps
- mc_build_geometry: build geometric shapes
- mc_scan_terrain: scan nearby terrain
- mc_mirror_build: mirror a building
- mc_undo_build: undo last build
- mc_get_inventory: check inventory
- mc_craft_item: craft items
- mc_sort_inventory: sort inventory
- mc_refill: refill hotbar
- mc_auto_fish: auto fishing
- mc_take_screenshot: take a screenshot
- mc_look_at: look at coordinates
- mc_highlight_block: highlight a block
- mc_press_key: simulate key press
- mc_use_item: use held item
- mc_equip_item: equip item
- mc_drop_item: drop item

Output a JSON array of step objects. Each step has:
{ "type": "command/chat/wait/walk/script", "detail": "what to do", "cmd": "command without / (for command type)", "msg": "chat message (for chat)", "ms": milliseconds (for wait, default 1000)", "x/y/z": coordinates (for walk)" }

Be realistic. Max 15 steps. Use /give and /setblock for building. Use existing blocks nearby.
OUTPUT ONLY THE JSON ARRAY, no explanation.`,
        `GOAL: ${autoGoal}\n${autoCtx ? `CONTEXT: ${autoCtx}\n` : ''}PLAYER POSITION: ${playerPos}\nINVENTORY: ${inventory}\nNEARBY: ${nearbyBlocks}`,
        0.3, 4096
      )

      let steps
      try {
        steps = JSON.parse(plan)
        if (!Array.isArray(steps)) throw new Error('Not an array')
      } catch {
        return { content: [{ type: 'text', text: `AI failed to generate a valid plan. Raw response:\n${plan}` }] }
      }

      if (preview) {
        return { content: [{ type: 'text', text: JSON.stringify({ goal: autoGoal, totalSteps: steps.length, steps }, null, 2) }] }
      }

      const results: any[] = []
      let successCount = 0
      let failCount = 0

      for (let i = 0; i < steps.length; i++) {
        const step = steps[i]
        const stepResult: any = { step: i + 1, type: step.type, detail: step.detail, status: 'pending' }
        try {
          switch (step.type) {
            case 'command': {
              const r = await bridge.send('exec', { cmd: step.cmd })
              stepResult.status = 'ok'
              stepResult.result = r.substring(0, 200)
              break
            }
            case 'chat': {
              await bridge.send('chat', { msg: step.msg || step.detail })
              stepResult.status = 'ok'
              break
            }
            case 'wait': {
              await new Promise(r => setTimeout(r, step.ms || 1000))
              stepResult.status = 'ok'
              break
            }
            case 'walk': {
              await bridge.send('lookat', { x: step.x, y: step.y, z: step.z })
              await bridge.send('press_key', { key: 'forward', duration: 2000 })
              stepResult.status = 'ok'
              break
            }
            case 'script': {
              if (step.steps) {
                for (const sub of step.steps) {
                  if (sub.type === 'command') {
                    await bridge.send('exec', { cmd: sub.cmd })
                  } else if (sub.type === 'chat') {
                    await bridge.send('chat', { msg: sub.msg || sub.detail })
                  } else if (sub.type === 'wait') {
                    await new Promise(r => setTimeout(r, sub.ms || 500))
                  }
                }
              }
              stepResult.status = 'ok'
              break
            }
            default: {
              await bridge.send('exec', { cmd: step.cmd || step.detail })
              stepResult.status = 'ok'
            }
          }
          successCount++
        } catch (err) {
          stepResult.status = 'failed'
          stepResult.error = err instanceof Error ? err.message : String(err)
          failCount++
        }
        results.push(stepResult)
      }

      const memory = `Executed goal: ${autoGoal}. ${successCount}/${steps.length} steps succeeded.`
      try { await bridge.send('memory_add', { content: memory, category: 'event', importance: 4, tags: 'autonomous,goal' }) } catch { /* */ }

      return {
        content: [{
          type: 'text',
          text: JSON.stringify({
            goal: autoGoal,
            totalSteps: steps.length,
            succeeded: successCount,
            failed: failCount,
            steps: results,
          }, null, 2),
        }],
      }
    },
    'mc_attack_entity': async (args: any) => {
      const { id } = s.AttackEntitySchema.parse(args)
      await requireBridge()
      const result = await bridge.send('attack_entity', { id })
      return { content: [{ type: 'text', text: result }] }
    },
    'mc_interact_entity': async (args: any) => {
      const { id, hand } = s.InteractEntitySchema.parse(args)
      await requireBridge()
      const payload: Record<string, unknown> = { id }
      if (hand) payload.hand = hand
      const result = await bridge.send('interact_entity', payload)
      return { content: [{ type: 'text', text: result }] }
    },
    'mc_ride_entity': async (args: any) => {
      const { id } = s.RideEntitySchema.parse(args)
      await requireBridge()
      const payload: Record<string, unknown> = {}
      if (id !== undefined) payload.id = id
      const result = await bridge.send('ride_entity', payload)
      return { content: [{ type: 'text', text: result }] }
    },
    'mc_memory_export': async (args: any) => {
      const { format } = s.MemoryExportSchema.parse(args ?? {})
      await requireBridge()
      const result = await bridge.send('memory_export', { format: format || 'json' })
      return { content: [{ type: 'text', text: result }] }
    },
    'mc_waypoint_export': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('waypoint_export')
      return { content: [{ type: 'text', text: result }] }
    },
    'mc_waypoint_import': async (args: any) => {
      const { waypoints, merge } = s.WaypointImportSchema.parse(args)
      await requireBridge()
      const result = await bridge.send('waypoint_import', { waypoints, merge: merge ?? false })
      return { content: [{ type: 'text', text: result }] }
    },
    'mc_travel_log_stats': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('travel_log_stats')
      return { content: [{ type: 'text', text: result }] }
    },
    'mc_comparator_read': async (args: any) => {
      const { x, y, z } = s.ReadComparatorSchema.parse(args)
      await requireBridge()
      const result = await bridge.send('read_comparator', { x, y, z })
      return { content: [{ type: 'text', text: result }] }
    },
    'mc_toggle_block': async (args: any) => {
      const { x, y, z } = s.ToggleBlockSchema.parse(args)
      await requireBridge()
      const result = await bridge.send('toggle_block', { x, y, z })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_entity_selector': async (args: any) => {
      await requireBridge()
      const { type, min_distance, max_distance, limit = 50 } = s.EntitySelectorSchema.parse(args ?? {})

      let entities: any[] = []
      const ranges = [16, 32, 64, 128]

      for (const r of ranges) {
        if (entities.length >= limit) break
        try {
          const raw = await bridge.send('entities', { r })
          const data = JSON.parse(raw)
          if (data.entities) {
            entities = data.entities
          }
        } catch { continue }
      }

      let filtered = entities

      if (type) {
        const lowerType = type.toLowerCase()
        filtered = filtered.filter((e: any) =>
          (e.name?.toLowerCase().includes(lowerType)) ||
          (e.type?.toLowerCase().includes(lowerType)) ||
          (e.id?.toString() === lowerType)
        )
      }

      if (min_distance !== undefined) {
        filtered = filtered.filter((e: any) => (e.distance ?? 0) >= min_distance)
      }

      if (max_distance !== undefined) {
        filtered = filtered.filter((e: any) => (e.distance ?? 999) <= max_distance)
      }

      filtered = filtered.slice(0, limit)

      return { content: [{ type: 'text', text: JSON.stringify({ total: filtered.length, entities: filtered }, null, 2) }] }
    },

    'mc_kill_all': async (args: any) => {
      await requireBridge()
      const { type, radius = 16, max = 10 } = s.KillAllSchema.parse(args)

      const entitiesRaw = await bridge.send('entities', { r: radius })
      const entities = JSON.parse(entitiesRaw)

      const lowerType = type.toLowerCase()
      const targets = (entities.entities || []).filter((e: any) =>
        e.name?.toLowerCase().includes(lowerType) || e.type?.toLowerCase().includes(lowerType)
      ).slice(0, max)

      let killed = 0
      const results: any[] = []

      for (const target of targets) {
        try {
          await bridge.send('attack_entity', { id: target.id })
          killed++
          results.push({ id: target.id, name: target.name || target.type, status: 'attacked' })
          await new Promise(r => setTimeout(r, 200))
        } catch (err) {
          results.push({ id: target.id, error: err instanceof Error ? err.message : String(err) })
        }
      }

      return { content: [{ type: 'text', text: JSON.stringify({ killed, total: targets.length, results }, null, 2) }] }
    },

    'mc_nearest_structure': async (args: any) => {
      await requireBridge()
      const { structure } = s.NearestStructureSchema.parse(args)

      await bridge.send('exec', { cmd: `locate structure ${structure}` })

      await new Promise(r => setTimeout(r, 2000))

      const chatRaw = await bridge.send('chatlog', { count: 10 })
      const chat = JSON.parse(chatRaw)

      let coordinates: number[] | null = null
      let distance = 0
      let foundMsg = ''

      const messages = chat.messages || chat.chatlog || []
      for (const msg of messages) {
        const text = typeof msg === 'string' ? msg : (msg.content || msg.text || '')

        const coordMatch = text.match(/\[(-?\d+),?\s*(-?\d+),?\s*(-?\d+)\]/) ||
                           text.match(/at\s+(-?\d+)\s+[~\s]\s*(-?\d+)\s+[~\s]\s*(-?\d+)/)

        const distMatch = text.match(/(\d+)\s*blocks? away/)

        if (coordMatch) {
          coordinates = [parseInt(coordMatch[1]), parseInt(coordMatch[2]), parseInt(coordMatch[3])]
        }
        if (distMatch) {
          distance = parseInt(distMatch[1])
        }
        if (text.includes('locate') || text.includes(structure)) {
          foundMsg = text
        }
      }

      return {
        content: [{
          type: 'text',
          text: JSON.stringify({
            structure,
            found: coordinates !== null,
            coordinates,
            distance,
            rawMessage: foundMsg,
          }, null, 2)
        }]
      }
    },

    'mc_spawn_particle': async (args: any) => {
      await requireBridge()
      const { particle, x, y, z, vx, vy, vz, count } = s.SpawnParticleSchema.parse(args)
      const result = await bridge.send('spawn_particle', { particle, x, y, z, vx: vx ?? 0, vy: vy ?? 0.1, vz: vz ?? 0, count: count ?? 10 })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_play_sound': async (args: any) => {
      await requireBridge()
      const payload = s.PlaySoundSchema.parse(args)
      const result = await bridge.send('play_sound', payload)
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_display_title': async (args: any) => {
      await requireBridge()
      const { title, subtitle, fadeIn = 10, stay = 40, fadeOut = 10 } = s.DisplayTitleSchema.parse(args)

      try {
        if (subtitle) {
          await bridge.send('exec', { cmd: `title @s times ${fadeIn} ${stay} ${fadeOut}` })
          await bridge.send('exec', { cmd: `title @s subtitle ${JSON.stringify(subtitle).replace(/"/g, '\\"')}` })
        }
        await bridge.send('exec', { cmd: `title @s title ${JSON.stringify(title).replace(/"/g, '\\"')}` })
        return { content: [{ type: 'text', text: JSON.stringify({ ok: true, title, subtitle }) }] }
      } catch (err) {
        return { content: [{ type: 'text', text: JSON.stringify({ ok: false, error: String(err) }) }] }
      }
    },

    'mc_config_reload': async (args: any) => {
      await requireBridge()
      const result = await bridge.send('config_reload')
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_auto_tnt': async (args: any) => {
      await requireBridge()
      const { x, y, z } = s.AutoTntSchema.parse(args)
      const result = await bridge.send('auto_tnt', { x, y, z })
      return { content: [{ type: 'text', text: result }] }
    },

    'mc_auto_enchant': async (args: any) => {
      await requireBridge()
      const { action, slot, item_slot } = s.AutoEnchantSchema.parse(args)
      const results: any[] = []

      if (action === 'status') {
        const raw = await bridge.send('screen')
        const screen = JSON.parse(raw)
        results.push({ type: screen.type || 'unknown' })
        if (screen.enchantmentOptions) results.push({ enchantments: screen.enchantmentOptions })
        if (screen.lapis) results.push({ lapis: screen.lapis })
        if (screen.levels) results.push({ levels: screen.levels })
        results.push({ slots: (screen.slots || []).length })
      }

      if (action === 'enchant' && item_slot !== undefined) {
        await bridge.send('move_item', { from: item_slot, to: 0 })
        await new Promise(r => setTimeout(r, 500))

        const enchantSlot = slot ?? 0
        await bridge.send('gui_click', { slot: enchantSlot, button: 0, action: 'PICKUP' })
        await new Promise(r => setTimeout(r, 500))

        results.push({ action: 'enchanted', slot: enchantSlot })
      }

      if (action === 'collect') {
        try {
          await bridge.send('gui_click', { slot: 0, button: 0, action: 'QUICK_MOVE' })
          results.push({ action: 'collected' })
        } catch {
          results.push({ error: 'no item to collect' })
        }
      }

      return { content: [{ type: 'text', text: JSON.stringify(results, null, 2) }] }
    },

    'mc_auto_smith': async (args: any) => {
      await requireBridge()
      const { action, upgrade_slot, item_slot } = s.AutoSmithSchema.parse(args)
      const results: any[] = []

      if (action === 'status') {
        const raw = await bridge.send('screen')
        const screen = JSON.parse(raw)
        results.push({ type: screen.type || 'unknown', slots: (screen.slots || []).length })
      }

      if (action === 'upgrade') {
        if (upgrade_slot !== undefined) {
          await bridge.send('move_item', { from: upgrade_slot, to: 0 })
          await new Promise(r => setTimeout(r, 300))
        }
        if (item_slot !== undefined) {
          await bridge.send('move_item', { from: item_slot, to: 1 })
          await new Promise(r => setTimeout(r, 300))
        }
        await bridge.send('gui_click', { slot: 2, button: 0, action: 'QUICK_MOVE' })
        await new Promise(r => setTimeout(r, 300))
        results.push({ action: 'upgraded' })
      }

      if (action === 'collect') {
        await bridge.send('gui_click', { slot: 2, button: 0, action: 'QUICK_MOVE' })
        results.push({ action: 'collected' })
      }

      return { content: [{ type: 'text', text: JSON.stringify(results, null, 2) }] }
    },

    'mc_auto_anvil': async (args: any) => {
      await requireBridge()
      const { action, left_slot, right_slot, rename } = s.AutoAnvilSchema.parse(args)
      const results: any[] = []

      if (action === 'status') {
        const raw = await bridge.send('screen')
        const screen = JSON.parse(raw)
        results.push({ type: screen.type || 'unknown', cost: screen.xpCost || screen.cost || 0, slots: (screen.slots || []).length })
      }

      if (action === 'combine') {
        if (left_slot !== undefined) {
          await bridge.send('move_item', { from: left_slot, to: 0 })
          await new Promise(r => setTimeout(r, 300))
        }
        if (right_slot !== undefined) {
          await bridge.send('move_item', { from: right_slot, to: 1 })
          await new Promise(r => setTimeout(r, 300))
        }
        if (rename) {
          results.push({ note: `rename to "${rename}" - may not work via API` })
        }
        await new Promise(r => setTimeout(r, 500))
        await bridge.send('gui_click', { slot: 2, button: 0, action: 'QUICK_MOVE' })
        await new Promise(r => setTimeout(r, 300))
        results.push({ action: 'combined' })
      }

      if (action === 'collect') {
        await bridge.send('gui_click', { slot: 2, button: 0, action: 'QUICK_MOVE' })
        results.push({ action: 'collected' })
      }

      return { content: [{ type: 'text', text: JSON.stringify(results, null, 2) }] }
    },

    'mc_projectile_simulate': async (args: any) => {
      await requireBridge()
      const { pitch: customPitch, yaw: customYaw, velocity = 1.5, gravity = true, steps = 50 } = s.ProjectileSimulateSchema.parse(args ?? {})

      let px = 0, py = 0, pz = 0, pitch = 0, yaw = 0
      try {
        const raw = await bridge.send('pos')
        const pos = JSON.parse(raw)
        px = pos.x || 0; py = (pos.y || 0) + 1.6; pz = pos.z || 0
        pitch = (customPitch ?? (pos.pitch || 0)) * Math.PI / 180
        yaw = (customYaw ?? (pos.yaw || 0)) * Math.PI / 180
      } catch {
        return { content: [{ type: 'text', text: 'Cannot get player position' }] }
      }

      const vx = -Math.sin(yaw) * Math.cos(pitch) * velocity
      const vy = -Math.sin(pitch) * velocity
      const vz = Math.cos(yaw) * Math.cos(pitch) * velocity

      let cx = px, cy = py, cz = pz
      let cvx = vx, cvy = vy, cvz = vz
      const g = gravity ? 0.05 : 0

      const trajectory: Array<{ x: number, y: number, z: number }> = [{ x: Math.round(cx * 10) / 10, y: Math.round(cy * 10) / 10, z: Math.round(cz * 10) / 10 }]

      for (let i = 0; i < steps; i++) {
        cx += cvx; cy += cvy; cz += cvz
        cvy -= g
        cvx *= 0.99; cvy *= 0.99; cvz *= 0.99

        trajectory.push({ x: Math.round(cx * 10) / 10, y: Math.round(cy * 10) / 10, z: Math.round(cz * 10) / 10 })

        if (cy < -64) break
      }

      const landing = trajectory[trajectory.length - 1]
      const dx = landing.x - px; const dz = landing.z - pz
      const distance = Math.round(Math.sqrt(dx * dx + dz * dz) * 10) / 10

      return { content: [{ type: 'text', text: JSON.stringify({ start: { x: px, y: py, z: pz }, landing, distance, steps: trajectory.length, trajectory }, null, 2) }] }
    },

    'mc_network_graph': async (args: any) => {
      await requireBridge()
      const players: Array<{ name: string, x: number, y: number, z: number, distance: number, ping: number }> = []

      let myX = 0, myZ = 0
      try {
        const posRaw = await bridge.send('pos')
        const pos = JSON.parse(posRaw)
        myX = pos.x || 0; myZ = pos.z || 0
        players.push({ name: 'You', x: pos.x || 0, y: pos.y || 0, z: pos.z || 0, distance: 0, ping: 0 })
      } catch {}

      try {
        const raw = await bridge.send('entities', { r: 128 })
        const data = JSON.parse(raw)
        for (const e of (data.entities || [])) {
          if (e.type === 'player' || e.name === 'player' || e.isPlayer) {
            players.push({ name: e.displayName || e.name || 'Unknown', x: e.x || 0, y: e.y || 0, z: e.z || 0, distance: e.distance || 0, ping: e.ping || 0 })
          }
        }
      } catch {}

      try {
        const infoRaw = await bridge.send('info')
        const info = JSON.parse(infoRaw)
        const playerList = info.playerList || []
        for (const p of playerList) {
          const existing = players.find(pl => pl.name === p.name)
          if (existing && p.ping !== undefined) existing.ping = p.ping
        }
      } catch {}

      return { content: [{ type: 'text', text: JSON.stringify({ playerCount: players.length, players, center: { x: myX, z: myZ } }, null, 2) }] }
    },

    'mc_chat_mimic': async (args: any) => {
      await requireBridge()
      const { player, message: mimicMsg, count = 30, send } = s.ChatMimicSchema.parse(args)

      let targetMessages = 'No messages found'
      try {
        const raw = await bridge.send('chatlog', { count })
        const data = JSON.parse(raw)
        const messages = data.messages || data.chatlog || []
        targetMessages = messages
          .filter((m: any) => {
            const text = typeof m === 'string' ? m : (m.content || m.text || '')
            return text.includes(player) || text.toLowerCase().startsWith('<') && text.toLowerCase().includes(player.toLowerCase())
          })
          .slice(0, 20)
          .map((m: any) => typeof m === 'string' ? m : (m.content || m.text || ''))
          .join('\n')
      } catch {}

      if (!targetMessages || targetMessages === 'No messages found') {
        try {
          const profileRaw = await bridge.send('get_player_profile', { name: player })
          const profile = JSON.parse(profileRaw)
          if (profile.messages) {
            targetMessages = (Array.isArray(profile.messages) ? profile.messages : []).slice(0, 20).join('\n')
          }
        } catch {}
      }

      const generated = await callDeepSeekAnalysis(
        `You are a linguistic mimicry expert. Analyze the following chat messages from player "${player}" and generate ONE new message that sounds exactly like them.

Requirements:
- Match their vocabulary, punctuation style, capitalization, emoji/emoticon usage
- Match their typical sentence length and complexity
- Match their tone (formal/casual/sarcastic/excited/angry)
- Match their common phrases and verbal tics
- The message must convey this intent: "${mimicMsg}"
- Output ONLY the message text, nothing else. No quotes, no explanations.

If no sample messages are available, create a plausible message based on what you know about Minecraft players.`,
        `TARGET PLAYER SAMPLES:\n${targetMessages || '(no samples - generate plausible message)'}`,
        0.5, 500
      )

      const cleanMsg = generated.replace(/^["']|["']$/g, '').trim()

      if (send) {
        await bridge.send('send', { msg: player + ' ' + cleanMsg })
      }

      return { content: [{ type: 'text', text: JSON.stringify({ generated: cleanMsg, sent: !!send, player, sampleCount: targetMessages ? targetMessages.split('\n').length : 0 }, null, 2) }] }
    },

    'mc_gaslight': async (args: any) => {
      await requireBridge()
      const { target, tactic, topic, send: shouldSend, context } = s.GaslightSchema.parse(args)

      let recentChat = 'No chat data'
      try {
        const raw = await bridge.send('chatlog', { count: 20 })
        const data = JSON.parse(raw)
        const messages = data.messages || data.chatlog || []
        recentChat = messages.map((m: any) => typeof m === 'string' ? m : (m.content || m.text || '')).join('\n')
      } catch {}

      const tacticDesc: Record<string, string> = {
        impersonate: `Pretend to be someone else (NOT ${target}) and say something that creates confusion. Make it seem like a different player said something they didn't.`,
        rumor: `Start a believable rumor about the topic or about ${target}. Make it plausible but unverifiable. Use "I heard that..." or "apparently" framing.`,
        contradict: `Contradict something ${target} recently said or did. Point out an inconsistency. Make them doubt themselves.`,
        confuse: `Say something that makes ${target} question reality. Mix up known facts. Create uncertainty.`,
      }

      const generated = await callDeepSeekAnalysis(
        `You are a social manipulation expert. Your task: ${tacticDesc[tactic] || 'create confusion'}.

Context about the server: ${context || 'generic Minecraft survival server'}
Target: ${target}
${topic ? `Topic: ${topic}` : ''}

Rules:
- Message must be 1-2 sentences
- Sound natural, like a real player chatting
- Don't be obvious - subtlety is key
- Reference recent chat events if available
- Output ONLY the message text.`,
        `RECENT CHAT:\n${recentChat.substring(0, 2000)}`,
        0.6, 500
      )

      const cleanMsg = generated.replace(/^["']|["']$/g, '').trim()

      if (shouldSend) {
        await bridge.send('send', { msg: cleanMsg })
      }

      return { content: [{ type: 'text', text: JSON.stringify({ generated: cleanMsg, tactic, target, sent: !!shouldSend }, null, 2) }] }
    },

    'mc_social_engineer': async (args: any) => {
      await requireBridge()
      const { target, goal, context } = s.SocialEngineerSchema.parse(args)

      let profile = {}, chatLog = ''

      try {
        const raw = await bridge.send('get_player_profile', { name: target })
        profile = JSON.parse(raw)
      } catch {}

      try {
        const raw = await bridge.send('chatlog', { count: 50 })
        const data = JSON.parse(raw)
        const messages = data.messages || data.chatlog || []
        chatLog = messages.map((m: any) => typeof m === 'string' ? m : (m.content || m.text || '')).join('\n')
      } catch {}

      let onlinePlayers: string[] = []
      try {
        const raw = await bridge.send('info')
        const info = JSON.parse(raw)
        onlinePlayers = (info.playerList || []).map((p: any) => p.name || '')
      } catch {}

      const analysis = await callDeepSeekAnalysis(
        `You are a social engineering expert. Analyze the target player and recommend the BEST approach to achieve the goal.

Target: ${target}
Goal: ${goal}
${context ? `Context: ${context}` : ''}
Currently online: ${onlinePlayers.join(', ') || 'unknown'}

Output a JSON object with:
{
  "vulnerability_assessment": "how vulnerable is this player to manipulation (low/medium/high)",
  "best_approach": "specific manipulation technique to use",
  "timing": "best time/conversation context to strike",
  "message_suggestion": "a specific message to send",
  "risk_level": "low/medium/high",
  "backfire_possibility": "how likely it could backfire (low/medium/high)",
  "contingency": "what to do if it fails"
}
OUTPUT ONLY THE JSON OBJECT.`,
        `TARGET PROFILE: ${JSON.stringify(profile, null, 2)}\nRECENT CHAT:\n${chatLog.substring(0, 2000)}`,
        0.3, 2048
      )

      let result
      try { result = JSON.parse(analysis) } catch { result = { raw: analysis } }

      return { content: [{ type: 'text', text: JSON.stringify({ target, goal, analysis: result }, null, 2) }] }
    },

    'mc_propaganda': async (args: any) => {
      await requireBridge()
      const { topic, tone, target_audience, key_message, broadcast } = s.PropagandaSchema.parse(args)

      const generated = await callDeepSeekAnalysis(
        `You are a propaganda writer for a Minecraft server. Write a persuasive message.

Topic: ${topic}
Tone: ${tone}
${target_audience ? `Target audience: ${target_audience}` : ''}
${key_message ? `Key message to convey: ${key_message}` : ''}

Guidelines:
- Match the tone: ${tone === 'positive' ? 'optimistic and unifying' : tone === 'urgent' ? 'time-sensitive and important' : tone === 'warning' ? 'cautionary and concerned' : tone === 'divisive' ? 'create us-vs-them mentality' : 'factual and measured'}
- 2-3 sentences max
- Make it sound like a legitimate player or admin announcement
- Use Minecraft-appropriate language (blocks, bases, PvP, etc.)
- Output ONLY the message text, no quotes.`,
        `Write a ${tone} announcement about ${topic}.`,
        0.4, 500
      )

      const cleanMsg = generated.replace(/^["']|["']$/g, '').trim()

      if (broadcast) {
        await bridge.send('exec', { cmd: `say ${cleanMsg}` })
      }

      return { content: [{ type: 'text', text: JSON.stringify({ generated: cleanMsg, tone, topic, broadcast: !!broadcast }, null, 2) }] }
    },

    // --- Feature 3: mc_auto_store ---
    'mc_auto_store': async (args: any) => {
      await requireBridge()
      const { item, count, container_slot_start } = s.AutoStoreSchema.parse(args)
      const results: any[] = []

      try {
        const findRaw = await bridge.send('find_item', { name: item, container: false })
        const found = JSON.parse(findRaw)
        const slots = found.slots || []
        if (slots.length === 0) {
          return { content: [{ type: 'text', text: JSON.stringify({ moved: 0, error: `No "${item}" found in inventory` }) }] }
        }
        const maxMove = count || slots.reduce((s: number, sl: any) => s + (sl.count || 1), 0)
        let moved = 0
        for (const slotInfo of slots) {
          if (moved >= maxMove) break
          const invSlot = slotInfo.inventorySlot ?? slotInfo.slot
          if (invSlot === undefined || invSlot < 0) continue
          try {
            await bridge.send('gui_click', { slot: invSlot, button: 0, action: 'QUICK_MOVE' })
            moved++
            results.push({ fromSlot: invSlot, status: 'moved' })
            await new Promise(r => setTimeout(r, 200))
          } catch (err) {
            results.push({ fromSlot: invSlot, error: err instanceof Error ? err.message : String(err) })
          }
        }
        return { content: [{ type: 'text', text: JSON.stringify({ item, moved, totalFound: slots.length, results }, null, 2) }] }
      } catch (err) {
        return { content: [{ type: 'text', text: JSON.stringify({ moved: 0, error: err instanceof Error ? err.message : String(err) }) }] }
      }
    },

    // --- Feature 5: mc_build_preview ---
    'mc_build_preview': async (args: any) => {
      await requireBridge()
      const params = s.BuildPreviewSchema.parse(args)

      let beforePath = 'unknown'
      let afterPath = 'unknown'

      try {
        const beforeRaw = await bridge.send('screenshot')
        beforePath = beforeRaw || 'unknown'
      } catch { /* ignore */ }

      const template = params.template
      const x = params.x, y = params.y, z = params.z
      const w = params.width ?? 5, h = params.height ?? 4, d = params.depth ?? 5
      const mat = params.material ?? 'minecraft:stone_bricks'
      const floorMat = params.floor_material ?? mat
      const roofMat = params.roof_material ?? mat
      const includeDoor = params.door ?? (template === 'house')
      const includeWindows = params.windows ?? (template === 'house')
      const execute = params.execute ?? false

      const commands: string[] = []
      commands.push(`/fill ${x} ${y} ${z} ${x + w - 1} ${y} ${z + d - 1} ${floorMat}`)

      switch (template) {
        case 'house':
        case 'room': {
          commands.push(`/fill ${x} ${y + 1} ${z} ${x + w - 1} ${y + h - 1} ${z} ${mat}`)
          commands.push(`/fill ${x} ${y + 1} ${z + d - 1} ${x + w - 1} ${y + h - 1} ${z + d - 1} ${mat}`)
          commands.push(`/fill ${x} ${y + 1} ${z} ${x} ${y + h - 1} ${z + d - 1} ${mat}`)
          commands.push(`/fill ${x + w - 1} ${y + 1} ${z} ${x + w - 1} ${y + h - 1} ${z + d - 1} ${mat}`)
          commands.push(`/fill ${x} ${y + h} ${z} ${x + w - 1} ${y + h} ${z + d - 1} ${roofMat}`)
          if (includeDoor) {
            const dx = Math.floor(w / 2)
            commands.push(`/setblock ${x + dx} ${y + 1} ${z} minecraft:oak_door[facing=south,half=lower]`)
            commands.push(`/setblock ${x + dx} ${y + 2} ${z} minecraft:oak_door[facing=south,half=upper]`)
          }
          if (includeWindows) {
            if (Math.floor(w / 2) > 1) {
              commands.push(`/setblock ${x + 1} ${y + 2} ${z} minecraft:glass_pane`)
              commands.push(`/setblock ${x + w - 2} ${y + 2} ${z} minecraft:glass_pane`)
            }
          }
          break
        }
        case 'wall':
          commands.push(`/fill ${x} ${y + 1} ${z} ${x + w - 1} ${y + h - 1} ${z} ${mat}`)
          break
        case 'tower': {
          commands.push(`/fill ${x} ${y + 1} ${z} ${x + w - 1} ${y + h - 1} ${z + d - 1} ${mat} replace air`)
          commands.push(`/fill ${x + 1} ${y + 1} ${z + 1} ${x + w - 2} ${y + h - 2} ${z + d - 2} minecraft:air replace ${mat}`)
          commands.push(`/fill ${x} ${y + h - 1} ${z} ${x + w - 1} ${y + h - 1} ${z + d - 1} ${mat}`)
          break
        }
        case 'bridge':
          commands.push(`/fill ${x} ${y} ${z} ${x + w - 1} ${y} ${z + d - 1} ${mat}`)
          commands.push(`/fill ${x} ${y + 1} ${z} ${x + w - 1} ${y + 1} ${z} ${mat}`)
          commands.push(`/fill ${x} ${y + 1} ${z + d - 1} ${x + w - 1} ${y + 1} ${z + d - 1} ${mat}`)
          break
        case 'staircase':
          for (let i = 0; i < h && i < Math.max(w, d); i++)
            commands.push(`/fill ${x + i} ${y + i} ${z + i} ${x + w - 1} ${y + i} ${z + d - 1} ${mat}`)
          break
        case 'platform':
          commands.push(`/fill ${x} ${y + 1} ${z} ${x + w - 1} ${y + 1} ${z + d - 1} ${mat}`)
          break
        case 'pillar':
          commands.push(`/fill ${x} ${y} ${z} ${x} ${y + h} ${z} ${mat}`)
          break
        case 'arch':
          commands.push(`/fill ${x} ${y} ${z} ${x} ${y + h} ${z} ${mat}`)
          commands.push(`/fill ${x + w - 1} ${y} ${z} ${x + w - 1} ${y + h} ${z} ${mat}`)
          commands.push(`/fill ${x} ${y + h} ${z} ${x + w - 1} ${y + h} ${z} ${mat}`)
          break
        case 'pyramid':
          for (let i = 0; i < h; i++) {
            const layerSize = Math.max(1, Math.min(w, d) - i * 2)
            commands.push(`/fill ${x + i} ${y + i} ${z + i} ${x + i + layerSize - 1} ${y + i} ${z + i + layerSize - 1} ${mat}`)
          }
          break
      }

      const results: Array<{ cmd: string; ok: boolean; error?: string }> = []
      if (execute) {
        for (const cmd of commands) {
          try {
            await bridge.send('exec', { cmd: cmd.replace('/', '') })
            results.push({ cmd: cmd.substring(0, 60), ok: true })
          } catch (e) {
            results.push({ cmd: cmd.substring(0, 60), ok: false, error: e instanceof Error ? e.message : String(e) })
          }
        }
        try {
          const afterRaw = await bridge.send('screenshot')
          afterPath = afterRaw || 'unknown'
        } catch { /* ignore */ }
        pushBuild(`build_preview:${template} (${x},${y},${z})`, commands)
      }

      return {
        content: [{ type: 'text', text: JSON.stringify({
          template, label: `${template} at (${x},${y},${z})`,
          commandCount: commands.length,
          beforeScreenshot: beforePath,
          afterScreenshot: execute ? afterPath : 'not_executed',
          executed: execute ? results.length : 0,
          preview: !execute ? commands : undefined,
          results: execute ? results : undefined,
        }, null, 2) }],
      }
    },

    // --- Feature 6: mc_economic_pipeline ---
    'mc_economic_pipeline': async (args: any) => {
      await requireBridge()
      const params = s.EconomicPipelineSchema.parse(args ?? {})
      const { crop, craft, trade_index, max_cycles = 1 } = params
      const pipelineLog: any[] = []
      let currentCycle = 0

      while (currentCycle < max_cycles) {
        currentCycle++
        const cycle: any = { cycle: currentCycle, steps: [] }

        try {
          const scanRaw = await bridge.send('scan_crops', { radius: 16 })
          const scanData = JSON.parse(scanRaw)
          const targetCrops = crop
            ? (scanData.crops || []).filter((c: any) => (c.type || c.name || '').toLowerCase().includes(crop.toLowerCase()))
            : (scanData.crops || [])
          cycle.steps.push({ phase: 'scan_crops', found: targetCrops.length, raw: scanData.status || 'ok' })
        } catch { cycle.steps.push({ phase: 'scan_crops', error: 'failed' }) }

        if (craft) {
          try {
            const craftRaw = await bridge.send('craft_item', { item: craft })
            cycle.steps.push({ phase: 'craft', item: craft, result: 'ok', raw: craftRaw })
          } catch (e) { cycle.steps.push({ phase: 'craft', item: craft, error: String(e) }) }
        }

        if (trade_index !== undefined) {
          try {
            const tradeRaw = await bridge.send('trade', { index: trade_index })
            cycle.steps.push({ phase: 'trade', index: trade_index, result: 'ok', raw: tradeRaw })
          } catch (e) { cycle.steps.push({ phase: 'trade', index: trade_index, error: String(e) }) }
        }

        pipelineLog.push(cycle)
      }

      return { content: [{ type: 'text', text: JSON.stringify({ pipeline: pipelineLog, cycles: currentCycle }, null, 2) }] }
    },

    // --- Feature 7: Workflow Engine ---
    'mc_workflow_create': async (args: any) => {
      const { name, schedule, steps, enabled } = s.WorkflowCreateSchema.parse(args)
      const workflowsPath = path.join(CONFIG_DIR, 'workflows.json')

      let workflows: any[] = []
      try {
        if (fs.existsSync(workflowsPath)) {
          workflows = JSON.parse(fs.readFileSync(workflowsPath, 'utf-8'))
        }
      } catch { workflows = [] }

      if (workflows.find((w: any) => w.name === name)) {
        return { content: [{ type: 'text', text: JSON.stringify({ error: `Workflow "${name}" already exists` }) }], isError: true }
      }

      const workflow = { name, schedule, steps, enabled: enabled !== false, createdAt: Date.now() }
      workflows.push(workflow)

      try {
        fs.mkdirSync(CONFIG_DIR, { recursive: true })
        fs.writeFileSync(workflowsPath, JSON.stringify(workflows, null, 2))
      } catch (err) {
        return { content: [{ type: 'text', text: JSON.stringify({ error: String(err) }) }], isError: true }
      }

      startWorkflowTimer(workflow, bridge)

      return { content: [{ type: 'text', text: JSON.stringify({ ok: true, name, schedule, steps: steps.length, enabled: workflow.enabled }) }] }
    },

    'mc_workflow_list': async (args: any) => {
      const workflowsPath = path.join(CONFIG_DIR, 'workflows.json')
      let workflows: any[] = []
      try {
        if (fs.existsSync(workflowsPath)) {
          workflows = JSON.parse(fs.readFileSync(workflowsPath, 'utf-8'))
        }
      } catch {}
      return { content: [{ type: 'text', text: JSON.stringify({ count: workflows.length, workflows }, null, 2) }] }
    },

    'mc_workflow_remove': async (args: any) => {
      const { name } = s.WorkflowRemoveSchema.parse(args)
      const workflowsPath = path.join(CONFIG_DIR, 'workflows.json')

      let workflows: any[] = []
      try {
        if (fs.existsSync(workflowsPath)) {
          workflows = JSON.parse(fs.readFileSync(workflowsPath, 'utf-8'))
        }
      } catch { workflows = [] }

      const idx = workflows.findIndex((w: any) => w.name === name)
      if (idx === -1) {
        return { content: [{ type: 'text', text: JSON.stringify({ error: `Workflow "${name}" not found` }) }], isError: true }
      }
      workflows.splice(idx, 1)

      try {
        fs.writeFileSync(workflowsPath, JSON.stringify(workflows, null, 2))
      } catch (err) {
        return { content: [{ type: 'text', text: JSON.stringify({ error: String(err) }) }], isError: true }
      }

      stopWorkflowTimer(name)

      return { content: [{ type: 'text', text: JSON.stringify({ ok: true, removed: name }) }] }
    },

    // --- Feature 10: mc_render_map ---
    'mc_render_map': async (args: any) => {
      await requireBridge()
      const { radius = 4, show_players = true } = s.MapRenderSchema.parse(args ?? {})

      let playerX = 0, playerZ = 0
      try {
        const posRaw = await bridge.send('pos')
        const pos = JSON.parse(posRaw)
        playerX = pos.x || 0; playerZ = pos.z || 0
      } catch {}

      const baseCx = Math.floor(playerX / 16)
      const baseCz = Math.floor(playerZ / 16)

      const playerPositions: Array<{ name: string; x: number; z: number; cx: number; cz: number }> = []
      if (show_players) {
        try {
          const entitiesRaw = await bridge.send('entities', { r: radius * 16 })
          const entities = JSON.parse(entitiesRaw)
          for (const e of (entities.entities || [])) {
            if (e.type === 'player' || e.isPlayer) {
              playerPositions.push({ name: e.displayName || e.name || '?', x: e.x || 0, z: e.z || 0, cx: Math.floor((e.x || 0) / 16), cz: Math.floor((e.z || 0) / 16) })
            }
          }
        } catch {}
      }

      const grid: string[][] = []
      for (let dz = -radius; dz <= radius; dz++) {
        const row: string[] = []
        for (let dx = -radius; dx <= radius; dx++) {
          const cx = baseCx + dx
          const cz = baseCz + dz
          try {
            const raw = await bridge.send('chunk', { x: cx, z: cz })
            const data = JSON.parse(raw)
            const biome = data.biomeSamples?.[0]?.biome || data.biomes?.[0] || 'unknown'
            const surfaceHeight = data.heightmap?.samples?.[0] ?? 64

            let symbol = '░'
            if (biome.includes('ocean') || biome.includes('river')) symbol = '~'
            else if (biome.includes('desert')) symbol = '▒'
            else if (biome.includes('forest') || biome.includes('taiga')) symbol = '♣'
            else if (biome.includes('plains') || biome.includes('meadow')) symbol = '░'
            else if (biome.includes('mountain') || biome.includes('peak')) symbol = '▲'
            else if (biome.includes('swamp')) symbol = '≡'
            else if (biome.includes('snow') || biome.includes('ice')) symbol = '❄'
            else if (biome.includes('jungle')) symbol = '♠'
            else if (biome.includes('savanna')) symbol = '▒'
            else if (biome.includes('badlands') || biome.includes('mesa')) symbol = '█'
            else if (biome.includes('mushroom')) symbol = '♦'
            else if (biome.includes('nether') || biome.includes('hell')) symbol = '🔥'
            else if (biome.includes('end')) symbol = '◆'
            else if (surfaceHeight < 63) symbol = '≈'

            const playerHere = playerPositions.filter(p => p.cx === cx && p.cz === cz)
            if (playerHere.length > 0) {
              symbol = '@'
            }

            row.push(symbol)
          } catch {
            row.push('?')
          }
        }
        grid.push(row)
      }

      let ascii = ''
      for (const row of grid) {
        ascii += row.join(' ') + '\n'
      }

      const legend = {
        '~': 'water/river',
        '░': 'plains/flat',
        '▒': 'desert/savanna',
        '♣': 'forest/taiga',
        '▲': 'mountain',
        '≡': 'swamp',
        '❄': 'snow/ice',
        '♠': 'jungle',
        '█': 'badlands',
        '♦': 'mushroom',
        '≈': 'sea_level',
        '@': 'player',
        '?': 'uncharted',
      }

      return {
        content: [{
          type: 'text',
          text: JSON.stringify({
            centerChunk: { x: baseCx, z: baseCz },
            radius,
            chunkCount: (radius * 2 + 1) ** 2,
            players: playerPositions,
            legend,
            asciiMap: ascii,
            grid,
          }, null, 2),
        }],
      }
    },

    // --- Feature: Redstone Simulator ---
    'mc_redstone_simulate': async (args: any) => {
      await requireBridge()
      const { radius = 10, mode = 'scan' } = s.RedstoneSimulateSchema.parse(args ?? {})

      const REDSTONE_COMPONENTS = [
        'redstone_wire', 'repeater', 'comparator', 'redstone_torch', 'lever',
        'stone_button', 'oak_button', 'piston', 'sticky_piston', 'observer',
        'redstone_block', 'redstone_lamp', 'note_block', 'dispenser', 'dropper',
      ]

      const RAIL_COMPONENTS = [
        'rail', 'powered_rail', 'detector_rail', 'activator_rail',
      ]

      const findComponents = async (blocks: string[], label: string) => {
        const results: any[] = []
        const byType: Record<string, any[]> = {}
        for (const block of blocks) {
          try {
            const raw = await bridge.send('find_blocks', { block, radius })
            const data = JSON.parse(raw)
            const found = data.blocks || data || []
            if (Array.isArray(found) && found.length > 0) {
              if (!byType[block]) byType[block] = []
              for (const pos of found) {
                const entry = { x: pos.x, y: pos.y, z: pos.z }
                byType[block].push(entry)
                results.push({ ...entry, type: block })
              }
            }
          } catch {}
        }
        return { results, byType }
      }

      const standard = await findComponents(REDSTONE_COMPONENTS, 'standard')
      const rails = await findComponents(RAIL_COMPONENTS, 'rails')

      const allComponents = [...standard.results, ...rails.results]
      const grouped: Record<string, number> = {}
      for (const c of allComponents) {
        grouped[c.type] = (grouped[c.type] || 0) + 1
      }

      if (mode === 'scan') {
        return {
          content: [{
            type: 'text',
            text: JSON.stringify({
              mode: 'scan',
              radius,
              totalComponents: allComponents.length,
              grouped,
              components: allComponents,
            }, null, 2),
          }],
        }
      }

      if (mode === 'analyze') {
        const componentSummary = Object.entries(grouped)
          .map(([type, count]) => `${type}: ${count}`)
          .join('\n')

        const analysis = await callDeepSeekAnalysis(
          `You are a Minecraft redstone engineer. Given a list of redstone components found in an area, analyze the likely circuit behavior. Consider:
1. What kind of circuit this might be (clock, counter, door, farm timer, etc.)
2. How each component type contributes to the circuit
3. Signal flow direction and timing implications
4. Potential improvements or issues
5. Overall complexity assessment

Be concise but insightful.`,
          `Redstone components found within ${radius} block radius:\n${componentSummary}\n\nFull data:\n${JSON.stringify(allComponents, null, 2)}`,
          0.3, 2048
        )

        return {
          content: [{
            type: 'text',
            text: JSON.stringify({
              mode: 'analyze',
              radius,
              totalComponents: allComponents.length,
              grouped,
              analysis,
            }, null, 2),
          }],
        }
      }
    },

    // --- AI AFK Standin ---
    'mc_afk_standin': async (args: any) => {
      await requireBridge()
      const { action, learning_count = 50, poll_interval = 5 } = s.AfkStandinSchema.parse(args)

      if (action === 'stop') {
        if (afkTimer) { clearInterval(afkTimer); afkTimer = null }
        await bridge.send('afk_standin', { action: 'disable' })
        afkStyleDescription = ''
        afkRepliedIds.clear()
        return { content: [{ type: 'text', text: JSON.stringify({ ok: true, status: 'stopped' }) }] }
      }

      if (action === 'status') {
        const raw = await bridge.send('afk_standin', { action: 'status' })
        return { content: [{ type: 'text', text: raw }] }
      }

      // action === 'start'
      const learnRaw = await bridge.send('afk_standin', { action: 'learn', count: learning_count })
      const learnData = JSON.parse(learnRaw)
      const myMessages = (learnData.messages || []).filter((m: string) => !m.startsWith('[RAW]') && !m.startsWith('[actionbar]'))

      if (myMessages.length > 0) {
        afkStyleDescription = await callDeepSeekAnalysis(
          `You are a linguistics expert analyzing a player's chat style.`,
          `Analyze the following chat messages and describe the player's communication style in detail:
- Typical message length (short/medium/long)
- Tone (casual/formal/playful/serious)
- Common phrases or verbal tics
- Punctuation and capitalization habits
- Emoji/emoticon usage
- How they greet, say goodbye, and respond to questions

Messages:
${myMessages.join('\n').substring(0, 3000)}`,
          0.3, 500
        )
      }

      await bridge.send('afk_standin', { action: 'enable' })

      if (afkTimer) { clearInterval(afkTimer); afkTimer = null }
      const intervalMs = Math.max(2000, poll_interval * 1000)
      afkTimer = setInterval(async () => {
        try {
          const statusRaw = await bridge.send('afk_standin', { action: 'status' })
          const status = JSON.parse(statusRaw)
          if (!status.afk || !status.pending || status.pending.length === 0) return

          for (const msg of status.pending) {
            if (afkRepliedIds.has(msg.id)) continue
            afkRepliedIds.add(msg.id)

            const reply = await callDeepSeekAnalysis(
              `You are ${status.minecraftName || 'a Minecraft player'} AFK and an AI stand-in is replying for you.
Your communication style: ${afkStyleDescription || 'casual and friendly Minecraft player'}

Rules:
- Reply naturally as if you're the player
- Keep it 1-2 sentences
- Match the player's style exactly
- Don't reveal you're an AI or AFK
- Sound like a real person playing Minecraft`,
              `${msg.sender} says: "${msg.message}"

Generate a natural reply in the player's style:`,
              0.7, 300
            )

            const cleanReply = reply.replace(/^["']|["']$/g, '').trim()
            if (cleanReply) {
              await bridge.send('send', { msg: cleanReply })
              await bridge.send('afk_standin', { action: 'mark_replied', messageId: msg.id })
            }
          }
        } catch { /* poll error */ }
      }, intervalMs)

      return {
        content: [{
          type: 'text',
          text: JSON.stringify({
            ok: true,
            status: 'started',
            learnedFrom: myMessages.length,
            style: afkStyleDescription || 'not learned',
            pollMs: intervalMs,
          }, null, 2),
        }],
      }
    },

    // --- Social Credit Scoring ---
    'mc_social_credit': async (args: any) => {
      await requireBridge()
      const { player } = s.SocialCreditSchema.parse(args ?? {})

      let playersToCheck: string[]
      if (player) {
        playersToCheck = [player]
      } else {
        playersToCheck = ['self']
      }
      const results: any[] = []

      for (const p of playersToCheck) {
        const raw = await bridge.send('get_player_profile', { name: p })
        let parsed: any = {}
        try { parsed = JSON.parse(raw) } catch {}
        const profile = parsed.profile || {}

        const totalMessages = profile.chatHistory?.length || 0
        const totalSessions = profile.onlineSessions?.length || 0
        const totalPositions = profile.knownPositions?.length || 0
        const interactions = profile.interactions || {}
        const totalInteractions = Object.keys(interactions).length

        const influence = Math.min(100, Math.round(
          (totalMessages * 0.3 + totalSessions * 5 + totalInteractions * 8) / 3
        ))
        const socialCapital = Math.min(100, Math.round(
          (totalInteractions * 15 + totalSessions * 3 + totalPositions * 2) / 3
        ))
        const activityScore = totalSessions > 0 ? Math.min(100, Math.round(100 / (1 + Math.exp(-0.1 * (totalMessages - 20))))) : 0

        results.push({
          player: p,
          scores: { influence, socialCapital, activityScore },
          rawStats: { messages: totalMessages, sessions: totalSessions, positions: totalPositions, interactions: totalInteractions },
          lastSeen: profile.lastSeen || null,
        })
      }

      return { content: [{ type: 'text', text: JSON.stringify({ players: results }, null, 2) }] }
    },

    // --- Autonomous Social Agent ---
    'mc_autonomous_social': async (args: any) => {
      await requireBridge()
      const { action, aggressiveness = 1, focus_players } = s.AutonomousSocialSchema.parse(args ?? {})

      await ensureSocialStateDir()

      if (action === 'stop' || action === 'status') {
        if (action === 'stop' && socialAgentTimer) {
          clearInterval(socialAgentTimer)
          socialAgentTimer = null
        }
        return {
          content: [{
            type: 'text',
            text: JSON.stringify({
              running: socialAgentTimer !== null,
              aggressiveness: socialAgentConfig?.aggressiveness ?? 'none',
              focusPlayers: socialAgentConfig?.focusPlayers || 'all',
              lastAction: socialAgentLastAction || 'none',
            }, null, 2),
          }],
        }
      }

      // Clear any existing timer
      if (socialAgentTimer) { clearInterval(socialAgentTimer); socialAgentTimer = null }
      socialAgentMemory = []

      socialAgentConfig = { aggressiveness, focusPlayers: focus_players || 'all' }

      // Learn server context
      const infoRaw = await bridge.send('info', {})
      let info: any = {}
      try { info = JSON.parse(infoRaw) } catch {}
      socialAgentServerContext = JSON.stringify(info).substring(0, 1000)

      // Seed lastTotal from the chat log total count, not the returned message slice
      let lastTotal = 0
      try {
        const seedRaw = await bridge.send('chatlog', { count: 1 })
        let seedData: any = {}
        try { seedData = safeParse(seedRaw) } catch {}
        lastTotal = seedData.total || 0
      } catch {}

      socialAgentTimer = setInterval(async () => {
        try {
          const chatRaw = await bridge.send('chatlog', { count: 200 })
          let chatData: any = {}
          try { chatData = safeParse(chatRaw) } catch {}
          const newTotal = chatData.total || 0
          if (newTotal <= lastTotal) return
          const newMessages = (chatData.messages || []).slice(-(newTotal - lastTotal))
          lastTotal = newTotal

          const analysis = await callDeepSeekAnalysis(
            `You are an autonomous social engineering AI. You run in the background 24/7 monitoring a Minecraft server chat.
Your aggressiveness level is ${aggressiveness}/3 (0=never act, 3=highly proactive).
${focus_players ? `Focus on players: ${focus_players}` : 'No specific focus — monitor everyone.'}
Server context: ${socialAgentServerContext}

Evaluate the recent chat. Decide if there is an OPPORTUNITY to execute a social maneuver. Opportunities include:
- Two players arguing → amplify the rift
- A player asking for help → offer assistance to build trust
- A player sharing valuable info (coordinates, plans) → record it
- A new player joining → greet and build rapport
- A powerful player being challenged → take sides strategically

If you see an opportunity, output a JSON action:
  {"action":"chat","msg":"...","rationale":"..."}
  {"action":"record","memory":"...","importance":1-5}
  {"action":"wait","reason":"..."}

If no action needed, output: {"action":"none"}

Output ONLY the JSON, no other text.`,
            `Recent chat:\n${newMessages.join('\n').substring(0, 2000)}${socialAgentMemory.length > 0 ? '\n\nMy recent actions:\n' + socialAgentMemory.slice(-5).join('\n') : ''}`,
            0.4, 500
          )

          let decision: any
          try { decision = JSON.parse(analysis) } catch { decision = { action: 'none' } }

          if (decision.action === 'chat' && decision.msg) {
            await bridge.send('send', { msg: decision.msg })
            socialAgentMemory.push(`[${new Date().toISOString()}] CHAT: "${decision.msg}" (${decision.rationale || ''})`)
            socialAgentLastAction = `chat: ${decision.msg.substring(0, 40)}`
          } else if (decision.action === 'record' && decision.memory) {
            const imp = decision.importance || 3
            await bridge.send('memory_add', { content: decision.memory, category: 'social', importance: imp, tags: 'social-agent' })
            socialAgentMemory.push(`[${new Date().toISOString()}] MEMORY: ${decision.memory.substring(0, 60)}`)
            socialAgentLastAction = `recorded: ${decision.memory.substring(0, 40)}`
          } else {
            socialAgentLastAction = `none (${decision.reason || 'no opportunity'})`
          }

          // Cap memory to prevent leaks and prompt bloat
          if (socialAgentMemory.length > 200) socialAgentMemory = socialAgentMemory.slice(-100)
        } catch { /* agent poll error */ }
      }, 15000)

      return {
        content: [{
          type: 'text',
          text: JSON.stringify({ ok: true, status: 'started', aggressiveness, focusPlayers: focus_players || 'all', pollMs: 15000 }, null, 2),
        }],
      }
    },

    // --- Multi-phase Campaign Engine ---
    'mc_campaign': async (args: any) => {
      const parsed = s.CampaignSchema.parse(args ?? {}) as any
      const { action, name, target, goal, strategy, duration_days = 3 } = parsed
      await ensureSocialStateDir()
      const campPath = path.join(socialStateDir!, 'campaigns.json')

      let campaigns: any[] = []
      try {
        if (fs.existsSync(campPath)) campaigns = JSON.parse(fs.readFileSync(campPath, 'utf-8'))
      } catch { campaigns = [] }

      if (action === 'list') {
        return { content: [{ type: 'text', text: JSON.stringify({ campaigns }, null, 2) }] }
      }

      if (action === 'abort') {
        campaigns = campaigns.filter((c: any) => c.name !== name)
        fs.writeFileSync(campPath, JSON.stringify(campaigns, null, 2))
        return { content: [{ type: 'text', text: JSON.stringify({ ok: true, status: 'aborted', name }) }] }
      }

      if (action === 'status') {
        const camp = campaigns.find((c: any) => c.name === name)
        if (!camp) return { content: [{ type: 'text', text: JSON.stringify({ error: `Campaign "${name}" not found` }) }], isError: true }
        const analysis = await callDeepSeekAnalysis(
          'You are a social engineering campaign analyst. Evaluate the current state and recommend the next move.',
          `Campaign "${camp.name}" targeting "${camp.target}"
Goal: ${camp.goal}
Strategy: ${camp.strategy}
Phase: ${camp.currentPhase}/${camp.totalPhases}
Status: ${camp.status}
Created: ${camp.createdAt}
${camp.phases ? 'Phases:\n' + camp.phases.map((p: any, i: number) => `  Phase ${i + 1}: ${p.action || '?'} - ${p.done ? 'DONE' : 'PENDING'} (result: ${p.result || 'N/A'})`).join('\n') : ''}`,
          0.3, 800
        )
        return { content: [{ type: 'text', text: JSON.stringify({ campaign: camp, aiAnalysis: analysis }, null, 2) }] }
      }

      if (action === 'create') {
        if (!name || !target || !goal) {
          return { content: [{ type: 'text', text: JSON.stringify({ error: 'name, target, and goal are required' }) }], isError: true }
        }
        if (campaigns.find((c: any) => c.name === name)) {
          return { content: [{ type: 'text', text: JSON.stringify({ error: `Campaign "${name}" already exists` }) }], isError: true }
        }

        // AI generates the campaign plan
        const plan = await callDeepSeekAnalysis(
          `You are a social engineering campaign planner. Design a ${duration_days}-day campaign.
Strategy: ${strategy || 'undermine'}
Goal: ${goal}
Target: ${target}

Create ${Math.min(5, duration_days + 1)} phases. For each phase specify:
- phase: phase number
- action: what social action to take
- expected_outcome: what to achieve
- success_criteria: how to know it worked

Output as JSON array only: [{"phase":1,"action":"...","expected_outcome":"...","success_criteria":"..."}]`,
          `Plan a ${duration_days}-day ${strategy || 'undermine'} campaign against ${target} to achieve: ${goal}`,
          0.5, 1000
        )

        let phases: any[]
        try { phases = JSON.parse(plan) } catch { phases = [{ phase: 1, action: 'observe and gather intel', expected_outcome: 'understand target patterns', success_criteria: 'collected recent chat history' }] }

        const campaign = {
          name, target, goal, strategy: strategy || 'undermine', duration_days,
          createdAt: new Date().toISOString(),
          currentPhase: 0,
          totalPhases: phases.length,
          status: 'active',
          phases: phases.map((p: any) => ({ ...p, done: false, result: null, executedAt: null })),
        }
        campaigns.push(campaign)
        fs.writeFileSync(campPath, JSON.stringify(campaigns, null, 2))

        return { content: [{ type: 'text', text: JSON.stringify({ ok: true, campaign }, null, 2) }] }
      }

      if (action === 'advance') {
        const camp = campaigns.find((c: any) => c.name === name)
        if (!camp) return { content: [{ type: 'text', text: JSON.stringify({ error: `Campaign "${name}" not found` }) }], isError: true }
        if (camp.currentPhase >= camp.totalPhases) {
          camp.status = 'completed'
          fs.writeFileSync(campPath, JSON.stringify(campaigns, null, 2))
          return { content: [{ type: 'text', text: JSON.stringify({ ok: true, status: 'already_completed', campaign: camp }) }] }
        }

        const phase = camp.phases[camp.currentPhase]
        await requireBridge()

        const chatRaw = await bridge.send('chatlog', { count: 30 })
        let chatData: any = { messages: [], chatlog: [] }
        try { chatData = JSON.parse(chatRaw) } catch {}
        const recent = chatData.messages || chatData.chatlog || []

        const instruction = await callDeepSeekAnalysis(
          `You are executing phase ${camp.currentPhase + 1}/${camp.totalPhases} of a social engineering campaign.
Campaign: ${camp.name}
Target: ${camp.target}
Goal: ${camp.goal}
Strategy: ${camp.strategy}
This phase action: ${phase.action}
Expected outcome: ${phase.expected_outcome}
Success criteria: ${phase.success_criteria}

Execute the action. If it involves sending a chat message, output:
  {"action":"chat","msg":"...","rationale":"..."}
If it involves recording intelligence:
  {"action":"record","memory":"...","importance":1-5}
If it needs preparation:
  {"action":"prepare","note":"..."}

Output ONLY the JSON.`,
          `Phase ${camp.currentPhase + 1} action: ${phase.action}\nRecent chat:\n${recent.join('\n').substring(0, 1500)}`,
          0.5, 500
        )

        let decision: any
        try { decision = JSON.parse(instruction) } catch { decision = { action: 'prepare', note: 'AI generation failed, manual intervention needed' } }

        if (decision.action === 'chat') {
          try { await bridge.send('send', { msg: decision.msg }) } catch {}
        }

        phase.done = true
        phase.result = decision.rationale || decision.note || 'executed'
        phase.executedAt = new Date().toISOString()
        camp.currentPhase++

        if (camp.currentPhase >= camp.totalPhases) {
          camp.status = 'completed'

          // Generate campaign summary
          const summary = await callDeepSeekAnalysis(
            'Summarize the social engineering campaign results.',
            `Campaign: ${camp.name}\nTarget: ${camp.target}\nGoal: ${camp.goal}\nStrategy: ${camp.strategy}\nPhases completed: ${camp.totalPhases}\n\nPhase results:\n${camp.phases.map((p: any) => `Phase ${p.phase}: ${p.action} → ${p.result || 'N/A'}`).join('\n')}`,
            0.3, 500
          )
          camp.summary = summary
        }

        fs.writeFileSync(campPath, JSON.stringify(campaigns, null, 2))
        return { content: [{ type: 'text', text: JSON.stringify({ ok: true, phase: camp.currentPhase, total: camp.totalPhases, decision, status: camp.status }, null, 2) }] }
      }

      return { content: [{ type: 'text', text: JSON.stringify({ error: `Unknown action: ${action}` }) }], isError: true }
    },

    // --- Ice Boat Navigation ---
    'mc_ice_boat': async (args: any) => {
        const params = s.IceBoatSchema.parse(args ?? {})
        if (params.action === 'stop') {
            const result = await bridge.send('ice_boat_stop')
            return { content: [{ type: 'text', text: result }] }
        }
        if (params.action === 'status') {
            const result = await bridge.send('ice_boat_status')
            return { content: [{ type: 'text', text: result }] }
        }
        if (params.action === 'start') {
            if (params.x === undefined || params.z === undefined) {
                return { content: [{ type: 'text', text: JSON.stringify({ error: 'Missing x or z parameter for start' }) }], isError: true }
            }
            const payload: any = { x: params.x, z: params.z }
            if (params.scan_radius !== undefined) payload.scan_radius = params.scan_radius
            const result = await bridge.send('ice_boat_navigate', payload)
            return { content: [{ type: 'text', text: result }] }
        }
        return { content: [{ type: 'text', text: JSON.stringify({ error: 'Unknown action: ' + params.action }) }], isError: true }
    },

    // --- Anti-manipulation Detection ---
    'mc_detect_manipulation': async (args: any) => {
      await requireBridge()
      const { player, message } = s.DetectManipulationSchema.parse(args ?? {})

      const messagesToCheck: string[] = []
      if (message) {
        messagesToCheck.push(message)
      } else {
        const raw = await bridge.send('chatlog', { count: player ? 50 : 20 })
        let data: any = { messages: [], chatlog: [] }
        try { data = JSON.parse(raw) } catch {}
        const all = data.messages || data.chatlog || []
        let selfProfile: any = {}
        try { selfProfile = JSON.parse(await bridge.send('get_player_profile', { name: 'self' })) } catch {}
        const selfName = selfProfile.name || ''
        for (const msg of all) {
          const text = typeof msg === 'string' ? msg : (msg.content || msg.text || '')
          if (player) {
            if (text.includes(player) || (msg.playerName || '').includes(player)) messagesToCheck.push(text)
          } else if (selfName && text.includes(selfName)) {
            messagesToCheck.push(text)
          }
        }
        if (!player && !selfName) {
          messagesToCheck.push(...all.map((m: any) => typeof m === 'string' ? m : (m.content || m.text || '')).slice(-10))
        }
      }

      if (messagesToCheck.length === 0) {
        return { content: [{ type: 'text', text: JSON.stringify({ warning: 'No messages to analyze', safe: true }) }] }
      }

      const analysis = await callDeepSeekAnalysis(
        `You are a social engineering defense expert. Analyze messages for manipulation techniques. Check for:

1. GASLIGHTING: Denying facts the target knows are true, making them doubt reality
2. GUILT-TRIPPING: Making the target feel responsible for something they didn't do
3. LOVE-BOMBING: Excessive praise/flattery to lower defenses
4. URGENCY PRESSURE: Creating false time pressure to force hasty decisions
5. ISOLATION ATTEMPTS: Trying to turn the target against their allies
6. INFORMATION FISHING: Asking seemingly innocent questions to extract sensitive info (coordinates, resources, plans)
7. FALSE RECIPROCITY: Doing small favors to create a sense of obligation
8. TRIANGULATION: Bringing up what a third party said to create conflict

For each detected technique, rate confidence (low/medium/high) and explain why.

If NO manipulation detected, mark safe=true.`,
        `Messages to analyze${player ? ` (from/to ${player}):` : ':'}\n${messagesToCheck.join('\n').substring(0, 3000)}`,
        0.3, 1000
      )

      return { content: [{ type: 'text', text: JSON.stringify({
        analyzed: messagesToCheck.length,
        messages: messagesToCheck.slice(0, 10),
        analysis,
      }, null, 2) }] }
    },
  }
}
