package com.tnc.tnc.boss.fire.client;

/** Client presentation only; never predicts or changes the server's health value. */
final class BossBarAnimation {
    static final double TRAIL_HOLD_TICKS = 6.0;
    static final float TRAIL_FADE_PER_TICK = 1.0F / 24.0F;
    static final float FLASH_TICKS = 5.0F;
    static final float STATE_BLEND_TICKS = 10.0F;

    private float progress;
    private float trail;
    private float flash;
    private float phase;
    private float rage;
    private float weak;
    private double lastTick;
    private double holdUntil;
    private double flow;

    BossBarAnimation(float progress, boolean phaseTwo, boolean enraged, boolean weakened, double tick) {
        reset(progress, phaseTwo, enraged, weakened, tick);
    }

    Frame update(float current, boolean phaseTwo, boolean enraged, boolean weakened, double tick) {
        current = clamp(current);
        if (tick < lastTick) {
            reset(current, phaseTwo, enraged, weakened, tick);
        }
        float elapsed = (float) (tick - lastTick);
        flash = Math.max(0.0F, flash - elapsed / FLASH_TICKS);
        double fading = Math.max(0.0, tick - Math.max(lastTick, holdUntil));
        trail = Math.max(progress, trail - (float) fading * TRAIL_FADE_PER_TICK);

        if (current < progress) {
            trail = Math.max(trail, progress);
            holdUntil = tick + TRAIL_HOLD_TICKS;
            flash = 1.0F;
        } else if (current > progress) {
            // Healing must not leave an old damage trail in front of the new health.
            trail = current;
            flash = 0.0F;
            holdUntil = tick;
        }
        progress = current;
        phase = approach(phase, phaseTwo ? 1.0F : 0.0F, elapsed / STATE_BLEND_TICKS);
        rage = approach(rage, enraged ? 1.0F : 0.0F, elapsed / STATE_BLEND_TICKS);
        weak = approach(weak, weakened ? 1.0F : 0.0F, elapsed / STATE_BLEND_TICKS);
        // Integrate motion so changing combat states never jumps the texture position.
        flow += elapsed * (0.45 + phase * 0.2 + rage * 0.8) * (1.0 - weak * 0.8);
        lastTick = tick;
        return new Frame(progress, Math.max(progress, trail), flash, phase, rage, weak, flow);
    }

    private void reset(float current, boolean phaseTwo, boolean enraged, boolean weakened, double tick) {
        progress = clamp(current);
        trail = progress;
        flash = 0.0F;
        phase = phaseTwo ? 1.0F : 0.0F;
        rage = enraged ? 1.0F : 0.0F;
        weak = weakened ? 1.0F : 0.0F;
        lastTick = tick;
        holdUntil = tick;
        flow = 0.0;
    }

    private static float approach(float value, float target, float step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }

    private static float clamp(float value) {
        return Float.isFinite(value) ? Math.max(0.0F, Math.min(1.0F, value)) : 0.0F;
    }

    record Frame(float progress, float trail, float flash, float phase, float rage, float weak, double flow) {
        int lavaTint() {
            int tint = mix(0xFFFFFF, 0xFFBE85, phase);
            tint = mix(tint, 0xFF7955, rage);
            return mix(tint, 0x8F8C88, weak);
        }

        private static int mix(int from, int to, float amount) {
            int r = Math.round((from >> 16 & 255) * (1 - amount) + (to >> 16 & 255) * amount);
            int g = Math.round((from >> 8 & 255) * (1 - amount) + (to >> 8 & 255) * amount);
            int b = Math.round((from & 255) * (1 - amount) + (to & 255) * amount);
            return r << 16 | g << 8 | b;
        }
    }
}
