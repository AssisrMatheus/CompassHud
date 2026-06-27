# CompassHud

A server-side Fabric mod for Minecraft 26.2 that renders a Skyrim /
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
- Server-side only; no client install required

## Build

This repo builds against the named Minecraft 26.2 server jar already installed
in `../versions/26.2/server-26.2.jar`. That avoids Fabric Loom mapping issues:
26.2 has Fabric API artifacts, but no Yarn mappings or Mojang mapping downloads.

Use a JDK 25 with `javac`:

```bash
./gradlew build -Dorg.gradle.java.home=/path/to/jdk-25
```

The mod jar is written to:

```text
build/libs/CompassHud-2.0.0.jar
```

## Install

Copy the jar into the Fabric server's `mods/` directory and restart the server:

```bash
cp build/libs/CompassHud-2.0.0.jar ../mods/
```

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
| `/compass reload` | Reload config; operator/gamemaster only |

Per-player toggle state lasts for the session and resets to `defaultEnabled` on
rejoin.

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
- `bossbar`
