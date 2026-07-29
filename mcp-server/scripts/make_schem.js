import { writeFileSync } from 'fs';
import { gzipSync } from 'zlib';

function nbt(tag) {
  const out = [];
  function w(b) { out.push(typeof b === 'number' ? Buffer.from([b]) : b); }
  function s(v) { const b = Buffer.from(v, 'utf8'); const h = Buffer.alloc(2); h.writeUInt16BE(b.length); w(h); w(b); }
  function t(type, name, val) {
    if (name !== null) { w(type); s(name); }
    switch (type) {
      case  0: break;
      case  1: w(val); break;
      case  2: { const b = Buffer.alloc(2); b.writeInt16BE(val); w(b); break; }
      case  3: { const b = Buffer.alloc(4); b.writeInt32BE(val); w(b); break; }
      case  7: { const b = Buffer.alloc(4 + val.length); b.writeInt32BE(val.length); val.copy(b, 4); w(b); break; }
      case  8: s(val); break;
      case  9: { w(val[0]); const b = Buffer.alloc(4); b.writeInt32BE(val[1].length); w(b); for (const v of val[1]) t(val[0], null, v); break; }
      case 10: { for (const [k, v] of Object.entries(val)) { t(v[0], k, v[1]); } w(0); break; }
      case 11: { const b = Buffer.alloc(4 + val.length * 4); b.writeInt32BE(val.length); for (let i = 0; i < val.length; i++) b.writeInt32BE(val[i], 4 + i * 4); w(b); break; }
    }
  }
  t(10, '', tag);
  return Buffer.concat(out);
}

// MCEdit .schematic format:
// Blocks: ByteArray (block IDs)
// Data: ByteArray (block data/metadata)
// Width: Short, Height: Short, Length: Short
// Materials: String ("Alpha")
// Block IDs for modern MC: use the block's string ID... but MCEdit format uses numeric IDs
// Actually for Baritone in 1.12+, they use a different approach
// Let me try using Baritone's own format: just a flat list of blocks

const W = 5, H = 5, D = 5;
const blocks = new Uint8Array(W * H * D);
const data = new Uint8Array(W * H * D);
let idx = 0;

for (let y = 0; y < H; y++) {
  for (let z = 0; z < D; z++) {
    for (let x = 0; x < W; x++) {
      // Use block IDs from the Minecraft block registry
      // stone_bricks = 98 (old ID) or use a different approach
      // Actually, for Baritone in modern MC, we need the named IDs in a palette
      let blockId = 0; // air
      if (y === 0 || y === H - 1) {
        blockId = 98; // stone_bricks (old ID) - might not work for 1.21.1
      } else if (x === 0 || x === W - 1 || z === 0 || z === D - 1) {
        blockId = 98;
      }
      if (y === H - 1 && x === Math.floor(W/2) && z === Math.floor(D/2)) {
        blockId = 133; // emerald_block (old ID)
      }
      blocks[idx] = blockId;
      data[idx] = 0;
      idx++;
    }
  }
}

// Actually Baritone 1.21.1 uses Sponge v2 format but with named IDs
// Let me use a DIFFERENT approach: make Baritone's own format
// with BlockIDs mapped to names

// Try approach: use a simple palette-based format
const pal = { 'minecraft:air': 0, 'minecraft:stone_bricks': 1, 'minecraft:emerald_block': 2 };
const palMax = Object.keys(pal).length;

const bdata = new Uint8Array(W * H * D);
idx = 0;
for (let y = 0; y < H; y++) {
  for (let z = 0; z < D; z++) {
    for (let x = 0; x < W; x++) {
      let name = 'minecraft:air';
      if (y === 0 || y === H - 1) name = 'minecraft:stone_bricks';
      else if (x === 0 || x === W - 1 || z === 0 || z === D - 1) name = 'minecraft:stone_bricks';
      if (y === H - 1 && x === Math.floor(W/2) && z === Math.floor(D/2)) name = 'minecraft:emerald_block';
      bdata[idx++] = pal[name];
    }
  }
}

const palComp = {};
for (const [name, id] of Object.entries(pal)) palComp[name] = [3, id];

// Baritone checks for .schematic but reads it as Sponge format if the version field exists
// OR it might look for .schem
// Let me also create .schem version
const root = {
  Version: [3, 2],
  DataVersion: [3, 3953],
  Width: [2, W],
  Height: [2, H],
  Length: [2, D],
  PaletteMax: [3, palMax],
  Palette: [10, palComp],
  BlockData: [7, Buffer.from(bdata)],
  OffsetX: [3, 0],
  OffsetY: [3, 0],
  OffsetZ: [3, 0],
  BlockEntities: [9, [10, []]],
};

const schemDir = 'D:\\PCL2\\.minecraft\\versions\\Fabulously.Optimized-6.5.0\\schematics\\';

writeFileSync(schemDir + 'monument.schem', gzipSync(nbt(root)));
writeFileSync(schemDir + 'monument.schematic', gzipSync(nbt(root)));
console.log('Created both monument.schem and monument.schematic');
