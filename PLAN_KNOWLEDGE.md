# SeedFinder — Plan Knowledge Graph

> Auto-built from master plan. Sections 1-11 all read ✅.
> Total: 2164 lines, 95KB plan, ~835 lines of new/changed code.

---

## SECTION 1: Research Background

```
Client-side math ✅ vs Chunkbase API ❌ vs Hugging Face server ❌
  - Pure Mojang scatter algorithm = deterministic, offline, zero latency
  - ~5% false positives from no biome validation — acceptable for utility mod
  - Chunkbase = ground truth (WASM-compiled MC, no API)
  - kaptainwutax/feature-utils used only for verification (Mojang mappings ≠ Yarn)
```

---

## SECTION 2: Current Architecture (v1 — as-is on main branch)

```
8 Java files, Fabric 1.21.11, Yarn mappings 1.21.11+build.6

SeedFinderMod.java          ← ClientModInitializer, G keybind
SeedFinderCommand.java      ← /seedfinder open|seed|clear
SeedFinderConfig.java       ← seedfinder.properties (seed + radius)
StructureType.java          ← 18 enum entries (includes BROKEN ones: Buried Treasure, Mineshaft)
StructureFinder.java        ← nearest(), scatterCandidate(), allWithin() — HAS BUGS
StructurePickerScreen.java  ← GUI with search, colored buttons, pick() → waypoint
WaypointStore.java          ← static List<Waypoint>, thread-safe (NO persistence)
WaypointRenderer.java       ← 320-block beams + HUD (no culling, per-frame alloc)
```

---

## SECTION 3: All 13 Bugs (Critical Knowledge)

```
B1  CRITICAL  Stronghold jitter 48.0 → MC uses 96.0         → 50% wrong positions
B2  CRITICAL  Missing triangular spread (Monument, Mansion)   → offset positions
B3  CRITICAL  Missing frequency (Pillager 20%, Treasure 1%, Mineshaft 0.4%)
B4  CRITICAL  Missing locate offset (+9,+9 for Buried Treasure)
B5  HIGH      Null seed NPE: Long → long unbox crash
B6  HIGH      GUI closes after every pick (method_25419())
B7  HIGH      Stronghold distance uses chunk coords → loses 16-block precision
B8  MEDIUM    Stronghold Y=0 (bedrock) → should be Y=64
B9  MEDIUM    Buried Treasure salt=0, spacing=1 → floods candidates
B10 MEDIUM    Mineshaft PER_CHUNK → returns null, button does nothing
B11 LOW       Nether+ Bastion share salt 30084232 (correct per MC, document it)
B12 LOW       No seed input in GUI
B13 LOW       320-block waypoint beams visually noisy
```

### RNG Call Sequence (CRITICAL — must match exactly)

```
LINEAR + freq < 1.0:
  1. nextInt(range) → ox
  2. nextInt(range) → oz
  3. nextFloat()    → freq check (if fail: return null)

TRIANGULAR + freq < 1.0:
  1. nextInt(range) → ox part 1
  2. nextInt(range) → ox part 2
  3. nextInt(range) → oz part 1
  4. nextInt(range) → oz part 2
  5. nextFloat()    → freq check (if fail: return null)

frequency == 1.0: skip freq check entirely
Buried Treasure: spacing=1 → range=1 → offset always 0 (handled by locateOffset)
```

---

## SECTION 4: Phase 1 — Algorithm Accuracy Fixes (MUST DO FIRST)

### 4.1 StructureType.java changes
```
ADD: SpreadType enum (LINEAR, TRIANGULAR)
ADD: frequency field (double, 0.0-1.0)
ADD: locateOffsetX/Z (int chunks)
ADD: Dimension enum (OVERWORLD, NETHER, END, ALL)
ADD: isSearchable() method (hides Buried Treasure + Mineshaft)
REMOVE: PER_CHUNK and BURIED_TREASURE from GUI (marked disabled)
```

### 4.2 StructureFinder.java changes
```
nearestScatter() → SPIRAL SEARCH replaces brute-force square grid
  - Spiral iterator: 0,0 → 1,0 → 1,1 → 0,1 → -1,1 → ...
  - ~50-200 iterations instead of ~35,000

scatterCandidateInline() → INLINE LCG replaces new Random()
  - zero object allocation
  - state = (popSeed ^ 0x5DEECE66DL) & ((1L<<48)-1)
  - advance: state = (state * 0x5DEECE66DL + 0xBL) & mask
  - nextIntFromState: (state >>> 17) % bound
  - nextFloatFromState: (state >>> 17) / (float)(1 << 31)

nearestStronghold() → FIXED
  - B1: jitter 48.0 → 96.0
  - B7: block coords (not chunk coords) for distance
  - B8: Y=0 → Y=64
  - Uses inline LCG (same as scatter)

nearest() → LRU CACHE (64 entries, LinkedHashMap)
  - key = (seed, type, chunkX, chunkZ)
  - synchronized access

allWithin() → uses scatterCandidateInline() with locateOffset
```

### 4.3 WaypointStore.java changes
```
ADD: remove(int index)
ADD: JSON persistence (load() / save())
  - save file: config/seedfinder-waypoints.json
  - no external lib — hand-written JSON parser
  - manual escape/unescape

save() called after every add/clear/remove
load() called once at startup (SeedFinderMod.onInitializeClient)
```

### 4.4 SeedFinderMod.java changes
```
ADD: WaypointStore.load() after SeedFinderConfig.load()
```

---

## SECTION 5: Phase 2 — Performance (Already Built Into Phase 1 Code)

```
Spiral search:      35,000→50-200 iterations (65-370x)
Inline LCG:         35,000 Random objects → 0 allocations
LRU cache:          Full recompute → O(1) cache hit on repeat
Async search:       GUI never freezes (CompletableFuture)
Diff-based rebuild: Don't destroy/recreate all widgets on keystroke
```

---

## SECTION 6: Phase 3 — GUI Redesign

### New Layout
```
┌────────────────────────────────────────────────────────────┐
│ SeedFinder                [Seed: -12345678] [Auto]          │ ← title bar
├────────────────────────────────────────────────────────────┤
│ [Overworld] [Nether] [End] [All]  🔍 [Search............]  │ ← tabs + search
├──────────────────────────┬─────────────────────────────────┤
│ ☐ Ancient City           │ Results (3 found)               │
│ ☐ Trial Chambers         │ ● Ancient City X:1048 Z:-2156   │
│ ☐ Pillager Outpost       │ ● Trial Chambers X:-520 Z:3200  │
│ ☐ ... (scrollable)       │    [X] remove button            │
├──────────────────────────┴─────────────────────────────────┤
│ Radius: [3200]    [Clear All]    [Close]                    │
└────────────────────────────────────────────────────────────┘
```

### Key Changes
```
1. Seed input field + [Auto] button in title bar  ← B12 fix
2. Category tabs (OW/Nether/End/All)               ← new feature
3. Fuzzy search with alias map                     ← "ac"→Ancient City
4. GUI stays open after picking                    ← B6 fix
5. Results panel with coords/distance/direction    ← new feature
6. Seed warning banner (yellow, when no seed)      ← B5 prevention
7. Async search (CompletableFuture)                ← no freeze
8. Search debounce (150ms)                          ← perf
9. Diff-based button rebuild                        ← perf
10. Disabled structures hidden (B9, B10)            ← UX
11. Auto-detect seed from world                     ← convenience
```

### Touch Adaptations (Zalith Launcher 2)
```
Buttons: 20px → 36px  |  Tabs: 20px → 40px
Search: 18px → 36px    |  Bottom: 20px → 40px
Layout: 2/row → 1/row  |  Results: right → bottom
Detection: GLFW primary monitor && width < 800
```

---

## SECTION 7: Phase 4 — Rendering Optimization

```
Beam height: 320 → 32 blocks (10x less GPU)
Distance culling: skip if >2048 blocks
Behind-camera culling: skip if dot product < -200
Pre-allocated buffer: enough for 50 waypoints, no per-frame alloc
Max visible beams: 8 (prevents death spiral on mobile)
Alpha: constant 0.2 → pulsing 0.15±0.1
HUD: 1 nearest → 8 nearest sorted by distance
Labels: only within 2048 blocks

renderFilledBox() → 24 vertices (triangulated box), top face brighter
drawLabels() → billboarded text at beam top
renderHud() → semi-transparent box top-left, color dot + label + dist + direction
```

### Yarn 1.21.11+build.6 API Notes
```
class_287 → BufferBuilder (vertex building)
class_11285 → GpuBuffer (GPU buffer)
class_9799 → BufferAllocator
class_9801 → BuiltBuffer
class_5590 → ShapeIndexBuffer (was AutoStorageIndexBuffer)
     .method_68274(count) → get index buffer slice
     .method_31924() → get index type
class_10799.field_56837 → FILLED_THROUGH_WALLS pipeline
RenderSystem.getSequentialBuffer(mode) → get ShapeIndexBuffer
RenderSystem.bindDefaultUniforms(pass) → bind shader uniforms
pass.setUniform("DynamicTransforms", dt) → set uniform
pass.drawIndexed(startVertex, startIndex, indexCount, instanceCount)
.most methods are obfuscated → grep / mcmodding-mcp to verify
```

---

## SECTION 8: Phase 5 — Zalith Launcher 2 (Android)

```
Works because: pure Java math, Fabric API support, RenderPipeline abstracts GL/Vulkan
Touch-friendly sizes built into GUI code ✅
Single-column layout built into GUI code ✅
Floating "SF" on-screen button ⬜ (low priority, /seedfinder open works)
Lower default radius for mobile ⬜
Persistent waypoints (JSON) ✅
32-block beam mandatory for mobile GPU (Mali/Adreno 2-8x less fill rate)
Max 8 beams prevents frame drops
```

---

## SECTION 9: Phase 6 — New Files

```
en_us.json → src/main/assets/seedfinder/lang/en_us.json
  10 keys: screen title/search/clear/close, msg no_seed/not_found/found/seed_set, keybindings
```

---

## SECTION 10: Testing (8 Tests)

```
Test 1: Stronghold accuracy — seed 12345, compare Chunkbase (B1 fix)
Test 2: Ocean Monument — seed 12345, compare Chunkbase (B2 fix)
Test 3: Pillager Outpost — find 10, only 20% candidates (B3 fix)
Test 4: Null seed safety — click with no seed, warning not crash (B5 fix)
Test 5: GUI stays open — pick structure, GUI doesn't close (B6 fix)
Test 6: Performance — Shipwreck radius 3200, <5ms async
Test 7: Waypoint persistence — close MC, reopen, waypoints survive
Test 8: Zalith — 36px buttons, single-column, touch-friendly
```

---

## SECTION 11: Implementation Checklist

```
STEP 1: Algorithm Accuracy (DO FIRST — Phase 1)
  [✅] 1.1 Rewrite StructureType.java  (~87 lines, +SpreadType/freq/Dimension/isSearchable)
  [✅] 1.2 Rewrite StructureFinder.java (~260 lines, spiral/inline LCG/fixed stronghold/cache)
  [✅] 1.3 Rewrite WaypointStore.java   (~105 lines, +remove()+JSON persistence)
  [✅] 1.4 Edit SeedFinderMod.java       (+1 line, WaypointStore.load())
  [⏳] 1.5 Test against Chunkbase (seed 12345) — CI will verify after push

STEP 2: GUI Redesign (Phase 3)
  [ ] 2.1 Rewrite StructurePickerScreen.java (~250 lines)
  [ ] 2.2 Create en_us.json                 (~10 lines)

STEP 3: Rendering Optimization (Phase 4)
  [ ] 3.1 Rewrite WaypointRenderer.java      (~200 lines)

STEP 4: Android / Zalith (Phase 5)
  [ ] 4.1 Add floating SF button
  [ ] 4.2 Test on Zalith Launcher 2

FILES SUMMARY:
  REWRITE: StructureType.java, StructureFinder.java, StructurePickerScreen.java,
           WaypointRenderer.java, WaypointStore.java
  EDIT:    SeedFinderMod.java
  NEW:     en_us.json
  NO CHANGE: SeedFinderCommand.java, SeedFinderConfig.java
```

---

## Current v1 Code Defects vs Plan Spec

| Aspect | v1 (current on main) | Plan Spec (what to build) |
|--------|---------------------|--------------------------|
| StructureType | 18 entries, NO spread/freq/offset | 18 entries, WITH SpreadType/frequency/locateOffset/Dimension/isSearchable() |
| StructureFinder | brute-force square, new Random(), 48.0 jitter, no triangular, no freq | spiral search, inline LCG, 96.0 jitter, triangular spread, frequency gating, LRU cache |
| StructurePickerScreen | closes on pick, no seed field, no tabs | stays open, seed field+Auto, tabs+aliases, async search, results panel |
| WaypointStore | in-memory only | JSON persistent, remove() method |
| WaypointRenderer | 320-block beams, no culling, per-frame alloc | 32-block, distance/behind-camera culling, pre-allocated buffer, 8 max, pulsing |
| SeedFinderMod | bare init | + WaypointStore.load(), + mobile floating button |
| en_us.json | missing | 10 translation keys |
| SeedFinderCommand | works | no changes needed |
| SeedFinderConfig | works | no changes needed |

## Dependencies Between Steps

```
Step 1 (Algorithm) ──no deps── can do FIRST
  │
  ├── Step 2 (GUI) depends on Step 1 for correct structure data
  │
  ├── Step 3 (Renderer) depends on Step 1 for waypoint data
  │
  └── Step 4 (Android) depends on Step 2+3 for touch GUI + render perf
```

## Yarn Mappings Quick Reference (obfuscated → concept)

```
class_2338    = BlockPos          (x, y, z position)
class_1923    = ChunkPos          (x, z chunk position)
class_310     = MinecraftClient   (main client instance)
class_332     = DrawContext       (2D rendering)
class_342     = TextFieldWidget   (input text field)
class_4185    = ButtonWidget      (clickable button)
class_437     = Screen            (GUI screen base)
class_2561    = Text              (text component)
class_124     = Formatting        (text color/format codes)
class_287     = BufferBuilder     (vertex building)
class_11285   = GpuBuffer         (GPU vertex buffer)
class_9799    = BufferAllocator   (memory allocator)
class_9801    = BuiltBuffer       (built vertex data)
class_10799   = RenderPipeline    (GPU pipeline definition)
```
