# KIMI PLAN -- SeedFinder Mod Improvement Plan

> Research date: 2026-07-13
> Target: Minecraft Fabric 1.21.11
> Sources: minecraft.wiki (official), fabricmc.net (official), Chunkbase analysis

---

## Table of Contents

1. [Current State Analysis](#1-current-state-analysis)
2. [Official Minecraft 1.21 Structure Algorithm](#2-official-minecraft-121-structure-algorithm)
3. [Bugs Found vs Chunkbase](#3-bugs-found-vs-chunkbase)
4. [Detailed Fix Plan](#4-detailed-fix-plan)
5. [File-by-File Changes](#5-file-by-file-changes)
6. [Testing Strategy](#6-testing-strategy)
7. [Future Roadmap](#7-future-roadmap)

---

## 1. Current State Analysis

### Repo Structure

```
in-game-finder/
├── mod/                              # Minecraft Fabric mod (main deliverable)
│   ├── src/main/java/dev/seedfinder/
│   │   ├── SeedFinderMod.java        # Entry point, keybind registration
│   │   ├── command/
│   │   │   └── SeedFinderCommand.java    # /seedfinder command tree
│   │   ├── config/
│   │   │   └── SeedFinderConfig.java     # Properties-based config
│   │   ├── finder/
│   │   │   ├── StructureFinder.java      # Core algorithm (HAS BUGS)
│   │   │   └── StructureType.java        # Structure enum (INCOMPLETE)
│   │   ├── gui/
│   │   │   └── StructurePickerScreen.java # GUI with search
│   │   └── waypoint/
│   │       ├── WaypointRenderer.java      # 3D rendering + HUD
│   │       └── WaypointStore.java         # Thread-safe storage
│   └── src/main/resources/
│       └── fabric.mod.json
├── src/                              # Web app (blank Lovable template)
└── package.json                      # React 19 + TanStack Start
```

### Current Versions (verified against fabricmc.net)

| Component | Current | Latest Official | Status |
|-----------|---------|----------------|--------|
| Minecraft | 1.21.11 | 1.21.11 | OK |
| Fabric Loader | 0.18.1 | 0.18.1 | OK |
| Fabric API | 0.141.4+1.21.11 | 0.141.4+1.21.11 | OK |
| Yarn Mappings | 1.21.11+build.6 | 1.21.11+build.6 | OK |
| Fabric Loom | 1.15-SNAPSHOT | 1.15-SNAPSHOT | OK |
| Gradle | 9.6.1 | 9.6.1 | OK |

**All dependency versions are current. The issues are algorithmic, not version-related.**

### What the Mod Does

- Client-side only Fabric mod that finds vanilla structures from a known seed
- Uses `/seedfinder seed <value>` to set seed
- Opens GUI with `G` key or `/seedfinder open`
- Calculates nearest structure using Mojang's deterministic placement algorithm
- Shows waypoint beam in-world + HUD overlay with distance/direction

---

## 2. Official Minecraft 1.21 Structure Algorithm

### 2.1 Random Spread (most structures)

Mojang uses `minecraft:random_spread` placement type:

```
regionX = floor(chunkX / spacing)
regionZ = floor(chunkZ / spacing)

seed = worldSeed + salt
    + regionX * 341873128712L
    + regionZ * 132897987541L

rng = new Random(seed)

range = spacing - separation

// LINEAR spread (default):
offsetX = rng.nextInt(range)   // 0 to range-1
offsetZ = rng.nextInt(range)   // 0 to range-1

// TRIANGULAR spread (Ocean Monument, Woodland Mansion):
offsetX = (rng.nextInt(range) + rng.nextInt(range)) / 2
offsetZ = (rng.nextInt(range) + rng.nextInt(range)) / 2

candidateChunkX = regionX * spacing + offsetX
candidateChunkZ = regionZ * spacing + offsetZ
```

### 2.2 Concentric Rings (Stronghold only)

Mojang uses `minecraft:concentric_rings`:

```
Parameters:
  distance = 32    // ring thickness in 6-chunk units
  count    = 128   // total strongholds
  spread   = 3     // first ring count

Ring N center distance (in chunks):
  centerChunks = distance * 6 * (N + 1)
               = 192 * (N + 1)

Ring N stronghold count:
  countN = spread * (N^2 + 3*N + 2) / 6
         = 3 * (N^2 + 3*N + 2) / 6

Actual counts per ring:
  Ring 0: 3  strongholds @ center 192  chunks (3072 blocks)
  Ring 1: 6  strongholds @ center 384  chunks (6144 blocks)
  Ring 2: 10 strongholds @ center 576  chunks (9216 blocks)
  Ring 3: 15 strongholds @ center 768  chunks (12288 blocks)
  Ring 4: 21 strongholds @ center 960  chunks (15360 blocks)
  Ring 5: 28 strongholds @ center 1152 chunks (18432 blocks)
  Ring 6: 36 strongholds @ center 1344 chunks (21504 blocks)
  Ring 7: 9  strongholds @ center 1536 chunks (24576 blocks)
  Total:  128

RNG:
  rng = new Random(worldSeed)
  baseAngle = rng.nextDouble() * 2 * PI

For each ring N:
  distBlocks = centerChunks * 16 + (rng.nextDouble() - 0.5) * 96 * 16
             = centerChunks * 16 + (rng.nextDouble() - 0.5) * 1536

  // Jitter is +/- 48 chunks = +/- 768 blocks from center

  For each stronghold i in ring:
    angle = baseAngle + (2 * PI * i / countN)
    x = round(cos(angle) * distBlocks)
    z = round(sin(angle) * distBlocks)

  baseAngle += rng.nextDouble() * 2 * PI  // random offset before next ring
```

### 2.3 Frequency Gating

Some structures don't spawn at every candidate position:

| Structure | Frequency | Meaning |
|-----------|-----------|---------|
| Pillager Outpost | 0.2 (20%) | Only 1 in 5 candidates actually spawn |
| Buried Treasure | 0.01 (1%) | Only 1 in 100 candidates actually spawn |
| Mineshaft | 0.004 (0.4%) | Only 1 in 250 candidates actually spawn |

The RNG for frequency uses: `seed + salt + chunkX * 341873128712L + chunkZ * 132897987541L`
If `rng.nextFloat() >= frequency`, the structure is skipped.

### 2.4 Locate Offset

Buried Treasure has a special `locate_offset` of `[9, 0, 9]` chunks. This means:
- The structure PLACES at the calculated chunk position
- But `/locate` reports it at `chunkX + 9, chunkZ + 9`
- This is why Chunkbase shows Buried Treasure offset from the actual placement

### 2.5 Official Structure Parameters (Minecraft Wiki)

| Structure | Spacing | Separation | Salt | Spread | Frequency | Locate Offset |
|-----------|---------|------------|------|--------|-----------|---------------|
| Village | 34 | 8 | 10387312 | linear | 1.0 | 0,0 |
| Pillager Outpost | 32 | 8 | 165745296 | linear | **0.2** | 0,0 |
| Desert Pyramid | 32 | 8 | 14357617 | linear | 1.0 | 0,0 |
| Jungle Temple | 32 | 8 | 14357619 | linear | 1.0 | 0,0 |
| Swamp Hut | 32 | 8 | 14357620 | linear | 1.0 | 0,0 |
| Igloo | 32 | 8 | 14357618 | linear | 1.0 | 0,0 |
| Ocean Monument | 32 | 5 | 10387313 | **triangular** | 1.0 | 0,0 |
| Woodland Mansion | 80 | 20 | 10387319 | **triangular** | 1.0 | 0,0 |
| Ruined Portal | 40 | 15 | 34222645 | linear | 1.0 | 0,0 |
| Shipwreck | 24 | 4 | 165745295 | linear | 1.0 | 0,0 |
| Buried Treasure | 1 | 0 | 0 | linear | **0.01** | **9,9** |
| Ancient City | 24 | 8 | 20083232 | linear | 1.0 | 0,0 |
| Trial Chambers | 34 | 12 | 94251327 | linear | 1.0 | 0,0 |
| Nether Fortress* | 27 | 4 | 30084232 | linear | 1.0 | 0,0 |
| Bastion Remnant* | 27 | 4 | 30084232 | linear | 1.0 | 0,0 |
| End City | 20 | 11 | 10387313 | linear | 1.0 | 0,0 |
| Mineshaft | 1 | 0 | 0 | linear | **0.004** | 0,0 |
| Stronghold | -- | -- | -- | concentric rings | -- | 0,0 |

\* Nether Fortress and Bastion Remnant share the `nether_complexes` set (40%/60% weight).
For "nearest location" finding, treating them separately with identical placement is acceptable.

---

## 3. Bugs Found vs Chunkbase

### Bug #1: Stronghold jitter is HALVED (CRITICAL)

**File:** `StructureFinder.java` -- `nearestStronghold()` method

**Current code:**
```java
double distChunks = (128.0 + ring * 192.0) + (rng.nextDouble() - 0.5) * 48.0;
```

**Problem:** The `48.0` gives a jitter of +/- 24 chunks. The Minecraft Wiki specifies +/- 48 chunks, which requires `96.0`.

**Impact:** ~50% of stronghold predictions are wrong by hundreds of blocks. This is the #1 reason results don't match Chunkbase.

**Fix:** Change `48.0` to `96.0`.

**Wiki reference:**
> Ring 1: 3 strongholds within 1,280-2,816 blocks of the origin
> 1280 blocks = 80 chunks, 2816 blocks = 176 chunks
> Center = 128 chunks, jitter = +/- 48 chunks
> Range = [80, 176] -- matches!

With current code (jitter +/- 24):
> Range = [104, 152] -- WRONG! Entirely shifted inward.

---

### Bug #2: Missing triangular spread

**File:** `StructureFinder.java` -- `scatterCandidate()` method

**Current code:** Always uses single `rng.nextInt(range)` for offset.

**Problem:** Ocean Monument and Woodland Mansion use TRIANGULAR spread (average of 2 RNG calls). Your code uses LINEAR for all structures.

**Impact:** These two structures will consistently be offset from Chunkbase predictions.

**Fix:** Check `type.spreadType` and use avg of 2 RNG calls for TRIANGULAR.

---

### Bug #3: Missing frequency gating

**File:** `StructureFinder.java` -- `scatterCandidate()` method

**Current code:** Returns every candidate position without checking if structure actually spawns.

**Problem:** Pillager Outpost (20%), Buried Treasure (1%), and Mineshaft (0.4%) don't spawn at every candidate. The mod shows positions where nothing exists.

**Impact:** Users dig to coordinates and find nothing. False positives.

**Fix:** Add RNG check against `type.frequency` before returning candidate.

---

### Bug #4: Missing locate offset for Buried Treasure

**File:** `StructureFinder.java` -- `nearest()` or `scatterCandidate()` method

**Current code:** Returns raw placement coordinates.

**Problem:** Buried Treasure has `locate_offset` of [9, 9] chunks. The mod should report `chunkX + 9, chunkZ + 9` to match `/locate` and Chunkbase.

**Impact:** Coordinates off by 144 blocks (9 chunks * 16).

**Fix:** Apply `type.locateOffsetX` and `type.locateOffsetZ` to returned coordinates.

---

### Bug #5: Stronghold Y coordinate is 0 (bedrock)

**File:** `StructureFinder.java` -- `nearestStronghold()` method

**Current code:**
```java
best = new BlockPos(sx, 0, sz);
```

**Problem:** Strongholds generate at all Y levels but the portal room is typically around Y=20-50. Y=0 places the waypoint at bedrock.

**Impact:** User sees waypoint at bedrock level, has to dig up to find the stronghold.

**Fix:** Change Y to 64 (approximate surface level) or better, estimate based on typical stronghold depth.

---

### Bug #6: StructureType enum is incomplete

**File:** `StructureType.java`

**Current code:** Only has `displayName`, `placement`, `spacing`, `separation`, `salt`.

**Missing fields:**
- `spreadType` (LINEAR vs TRIANGULAR)
- `frequency` (placement probability)
- `locateOffsetX` / `locateOffsetZ`

**Impact:** Cannot implement fixes for bugs #2, #3, #4 without these fields.

---

## 4. Detailed Fix Plan

### Phase 1: Data Layer -- StructureType.java

Add three new fields and update affected structures:

1. Add `SpreadType` enum: `LINEAR`, `TRIANGULAR`
2. Add `spreadType` field (default: `LINEAR`)
3. Add `frequency` field (default: `1.0`)
4. Add `locateOffsetX` and `locateOffsetZ` fields (default: `0`)
5. Update structures:
   - `OCEAN_MONUMENT`: `spreadType = TRIANGULAR`
   - `WOODLAND_MANSION`: `spreadType = TRIANGULAR`
   - `PILLAGER_OUTPOST`: `frequency = 0.2`
   - `BURIED_TREASURE`: `frequency = 0.01`, `locateOffsetX = 9`, `locateOffsetZ = 9`
   - `MINESHAFT`: `frequency = 0.004`

### Phase 2: Algorithm Layer -- StructureFinder.java

Six fixes in the `StructureFinder` class:

**Fix 1 -- Stronghold jitter (Bug #1):**
```java
// BEFORE (WRONG):
double distChunks = (128.0 + ring * 192.0) + (rng.nextDouble() - 0.5) * 48.0;

// AFTER (CORRECT):
double distChunks = (128.0 + ring * 192.0) + (rng.nextDouble() - 0.5) * 96.0;
```

**Fix 2 -- Triangular spread (Bug #2):**
```java
// BEFORE (all linear):
int ox = range > 0 ? rng.nextInt(range) : 0;
int oz = range > 0 ? rng.nextInt(range) : 0;

// AFTER (check spread type):
int ox, oz;
if (type.spreadType == SpreadType.TRIANGULAR && range > 0) {
    ox = (rng.nextInt(range) + rng.nextInt(range)) / 2;
    oz = (rng.nextInt(range) + rng.nextInt(range)) / 2;
} else {
    ox = range > 0 ? rng.nextInt(range) : 0;
    oz = range > 0 ? rng.nextInt(range) : 0;
}
```

**Fix 3 -- Frequency gating (Bug #3):**
```java
// AFTER scatterCandidate(), before returning:
if (type.frequency < 1.0) {
    long freqSeed = seed + type.salt
        + (long) regionX * 341873128712L
        + (long) regionZ * 132897987541L;
    Random freqRng = new Random(freqSeed);
    if (freqRng.nextFloat() >= type.frequency) {
        return null; // Structure doesn't spawn here
    }
}
```

**Fix 4 -- Locate offset (Bug #4):**
```java
// In nearestScatter(), after calculating candidate:
int finalChunkX = c.x + type.locateOffsetX;
int finalChunkZ = c.z + type.locateOffsetZ;
return new BlockPos((finalChunkX << 4) + 8, 64, (finalChunkZ << 4) + 8);
```

**Fix 5 -- Stronghold Y coordinate (Bug #5):**
```java
// BEFORE:
best = new BlockPos(sx, 0, sz);

// AFTER:
best = new BlockPos(sx, 64, sz);
```

**Fix 6 -- Handle null candidates:**
```java
// In nearestScatter(), skip null candidates from frequency gating:
ChunkPos c = scatterCandidate(seed, type, rx, rz);
if (c == null) continue; // Frequency check failed
```

### Phase 3: UI Layer -- StructurePickerScreen.java

Handle null returns gracefully:

```java
// In pick(), after StructureFinder.nearest():
if (found == null) {
    MinecraftClient.getInstance().player.sendMessage(
        Text.translatable("seedfinder.msg.not_found", type.displayName)
           .formatted(Formatting.RED), false);
    return;
}
```

Already partially handled, but needs to distinguish between:
- "No seed set" -- already handled
- "No candidate found in radius" -- already handled
- "Frequency check filtered all candidates" -- needs new message

---

## 5. File-by-File Changes

### File 1: `mod/src/main/java/dev/seedfinder/finder/StructureType.java`

**Lines added:** ~25
**Lines changed:** 3 structures updated
**Risk:** LOW (pure data, no logic changes)

```java
// ADD enum:
public enum SpreadType { LINEAR, TRIANGULAR }

// ADD fields:
public final SpreadType spreadType;
public final double frequency;
public final int locateOffsetX;
public final int locateOffsetZ;

// UPDATE constructors (add full constructor, keep simple one with defaults)

// UPDATE affected structure entries:
OCEAN_MONUMENT(..., SpreadType.TRIANGULAR, 1.0, 0, 0),
WOODLAND_MANSION(..., SpreadType.TRIANGULAR, 1.0, 0, 0),
PILLAGER_OUTPOST(..., SpreadType.LINEAR, 0.2, 0, 0),
BURIED_TREASURE(..., SpreadType.LINEAR, 0.01, 9, 9),
MINESHAFT(..., SpreadType.LINEAR, 0.004, 0, 0),
```

### File 2: `mod/src/main/java/dev/seedfinder/finder/StructureFinder.java`

**Lines added:** ~35
**Lines changed:** ~15
**Risk:** MEDIUM (core algorithm changes)

Changes needed:
1. `scatterCandidate()` -- add triangular spread logic, frequency gating, locate offset
2. `nearestScatter()` -- handle null returns from scatterCandidate
3. `nearestStronghold()` -- fix jitter multiplier (48 -> 96), fix Y coordinate (0 -> 64)
4. `nearest()` -- pass full StructureType to scatterCandidate for new fields

### File 3: `mod/src/main/java/dev/seedfinder/gui/StructurePickerScreen.java`

**Lines added:** ~5
**Lines changed:** ~3
**Risk:** LOW (UI messaging only)

Changes needed:
1. Better error message when all candidates are filtered by frequency
2. Consider adding a retry with larger radius for low-frequency structures

---

## 6. Testing Strategy

### Test 1: Stronghold verification

Pick a known seed (e.g., `12345`), find nearest stronghold:
- **Before fix:** Likely wrong by 500+ blocks
- **After fix:** Should match Chunkbase within 16 blocks

### Test 2: Ocean Monument verification

Pick a seed, find nearest monument:
- **Before fix:** Offset by 0-50 chunks due to missing triangular spread
- **After fix:** Should match Chunkbase exactly

### Test 3: Buried Treasure verification

Pick a seed, find nearest treasure:
- **Before fix:** Off by 144 blocks (missing locate offset)
- **After fix:** Should match Chunkbase

### Test 4: Frequency gating

Pick seed `12345`, search for Pillager Outpost:
- **Before fix:** Returns every candidate position (too many)
- **After fix:** Returns only the 1 in 5 that actually spawn

### Test 5: Y coordinate

Place stronghold waypoint:
- **Before fix:** Beam goes to Y=0 (bedrock)
- **After fix:** Beam goes to Y=64 (surface-ish)

---

## 7. Future Roadmap

### v1.1 (this plan) -- Algorithm accuracy
- [ ] Fix stronghold jitter (Bug #1)
- [ ] Add triangular spread (Bug #2)
- [ ] Add frequency gating (Bug #3)
- [ ] Add locate offset (Bug #4)
- [ ] Fix stronghold Y coordinate (Bug #5)
- [ ] Add missing data fields (Bug #6)

### v1.2 -- Performance
- [ ] Add distance-based culling to WaypointRenderer (skip labels >500 blocks)
- [ ] Reuse BufferBuilder instead of nulling it each frame
- [ ] Cache structure lookups for repeated queries

### v1.3 -- UX
- [ ] Show "candidate" disclaimer for unverified placements
- [ ] Add biome validation (approximate, reduces false positives from ~5% to ~1%)
- [ ] Show all waypoints in HUD (not just nearest)
- [ ] Add waypoint persistence across sessions

### v1.4 -- Port to 26.x
- [ ] Migrate from Yarn to Mojang mappings (Fabric's new recommendation)
- [ ] Replace `HudRenderCallback` with `HudElementRegistry`
- [ ] Handle unobfuscated Minecraft (26.1+)
- [ ] Test against 26.1+ snapshots

---

## Appendix: References

1. **Minecraft Wiki -- Structure set:** https://minecraft.wiki/w/Structure_set
2. **Minecraft Wiki -- Stronghold:** https://minecraft.wiki/w/Stronghold
3. **Fabric develop page:** https://fabricmc.net/develop/
4. **Fabric blog 1.21.11:** https://fabricmc.net/2025/12/05/12111.html
5. **Chunkbase:** https://www.chunkbase.com/apps/seed-map

---

*Plan generated by KIMI on 2026-07-13. All data sourced from official Minecraft Wiki and Fabric documentation.*
