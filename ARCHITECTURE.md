# SeedFinder — Architecture Graph

> Auto-generated from source. Read this when context is lost.

## 1. System Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    SeedFinder Project                            │
│  Repo: github.com/abuzhussain-dev/in-game-finder                │
│  Branch: main (v1 done), v2 (active dev)                        │
│  MC: 1.21.11  Fabric: 0.141.4  Loader: 0.18.1  Loom: 1.15      │
│  Yarn: 1.21.11+build.6  Java: 21  Gradle: 9.6.1 (CI)           │
└─────────────────────────────────────────────────────────────────┘
```

## 2. Mod Architecture (Java/Fabric)

```
┌────────────────────────────────────────────────────────────────────────────┐
│                     FABRIC MOD (client-side only)                          │
│  mod/src/main/java/dev/seedfinder/                                         │
│                                                                             │
│  ┌──────────────────────┐     ┌──────────────────────────────┐             │
│  │  SeedFinderMod.java  │────▶│  SeedFinderCommand.java      │             │
│  │  ─ entry point       │     │  ─ /seedfinder open          │             │
│  │  ─ keybind G         │     │  ─ /seedfinder seed <val>   │             │
│  │  ─ registers all     │     │  ─ /seedfinder clear         │             │
│  └──────────┬───────────┘     └──────────────────────────────┘             │
│             │                                                              │
│             │  opens GUI                                                   │
│             ▼                                                              │
│  ┌──────────────────────────────┐     ┌──────────────────────────────┐     │
│  │  StructurePickerScreen.java  │────▶│  SeedFinderConfig.java       │     │
│  │  ─ GUI with search bar       │     │  ─ seedfinder.properties    │     │
│  │  ─ lists all structure types │     │  ─ seed, radius              │     │
│  │  ─ filter by displayName     │     └──────────────────────────────┘     │
│  │  ─ color-coded waypoints     │                                          │
│  └──────────────┬───────────────┘                                          │
│                 │                                                          │
│         pick(t) │  finds structure                                         │
│                 ▼                                                          │
│  ┌──────────────────────────────┐     ┌──────────────────────────────┐     │
│  │  StructureFinder.java        │────▶│  StructureType.java          │     │
│  │  ─ nearest() dispatcher      │     │  ─ 18 structure enums        │     │
│  │  ─ nearestScatter(): LCG     │     │  ─ Placement (SCATTER,       │     │
│  │  ─ nearestStronghold(): rings│     │    STRONGHOLD_RING, PER_CHUNK)│     │
│  │  ─ allWithin(): batch search │     │  ─ spacing/separation/salt   │     │
│  │  ─ scatterCandidate(): core  │     └──────────────────────────────┘     │
│  └──────────────────────────────┘                                          │
│                 │                                                          │
│         returns BlockPos                                                    │
│                 │                                                          │
│                 ▼                                                          │
│  ┌──────────────────────────────┐     ┌──────────────────────────────┐     │
│  │  WaypointStore.java          │     │  WaypointRenderer.java       │     │
│  │  ─ List<Waypoint> (static)   │     │  ─ WorldRenderEvents.END_MAIN│     │
│  │  ─ add/clear/snapshot        │     │  ─ filled boxes (y=0→320)   │     │
│  │  ─ Waypoint(label,pos,color) │     │  ─ labels + distance at top  │     │
│  │  ─ thread-safe (synchronized)│     │  ─ HUD overlay top-left     │     │
│  └──────────────────────────────┘     └──────────────────────────────┘     │
│                                                                             │
│  YARN 1.21.11+build.6 quirks:                                              │
│  ─ BufferBuilder, BuiltBuffer, VertexFormat → net.minecraft.client.render  │
│  ─ BufferAllocator → net.minecraft.client.util                              │
│  ─ .vertex() / .color() (not addVertex/setColor)                           │
│  ─ END_MAIN (not AFTER_TRANSLUCENT)                                        │
│  ─ RenderSystem.ShapeIndexBuffer (not AutoStorageIndexBuffer)              │
│  ─ getBlocking() (not currentBuffer())                                     │
│  ─ RenderPass + pass.drawIndexed() (not BufferUploader)                    │
└────────────────────────────────────────────────────────────────────────────┘
```

## 3. Stronghold Ring Algorithm (BROKEN — Step 11)

```
Mojang 1.21.1 spec: 8 rings, 128 strongholds total
  Ring 0:  3 strongholds  distance =  128-176 chunks
  Ring 1:  6 strongholds  distance =  128+192-? chunks
  Ring 2: 10
  Ring 3: 15
  Ring 4: 21
  Ring 5: 28
  Ring 6: 36
  Ring 7:  9

Current code (StructureFinder.java:489-522):
  - Uses single Random(seed) — WRONG, each ring should use seed + salt
  - Angle starts once, increments per stronghold — WRONG, each ring re-derives
  - Single .nextDouble() for all distance jitter — WRONG, per-ring
  - Result: positions don't match vanilla / Chunkbase

Fix needed: Rewrite nearestStronghold() with per-ring random + proper angles
```

## 4. Web Frontend (TypeScript/TanStack)

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    WEB FRONTEND (TanStack Start + SSR)                     │
│  src/                                                                      │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │  MCP Layer (@lovable.dev/mcp-js)                                     │  │
│  │                                                                       │  │
│  │  src/lib/mcp/index.ts ─── defines MCP server "seedfinder-mcp"        │  │
│  │       │                                                               │  │
│  │       ├── tools/list-structures.ts    ─ list all structure IDs       │  │
│  │       ├── tools/find-structure.ts     ─ nearest by seed + XZ         │  │
│  │       ├── tools/list-waypoints.ts     ─ list saved waypoints         │  │
│  │       ├── tools/add-waypoint.ts       ─ save a waypoint              │  │
│  │       └── tools/clear-waypoints.ts    ─ clear all waypoints          │  │
│  │       │                                                               │  │
│  │       └── structures.ts ─ core algorithm (BigInt port of Java LCG)   │  │
│  │           └── JavaRandom class (LCG: 0x5DEECE66D, 0xB, 48-bit)       │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │  Routes (TanStack Router, file-based)                                │  │
│  │  src/routes/                                                          │  │
│  │    __root.tsx           ─ root layout + meta                         │  │
│  │    index.tsx            ─ home page (placeholder)                    │  │
│  │    mcp.ts               ─ MCP page                                   │  │
│  │    [.mcp]/list-tools.ts ─ MCP tool listing endpoint                  │  │
│  │    [.mcp]/invoke-tool/$tool.ts ─ MCP invoke endpoint                 │  │
│  │    [.well-known]/oauth-protected-resource.ts ─ OAuth                 │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │  Shared Components (shadcn/ui)                                       │  │
│  │  src/components/ui/  ─ 48+ components (accordion, button, card, ...) │  │
│  │  src/hooks/use-mobile.tsx                                           │  │
│  │  src/lib/utils.ts                                                   │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────────┘
```

## 5. CI/CD Pipeline

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    GitHub Actions — build-mod.yml                          │
│                                                                             │
│  Triggers: push/PR to mod/** or build-mod.yml, workflow_dispatch           │
│                                                                             │
│  Steps:                                                                     │
│    1. checkout v4                                                          │
│    2. JDK 21 (temurin)                                                     │
│    3. Gradle setup (validate-wrappers: false)                              │
│    4. gradle wrapper --gradle-version latest                               │
│    5. ./gradlew build --stacktrace                                           │
│    6. Upload jar (mod/build/libs/*.jar)                                     │
│    7. (on tag) gh-release attach jar                                       │
│                                                                             │
│  Historical CI runs: 15 total (Runs 1-12 failed, Run 13+ green)           │
│  ─ Run 1:  Loom 1.17.14 incompatible with Gradle 8.x                     │
│  ─ Run 2:  Gradle wrapper JAR hash validation failed                     │
│  ─ Run 3:  15 Fabric API compilation errors                              │
│  ─ Runs 4-9:  Yarn 1.21.11+build.6 API migration (34→14→2→0 errors)    │
│  ─ Run 10:  Compile OK, jar FAILED (archivesName removed in Gradle 9.x) │
│  ─ Run 13:  GREEN BUILD ✅                                              │
│  ─ Runs 14-15:  StructureType experiment + revert, both green            │
└────────────────────────────────────────────────────────────────────────────┘
```

## 6. Data Flow Diagram

```
Player presses G
       │
       ▼
StructurePickerScreen opens
       │
       │  Player clicks a structure type
       ▼
StructureFinder.nearest(seed, type, playerX, playerZ, 200)
       │
       ├── SCATTER ──▶ nearestScatter()
       │                  │
       │                  ├── For each region in radius:
       │                  │     popSeed = rx*341873128712 + rz*132897987541 + seed + salt
       │                  │     Random(popSeed).nextInt(spacing - separation) → offset
       │                  │     candidate = (region*spacing + offset) * 16 + 8
       │                  │     keep nearest by squared distance
       │                  │
       │                  └── return BlockPos(bx, 64, bz)
       │
       └── STRONGHOLD ──▶ nearestStronghold() [BROKEN]
                            │
                            └── Uses single Random(seed), wrong ring math
                                  Need complete rewrite (Step 11)

       │
       ▼
BlockPos returned to StructurePickerScreen.pick()
       │
       ├── Send chat message: "Found Village at X=100 Z=200 (500m NW)"
       ├── WaypointStore.add(new Waypoint("Village", pos, color))
       └── close GUI

       │
       ▼
WaypointRenderer (WorldRenderEvents.END_MAIN)
       │
       ├── For each waypoint: render filled box y=0→320
       │     └── Uses RenderPass pipeline (GpuBuffer, MappableRingBuffer)
       │
       ├── For each waypoint: draw label + distance at y=320
       │
       └── HUD (top-left): nearest waypoint name + dist + direction
```

## 7. File Dependency Map

```
Mod Java files:
  SeedFinderMod.java
    ├── imports: SeedFinderCommand, SeedFinderConfig,
    │            StructurePickerScreen, WaypointRenderer
    ├── calls: SeedFinderCommand.register()
    ├── calls: SeedFinderConfig.load()
    ├── calls: WaypointRenderer.register()
    └── creates: StructurePickerScreen() on keypress

  SeedFinderCommand.java
    ├── imports: SeedFinderConfig, StructurePickerScreen, WaypointStore
    ├── /seedfinder open  → new StructurePickerScreen()
    ├── /seedfinder seed  → SeedFinderConfig.setSeed()
    └── /seedfinder clear → WaypointStore.clear()

  StructurePickerScreen.java
    ├── imports: SeedFinderConfig, StructureFinder{nearest},
    │            StructureType, WaypointStore.add()
    └── onPick: StructureFinder.nearest() → WaypointStore.add()

  StructureFinder.java
    └── imports: StructureType (enum values)

  WaypointRenderer.java
    └── imports: WaypointStore{snapshot, Waypoint}

  WaypointStore.java ─── standalone (no internal deps)

  SeedFinderConfig.java ─── standalone (no internal deps)

Web TS files:
  src/lib/mcp/index.ts
    ├── imports tools from: tools/list-structures.ts
    │                       tools/find-structure.ts
    │                       tools/list-waypoints.ts
    │                       tools/add-waypoint.ts
    │                       tools/clear-waypoints.ts
    └── exports: MCP server "seedfinder-mcp"

  src/lib/mcp/structures.ts
    └── standalone (all algorithm + JavaRandom class)

  src/lib/mcp/tools/find-structure.ts
    └── imports: findNearestStructure from structures.ts

  src/routes/[.mcp]/list-tools.ts
  src/routes/[.mcp]/invoke-tool/$tool.ts
    └── wire MCP tools to HTTP routes
```

## 8. Key Constants

```
Java LCG:  seed = (seed * 0x5DEECE66D + 0xB) & ((1 << 48) - 1)
Pop seed:  regionX * 341873128712L + regionZ * 132897987541L + worldSeed + structureSalt

Stronghold rings (vanilla 1.21.1):
  ringCounts = {3, 6, 10, 15, 21, 28, 36, 9}
  ring 0 distance: 128-176 chunks (128 + random(48))

COLORS (StructurePickerScreen):
  {0xFFFFAA00, 0xFF00FFAA, 0xFFAA00FF, 0xFFF3355, 0xFF33CCFF,
   0xFFFFFF55, 0xFF55FF55, 0xFFFF55FF, 0xFFAAAAAA, 0xFFF9900}

GUI layout:
  search field at top-center
  buttons: 3 per row, 160x20px each
  "Clear Waypoints" + "Close" at bottom
```

## 9. Known Issues

```
1. Stronghold ring algorithm BROKEN — positions don't match vanilla (Step 11)
2. Biome validation skipped — ~5% false positives on scatter structures
3. Spawn chunk finder not implemented (Step 12)
4. Web MCP structure tools incomplete (Step 13)
5. No lang file translation for en_us.json
6. Home page (index.tsx) is still placeholder image
7. No local build (mod/build/libs/ doesn't exist locally)
8. mcmodding-mcp MCP server non-functional
```

## 10. Complete File Index

```
/root/in-game-finder/
├── AGENTS.md                    ← Mandatory rules for AI agents
├── ARCHITECTURE.md              ← This file
├── CHECKPOINT.md                ← Full context dump + progress
├── CLAUDE.md                    ← Auto-generated project instructions
├── KIMI PLAN.md                 ← 529-line research doc (bug analysis)
├── PLAN/
│   └── seedfinder-MASTER-PLAN.md ← Full v1.0.0 implementation spec (~96KB)
│
├── .github/workflows/
│   └── build-mod.yml            ← CI: builds mod on push to mod/
│
├── mod/                         ← Fabric mod source
│   ├── build.gradle
│   ├── gradle.properties
│   ├── gradlew / gradlew.bat / gradle/wrapper/
│   └── src/main/
│       ├── resources/
│       │   ├── fabric.mod.json
│       │   └── assets/seedfinder/lang/en_us.json
│       └── java/dev/seedfinder/
│           ├── SeedFinderMod.java              (entry point)
│           ├── command/SeedFinderCommand.java   (commands)
│           ├── config/SeedFinderConfig.java     (config)
│           ├── finder/StructureFinder.java      (algorithm)
│           ├── finder/StructureType.java        (enums)
│           ├── gui/StructurePickerScreen.java   (GUI)
│           ├── waypoint/WaypointStore.java      (storage)
│           └── waypoint/WaypointRenderer.java   (rendering)
│
├── src/                         ← Web frontend (TanStack Start)
│   ├── server.ts
│   ├── start.ts
│   ├── router.tsx
│   ├── routeTree.gen.ts
│   ├── styles.css
│   ├── lib/
│   │   ├── utils.ts
│   │   ├── error-capture.ts
│   │   ├── error-page.ts
│   │   ├── lovable-error-reporting.ts
│   │   └── mcp/
│   │       ├── index.ts                     (MCP server def)
│   │       ├── structures.ts                (TS algorithm port)
│   │       └── tools/
│   │           ├── list-structures.ts
│   │           ├── find-structure.ts
│   │           ├── list-waypoints.ts
│   │           ├── add-waypoint.ts
│   │           └── clear-waypoints.ts
│   ├── components/ui/           (48 shadcn/ui components)
│   ├── hooks/use-mobile.tsx
│   └── routes/
│       ├── __root.tsx
│       ├── index.tsx
│       ├── mcp.ts
│       ├── [.mcp]/list-tools.ts
│       ├── [.mcp]/invoke-tool/$tool.ts
│       └── [.well-known]/oauth-protected-resource.ts
│
├── package.json
├── tsconfig.json
├── vite.config.ts
├── bun.lock
├── bunfig.toml
├── components.json
└── eslint.config.js
```
