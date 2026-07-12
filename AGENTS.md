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

## Checkpoint Rule (MANDATORY)

Every change, progress update, problem, or decision MUST be written to `CHECKPOINT.md` immediately. This ensures context survives compaction, crashes, or session restarts.

Requirements:
1. Update `CHECKPOINT.md` after EVERY change to any file — code, config, docs, anything
2. Include: what changed, why, file paths, exact versions/branches, any errors encountered and their fixes
3. At the start of every session, READ `CHECKPOINT.md` and the plan file before doing anything else
4. Track all plan steps with clear ✅/🔄/❌ status markers
5. If CI fails: record the error, the fix attempted, and whether the re-run passed
