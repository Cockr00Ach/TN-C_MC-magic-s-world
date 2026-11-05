package com.tnc.tnc.warrior.forge;

/** Weapon quality is independent from potion quality; no lottery or material gate. */
public enum ForgeQuality {
    BOTCHED("拉夯了", 0, 0, 0, .70, 0x777777),
    COMMON("普通", 40, 20, 0, 1, 0xe2e4e7),
    FINE("精良", 60, 40, .01, 1.08, 0x76dba3),
    EPIC("史诗", 75, 60, .02, 1.15, 0xb59be7),
    LEGENDARY("传奇", 85, 75, .03, 1.25, 0xe7bd68),
    MASTER("大师", 92, 85, .04, 1.40, 0xf09565),
    PRIMORDIAL("原初·神铸", 98, 95, .05, 1.60, 0xf5e8c6);

    public final String title;
    public final double threshold, minimum, attack, durability;
    public final int color;

    ForgeQuality(String title, double threshold, double minimum, double attack, double durability, int color) {
        this.title = title; this.threshold = threshold; this.minimum = minimum;
        this.attack = attack; this.durability = durability; this.color = color;
    }

    public static double score(double h, double p, double s, double q, double a) {
        return .15 * clamp(h) + .25 * clamp(p) + .20 * clamp(s) + .30 * clamp(q) + .10 * clamp(a);
    }

    public static ForgeQuality grade(double h, double p, double s, double q, double a, boolean defect) {
        if (defect) return BOTCHED;
        double score = score(h, p, s, q, a);
        double min = Math.min(clamp(h), Math.min(clamp(p), Math.min(clamp(s), Math.min(clamp(q), clamp(a)))));
        for (int i = values().length - 1; i >= 0; i--) {
            var tier = values()[i];
            if (score + 1e-9 >= tier.threshold && min + 1e-9 >= tier.minimum) return tier;
        }
        return BOTCHED;
    }

    public static double clamp(double value) { return Double.isFinite(value) ? Math.max(0, Math.min(100, value)) : 0; }
}
