<!-- LOVABLE:BEGIN -->
> [!IMPORTANT]
> This project is connected to [Lovable](https://lovable.dev). Avoid rewriting
> published git history — force pushing, or rebasing/amending/squashing commits
> that are already pushed — as it rewrites history on Lovable's side and the
> user will likely lose their project history.
>
> Commits you push to the connected branch sync back to Lovable and show up in
> the editor, so keep the branch in a working state.
<!-- LOVABLE:END -->

## Web Search Rule (MANDATORY)

When researching API versions, dependency versions, Minecraft version info, or any technical information that may have changed after the model's training cutoff:
1. ALWAYS use web search — never rely on training data
2. Search with EXACT version numbers (e.g., "1.21.11" not "1.21")
3. Verify from official sources: Fabric Maven, FabricMC GitHub, Minecraft Wiki
4. For fabric-loom/fabric-api/fabric-loader versions, check the Fabric Maven repository directly
5. Cross-reference between web search and maven-metadata.xml for version accuracy

## mcmodding-mcp Rule (MANDATORY)

When working on Minecraft modding tasks (Fabric/NeoForge), ALWAYS use mcmodding-mcp MCP tools. DO NOT rely on training data — APIs change frequently.

Available tools:
- `search_fabric_docs` — Search Fabric documentation for guides and API info
- `get_example` — Get code examples for specific topics (e.g., "register item", "world render")
- `explain_fabric_concept` — Get explanations of modding concepts
- `search_mappings` — Search Minecraft mappings for accurate class/method names
- `get_class_details` — Get class details with Javadocs
- `search_mod_examples` — Battle-tested implementations from popular mods

Workflow:
1. Search docs first with `search_fabric_docs` or `get_example`
2. Use `get_example` with loader="fabric", minecraft_version="1.21.11" for version-accurate code
3. Prefer working code examples over theoretical explanations

## Checkpoint Rule (MANDATORY)

Every change, progress update, problem, decision, user request, tool invocation, and result MUST be written to `CHECKPOINT.md` immediately. This ensures context survives compaction, crashes, session restarts, and context-window limits. No detail is too small — if the model knows it, CHECKPOINT.md must contain it.

### Requirements

1. **Update `CHECKPOINT.md` after EVERY action** — code change, config change, CI run, web search, tool invocation, error, anything
2. **Record ALL of the following in every entry:**
   - **What changed** — file paths, exact diffs or summaries
   - **Why** — the reason or user request that drove the change
   - **Tools used** — which tools were called (Bash, Read, Edit, WebFetch, WebSearch, gh, etc.) and their results
   - **User statements** — exact quotes of what the user said/requested
   - **Current task** — what I am actively working on right now
   - **Errors and fixes** — exact error messages, what was attempted, what fixed it
   - **Versions** — exact dependency versions, commit SHAs, branch names
3. **At the start of every session: READ `CHECKPOINT.md` and the plan file FIRST** before doing anything else
4. **Track all plan steps** with clear ✅ (done) / 🔄 (in progress) / ❌ (failed) / ⏳ (pending) status markers
5. **CI tracking:** record every run ID, commit SHA, status (✅/❌/🔄), error log, and fix attempted
6. **Before context compaction or session end:** ensure CHECKPOINT.md is fully up to date
