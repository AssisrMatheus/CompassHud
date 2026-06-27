package com.vanillapings.api;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record PingCreatedEvent(
        Level world,
        Vec3 position,
        @Nullable UUID targetEntity,
        UUID player,
        String playerName,
        int maxAgeTicks,
        PingKind kind
) {
}
