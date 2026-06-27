package dev.assisr.compasshud;

import dev.assisr.compasshud.integration.vanillapings.VanillaPingsIntegration;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Fabric entrypoint for CompassHud — a server-side Skyrim / battle-royale style
 * directional compass HUD. It only sends vanilla boss-bar / action-bar packets,
 * so no client mod or resource pack is required.
 */
public final class CompassHud implements ModInitializer {

    public static final String MOD_ID = "compasshud";
    public static final Logger LOGGER = LoggerFactory.getLogger("CompassHud");

    private Path configPath;
    private CompassManager manager;

    @Override
    public void onInitialize() {
        this.configPath = FabricLoader.getInstance().getConfigDir().resolve("compasshud.json");
        CompassConfig config = CompassConfig.load(configPath, LOGGER);
        this.manager = new CompassManager(config);

        ServerTickEvents.END_SERVER_TICK.register(manager::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> manager.handleJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> manager.handleQuit(handler.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(manager::shutdown);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                CompassCommand.register(dispatcher, this));
        if (FabricLoader.getInstance().isModLoaded("vanillapings")) {
            VanillaPingsIntegration.register(manager);
        }

        LOGGER.info("[CompassHud] Initialized (display={}).", config.display);
    }

    public CompassManager manager() {
        return manager;
    }

    /** Reload the config from disk and re-apply it. @return true on success. */
    public boolean reload() {
        try {
            CompassConfig config = CompassConfig.load(configPath, LOGGER);
            manager.apply(config);
            LOGGER.info("[CompassHud] Config reloaded (display={}).", config.display);
            return true;
        } catch (RuntimeException e) {
            LOGGER.error("[CompassHud] Reload failed.", e);
            return false;
        }
    }
}
