# SeedFinder Mod — Session Checkpoint

## Project
Minecraft Fabric 1.21.11 mod that finds vanilla structures in-game from a known seed.

## Versions (web-searched, verified against Fabric Maven + example-mod)
| Dep | Value | Status |
|-----|-------|--------|
| Minecraft | 1.21.11 | ✅ |
| Fabric Loom | 1.15-SNAPSHOT | ✅ (works with Gradle 8.x) |
| Fabric Loader | 0.18.1 | ✅ |
| Fabric API | 0.141.4+1.21.11 | ✅ |
| Yarn Mappings | 1.21.11+build.6 | ✅ |
| Java | 21 | ✅ |
| Gradle | latest (9.x via CI) | ✅ |
| `~1.21.11` dep range | fabric.mod.json | ✅ |

## Files Modified (step 1 of 8 — DONE)
- `mod/gradle.properties` → mc 1.21.11, yarn 1.21.11+build.6, loader 0.18.1, api 0.141.4+1.21.11 ✅
- `mod/build.gradle` → fabric-loom '1.15-SNAPSHOT' ✅
- `mod/src/main/resources/fabric.mod.json` → minecraft dep "~1.21.11" ✅
- `.github/workflows/build-mod.yml` → gradle wrapper `--gradle-version latest` ✅

## Gradle Wrapper (step 2 — DONE)
- `gradle wrapper --gradle-version 8.11` generated ✅
- CI workflow runs `gradle wrapper --gradle-version latest` (uses GitHub-hosted Gradle, not local)

## Plan (8 steps)
1. ✅ [done] Update build files to 1.21.11
2. ✅ [done] Generate Gradle wrapper
3. 🔄 [build] Verify build compiles — CI will handle this on push (local env has old Gradle 4.4.1)
4. 🔲 [code] Fix stronghold rings (8 rings, 128 total)
5. 🔲 [code] Add GUI search bar
6. 🔲 [code] Add waypoint labels
7. 🔲 [code] Add HUD overlay
8. 🔲 [docs] Update AGENTS.md with web-search-first rule

## Key Source Files
| File | What it does |
|------|-------------|
| `SeedFinderMod.java` | ClientModInitializer, keybind G, registrations |
| `SeedFinderCommand.java` | `/seedfinder open`, `seed <long>`, `clear` |
| `SeedFinderConfig.java` | Properties config → `config/seedfinder.properties` |
| `StructurePickerScreen.java` | GUI with 18 structure buttons |
| `StructureFinder.java` | Scatter algorithm + stronghold ring |
| `StructureType.java` | 18 types with spacing/separation/salt |
| `WaypointRenderer.java` | Colored beacon beam through walls |
| `WaypointStore.java` | Thread-safe in-memory waypoints (has label field!) |
| `en_us.json` | All translations (includes search string) |

## Known Issues
- Build must happen via GitHub CI (local Gradle 4.4.1 incompatible)
- Need to push to GitHub and verify workflow runs
- Stronghold: only ring 1 (3 of 128) implemented — need all 8 rings
- GUI: no search/filter for 18 buttons
- Waypoints: has label field in Waypoint record, but renderer ignores it — no text shown
- HUD: no direction/distance overlay for nearest waypoint
- AGENTS.md: only has Lovable instructions, no web-search rule
