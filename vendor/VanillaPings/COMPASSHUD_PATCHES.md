# CompassHud VanillaPings Patches

Upstream: https://github.com/crossy-l/VanillaPings
Base commit: `0a5fd93a772bd1068b68a8ab409e2cf34cf05d4d`

CompassHud vendors VanillaPings as regular source because this project carries
patches on top of upstream and does not control the upstream repository.

The `UPSTREAM` file is intentionally machine-readable enough for scripts and
simple enough to keep accurate during manual updates.

When updating VanillaPings, start from the upstream commit above or a newer
upstream commit, then replay the CompassHud changes in this directory.
