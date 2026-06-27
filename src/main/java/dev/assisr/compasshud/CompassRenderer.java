package dev.assisr.compasshud;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Turns a player yaw into a compass strip {@link Component}.
 *
 * <p>The strip is a fixed-width character buffer representing a window of the
 * 360° ring centered on the direction the player faces. Marks are painted into
 * the buffer at the column matching their angular offset from the player; a
 * fixed pointer is drawn over the exact center. The buffer is then collapsed
 * into colored runs to minimise the number of component children.
 */
public final class CompassRenderer {

    /** Result of a render: the component to show plus a cheap change-detection key. */
    public record Rendered(Component component, String key) {}

    private final CompassConfig cfg;

    public CompassRenderer(CompassConfig cfg) {
        this.cfg = cfg;
    }

    /**
     * Convert a Minecraft yaw to a compass bearing (N=0, E=90, S=180, W=270).
     * In Minecraft, yaw 0 faces south (+Z), so bearing = yaw + 180 (mod 360).
     */
    public static double yawToBearing(float yaw) {
        double b = (yaw + 180.0) % 360.0;
        return b < 0 ? b + 360.0 : b;
    }

    /** Signed smallest angle from {@code from} to {@code to}, in [-180, 180). */
    private static double angleDelta(double to, double from) {
        double d = (to - from) % 360.0;
        if (d < -180.0) d += 360.0;
        if (d >= 180.0) d -= 360.0;
        return d;
    }

    public Rendered render(float yaw, List<CompassMarker> markers) {
        double bearing = yawToBearing(yaw);
        int width = cfg.width;
        int center = width / 2;
        double degPerChar = cfg.fieldOfView / width;
        double halfFov = cfg.fieldOfView / 2.0;

        char[] buf = new char[width];
        Arrays.fill(buf, cfg.filler);
        TextColor[] col = new TextColor[width];
        Arrays.fill(col, cfg.fillerColor);

        if (cfg.resourcePackMode) {
            for (int i = 0; i < width; i += 5) {
                buf[i] = cfg.minorTick;
            }
            for (int i = 0; i < width; i += 10) {
                buf[i] = cfg.majorTick;
            }
        }

        // Include a little slack so a long label whose center is just off-screen
        // still paints the part of itself that is on-screen.
        double slack = 3 * degPerChar;

        for (CompassConfig.Mark mark : cfg.marks) {
            double delta = angleDelta(mark.bearing(), bearing);
            if (Math.abs(delta) > halfFov + slack) continue;

            int colCenter = (int) Math.round(center + delta / degPerChar);
            String label = mark.label();
            int start = colCenter - label.length() / 2;
            for (int i = 0; i < label.length(); i++) {
                int idx = start + i;
                if (idx < 0 || idx >= width) continue;
                buf[idx] = label.charAt(i);
                col[idx] = mark.color();
            }
        }

        for (CompassMarker marker : markers) {
            double delta = angleDelta(marker.bearing(), bearing);
            if (Math.abs(delta) > halfFov) {
                if (!marker.clampToEdge()) continue;
                delta = Math.copySign(halfFov, delta);
            }

            int colCenter = (int) Math.round(center + delta / degPerChar);
            String label = marker.label();
            if (label == null || label.isEmpty()) {
                if (colCenter >= 0 && colCenter < width) {
                    buf[colCenter] = marker.glyph();
                    col[colCenter] = marker.color();
                }
            } else {
                String iconAndLabel = marker.glyph() + label;
                int start = colCenter - iconAndLabel.length() / 2;
                for (int i = 0; i < iconAndLabel.length(); i++) {
                    int idx = start + i;
                    if (idx < 0 || idx >= width) continue;
                    buf[idx] = iconAndLabel.charAt(i);
                    col[idx] = marker.color();
                }
            }
        }

        if (cfg.hasPointer) {
            // Always highlight the center cell as the heading indicator, but only
            // draw the pointer glyph when nothing else is there. This keeps a
            // centered label (e.g. "N" when facing due north) visible instead of
            // being hidden under the pointer.
            if (buf[center] == cfg.filler) {
                buf[center] = cfg.pointer;
            }
            col[center] = cfg.pointerColor;
        }

        return new Rendered(assemble(buf, col), new String(buf));
    }

    /** Collapse the per-character buffer into runs of equal color. */
    private Component assemble(char[] buf, TextColor[] col) {
        MutableComponent out = Component.empty();
        if (!cfg.leftBracket.isEmpty()) {
            out.append(Component.literal(cfg.leftBracket).setStyle(style(cfg.bracketColor)));
        }
        int i = 0;
        while (i < buf.length) {
            TextColor runColor = col[i];
            int j = i;
            StringBuilder run = new StringBuilder();
            while (j < buf.length && Objects.equals(col[j], runColor)) {
                run.append(buf[j]);
                j++;
            }
            out.append(Component.literal(run.toString()).setStyle(style(runColor)));
            i = j;
        }
        if (!cfg.rightBracket.isEmpty()) {
            out.append(Component.literal(cfg.rightBracket).setStyle(style(cfg.bracketColor)));
        }
        return out;
    }

    private Style style(TextColor color) {
        Style style = Style.EMPTY.withColor(color);
        return cfg.resourcePackMode ? style.withFont(cfg.resourcePackFont) : style;
    }
}
