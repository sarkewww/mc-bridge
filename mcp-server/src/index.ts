import { Server } from '@modelcontextprotocol/sdk/server/index.js'
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js'
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from '@modelcontextprotocol/sdk/types.js'
import * as fs from 'fs'
import * as path from 'path'
import * as os from 'os'
import { execSync } from 'child_process'
import * as s from './schemas.js'
import { toolDefinitions } from './tools.js'
import { createHandlers } from './handlers.js'

// --- Minecraft source analysis config ---
const MINECRAFT_MERGED_JAR = path.join(
  process.env.HOME || process.env.USERPROFILE || 'C:\\Users\\seakura',
  '.gradle', 'caches', 'fabric-loom', '1.21.1', 'minecraft-merged.jar'
)

const MAPPINGS_TINY = path.join(
  process.env.HOME || process.env.USERPROFILE || 'C:\\Users\\seakura',
  '.gradle', 'caches', 'fabric-loom', '1.21.1',
  'net.fabricmc.yarn.1_21_1.1.21.1+build.3-v2', 'mappings.tiny'
)

const CONFIG_DIR = path.join(process.env.HOME || process.env.USERPROFILE || 'C:\\Users\\seakura', '.config', 'mc-mcp-server')
const KEY_FILE = path.join(CONFIG_DIR, 'deepseek-key.txt')

function loadSavedKey(): string {
  try {
    if (fs.existsSync(KEY_FILE)) {
      return fs.readFileSync(KEY_FILE, 'utf8').trim()
    }
  } catch { /* ignore */ }
  return ''
}

function saveKey(key: string) {
  try {
    if (!fs.existsSync(CONFIG_DIR)) fs.mkdirSync(CONFIG_DIR, { recursive: true })
    fs.writeFileSync(KEY_FILE, key, 'utf8')
  } catch { /* ignore */ }
}

// --- WebSocket bridge client ---

export class McBridgeClient {
  private ws: WebSocket | null = null
  private requestId = 0
  private pending = new Map<string, s.PendingRequest>()
  private onDisconnect?: () => void
  autoReconnect = true
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null
  reconnectAttempts = 0
  private maxReconnectAttempts = 20
  private reconnectDelay = 3000

  isConnected(): boolean {
    return this.ws !== null && this.ws.readyState === WebSocket.OPEN
  }

  setReconnectConfig(delay: number, maxAttempts: number): void {
    this.reconnectDelay = delay
    this.maxReconnectAttempts = maxAttempts
  }

  getReconnectConfig(): { delay: number; maxAttempts: number } {
    return { delay: this.reconnectDelay, maxAttempts: this.maxReconnectAttempts }
  }

  private startReconnect(): void {
    if (!this.autoReconnect || this.reconnectAttempts >= this.maxReconnectAttempts) return
    this.reconnectAttempts++
    console.error(`[mc-mcp] Reconnecting in ${this.reconnectDelay}ms (attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts})...`)
    this.reconnectTimer = setTimeout(() => {
      this.connect().then(
        () => {
          this.reconnectAttempts = 0
          console.error('[mc-mcp] Reconnected successfully')
        },
        (err) => {
          console.error(`[mc-mcp] Reconnect failed: ${err.message}`)
          this.startReconnect()
        }
      )
    }, this.reconnectDelay)
  }

  stopReconnect(): void {
    this.autoReconnect = false
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    this.reconnectAttempts = 0
  }

  connect(): Promise<string> {
    return new Promise((resolve, reject) => {
      if (this.ws) {
        try { this.ws.close(); } catch { /* ignore */ }
      }

      const ws = new WebSocket('ws://127.0.0.1:25575')
      const timeout = setTimeout(() => {
        reject(new Error('Connection to mc-bridge timed out (is Minecraft running with the mod?)'))
      }, 10000)

      ws.onopen = () => {
        clearTimeout(timeout)
        this.ws = ws
        ws.onmessage = (event) => {
          try {
            const msg: s.WsMessage = JSON.parse(event.data.toString())
            const replyId = msg.rid ?? msg.id
            if (replyId && this.pending.has(replyId)) {
              const p = this.pending.get(replyId)!
              this.pending.delete(replyId)
              if (msg.ok) {
                p.resolve(msg.data ?? JSON.stringify(msg))
              } else {
                p.reject(new Error(msg.error ?? 'Unknown error'))
              }
            }
          } catch {
            /* ignore unparseable messages */
          }
        }
        ws.onclose = () => {
          this.ws = null
          const pendingReqs = [...this.pending.values()]
          this.pending.clear()
          for (const p of pendingReqs) {
            p.reject(new Error('Connection lost'))
          }
          this.onDisconnect?.()
          this.startReconnect()
        }
        ws.onerror = () => { /* handled by onclose */ }
        resolve('Connected to mc-bridge on ws://127.0.0.1:25575')
      }

      ws.onerror = () => {
        clearTimeout(timeout)
        reject(new Error('Cannot connect to mc-bridge. Is Minecraft running with the mc-bridge mod installed?'))
      }
    })
  }

  disconnect(): void {
    this.stopReconnect()
    if (this.ws) {
      try { this.ws.close(); } catch { /* ignore */ }
      this.ws = null
    }
  }

  setOnDisconnect(fn: () => void): void {
    this.onDisconnect = fn
  }

  send(type: string, data: Record<string, unknown> = {}): Promise<string> {
    if (!this.isConnected()) {
      throw new Error('Not connected to mc-bridge. Call mc_connect first.')
    }

    const id = String(++this.requestId)
    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        this.pending.delete(id)
        reject(new Error(`Request ${type} timed out`))
      }, 30000)

      this.pending.set(id, {
        resolve: (v) => { clearTimeout(timeout); resolve(v) },
        reject: (e) => { clearTimeout(timeout); reject(e) },
      })

      const payload = JSON.stringify({ rid: id, type, ...data })
      this.ws!.send(payload)
    })
  }
}

const bridge = new McBridgeClient()

const ANALYSIS_CACHE_DIR = path.join(os.homedir(), '.config', 'mc-mcp-server', 'analysis-cache')

// --- DeepSeek API key ---

let deepseekKey: string = loadSavedKey()

async function callDeepSeek(text: string, targetLang: string, sourceLang?: string): Promise<string> {
  if (!deepseekKey) throw new Error('DeepSeek API key not set. Use mc_set_deepseek_key first.')

  const systemPrompt = `You are a translator. Translate the following text to ${targetLang}.${sourceLang ? ` Source language: ${sourceLang}.` : ''} Only output the translated text,Do not answer text, no explanations, no quotes, use half-width symbols.`

  const resp = await fetch('https://api.deepseek.com/v1/chat/completions', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${deepseekKey}`,
    },
    body: JSON.stringify({
      model: 'deepseek-chat',
      messages: [
        { role: 'system', content: systemPrompt },
        { role: 'user', content: text },
      ],
      max_tokens: 1024,
      temperature: 0.3,
    }),
  })

  if (!resp.ok) {
    const errText = await resp.text()
    throw new Error(`DeepSeek API error (${resp.status}): ${errText}`)
  }

  const data = await resp.json() as s.DeepSeekResponse
  return data.choices?.[0]?.message?.content?.trim() ?? ''
}

async function callDeepSeekAnalysis(systemPrompt: string, userContent: string, temperature = 0.7, maxTokens = 2048): Promise<string> {
  if (!deepseekKey) throw new Error('DeepSeek API key not set. Use mc_set_deepseek_key first.')
  const resp = await fetch('https://api.deepseek.com/v1/chat/completions', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${deepseekKey}`,
    },
    body: JSON.stringify({
      model: 'deepseek-chat',
      messages: [
        { role: 'system', content: systemPrompt },
        { role: 'user', content: userContent },
      ],
      max_tokens: maxTokens,
      temperature,
    }),
  })
  if (!resp.ok) {
    const errText = await resp.text()
    throw new Error(`DeepSeek API error (${resp.status}): ${errText}`)
  }
  const data = await resp.json() as s.DeepSeekResponse
  return data.choices?.[0]?.message?.content?.trim() ?? ''
}

// --- Server setup ---

const server = new Server(
  { name: 'mc-mcp-server', version: '2.0.0' },
  { capabilities: { tools: {} } }
)

async function requireBridge(): Promise<void> {
  if (bridge.isConnected()) return
  console.error('[mc-mcp] Not connected, attempting auto-connect...')
  try {
    bridge.autoReconnect = true
    bridge.reconnectAttempts = 0
    await bridge.connect()
    bridge.setOnDisconnect(() => {
      console.error('[mc-mcp] Bridge disconnected, auto-reconnecting...')
    })
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e)
    throw new Error('Not connected to Minecraft. Auto-connect failed: ' + msg + '. Ensure Minecraft is running with mc-bridge mod.')
  }
}

// --- Create handlers ---

const handlers = createHandlers(bridge, callDeepSeek, callDeepSeekAnalysis, requireBridge, {
  getDeepseekKey: () => deepseekKey,
  setDeepseekKey: (key: string) => { deepseekKey = key },
  saveKey,
  ANALYSIS_CACHE_DIR,
  MINECRAFT_MERGED_JAR,
  MAPPINGS_TINY,
})

// --- List tools ---

server.setRequestHandler(ListToolsRequestSchema, async () => ({
  tools: toolDefinitions,
}))

// --- Call tool ---

server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const { name, arguments: args } = request.params

  try {
    const handler = handlers[name as keyof typeof handlers]
    if (!handler) throw new Error(`Unknown tool: ${name}`)
    const result = await handler(args)
    if (!result) {
      return { content: [{ type: 'text', text: 'ok' }] }
    }
    return result as any
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err)
    return {
      content: [{ type: 'text', text: `Error: ${message}` }],
      isError: true,
    } as any
  }
})

// --- Background analysis ---

function startBackgroundAnalysis() {
  setInterval(async () => {
    if (!bridge.isConnected()) return
    try {
      const pendingRaw = await bridge.send('get_pending_analysis')
      const pendingList = JSON.parse(pendingRaw)
      if (!Array.isArray(pendingList) || pendingList.length === 0) return
      console.error(`[mc-mcp] Background analysis: ${pendingList.length} player(s) pending`)

      for (const player of pendingList) {
        try {
          const name = typeof player === 'string' ? player : player.name
          if (!name) continue

          console.error(`[mc-mcp] Analyzing player: ${name}`)
          const profileRaw = await bridge.send('get_player_profile', { name })
          const profileData = JSON.parse(profileRaw)
          const profile = profileData.profile || profileData

          const analysis = await callDeepSeekAnalysis(
            `You are a social psychology analyst in Minecraft. Analyze this player's profile data and provide a comprehensive psychological profile. Cover:
1. Personality traits and tendencies
2. Behavior patterns and habits
3. Communication style
4. Trust level and reliability
5. Potential social influence vectors and vulnerabilities
6. Relationship dynamics with other players
Be specific, evidence-based, and concise.`,
            `Player profile data:\n${JSON.stringify(profile, null, 2)}`,
            0.7, 2048
          )

          const cacheEntry = {
            playerName: name,
            generatedAt: new Date().toISOString(),
            profile: analysis,
          }
          fs.writeFileSync(path.join(ANALYSIS_CACHE_DIR, `${name}.json`), JSON.stringify(cacheEntry, null, 2), 'utf8')

          await bridge.send('mark_analysis_done', { name })
          console.error(`[mc-mcp] Analysis complete: ${name}`)
        } catch (err) {
          console.error(`[mc-mcp] Failed to analyze player: ${err instanceof Error ? err.message : String(err)}`)
        }
      }
    } catch { /* bridge not connected, skip this cycle */ }
  }, 60_000)
}

async function main() {
  try {
    fs.mkdirSync(ANALYSIS_CACHE_DIR, { recursive: true })
  } catch { /* ignore */ }
  console.error(`[mc-mcp] Analysis cache dir: ${ANALYSIS_CACHE_DIR}`)

  startBackgroundAnalysis()

  const transport = new StdioServerTransport()
  await server.connect(transport)
  console.error('[mc-mcp] Server running on stdio')
}

main().catch((err) => {
  console.error('[mc-mcp] Fatal error:', err)
  process.exit(1)
})
