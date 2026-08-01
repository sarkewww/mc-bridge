import { z } from 'zod'

export interface WsMessage {
  rid?: string
  id?: string
  ok: boolean
  data?: string
  error?: string
}

export interface DeepSeekChoice {
  message: { content: string; role?: string }
  finish_reason?: string
}

export interface DeepSeekResponse {
  choices: DeepSeekChoice[]
}

export interface PendingRequest {
  resolve: (v: string) => void
  reject: (e: Error) => void
}

export interface BuildAction {
  type: string
  label: string
  commands: string[]
  timestamp: number
}

export const ConnectSchema = z.object({})

export const ChatSchema = z.object({
  msg: z.string().describe('Message to send'),
})

export const ExecSchema = z.object({
  cmd: z.string().describe('Minecraft command without /, e.g. "time set day"'),
})

export const PositionSchema = z.object({
  x: z.number().describe('X coordinate'),
  y: z.number().describe('Y coordinate'),
  z: z.number().describe('Z coordinate'),
})

export const EntitiesSchema = z.object({
  r: z.number().optional().describe('Scan radius (default: 16)'),
})

export const ChatlogSchema = z.object({
  count: z.number().optional().describe('Number of messages to return (default: 50)'),
  player: z.string().optional().describe('Filter by player name'),
  keyword: z.string().optional().describe('Filter by keyword'),
})

export const LogsSchema = z.object({
  lines: z.number().optional().describe('Number of log lines to return (default: 50)'),
})

export const BaritoneSchema = z.object({
  cmd: z.string().describe('Baritone command without # prefix'),
})

export const AnalyzeLogsSchema = z.object({
  lines: z.number().optional().describe('Number of log lines to scan (default: 200)'),
})

export const ModInfoSchema = z.object({
  mod_id: z.string().optional().describe('Filter by mod ID'),
})

export const HighlightBlockSchema = z.object({
  x: z.number().optional().describe('X coordinate (omit to use look target)'),
  y: z.number().optional().describe('Y coordinate'),
  z: z.number().optional().describe('Z coordinate'),
  r: z.number().optional().describe('Red (0-1)'),
  g: z.number().optional().describe('Green (0-1)'),
  b: z.number().optional().describe('Blue (0-1)'),
  duration: z.number().optional().describe('Duration in ms (default: 60000)'),
})

export const EntitySchema = z.object({
  id: z.number().optional().describe('Entity ID to query'),
  look: z.boolean().optional().describe('Use the entity the player is looking at (set to true)'),
})

export const BlockSchema = z.object({
  x: z.number().optional().describe('X coordinate'),
  y: z.number().optional().describe('Y coordinate'),
  z: z.number().optional().describe('Z coordinate'),
  look: z.boolean().optional().describe('Use the block the player is looking at (set to true)'),
})

export const InterceptSchema = z.object({
  enable: z.boolean().optional().describe('Set intercept on/off'),
  toggle: z.boolean().optional().describe('Toggle current state'),
  mode: z.enum(['off', 'copy', 'intercept']).optional().describe('Intercept mode: off/copy/intercept'),
})

export const SendSchema = z.object({
  msg: z.string().describe('Message to send'),
})

export const SetKeySchema = z.object({
  key: z.string().describe('DeepSeek API key (sk-...)'),
})

export const TranslateSchema = z.object({
  text: z.string().describe('Text to translate'),
  target: z.string().optional().describe('Target language (default: Japanese)'),
  source: z.string().optional().describe('Source language (optional, auto-detect if omitted)'),
})

export const ConfigureTranslateSchema = z.object({
  enable: z.boolean().optional().describe('Enable auto-translate'),
  api_key: z.string().optional().describe('DeepSeek API key'),
  target: z.string().optional().describe('Target language (default: Japanese)'),
})

export const ScreenSchema = z.object({})

export const ScreenshotSchema = z.object({})

export const ListClassesSchema = z.object({
  query: z.string().optional().describe('Search filter for class names (e.g. "Entity", "Block", "Player")'),
  limit: z.number().optional().describe('Max results (default: 50)'),
})

export const InspectClassSchema = z.object({
  class: z.string().describe('Full qualified class name, e.g. "net.minecraft.entity.LivingEntity"'),
  detail: z.boolean().optional().describe('Show verbose details (default: false)'),
})

export const SearchSourceSchema = z.object({
  query: z.string().describe('Search query (class/method name)'),
  type: z.enum(['class', 'method', 'all']).optional().describe('Search type (default: all)'),
})

export const BreakBlockSchema = z.object({
  x: z.number().describe('X coordinate'),
  y: z.number().describe('Y coordinate'),
  z: z.number().describe('Z coordinate'),
})

export const SeedSchema = z.object({})

export const MoveItemSchema = z.object({
  from: z.number().describe('Source slot index'),
  to: z.number().describe('Target slot index'),
  count: z.number().optional().describe('Item count (default: all)'),
})

export const DropItemSchema = z.object({
  slot: z.number().describe('Slot index to drop from'),
})

export const ScoreboardSchema = z.object({})
export const BossBarSchema = z.object({})
export const AdvancementSchema = z.object({
  only_done: z.boolean().optional().describe('Only show done/earned advancements (default: false)'),
})

export const EquipItemSchema = z.object({
  slot: z.number().describe('Inventory slot containing the item'),
  equipment_slot: z.string().optional().describe('Target equipment slot: head/chest/legs/feet/offhand (default: mainhand)'),
})

export const SummonEntitySchema = z.object({
  entity: z.string().describe('Entity type, e.g. "minecraft:pig" or "pig"'),
  x: z.number().describe('X coordinate'),
  y: z.number().describe('Y coordinate'),
  z: z.number().describe('Z coordinate'),
  nbt: z.string().optional().describe('Optional NBT tag string'),
})

export const PlaceBlockSchema = z.object({
  x: z.number().describe('X coordinate'),
  y: z.number().describe('Y coordinate'),
  z: z.number().describe('Z coordinate'),
  block: z.string().describe('Block ID, e.g. "minecraft:stone" or "stone"'),
})

export const BiomeSchema = z.object({
  x: z.number().optional().describe('X coordinate (omit for player position)'),
  y: z.number().optional().describe('Y coordinate'),
  z: z.number().optional().describe('Z coordinate'),
})

export const FindBlocksSchema = z.object({
  block: z.string().optional(),
  blocks: z.array(z.string()).optional(),
  radius: z.number().optional(),
  x1: z.number().optional(),
  y1: z.number().optional(),
  z1: z.number().optional(),
  x2: z.number().optional(),
  y2: z.number().optional(),
  z2: z.number().optional(),
})

export const SlimeChunkSchema = z.object({
  cx: z.number().describe('Chunk X coordinate'),
  cz: z.number().describe('Chunk Z coordinate'),
  radius: z.number().optional().describe('Search radius in chunks (default: 0 = just check this chunk)'),
  seed: z.number().optional().describe('World seed (fetched via /seed if not provided)'),
})

export const FindItemSchema = z.object({
  name: z.string(),
  container: z.boolean().optional(),
})

export const EnchantSimSchema = z.object({
  target: z.string().optional().describe('Target item or enchantment goal (e.g., "sharpness V diamond sword")'),
  max_xp: z.number().optional().describe('Maximum XP levels to spend'),
  strategy: z.enum(['best', 'cheapest', 'balanced']).optional().describe('Strategy: best/cheapest/balanced'),
})

export const RelationshipGraphSchema = z.object({
  names: z.string().optional().describe('Comma-separated player names to include (default: all tracked)'),
  format: z.enum(['mermaid', 'json']).optional().describe('Output format (default: mermaid)'),
})

export const AutoTradeSchema = z.object({
  index: z.number().optional().describe('Trade recipe index to repeat (default: 0 = first trade, -1 = auto-detect best)'),
  count: z.number().optional().describe('How many trades to execute (default: as many as possible)'),
  villager_name: z.string().optional().describe('Target villager name (optional, auto-finds nearest if omitted)'),
})

export const AutoFarmSchema = z.object({
  radius: z.number().optional().describe('Scan radius in blocks (default: 16)'),
  action: z.enum(['scan', 'harvest', 'full']).optional().describe('Action: scan (just check), harvest (break mature), full (harvest + replant)'),
  crop: z.string().optional().describe('Specific crop to harvest (e.g., "wheat", "carrots"). Default: all mature crops'),
})

export const ChatSentimentSchema = z.object({
  count: z.number().optional().describe('Number of recent messages to analyze (default: 20)'),
  player: z.string().optional().describe('Filter by specific player name'),
})

export const PlayerEffectsSchema = z.object({})

export const StatisticsSchema = z.object({})

export const SignSchema = z.object({
  x: z.number().optional(),
  y: z.number().optional(),
  z: z.number().optional(),
  look: z.boolean().optional(),
})

export const WorldBorderSchema = z.object({})

export const PlayerAbilitiesSchema = z.object({})

export const LastDeathSchema = z.object({})

export const PressKeySchema = z.object({
  key: z.string().describe('Key name: "forward", "jump", "use", "key.keyboard.w", "slot_1", etc.'),
  state: z.boolean().optional().describe('true = press, false = release (default: true)'),
  duration: z.number().optional().describe('Hold duration in ms (auto-releases after)'),
})

export const UseItemSchema = z.object({})

export const WalkToSchema = z.object({
  x: z.number(),
  y: z.number(),
  z: z.number(),
  threshold: z.number().optional().describe('Stop distance from target (default: 1.5)'),
  sprint: z.boolean().optional().describe('Hold sprint while walking (default: false)'),
})

export const ChunkSchema = z.object({
  x: z.number().describe('Chunk X coordinate'),
  z: z.number().describe('Chunk Z coordinate'),
})

export const BuildTemplateSchema = z.object({
  template: z.enum(['house', 'wall', 'tower', 'bridge', 'staircase', 'platform', 'pillar', 'arch', 'pyramid', 'room']).describe('Building template'),
  x: z.number().describe('Start X coordinate'),
  y: z.number().describe('Start Y coordinate'),
  z: z.number().describe('Start Z coordinate'),
  width: z.number().optional().describe('Width (default: 5)'),
  height: z.number().optional().describe('Height (default: 4)'),
  depth: z.number().optional().describe('Depth (default: 5)'),
  material: z.string().optional().describe('Block material (default: "minecraft:stone_bricks")'),
  floor_material: z.string().optional().describe('Floor material (default: same as material)'),
  roof_material: z.string().optional().describe('Roof material (default: same as material)'),
  door: z.boolean().optional().describe('Include door (default: true for house)'),
  windows: z.boolean().optional().describe('Include windows (default: true for house)'),
  execute: z.boolean().optional().describe('Execute immediately (default: true — set to false to preview)'),
})

export const MirrorBuildSchema = z.object({
  axis: z.enum(['x', 'y', 'z']).describe('Mirror axis'),
  center: z.number().optional().describe('Center block coordinate (ODD width — axis passes through this block)'),
  center1: z.number().optional().describe('First center coordinate (EVEN width — axis is BETWEEN center1 and center2)'),
  center2: z.number().optional().describe('Second center coordinate (EVEN width)'),
  source_min: z.number().describe('Source half minimum along axis'),
  source_max: z.number().describe('Source half maximum along axis'),
  y1: z.number().describe('Minimum Y bound'),
  y2: z.number().describe('Maximum Y bound'),
  z1: z.number().describe('Z bound (or X bound when axis=z)'),
  z2: z.number().describe('Z bound (or X bound when axis=z)'),
  x1: z.number().optional().describe('Minimum X bound (for axis=z or axis=y)'),
  x2: z.number().optional().describe('Maximum X bound (for axis=z or axis=y)'),
})

export const BuildLlmSchema = z.object({
  description: z.string().describe('Natural language description of what to build'),
  x: z.number().describe('Start X coordinate'),
  y: z.number().describe('Start Y coordinate'),
  z: z.number().describe('Start Z coordinate'),
  execute: z.boolean().optional().describe('Execute the plan immediately (default: false — preview only)'),
})

export const SetDeepSeekKeySchema = z.object({
  key: z.string().describe('DeepSeek API key (sk-...)'),
})

export const GuiClickSchema = z.object({
  slot: z.number().describe('Slot index to click'),
  button: z.number().optional().describe('Mouse button (0=left, 1=right, default: 0)'),
  action: z.enum(['PICKUP', 'QUICK_MOVE', 'THROW', 'SWAP', 'CLONE']).optional().describe('Click action type (default: PICKUP)'),
})

export const TradeSchema = z.object({
  index: z.number().describe('Trade recipe index (0-based)'),
})

export const AutoFishSchema = z.object({
  action: z.enum(['start', 'stop', 'status']).optional().describe('Action (default: status)'),
})

export const ScreenshotRepeatSchema = z.object({
  action: z.enum(['start', 'stop', 'status']).optional().describe('Action (default: status)'),
  interval: z.number().optional().describe('Interval in seconds (default: 60)'),
  count: z.number().optional().describe('Number of screenshots (default: 0 = unlimited)'),
})

export const BuildGeometrySchema = z.object({
  shape: z.enum(['cylinder', 'sphere', 'dome']).describe('Shape type'),
  x: z.number().describe('Center X'),
  y: z.number().describe('Base Y'),
  z: z.number().describe('Center Z'),
  radius: z.number().describe('Radius in blocks'),
  height: z.number().optional().describe('Height (for cylinder/dome; default: radius)'),
  material: z.string().optional().describe('Block material (default: minecraft:stone_bricks)'),
  hollow: z.boolean().optional().describe('Hollow (default: false)'),
  execute: z.boolean().optional().describe('Execute immediately (default: true)'),
})

export const UndoBuildSchema = z.object({})

export const SetReconnectSchema = z.object({
  interval: z.number().optional().describe('Reconnect delay in ms (default: 3000)'),
  max_attempts: z.number().optional().describe('Max reconnect attempts (default: 20)'),
})

export const RunScriptSchema = z.object({
  steps: z.array(z.object({
    type: z.enum(['command', 'chat', 'wait', 'loop', 'condition', 'while', 'if', 'parallel']).describe('Step type'),
    cmd: z.string().optional().describe('Minecraft command (for type=command)'),
    msg: z.string().optional().describe('Chat message (for type=chat)'),
    ms: z.number().optional().describe('Wait duration in ms (for type=wait)'),
    count: z.number().optional().describe('Loop count or maxIterations for while (default: 1/100)'),
    steps: z.array(z.any()).optional().describe('Loop/while body steps'),
    then: z.array(z.any()).optional().describe('If-true branch steps (for type=if)'),
    else: z.array(z.any()).optional().describe('If-false branch steps (for type=if)'),
    condition: z.object({
      if_block: z.object({
        x: z.number(), y: z.number(), z: z.number(),
        block: z.string().optional(),
        not: z.boolean().optional(),
      }).optional(),
      if_slot: z.object({
        slot: z.number(),
        item: z.string().optional(),
        not: z.boolean().optional(),
      }).optional(),
      if_distance: z.object({
        x: z.number(), y: z.number(), z: z.number(),
        max: z.number().optional(),
        min: z.number().optional(),
        not: z.boolean().optional(),
      }).optional(),
    }).optional().describe('Condition object for while/if steps'),
    if_block: z.object({
      x: z.number(), y: z.number(), z: z.number(),
      block: z.string().optional().describe('Expected block ID (omit to check any block)'),
    }).optional().describe('Block condition check (for type=condition)'),
    if_slot: z.object({
      slot: z.number(),
      item: z.string().optional(),
      not: z.boolean().optional(),
    }).optional().describe('Slot condition check'),
    if_distance: z.object({
      x: z.number(), y: z.number(), z: z.number(),
      max: z.number().optional(),
      min: z.number().optional(),
      not: z.boolean().optional(),
    }).optional().describe('Distance condition check'),
    not: z.boolean().optional().describe('Invert condition result'),
  })).describe('Sequence of script steps to execute'),
})

export const FindVillagerSchema = z.object({})

export const SortInventorySchema = z.object({})

export const RefillSchema = z.object({
  threshold: z.number().optional().describe('Min item count before refilling (default: 16)'),
  container: z.boolean().optional().describe('Search container too (default: false)'),
})

export const AutoStoreSchema = z.object({
  item: z.string().describe('Item name/ID to move from inventory to container'),
  count: z.number().optional().describe('Max count to move (default: all)'),
  container_slot_start: z.number().optional().describe('Container slot to start filling (default: 0)'),
})

export const CraftItemSchema = z.object({
  item: z.string().describe('Item name/ID to craft'),
  count: z.number().optional().describe('How many to craft (default: 1)'),
})

export const ScanTerrainSchema = z.object({
  radius: z.number().optional().describe('Scan radius in blocks (default: 16)'),
  yRange: z.number().optional().describe('Vertical range (default: 8)'),
  ai_suggest: z.boolean().optional().describe('Include AI building recommendations based on terrain (default: false)'),
})

export const ExplainScreenSchema = z.object({})

export const AnalyzeInventorySchema = z.object({})

export const BatchBuildSchema = z.object({
  commands: z.array(z.string()).describe('Array of /setblock or /fill commands to optimize'),
})

export const EntityHighlightSchema = z.object({
  type: z.string().optional().describe('Entity type/name to search for'),
  radius: z.number().optional().describe('Search radius (default: 32)'),
  max: z.number().optional().describe('Max results (default: 20)'),
  auto_highlight: z.boolean().optional().describe('Auto-highlight found entities in-game (default: false)'),
})

export const DamageDisplaySchema = z.object({
  id: z.number().describe('Entity ID to query'),
})

export const TpsSchema = z.object({})

export const ReachSchema = z.object({})

export const PingInfoSchema = z.object({})

export const PacketLoggerSchema = z.object({
  action: z.enum(['start', 'stop', 'status']).describe('Action'),
})

export const PacketLoggerDetailSchema = z.object({
  action: z.enum(['start', 'stop', 'status']).describe('Action'),
  filter: z.string().optional().describe('Packet name filter'),
})

export const PacketLoggerFindSchema = z.object({
  query: z.string().describe('Search query (packet name or content)'),
  direction: z.enum(['C2S', 'S2C', '']).optional().describe('Packet direction'),
  limit: z.number().optional().describe('Max results (default: 20)'),
})

export const BedrockBreakerSchema = z.object({
  x: z.number().describe('X coordinate'),
  y: z.number().describe('Y coordinate'),
  z: z.number().describe('Z coordinate'),
  attempts: z.number().optional().describe('Attempts (default: 5)'),
})

export const ScanContainersSchema = z.object({
  radius: z.number().optional().describe('Scan radius (default: 32)'),
})

export const ShulkerPeekSchema = z.object({
  slot: z.number().optional().describe('Specific slot to check (default: all)'),
})

export const WaypointsSchema = z.object({
  action: z.enum(['list', 'add', 'remove', 'goto']).describe('Action'),
  name: z.string().optional().describe('Waypoint name'),
  x: z.number().optional().describe('X coordinate'),
  y: z.number().optional().describe('Y coordinate'),
  z: z.number().optional().describe('Z coordinate'),
})

export const TravelLogSchema = z.object({
  action: z.enum(['start', 'stop', 'get']).describe('Action'),
  limit: z.number().optional().describe('Max entries (default: 100)'),
})

export const ScanCropsSchema = z.object({
  radius: z.number().optional().describe('Scan radius (default: 16)'),
})

export const BlockCounterSchema = z.object({
  cx: z.number().optional().describe('Chunk X (default: current chunk)'),
  cz: z.number().optional().describe('Chunk Z (default: current chunk)'),
})

export const GetPlayerProfileSchema = z.object({
  name: z.string().optional().describe('Player name (default: self)'),
})

export const AnalyzePlayerProfileSchema = z.object({
  name: z.string().describe('Player name to analyze'),
  focus: z.string().optional().describe('Analysis focus (personality/behavior/communication/trust/vulnerability)'),
})

export const LearnMyStyleSchema = z.object({
  messages: z.number().optional().describe('Number of recent outgoing messages to analyze (default: 50)'),
})

export const GenerateMessageSchema = z.object({
  goal: z.string().describe('What you want to achieve with this message'),
  target: z.string().describe('Target player name'),
  tone: z.string().optional().describe('Tone/style (friendly/neutral/formal/playful/assertive)'),
  context: z.string().optional().describe('Additional context about the situation'),
})

export const AnalyzeRelationshipsSchema = z.object({
  names: z.string().optional().describe('Comma-separated player names to analyze (default: all tracked)'),
})

export const AnalyzeChatStreamSchema = z.object({
  duration: z.number().optional().describe('Minutes of recent chat to analyze (default: 10)'),
  focus: z.string().optional().describe('Analysis focus (sentiment/topics/tensions/opportunities)'),
})

export const SimulateOutcomeSchema = z.object({
  message: z.string().describe('The message or action you plan to take'),
  target: z.string().describe('Target player name'),
  context: z.string().optional().describe('Background context for simulation'),
})

export const GetServerInfoSchema = z.object({})

export const SetServerBrandSchema = z.object({
  address: z.string().describe('Server address'),
  brand: z.string().describe('Brand name'),
  networkType: z.string().describe('major_network or small_server'),
  displayName: z.string().describe('Display name'),
})

export const GetAllServersSchema = z.object({})

export const GetPendingAnalysisSchema = z.object({})

export const MarkAnalysisDoneSchema = z.object({
  name: z.string().describe('Player name'),
})

export const AnalyzeCurrentServerSchema = z.object({
  address: z.string().optional().describe('Auto-detected; omit to use current'),
  motd: z.string().optional().describe('Auto-detected; omit to use current'),
})

export const AutoExploreSchema = z.object({
  action: z.enum(['start', 'stop', 'status', 'data']).describe('Action'),
  radius: z.number().optional().describe('Max radius in blocks (default: 100)'),
  mode: z.enum(['spiral', 'radial']).optional().describe('Exploration pattern (default: spiral)'),
})

export const MemoryAddSchema = z.object({
  content: z.string().describe('Memory content'),
  category: z.enum(['event', 'location', 'player', 'observation', 'note']).optional().describe('Category (default: note)'),
  importance: z.number().optional().describe('Importance 1-5 (default: 3)'),
  tags: z.string().optional().describe('Comma-separated tags'),
})

export const MemoryRecallSchema = z.object({
  query: z.string().optional().describe('Search query'),
  category: z.string().optional().describe('Filter by category'),
  limit: z.number().optional().describe('Max results (default: 20)'),
})

export const MemoryDeleteSchema = z.object({
  id: z.string().describe('Memory ID to delete'),
})

export const MemoryNearSchema = z.object({
  x: z.number().describe('Center X'),
  y: z.number().describe('Center Y'),
  z: z.number().describe('Center Z'),
  radius: z.number().optional().describe('Search radius in blocks (default: 32)'),
  limit: z.number().optional().describe('Max results (default: 20)'),
})

export const AttackEntitySchema = z.object({
  id: z.number().describe('Entity ID to attack'),
})

export const InteractEntitySchema = z.object({
  id: z.number().describe('Entity ID to interact with'),
  hand: z.enum(['main', 'offhand']).optional().describe('Hand to use (default: main)'),
})

export const RideEntitySchema = z.object({
  id: z.number().optional().describe('Entity ID to ride. Omit to dismount.'),
})

export const MemoryExportSchema = z.object({
  format: z.enum(['json', 'csv']).optional().describe('Export format (default: json)'),
})

export const WaypointExportSchema = z.object({})

export const WaypointImportSchema = z.object({
  waypoints: z.string().describe('JSON string of waypoints array to import'),
  merge: z.boolean().optional().describe('Merge with existing (default: false, replaces all)'),
})

export const TravelLogStatsSchema = z.object({})

export const ReadComparatorSchema = z.object({
  x: z.number().describe('X coordinate'),
  y: z.number().describe('Y coordinate'),
  z: z.number().describe('Z coordinate'),
})

export const ToggleBlockSchema = z.object({
  x: z.number().describe('X coordinate of lever/button/piston'),
  y: z.number().describe('Y coordinate'),
  z: z.number().describe('Z coordinate'),
})

export const TerrainHeightmapSchema = z.object({
  radius: z.number().optional().describe('Scan radius in chunks (default: 3, so 7x7 chunks)'),
})

export const SetModelSchema = z.object({
  provider: z.enum(['deepseek', 'openai', 'claude']).describe('AI provider'),
  api_key: z.string().describe('API key for the provider'),
  model: z.string().optional().describe('Model name (e.g., "gpt-4", "claude-3-opus-20240229")'),
})

export const DiscordWebhookSchema = z.object({
  url: z.string().optional().describe('Discord webhook URL'),
  bridge_chat: z.boolean().optional().describe('Bridge chat messages (default: true)'),
  bot_token: z.string().optional().describe('Discord bot token for bidirectional bridging'),
  channel_id: z.string().optional().describe('Discord channel ID to bridge'),
  action: z.enum(['set', 'status', 'start', 'stop']).optional().describe('Action: set (save config), status, start (poll), stop'),
})

export const QQBridgeSchema = z.object({
  action: z.enum(['connect', 'disconnect', 'status']).describe('Action'),
  endpoint: z.string().optional().describe('go-cqhttp HTTP API endpoint (e.g., "http://127.0.0.1:5700")'),
  group_id: z.number().optional().describe('QQ group ID to bridge'),
})

export const QQSendSchema = z.object({
  message: z.string().describe('Message to send to QQ group'),
  group_id: z.number().optional().describe('QQ group ID (default: from config)'),
})

export const AutoBrewSchema = z.object({
  action: z.enum(['status', 'brew', 'collect']).describe('Action: status (check stand), brew (add ingredient and start), collect (collect potions)'),
  ingredient: z.string().optional().describe('Item name/ID for the brewing ingredient (e.g., "nether_wart", "glowstone_dust")'),
  slot: z.number().optional().describe('Ingredient slot (0-3, default: 0 = top slot)'),
})

export const AutoCookSchema = z.object({
  action: z.enum(['status', 'cook', 'collect']).describe('Action'),
  fuel: z.string().optional().describe('Fuel item name/ID (e.g., "coal", "charcoal", "planks")'),
  input: z.string().optional().describe('Item name/ID to cook/smelt'),
  count: z.number().optional().describe('How many to cook (default: all available)'),
})

export const EntitySelectorSchema = z.object({
  type: z.string().optional().describe('Entity type/name filter (e.g., "zombie", "sheep", "item")'),
  min_distance: z.number().optional().describe('Minimum distance in blocks'),
  max_distance: z.number().optional().describe('Maximum distance in blocks'),
  limit: z.number().optional().describe('Max results (default: 50)'),
})

export const KillAllSchema = z.object({
  type: z.string().describe('Entity type to kill (e.g., "zombie", "creeper")'),
  radius: z.number().optional().describe('Search radius (default: 16)'),
  max: z.number().optional().describe('Max entities to kill (default: 10)'),
})

export const NearestStructureSchema = z.object({
  structure: z.string().describe('Structure ID, e.g. "village", "fortress", "mansion", "ancient_city"'),
})

export const EntityDensityMapSchema = z.object({
  radius: z.number().optional().describe('Scan radius in chunks (default: 3)'),
  type: z.string().optional().describe('Filter by entity type'),
})

export const OreDistributionSchema = z.object({
  ores: z.array(z.string()).optional().describe('Ore types to scan (default: ["diamond_ore", "iron_ore", "coal_ore", "copper_ore", "gold_ore", "redstone_ore", "lapis_ore", "emerald_ore"])'),
  radius: z.number().optional().describe('Scan radius in blocks (default: 16)'),
})

export const ScreenshotAnalyzeSchema = z.object({
  question: z.string().optional().describe('Specific question about the screenshot (default: "What do you see?")'),
})

export const EntityTrackSchema = z.object({
  id: z.number().describe('Entity ID to track'),
  duration: z.number().optional().describe('Tracking duration in seconds (default: 10)'),
  interval: z.number().optional().describe('Polling interval in seconds (default: 1)'),
})

export const AutoShearSchema = z.object({
  radius: z.number().optional().describe('Search radius (default: 10)'),
  max: z.number().optional().describe('Max sheep to shear (default: 5)'),
})

export const SpawnParticleSchema = z.object({
  particle: z.string().describe('Particle type ID (e.g., "minecraft:flame", "minecraft:heart", "minecraft:cloud")'),
  x: z.number().describe('X coordinate'),
  y: z.number().describe('Y coordinate'),
  z: z.number().describe('Z coordinate'),
  vx: z.number().optional().describe('Velocity X (default: 0)'),
  vy: z.number().optional().describe('Velocity Y (default: 0.1)'),
  vz: z.number().optional().describe('Velocity Z (default: 0)'),
  count: z.number().optional().describe('Particle count (default: 10)'),
})

export const PlaySoundSchema = z.object({
  sound: z.string().describe('Sound ID (e.g., "minecraft:entity.experience_orb.pickup", "minecraft:block.note_block.pling")'),
  x: z.number().optional().describe('X coordinate (default: player position)'),
  y: z.number().optional().describe('Y coordinate'),
  z: z.number().optional().describe('Z coordinate'),
  volume: z.number().optional().describe('Volume (default: 1.0)'),
  pitch: z.number().optional().describe('Pitch (default: 1.0)'),
})

export const DisplayTitleSchema = z.object({
  title: z.string().describe('Main title text'),
  subtitle: z.string().optional().describe('Subtitle text'),
  fadeIn: z.number().optional().describe('Fade-in time in ticks (default: 10)'),
  stay: z.number().optional().describe('Stay time in ticks (default: 40)'),
  fadeOut: z.number().optional().describe('Fade-out time in ticks (default: 10)'),
})

export const ConfigReloadSchema = z.object({})

export const AutoTntSchema = z.object({
  x: z.number().describe('X coordinate'),
  y: z.number().describe('Y coordinate'),
  z: z.number().describe('Z coordinate'),
})

export const AutoEnchantSchema = z.object({
  action: z.enum(['status', 'enchant', 'collect']).describe('Action'),
  slot: z.number().optional().describe('Enchantment slot index (0-2, default: auto-select)'),
  item_slot: z.number().optional().describe('Inventory slot of item to enchant'),
})

export const AutoSmithSchema = z.object({
  action: z.enum(['status', 'upgrade', 'collect']).describe('Action'),
  upgrade_slot: z.number().optional().describe('Inventory slot containing upgrade template/netherite ingot'),
  item_slot: z.number().optional().describe('Inventory slot containing item to upgrade'),
})

export const AutoAnvilSchema = z.object({
  action: z.enum(['status', 'combine', 'collect']).describe('Action'),
  left_slot: z.number().optional().describe('Inventory slot for left item (target)'),
  right_slot: z.number().optional().describe('Inventory slot for right item (sacrifice/book)'),
  rename: z.string().optional().describe('Rename the item (optional)'),
})

export const ProjectileSimulateSchema = z.object({
  pitch: z.number().optional().describe('Launch pitch in degrees (default: current player pitch)'),
  yaw: z.number().optional().describe('Launch yaw in degrees (default: current player yaw)'),
  velocity: z.number().optional().describe('Velocity multiplier (default: 1.0)'),
  gravity: z.boolean().optional().describe('Apply gravity (default: true)'),
  steps: z.number().optional().describe('Trajectory steps (default: 50)'),
})

export const NetworkGraphSchema = z.object({})

export const ChatMimicSchema = z.object({
  player: z.string().describe('The player to mimic'),
  message: z.string().describe('The message content or intent you want conveyed'),
  count: z.number().optional().describe('Number of recent messages to learn from (default: 30)'),
  send: z.boolean().optional().describe('Immediately send the generated message (default: false)'),
})

export const GaslightSchema = z.object({
  target: z.string().describe('Target player or audience'),
  tactic: z.enum(['impersonate', 'rumor', 'contradict', 'confuse']).describe('Gaslight tactic'),
  topic: z.string().optional().describe('Topic to focus on'),
  send: z.boolean().optional().describe('Send immediately (default: false)'),
  context: z.string().optional().describe('Additional context about the server situation'),
})

export const SocialEngineerSchema = z.object({
  target: z.string().describe('Target player name'),
  goal: z.string().describe('What you want to achieve (e.g., "get them to share their base coords", "turn them against another player")'),
  context: z.string().optional().describe('Additional context'),
})

export const PropagandaSchema = z.object({
  topic: z.string().describe('Topic of the announcement'),
  tone: z.enum(['positive', 'urgent', 'warning', 'neutral', 'divisive']).describe('Tone of the message'),
  target_audience: z.string().optional().describe('Who this is targeting (e.g., "new players", "the whole server")'),
  key_message: z.string().optional().describe('The core message to convey'),
  broadcast: z.boolean().optional().describe('Broadcast as server announcement (default: false)'),
})

export const AutonomousGoalSchema = z.object({
  goal: z.string().describe('The goal to achieve, e.g. "build a small wooden house near the village"'),
  context: z.string().optional().describe('Additional context about the current situation, resources, location'),
  preview: z.boolean().optional().describe('Preview the plan without executing (default: false)'),
})

export const SessionReportSchema = z.object({
  include_chat: z.boolean().optional().describe('Include recent chat (default: true)'),
  include_stats: z.boolean().optional().describe('Include statistics (default: true)'),
  include_inventory: z.boolean().optional().describe('Include inventory (default: false)'),
  include_memories: z.boolean().optional().describe('Include session memories (default: true)'),
  ai_summary: z.boolean().optional().describe('Generate AI summary using DeepSeek (default: false)'),
})

export const SetAliasSchema = z.object({
  alias: z.string().describe('Short alias name (e.g., "home", "spawn")'),
  command: z.string().describe('Command or message to execute (e.g., "tp 0 64 0" or "I\'m back!")'),
  remove: z.boolean().optional().describe('Remove this alias if true'),
})

export const ListAliasesSchema = z.object({})

export const MemoryDedupSchema = z.object({
  action: z.enum(['scan', 'remove']).describe('scan: find duplicates; remove: deduplicate automatically'),
  strategy: z.enum(['content', 'location']).optional().describe('Dedup strategy (default: content)'),
})

export const BuildPreviewSchema = z.object({
  template: z.enum(['house', 'wall', 'tower', 'bridge', 'staircase', 'platform', 'pillar', 'arch', 'pyramid', 'room']).describe('Building template'),
  x: z.number().describe('Start X coordinate'),
  y: z.number().describe('Start Y coordinate'),
  z: z.number().describe('Start Z coordinate'),
  width: z.number().optional().describe('Width (default: 5)'),
  height: z.number().optional().describe('Height (default: 4)'),
  depth: z.number().optional().describe('Depth (default: 5)'),
  material: z.string().optional().describe('Block material (default: "minecraft:stone_bricks")'),
  floor_material: z.string().optional().describe('Floor material (default: same as material)'),
  roof_material: z.string().optional().describe('Roof material (default: same as material)'),
  door: z.boolean().optional().describe('Include door (default: true for house)'),
  windows: z.boolean().optional().describe('Include windows (default: true for house)'),
  execute: z.boolean().optional().describe('Actually execute build (default: false — just preview)'),
})

export const EconomicPipelineSchema = z.object({
  crop: z.string().optional().describe('Crop type to harvest (e.g., "wheat", "carrots")'),
  craft: z.string().optional().describe('Item to craft from harvest (e.g., "bread", "golden_carrot")'),
  trade_index: z.number().optional().describe('Trade recipe index to use (default: 0)'),
  max_cycles: z.number().optional().describe('Max pipeline cycles (default: 1)'),
})

export const WorkflowCreateSchema = z.object({
  name: z.string().describe('Workflow name'),
  schedule: z.string().describe('Schedule: "interval:<seconds>", "cron:<expression>", or "once"'),
  steps: z.array(z.object({
    type: z.enum(['command', 'chat', 'wait']).describe('Step type'),
    cmd: z.string().optional().describe('Minecraft command (for type=command)'),
    msg: z.string().optional().describe('Chat message (for type=chat)'),
    ms: z.number().optional().describe('Wait duration in ms (for type=wait)'),
  })).describe('Steps to execute when triggered'),
  enabled: z.boolean().optional().describe('Start enabled (default: true)'),
})

export const WorkflowListSchema = z.object({})

export const WorkflowRemoveSchema = z.object({
  name: z.string().describe('Workflow name to remove'),
})

export const MapRenderSchema = z.object({
  radius: z.number().optional().describe('Map radius in chunks (default: 4)'),
  show_players: z.boolean().optional().describe('Overlay player positions (default: true)'),
})

export const RedstoneSimulateSchema = z.object({
  radius: z.number().optional().describe('Scan radius (default: 10)'),
  mode: z.enum(['scan', 'analyze']).optional().describe('scan: find components, analyze: AI analysis of circuit'),
})

export const AfkStandinSchema = z.object({
  action: z.enum(['start', 'stop', 'status']).describe('Action'),
  learning_count: z.number().optional().describe('Number of recent messages to learn style from (default: 50)'),
  poll_interval: z.number().optional().describe('Poll interval in seconds (default: 5)'),
})

export const SocialCreditSchema = z.object({
  player: z.string().optional().describe('Player name (default: all tracked players)'),
})

export const AutonomousSocialSchema = z.object({
  action: z.enum(['start', 'stop', 'status']).describe('Action'),
  aggressiveness: z.number().optional().describe('0=passive observer, 1=neutral, 2=opportunistic, 3=aggressive (default: 1)'),
  focus_players: z.string().optional().describe('Comma-separated player names to focus on (default: all)'),
})

export const CampaignSchema = z.discriminatedUnion('action', [
  z.object({ action: z.literal('list') }),
  z.object({ action: z.literal('abort'), name: z.string() }),
  z.object({ action: z.literal('status'), name: z.string() }),
  z.object({ action: z.literal('advance'), name: z.string() }),
  z.object({
    action: z.literal('create'),
    name: z.string(),
    target: z.string(),
    goal: z.string(),
    strategy: z.enum(['undermine', 'befriend', 'rivalry', 'divide']).optional(),
    duration_days: z.number().optional(),
  }),
])

export const DetectManipulationSchema = z.object({
  player: z.string().optional().describe('Check if a specific player is manipulating you (default: auto-detect from recent chat)'),
  message: z.string().optional().describe('Check a specific message (default: check all recent messages to you)'),
})

export const IceBoatSchema = z.object({
    action: z.enum(['start', 'stop', 'status']).describe('Action: start navigation, stop, or get status'),
    x: z.number().optional().describe('Target X coordinate (required for start)'),
    z: z.number().optional().describe('Target Z coordinate (required for start)'),
    scan_radius: z.number().optional().describe('Scan radius for ice blocks (default: 8)'),
})
