# Contributing

This mod is developed in `/home/assisr/dev/minecraft/CompassHud` for the Fabric
server in `/home/assisr/dev/minecraft`.

## Toolchain

Use the checked-in Gradle wrapper. Do not depend on system Gradle.

The local Linux toolchains are installed outside agent temp folders:

```text
/home/assisr/.local/share/jdks/current-25
/home/assisr/.local/share/jdks/jdk-25.0.3+9
/home/assisr/.local/share/jdks/jdk-21.0.11+10
/home/assisr/.local/share/gradle/gradle-9.6.1
/home/assisr/.local/bin/gradle-9.6.1
```

Downloaded toolchain archives are kept in:

```text
/home/assisr/.cache/toolchains
```

`gradle.properties` points Gradle's Java toolchain discovery at
`/home/assisr/.local/share/jdks/current-25`, so this should work from the repo:

```bash
./gradlew build
```

If Gradle cannot find Java, run the build explicitly:

```bash
./gradlew build -Dorg.gradle.java.home=/home/assisr/.local/share/jdks/current-25
```

## Mapping Strategy

This project intentionally does not use Fabric Loom right now. Minecraft 26.2
has a named server jar available at:

```text
../versions/26.2/server-26.2.jar
```

The build compiles directly against that jar plus the server libraries, Fabric
Loader, and processed Fabric API jars:

```text
../libraries
../.fabric/processedMods
```

Keep this approach unless Fabric/Yarn/Mojang mapping support for 26.2 becomes
cleanly available. Avoid copying old client-rendering code from other mods; this
is a server-side vanilla-client HUD.

## Build And Deploy

Build:

```bash
./gradlew clean build
```

Main artifact:

```text
build/libs/CompassHud-2.1.0.jar
```

Deploy to the local Fabric server:

```bash
cp build/libs/CompassHud-2.1.0.jar ../mods/
```

Remove older CompassHud jars from `../mods` before restarting so Fabric does not
load duplicate mod IDs.

## Resource Pack

The source resource pack lives in:

```text
resource-pack
```

The distributable zip lives in:

```text
dist/CompassHud-resource-pack-2.1.0.zip
```

When the resource pack changes:

1. Rebuild the zip from the contents of `resource-pack`.
2. Update the SHA1 in `../server.properties`.
3. Keep `config/compasshud.json` glyph settings in sync with
   `resource-pack/assets/compasshud/font/compass.json`.

Vanilla Minecraft resource packs are downloaded by the client from the URL in
`server.properties`. The Minecraft game server does not proxy that file over the
game connection. If the HTTP host is local-only, clients must still be able to
reach that local URL directly.

## Runtime Config

Live server config is in:

```text
../config/compasshud.json
```

Useful commands in game:

```text
/compass
/compass on
/compass off
/compass reload
```

`/compass reload` should be enough for most config-only changes. Jar changes
require a server restart.

## Code Notes

- `CompassHud` registers Fabric lifecycle, player, sleep, death, and tick events.
- `CompassManager` owns per-player state, HUD updates, target caches, and marker
  refreshes.
- `CompassRenderer` creates the text component for boss-bar and action-bar
  output.
- `CompassConfig` owns JSON config defaults and parsing.
- `CompassMarker` represents world-spawn and last-death indicators.

Keep expensive work out of the per-tick path. Marker target positions are cached
and refreshed on relevant events, with a low-frequency fallback refresh. Per-tick
work should stay limited to cheap yaw checks, cached-position bearing math, and
HUD packet updates according to `updateIntervalTicks`.

## Scratch Work

Do not leave required Java, Gradle, JDKs, archives, or generated binaries under
`/tmp/claude-*`. Put durable tooling under the user-local Linux locations listed
above, source notes under this repo, and generated release artifacts under
`build` or `dist`.

The old renderer scratch program from the Claude temp folder is kept as:

```text
dev-notes/RenderTest.java
```
