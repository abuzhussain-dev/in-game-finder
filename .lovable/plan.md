## What we're building

A **Fabric mod for Minecraft 1.21.1** (Java) — "SeedFinder" — that lets you locate any vanilla structure in-game from a known server seed, without ever opening Chunkbase.

Flow in-game:

1. Press a keybind (default `G`) or run `/seedfinder`.
2. A GUI opens listing every vanilla structure (Village, Stronghold, Ocean Monument, Woodland Mansion, End City, Ancient City, Trial Chambers, Bastion, Nether Fortress, Pillager Outpost, Ruined Portal, Buried Treasure, Shipwreck, Igloo, Desert/Jungle Pyramid, Swamp Hut, Mineshaft, Witch Hut, etc.).
3. Click a structure → mod computes nearest instance from your XZ using the stored seed.
4. But it will be better if we use any api like of chunkbase or external (if work) so the device dont have to compute itself
5. A **glowing square waypoint** renders in the world at those coordinates (through-walls beacon beam + coord label), and chat prints `Nearest Village: X=1234, Z=-567 (312 blocks NE)`.

Seed is entered once in a config screen (`/seedfinder seed <long>`) and saved to `config/seedfinder.json`.

## Why this needs a separate build

This Lovable project is a TanStack Start **web app** sandbox — it can't compile Java or run Gradle. I'll deliver the mod as a **complete Gradle project (source + build files)** written into this repo under `mod/`. You then:

```
cd mod
./gradlew build     # produces build/libs/seedfinder-1.0.0.jar
```

Drop the jar in `.minecraft/mods/` alongside Fabric API. I'll include a README with exact steps.

## Structure location — how

Chunkbase has no public API, so we replicate the math locally. Mojang's structure placement is deterministic from `(seed, chunkX, chunkZ, structureType)`. Two proven options:

- **Preferred: Cubiomes-style port.** Use the **Amulet/Cubitick** approach — a pure-Java port of Cubiomes' structure seed checks (fast, no world load needed). Ships as a small `structures/` package in the mod.
- **Fallback: server-side `/locate`.** If we're on a server we don't own, wrap vanilla `/locate structure minecraft:village` — works but requires op or the command being allowed.

Default is the local Cubiomes port so it works on any server with just the seed.

## Files I'll create

```text
mod/
  build.gradle
  settings.gradle
  gradle.properties           # minecraft 1.21.1, yarn, fabric-loader, fabric-api, loom
  gradle/wrapper/...          # wrapper jars + script
  src/main/resources/
    fabric.mod.json
    seedfinder.mixins.json
    assets/seedfinder/lang/en_us.json
    assets/seedfinder/icon.png
  src/main/java/dev/seedfinder/
    SeedFinderMod.java              # ClientModInitializer, keybind, command registration
    config/SeedFinderConfig.java    # JSON load/save (seed, last results, waypoint color)
    command/SeedFinderCommand.java  # /seedfinder seed|find|clear
    gui/StructurePickerScreen.java  # Screen with searchable structure list
    gui/WaypointRenderer.java       # WorldRenderEvents.AFTER_TRANSLUCENT — beacon beam + label
    finder/StructureFinder.java     # public API: nearest(seed, x, z, type) -> BlockPos
    finder/StructureType.java       # enum of supported structures
    finder/impl/                    # per-structure placement + biome checks (Cubiomes port)
      VillageFinder.java
      StrongholdFinder.java
      MonumentFinder.java
      MansionFinder.java
      ... one file per structure
    finder/noise/                   # minimal biome-noise sampler needed for validity checks
```

## Scope for v1 vs later

**v1 (this plan):** Overworld surface structures where placement math is well-documented — Village, Pillager Outpost, Desert/Jungle Pyramid, Swamp Hut, Igloo, Witch Hut, Ocean Monument, Woodland Mansion, Ruined Portal, Shipwreck, Buried Treasure, Ancient City, Trial Chambers, Stronghold (ring algorithm), Nether Fortress, Bastion, End City.

**Later:** Mineshafts and Strongholds' *exact* staircase (needs full biome-noise validation — bigger port). Marked as "approximate" in v1 with a note in the GUI.

## Things to confirm before I code

1. **Minecraft version.** You wrote `1.21.11` — that version doesn't exist. Latest 1.21.x is **1.21.10**. I'll target **1.21.1** (most-modded, widest server compat) unless you say otherwise.
2. **Client-only or client+server?** Client-only means you install the jar solo, works on any server. Confirming client-only.
3. **License.** MIT unless you say otherwise.

Reply with the version + any changes and I'll switch to build mode to generate the whole `mod/` tree.