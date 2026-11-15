package com.tnc.tnc.client;

/** Shared fire materials. The pale core is deliberately small in the consuming geometry. */
public final class FireFlamePalette {
    public record Palette(int fringe, int body, int glow, int core, float motion) {}
    public static final Palette FLAME = new Palette(0xc3240d, 0xff6320, 0xffb955, 0xffefc9, 1F);
    /** Ember-soul ghostfire: a dark cobalt rim, blue body and cold cyan-white core. */
    public static final Palette GHOST = new Palette(0x12265d, 0x2866d7, 0x51d4f2, 0xd5faff, .45F);
    /** Ion fire retains an orange structural shell, with electrical blue-white hot seams. */
    public static final Palette ION = new Palette(0xa72c1b, 0xf47736, 0x6bc9ff, 0xf0faff, 1.15F);

    private static final Palette WHITE_FLAME = new Palette(0xaa3820, 0xff8039, 0xffd29e, 0xfff5de, .8F);
    private static final Palette BLUE_FLAME = new Palette(0x232763, 0x407ee4, 0x9addfa, 0xe8f7ff, .68F);

    private FireFlamePalette() {}

    public static Palette forIgnition(int tier) {
        return tier >= 5 ? WHITE_FLAME : tier == 4 ? BLUE_FLAME : FLAME;
    }

    public static Palette forTier(int tier) {
        return tier == 4 ? GHOST : tier >= 5 ? ION : FLAME;
    }

    /** FORM encodes the active status tier in its intensity. */
    public static Palette forEffect(String kind, float intensity) {
        if ("FORM".equals(kind)) {
            int tier = formTier(intensity);
            return tier == 4 ? GHOST : tier == 5 ? WHITE_FLAME : FLAME;
        }
        if ("SCORCHED".equals(kind) || "FIRE_WAVE".equals(kind)) return forIgnition(formTier(intensity));
        if ("GHOST_BURST".equals(kind)) return GHOST;
        if ("SKYFIRE".equals(kind) || "SKY_IMPACT".equals(kind) || "MARTYR".equals(kind)
                || "MARTYR_DRAW".equals(kind)) return ION;
        if ("HEAT_BURST".equals(kind)) {
            float heat = Float.isFinite(intensity) ? Math.max(0, Math.min(1, intensity)) : 0;
            // Only the hottest quarter of the heat range develops a restrained ion fringe.
            float blend = Math.max(0, (heat - .75F) / .25F) * .65F;
            return new Palette(mix(FLAME.fringe, ION.fringe, blend * .35F), FLAME.body,
                    mix(FLAME.glow, ION.glow, blend), mix(FLAME.core, ION.core, blend),
                    1 + blend * .15F);
        }
        return FLAME;
    }

    public static int formTier(float intensity) {
        return Float.isFinite(intensity) ? Math.max(1, Math.min(5, Math.round(intensity * 5))) : 1;
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t);
        int g = Math.round((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t);
        int blue = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | blue;
    }
}
