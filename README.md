# Nick Hider

Client-side privacy mod for Minecraft that masks player names, skins and capes in common vanilla client paths.

Copyright (c) 2026 przxmus

## Current Support

- Minecraft: `1.20.1`
- Loader: Forge `47.4.15`
- Mod version: `0.0.1`

Roadmap: the project is structured for future MultiLoader/MultiVersion expansion (Fabric/NeoForge + more MC versions), but this release targets Forge 1.20.1 only.

## What It Does

- Hides/replaces local player name.
- Hides/replaces local player skin.
- Hides/replaces local player cape (and cape-based elytra texture where applicable).
- Hides/replaces other players' names (template with `[ID]` token).
- Hides/replaces other players' skins (shared configured source).
- Hides/replaces other players' capes (and cape-based elytra texture where applicable).
- Global enable/disable switch for all masking behavior.

Settings are available in-game:

- Forge Mod List config screen
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
3. Put the jar into your Minecraft `mods` directory for Forge `1.20.1`.
4. Start the game and open the Nick Hider config in Mod List.

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
- Produced mod classes are compiled to Java 17 bytecode (`options.release = 17`) for runtime compatibility.

## Releases

- CI build workflow uploads jar artifacts for each run.
- Manual release workflow creates a draft GitHub Release and uploads all built jars as direct release assets.

## License

Licensed under the GNU Affero General Public License v3.0 (AGPLv3).  
See `/LICENSE` for full text.
