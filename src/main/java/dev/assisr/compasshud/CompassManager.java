package dev.assisr.compasshud;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashMap;
import java.util.List;
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

    private static final int TARGET_REFRESH_TICKS = 100;

    private CompassConfig cfg;
    private CompassRenderer renderer;
    private int tickCounter;
    private int targetRefreshCounter;
    private long elapsedTicks;
    private int nextPingGlyph;
    private GlobalPos worldSpawn;
    private SavedSpotData savedSpotData;

    private final Map<UUID, ServerBossEvent> bars = new HashMap<>();
    private final Map<UUID, String> lastKey = new HashMap<>();
    // Players who have explicitly toggled the HUD away from the default state.
    private final Map<UUID, Boolean> overrides = new HashMap<>();
    private final Map<UUID, PlayerTargets> targets = new HashMap<>();
    private final List<VanillaPingMarker> vanillaPings = new ArrayList<>();

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
        targetRefreshCounter = 0;
        for (PlayerTargets target : targets.values()) {
            target.savedSpotsLoaded = false;
        }
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
        targets.remove(id);
    }

    public void shutdown(MinecraftServer server) {
        clearAllBars();
        lastKey.clear();
        vanillaPings.clear();
        if (savedSpotData != null && savedSpotData.isDirty()) {
            server.getDataStorage().saveAndJoin();
        }
        savedSpotData = null;
    }

    public void addVanillaPing(ResourceKey<Level> dimension, Vec3 position, UUID targetEntity, int maxAgeTicks) {
        if (!cfg.pingsEnabled) {
            return;
        }
        String glyph = cfg.pingGlyphs[nextPingGlyph++ % cfg.pingGlyphs.length];
        vanillaPings.add(new VanillaPingMarker(dimension, position, targetEntity,
                elapsedTicks + Math.max(1, maxAgeTicks), glyph));
    }

    // ---- tick -------------------------------------------------------------

    /** Registered against {@code ServerTickEvents.END_SERVER_TICK}. */
    public void tick(MinecraftServer server) {
        elapsedTicks++;
        data(server);
        updateVanillaPings(server);
        refreshTargetsIfNeeded(server);

        if (++tickCounter < cfg.updateIntervalTicks) return;
        tickCounter = 0;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isEnabled(player)) {
                hide(player);
                continue;
            }
            CompassRenderer.Rendered r = renderer.render(player.getYRot(), markersFor(player));
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

    public void handleJoin(ServerPlayer player) {
        refreshPlayerTargets(player);
        refreshSavedSpots(player);
    }

    public void handleRespawn(ServerPlayer player) {
        refreshPlayerTargets(player);
    }

    public void handleDeath(ServerPlayer player) {
        refreshPlayerTargets(player);
    }

    public void refreshAllTargets(MinecraftServer server) {
        this.worldSpawn = server.overworld().getRespawnData().globalPos();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            refreshPlayerTargets(player);
        }
    }

    private void refreshTargetsIfNeeded(MinecraftServer server) {
        if (worldSpawn == null) {
            refreshAllTargets(server);
            return;
        }
        if (++targetRefreshCounter < TARGET_REFRESH_TICKS) return;
        targetRefreshCounter = 0;
        refreshAllTargets(server);
    }

    private void refreshPlayerTargets(ServerPlayer player) {
        targets.computeIfAbsent(player.getUUID(), id -> new PlayerTargets())
                .lastDeath = player.getLastDeathLocation().orElse(null);
    }

    private List<CompassMarker> markersFor(ServerPlayer player) {
        PlayerTargets state = targets.computeIfAbsent(player.getUUID(), id -> new PlayerTargets());
        if (!state.savedSpotsLoaded) {
            refreshSavedSpots(player);
        }
        state.markers.clear();

        if (cfg.showWorldSpawn && worldSpawn != null) {
            addMarker(state.markers, player, worldSpawn,
                    "", cfg.spawnColor, cfg.spawnMarker);
        }
        if (cfg.showLastDeath && state.lastDeath != null) {
            addMarker(state.markers, player, state.lastDeath, "", cfg.deathColor, cfg.deathMarker);
        }
        addSavedSpotMarkers(state.markers, player, state.savedSpots);
        if (cfg.pingsEnabled) {
            addVanillaPingMarkers(state.markers, player);
        }
        return state.markers;
    }

    private void addVanillaPingMarkers(List<CompassMarker> markers, ServerPlayer player) {
        for (VanillaPingMarker ping : vanillaPings) {
            if (cfg.pingSameDimensionOnly && !ping.dimension.equals(player.level().dimension())) {
                continue;
            }
            markers.add(new CompassMarker(bearingTo(player, ping.position), "", cfg.pingColor,
                    ping.glyph, cfg.pingClampToEdge, cfg.resourcePackMode));
        }
    }

    private void addMarker(List<CompassMarker> markers, ServerPlayer player, GlobalPos target,
                           String label, TextColor color, String glyph) {
        if (cfg.markerSameDimensionOnly && !target.dimension().equals(player.level().dimension())) {
            return;
        }
        markers.add(new CompassMarker(bearingTo(player, target.pos()), label, color,
                glyph, cfg.markerClampToEdge, cfg.resourcePackMode));
    }

    private void addSavedSpotMarkers(List<CompassMarker> markers, ServerPlayer player, List<SavedSpot> savedSpots) {
        if (savedSpots.isEmpty()) {
            return;
        }
        ResourceKey<Level> playerDimension = player.level().dimension();
        for (SavedSpot spot : savedSpots) {
            if (cfg.savedSpotSameDimensionOnly && !spot.dimension().equals(playerDimension)) {
                continue;
            }
            markers.add(new CompassMarker(bearingTo(player, spot.pos()), "", spot.color(),
                    spot.glyph(), cfg.savedSpotClampToEdge, cfg.resourcePackMode && cfg.savedSpotResourcePackMode));
        }
    }

    public SavedSpotData.SaveResult saveSpot(ServerPlayer player, BlockPos pos, String label) {
        SavedSpotData.SaveResult result = data(player.level().getServer()).save(player.getUUID(),
                label == null || label.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(label),
                player.level().dimension(), pos, cfg);
        refreshSavedSpots(player);
        return result;
    }

    public List<SavedSpot> savedSpots(ServerPlayer player) {
        PlayerTargets state = targets.computeIfAbsent(player.getUUID(), id -> new PlayerTargets());
        if (!state.savedSpotsLoaded) {
            refreshSavedSpots(player);
        }
        return List.copyOf(state.savedSpots);
    }

    public SavedSpotData.DeleteResult deleteSavedSpot(ServerPlayer player, int displayId) {
        SavedSpotData.DeleteResult result = data(player.level().getServer()).delete(player.getUUID(), displayId);
        refreshSavedSpots(player);
        return result;
    }

    private void refreshSavedSpots(ServerPlayer player) {
        PlayerTargets state = targets.computeIfAbsent(player.getUUID(), id -> new PlayerTargets());
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            state.savedSpots = List.of();
        } else {
            state.savedSpots = data(server).spotsFor(player.getUUID());
        }
        state.savedSpotsLoaded = true;
    }

    private SavedSpotData data(MinecraftServer server) {
        if (savedSpotData == null) {
            savedSpotData = server.getDataStorage().computeIfAbsent(SavedSpotData.TYPE);
        }
        return savedSpotData;
    }

    private static int bearingTo(ServerPlayer player, BlockPos target) {
        double dx = (target.getX() + 0.5) - player.getX();
        double dz = (target.getZ() + 0.5) - player.getZ();
        return bearingTo(player, dx, dz);
    }

    private static int bearingTo(ServerPlayer player, Vec3 target) {
        double dx = target.x() - player.getX();
        double dz = target.z() - player.getZ();
        return bearingTo(player, dx, dz);
    }

    private static int bearingTo(ServerPlayer player, double dx, double dz) {
        double bearing = Math.toDegrees(Math.atan2(dx, -dz)) % 360.0;
        if (bearing < 0) bearing += 360.0;
        return (int) Math.round(bearing) % 360;
    }

    private void updateVanillaPings(MinecraftServer server) {
        Iterator<VanillaPingMarker> iterator = vanillaPings.iterator();
        while (iterator.hasNext()) {
            VanillaPingMarker ping = iterator.next();
            if (elapsedTicks >= ping.expiresAtTick) {
                iterator.remove();
                continue;
            }
            if (ping.targetEntity == null) {
                continue;
            }

            ServerLevel level = server.getLevel(ping.dimension);
            Entity entity = level != null ? level.getEntity(ping.targetEntity) : null;
            if (entity != null && !entity.isRemoved()) {
                ping.position = entity.position();
            } else if (cfg.pingMissingEntityBehavior == CompassConfig.MissingEntityBehavior.EXPIRE) {
                iterator.remove();
            }
        }
    }

    private static final class PlayerTargets {
        private final ArrayList<CompassMarker> markers = new ArrayList<>(4);
        private GlobalPos lastDeath;
        private List<SavedSpot> savedSpots = List.of();
        private boolean savedSpotsLoaded;
    }

    private static final class VanillaPingMarker {
        private final ResourceKey<Level> dimension;
        private Vec3 position;
        private final UUID targetEntity;
        private final long expiresAtTick;
        private final String glyph;

        private VanillaPingMarker(ResourceKey<Level> dimension, Vec3 position, UUID targetEntity,
                                  long expiresAtTick, String glyph) {
            this.dimension = dimension;
            this.position = position;
            this.targetEntity = targetEntity;
            this.expiresAtTick = expiresAtTick;
            this.glyph = glyph;
        }
    }
}
