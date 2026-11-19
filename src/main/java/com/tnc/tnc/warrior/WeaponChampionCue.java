package com.tnc.tnc.warrior;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/** Locked strike coordinates and timer shared by hit detection and ground rendering. */
public record WeaponChampionCue(WeaponChampion.Strike strike, Vec3 home, Vec3 origin, Vec3 forward,
                                long start, int ticks, int wavesLeft, double scale, double arc, WarriorAnimation animation) {
    WeaponChampionCue(WeaponChampion.Strike strike, Vec3 home, Vec3 origin, Vec3 forward, long start, int ticks, int wavesLeft) {
        this(strike, home, origin, forward, start, ticks, wavesLeft, 1, 360, null);
    }
    public static final double FOOTPRINT_HALF = 10.5;

    public boolean contains(double x, double z) {
        return Math.abs(x - home.x) <= FOOTPRINT_HALF && Math.abs(z - home.z) <= FOOTPRINT_HALF;
    }

    public boolean hits(double x, double z) {
        return templateHits(origin.x + (x - origin.x) / scale, origin.z + (z - origin.z) / scale);
    }

    public boolean templateHits(double x, double z) {
        if (strike == WeaponChampion.Strike.COMBO_FINISH) {
            var offset = new Vec3(x - origin.x, 0, z - origin.z);
            return offset.lengthSqr() <= 16 && offset.dot(forward) >= offset.length() * Math.cos(Math.toRadians(arc * .5));
        }
        return contains(x, z) && strike.hits(origin, forward, new Vec3(x, origin.y, z), wavesLeft);
    }

    public double progress(double now) {
        return Math.max(0, Math.min(1, (now - start) / ticks));
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Strike", strike.ordinal());
        putVector(tag, "Home", home);
        putVector(tag, "Origin", origin);
        putVector(tag, "Forward", forward);
        tag.putLong("Start", start);
        tag.putInt("Ticks", ticks);
        tag.putInt("Waves", wavesLeft);
        tag.putDouble("Scale", scale);
        tag.putDouble("Arc", arc);
        if (animation != null) tag.putString("Animation", animation.name());
        return tag;
    }

    static WeaponChampionCue load(CompoundTag tag) {
        if (tag.isEmpty()) return null;
        return new WeaponChampionCue(WeaponChampion.Strike.values()[tag.getInt("Strike")],
            vector(tag, "Home"), vector(tag, "Origin"), vector(tag, "Forward"),
            tag.getLong("Start"), tag.getInt("Ticks"), tag.getInt("Waves"), tag.contains("Scale") ? tag.getDouble("Scale") : 1, tag.contains("Arc") ? tag.getDouble("Arc") : 360,
            tag.contains("Animation") ? WarriorAnimation.valueOf(tag.getString("Animation")) : null);
    }

    private static void putVector(CompoundTag tag, String key, Vec3 value) {
        tag.putDouble(key + "X", value.x);
        tag.putDouble(key + "Y", value.y);
        tag.putDouble(key + "Z", value.z);
    }

    private static Vec3 vector(CompoundTag tag, String key) {
        return new Vec3(tag.getDouble(key + "X"), tag.getDouble(key + "Y"), tag.getDouble(key + "Z"));
    }
}
