# Nick Hider Leak Audit Checklist

## Vanilla paths
- Name tags above local and remote players
- Player list (TAB overlay)
- Chat lines and system messages
- Death messages and advancement messages
- Scoreboard sidebar and player entries
- HUD overlays that render player names

## Skin paths
- Third-person local player model
- Remote player models
- Player head icons in vanilla UI where available
- Join/leave world transitions

## Expected behavior
- With local toggles ON, local name/skin are always replaced.
- With others toggles ON, other players' names/skins are always replaced.
- Invalid or unavailable skin source username falls back to default Steve/Alex-like skin.
- When toggles are OFF, original values are shown again.
