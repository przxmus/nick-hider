# Nick Hider

Client-side privacy masking for Minecraft player names, skins, and capes.

<p align="center">
  <img src="https://raw.githubusercontent.com/przxmus/nick-hider/refs/heads/main/src/main/resources/assets/nickhider/icon.png" alt="Nick Hider icon" width="128" height="128">
</p>

<p align="center">
  <img alt="Minecraft" src="https://img.shields.io/badge/Minecraft-1.20.1%20%7C%201.21.1-2ea043">
  <img alt="Loaders" src="https://img.shields.io/badge/Loaders-Fabric%20%7C%20Forge%20%7C%20NeoForge-orange">
  <img alt="License" src="https://img.shields.io/badge/License-AGPL--3.0-red">
</p>

## What This Mod Does

Nick Hider masks player identity details in common vanilla client rendering and text paths.

- Local player: replace your shown name, skin, and cape.
- Other players: replace names with a template and swap skins/capes from configured source accounts.
- Global switch: instantly enable or disable all masking behavior.

## Quick Start Tutorial

1. Download the jar for your exact Minecraft + loader combination.
2. Put the jar in your Minecraft `mods` folder.
3. Launch the game and open settings from Mod Menu (Fabric + Mod Menu), Mod List (Forge/NeoForge), or the keybind `Open Nick Hider Settings` (default unbound).
4. First-time setup suggestion: enable `Hide Local Name`, `Hide Local Skin`, and `Hide Local Cape`, then set `Local Replacement Name` to a neutral alias.
5. Join a world/server and verify your own name and skin/cape are masked; enable `Hide Other Names/Skins/Capes` if you also want to mask other players.

## Features At A Glance

| Area              | Behavior                                               |
| ----------------- | ------------------------------------------------------ |
| Local name        | Replaces your local displayed name                     |
| Local skin        | Replaces your local rendered skin                      |
| Local cape        | Replaces your local cape and cape-based elytra texture |
| Other names       | Template-based replacement with `[ID]` token support   |
| Other skins/capes | Shared configured source usernames                     |
| Runtime toggle    | Global `Enable Nick Hider` on/off switch               |

## Compatibility

- Mod version: `0.1.1`
- Supported Minecraft versions: `1.20.1` and `1.21.1`
- Loader support policy:
  - `1.20.1`: Fabric, Forge
  - `1.21.1`: Fabric, Forge, NeoForge

Short matrix:

- `1.20.1`: Fabric, Forge
- `1.21.1`: Fabric, Forge, NeoForge

## Configuration

### Toggle Controls

- Hide local: name, skin, cape
- Hide others: names, skins, capes
- Global master toggle: `Enable Nick Hider`

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

## Known Limits

Nick Hider is aggressive on vanilla client paths, but masking is still best-effort. Third-party mods or custom render/UI pipelines can display unmasked data outside covered paths.

## License

Licensed under the GNU Affero General Public License v3.0 (AGPLv3).  
See [LICENSE](LICENSE).

## Maintainer and Links

- Author: `przxmus`
- Source: [GitHub Repository](https://github.com/przxmus/nick-hider)
- Issues: [GitHub Issues](https://github.com/przxmus/nick-hider/issues)
