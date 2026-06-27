# VanillaPings Integration

CompassHud vendors VanillaPings at upstream commit:

```text
0a5fd93a772bd1068b68a8ab409e2cf34cf05d4d
```

The local patch is intentionally small:

- add a `26.2` Stonecutter target using Fabric API `0.153.0+26.2`
- add `com.vanillapings.api.PingEvents`
- fire one `PingCreatedEvent` after `PingManager.pingAtPosition(...)` accepts and creates a ping
- add `ping-duration-seconds` to VanillaPings' properties file so ping lifetime is server-configurable

VanillaPings remains the source of truth for `/ping`, raycasts, cooldowns,
messages, sounds, world markers, entity highlighting, animation, and cleanup.
CompassHud only mirrors accepted ping events into temporary compass markers.
