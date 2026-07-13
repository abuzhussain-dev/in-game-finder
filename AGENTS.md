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

## Checkpoint Rule — MANDATORY, NO EXCEPTIONS

Every progress made in this project MUST be written to `CHECKPOINT.md`. The file is the single source of truth — if it's not in CHECKPOINT.md, it didn't happen.

### Requirements

1. **Update after EVERY action** — feature done, bug fixed, file created, error hit, CI run, anything
2. **Write ALL of:**
   - **What changed** — file paths, diffs or summaries
   - **Why** — user request or reason behind the change
   - **Tools used** — which tools and results
   - **User statements** — exact quotes of requests
   - **Active task** — what's being worked on right now
   - **Errors** — exact messages, what was tried, what fixed it
   - **Versions** — dep versions, commit SHAs, branch names
3. **At session start: read CHECKPOINT.md first** before anything else
4. **Track all plan steps** with ✅ (done) / 🔄 (in progress) / ❌ (failed) / ⏳ (pending)
5. **Keep the COMPLETE FILE MAP updated** when files are added/removed
6. **Before session end: ensure CHECKPOINT.md is fully up to date, commit and push**
