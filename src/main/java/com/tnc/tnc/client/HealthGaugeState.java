package com.tnc.tnc.client;

/** Damage trail measured in active game ticks; independent of frame rate and pauses. */
public final class HealthGaugeState {
    private float health, maximum, trail;
    private int hold;
    public void tick(float value, float max) {
        float nextMax = Float.isFinite(max) && max > 0 ? max : 1;
        float next = Float.isFinite(value) ? Math.max(0, Math.min(nextMax, value)) : 0;
        if (maximum != nextMax) { maximum = nextMax; health = trail = next; hold = 0; return; }
        if (next < health) hold = 10;
        if (next > health) trail = Math.max(trail, next);
        health = next;
        if (hold > 0) hold--;
        else trail = Math.max(health, trail - maximum / 40);
    }
    public float fraction() { return maximum > 0 ? health / maximum : 0; }
    public float trailFraction() { return maximum > 0 ? trail / maximum : 0; }
}
