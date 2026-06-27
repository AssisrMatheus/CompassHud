package dev.assisr.compasshud;

import net.minecraft.network.chat.TextColor;

record CompassMarker(int bearing, String label, TextColor color, String glyph,
                     boolean clampToEdge, boolean resourcePackFont) {
}
