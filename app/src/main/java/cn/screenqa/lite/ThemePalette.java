package cn.screenqa.lite;

/** One color contract for the whole app: pages, cards, dock, overlay and animations read it. */
final class ThemePalette {
    final String id, name;
    final boolean dark;
    final int background, surface, surfaceAlt, border;
    final int foreground, secondary, accent, accentSoft, onAccent;
    final int heroStart, heroEnd;
    final int glassTint, glassEdge, glassSheen;
    final int danger;
    final int auroraA, auroraB, auroraC;

    private ThemePalette(Builder b) {
        id = b.id; name = b.name; dark = b.dark;
        background = b.background; surface = b.surface; surfaceAlt = b.surfaceAlt; border = b.border;
        foreground = b.foreground; secondary = b.secondary;
        accent = b.accent; accentSoft = b.accentSoft; onAccent = b.onAccent;
        heroStart = b.heroStart; heroEnd = b.heroEnd;
        glassTint = b.glassTint; glassEdge = b.glassEdge; glassSheen = b.glassSheen;
        danger = b.danger; auroraA = b.auroraA; auroraB = b.auroraB; auroraC = b.auroraC;
    }

    static final class Builder {
        private final String id, name;
        private final boolean dark;
        private final int background, surface, surfaceAlt, border, foreground, secondary;
        private int accent, accentSoft, onAccent;
        private int heroStart, heroEnd;
        private int glassTint, glassEdge, glassSheen;
        private int danger = 0xFFFF6B6B;
        private int auroraA, auroraB, auroraC;

        Builder(String id, String name, boolean dark, int background, int surface, int surfaceAlt,
                int border, int foreground, int secondary) {
            this.id = id; this.name = name; this.dark = dark;
            this.background = background; this.surface = surface; this.surfaceAlt = surfaceAlt;
            this.border = border; this.foreground = foreground; this.secondary = secondary;
        }
        Builder accent(int value, int soft, int on) { accent = value; accentSoft = soft; onAccent = on; return this; }
        Builder hero(int start, int end) { heroStart = start; heroEnd = end; return this; }
        Builder glass(int tint, int edge, int sheen) { glassTint = tint; glassEdge = edge; glassSheen = sheen; return this; }
        Builder aurora(int a, int b, int c) { auroraA = a; auroraB = b; auroraC = c; return this; }
        Builder danger(int value) { danger = value; return this; }
        ThemePalette build() { return new ThemePalette(this); }
    }

    /**
     * The floating overlay window's identity: the deep green panel, its mint ring and the pale text
     * of the on-screen assistant. It is the built-in default so both surfaces read as one product.
     */
    static final ThemePalette OVERLAY_GREEN = new Builder("overlay_green", "悬浮窗绿", true,
            0xFF0A1512, 0xFF142420, 0xFF1D3930, 0xFF3A6250, 0xFFF4FFF8, 0xFFB8D3C5)
            .accent(0xFF6EE7A8, 0xFF16382C, 0xFF062017)
            .hero(0xFF1F5C42, 0xFF0B1613)
            .glass(0xCC10201B, 0x8C6EE7A8, 0x3DFFFFFF)
            .aurora(0x3D68E6A0, 0x265FE3D0, 0x1A2FA8E0)
            .build();

    static final ThemePalette BLACK_GOLD = new Builder("black_gold", "黑金", true,
            0xFF08080A, 0xFF141210, 0xFF1D1913, 0xFF352E20, 0xFFF7F0DE, 0xFFA89B79)
            .accent(0xFFE9C56B, 0xFF2A2210, 0xFF1A1409)
            .hero(0xFF4A370F, 0xFF100D07)
            .glass(0xCC1A1509, 0x8CE9C56B, 0x3DFFFFFF)
            .aurora(0x3DE9C56B, 0x26FF9E4D, 0x1A6BC5E9)
            .build();

    static final ThemePalette OBSIDIAN_TEAL = new Builder("obsidian_teal", "曜石青", true,
            0xFF060B0B, 0xFF0F1717, 0xFF16211F, 0xFF243432, 0xFFE9F6F1, 0xFF8FA8A1)
            .accent(0xFF5FE3B4, 0xFF11302A, 0xFF04140E)
            .hero(0xFF0E5140, 0xFF071110)
            .glass(0xCC0B1512, 0x8C5FE3B4, 0x38FFFFFF)
            .aurora(0x3D5FE3B4, 0x262FA8E0, 0x1A9B7BFF)
            .build();

    static final ThemePalette MIDNIGHT_VIOLET = new Builder("midnight_violet", "暗夜紫", true,
            0xFF0A0812, 0xFF141020, 0xFF1C1730, 0xFF302950, 0xFFF1ECFF, 0xFFA79FC0)
            .accent(0xFFB48CFF, 0xFF241A44, 0xFF140A28)
            .hero(0xFF3B2A6B, 0xFF0D0A18)
            .glass(0xCC120E20, 0x8CB48CFF, 0x38FFFFFF)
            .aurora(0x3DB48CFF, 0x26FF8AD8, 0x1A6FA8FF)
            .build();

    static final ThemePalette CRIMSON_NIGHT = new Builder("crimson_night", "赤夜红", true,
            0xFF0C0708, 0xFF171011, 0xFF221618, 0xFF3E2628, 0xFFFBEDEC, 0xFFB39795)
            .accent(0xFFFF8A73, 0xFF3A1C17, 0xFF2A0C06)
            .hero(0xFF6B2318, 0xFF120808)
            .glass(0xCC1A0F0F, 0x8CFF8A73, 0x38FFFFFF)
            .aurora(0x3DFF8A73, 0x26FFC46B, 0x1AE86BC0)
            .build();

    static final ThemePalette JADE_GREEN = new Builder("jade_green", "翡翠绿", true,
            0xFF070C08, 0xFF101A12, 0xFF18261B, 0xFF283C2C, 0xFFEDF8EC, 0xFF9BB39C)
            .accent(0xFF7FE08A, 0xFF17331C, 0xFF08200C)
            .hero(0xFF1E5B2A, 0xFF08120A)
            .glass(0xCC0E1A12, 0x8C7FE08A, 0x38FFFFFF)
            .aurora(0x3D7FE08A, 0x265FE3D0, 0x1AC8E06B)
            .build();

    static final ThemePalette IVORY_LIGHT = new Builder("ivory_light", "象牙白", false,
            0xFFF7F4EC, 0xFFFFFFFF, 0xFFF1ECE0, 0xFFDFD6C1, 0xFF1B1710, 0xFF6E6450)
            .accent(0xFF9A7218, 0xFFF3E6C8, 0xFFFFFFFF)
            .hero(0xFF7A5E1C, 0xFF2C230B)
            .glass(0x99FFFFFF, 0x669A7218, 0x66FFFFFF)
            .aurora(0x269A7218, 0x1AC99A2E, 0x14884A9A)
            .danger(0xFFC0392B)
            .build();

    /** Built-in default; the first entry is also what the appearance settings list shows on top. */
    static final ThemePalette DEFAULT = OVERLAY_GREEN;
    /** The default of earlier versions, remapped once by {@link Settings#migrateThemeDefault()}. */
    static final String LEGACY_DEFAULT_ID = "black_gold";

    static final ThemePalette[] ALL = {OVERLAY_GREEN, BLACK_GOLD, OBSIDIAN_TEAL, MIDNIGHT_VIOLET,
            CRIMSON_NIGHT, JADE_GREEN, IVORY_LIGHT};

    static ThemePalette fromStored(String value) {
        if (value != null) for (ThemePalette palette : ALL) if (palette.id.equals(value)) return palette;
        return DEFAULT;
    }

    /** Picks readable text for an arbitrary accent so custom palettes never lose contrast. */
    static int onColor(int color) {
        double luminance = (0.2126 * ((color >> 16) & 255) + 0.7152 * ((color >> 8) & 255) + 0.0722 * (color & 255)) / 255.0;
        return luminance > 0.55 ? 0xFF14120C : 0xFFFFFFFF;
    }

    static int blend(int from, int to, float fraction) {
        float f = fraction < 0f ? 0f : fraction > 1f ? 1f : fraction;
        int a = Math.round(((from >>> 24) & 255) + (((to >>> 24) & 255) - ((from >>> 24) & 255)) * f);
        int r = Math.round(((from >> 16) & 255) + (((to >> 16) & 255) - ((from >> 16) & 255)) * f);
        int g = Math.round(((from >> 8) & 255) + (((to >> 8) & 255) - ((from >> 8) & 255)) * f);
        int b = Math.round((from & 255) + ((to & 255) - (from & 255)) * f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    static int alpha(int color, float fraction) {
        int a = Math.round(255 * (fraction < 0f ? 0f : fraction > 1f ? 1f : fraction));
        return (a << 24) | (color & 0x00FFFFFF);
    }
}
