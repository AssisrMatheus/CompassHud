package dev.assisr.compasshud;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SavedSpotData extends SavedData {

    static final Codec<SavedSpotData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            SavedSpot.CODEC.listOf().optionalFieldOf("spots", List.of()).forGetter(SavedSpotData::allSpots),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT)
                    .optionalFieldOf("next_ids", Map.of())
                    .forGetter(SavedSpotData::nextIdsForCodec)
    ).apply(instance, SavedSpotData::new));

    static final SavedDataType<SavedSpotData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(CompassHud.MOD_ID, "saved_spots"),
            SavedSpotData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Map<UUID, ArrayList<SavedSpot>> byOwner = new HashMap<>();
    private final Map<UUID, Integer> nextIds = new HashMap<>();

    public SavedSpotData() {
    }

    private SavedSpotData(List<SavedSpot> spots, Map<UUID, Integer> nextIds) {
        this.nextIds.putAll(nextIds);
        for (SavedSpot spot : spots) {
            byOwner.computeIfAbsent(spot.owner(), id -> new ArrayList<>()).add(spot);
        }
        for (Map.Entry<UUID, ArrayList<SavedSpot>> entry : byOwner.entrySet()) {
            UUID owner = entry.getKey();
            ArrayList<SavedSpot> playerSpots = entry.getValue();
            playerSpots.sort(Comparator.comparingLong(SavedSpot::createdAt));
            normalizeDisplayIds(playerSpots);
            int next = maxDisplayId(playerSpots) + 1;
            if (this.nextIds.getOrDefault(owner, 0) < next) {
                this.nextIds.put(owner, next);
                setDirty();
            }
        }
    }

    List<SavedSpot> spotsFor(UUID playerId) {
        ArrayList<SavedSpot> spots = byOwner.get(playerId);
        return spots == null ? List.of() : List.copyOf(spots);
    }

    SaveResult save(UUID playerId, Optional<String> label, ResourceKey<Level> dimension, BlockPos pos,
                    CompassConfig cfg) {
        ArrayList<SavedSpot> spots = byOwner.computeIfAbsent(playerId, id -> new ArrayList<>());
        List<SavedSpot> dimensionSpots = spotsInDimension(spots, dimension);
        for (SavedSpot existing : dimensionSpots) {
            if (existing.sameLocation(dimension, pos)) {
                return SaveResult.duplicate(existing);
            }
        }

        int capacity = cfg.savedSpotMaxCount;
        if (dimensionSpots.size() >= capacity) {
            return SaveResult.capacityReached(capacity);
        }

        GlyphChoice choice = chooseGlyph(dimensionSpots, cfg);
        if (choice == null) {
            return SaveResult.capacityReached(capacity);
        }

        SavedSpot created = new SavedSpot(UUID.randomUUID(), allocateDisplayId(playerId, spots), playerId, cleanLabel(label), dimension,
                pos.immutable(), choice.glyph(), choice.color(), Instant.now().toEpochMilli());
        spots.add(created);
        setDirty();
        return SaveResult.created(created);
    }

    DeleteResult delete(UUID playerId, int displayId) {
        ArrayList<SavedSpot> spots = byOwner.get(playerId);
        if (spots == null) {
            return DeleteResult.invalid(0);
        }

        int index = -1;
        for (int i = 0; i < spots.size(); i++) {
            if (spots.get(i).displayId() == displayId) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return DeleteResult.invalid(spots == null ? 0 : spots.size());
        }

        SavedSpot removed = spots.remove(index);
        if (spots.isEmpty()) {
            byOwner.remove(playerId);
        }
        setDirty();
        return DeleteResult.deleted(removed);
    }

    private List<SavedSpot> allSpots() {
        return byOwner.values().stream()
                .flatMap(List::stream)
                .sorted(Comparator.comparing(SavedSpot::owner).thenComparingLong(SavedSpot::createdAt))
                .toList();
    }

    private Map<UUID, Integer> nextIdsForCodec() {
        return Map.copyOf(nextIds);
    }

    private static Optional<String> cleanLabel(Optional<String> label) {
        return label.map(String::trim).filter(s -> !s.isEmpty()).map(s -> s.length() > 64 ? s.substring(0, 64) : s);
    }

    private static List<SavedSpot> spotsInDimension(List<SavedSpot> spots, ResourceKey<Level> dimension) {
        return spots.stream()
                .filter(spot -> spot.dimension().equals(dimension))
                .toList();
    }

    private void normalizeDisplayIds(ArrayList<SavedSpot> spots) {
        Set<Integer> used = new HashSet<>();
        boolean changed = false;
        for (int i = 0; i < spots.size(); i++) {
            SavedSpot spot = spots.get(i);
            int displayId = spot.displayId();
            if (displayId <= 0 || !used.add(displayId)) {
                int replacement = nextLegacyDisplayId(used);
                used.add(replacement);
                spots.set(i, new SavedSpot(spot.id(), replacement, spot.owner(), spot.label(), spot.dimension(),
                        spot.pos(), spot.glyph(), spot.color(), spot.createdAt()));
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    private int allocateDisplayId(UUID playerId, List<SavedSpot> spots) {
        int next = Math.max(nextIds.getOrDefault(playerId, 1), maxDisplayId(spots) + 1);
        nextIds.put(playerId, next + 1);
        return next;
    }

    private static int maxDisplayId(List<SavedSpot> spots) {
        int max = 0;
        for (SavedSpot spot : spots) {
            max = Math.max(max, spot.displayId());
        }
        return max;
    }

    private static int nextLegacyDisplayId(Set<Integer> used) {
        int id = 1;
        while (used.contains(id)) {
            id++;
        }
        return id;
    }

    private static GlyphChoice chooseGlyph(List<SavedSpot> existing, CompassConfig cfg) {
        Set<String> used = new HashSet<>();
        for (SavedSpot spot : existing) {
            used.add(spot.glyph());
        }
        for (int i = 0; i < cfg.savedSpotMaxCount; i++) {
            String glyph = cfg.savedSpotGlyphs[i];
            if (!used.contains(glyph)) {
                TextColor color = cfg.savedSpotColors[i % cfg.savedSpotColors.length];
                return new GlyphChoice(glyph, color);
            }
        }
        return null;
    }

    private record GlyphChoice(String glyph, TextColor color) {
    }

    record SaveResult(Status status, SavedSpot spot, int capacity) {
        enum Status { CREATED, DUPLICATE, CAPACITY_REACHED }

        static SaveResult created(SavedSpot spot) {
            return new SaveResult(Status.CREATED, spot, 0);
        }

        static SaveResult duplicate(SavedSpot spot) {
            return new SaveResult(Status.DUPLICATE, spot, 0);
        }

        static SaveResult capacityReached(int capacity) {
            return new SaveResult(Status.CAPACITY_REACHED, null, capacity);
        }
    }

    record DeleteResult(Status status, SavedSpot spot, int size) {
        enum Status { DELETED, INVALID_INDEX }

        static DeleteResult deleted(SavedSpot spot) {
            return new DeleteResult(Status.DELETED, spot, 0);
        }

        static DeleteResult invalid(int size) {
            return new DeleteResult(Status.INVALID_INDEX, null, size);
        }
    }
}
