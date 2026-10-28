package com.tnc.tnc.boss.fire;

/** Shared, damage-free phase timeline. Times are server game ticks, never wall time. */
public final class TortoiseTransition {
    public static final int DURATION = 160;
    public static final int SILENCE_START = 40;
    public static final int FRACTURE_START = 52;
    public static final int MODEL_SWAP = 53;
    public static final int FRACTURE_END = 76;
    public static final int REBUILD_END = 124;
    public static final int IDLE_BLEND_START = 140;
    public static final int FLASH_END = MODEL_SWAP + 2;
    public static final int SHOCK_END = MODEL_SWAP + 24;
    public static final int ASH_END = FRACTURE_END + 20;
    public static final int SHAKE_END = MODEL_SWAP + 8;

    private TortoiseTransition() {}

    /** Cosmetic only: bounded per-boss emissions, and no new particles during the silent beat. */
    public static int particleBudget(float age) {
        if (age < 0 || age >= IDLE_BLEND_START || (age >= SILENCE_START && age < MODEL_SWAP)) return 0;
        return age < FRACTURE_END && age >= MODEL_SWAP ? 48 : 40;
    }

    public static float age(long start, long now, float partialTick) {
        return start < 0 ? -1.0F : Math.max(0.0F, Math.min(DURATION, now - start + partialTick));
    }

    public static boolean phaseTwo(float age) {
        return age >= MODEL_SWAP;
    }

    public static float glow(float age) {
        if (age < 0) return 1.0F;
        if (age < SILENCE_START) return lerp(0.55F, 0.15F, smooth(age / SILENCE_START));
        if (age < FRACTURE_START) return ((int) age / 2 & 1) == 0 ? 0.15F : 0.65F;
        if (age < MODEL_SWAP) return lerp(0.15F, 1.0F, age - FRACTURE_START);
        if (age < FLASH_END) return lerp(1.0F, 0.15F, (age - MODEL_SWAP) / (FLASH_END - MODEL_SWAP));
        if (age < FRACTURE_END) return lerp(0.35F, 0.75F, smooth((age - FLASH_END) / (FRACTURE_END - FLASH_END)));
        return lerp(0.75F, 1.0F, smooth((age - FRACTURE_END) / (REBUILD_END - FRACTURE_END)));
    }

    public static float ambient(float age) {
        if (age < 0 || age >= DURATION) return 1.0F;
        if (age < SILENCE_START) return lerp(1.0F, 0.08F, smooth(age / SILENCE_START));
        if (age < FRACTURE_END) return 0.0F;
        return lerp(0.12F, 1.0F, smooth((age - FRACTURE_END) / (REBUILD_END - FRACTURE_END)));
    }

    public static float flash(float age) {
        if (age < FRACTURE_START || age >= FLASH_END) return 0.0F;
        float envelope = age <= MODEL_SWAP ? age - FRACTURE_START : (FLASH_END - age) / (FLASH_END - MODEL_SWAP);
        return 0.82F * clamp(envelope);
    }

    public static float shake(float age) {
        if (age < MODEL_SWAP || age >= SHAKE_END) return 0.0F;
        return 0.35F * (SHAKE_END - age) / 8.0F;
    }

    public static float shockRadius(float age) {
        return age < MODEL_SWAP || age >= SHOCK_END ? 0.0F : 14.0F * clamp((age - MODEL_SWAP) / (SHOCK_END - MODEL_SWAP));
    }

    public static float scorchRadius(float age) {
        return age < REBUILD_END || age >= DURATION ? 0.0F : 12.0F * clamp((age - REBUILD_END) / (DURATION - REBUILD_END - 4.0F));
    }

    public static float smooth(float value) {
        float t = clamp(value);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static float lerp(float from, float to, float value) {
        return from + (to - from) * clamp(value);
    }
}
