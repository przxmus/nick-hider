# Nick Hider

Client-side privacy masking for Minecraft player names, skins, and capes.

<p align="center">
  <img src="src/main/resources/assets/nickhider/icon.png" alt="Nick Hider icon" width="128" height="128">
</p>

<p align="center">
  <img alt="Minecraft" src="https://img.shields.io/badge/Minecraft-1.20.1%20%7C%201.21.1-2ea043">
  <img alt="Loaders" src="https://img.shields.io/badge/Loaders-Fabric%20%7C%20Forge%20%7C%20NeoForge-orange">
  <img alt="License" src="https://img.shields.io/badge/License-AGPL--3.0-red">
</p>

## Download

- [Download on Modrinth](https://modrinth.com/project/tRP3LbwW)
- [Download on CurseForge](https://curseforge.com/minecraft/mc-mods/nick-hider)
- [Download from GitHub Releases](https://github.com/przxmus/nick-hider/releases)

## What This Mod Does

Nick Hider masks player identity details in common vanilla client rendering and text paths.

- Local player: replace your shown name, skin, and cape.
- Other players: replace names with a template and swap skins/capes from configured source accounts.
- Global switch: instantly enable or disable all masking behavior.

## Quick Start Tutorial

1. Download the jar for your exact Minecraft + loader combination from [Releases](https://github.com/przxmus/nick-hider/releases).
2. Put the jar in your Minecraft `mods` folder.
3. Launch the game and open settings from Mod Menu (Fabric + Mod Menu), Mod List (Forge/NeoForge), keybind `Open Nick Hider Settings` (default unbound), or command `/nickhider`.
4. First-time setup suggestion: enable `Hide Local Name`, `Hide Local Skin`, and `Hide Local Cape`, then set `Local Replacement Name` to a neutral alias.
5. Join a world/server and verify your own name and skin/cape are masked; enable `Hide Other Names/Skins/Capes` if you also want to mask other players.
6. After config save/reload, Nick Hider automatically refreshes skin/cape sources in the background.

## Features At A Glance

| Area              | Behavior                                               |
| ----------------- | ------------------------------------------------------ |
| Local name        | Replaces your local displayed name                     |
| Local skin        | Replaces your local rendered skin                      |
| Local cape        | Replaces your local cape and cape-based elytra texture |
| Other names       | Template-based replacement with `[ID]` token support   |
| Other skins/capes | Shared configured source usernames                     |
| External fallback | Optional `mineskin.eu` + `api.capes.dev` (default: off) |
| Runtime toggle    | Global `Enable Nick Hider` on/off switch               |
| Open config       | Keybind + `/nickhider` command                         |
| Refresh behavior  | Automatic source refresh (no manual refresh button)    |
| Diagnostics       | `NH-*` runtime codes with throttled warnings           |

## Compatibility

- Supported Minecraft versions: `1.20.1` and `1.21.1`
- Loader support policy:
  - `1.20.1`: Fabric, Forge
  - `1.21.1`: Fabric, NeoForge
- Artifacts declare exact Minecraft version compatibility (`1.20.1` or `1.21.1`), not open-ended ranges.

Short matrix:

- `1.20.1`: Fabric, Forge
- `1.21.1`: Fabric, NeoForge

For exact jar availability per release, use [GitHub Releases](https://github.com/przxmus/nick-hider/releases).

## Configuration

### Toggle Controls

- Hide local: name, skin, cape
- Hide others: names, skins, capes
- Global master toggle: `Enable Nick Hider`
- Optional switch: `Enable External Skin/Cape Fallbacks` (default: off)

### Replacement Inputs

- Local replacement name
- Local skin source username
- Local cape source username
- Other players name template
- Other players skin source username
- Other players cape source username

### Validation and Fallbacks

- Replacement usernames must be empty or match `[A-Za-z0-9_]{3,16}`.
- Local replacement name must match `[A-Za-z0-9_]{3,16}`.
- If cape masking is enabled but no valid cape source resolves, cape rendering is hidden instead of showing the original cape.
- External fallbacks are queried only after official skin/cape lookups fail.
- Saving or reloading config clears runtime cache and starts an automatic source prefetch.

### Diagnostics Logs

- Skin/cape runtime diagnostics use stable `NH-*` codes (for example `NH-SKIN-FETCH`, `NH-SKIN-RECOVERED`, `NH-HOOK-FAIL`).
- Repeated identical failures are throttled per source user to avoid console spam.
- Hook failures log the first incident as warning, and subsequent repeats as debug.

## Known Limits

Nick Hider is aggressive on vanilla client paths, but masking is still best-effort. Third-party mods or custom render/UI pipelines can display unmasked data outside covered paths.

## For Developers

Requirements:

- Java 21
- Git

Build:

```bash
./gradlew clean build
```

Run tests:

```bash
./gradlew test
```

## License

Licensed under the GNU Affero General Public License v3.0 (AGPLv3).  
See [LICENSE](LICENSE).

## Maintainer and Links

- Author: `przxmus`
- Source: [GitHub Repository](https://github.com/przxmus/nick-hider)
- Issues: [GitHub Issues](https://github.com/przxmus/nick-hider/issues)
