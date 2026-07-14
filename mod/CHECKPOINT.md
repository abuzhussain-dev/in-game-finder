# SeedFinder Mod — CHECKPOINT

## Active Task
StructurePickerScreen complete rewrite — radar map, Find All, click-to-toggle waypoints, sort controls, radius presets, TP/Copy buttons, nether coords, clipboard. Waiting for CI build verification.

### Current Changes (uncommitted)

| # | File | Changes |
|---|------|---------|
| 1 | `StructurePickerScreen.java` | Complete rewrite (~1100 lines) — see below |
| 2 | `CHECKPOINT.md` | This update |

### StructurePickerScreen.java — New Features

| Feature | Details |
|---------|---------|
| **Find All in Radius** | Searches all 18 structure types in parallel via `CompletableFuture`. Capped at 150/type, 1000 total. Debounced. Shows "Searching..." + remaining types. |
| **Radar Map View** | Toggle [List]/[Map] in results panel header. Player at center (cross marker + "You"). Structure dots colored by type. Distance rings at 250/500/1000 blocks (circle approximation via perimeter dots). N/S/E/W cardinal labels. Hover dot → tooltip (name, distance, direction, coords). Click dot → toggle waypoint. Scroll to zoom (1.2x factor). Scale indicator bottom-left. |
| **Click-to-toggle waypoint** | Click any result row (list view) or dot (map view) → adds waypoint if not waypointed, removes if already. Visual feedback: checkmark prefix on label, color-coded top border for waypointed rows. |
| **Sort controls** | Header button cycles: Dist ▼ / Type ▼ / Dim ▼. Results re-sort in-place. |
| **[TP] button** | Per-row teleport button in list view. Only visible in creative mode. Runs `/tp @p X ~ Z`. Green text, brighter on hover. |
| **[Copy] button** | Per-row copy button → clipboard via `Keyboard.setClipboard()`. "Copied!" toast overlay (1.5s). |
| **[Copy All] button** | Copies all results as formatted string: `"Name" at X:... Z:... | ...`. Shows "Copied all (N results)" toast. |
| **Nether coords** | Shows `(N:X-30 Z-4)` on second line of each result row. Only for OVERWORLD/ALL dimension structures. |
| **Radius presets** | 4 small inline buttons: `[500][1k][2k][5k]`. Click → updates config + Find All button label dynamically via `setMessage()`. |
| **Bottom bar** | Reorganized: `[Find All (640 ch)] [500][1k][2k][5k] [Clear] [Close]` |
| **Removed 20-result cap** | Per-type search now shows up to 200 nearest instances. Virtual scrolling handles large lists. |
| **Clipboard toast** | Centered dark overlay showing "Copied!" or "Copied all..." for 1.5s. |
| **Row highlighting** | Hovered rows get subtle white overlay. Waypointed rows have color-coded top border. |
| **Map scroll-to-zoom** | Mouse wheel in map view adjusts `mapScale` (0.25–20.0 range, 1.2x factor). |
| **Preserved** | `resolveStructure()` unchanged (used by commands). `cardinalDirection()` unchanged. Tab/search/filter system unchanged. Seed auto-detect unchanged. `removeResult()` unchanged. Structure button grid unchanged. Touch device support kept.

## All Changes (Session 2026-07-13 — resumed)

### Bug Fixes (previous)

| # | File | Bug | Fix |
|---|------|-----|------|
| 1 | `WaypointRenderer.java:289` | HUD arrows 90° rotated | Added `- 90` offset to `relative` angle calculation |
| 2 | `StructurePickerScreen.java:270` | NPE if player disconnects mid-async-search | Added `if (client.player == null) return;` |
| 3 | `SeedFinderMod.java:52-58` | Floating SF button rendered but non-clickable on mobile | Added `mouse.wasLeftButtonClicked()` hit-test |
| 4 | `StructureType.java` (+ `StructureFinder.java`) | Nether Fortress & Bastion share same salt (nether_complexes) | Added `sharedSaltGroup` field + independent split LCG |
| 5 | `StructurePickerScreen.java:148` | Missing `));` closing TextFieldWidget constructor | Changed `Text.literal("Search..."),` → `Text.literal("Search..."));` |
| 6 | `StructureType.java:46` | END_CITY missing `sharedSaltGroup=0` — only 10 args, constructor needs 11 | Added `, 0` after `Dimension.END` |
| 7 | `SeedFinderMod.java:39,93-94` | `Window.setCallback()`/`getCallback()` don't exist in 1.21.11 Yarn mappings | Replaced with `GLFW.glfwSetWindowSizeCallback()` chaining |
| 8 | `StructurePickerScreen.java:248` | `remove(structureButtons.remove(...))` type mismatch | Added `.button()` to unwrap `StructureButton` record |
| 9 | `StructurePickerScreen.java:275-278` | StructureButton record mismatches in `rebuildButtons()` | Added `.button()`, deleted dead code, wrapped `new StructureButton(btn, t)` |

### GUI Polish + Build Changes (current session)

| # | What | Details |
|---|------|---------|
| 1 | **Cloth Config integration** | `build.gradle`: added shedaniel maven repo + `cloth-config-fabric:15.0.0+1.21.11` dep + `include` for JAR-in-JAR |
| 2 | **`gradle.properties`** | Added `cloth_version=15.0.0+1.21.11` |
| 3 | **`SeedFinderConfigScreen.java` rewrite** | Replaced vanilla SliderWidget with Cloth Config `ConfigBuilder` — auto-generated UI, same save/load |
| 4 | **`StructurePickerScreen.java`** | Updated settings button to use `SeedFinderConfigScreen.create(this)` factory |
| 5 | **`WaypointRenderer.java` — compass bar** | New `renderCompassBar()` — top-center bar showing N/NE/E/SE/S/SW/W/NW with waypoint bearing ticks |
| 6 | **`WaypointRenderer.java` — drawn indicators** | Replaced unicode arrows (`↑↗→↘↓↙←↖`) with `drawDirectionIndicator()` — rotated arrow via `MatrixStack` + `RotationAxis` |
| 7 | **`WaypointRenderer.java` — bearing math** | `relativeAngle()` computes precise bearing delta for direction indicator; `bearingFromNorth()` for compass |
| 8 | **`SeedFinderMod.java` — floating button** | Drop shadow + hover feedback (brighter when mouse over) + border glow on hover |
| 9 | **No translatable keys** | All `Text.translatable` → `Text.literal` (no lang file dep) |

### File Manifest

```
src/main/java/dev/seedfinder/
  SeedFinderMod.java              — Entry, G keybind, SF button (mobile + hover)
  command/SeedFinderCommand.java  — /seedfinder open|seed|clear
  config/SeedFinderConfig.java    — Properties-backed config (seed, radius)
  finder/StructureFinder.java     — Scatter/stronghold locator, inline LCG, shared salt
  finder/StructureType.java       — 18 types, sharedSaltGroup field
  gui/StructurePickerScreen.java  — Full GUI: tabs, search, tooltips, scroll, results
  gui/SeedFinderConfigScreen.java — Cloth Config screen (ConfigBuilder)
  util/TouchUtil.java             — Shared touch detection
  waypoint/WaypointRenderer.java  — Custom RenderPipeline, NO_DEPTH_TEST, 32-block beams,
                                    compass bar HUD, drawn direction indicators
  waypoint/WaypointStore.java     — Thread-safe JSON persistence

.claude/
  CLAUDE.md            — AI context
  commands/build.json  — Build slash command
  commands/check-render.json — Render review slash command

.claudeignore          — Context window filter
CHECKPOINT.md          — This file (project state, session 2026-07-13)
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
- Cloth Config 15.0.0+1.21.11 (JAR-in-JAR via `include`)
- Branch: v2

### User Request (session summary)
- User analyzed 3 options for GUI libs and recommended **Option 3 (Vanilla + Cloth Config)**:
  - Rejected Vexel (unknown author)
  - Rejected SGL (CDN download hack, fragile)
  - Approved Cloth Config (battle-tested, on Maven Central)
  - "Go Option 3. The StructurePickerScreen rewrite without SGL is maybe +50 lines over the SGL version — not enough to justify a dependency on a CDN-downloaded library or an unknown author's Maven server."
