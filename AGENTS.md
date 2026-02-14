# AGENTS.md

## Mission
Maintain Nick Hider as a user-first Minecraft mod project. Keep documentation accurate and keep both `README.md` and `README_PROVIDERS.md` continuously up to date without waiting for explicit user requests.

## Auto-Loaded Rules (Highest Priority In This Repo)
1. Always evaluate whether `README.md` and `README_PROVIDERS.md` need updates in every task that changes behavior, compatibility, metadata, distribution, setup, or docs.
2. If any README-relevant fact changes, update both files in the same task before finishing (unless a section is intentionally provider-only or repo-only).
3. Never leave stale version/support/install/config/link info in either README file.
4. If uncertain whether README is impacted, assume yes and verify.

## Project Snapshot
- Mod name: `Nick Hider`
- Mod id: `nickhider`
- Current version source of truth: `gradle.properties` (`mod.version`)
- Description source of truth: `gradle.properties` (`mod.description`)
- Icon source: `src/main/resources/assets/nickhider/icon.png`
- Supported loaders and metadata:
- Fabric: `src/main/resources/fabric.mod.json`
- Forge: `src/main/resources/META-INF/mods.toml`
- NeoForge: `src/main/resources/META-INF/neoforge.mods.toml`
- Release/download source: GitHub Releases

## README Ownership Policy
Treat `README.md` as a maintained product page first, developer guide second.
Treat `README_PROVIDERS.md` as a curated provider-facing variant with the same factual core.

Required sections to preserve (unless user asks otherwise):
1. Hero (name + value proposition)
2. Icon and badges
3. Download links (Modrinth, CurseForge, GitHub Releases)
4. What the mod does
5. Quick start tutorial
6. Features at a glance
7. Compatibility summary
8. Configuration summary
9. Known limits
10. Compact developer section
11. License and maintainer links

## README Auto-Maintenance Triggers
Update both README files whenever changes touch any of these:
- `gradle.properties` (`mod.version`, `mod.name`, `mod.description`)
- Loader metadata files (`fabric.mod.json`, `mods.toml`, `neoforge.mods.toml`)
- Minecraft version support or loader matrix
- Config options, defaults, validation rules, behavior/fallbacks
- Install/build/test commands
- Release/publish workflow behavior in `.github/workflows/`
- Icon/logo path changes
- Repository URLs, issues URL, or license text

## How To Maintain README (Execution Checklist)
1. Read current `README.md`.
2. Read current `README_PROVIDERS.md`.
3. Collect new facts from source-of-truth files above.
4. Patch only what changed; keep user-facing tone and scannable structure.
5. Verify links are valid or clearly marked placeholders.
6. Ensure no contradictions between README files and code/config metadata.
7. If README files are unaffected, explicitly state why in final response.

## Content Quality Bar
- Prefer concise bullets/tables over long prose.
- Keep claims factual and implementation-aligned.
- Keep developer internals short; link to deeper paths where possible.
- Avoid marketing fluff that cannot be verified.

## Commit Conventions
- If only README/docs changed: use `docs(...)` commit type.
- If code + README changed: commit together when part of one logical change.
- Suggested scopes: `readme`, `docs`, `meta`.

## Agent Behavior Notes
- Do not wait for user prompt to maintain README; apply proactively.
- Keep shared facts synchronized across both README files (version, compatibility range, loader support, config rules, core behavior).
- Allow intentional differences: provider formatting, icon URL style, and section selection.
- Do not remove placeholder download links unless real URLs are known.
- Keep this file under 200 lines.
- If future project structure changes, update this file first, then both README files.
