# Nick Hider

Client-side privacy mod for Minecraft that masks player names, skins and capes in common vanilla client paths.

Copyright (c) 2026 przxmus

## Current Support

- Minecraft range: `1.20` to `1.21.11` (inclusive)
- Loader strategy:
  - Fabric: all supported Minecraft versions
  - Forge: all supported versions where Forge exists
  - NeoForge: only versions with stable NeoForge support
- Mod version: `0.0.1`

### Version Matrix

- `1.20`: Fabric, Forge
- `1.20.1`: Fabric, Forge
- `1.20.2`: Fabric, Forge, NeoForge
- `1.20.3`: Fabric
- `1.20.4`: Fabric, Forge, NeoForge
- `1.20.5`: Fabric
- `1.20.6`: Fabric, Forge, NeoForge
- `1.21`: Fabric, NeoForge
- `1.21.1`: Fabric, NeoForge
- `1.21.2`: Fabric
- `1.21.3`: Fabric, NeoForge
- `1.21.4`: Fabric, NeoForge
- `1.21.5`: Fabric, NeoForge
- `1.21.6`: Fabric
- `1.21.7`: Fabric
- `1.21.8`: Fabric, NeoForge
- `1.21.9`: Fabric
- `1.21.10`: Fabric, NeoForge
- `1.21.11`: Fabric

## What It Does

- Hides/replaces local player name.
- Hides/replaces local player skin.
- Hides/replaces local player cape (and cape-based elytra texture where applicable).
- Hides/replaces other players' names (template with `[ID]` token).
- Hides/replaces other players' skins (shared configured source).
- Hides/replaces other players' capes (and cape-based elytra texture where applicable).
- Global enable/disable switch for all masking behavior.

Settings are available in-game:

- Forge/NeoForge Mod List config screen
- Keybind entry (`Open Nick Hider Settings`, default unbound)

## Settings

The config exposes only these fields:

- `Hide Local Name`
- `Hide Local Skin`
- `Hide Local Cape`
- `Hide Other Names`
- `Hide Other Skins`
- `Hide Other Capes`
- `Local Replacement Name`
- `Local Skin Source Username`
- `Local Cape Source Username`
- `Other Players Name Template`
- `Other Players Skin Source Username`
- `Other Players Cape Source Username`
- `Enable Nick Hider`

Cape fallback behavior:

- If cape masking is enabled but no valid cape source can be resolved, cape rendering is hidden instead of falling back to the original cape.

## Installation

1. Open the repository Releases page.
2. Download the jar asset for the target release (direct `.jar` file, not a zip bundle).
3. Put the jar into your Minecraft `mods` directory for the matching loader + Minecraft version.
4. Start the game and open Nick Hider settings via keybind (or Mod List screen on Forge/NeoForge).

## Privacy Scope

Nick Hider applies aggressive client-side masking on vanilla rendering/text paths.  
This is best-effort behavior, not a mathematical guarantee across every third-party mod renderer/UI pipeline.

## Icon Compatibility

The mod icon is wired through loader metadata using `assets/nickhider/icon.png`:

- Forge `mods.toml` (`logoFile`)
- NeoForge `neoforge.mods.toml` (`logoFile`)
- Fabric `fabric.mod.json` (`icon`)

Launchers and tooling (for example Prism Launcher) may render mod icons differently depending on their metadata support. This project configures standard metadata paths for best-effort compatibility.

## Building From Source

Requirements:

- Java 21 for Gradle/Stonecraft runtime
- Git

Build:

```bash
./gradlew clean build
```

Notes:

- The build runtime uses Java 21 because Stonecraft requires it.
- Produced mod classes are compiled with version-aware bytecode targets:
  - Java 17 for pre-`1.20.5` targets
  - Java 21 for `1.20.5+` targets

## Semi-automated runClient checks

Use:

```bash
bash scripts/runclient-notes.sh
```

Optional filters:

```bash
bash scripts/runclient-notes.sh --only '1.21.*-neoforge'
bash scripts/runclient-notes.sh --from 1.20.6-fabric --to 1.21.1-neoforge
```

Behavior:

- Detects all `1.*` Gradle subprojects dynamically (`./gradlew projects -q`).
- Runs `runClient` one project at a time.
- Stores full logs per project.
- Extracts `<developer>` chat messages into a single Markdown notes file.
- Deduplicates only same-event server/render duplicates (same timestamp + same message).
- While a project is running, type `/next` + Enter to force moving to the next project.
- After each game closes, prompts for manual multiline notes; finish with `/done`.

Output:

- `runchecks/<run-id>/notes.md`
- `runchecks/<run-id>/logs/<project>.log`
- `runchecks/<run-id>/summary.tsv`
- `runchecks/latest.txt` (last run id)

## Releases

- CI build workflow runs in loader shards (`fabric`, `forge`, `neoforge`) and uploads jar artifacts per shard.
- Manual release workflow reuses those CI artifacts for the tagged release commit instead of rebuilding jars.
- Manual Modrinth publish workflow (`Manual Modrinth Publish`) publishes each release jar as a separate Modrinth version.
- Required repository secrets for Modrinth publish:
  - `MODRINTH_TOKEN`
  - `MODRINTH_PROJECT_ID` (project ID or slug; project URL is also accepted)

## License

Licensed under the GNU Affero General Public License v3.0 (AGPLv3).  
See `/LICENSE` for full text.
