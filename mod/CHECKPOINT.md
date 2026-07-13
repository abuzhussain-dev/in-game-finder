# SeedFinder Mod — CHECKPOINT

## Active Task
Pre-push code verification — two compile bugs found and fixed.

## All Changes (Session 2026-07-13)

### Bug Fixes

| # | File | Bug | Fix |
|---|------|-----|-----|
| 1 | `WaypointRenderer.java:289` | HUD arrows 90° rotated | Added `- 90` offset to `relative` angle calculation |
| 2 | `StructurePickerScreen.java:270` | NPE if player disconnects mid-async-search | Added `if (client.player == null) return;` |
| 3 | `SeedFinderMod.java:52-58` | Floating SF button rendered but non-clickable on mobile | Added `mouse.wasLeftButtonClicked()` hit-test |
| 4 | `StructureType.java` (+ `StructureFinder.java`) | Nether Fortress & Bastion share same salt (nether_complexes) | Added `sharedSaltGroup` field + independent split LCG |
| 5 | `StructurePickerScreen.java:148` | Missing `));` closing TextFieldWidget constructor | Changed `Text.literal("Search..."),` → `Text.literal("Search..."));` |
| 6 | `StructureType.java:46` | END_CITY missing `sharedSaltGroup=0` — only 10 args, constructor needs 11 | Added `, 0` after `Dimension.END` |

### GUI Polish (new)

| # | What | Details |
|---|------|---------|
| 1 | **StructurePickerScreen full rewrite** | Vanilla widgets, no libs needed |
|   | → Tab filtering | Overworld / Nether / End / All |
|   | → Live search field | Filters by name + aliases (ac, tc, po, etc.) |
|   | → Tooltips on hover | Shows spacing, separation, dimension |
|   | → Waypoint indicators | ✓ checkmark on already-found types |
|   | → Results panel | Scrollable, shows X/Z/dist/direction per result |
|   | → [X] remove button per result | Removes waypoint + closes result |
|   | → Mouse scroll support | Scrolls structure list + results |
|   | → Width-responsive layout | 1 column on mobile, 2 on desktop |
| 2 | **TouchUtil.java** (new) | Shared touch detection replaces duplicate code |
| 3 | **SeedFinderConfigScreen.java** (new) | Vanilla SliderWidget for search radius (100-10000) |
| 4 | **SeedFinderMod.java** improved | Better floating button (dark blue + border), window resize callback, uses TouchUtil |
| 5 | **WaypointRenderer.java** HUD improved | Shows coords + waypoint count, click-to-remove from HUD |
| 6 | **SeedFinderConfig.java** updated | Added `setSearchRadius()` + `hasSeed()` |
| 7 | **No translatable keys** | All `Text.translatable` → `Text.literal` (no lang file dep) |

### File Manifest

```
src/main/java/dev/seedfinder/
  SeedFinderMod.java              — Entry, G keybind, SF button (mobile)
  command/SeedFinderCommand.java  — /seedfinder open|seed|clear
  config/SeedFinderConfig.java    — Properties-backed config (seed, radius)
  finder/StructureFinder.java     — Scatter/stronghold locator, inline LCG, shared salt
  finder/StructureType.java       — 18 types, sharedSaltGroup field
  gui/StructurePickerScreen.java  — Full GUI: tabs, search, tooltips, scroll, results
  gui/SeedFinderConfigScreen.java — Radius slider screen
  util/TouchUtil.java             — Shared touch detection
  waypoint/WaypointRenderer.java  — Custom RenderPipeline, NO_DEPTH_TEST, 32-block beams
  waypoint/WaypointStore.java     — Thread-safe JSON persistence

.claude/
  CLAUDE.md            — AI context
  commands/build.json  — Build slash command
  commands/check-render.json — Render review slash command

.claudeignore          — Context window filter
CHECKPOINT.md          — This file (project state)
```

### StructureType.sharedSaltGroup Design
- `sharedSaltGroup = 0` → unique type, frequency gating uses existing LCG state
- `sharedSaltGroup > 0` → shares salt/spacing/separation, frequency encodes split ratio
- Independent LCG seeded from `regionX * 341873128712L + regionZ * 132897987541L + seed + salt + 9999L`
- Nether Fortress: `frequency=0.4, sharedSaltGroup=1`
- Bastion Remnant: `frequency=0.6, sharedSaltGroup=1`

### Build Details
- Minecraft 1.21.11, Fabric Loader 0.18.1, Fabric API 0.141.4
- Yarn mappings 1.21.11+build.6, Java 21, Gradle 8.11
- **No external Maven deps** — pure Fabric API + vanilla mc
- Branch: v2
