# CompassHud

A server-side Fabric mod for Minecraft 26.2 that renders a Skyrim /
battle-royale style directional compass HUD. It uses vanilla boss-bar or
action-bar packets, so players do not need a client mod. An optional server
resource pack makes the compass look like a slim graphical ruler and hides the
empty white boss-bar strip.

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
- Optional custom-font resource-pack mode for graphical ticks and marker icons
- Optional world-spawn and last-death markers
- Configurable width, field of view, update interval, characters, and colors
- Per-player toggle with `/compass`, plus live config reload
- Server-side only; no client install required

## Build

This repo builds against the named Minecraft 26.2 server jar already installed
in `../versions/26.2/server-26.2.jar`. That avoids Fabric Loom mapping issues:
26.2 has Fabric API artifacts, but no Yarn mappings or Mojang mapping downloads.

Use a JDK 25 with `javac`. On this machine, the agent-managed JDK is installed
at `/home/assisr/.local/share/jdks/current-25`, and `gradle.properties` points
Gradle toolchain discovery there:

```bash
./gradlew build
```

If Gradle cannot find the JDK, pass it explicitly:

```bash
./gradlew build -Dorg.gradle.java.home=/home/assisr/.local/share/jdks/current-25
```

The mod jar is written to:

```text
build/libs/CompassHud-2.1.0.jar
```

## Install

Copy the jar into the Fabric server's `mods/` directory and restart the server:

```bash
cp build/libs/CompassHud-2.1.0.jar ../mods/
```

The config file is created on first startup:

```text
config/compasshud.json
```

## Resource Pack

The optional resource pack is written to:

```text
dist/CompassHud-resource-pack-2.1.0.zip
```

To auto-send it to vanilla clients, host that zip at an HTTP(S) URL and set
these in `server.properties`:

```properties
resource-pack=<url-to-CompassHud-resource-pack-2.1.0.zip>
resource-pack-sha1=<sha1>
require-resource-pack=false
```

When `resourcePackMode` is enabled in `config/compasshud.json`, both boss-bar
and action-bar displays use the custom glyph font. The pack also makes the
WHITE boss bar background/progress transparent, so use `bossbar.color: WHITE`
for CompassHud.

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
- `resourcePackMode`, `resourcePackFont`
- `markers.worldSpawn`, `markers.lastDeath`
