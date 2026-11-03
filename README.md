[//]: # (This is a link to the raw location so that the image can be displayed from pages like Modrinth)
![Portals Pro Max](https://raw.githubusercontent.com/angwdev/Portals-Pro-Max/refs/heads/main/docs/logo.png)

# Portals Pro Max

Portals, destinations and warps for **Paper 26.3+** (and Folia). Select a region, give it a few tags and it becomes
a portal to anywhere: another place in the world, another world, or another server on your proxy.

Portals Pro Max is a Paper-only fork of [Advanced Portals](https://github.com/sekwah41/Advanced-Portals) by
sekwah41 that uses the modern Paper API throughout, is built for performance on big servers, and works for
Bedrock players joining through Geyser.

[![Build](https://github.com/angwdev/Portals-Pro-Max/actions/workflows/build.yml/badge.svg)](https://github.com/angwdev/Portals-Pro-Max/actions/workflows/build.yml)

## Requirements

- Paper **26.3** or newer (Folia is supported too). Spigot and Bukkit are **not** supported.
- Java 25 (what Paper 26.x needs anyway)
- Optional: PlaceholderAPI (`conditions` tag), Vault (`cost` tag), Geyser and/or Floodgate (Bedrock players)

The proxy plugin for Velocity and BungeeCord is contained within the same jar.

## Quick start

1. Get the selector axe: `/portal selector` and left/right click two corners, **or** stand in a corner and run
   `/portal pos1` and `/portal pos2` (also works with coordinates: `/portal pos2 ~10 ~5 ~10`).
2. Stand where players should arrive and run `/destination create spawn`.
3. Run `/portal create hub triggerblock:NETHER_PORTAL destination:spawn`.

Walking into the portal region now warps you to `spawn`.

## Commands

| Command | What it does |
| --- | --- |
| `/portal create <name> [tag:value...]` | Creates a portal from your selection |
| `/portal edit <name>` | Opens a click-through editor (dialog on Java, form on Bedrock) |
| `/portal edit <name> tag:value...` | Sets or replaces tags |
| `/portal edit <name> add tag:value` | Adds a value to a multi value tag, e.g. another destination |
| `/portal edit <name> remove tag [tag:value]` | Removes a tag, or only one value of it |
| `/portal rename <old> <new>` | Renames a portal and keeps everything else |
| `/portal region <name>` | Loads a portal's region into your selection, change it and apply with `/portal move <name>` |
| `/portal move <name>` | Moves a portal to your current selection (saved) |
| `/portal highlight <name> [seconds]` | Outlines a portal with a glowing box only you can see |
| `/portal info`, `list`, `remove`, `show`, `reload` | Inspect, list, remove, show with particles, reload |
| `/destination create\|edit\|rename\|move\|info\|highlight\|remove\|list\|teleport` | The same for destinations |

Tab completion works for portal names, destinations, tags and tag values.

### Tags

`name`, `destination`, `triggerblock`, `permission`, `cooldown`, `message`, `command`, `portalEvent`, `bungee`,
`proxy`, `cost` (Vault), `conditions` (PlaceholderAPI) and `keepvelocity`.

`keepvelocity:true` keeps the player's momentum through the warp, handy for launch pads. Tags can be put on a portal or
a destination where it makes sense. `/portal edit <name>` lists every tag that can be used.

## Configuration

`plugins/PortalsProMax/config.yaml` is created on the first start. A few options worth knowing:

| Option | Default | Description |
| --- | --- | --- |
| `triggerWithHitbox` | `false` | Trigger as soon as any part of the player's body touches a trigger block (like vanilla portals) instead of only their position |
| `portalProtection` / `portalProtectionRadius` | `true` / `5` | Stops players breaking or placing blocks near portals |
| `throwbackStrength` | `0.7` | How hard players are pushed back when a portal denies them |
| `disablePhysicsEvents` | `false` | Stops block physics around portals (uses a spatial index, no per-event cost) |
| `warpMessageOnActionBar` | `true` | Show the warp message in the action bar |

## Performance and accuracy

- Portals are kept in a chunk-bucket index: movement, block protection, physics, explosions and chunk loads only look
  at the portals in the chunk instead of every portal on the server. Look-only movement is ignored.
- Folia-safe: caches are concurrent and teleports use `teleportAsync`. Messages, commands and costs wait until the
  player has arrived (and are skipped if the teleport was cancelled).
- The player's whole body (feet to head, optionally the full hitbox) is checked, not a single point.
- Destinations store the exact position and facing, `/destination move` uses your exact position.
- Data files are written atomically, so a crash can't leave half a portal behind.

## Bedrock players (Geyser / Floodgate)

Everything works for Bedrock players, a few features use a Bedrock friendly version automatically:

- The editor opens as a native Bedrock form instead of a Java dialog.
- `/portal highlight` draws a particle outline instead of display entities.
- `/portal pos1` and `/portal pos2` replace clicking blocks with the axe (awkward on touch screens and gamepads).
- The selector axe and block placers are recognised by an item tag, not their name, so Bedrock clients that drop
  the formatting of item names still work.

Detection works with Floodgate (including Geyser on a proxy with Floodgate on the backend) or the Geyser plugin.

## Permissions

The permission nodes are unchanged from Advanced Portals (`advancedportals.*`), so existing permission setups keep
working. The most useful ones:

| Permission | Allows |
| --- | --- |
| `advancedportals.portal.create` / `.remove` / `.edit` / `.show` / `.info` | Managing portals |
| `advancedportals.desti.create` / `.remove` / `.edit` / `.info` / `.teleport` | Managing destinations |
| `advancedportals.portal.use` | Using portals (default: everyone) |
| `advancedportals.build` | Building inside protected portal regions |
| `advancedportals.*` | Everything |

## Upgrading from Advanced Portals

- Put the new jar in `plugins` and remove the old Advanced Portals jar.
- On the first start `plugins/AdvancedPortals` is copied to `plugins/PortalsProMax` (the old folder is left alone).
- Portals, destinations, config and language files work as before.
- Items given by the old version (selector axe, block placers) keep working.

## Building

```bash
./gradlew :core:test       # tests (Java 17)
./gradlew :paper:jar       # the plugin jar in paper/build/libs (needs Java 25 and 17 installed)
```

The build fails on deprecated or to-be-removed Paper API, new code has to use the modern API.
GitHub Actions builds every push, runs the tests and starts a real Paper 26.3 server with the plugin.

## Contributing

Please use [conventional commits](https://www.conventionalcommits.org/en/v1.0.0/) and see [CONTRIBUTING.md](CONTRIBUTING.md).
Run `pre-commit run --all-files` before opening a pull request (needs clang-format).

## Credits and license

Based on [Advanced Portals](https://github.com/sekwah41/Advanced-Portals) by sekwah41 and its contributors.
Licensed under the [GNU LGPL v3](LICENSE.md).
