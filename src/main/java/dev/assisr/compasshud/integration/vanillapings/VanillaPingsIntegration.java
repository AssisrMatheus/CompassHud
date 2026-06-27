package dev.assisr.compasshud.integration.vanillapings;

import com.vanillapings.api.PingEvents;
import dev.assisr.compasshud.CompassHud;
import dev.assisr.compasshud.CompassManager;
import net.minecraft.server.level.ServerLevel;

public final class VanillaPingsIntegration {
    private VanillaPingsIntegration() {
    }

    public static void register(CompassManager manager) {
        PingEvents.register(event -> {
            if (!(event.world() instanceof ServerLevel level)) {
                return;
            }
            manager.addVanillaPing(level.dimension(), event.position(), event.targetEntity(), event.maxAgeTicks());
        });
        CompassHud.LOGGER.info("[CompassHud] VanillaPings compass integration enabled.");
    }
}
