package com.tnc.tnc.boss.fire;

/** Client playback anchored to a received elapsed age, never to another world's clock. */
public final class TortoiseDeathClock {
    private int elapsed = -1;
    private int receivedAtTick;

    public boolean started() { return this.elapsed >= 0; }

    public void receive(int elapsed, int localTick) {
        this.elapsed = Math.max(-1, Math.min(TortoiseDeath.DURATION, elapsed));
        this.receivedAtTick = localTick;
    }

    /** The vanilla death event may arrive before the next metadata packet. */
    public void startIfAbsent(int localTick) {
        if (!started()) receive(0, localTick);
    }

    public float age(int localTick, float partial) {
        if (!started()) return -1;
        return Math.max(0, Math.min(TortoiseDeath.DURATION,
                this.elapsed + Math.max(0, localTick - this.receivedAtTick) + partial));
    }
}
