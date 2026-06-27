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

This project intentionally does not use Fabric Loom right now. The configured
Minecraft target has a named server jar available at:

```text
../versions/${minecraft_version}/server-${minecraft_version}.jar
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
build/libs/CompassHud-${mod_version}.jar
```

Deploy to the local Fabric server:

```bash
./gradlew deployLocal
```

The deploy task removes older CompassHud jars from `../mods`, copies the current
jar, builds and copies the resource pack, and updates `../server.properties`
with the correct resource-pack URL and SHA1.

## Versioning

CompassHud's release version has one source of truth:

```text
gradle.properties -> mod_version
```

Do not hardcode release numbers in documentation, resource-pack filenames, or
deployment commands. Use Gradle tasks and `${mod_version}` examples instead.
`processResources` expands the manifest version, `resourcePackZip` names the
distributable resource pack from `project.version`, and `deployLocal` updates
the local server files and resource-pack SHA.

When the vendored VanillaPings patch changes behavior, also bump:

```text
vendor/VanillaPings/stonecutter.properties.toml -> mod.version
```

CompassHud's build reads that value and nests the matching patched VanillaPings
jar as `META-INF/jars/vanillapings-patched.jar`, so stale collected
VanillaPings jars should not be selected after a patch-version bump.

## Resource Pack

The source resource pack lives in:

```text
resource-pack
```

The distributable zip lives in:

```text
dist/CompassHud-resource-pack-${mod_version}.zip
```

The runtime copy served by the local resource-pack host is:

```text
../resourcepacks/CompassHud-resource-pack.zip
```

`./gradlew build` creates the versioned distributable zip and its `.sha1` file.
`./gradlew deployLocal` copies the runtime zip and updates the SHA1 in
`../server.properties`.

When resource-pack glyphs change, keep `config/compasshud.json` glyph settings in sync with
   `resource-pack/assets/compasshud/font/compass.json`.

Vanilla Minecraft resource packs are downloaded by the client from the URL in
`server.properties`. The Minecraft game server does not proxy that file over the
game connection. If the HTTP host is local-only, clients must still be able to
reach that local URL directly.

### Local Docker Host

Do not modify `/home/assisr/dev/docker` for this mod. CompassHud's resource-pack
HTTP host is configured in the Minecraft server directory:

```text
/home/assisr/dev/minecraft/docker-compose.resourcepacks.yml
/home/assisr/dev/minecraft/resourcepack-nginx/default.conf
/home/assisr/dev/minecraft/config.env
/home/assisr/dev/minecraft/start.sh
/home/assisr/dev/minecraft/stop.sh
```

`start.sh` starts the resource-pack Docker container before Minecraft starts.
`stop.sh` stops it after Minecraft stops. `restart.sh` uses those scripts, so it
also handles the container lifecycle.

Current resource-pack settings in `config.env`:

```text
RESOURCE_PACK_HOST_ENABLED="1"
RESOURCE_PACK_BIND_IP="192.168.18.100"
RESOURCE_PACK_PUBLIC_HOST="mine.assisr.com"
RESOURCE_PACK_PORT="8000"
RESOURCE_PACK_FILE="CompassHud-resource-pack.zip"
RESOURCE_PACK_ID="4b06a502-d777-4a99-bc2e-6e06c6e4c58e"
RESOURCE_PACK_COMPOSE_PROJECT="compasshud-resource-pack"
```

The container binds to the server's LAN IP only:

```text
192.168.18.100:8000 -> container:80
```

The URL advertised to Minecraft clients is public:

```text
http://mine.assisr.com:8000/CompassHud-resource-pack.zip
```

Remote players do not fetch this through the Minecraft protocol on port 25565.
Their vanilla client downloads the URL directly. For off-LAN users to work,
public TCP `8000` must be allowed through host firewall and router/NAT to:

```text
192.168.18.100:8000
```

Host firewall command:

```bash
sudo ufw allow 8000/tcp comment 'CompassHud resource pack'
```

External test, preferably from outside the LAN:

```bash
curl -I http://mine.assisr.com:8000/CompassHud-resource-pack.zip
```

### Resource-Pack HTTP Security

Random users must never get broad file access to the Minecraft server. The
resource-pack container is intentionally restricted:

- It bind-mounts only the permitted zip file, not the whole server directory.
- The zip is mounted read-only.
- The Nginx config allows only the exact zip path.
- `/`, `/server.properties`, `/config.env`, traversal attempts, and other paths
  must return `404`.
- The container uses a read-only root filesystem.
- `no-new-privileges` is enabled.
- Nginx runtime/cache paths are tmpfs mounts.

Expected verification:

```text
/CompassHud-resource-pack.zip                  200
/                                                404
/server.properties                               404
/config.env                                      404
/../server.properties                            404
/CompassHud-resource-pack.zip/anything         404
```

Useful checks:

```bash
docker ps --filter name=compasshud-resource-pack
docker inspect compasshud-resource-pack --format '{{json .NetworkSettings.Ports}}'
curl -s -o /dev/null -w '%{http_code}\n' \
  http://192.168.18.100:8000/CompassHud-resource-pack.zip
```

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
