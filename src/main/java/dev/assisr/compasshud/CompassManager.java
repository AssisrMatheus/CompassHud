package dev.assisr.compasshud;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the per-player HUD state and drives the per-tick update.
 *
 * <p>One {@link ServerBossEvent} is created per viewing player and reused; its
 * name is only re-sent when the rendered text actually changes (the boss event
 * sends update packets to its added players). Action-bar mode resends each
 * update because action-bar text fades client-side.
 */
public final class CompassManager {

    private CompassConfig cfg;
    private CompassRenderer renderer;
    private int tickCounter;

    private final Map<UUID, ServerBossEvent> bars = new HashMap<>();
    private final Map<UUID, String> lastKey = new HashMap<>();
    // Players who have explicitly toggled the HUD away from the default state.
    private final Map<UUID, Boolean> overrides = new HashMap<>();

    public CompassManager(CompassConfig cfg) {
        apply(cfg);
    }

    /** Apply a (re)loaded config: rebuild the renderer and reset per-player view state. */
    public void apply(CompassConfig cfg) {
        this.cfg = cfg;
        this.renderer = new CompassRenderer(cfg);
        clearAllBars();
        lastKey.clear();
        tickCounter = 0;
    }

    public boolean isEnabled(ServerPlayer player) {
        Boolean override = overrides.get(player.getUUID());
        return override != null ? override : cfg.defaultEnabled;
    }

    /** @return the new enabled state. */
    public boolean toggle(ServerPlayer player) {
        boolean now = !isEnabled(player);
        setEnabled(player, now);
        return now;
    }

    public void setEnabled(ServerPlayer player, boolean enabled) {
        overrides.put(player.getUUID(), enabled);
        if (!enabled) {
            hide(player);
        }
        // When enabling, the next tick draws it.
    }

    public void handleQuit(ServerPlayer player) {
        UUID id = player.getUUID();
        ServerBossEvent bar = bars.remove(id);
        if (bar != null) bar.removePlayer(player);
        lastKey.remove(id);
        overrides.remove(id);
    }

    public void shutdown() {
        clearAllBars();
        lastKey.clear();
    }

    // ---- tick -------------------------------------------------------------

    /** Registered against {@code ServerTickEvents.END_SERVER_TICK}. */
    public void tick(MinecraftServer server) {
        if (++tickCounter < cfg.updateIntervalTicks) return;
        tickCounter = 0;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isEnabled(player)) {
                hide(player);
                continue;
            }
            CompassRenderer.Rendered r = renderer.render(player.getYRot());
            if (cfg.display == CompassConfig.DisplayMode.ACTION_BAR) {
                player.sendOverlayMessage(r.component());
            } else {
                showBossBar(player, r);
            }
        }
    }

    private void showBossBar(ServerPlayer player, CompassRenderer.Rendered r) {
        UUID id = player.getUUID();
        ServerBossEvent bar = bars.get(id);
        if (bar == null) {
            bar = new ServerBossEvent(UUID.randomUUID(), r.component(), cfg.bossBarColor, cfg.bossBarOverlay);
            bar.setProgress(cfg.bossBarProgress);
            bars.put(id, bar);
            lastKey.put(id, r.key());
            bar.addPlayer(player);
            return;
        }
        // Re-add is a no-op if already a viewer (e.g. after re-enabling).
        bar.addPlayer(player);
        if (!r.key().equals(lastKey.get(id))) {
            bar.setName(r.component());
            lastKey.put(id, r.key());
        }
    }

    /** Stop showing the HUD to a player without forgetting their toggle choice. */
    private void hide(ServerPlayer player) {
        UUID id = player.getUUID();
        ServerBossEvent bar = bars.remove(id);
        if (bar != null) bar.removePlayer(player);
        lastKey.remove(id);
        // Action-bar text is not removed explicitly; it fades on its own.
    }

    private void clearAllBars() {
        for (ServerBossEvent bar : bars.values()) {
            bar.removeAllPlayers();
        }
        bars.clear();
    }
}
