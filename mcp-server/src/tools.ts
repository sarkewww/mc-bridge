export const toolDefinitions = [
  {
    name: 'mc_connect',
    description: 'Connect to the mc-bridge mod WebSocket (Minecraft must be running with the mod installed)',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_disconnect',
    description: 'Disconnect from the bridge',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_ping',
    description: 'Check if the bridge connection is alive',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_command',
    description: 'Execute a Minecraft command (e.g. "time set day", "give @p diamond 64")',
    inputSchema: {
      type: 'object',
      properties: { cmd: { type: 'string', description: 'Command without leading /' } },
      required: ['cmd'],
    },
  },
  {
    name: 'mc_chat',
    description: 'Send a chat message or command',
    inputSchema: {
      type: 'object',
      properties: { msg: { type: 'string', description: 'Message or /command' } },
      required: ['msg'],
    },
  },
  {
    name: 'mc_get_position',
    description: 'Get your current position, rotation, and dimension',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_get_direction',
    description: 'Get the cardinal direction the player is facing (N/NE/E/SE/S/SW/W/NW)',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_get_f3',
    description: 'Get F3 debug info: coordinates, FPS, health, food, entities, memory, ping',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_get_entities',
    description: 'List nearby entities (players, mobs, items) within a radius',
    inputSchema: {
      type: 'object',
      properties: { r: { type: 'number', description: 'Radius in blocks (default: 16)' } },
    },
  },
  {
    name: 'mc_nearby_players',
    description: 'Get online player list with UUID, game mode, ping, and display name (from tab list)',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_get_inventory',
    description: 'List your inventory (main, offhand, armor, held item)',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_get_item_detail',
    description: 'Get detailed info about a held or specific inventory slot item: enchantments, lore, durability, NBT',
    inputSchema: {
      type: 'object',
      properties: { slot: { type: 'number', description: 'Slot index (0-35 main, 40 offhand, 100-103 armor). Defaults to held item.' } },
    },
  },
  {
    name: 'mc_get_info',
    description: 'Get server info: difficulty, time, player list, server brand',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_look_at',
    description: 'Make your character look at specific coordinates',
    inputSchema: {
      type: 'object',
      properties: {
        x: { type: 'number', description: 'Target X' },
        y: { type: 'number', description: 'Target Y' },
        z: { type: 'number', description: 'Target Z' },
      },
      required: ['x', 'y', 'z'],
    },
  },
  {
    name: 'mc_get_mods',
    description: 'List all installed Fabric mods with IDs and versions',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_get_baritone',
    description: 'Check Baritone mod status: isPathing, goal, builder, mine, follow processes',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_get_wurst',
    description: 'Check Wurst client mod status: enabled hacks, features',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_get_chatlog',
    description: 'Get recent chat messages from the chat log',
    inputSchema: {
      type: 'object',
      properties: {
        count: { type: 'number', description: 'Number of messages (default: 50)' },
        player: { type: 'string', description: 'Filter by player name' },
        keyword: { type: 'string', description: 'Filter by keyword' },
      },
    },
  },
  {
    name: 'mc_clear_chat',
    description: 'Clear the chat screen',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_get_container',
    description: 'Read the currently open container\'s contents (chest, furnace, etc.)',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_get_entity',
    description: 'Get detailed info about an entity by ID or by looking at it',
    inputSchema: {
      type: 'object',
      properties: {
        id: { type: 'number', description: 'Entity ID' },
        look: { type: 'boolean', description: 'Look at entity instead (set to true)' },
      },
    },
  },
  {
    name: 'mc_get_block',
    description: 'Get detailed info about a block at coordinates or the block being looked at',
    inputSchema: {
      type: 'object',
      properties: {
        x: { type: 'number', description: 'X coordinate' },
        y: { type: 'number', description: 'Y coordinate' },
        z: { type: 'number', description: 'Z coordinate' },
        look: { type: 'boolean', description: 'Look at block instead (set to true)' },
      },
    },
  },
  {
    name: 'mc_set_intercept',
    description: 'Toggle or set chat intercept mode. When enabled, your outgoing chat messages are intercepted and logged so the AI can translate or moderate them. Use mc_get_chatlog to see intercepted messages, then mc_send to send the processed version.',
    inputSchema: {
      type: 'object',
      properties: {
        enable: { type: 'boolean', description: 'true to enable, false to disable' },
        toggle: { type: 'boolean', description: 'Toggle current state (set to true)' },
        mode: { type: 'string', enum: ['off', 'copy', 'intercept'], description: 'Set intercept mode directly' },
      },
    },
  },
  {
    name: 'mc_send',
    description: 'Send a chat message or /command as you, bypassing intercept mode',
    inputSchema: {
      type: 'object',
      properties: { msg: { type: 'string', description: 'Message to send' } },
      required: ['msg'],
    },
  },
  {
    name: 'mc_configure_translate',
    description: 'Configure auto-translate: set DeepSeek API key, target language, and toggle auto-translate. When auto-translate is ON and intercept is ON, intercepted messages are automatically translated and sent.',
    inputSchema: {
      type: 'object',
      properties: {
        enable: { type: 'boolean', description: 'Enable auto-translate' },
        api_key: { type: 'string', description: 'DeepSeek API key (sk-...)' },
        target: { type: 'string', description: 'Target language (default: Japanese)' },
      },
    },
  },
  {
    name: 'mc_configure_translate_received',
    description: 'Toggle auto-translation for received chat messages (server messages, other players). Requires mc_configure_translate to be set up first. Click translated message to toggle between original/translated.',
    inputSchema: {
      type: 'object',
      properties: {
        enable: { type: 'boolean', description: 'Enable incoming auto-translate' },
      },
    },
  },
  {
    name: 'mc_translate',
    description: 'Manually translate text using DeepSeek API (requires key set via mc_configure_translate first)',
    inputSchema: {
      type: 'object',
      properties: {
        text: { type: 'string', description: 'Text to translate' },
        target: { type: 'string', description: 'Target language (default: Japanese)' },
        source: { type: 'string', description: 'Source language (optional)' },
      },
      required: ['text'],
    },
  },
  {
    name: 'mc_get_logs',
    description: 'Get recent lines from the Minecraft game log (latest.log)',
    inputSchema: {
      type: 'object',
      properties: { lines: { type: 'number', description: 'Number of lines to return (default: 50)' } },
    },
  },
  {
    name: 'mc_baritone',
    description: 'Send a Baritone command via chat (#goto, #mine, #build, etc.)',
    inputSchema: {
      type: 'object',
      properties: { cmd: { type: 'string', description: 'Baritone command without #, e.g. "goto 100 64 200" or "mine diamond_ore"' } },
      required: ['cmd'],
    },
  },
  {
    name: 'mc_analyze_logs',
    description: 'Analyze the Minecraft log for errors, warnings, and exceptions',
    inputSchema: {
      type: 'object',
      properties: { lines: { type: 'number', description: 'Number of log lines to scan (default: 200)' } },
    },
  },
  {
    name: 'mc_mod_info',
    description: 'Get detailed information about installed mods, dependencies, and versions',
    inputSchema: {
      type: 'object',
      properties: { mod_id: { type: 'string', description: 'Filter by mod ID (optional, returns all if omitted)' } },
    },
  },
  {
    name: 'mc_highlight_block',
    description: 'Highlight a block with a colored outline in the world (visible in-game)',
    inputSchema: {
      type: 'object',
      properties: {
        x: { type: 'number', description: 'X coordinate (omit to use block you are looking at)' },
        y: { type: 'number', description: 'Y coordinate' },
        z: { type: 'number', description: 'Z coordinate' },
        r: { type: 'number', description: 'Red 0-1 (default: 1)' },
        g: { type: 'number', description: 'Green 0-1 (default: 0)' },
        b: { type: 'number', description: 'Blue 0-1 (default: 0)' },
        duration: { type: 'number', description: 'Duration in ms (default: 60000)' },
      },
    },
  },
  {
    name: 'mc_highlight_clear',
    description: 'Clear all block highlights',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_get_entity_detail',
    description: 'Get comprehensive entity info including attributes, status effects, equipment details, NBT data, vehicle/passengers, and more',
    inputSchema: {
      type: 'object',
      properties: {
        id: { type: 'number', description: 'Entity ID to query' },
        look: { type: 'boolean', description: 'Use the entity the player is looking at (set to true)' },
      },
    },
  },
  {
    name: 'mc_get_screen',
    description: 'Read the currently open GUI screen in detail (merchant trades, anvil, beacon, crafting table, enchanting table, stonecutter, smithing table, loom, cartography table, grindstone, brewing stand, furnace, etc.)',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_take_screenshot',
    description: 'Take a screenshot of the current game view (saves to Minecraft screenshots folder)',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_list_classes',
    description: 'Search for Minecraft classes by name pattern (uses decompiled merged jar)',
    inputSchema: {
      type: 'object',
      properties: {
        query: { type: 'string', description: 'Search filter for class names (e.g. "Entity", "LivingEntity", "Block")' },
        limit: { type: 'number', description: 'Max results (default: 50)' },
      },
    },
  },
  {
    name: 'mc_inspect_class',
    description: 'Inspect a Minecraft class: methods, fields, superclass, interfaces (uses javap)',
    inputSchema: {
      type: 'object',
      properties: {
        class: { type: 'string', description: 'Full class name, e.g. "net.minecraft.entity.LivingEntity"' },
        detail: { type: 'boolean', description: 'Show verbose details (default: false)' },
      },
      required: ['class'],
    },
  },
  {
    name: 'mc_search_source',
    description: 'Search the Minecraft mappings file for class/method names',
    inputSchema: {
      type: 'object',
      properties: {
        query: { type: 'string', description: 'Search query' },
        type: { type: 'string', description: 'Search type: class, method, or all (default: all)', enum: ['class', 'method', 'all'] },
      },
      required: ['query'],
    },
  },
  {
    name: 'mc_build_template',
    description: 'Build a predefined structure template (house, wall, tower, bridge, staircase, etc.) using /fill and /setblock commands near your position',
    inputSchema: {
      type: 'object',
      properties: {
        template: { type: 'string', description: 'Building template: house, wall, tower, bridge, staircase, platform, pillar, arch, pyramid, room', enum: ['house', 'wall', 'tower', 'bridge', 'staircase', 'platform', 'pillar', 'arch', 'pyramid', 'room'] },
        x: { type: 'number', description: 'Start X coordinate' },
        y: { type: 'number', description: 'Start Y coordinate' },
        z: { type: 'number', description: 'Start Z coordinate' },
        width: { type: 'number', description: 'Width (default: 5)' },
        height: { type: 'number', description: 'Height (default: 4)' },
        depth: { type: 'number', description: 'Depth (default: 5)' },
        material: { type: 'string', description: 'Block material (default: minecraft:stone_bricks)' },
        floor_material: { type: 'string', description: 'Floor material (default: same as material)' },
        roof_material: { type: 'string', description: 'Roof material (default: same as material)' },
        door: { type: 'boolean', description: 'Include door (default: true for house)' },
        windows: { type: 'boolean', description: 'Include windows (default: true for house)' },
        execute: { type: 'boolean', description: 'Execute immediately (default: true). Set to false to preview commands.' },
      },
      required: ['template', 'x', 'y', 'z'],
    },
  },
  {
    name: 'mc_build_llm',
    description: 'Use AI (DeepSeek LLM) to plan a building from natural language description. Generates a block-by-block plan and optionally executes it.',
    inputSchema: {
      type: 'object',
      properties: {
        description: { type: 'string', description: 'Natural language description, e.g. "a medieval watchtower with battlements and a flag on top"' },
        x: { type: 'number', description: 'Start X coordinate' },
        y: { type: 'number', description: 'Start Y coordinate' },
        z: { type: 'number', description: 'Start Z coordinate' },
        execute: { type: 'boolean', description: 'Execute the plan immediately (default: false). Always preview first!' },
      },
      required: ['description', 'x', 'y', 'z'],
    },
  },
  {
    name: 'mc_gui_click',
    description: 'Click a slot in the currently open GUI screen (for trading, moving items, etc.)',
    inputSchema: {
      type: 'object',
      properties: {
        slot: { type: 'number', description: 'Slot index to click' },
        button: { type: 'number', description: 'Mouse button (0=left, 1=right, default: 0)' },
        action: { type: 'string', description: 'Action: PICKUP, QUICK_MOVE, THROW, SWAP, CLONE (default: PICKUP)', enum: ['PICKUP', 'QUICK_MOVE', 'THROW', 'SWAP', 'CLONE'] },
      },
      required: ['slot'],
    },
  },
  {
    name: 'mc_trade',
    description: 'Execute a trade with a villager/wandering trader by recipe index. Must have a merchant screen open.',
    inputSchema: {
      type: 'object',
      properties: {
        index: { type: 'number', description: 'Trade recipe index (0-based)' },
      },
      required: ['index'],
    },
  },
  {
    name: 'mc_set_deepseek_key',
    description: 'Set the DeepSeek API key on the server side (used by mc_build_llm). Does NOT require a bridge connection.',
    inputSchema: {
      type: 'object',
      properties: {
        key: { type: 'string', description: 'DeepSeek API key (sk-...)' },
      },
      required: ['key'],
    },
  },
  {
    name: 'mc_run_script',
    description: 'Execute a script sequence of commands, waits, and chats on the Minecraft client. Supports command, chat, wait, and loop steps.',
    inputSchema: {
      type: 'object',
      properties: {
        steps: {
          type: 'array',
          description: 'Array of script steps to execute in sequence',
          items: {
            type: 'object',
            properties: {
              type: { type: 'string', enum: ['command', 'chat', 'wait', 'loop', 'condition', 'while', 'if', 'parallel'], description: 'Step type' },
              cmd: { type: 'string', description: 'Minecraft command without / (for type=command)' },
              msg: { type: 'string', description: 'Chat message (for type=chat)' },
              ms: { type: 'number', description: 'Wait duration in ms (for type=wait)' },
              count: { type: 'number', description: 'Loop count (for type=loop or steps with count)' },
              steps: { type: 'array', description: 'Enclosed steps (for type=loop/while)', items: { type: 'object' } },
              then: { type: 'array', description: 'If-true branch (for type=if)', items: { type: 'object' } },
              else: { type: 'array', description: 'If-false branch (for type=if)', items: { type: 'object' } },
              condition: { type: 'object', description: 'Condition object for while/if steps: {if_block?, if_slot?, if_distance?}' },
              if_block: { type: 'object', description: 'Block condition: {x, y, z, block?, not?}' },
              if_slot: { type: 'object', description: 'Slot condition: {slot, item?, not?}' },
              if_distance: { type: 'object', description: 'Distance condition: {x, y, z, max?, min?, not?}' },
              not: { type: 'boolean', description: 'Invert condition result' },
            },
          },
        },
      },
      required: ['steps'],
    },
  },
  {
    name: 'mc_mirror_build',
    description: 'Mirror one half of a building across a symmetry axis. Reads blocks from the source half and places mirrored blocks on the opposite side. Supports both odd width (center is a real block) and even width (center is between center1 and center2). Use after building one half of a symmetrical structure.',
    inputSchema: {
      type: 'object',
      properties: {
        axis: { type: 'string', description: 'Mirror axis: x, y, or z', enum: ['x', 'y', 'z'] },
        center: { type: 'number', description: 'Center block coordinate (for ODD width building — the symmetry axis passes through this block)' },
        center1: { type: 'number', description: 'First center coordinate (for EVEN width — the symmetry axis is BETWEEN center1 and center2)' },
        center2: { type: 'number', description: 'Second center coordinate (for EVEN width)' },
        source_min: { type: 'number', description: 'Minimum coordinate of the source half along the axis' },
        source_max: { type: 'number', description: 'Maximum coordinate of the source half along the axis' },
        y1: { type: 'number', description: 'Minimum Y bound' },
        y2: { type: 'number', description: 'Maximum Y bound' },
        z1: { type: 'number', description: 'Z bound (or X bound when axis=z)' },
        z2: { type: 'number', description: 'Z bound (or X bound when axis=z)' },
        x1: { type: 'number', description: 'Minimum X bound (only needed for axis=z or axis=y)' },
        x2: { type: 'number', description: 'Maximum X bound (only needed for axis=z or axis=y)' },
      },
      required: ['axis', 'source_min', 'source_max'],
    },
  },
  {
    name: 'mc_break_block',
    description: 'Break a block at the given coordinates (uses API, not command)',
    inputSchema: {
      type: 'object',
      properties: {
        x: { type: 'number', description: 'X coordinate' },
        y: { type: 'number', description: 'Y coordinate' },
        z: { type: 'number', description: 'Z coordinate' },
      },
      required: ['x', 'y', 'z'],
    },
  },
  {
    name: 'mc_place_block',
    description: 'Place a block at the given coordinates (uses /setblock)',
    inputSchema: {
      type: 'object',
      properties: {
        x: { type: 'number', description: 'X coordinate' },
        y: { type: 'number', description: 'Y coordinate' },
        z: { type: 'number', description: 'Z coordinate' },
        block: { type: 'string', description: 'Block ID, e.g. "minecraft:stone" or "stone"' },
      },
      required: ['x', 'y', 'z', 'block'],
    },
  },
  {
    name: 'mc_move_item',
    description: 'Move an item from one slot to another in the open container/inventory',
    inputSchema: {
      type: 'object',
      properties: {
        from: { type: 'number', description: 'Source slot index' },
        to: { type: 'number', description: 'Target slot index' },
        count: { type: 'number', description: 'Item count (default: all)' },
      },
      required: ['from', 'to'],
    },
  },
  {
    name: 'mc_drop_item',
    description: 'Drop an item from a slot in the open container/inventory',
    inputSchema: {
      type: 'object',
      properties: {
        slot: { type: 'number', description: 'Slot index to drop from' },
      },
      required: ['slot'],
    },
  },
  {
    name: 'mc_equip_item',
    description: 'Equip an item from inventory to an equipment slot (head/chest/legs/feet/offhand/mainhand)',
    inputSchema: {
      type: 'object',
      properties: {
        slot: { type: 'number', description: 'Inventory slot containing the item' },
        equipment_slot: { type: 'string', description: 'Target: head/chest/legs/feet/offhand (default: mainhand)' },
      },
      required: ['slot'],
    },
  },
  {
    name: 'mc_get_bossbar',
    description: 'Get active boss bars (health, color, overlay, darkenSky, thickenFog, dragonMusic)',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_get_advancements',
    description: 'Get all advancements and their progress status',
    inputSchema: {
      type: 'object',
      properties: {
        only_done: { type: 'boolean', description: 'Only show done/earned advancements (default: false)' },
      },
    },
  },
  {
    name: 'mc_get_weather',
    description: 'Get current weather state (raining, thundering, rain/thunder gradient)',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_get_gamemode',
    description: 'Get current game mode (survival, creative, adventure, spectator)',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_get_xp',
    description: 'Get player experience level, progress bar, and total experience',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_get_time',
    description: 'Get world time of day, 24h format, and day count',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_hotbar_select',
    description: 'Switch held item by hotbar slot index (0-8)',
    inputSchema: {
      type: 'object',
      properties: {
        slot: { type: 'number', description: 'Hotbar slot index (0-8)' },
      },
      required: ['slot'],
    },
  },
  {
    name: 'mc_get_recipes',
    description: 'Get all recipes with unlock/display status from the player recipe book',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_get_recipe_for_item',
    description: 'Search unlocked recipes by output item name, returns ingredients and output count',
    inputSchema: {
      type: 'object',
      properties: { item: { type: 'string', description: 'Item name or ID to search for' } },
      required: ['item'],
    },
  },
  {
    name: 'mc_get_light_level',
    description: 'Get light level at a position (defaults to player position)',
    inputSchema: {
      type: 'object',
      properties: {
        x: { type: 'number', description: 'X coordinate (default: player X)' },
        y: { type: 'number', description: 'Y coordinate (default: player Y)' },
        z: { type: 'number', description: 'Z coordinate (default: player Z)' },
      },
    },
  },
  {
    name: 'mc_get_scoreboard',
    description: 'Get the world scoreboard: objectives and teams',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_get_seed',
    description: 'Get the world seed (requires OP)',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_summon_entity',
    description: 'Summon an entity at coordinates (uses /summon). Requires OP.',
    inputSchema: {
      type: 'object',
      properties: {
        entity: { type: 'string', description: 'Entity type, e.g. "minecraft:pig" or "pig"' },
        x: { type: 'number', description: 'X coordinate' },
        y: { type: 'number', description: 'Y coordinate' },
        z: { type: 'number', description: 'Z coordinate' },
        nbt: { type: 'string', description: 'Optional NBT tag, e.g. "{CustomName:\\"\\"Gerald\\"\\"}"' },
      },
      required: ['entity', 'x', 'y', 'z'],
    },
  },
  {
    name: 'mc_get_biome',
    description: 'Get biome info at coordinates (or current player position)',
    inputSchema: {
      type: 'object',
      properties: {
        x: { type: 'number', description: 'X coordinate (omit for player position)' },
        y: { type: 'number', description: 'Y coordinate' },
        z: { type: 'number', description: 'Z coordinate' },
      },
    },
  },
  {
    name: 'mc_locate_structure',
    description: 'Locate the nearest structure of a given type. Sends /locate structure command; result appears in chat.',
    inputSchema: {
      type: 'object',
      properties: { structure: { type: 'string', description: 'Structure ID, e.g. "village", "fortress", "mansion"' } },
      required: ['structure'],
    },
  },
  {
    name: 'mc_locate_biome',
    description: 'Locate the nearest biome of a given type. Sends /locatebiome command; result appears in chat.',
    inputSchema: {
      type: 'object',
      properties: { biome: { type: 'string', description: 'Biome ID, e.g. "desert", "plains", "jungle"' } },
      required: ['biome'],
    },
  },
  {
    name: 'mc_find_blocks',
    description: 'Search for blocks of specific types in the world by name or block ID. Supports single (block) or multiple (blocks array) queries. Returns all matching block positions.',
    inputSchema: {
      type: 'object',
      properties: {
        block: { type: 'string', description: 'Single block name or ID to search for (e.g., "spruce_log", "minecraft:spruce_log"). Use blocks[] for multi-type search.' },
        blocks: { type: 'array', items: { type: 'string' }, description: 'Multiple block names/IDs to search for in one pass (e.g., ["diamond_ore", "iron_ore"])' },
        radius: { type: 'number', description: 'Search radius from the player (default: 32). Use this OR explicit bounds.' },
        x1: { type: 'number', description: 'Bounding box min X (use with x2,y1,y2,z1,z2 instead of radius)' },
        x2: { type: 'number', description: 'Bounding box max X' },
        y1: { type: 'number', description: 'Bounding box min Y' },
        y2: { type: 'number', description: 'Bounding box max Y' },
        z1: { type: 'number', description: 'Bounding box min Z' },
        z2: { type: 'number', description: 'Bounding box max Z' },
      },
    },
  },
  {
    name: 'mc_find_slime_chunks',
    description: 'Check if a chunk is a slime chunk. If seed is not provided, fetches via /seed first.',
    inputSchema: {
      type: 'object',
      properties: {
        cx: { type: 'number', description: 'Chunk X coordinate' },
        cz: { type: 'number', description: 'Chunk Z coordinate' },
        radius: { type: 'number', description: 'Search radius in chunks (default: 0 = just this chunk)' },
        seed: { type: 'number', description: 'World seed (optional, fetched via /seed if not provided)' },
      },
      required: ['cx', 'cz'],
    },
  },
  {
    name: 'mc_find_spawners',
    description: 'Search for mob spawner blocks in a radius around the player',
    inputSchema: {
      type: 'object',
      properties: {
        radius: { type: 'number', description: 'Search radius in blocks (default: 32)' },
      },
    },
  },
  {
    name: 'mc_find_item',
    description: 'Search the player inventory for items matching a name. Optionally also search the open container.',
    inputSchema: {
      type: 'object',
      properties: {
        name: { type: 'string', description: 'Item name or ID to search for (case-insensitive substring match)' },
        container: { type: 'boolean', description: 'Also search the open container (default: false)' },
      },
      required: ['name'],
    },
  },
  {
    name: 'mc_relationship_graph',
    description: 'Generate a relationship graph (mermaid.js diagram) from tracked player data showing alliances, rivalries, and trust networks',
    inputSchema: {
      type: 'object',
      properties: {
        names: { type: 'string', description: 'Comma-separated player names (default: all tracked)' },
        format: { type: 'string', enum: ['mermaid', 'json'], description: 'Output format (default: mermaid)' },
      },
    },
  },
  {
    name: 'mc_enchant_simulate',
    description: 'Get optimal enchanting strategy. Analyzes inventory, available recipes, and XP to suggest the best enchantment path using DeepSeek AI.',
    inputSchema: {
      type: 'object',
      properties: {
        target: { type: 'string', description: 'Target item or enchantment goal (e.g., "sharpness V diamond sword")' },
        max_xp: { type: 'number', description: 'Maximum XP levels to spend' },
        strategy: { type: 'string', enum: ['best', 'cheapest', 'balanced'], description: 'Strategy (default: balanced)' },
      },
    },
  },
  {
    name: 'mc_get_player_effects',
    description: 'Get the player\'s active status effects (potion effects, beacon effects, etc.) with duration, amplifier, and icon info',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_get_statistics',
    description: 'Get player statistics: distances traveled, jumps, deaths, kills, damage, etc. from the stat tracker',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_get_sign',
    description: 'Read the text from a sign block at the given coordinates or the one being looked at',
    inputSchema: {
      type: 'object',
      properties: {
        x: { type: 'number', description: 'X coordinate (omit to use look)' },
        y: { type: 'number', description: 'Y coordinate' },
        z: { type: 'number', description: 'Z coordinate' },
        look: { type: 'boolean', description: 'Use the block you are looking at (set to true)' },
      },
    },
  },
  {
    name: 'mc_get_world_border',
    description: 'Get the world border info: center, size, damage, and warnings',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_get_player_abilities',
    description: 'Get player abilities: creative mode, flying, allow flight, invulnerable, walk/fly speed',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_get_last_death',
    description: 'Get the player\'s last death position (if available in this world)',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_press_key',
    description: 'Press or release a keyboard key. Supports named keys (forward/jump/use/attack/sneak/sprint/etc.), raw keys (key.keyboard.w, key.keyboard.space), hotbar slots (hotbar_1/slot_1 through slot_9), and any registered key binding.',
    inputSchema: {
      type: 'object',
      properties: {
        key: { type: 'string', description: 'Key to press: forward, back, left, right, jump, sneak, sprint, attack, use, drop, inventory, chat, command, screenshot, hotbar_1, key.keyboard.w, etc.' },
        state: { type: 'boolean', description: 'true = press, false = release (default: true). Use with duration instead for timed holds.' },
        duration: { type: 'number', description: 'Hold duration in milliseconds. Presses for this long then auto-releases.' },
      },
      required: ['key'],
    },
  },
  {
    name: 'mc_use_item',
    description: 'Use the currently held item (right-click). Wrapper around press_key(key="use", duration=100).',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_walk_to',
    description: 'Walk the player toward target coordinates. Uses Baritone #goto if available (recommended), otherwise falls back to custom key-press walking. For mining by block type, use mc_baritone with cmd="mine diamond_ore".',
    inputSchema: {
      type: 'object',
      properties: {
        x: { type: 'number', description: 'Target X coordinate' },
        y: { type: 'number', description: 'Target Y coordinate' },
        z: { type: 'number', description: 'Target Z coordinate' },
        threshold: { type: 'number', description: 'Stop distance (default: 1.5)' },
        sprint: { type: 'boolean', description: 'Hold sprint key (default: false)' },
      },
      required: ['x', 'y', 'z'],
    },
  },
  {
    name: 'mc_get_chunk',
    description: 'Get chunk data: surface heightmap samples and biomes for a chunk at given chunk coordinates',
    inputSchema: {
      type: 'object',
      properties: {
        x: { type: 'number', description: 'Chunk X coordinate (in chunk coords, not block coords)' },
        z: { type: 'number', description: 'Chunk Z coordinate' },
      },
      required: ['x', 'z'],
    },
  },
  {
    name: 'mc_auto_fish',
    description: 'Auto-fish: automatically cast, detect bites, and reel in. Start/stop/status.',
    inputSchema: {
      type: 'object',
      properties: {
        action: { type: 'string', enum: ['start', 'stop', 'status'], description: 'Action (default: status)' },
      },
    },
  },
  {
    name: 'mc_screenshot_repeat',
    description: 'Take screenshots at regular intervals. Start/stop/status.',
    inputSchema: {
      type: 'object',
      properties: {
        action: { type: 'string', enum: ['start', 'stop', 'status'], description: 'Action (default: status)' },
        interval: { type: 'number', description: 'Interval in seconds (default: 60)' },
        count: { type: 'number', description: 'Number of screenshots to take (default: 0 = unlimited)' },
      },
    },
  },
  {
    name: 'mc_build_geometry',
    description: 'Build a geometric shape (cylinder, sphere, dome) using /setblock commands',
    inputSchema: {
      type: 'object',
      properties: {
        shape: { type: 'string', enum: ['cylinder', 'sphere', 'dome'], description: 'Shape type' },
        x: { type: 'number', description: 'Center X coordinate' },
        y: { type: 'number', description: 'Base Y coordinate' },
        z: { type: 'number', description: 'Center Z coordinate' },
        radius: { type: 'number', description: 'Radius in blocks' },
        height: { type: 'number', description: 'Height (for cylinder/dome; default: radius)' },
        material: { type: 'string', description: 'Block material (default: minecraft:stone_bricks)' },
        hollow: { type: 'boolean', description: 'Hollow (default: false)' },
        execute: { type: 'boolean', description: 'Execute immediately (default: true)' },
      },
      required: ['shape', 'x', 'y', 'z', 'radius'],
    },
  },
  {
    name: 'mc_undo_build',
    description: 'Undo the last build action (build_template, build_geometry, or build_llm). Fills the bounding box with air.',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_set_reconnect',
    description: 'Configure auto-reconnect behavior (delay, max attempts). Persists until changed.',
    inputSchema: {
      type: 'object',
      properties: {
        interval: { type: 'number', description: 'Reconnect delay in ms (default: 3000)' },
        max_attempts: { type: 'number', description: 'Max reconnect attempts (default: 20)' },
      },
    },
  },
  {
    name: 'mc_find_villager',
    description: 'Find nearby villagers with positions and distances',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_sort_inventory',
    description: 'Preview sorted inventory view. Use mc_move_item + mc_gui_click to physically sort.',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_refill',
    description: 'Find matching items in inventory to refill hotbar slots below threshold',
    inputSchema: {
      type: 'object',
      properties: {
        threshold: { type: 'number', description: 'Min item count before refilling (default: 16)' },
        container: { type: 'boolean', description: 'Search container too (default: false)' },
      },
    },
  },
  {
    name: 'mc_craft_item',
    description: 'Get crafting guidance. Opens crafting table and check available ingredients.',
    inputSchema: {
      type: 'object',
      properties: {
        item: { type: 'string', description: 'Item name/ID to craft' },
        count: { type: 'number', description: 'How many to craft (default: 1)' },
      },
      required: ['item'],
    },
  },
  {
    name: 'mc_scan_terrain',
    description: 'Scan surrounding terrain and return block type counts summary. Optionally get AI building recommendations.',
    inputSchema: {
      type: 'object',
      properties: {
        radius: { type: 'number', description: 'Scan radius in blocks (default: 16)' },
        yRange: { type: 'number', description: 'Vertical range (default: 8)' },
        ai_suggest: { type: 'boolean', description: 'Include AI building recommendations based on terrain (default: false)' },
      },
    },
  },
  {
    name: 'mc_explain_screen',
    description: 'Get a human-readable explanation of the currently open GUI screen and how to use it',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_analyze_inventory',
    description: 'Analyze inventory contents: categorization, valuable items, free space summary',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_batch_build',
    description: 'Optimize a list of /setblock commands by merging contiguous same-material blocks into /fill commands',
    inputSchema: {
      type: 'object',
      properties: {
        commands: {
          type: 'array',
          items: { type: 'string' },
          description: 'Array of /setblock commands to optimize',
        },
      },
      required: ['commands'],
    },
  },
  {
    name: 'mc_entity_highlight',
    description: 'Search for entities by name/type and return positions for highlighting with mc_highlight_block. Optionally auto-highlights entities in-game.',
    inputSchema: {
      type: 'object',
      properties: {
        type: { type: 'string', description: 'Entity type/name to search for' },
        radius: { type: 'number', description: 'Search radius (default: 32)' },
        max: { type: 'number', description: 'Max results (default: 20)' },
        auto_highlight: { type: 'boolean', description: 'Auto-highlight found entities in-game with red outlines (default: false)' },
      },
    },
  },
  {
    name: 'mc_damage_display',
    description: 'Get detailed damage/combat info about an entity: health, armor, attack damage, effects',
    inputSchema: {
      type: 'object',
      properties: {
        id: { type: 'number', description: 'Entity ID to query' },
      },
      required: ['id'],
    },
  },
  {
    name: 'mc_tps',
    description: 'Estimate server TPS using client-side tick timing',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_reach',
    description: 'Get distance to the block or entity being looked at (crosshair target)',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_ping_info',
    description: 'Get player ping values from tab list (self, average, min, max)',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_packet_logger',
    description: 'Start/stop packet logging (C2S type + frequency stats)',
    inputSchema: {
      type: 'object',
      properties: {
        action: { type: 'string', enum: ['start', 'stop', 'status'], description: 'Action' },
      },
      required: ['action'],
    },
  },
  {
    name: 'mc_packet_logger_detail',
    description: 'Start/stop detailed packet logging with full content capture (filter by packet name)',
    inputSchema: {
      type: 'object',
      properties: {
        action: { type: 'string', enum: ['start', 'stop', 'status'], description: 'Action' },
        filter: { type: 'string', description: 'Packet name filter (optional)' },
      },
      required: ['action'],
    },
  },
  {
    name: 'mc_packet_logger_find',
    description: 'Search recorded packet log for specific packet types or content',
    inputSchema: {
      type: 'object',
      properties: {
        query: { type: 'string', description: 'Search query (packet name or content)' },
        direction: { type: 'string', enum: ['C2S', 'S2C', ''], description: 'Packet direction' },
        limit: { type: 'number', description: 'Max results (default: 20)' },
      },
      required: ['query'],
    },
  },
  {
    name: 'mc_bedrock_breaker',
    description: 'Attempt to break unbreakable blocks via special packet sequence. WARNING - may trigger anti-cheat',
    inputSchema: {
      type: 'object',
      properties: {
        x: { type: 'number', description: 'X coordinate' },
        y: { type: 'number', description: 'Y coordinate' },
        z: { type: 'number', description: 'Z coordinate' },
        attempts: { type: 'number', description: 'Number of attempts (default: 5)' },
      },
      required: ['x', 'y', 'z'],
    },
  },
  {
    name: 'mc_scan_containers',
    description: 'Scan loaded chunks for containers (chests, barrels, hoppers, shulker boxes)',
    inputSchema: {
      type: 'object',
      properties: {
        radius: { type: 'number', description: 'Scan radius (default: 32)' },
      },
    },
  },
  {
    name: 'mc_shulker_peek',
    description: 'Preview shulker box contents from inventory without placing them',
    inputSchema: {
      type: 'object',
      properties: {
        slot: { type: 'number', description: 'Specific slot to inspect (default: all shulkers)' },
      },
    },
  },
  {
    name: 'mc_waypoints',
    description: 'Manage coordinate waypoints: list, add, remove, or goto',
    inputSchema: {
      type: 'object',
      properties: {
        action: { type: 'string', enum: ['list', 'add', 'remove', 'goto'], description: 'Action' },
        name: { type: 'string', description: 'Waypoint name' },
        x: { type: 'number', description: 'X coordinate' },
        y: { type: 'number', description: 'Y coordinate' },
        z: { type: 'number', description: 'Z coordinate' },
      },
      required: ['action'],
    },
  },
  {
    name: 'mc_travel_log',
    description: 'Background position logging every 30s. Start/stop/get path',
    inputSchema: {
      type: 'object',
      properties: {
        action: { type: 'string', enum: ['start', 'stop', 'get'], description: 'Action' },
        limit: { type: 'number', description: 'Max entries (default: 100)' },
      },
      required: ['action'],
    },
  },
  {
    name: 'mc_scan_crops',
    description: 'Scan for mature crops and nether wart in radius',
    inputSchema: {
      type: 'object',
      properties: {
        radius: { type: 'number', description: 'Scan radius (default: 16)' },
      },
    },
  },
  {
    name: 'mc_block_counter',
    description: 'Count all block types in a chunk with frequency and percentage',
    inputSchema: {
      type: 'object',
      properties: {
        cx: { type: 'number', description: 'Chunk X (default: current)' },
        cz: { type: 'number', description: 'Chunk Z (default: current)' },
      },
    },
  },
  {
    name: 'mc_get_player_profile',
    description: 'Get raw player profile data (chat history, online sessions, known positions, interaction stats)',
    inputSchema: {
      type: 'object',
      properties: {
        name: { type: 'string', description: 'Player name (default: self)' },
      },
    },
  },
  {
    name: 'mc_analyze_player_profile',
    description: 'AI analysis of a player\'s psychological profile from tracked data (personality, behavior patterns, communication style, trust level, potential vulnerabilities)',
    inputSchema: {
      type: 'object',
      properties: {
        name: { type: 'string', description: 'Player name to analyze' },
        focus: { type: 'string', description: 'Analysis focus: personality/behavior/communication/trust/vulnerability' },
      },
      required: ['name'],
    },
  },
  {
    name: 'mc_learn_my_style',
    description: 'AI analyzes your own chat messages to understand your communication style, mannerisms, and phrasing patterns',
    inputSchema: {
      type: 'object',
      properties: {
        messages: { type: 'number', description: 'Number of recent outgoing messages to analyze (default: 50)' },
      },
    },
  },
  {
    name: 'mc_generate_message',
    description: 'Generate a strategic message optimized for influencing a specific player based on their psychological profile',
    inputSchema: {
      type: 'object',
      properties: {
        goal: { type: 'string', description: 'What you want to achieve' },
        target: { type: 'string', description: 'Target player name' },
        tone: { type: 'string', description: 'Tone: friendly/neutral/formal/playful/assertive' },
        context: { type: 'string', description: 'Additional context about the situation' },
      },
      required: ['goal', 'target'],
    },
  },
  {
    name: 'mc_analyze_relationships',
    description: 'AI analyzes social relationships between tracked players: alliances, rivalries, trust networks, influence dynamics',
    inputSchema: {
      type: 'object',
      properties: {
        names: { type: 'string', description: 'Comma-separated player names to analyze (default: all tracked)' },
      },
    },
  },
  {
    name: 'mc_analyze_chat_stream',
    description: 'AI analysis of recent chat stream for sentiment, emerging topics, social tensions, and influence opportunities',
    inputSchema: {
      type: 'object',
      properties: {
        duration: { type: 'number', description: 'Minutes of recent chat to analyze (default: 10)' },
        focus: { type: 'string', description: 'Focus: sentiment/topics/tensions/opportunities' },
      },
    },
  },
  {
    name: 'mc_analyze_chat_sentiment',
    description: 'Analyze the sentiment of recent chat messages. Uses DeepSeek to detect mood, tension, and topics in the conversation.',
    inputSchema: {
      type: 'object',
      properties: {
        count: { type: 'number', description: 'Number of recent messages to analyze (default: 20)' },
        player: { type: 'string', description: 'Filter by specific player name' },
      },
    },
  },
  {
    name: 'mc_simulate_outcome',
    description: 'AI simulates likely outcomes of a planned message or action, considering target psychology and relationship context',
    inputSchema: {
      type: 'object',
      properties: {
        message: { type: 'string', description: 'The message or action you plan to take' },
        target: { type: 'string', description: 'Target player name' },
        context: { type: 'string', description: 'Background context for simulation' },
      },
      required: ['message', 'target'],
    },
  },
  {
    name: 'mc_analyze_current_server',
    description: 'Analyze the current server using MOTD+scoreboard. If unknown, AI identifies brand and network type. Returns brand info.',
    inputSchema: {
      type: 'object',
      properties: {
        address: { type: 'string', description: 'Auto-detected; omit to use current' },
        motd: { type: 'string', description: 'Auto-detected; omit to use current' },
      },
    },
  },
  {
    name: 'mc_list_known_servers',
    description: 'List all known servers with their brand, network type, and first/last seen timestamps',
    inputSchema: {
      type: 'object',
      properties: {},
    },
  },
  {
    name: 'mc_auto_explore',
    description: 'Autonomous exploration: walk in a spiral pattern, scan biomes, and record discoveries. Start/stop/status.',
    inputSchema: {
      type: 'object',
      properties: {
        action: { type: 'string', enum: ['start', 'stop', 'status', 'data'], description: 'Action' },
        radius: { type: 'number', description: 'Max radius in blocks (default: 100)' },
        mode: { type: 'string', enum: ['spiral'], description: 'Exploration pattern (default: spiral)' },
      },
      required: ['action'],
    },
  },
  {
    name: 'mc_save_memory',
    description: 'Save a long-term memory (event, observation, player note, location, etc.) that persists across sessions',
    inputSchema: {
      type: 'object',
      properties: {
        content: { type: 'string', description: 'Memory content' },
        category: { type: 'string', enum: ['event', 'location', 'player', 'observation', 'note'], description: 'Category' },
        importance: { type: 'number', description: 'Importance 1-5' },
        tags: { type: 'string', description: 'Comma-separated tags' },
      },
      required: ['content'],
    },
  },
  {
    name: 'mc_recall_memory',
    description: 'Search and recall saved memories by query text or category',
    inputSchema: {
      type: 'object',
      properties: {
        query: { type: 'string', description: 'Search query (optional)' },
        category: { type: 'string', description: 'Filter by category (optional)' },
        limit: { type: 'number', description: 'Max results (default: 20)' },
      },
    },
  },
  {
    name: 'mc_list_memories',
    description: 'List all saved memories, optionally filtered by category',
    inputSchema: {
      type: 'object',
      properties: {
        category: { type: 'string', description: 'Filter by category (optional)' },
        limit: { type: 'number', description: 'Max results (default: 50)' },
      },
    },
  },
  {
    name: 'mc_forget_memory',
    description: 'Delete a memory by its ID',
    inputSchema: {
      type: 'object',
      properties: {
        id: { type: 'string', description: 'Memory ID to delete' },
      },
      required: ['id'],
    },
  },
  {
    name: 'mc_memory_near',
    description: 'Find memories near a position (coordinate-based search)',
    inputSchema: {
      type: 'object',
      properties: {
        x: { type: 'number', description: 'Center X' },
        y: { type: 'number', description: 'Center Y' },
        z: { type: 'number', description: 'Center Z' },
        radius: { type: 'number', description: 'Search radius in blocks (default: 32)' },
        limit: { type: 'number', description: 'Max results (default: 20)' },
      },
      required: ['x', 'y', 'z'],
    },
  },
  {
    name: 'mc_auto_trade',
    description: 'Automatically trade with the nearest villager. Finds villager, walks to them, opens GUI, and executes profitable trades in a loop.',
    inputSchema: {
      type: 'object',
      properties: {
        index: { type: 'number', description: 'Trade recipe index to repeat (default: 0 = first trade, -1 = auto-detect best)' },
        count: { type: 'number', description: 'How many trades to execute (default: as many as possible)' },
        villager_name: { type: 'string', description: 'Target villager name (optional, auto-finds nearest if omitted)' },
      },
    },
  },
  {
    name: 'mc_auto_farm',
    description: 'Automated crop management: scan for mature crops, harvest, and replant. Uses mc_scan_crops and mc_break_block.',
    inputSchema: {
      type: 'object',
      properties: {
        radius: { type: 'number', description: 'Scan radius in blocks (default: 16)' },
        action: { type: 'string', enum: ['scan', 'harvest', 'full'], description: 'Action (default: full)' },
        crop: { type: 'string', description: 'Specific crop type (e.g., "wheat", "carrots"). Default: all' },
      },
    },
  },
  {
    name: 'mc_attack_entity',
    description: 'Attack an entity by ID',
    inputSchema: {
      type: 'object',
      properties: {
        id: { type: 'number', description: 'Entity ID to attack' },
      },
      required: ['id'],
    },
  },
  {
    name: 'mc_interact_entity',
    description: 'Right-click interact with an entity (e.g., open villager GUI, ride boat)',
    inputSchema: {
      type: 'object',
      properties: {
        id: { type: 'number', description: 'Entity ID to interact with' },
        hand: { type: 'string', enum: ['main', 'offhand'], description: 'Hand to use (default: main)' },
      },
      required: ['id'],
    },
  },
  {
    name: 'mc_ride_entity',
    description: 'Mount or dismount an entity (ride boat/minecart/horse, dismount if already riding)',
    inputSchema: {
      type: 'object',
      properties: {
        id: { type: 'number', description: 'Entity ID to ride. Omit to dismount.' },
      },
    },
  },
  {
    name: 'mc_memory_export',
    description: 'Export all memories as JSON or CSV for backup/analysis',
    inputSchema: {
      type: 'object',
      properties: {
        format: { type: 'string', enum: ['json', 'csv'], description: 'Export format (default: json)' },
      },
    },
  },
  {
    name: 'mc_waypoint_export',
    description: 'Export all waypoints as JSON for backup or sharing',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_waypoint_import',
    description: 'Import waypoints from JSON, merging or replacing existing',
    inputSchema: {
      type: 'object',
      properties: {
        waypoints: { type: 'string', description: 'JSON string of waypoints array to import' },
        merge: { type: 'boolean', description: 'Merge with existing (default: false, replaces all)' },
      },
      required: ['waypoints'],
    },
  },
  {
    name: 'mc_travel_log_stats',
    description: 'Get statistics from travel log: total distance, dimensions visited, entry count',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_comparator_read',
    description: 'Read the signal strength of a redstone comparator block',
    inputSchema: {
      type: 'object',
      properties: {
        x: { type: 'number', description: 'X coordinate' },
        y: { type: 'number', description: 'Y coordinate' },
        z: { type: 'number', description: 'Z coordinate' },
      },
      required: ['x', 'y', 'z'],
    },
  },
  {
    name: 'mc_toggle_block',
    description: 'Right-click a block to toggle it (lever, button, piston, etc.)',
    inputSchema: {
      type: 'object',
      properties: {
        x: { type: 'number', description: 'X coordinate' },
        y: { type: 'number', description: 'Y coordinate' },
        z: { type: 'number', description: 'Z coordinate' },
      },
      required: ['x', 'y', 'z'],
    },
  },
  {
    name: 'mc_analyze_terrain_heightmap',
    description: 'Analyze terrain heightmap: min/max/avg elevation, steepest slopes, flat areas from chunk data',
    inputSchema: {
      type: 'object',
      properties: {
        radius: { type: 'number', description: 'Scan radius in chunks (default: 3)' },
      },
    },
  },
  {
    name: 'mc_set_model',
    description: 'Switch between AI models (DeepSeek/OpenAI/Claude) for chat and analysis operations',
    inputSchema: {
      type: 'object',
      properties: {
        provider: { type: 'string', enum: ['deepseek', 'openai', 'claude'], description: 'AI provider' },
        api_key: { type: 'string', description: 'API key for the provider' },
        model: { type: 'string', description: 'Model name (optional, uses provider default)' },
      },
      required: ['provider', 'api_key'],
    },
  },
  {
    name: 'mc_set_discord_webhook',
    description: 'Configure Discord webhook or bidirectional bot bridging. Use action=set to save webhook (Minecraft→Discord). Use action=start with bot_token+channel_id to start bidirectional polling (Discord→Minecraft).',
    inputSchema: {
      type: 'object',
      properties: {
        url: { type: 'string', description: 'Discord webhook URL (required for action=set)' },
        bridge_chat: { type: 'boolean', description: 'Bridge chat messages (default: true)' },
        bot_token: { type: 'string', description: 'Discord bot token for bidirectional bridging' },
        channel_id: { type: 'string', description: 'Discord channel ID to bridge' },
        action: { type: 'string', enum: ['set', 'status', 'start', 'stop'], description: 'Action (default: set)' },
      },
    },
  },
  {
    name: 'mc_auto_brew',
    description: 'Automated potion brewing: check brewing stand status, add ingredients, start brewing',
    inputSchema: {
      type: 'object',
      properties: {
        action: { type: 'string', enum: ['status', 'brew', 'collect'], description: 'Action (default: status)' },
        ingredient: { type: 'string', description: 'Item name/ID for ingredient (e.g., "nether_wart", "glowstone_dust")' },
        slot: { type: 'number', description: 'Ingredient slot (0-3, default: 0)' },
      },
      required: ['action'],
    },
  },
  {
    name: 'mc_auto_cook',
    description: 'Automated cooking/smelting: check furnace status, add fuel/input, collect results',
    inputSchema: {
      type: 'object',
      properties: {
        action: { type: 'string', enum: ['status', 'cook', 'collect'], description: 'Action (default: status)' },
        fuel: { type: 'string', description: 'Fuel item name/ID (e.g., "coal", "charcoal", "planks")' },
        input: { type: 'string', description: 'Item name/ID to cook/smelt' },
        count: { type: 'number', description: 'How many to cook (default: all available)' },
      },
      required: ['action'],
    },
  },
  {
    name: 'mc_entity_selector',
    description: 'Filter nearby entities by type, distance, and count limits',
    inputSchema: {
      type: 'object',
      properties: {
        type: { type: 'string', description: 'Entity type/name filter (e.g., "zombie", "sheep", "item")' },
        min_distance: { type: 'number', description: 'Minimum distance in blocks' },
        max_distance: { type: 'number', description: 'Maximum distance in blocks' },
        limit: { type: 'number', description: 'Max results (default: 50)' },
      },
    },
  },
  {
    name: 'mc_kill_all',
    description: 'Attack all entities of a given type within radius using mc_attack_entity',
    inputSchema: {
      type: 'object',
      properties: {
        type: { type: 'string', description: 'Entity type to kill (e.g., "zombie", "creeper")' },
        radius: { type: 'number', description: 'Search radius (default: 16)' },
        max: { type: 'number', description: 'Max entities to kill (default: 10)' },
      },
      required: ['type'],
    },
  },
  {
    name: 'mc_nearest_structure',
    description: 'Use /locate to find the nearest structure and parse coordinates from the result',
    inputSchema: {
      type: 'object',
      properties: {
        structure: { type: 'string', description: 'Structure ID, e.g. "village", "fortress", "mansion"' },
      },
      required: ['structure'],
    },
  },
  {
    name: 'mc_session_report',
    description: 'Generate a comprehensive session report with stats, chat summary, memories, and optional AI analysis',
    inputSchema: {
      type: 'object',
      properties: {
        include_chat: { type: 'boolean', description: 'Include recent chat (default: true)' },
        include_stats: { type: 'boolean', description: 'Include statistics (default: true)' },
        include_inventory: { type: 'boolean', description: 'Include inventory (default: false)' },
        include_memories: { type: 'boolean', description: 'Include session memories (default: true)' },
        ai_summary: { type: 'boolean', description: 'Generate AI summary using DeepSeek (default: false)' },
      },
    },
  },
  {
    name: 'mc_set_alias',
    description: 'Create, update, or remove a command alias (shortcut for longer commands)',
    inputSchema: {
      type: 'object',
      properties: {
        alias: { type: 'string', description: 'Short alias name (e.g., "home", "spawn")' },
        command: { type: 'string', description: 'Command or message (e.g., "tp 0 64 0")' },
        remove: { type: 'boolean', description: 'Remove this alias (default: false)' },
      },
      required: ['alias', 'command'],
    },
  },
  {
    name: 'mc_aliases',
    description: 'List all saved command aliases',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_memory_dedup',
    description: 'Find and merge/remove duplicate memories (same content or same location)',
    inputSchema: {
      type: 'object',
      properties: {
        action: { type: 'string', enum: ['scan', 'remove'], description: 'scan: find duplicates; remove: auto-deduplicate' },
        strategy: { type: 'string', enum: ['content', 'location'], description: 'Dedup strategy (default: content)' },
      },
      required: ['action'],
    },
  },
  {
    name: 'mc_entity_density_map',
    description: 'Create a density heatmap of entities across loaded chunks',
    inputSchema: { type: 'object', properties: {
      radius: { type: 'number', description: 'Scan radius in chunks (default: 3)' },
      type: { type: 'string', description: 'Filter by entity type' },
    } },
  },
  {
    name: 'mc_ore_distribution',
    description: 'Analyze ore distribution by Y level in nearby chunks',
    inputSchema: { type: 'object', properties: {
      ores: { type: 'array', items: { type: 'string' }, description: 'Ore types to scan (default: all major ores)' },
      radius: { type: 'number', description: 'Scan radius in blocks (default: 16)' },
    } },
  },
  {
    name: 'mc_screenshot_analyze',
    description: 'Take a screenshot and analyze its contents using AI',
    inputSchema: { type: 'object', properties: {
      question: { type: 'string', description: 'Specific question about what to look for' },
    } },
  },
  {
    name: 'mc_entity_track',
    description: 'Track an entity\'s movement over time (periodic position checks)',
    inputSchema: { type: 'object', properties: {
      id: { type: 'number', description: 'Entity ID to track' },
      duration: { type: 'number', description: 'Duration in seconds (default: 10)' },
      interval: { type: 'number', description: 'Polling interval in seconds (default: 1)' },
    }, required: ['id'] },
  },
  {
    name: 'mc_auto_shear',
    description: 'Automatically shear nearby sheep (find sheep, use shears, interact)',
    inputSchema: { type: 'object', properties: {
      radius: { type: 'number', description: 'Search radius (default: 10)' },
      max: { type: 'number', description: 'Max sheep to shear (default: 5)' },
    } },
  },
  {
    name: 'mc_spawn_particle',
    description: 'Spawn particles at a location for visual effects and markers',
    inputSchema: { type: 'object', properties: {
      particle: { type: 'string', description: 'Particle ID (e.g., "minecraft:flame", "minecraft:heart")' },
      x: { type: 'number' }, y: { type: 'number' }, z: { type: 'number' },
      vx: { type: 'number', description: 'Velocity X (default: 0)' },
      vy: { type: 'number', description: 'Velocity Y (default: 0.1)' },
      vz: { type: 'number', description: 'Velocity Z (default: 0)' },
      count: { type: 'number', description: 'Particle count (default: 10)' },
    }, required: ['particle', 'x', 'y', 'z'] },
  },
  {
    name: 'mc_play_sound',
    description: 'Play a sound effect at a position or at the player',
    inputSchema: { type: 'object', properties: {
      sound: { type: 'string', description: 'Sound ID (e.g., "minecraft:entity.experience_orb.pickup")' },
      x: { type: 'number' }, y: { type: 'number' }, z: { type: 'number' },
      volume: { type: 'number', description: 'Volume (default: 1.0)' },
      pitch: { type: 'number', description: 'Pitch (default: 1.0)' },
    }, required: ['sound'] },
  },
  {
    name: 'mc_display_title',
    description: 'Display a title on screen with optional subtitle and timing',
    inputSchema: { type: 'object', properties: {
      title: { type: 'string', description: 'Main title text' },
      subtitle: { type: 'string', description: 'Subtitle text' },
      fadeIn: { type: 'number', description: 'Fade-in ticks (default: 10)' },
      stay: { type: 'number', description: 'Stay ticks (default: 40)' },
      fadeOut: { type: 'number', description: 'Fade-out ticks (default: 10)' },
    }, required: ['title'] },
  },
  {
    name: 'mc_config_reload',
    description: 'Reload all config files from disk (bridge, auto, permission configs)',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_auto_tnt',
    description: 'Place TNT at coordinates and ignite it (uses /setblock + flint and steel)',
    inputSchema: { type: 'object', properties: {
      x: { type: 'number' }, y: { type: 'number' }, z: { type: 'number' },
    }, required: ['x', 'y', 'z'] },
  },
  {
    name: 'mc_auto_enchant',
    description: 'Automated enchanting: check enchantment table options, apply enchantments, collect items',
    inputSchema: { type: 'object', properties: {
      action: { type: 'string', enum: ['status', 'enchant', 'collect'], description: 'Action' },
      slot: { type: 'number', description: 'Enchantment slot (0-2)' },
      item_slot: { type: 'number', description: 'Inventory slot of item to enchant' },
    }, required: ['action'] },
  },
  {
    name: 'mc_auto_smith',
    description: 'Automated smithing: check smithing table, upgrade items, collect results',
    inputSchema: { type: 'object', properties: {
      action: { type: 'string', enum: ['status', 'upgrade', 'collect'], description: 'Action' },
      upgrade_slot: { type: 'number', description: 'Upgrade material inventory slot' },
      item_slot: { type: 'number', description: 'Item to upgrade inventory slot' },
    }, required: ['action'] },
  },
  {
    name: 'mc_auto_anvil',
    description: 'Automated anvil usage: combine items, apply enchanted books, rename items',
    inputSchema: { type: 'object', properties: {
      action: { type: 'string', enum: ['status', 'combine', 'collect'], description: 'Action' },
      left_slot: { type: 'number', description: 'Left item (target) inventory slot' },
      right_slot: { type: 'number', description: 'Right item (book/sacrifice) inventory slot' },
      rename: { type: 'string', description: 'Rename the result (optional)' },
    }, required: ['action'] },
  },
  {
    name: 'mc_projectile_simulate',
    description: 'Simulate a projectile trajectory showing predicted landing point',
    inputSchema: { type: 'object', properties: {
      pitch: { type: 'number', description: 'Launch pitch (default: player pitch)' },
      yaw: { type: 'number', description: 'Launch yaw (default: player yaw)' },
      velocity: { type: 'number', description: 'Velocity (default: 1.0)' },
      gravity: { type: 'boolean', description: 'Enable gravity (default: true)' },
      steps: { type: 'number', description: 'Trajectory steps (default: 50)' },
    } },
  },
  {
    name: 'mc_network_graph',
    description: 'Get player positions and render a real-time map of nearby players',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_chat_mimic',
    description: '[SOCIAL] Learn a player\'s chat style and generate a message that convincingly sounds like them',
    inputSchema: { type: 'object', properties: {
      player: { type: 'string', description: 'Player to mimic' },
      message: { type: 'string', description: 'Content/intent to convey' },
      count: { type: 'number', description: 'Messages to learn from (default: 30)' },
      send: { type: 'boolean', description: 'Send immediately (default: false)' },
    }, required: ['player', 'message'] },
  },
  {
    name: 'mc_gaslight',
    description: '[SOCIAL] Generate strategically confusing/confusing chat messages to manipulate social dynamics',
    inputSchema: { type: 'object', properties: {
      target: { type: 'string', description: 'Target player or audience' },
      tactic: { type: 'string', enum: ['impersonate', 'rumor', 'contradict', 'confuse'], description: 'Tactic' },
      topic: { type: 'string', description: 'Topic to focus on' },
      send: { type: 'boolean', description: 'Send immediately (default: false)' },
      context: { type: 'string', description: 'Server situation context' },
    }, required: ['target', 'tactic'] },
  },
  {
    name: 'mc_social_engineer',
    description: '[SOCIAL] Analyze a player\'s patterns and recommend optimal timing/approach for social manipulation',
    inputSchema: { type: 'object', properties: {
      target: { type: 'string', description: 'Target player' },
      goal: { type: 'string', description: 'What you want to achieve' },
      context: { type: 'string', description: 'Situation context' },
    }, required: ['target', 'goal'] },
  },
  {
    name: 'mc_propaganda',
    description: '[SOCIAL] Write persuasive announcements/公告 to influence server opinion',
    inputSchema: { type: 'object', properties: {
      topic: { type: 'string', description: 'Announcement topic' },
      tone: { type: 'string', enum: ['positive', 'urgent', 'warning', 'neutral', 'divisive'], description: 'Tone' },
      target_audience: { type: 'string', description: 'Target audience' },
      key_message: { type: 'string', description: 'Core message' },
      broadcast: { type: 'boolean', description: 'Broadcast via /say (default: false)' },
    }, required: ['topic', 'tone'] },
  },
  {
    name: 'mc_autonomous_goal',
    description: 'AI autonomously plans and executes steps to achieve a goal. Decomposes goal into actions using DeepSeek, then executes them via bridge tools.',
    inputSchema: {
      type: 'object',
      properties: {
        goal: { type: 'string', description: 'The goal to achieve, e.g. "build a small wooden house near the village"' },
        context: { type: 'string', description: 'Additional context about the situation' },
        preview: { type: 'boolean', description: 'Preview the plan without executing (default: false)' },
      },
      required: ['goal'],
    },
  },
  {
    name: 'mc_auto_store',
    description: 'Automatically move matching items from inventory to an open container/chest. Finds items by name and QUICK_MOVEs them to the container.',
    inputSchema: {
      type: 'object',
      properties: {
        item: { type: 'string', description: 'Item name/ID to move from inventory to container' },
        count: { type: 'number', description: 'Max count to move (default: all found)' },
        container_slot_start: { type: 'number', description: 'Container slot to start filling (default: 0)' },
      },
      required: ['item'],
    },
  },
  {
    name: 'mc_build_preview',
    description: 'Preview a building template with before/after screenshots. Takes a screenshot, builds the template, takes another screenshot, and returns both paths.',
    inputSchema: {
      type: 'object',
      properties: {
        template: { type: 'string', enum: ['house', 'wall', 'tower', 'bridge', 'staircase', 'platform', 'pillar', 'arch', 'pyramid', 'room'], description: 'Building template' },
        x: { type: 'number', description: 'Start X coordinate' },
        y: { type: 'number', description: 'Start Y coordinate' },
        z: { type: 'number', description: 'Start Z coordinate' },
        width: { type: 'number', description: 'Width (default: 5)' },
        height: { type: 'number', description: 'Height (default: 4)' },
        depth: { type: 'number', description: 'Depth (default: 5)' },
        material: { type: 'string', description: 'Block material (default: minecraft:stone_bricks)' },
        floor_material: { type: 'string', description: 'Floor material (default: same as material)' },
        roof_material: { type: 'string', description: 'Roof material (default: same as material)' },
        door: { type: 'boolean', description: 'Include door (default: true for house)' },
        windows: { type: 'boolean', description: 'Include windows (default: true for house)' },
        execute: { type: 'boolean', description: 'Actually execute build (default: false — just preview)' },
      },
      required: ['template', 'x', 'y', 'z'],
    },
  },
  {
    name: 'mc_economic_pipeline',
    description: 'Run a full economic pipeline: auto-farm crops → auto-store harvest → auto-craft items → auto-trade with villagers. Chained automation with state tracking.',
    inputSchema: {
      type: 'object',
      properties: {
        crop: { type: 'string', description: 'Crop type to harvest (e.g., "wheat", "carrots"). Default: all mature' },
        craft: { type: 'string', description: 'Item to craft from harvest (e.g., "bread", "golden_carrot")' },
        trade_index: { type: 'number', description: 'Trade recipe index to use (default: 0)' },
        max_cycles: { type: 'number', description: 'Max pipeline cycles (default: 1)' },
      },
    },
  },
  {
    name: 'mc_workflow_create',
    description: 'Create a persistent scheduled/triggered workflow that executes steps on a schedule. Supports interval, cron, and one-time schedules.',
    inputSchema: {
      type: 'object',
      properties: {
        name: { type: 'string', description: 'Workflow name' },
        schedule: { type: 'string', description: 'Schedule: "interval:<seconds>", "cron:<expression>", or "once"' },
        steps: {
          type: 'array',
          description: 'Steps to execute when triggered',
          items: {
            type: 'object',
            properties: {
              type: { type: 'string', enum: ['command', 'chat', 'wait'], description: 'Step type' },
              cmd: { type: 'string', description: 'Minecraft command (for type=command)' },
              msg: { type: 'string', description: 'Chat message (for type=chat)' },
              ms: { type: 'number', description: 'Wait duration in ms (for type=wait)' },
            },
          },
        },
        enabled: { type: 'boolean', description: 'Start enabled (default: true)' },
      },
      required: ['name', 'schedule', 'steps'],
    },
  },
  {
    name: 'mc_workflow_list',
    description: 'List all registered workflows',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'mc_workflow_remove',
    description: 'Remove a workflow by name',
    inputSchema: {
      type: 'object',
      properties: {
        name: { type: 'string', description: 'Workflow name to remove' },
      },
      required: ['name'],
    },
  },
  {
    name: 'mc_render_map',
    description: 'Render a 2D top-down block map of the surrounding terrain. Scans chunks, determines surface blocks, and returns ASCII art or JSON grid.',
    inputSchema: {
      type: 'object',
      properties: {
        radius: { type: 'number', description: 'Map radius in chunks (default: 4)' },
        show_players: { type: 'boolean', description: 'Overlay player positions on map (default: true)' },
      },
    },
  },
  {
    name: 'mc_qq_bridge',
    description: 'Connect/disconnect/status for QQ Bot chat bridging via go-cqhttp HTTP API. On connect, polls for new messages every 2s and relays them to Minecraft chat.',
    inputSchema: {
      type: 'object',
      properties: {
        action: { type: 'string', enum: ['connect', 'disconnect', 'status'], description: 'Action' },
        endpoint: { type: 'string', description: 'go-cqhttp HTTP API endpoint (e.g., "http://127.0.0.1:5700")' },
        group_id: { type: 'number', description: 'QQ group ID to bridge' },
      },
      required: ['action'],
    },
  },
  {
    name: 'mc_redstone_simulate',
    description: 'Scan redstone components or get AI analysis of a redstone circuit within radius',
    inputSchema: {
      type: 'object',
      properties: {
        radius: { type: 'number', description: 'Scan radius (default: 10)' },
        mode: { type: 'string', enum: ['scan', 'analyze'], description: 'scan: find components, analyze: AI analysis of circuit' },
      },
    },
  },
  {
    name: 'mc_qq_send',
    description: 'Send a message to a QQ group via the go-cqhttp API. Requires mc_qq_bridge to be connected first.',
    inputSchema: {
      type: 'object',
      properties: {
        message: { type: 'string', description: 'Message to send to QQ group' },
        group_id: { type: 'number', description: 'QQ group ID (default: from config)' },
      },
      required: ['message'],
    },
  },
  {
    name: 'mc_afk_standin',
    description: 'AI AFK stand-in: enables an AI that learns your chat style and auto-replies to incoming messages while you are away from keyboard.',
    inputSchema: {
      type: 'object',
      properties: {
        action: { type: 'string', enum: ['start', 'stop', 'status'], description: 'Action' },
        learning_count: { type: 'number', description: 'Number of recent messages to learn style from (default: 50)' },
        poll_interval: { type: 'number', description: 'Poll interval in seconds (default: 5)' },
      },
      required: ['action'],
    },
  },
  {
    name: 'mc_social_credit',
    description: 'Quantified social credit scores for tracked players: influence, trust, emotional stability, social capital, and manipulation resistance.',
    inputSchema: {
      type: 'object',
      properties: {
        player: { type: 'string', description: 'Player name (default: all tracked)' },
      },
    },
  },
  {
    name: 'mc_autonomous_social',
    description: 'Start/stop a background social AI agent that continuously monitors chat, analyzes relationships, and autonomously executes social maneuvers.',
    inputSchema: {
      type: 'object',
      properties: {
        action: { type: 'string', enum: ['start', 'stop', 'status'], description: 'Action' },
        aggressiveness: { type: 'number', description: '0=passive, 1=neutral, 2=opportunistic, 3=aggressive (default: 1)' },
        focus_players: { type: 'string', description: 'Comma-separated player names to focus on (default: all)' },
      },
      required: ['action'],
    },
  },
  {
    name: 'mc_campaign',
    description: 'Multi-phase social engineering campaign engine. Create, advance, and manage multi-day influence operations against targets.',
    inputSchema: {
      type: 'object',
      properties: {
        action: { type: 'string', enum: ['create', 'status', 'list', 'advance', 'abort'], description: 'Action' },
        name: { type: 'string', description: 'Campaign name' },
        target: { type: 'string', description: 'Target player' },
        goal: { type: 'string', description: 'Desired outcome' },
        strategy: { type: 'string', enum: ['undermine', 'befriend', 'rivalry', 'divide'], description: 'Strategy' },
        duration_days: { type: 'number', description: 'Max duration in days (default: 3)' },
      },
    },
  },
  {
    name: 'mc_detect_manipulation',
    description: 'Analyze chat history to detect if someone is attempting to manipulate, gaslight, or socially engineer you.',
    inputSchema: {
      type: 'object',
      properties: {
        player: { type: 'string', description: 'Check a specific player (default: auto-detect from recent chat)' },
        message: { type: 'string', description: 'Check a specific message (default: check all recent messages)' },
      },
    },
  },
]
