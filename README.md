# CompassHud

A server-side Fabric mod for the configured Minecraft target that renders a Skyrim /
battle-royale style directional compass HUD. It uses vanilla boss-bar or
action-bar packets, so players do not need a client mod or resource pack.

```text
[ E---120---150---S----210--240---- ]      <- facing South
                  ^ heading
```

The strip scrolls per player from that player's yaw.

## Features

- Cardinals: `N`, `E`, `S`, `W`
- Optional intercardinals: `NE`, `SE`, `SW`, `NW`
- Configurable numeric marks, such as every 30, 45, 50, 90, or 100 degrees
- Boss bar at the top of the screen or action bar above the hotbar
- Configurable width, field of view, update interval, characters, and colors
- Per-player toggle with `/compass`, plus live config reload
- Per-player saved compass spots with stable glyphs/colors across relog and restart
- Server-side only; no client install required

## Build

This repo builds against the named Minecraft server jar for `minecraft_version`
from `gradle.properties`, installed under `../versions/`. That avoids Fabric
Loom mapping issues for targets where Fabric API artifacts exist but Yarn or
Mojang mapping downloads are not cleanly available.

Use a JDK 25 with `javac`:

```bash
./gradlew build -Dorg.gradle.java.home=/path/to/jdk-25
```

The version comes from `gradle.properties` and is expanded into
`fabric.mod.json` during the build. The mod jar is written to:

```text
build/libs/CompassHud-${mod_version}.jar
```

## Install

Deploy the current build to the adjacent Fabric server and restart the server:

```bash
./gradlew deployLocal
```

That task removes older CompassHud jars from `../mods`, copies the current jar,
builds the resource pack, copies it to `../resourcepacks/CompassHud-resource-pack.zip`,
and updates `../server.properties` with the current resource-pack SHA1.

The config file is created on first startup:

```text
config/compasshud.json
```

## Commands

| Command           | Description                |
|-------------------|----------------------------|
| `/compass`        | Toggle your compass on/off |
| `/compass on`     | Force on                   |
| `/compass off`    | Force off                  |
| `/compass toggle` | Toggle on/off              |
| `/compass save`   | Save your current block position |
| `/compass save <name>` | Save your current block position with a label |
| `/compass save <x> <z>` | Save a horizontal coordinate at your current Y |
| `/compass save <x> <z> <name>` | Save a horizontal coordinate with a label |
| `/compass list`   | List your saved spots with clickable delete actions |
| `/compass delete <id>` | Delete the numbered saved-spot ID from `/compass list` |
| `/compass reload` | Reload config; operator/gamemaster only |

Per-player toggle state lasts for the session and resets to `defaultEnabled` on
rejoin. Saved spots persist per player across logout/login and server restarts.
Saved-spot capacity and glyph reuse are per dimension; IDs remain stable per
player across all dimensions.

## Configuration

The generated `config/compasshud.json` is documented inline. Important keys:

- `display`: `BOSS_BAR` or `ACTION_BAR`
- `width`: characters across the strip; around 33 is roughly one third of the screen
- `fieldOfView`: degrees shown across the strip
- `markInterval`: spacing between numeric marks
- `showDegrees`, `showIntercardinals`
- `updateIntervalTicks`
- `characters`
- `colors`
- `savedSpots`: per-dimension glyph pool/capacity, muted color palette, same-dimension display, edge clamping, and resource-pack font fallback
- `bossbar`
