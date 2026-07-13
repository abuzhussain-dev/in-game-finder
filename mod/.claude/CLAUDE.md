# SeedFinder Mod

## Stack
- Minecraft 1.21.11, Fabric Loader 0.18.1, Fabric API 0.141.4
- Yarn mappings 1.21.11+build.6, Java 21, Gradle 8.11
- Branch: v2 (never main). CI builds only, no local gradle.

## Files
- `dev.seedfinder.SeedFinderMod` — entry, G keybind
- `dev.seedfinder.finder.StructureFinder` — vanilla-accurate seeded Random locator
- `dev.seedfinder.finder.StructureType` — 16 types with salt/spacing/offset/freq
- `dev.seedfinder.gui.StructurePickerScreen` — async search GUI with tabs
- `dev.seedfinder.waypoint.WaypointRenderer` — custom RenderPipeline, NO_DEPTH_TEST, 32-block beams
- `dev.seedfinder.waypoint.WaypointStore` — thread-safe JSON persistence
- `dev.seedfinder.config.SeedFinderConfig` — seed saved on screen close

## Rules
- All work on v2. Never touch main.
- Never run gradlew locally.
- Render code must have `client.world != null` guards.
- Verify salt/spacing/frequency in StructureType.java before changing structure logic.
- GUI must fit mobile (Zalith Launcher 2) — single-column, larger buttons.
