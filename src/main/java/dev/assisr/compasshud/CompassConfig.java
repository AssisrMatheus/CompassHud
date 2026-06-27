package dev.assisr.compasshud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.BossEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable snapshot of the mod configuration plus the pre-computed list of
 * compass marks. Rebuilt on every reload so the hot render path never touches
 * raw config strings.
 *
 * <p>The on-disk format is JSON (idiomatic for Fabric) at
 * {@code config/compasshud.json}. Unknown keys (such as the {@code _help}
 * documentation block) are ignored on read.
 */
public final class CompassConfig {

    public enum DisplayMode { BOSS_BAR, ACTION_BAR }

    public enum MissingEntityBehavior { FREEZE, EXPIRE }

    /** A single labelled point on the compass ring (cardinal, intercardinal, or number). */
    public record Mark(int bearing, String label, TextColor color) {}

    public final DisplayMode display;
    public final int width;
    public final double fieldOfView;
    public final int updateIntervalTicks;
    public final boolean defaultEnabled;
    public final boolean resourcePackMode;
    public final FontDescription.Resource resourcePackFont;

    public final String filler;
    public final String minorTick;
    public final String majorTick;
    public final String pointer;
    public final String spawnMarker;
    public final String deathMarker;
    public final boolean hasPointer;
    public final String leftBracket;
    public final String rightBracket;

    public final TextColor fillerColor;
    public final TextColor pointerColor;
    public final TextColor bracketColor;
    public final TextColor spawnColor;
    public final TextColor deathColor;

    public final boolean showWorldSpawn;
    public final boolean showLastDeath;
    public final boolean markerSameDimensionOnly;
    public final boolean markerClampToEdge;

    public final boolean pingsEnabled;
    public final String[] pingGlyphs;
    public final TextColor pingColor;
    public final boolean pingSameDimensionOnly;
    public final boolean pingClampToEdge;
    public final MissingEntityBehavior pingMissingEntityBehavior;

    public final String[] savedSpotGlyphs;
    public final TextColor[] savedSpotColors;
    public final int savedSpotMaxCount;
    public final boolean savedSpotResourcePackMode;
    public final boolean savedSpotSameDimensionOnly;
    public final boolean savedSpotClampToEdge;

    public final BossEvent.BossBarColor bossBarColor;
    public final BossEvent.BossBarOverlay bossBarOverlay;
    public final float bossBarProgress;

    /** Marks ordered by bearing (0..359), each bearing appearing at most once. */
    public final List<Mark> marks;

    private CompassConfig(Raw raw, Logger log) {
        this.display = enumOf(DisplayMode.class, raw.display, DisplayMode.BOSS_BAR, log, "display");
        this.width = Math.max(5, raw.width);
        this.fieldOfView = clamp(raw.fieldOfView, 10.0, 360.0);
        this.updateIntervalTicks = Math.max(1, raw.updateIntervalTicks);
        this.defaultEnabled = raw.defaultEnabled;
        this.resourcePackMode = raw.resourcePackMode;
        this.resourcePackFont = new FontDescription.Resource(Identifier.parse(orDefault(raw.resourcePackFont, "compasshud:compass")));

        Raw.Chars chars = raw.characters != null ? raw.characters : new Raw.Chars();
        this.filler = firstGlyph(chars.filler, "-");
        this.minorTick = firstGlyph(chars.minorTick, "|");
        this.majorTick = firstGlyph(chars.majorTick, "|");
        this.hasPointer = chars.pointer != null && !chars.pointer.isEmpty();
        this.pointer = firstGlyph(chars.pointer, "^");
        this.spawnMarker = firstGlyph(chars.spawnMarker, "✦");
        this.deathMarker = firstGlyph(chars.deathMarker, "✕");
        this.leftBracket = orEmpty(chars.leftBracket);
        this.rightBracket = orEmpty(chars.rightBracket);

        Raw.Colors colors = raw.colors != null ? raw.colors : new Raw.Colors();
        this.fillerColor = color(colors.filler, tc(ChatFormatting.DARK_GRAY), log);
        TextColor numberColor = color(colors.number, tc(ChatFormatting.WHITE), log);
        TextColor cardinalColor = color(colors.cardinal, tc(ChatFormatting.AQUA), log);
        TextColor interColor = color(colors.intercardinal, tc(ChatFormatting.GRAY), log);
        this.pointerColor = color(colors.pointer, tc(ChatFormatting.GOLD), log);
        this.bracketColor = color(colors.bracket, tc(ChatFormatting.DARK_GRAY), log);
        this.spawnColor = color(colors.spawn, tc(ChatFormatting.BLUE), log);
        this.deathColor = color(colors.death, tc(ChatFormatting.DARK_PURPLE), log);

        Raw.Markers markers = raw.markers != null ? raw.markers : new Raw.Markers();
        this.showWorldSpawn = markers.worldSpawn;
        this.showLastDeath = markers.lastDeath;
        this.markerSameDimensionOnly = markers.sameDimensionOnly;
        this.markerClampToEdge = markers.clampToEdge;

        Raw.Pings pings = raw.pings != null ? raw.pings : new Raw.Pings();
        this.pingsEnabled = pings.enabled;
        this.pingGlyphs = glyphsOrDefault(pings.glyphs, "✱✸⬟✹");
        this.pingColor = color(pings.color, TextColor.fromRgb(0xFFAA00), log);
        this.pingSameDimensionOnly = pings.sameDimensionOnly;
        this.pingClampToEdge = pings.clampToEdge;
        this.pingMissingEntityBehavior = enumOf(MissingEntityBehavior.class, pings.missingEntityBehavior,
                MissingEntityBehavior.FREEZE, log, "pings.missingEntityBehavior");

        Raw.SavedSpots savedSpots = raw.savedSpots != null ? raw.savedSpots : new Raw.SavedSpots();
        this.savedSpotGlyphs = glyphsOrDefault(savedSpots.glyphs, "⏾■▲◆◉●🞛🞿🟊🟋🟎🟐✪⬟⬣✤");
        this.savedSpotColors = colors(savedSpots.colors, new int[]{
                0x8FB9A8, 0xB8A47E, 0x9AA7C7, 0xB58A8A,
                0x8DB6BD, 0xA79BBE, 0xA6AE7D, 0xB09675
        }, log);
        this.savedSpotMaxCount = savedSpots.maxCount > 0
                ? Math.min(savedSpots.maxCount, this.savedSpotGlyphs.length)
                : this.savedSpotGlyphs.length;
        this.savedSpotResourcePackMode = savedSpots.resourcePackMode;
        this.savedSpotSameDimensionOnly = savedSpots.sameDimensionOnly;
        this.savedSpotClampToEdge = savedSpots.clampToEdge;

        Raw.Boss boss = raw.bossbar != null ? raw.bossbar : new Raw.Boss();
        this.bossBarColor = enumOf(BossEvent.BossBarColor.class, boss.color, BossEvent.BossBarColor.WHITE, log, "bossbar.color");
        this.bossBarOverlay = enumOf(BossEvent.BossBarOverlay.class, boss.overlay, BossEvent.BossBarOverlay.PROGRESS, log, "bossbar.overlay");
        this.bossBarProgress = (float) clamp(boss.progress, 0.0, 1.0);

        this.marks = buildMarks(raw.markInterval, raw.showDegrees, raw.showIntercardinals,
                numberColor, interColor, cardinalColor);
    }

    /**
     * Load the config from {@code path}, creating a documented default file if it
     * does not exist yet. Never throws: parse errors fall back to defaults.
     */
    public static CompassConfig load(Path path, Logger log) {
        Raw raw = readOrCreate(path, log);
        return new CompassConfig(raw, log);
    }

    // ---- disk I/O ---------------------------------------------------------

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Raw readOrCreate(Path path, Logger log) {
        try {
            if (Files.notExists(path)) {
                if (path.getParent() != null) Files.createDirectories(path.getParent());
                Files.writeString(path, DEFAULT_JSON, StandardCharsets.UTF_8);
                log.info("[CompassHud] Wrote default config to {}", path);
                return new Raw();
            }
            String json = Files.readString(path, StandardCharsets.UTF_8);
            Raw raw = GSON.fromJson(json, Raw.class);
            return raw != null ? raw : new Raw();
        } catch (IOException e) {
            log.error("[CompassHud] Could not read config {} ({}); using defaults.", path, e.getMessage());
            return new Raw();
        } catch (RuntimeException e) {
            log.error("[CompassHud] Invalid config {} ({}); using defaults.", path, e.getMessage());
            return new Raw();
        }
    }

    /** Mutable view used only for (de)serialization; defaults live in the field initializers. */
    static final class Raw {
        String display = "BOSS_BAR";
        int width = 93;
        double fieldOfView = 180.0;
        int markInterval = 30;
        boolean showDegrees = true;
        boolean showIntercardinals = false;
        int updateIntervalTicks = 1;
        boolean defaultEnabled = true;
        boolean resourcePackMode = true;
        String resourcePackFont = "compasshud:compass";
        Chars characters = new Chars();
        Colors colors = new Colors();
        Markers markers = new Markers();
        Pings pings = new Pings();
        SavedSpots savedSpots = new SavedSpots();
        Boss bossbar = new Boss();

        static final class Chars {
            String filler = "\uE000";
            String minorTick = "\uE001";
            String majorTick = "\uE002";
            String pointer = "";
            String spawnMarker = "✦";
            String deathMarker = "✕";
            String leftBracket = "";
            String rightBracket = "";
        }

        static final class Colors {
            String filler = "#c9d7ff";
            String number = "WHITE";
            String cardinal = "AQUA";
            String intercardinal = "GRAY";
            String pointer = "GOLD";
            String bracket = "DARK_GRAY";
            String spawn = "#33CC33";
            String death = "#AA0000";
        }

        static final class Markers {
            boolean worldSpawn = true;
            boolean lastDeath = true;
            boolean sameDimensionOnly = true;
            boolean clampToEdge = true;
        }

        static final class Pings {
            boolean enabled = true;
            String glyphs = "✱✸⬟✹";
            String color = "GOLD";
            boolean sameDimensionOnly = true;
            boolean clampToEdge = true;
            String missingEntityBehavior = "FREEZE";
        }

        static final class SavedSpots {
            String glyphs = "⏾■▲◆◉●🞛🞿🟊🟋🟎🟐✪⬟⬣✤";
            List<String> colors = List.of(
                    "#8FB9A8", "#B8A47E", "#9AA7C7", "#B58A8A",
                    "#8DB6BD", "#A79BBE", "#A6AE7D", "#B09675"
            );
            int maxCount = 0;
            boolean resourcePackMode = false;
            boolean sameDimensionOnly = true;
            boolean clampToEdge = true;
        }

        static final class Boss {
            String color = "WHITE";
            String overlay = "PROGRESS";
            double progress = 0.0;
        }
    }

    /**
     * Build the ring of marks. Precedence (later overrides earlier on the same
     * bearing): numbers < intercardinals < cardinals. This makes a number that
     * lands on a cardinal show the letter instead.
     */
    private static List<Mark> buildMarks(int interval, boolean showDegrees, boolean showInter,
                                         TextColor numberColor, TextColor interColor, TextColor cardinalColor) {
        Map<Integer, Mark> byBearing = new LinkedHashMap<>();
        if (showDegrees && interval > 0) {
            for (int d = 0; d < 360; d += interval) {
                byBearing.put(d, new Mark(d, Integer.toString(d), numberColor));
            }
        }
        if (showInter) {
            byBearing.put(45, new Mark(45, "NE", interColor));
            byBearing.put(135, new Mark(135, "SE", interColor));
            byBearing.put(225, new Mark(225, "SW", interColor));
            byBearing.put(315, new Mark(315, "NW", interColor));
        }
        byBearing.put(0, new Mark(0, "N", cardinalColor));
        byBearing.put(90, new Mark(90, "E", cardinalColor));
        byBearing.put(180, new Mark(180, "S", cardinalColor));
        byBearing.put(270, new Mark(270, "W", cardinalColor));
        return new ArrayList<>(byBearing.values());
    }

    // ---- small parsing helpers -------------------------------------------

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String firstGlyph(String s, String fallback) {
        if (s == null || s.isEmpty()) return fallback;
        int codePoint = s.codePointAt(0);
        return Character.toString(codePoint);
    }

    private static String[] glyphsOrDefault(String s, String fallback) {
        String value = (s == null || s.isBlank()) ? fallback : s;
        String[] out = value.codePoints()
                .mapToObj(Character::toString)
                .toArray(String[]::new);
        return out.length == 0 ? glyphsOrDefault(fallback, fallback) : out;
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String orDefault(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }

    private static TextColor tc(ChatFormatting f) {
        return TextColor.fromRgb(COLOR_RGB.getOrDefault(f, 0xFFFFFF));
    }

    private static TextColor color(String raw, TextColor fallback, Logger log) {
        if (raw == null || raw.isBlank()) return fallback;
        String s = raw.trim();
        if (s.startsWith("#")) {
            try {
                return TextColor.fromRgb(Integer.parseInt(s.substring(1), 16));
            } catch (NumberFormatException ignored) {
                // fall through to named lookup / warning
            }
        }
        ChatFormatting cf = FORMATTING_BY_NAME.get(s.toLowerCase(Locale.ROOT));
        if (cf != null && COLOR_RGB.containsKey(cf)) {
            return tc(cf);
        }
        log.warn("[CompassHud] Unknown color '{}', using fallback.", raw);
        return fallback;
    }

    private static TextColor[] colors(List<String> raw, int[] fallbackRgb, Logger log) {
        List<String> source = raw == null || raw.isEmpty() ? List.of() : raw;
        if (source.isEmpty()) {
            TextColor[] out = new TextColor[fallbackRgb.length];
            for (int i = 0; i < fallbackRgb.length; i++) {
                out[i] = TextColor.fromRgb(fallbackRgb[i]);
            }
            return out;
        }
        TextColor[] out = new TextColor[source.size()];
        for (int i = 0; i < source.size(); i++) {
            out[i] = color(source.get(i), TextColor.fromRgb(fallbackRgb[i % fallbackRgb.length]), log);
        }
        return out;
    }

    private static final Map<ChatFormatting, Integer> COLOR_RGB = Map.ofEntries(
            Map.entry(ChatFormatting.BLACK, 0x000000),
            Map.entry(ChatFormatting.DARK_BLUE, 0x0000AA),
            Map.entry(ChatFormatting.DARK_GREEN, 0x00AA00),
            Map.entry(ChatFormatting.DARK_AQUA, 0x00AAAA),
            Map.entry(ChatFormatting.DARK_RED, 0xAA0000),
            Map.entry(ChatFormatting.DARK_PURPLE, 0xAA00AA),
            Map.entry(ChatFormatting.GOLD, 0xFFAA00),
            Map.entry(ChatFormatting.GRAY, 0xAAAAAA),
            Map.entry(ChatFormatting.DARK_GRAY, 0x555555),
            Map.entry(ChatFormatting.BLUE, 0x5555FF),
            Map.entry(ChatFormatting.GREEN, 0x55FF55),
            Map.entry(ChatFormatting.AQUA, 0x55FFFF),
            Map.entry(ChatFormatting.RED, 0xFF5555),
            Map.entry(ChatFormatting.LIGHT_PURPLE, 0xFF55FF),
            Map.entry(ChatFormatting.YELLOW, 0xFFFF55),
            Map.entry(ChatFormatting.WHITE, 0xFFFFFF)
    );

    private static final Map<String, ChatFormatting> FORMATTING_BY_NAME = formattingByName();

    private static Map<String, ChatFormatting> formattingByName() {
        Map<String, ChatFormatting> out = new LinkedHashMap<>();
        for (ChatFormatting formatting : ChatFormatting.values()) {
            out.put(formatting.name().toLowerCase(Locale.ROOT), formatting);
        }
        return out;
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String raw, E fallback, Logger log, String key) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            log.warn("[CompassHud] Invalid value '{}' for {}, using {}.", raw, key, fallback);
            return fallback;
        }
    }

    /** Documented default config written verbatim on first run. */
    private static final String DEFAULT_JSON = """
            {
              "_help": [
                "CompassHud - server-side directional compass HUD (no client mod needed).",
                "Edit this file then run /compass reload (or restart the server).",
                "",
                "display: BOSS_BAR (top of screen) or ACTION_BAR (above the hotbar, text only).",
                "width: characters across the strip; ~33 is roughly 1/3 of the screen. The client centers it.",
                "fieldOfView: degrees of arc shown across the width (180 = half circle; smaller = more zoomed in).",
                "markInterval: degrees between numeric marks (e.g. 30, 45, 50, 90). Marks on a cardinal show N/E/S/W.",
                "showDegrees: show the degree numbers (false = letters-only compass).",
                "showIntercardinals: show NE/SE/SW/NW (they replace numbers at those bearings).",
                "updateIntervalTicks: refresh period in server ticks (20 = 1s). 1 is the fastest server-side cadence; boss bar only sends a packet on change.",
                "defaultEnabled: whether players see the compass on join (toggle per-session with /compass).",
                "resourcePackMode: use CompassHud's custom-font glyphs. Requires clients to accept the CompassHud resource pack.",
                "resourcePackFont: font id defined by the CompassHud resource pack.",
                "characters.pointer: set to \\"\\" to disable the center glyph and highlight.",
                "characters filler/minorTick/majorTick/pointer default to private-use glyphs from the resource pack; spawnMarker/deathMarker default to visible symbols.",
                "markers.worldSpawn: show an icon pointing toward world spawn.",
                "markers.lastDeath: show an icon pointing toward the player's last death location.",
                "markers.sameDimensionOnly: hide markers from other dimensions.",
                "markers.clampToEdge: keep off-screen markers pinned to the compass edge.",
                "pings.enabled: mirror accepted VanillaPings pings onto the compass.",
                "pings.glyphs: single-character glyph pool for concurrent ping markers.",
                "pings.sameDimensionOnly: hide VanillaPings compass markers from other dimensions.",
                "pings.clampToEdge: keep off-screen VanillaPings compass markers pinned to the compass edge.",
                "pings.missingEntityBehavior: FREEZE at last known position or EXPIRE when an entity ping target disappears.",
                "savedSpots.glyphs: one-glyph marker pool for /compass save entries; pool length is the default per-player per-dimension capacity.",
                "savedSpots.colors: muted color palette assigned when a saved spot is created and then persisted.",
                "savedSpots.maxCount: 0 means use the glyph pool length per dimension; positive values are capped by the glyph pool length.",
                "savedSpots.resourcePackMode: use the CompassHud font for saved spot glyphs; false uses vanilla/default glyph fallback.",
                "savedSpots.sameDimensionOnly: hide saved spots from dimensions other than the player's current dimension.",
                "savedSpots.clampToEdge: keep off-screen saved spots pinned to the compass edge.",
                "colors: a Minecraft color name (WHITE, AQUA, GOLD, DARK_GRAY, ...) or a hex string like \\"#55ffff\\".",
                "bossbar.color: PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE.",
                "The provided resource pack hides WHITE boss-bar textures; use WHITE for the CompassHud boss bar.",
                "bossbar.overlay: PROGRESS, NOTCHED_6, NOTCHED_10, NOTCHED_12, NOTCHED_20.",
                "bossbar.progress: 0.0 - 1.0 length of the filled bar (purely cosmetic)."
              ],

              "display": "BOSS_BAR",
              "width": 93,
              "fieldOfView": 180,
              "markInterval": 30,
              "showDegrees": true,
              "showIntercardinals": false,
              "updateIntervalTicks": 1,
              "defaultEnabled": true,
              "resourcePackMode": true,
              "resourcePackFont": "compasshud:compass",

              "characters": {
                "filler": "\\uE000",
                "minorTick": "\\uE001",
                "majorTick": "\\uE002",
                "pointer": "",
                "spawnMarker": "✦",
                "deathMarker": "✕",
                "leftBracket": "",
                "rightBracket": ""
              },

              "colors": {
                "filler": "#c9d7ff",
                "number": "WHITE",
                "cardinal": "AQUA",
                "intercardinal": "GRAY",
                "pointer": "GOLD",
                "bracket": "DARK_GRAY",
                "spawn": "#33CC33",
                "death": "#AA0000"
              },

              "markers": {
                "worldSpawn": true,
                "lastDeath": true,
                "sameDimensionOnly": true,
                "clampToEdge": true
              },

              "pings": {
                "enabled": true,
                "glyphs": "✱✸⬟✹",
                "color": "GOLD",
                "sameDimensionOnly": true,
                "clampToEdge": true,
                "missingEntityBehavior": "FREEZE"
              },

              "savedSpots": {
                "glyphs": "⏾■▲◆◉●🞛🞿🟊🟋🟎🟐✪⬟⬣✤",
                "colors": [
                  "#8FB9A8",
                  "#B8A47E",
                  "#9AA7C7",
                  "#B58A8A",
                  "#8DB6BD",
                  "#A79BBE",
                  "#A6AE7D",
                  "#B09675"
                ],
                "maxCount": 0,
                "resourcePackMode": false,
                "sameDimensionOnly": true,
                "clampToEdge": true
              },

              "bossbar": {
                "color": "WHITE",
                "overlay": "PROGRESS",
                "progress": 0.0
              }
            }
            """;
}
