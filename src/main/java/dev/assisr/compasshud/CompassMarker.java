package dev.assisr.compasshud;

import net.minecraft.network.chat.TextColor;

public record CompassMarker(int bearing, String label, TextColor color, char glyph, boolean clampToEdge) {
}
