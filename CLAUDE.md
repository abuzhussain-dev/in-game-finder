# In-Game Finder — Project Instructions

## Tech Stack
- **Frontend**: TypeScript + TanStack Start (SSR) + TanStack Router + React 19 + Tailwind v4 + shadcn/ui
- **Minecraft Mod**: Java 21 + Fabric Loom 1.7 + Minecraft 1.21.1
- **Build**: Bun (frontend) + Gradle 8.10 (mod)
- **MCP**: @lovable.dev/mcp-js exposes structure-finding as MCP tools

## Code Style
- **TypeScript**: camelCase files and variables, PascalCase components, strict mode
- **Java**: PascalCase classes, camelCase methods, package `dev.seedfinder.*`
- **No testing framework** configured yet
- **Formatting**: Prettier (frontend), no auto-formatter for mod

## Structure-Finding Algorithm
Both TS and Java implement Mojang's scatter algorithm using Java Random LCG:
- `src/lib/mcp/structures.ts` — TypeScript port using BigInt
- `mod/src/main/java/dev/seedfinder/finder/StructureFinder.java` — Java original
- Keep both in sync when changing structure parameters or algorithm logic

## Build & Run
- Dev server: `bun run dev`
- Build frontend: `bun run build`
- Lint: `bun run lint`
- Format: `bun run format`
- Build mod: `cd mod && ./gradlew build`

## Project Structure
- `src/` — TanStack Start frontend (file-based routes, MCP tools, shadcn/ui components)
- `mod/` — Fabric mod (Gradle, Java source, fabric.mod.json)
- `.github/workflows/build-mod.yml` — CI builds mod on pushes to `mod/`

## Conventions
- **Git**: single `main` branch. Do NOT force-push, rebase, or amend published commits — Lovable.dev syncs from git history
- **MCP tools**: defined in `src/lib/mcp/tools/` with Zod schema, registered via `src/lib/mcp/index.ts`
- **Structure data**: both `StructureType.java` (enum) and `STRUCTURES` (TS array) must mirror vanilla 1.21.1 values
- **Error handling**: SSR uses try/catch with error page fallback; mod silences IO errors in config load
