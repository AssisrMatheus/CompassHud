package dev.assisr.compasshud;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

record SavedSpot(UUID id, int displayId, UUID owner, Optional<String> label, ResourceKey<Level> dimension,
                 BlockPos pos, String glyph, TextColor color, long createdAt) {

    static final Codec<SavedSpot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(SavedSpot::id),
            Codec.INT.optionalFieldOf("display_id", 0).forGetter(SavedSpot::displayId),
            UUIDUtil.STRING_CODEC.fieldOf("owner").forGetter(SavedSpot::owner),
            Codec.STRING.optionalFieldOf("label").forGetter(SavedSpot::label),
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(SavedSpot::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(SavedSpot::pos),
            Codec.STRING.fieldOf("glyph").forGetter(SavedSpot::glyph),
            TextColor.CODEC.fieldOf("color").forGetter(SavedSpot::color),
            Codec.LONG.fieldOf("created_at").forGetter(SavedSpot::createdAt)
    ).apply(instance, SavedSpot::new));

    boolean sameLocation(ResourceKey<Level> otherDimension, BlockPos otherPos) {
        return dimension.equals(otherDimension) && pos.equals(otherPos);
    }

    String displayLabel() {
        return label.filter(s -> !s.isBlank()).orElse("Saved spot");
    }
}
