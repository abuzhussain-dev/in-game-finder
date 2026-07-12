# SeedFinder — Fabric 1.21.1

Find any vanilla structure in-game from a known server seed. No Chunkbase, no alt-tab.

## Build

Requires JDK 21.

```bash
cd mod
# first time only — generates the gradle wrapper
gradle wrapper --gradle-version 8.10
./gradlew build
```

Output: `build/libs/seedfinder-1.0.0.jar`.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft **1.21.1**.
2. Drop the jar into `.minecraft/mods/` alongside **Fabric API**.
3. Launch Minecraft.

## Use

1. In-game, run `/seedfinder seed <your server seed>` (saved to `config/seedfinder.json`).
2. Press **G** (rebindable in Controls) or run `/seedfinder open`.
3. Click a structure. The nearest instance's coordinates print in chat and a waypoint beam renders in-world.
4. `/seedfinder clear` removes waypoints.

## How it works

Mojang's structure placement is deterministic from `(seed, chunkX, chunkZ, salt)`. This mod ports the well-known scatter algorithm (used by villages, outposts, temples, monuments, mansions, ancient cities, trial chambers, portals, shipwrecks, buried treasure, nether fortresses, bastions, end cities) plus the stronghold ring algorithm. **Client-only** — works on any server as long as you know the seed. Biome validity check is approximate for v1; results are candidate placements — a small number may be skipped by the server's actual biome. Combine with `/locate` if you have permission.

## License

MIT.