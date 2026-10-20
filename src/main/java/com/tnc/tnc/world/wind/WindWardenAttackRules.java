package com.tnc.tnc.world.wind;

/**
 * Timings, reaches and budgets for 翠岚武尊 / Wind Warden.
 *
 * <p>Everything here is a pure constant or a pure function on purpose: the whole
 * attack schedule can be unit-tested without a level, an entity or a client, which
 * is how this project keeps boss timing honest.
 *
 * <h2>The two phases</h2>
 * Phase one is a swordsman: short, fast, three-hit combos and blade waves.
 * Phase two swaps the conjured sword for a spear: longer reach, slower swings,
 * and a thrown spear. The weapon itself is the shared wind arsenal model
 * ({@code tnc:wind/arsenal/sword} / {@code spear}) drawn by the renderer, so the
 * boss literally wields the same wind weapon the player's spells conjure.
 */
public final class WindWardenAttackRules {

    // ---- attack ids (also the values synced to the client) ----
    public static final int IDLE = 0;
    public static final int SLASH_LEFT = 1;
    public static final int SLASH_RIGHT = 2;
    public static final int OVERHEAD = 3;
    public static final int BLADE_WAVE = 4;
    public static final int LUNGE = 5;
    public static final int SPEAR_THRUST = 6;
    public static final int SPEAR_SWEEP = 7;
    public static final int SPEAR_THROW = 8;
    public static final int CYCLONE = 9;

    // ---- phases ----
    public static final int PHASE_SWORD = 1;
    public static final int PHASE_SPEAR = 2;
    /** Both phases change at half health, the same threshold the water leviathan uses. */
    public static final float PHASE_TWO_THRESHOLD = 0.5F;

    // ---- body ----
    /**
     * The rig is authored on the standard Minecraft humanoid template scaled to
     * 86.4 model units, and 1 unit is 1/16 block, so it already stands 5.4 blocks
     * (three players) tall with no extra scaling.
     */
    public static final float BODY_SCALE = 1.0F;
    public static final float HITBOX_WIDTH = 2.2F;
    public static final float HITBOX_HEIGHT = 5.4F;
    /** Model units are 1/16 block, so melee reach is expressed in blocks. */
    public static final double SWORD_REACH = 5.0D;
    public static final double SPEAR_REACH = 9.5D;
    public static final double CYCLONE_RADIUS = 7.5D;
    public static final double THROW_DISTANCE = 26.0D;

    // ---- durations (ticks) ----
    public static final int SLASH_LENGTH = 22;
    public static final int OVERHEAD_LENGTH = 30;
    public static final int BLADE_WAVE_LENGTH = 28;
    public static final int LUNGE_LENGTH = 26;
    public static final int THRUST_LENGTH = 26;
    public static final int SWEEP_LENGTH = 34;
    public static final int THROW_LENGTH = 30;
    public static final int CYCLONE_LENGTH = 46;
    public static final int PHASE_SHIFT_LENGTH = 48;

    /** The tick inside each move at which damage is actually dealt. */
    public static final int SLASH_HIT = 8;
    public static final int OVERHEAD_HIT = 14;
    public static final int BLADE_WAVE_FIRST = 10;
    public static final int BLADE_WAVE_INTERVAL = 5;
    public static final int BLADE_WAVE_COUNT = 3;
    public static final int LUNGE_HIT = 14;
    public static final int THRUST_HIT = 12;
    public static final int SWEEP_HIT = 15;
    public static final int THROW_RELEASE = 12;
    public static final int CYCLONE_HIT_FIRST = 12;
    public static final int CYCLONE_HIT_INTERVAL = 10;
    public static final int CYCLONE_HITS = 3;

    // ---- damage, as a multiplier of ATTACK_DAMAGE ----
    public static final float SLASH_DAMAGE = 0.55F;
    public static final float OVERHEAD_DAMAGE = 1.15F;
    public static final float WAVE_DAMAGE = 0.45F;
    public static final float LUNGE_DAMAGE = 0.9F;
    public static final float THRUST_DAMAGE = 1.25F;
    public static final float SWEEP_DAMAGE = 0.7F;
    public static final float THROW_DAMAGE = 1.0F;
    public static final float CYCLONE_DAMAGE = 0.5F;

    // ---- recovery between moves (ticks) ----
    public static final int SWORD_COOLDOWN = 14;
    public static final int SPEAR_COOLDOWN = 20;
    public static final int ENGAGE_RANGE = 40;
    public static final int BLADE_WAVE_RANGE = 26;   // beyond this the boss closes instead of shooting
    public static final int LUNGE_RANGE = 14;        // inside this it lunges rather than walks
    private WindWardenAttackRules() {}

    public static int phaseFor(float health, float maxHealth) {
        return health <= maxHealth * PHASE_TWO_THRESHOLD ? PHASE_SPEAR : PHASE_SWORD;
    }

    /** Total length of one move, used for both the state machine and the tests. */
    public static int duration(int move) {
        return switch (move) {
            case SLASH_LEFT, SLASH_RIGHT -> SLASH_LENGTH;
            case OVERHEAD -> OVERHEAD_LENGTH;
            case BLADE_WAVE -> BLADE_WAVE_LENGTH;
            case LUNGE -> LUNGE_LENGTH;
            case SPEAR_THRUST -> THRUST_LENGTH;
            case SPEAR_SWEEP -> SWEEP_LENGTH;
            case SPEAR_THROW -> THROW_LENGTH;
            case CYCLONE -> CYCLONE_LENGTH;
            default -> throw new IllegalArgumentException("Unknown wind warden move: " + move);
        };
    }

    /** Reach of the damaging window, in blocks, for the current phase and move. */
    public static double reach(int move) {
        return switch (move) {
            case SPEAR_THRUST, SPEAR_THROW -> SPEAR_REACH;
            case SPEAR_SWEEP -> SPEAR_REACH * 0.8D;
            case CYCLONE -> CYCLONE_RADIUS;
            case BLADE_WAVE -> BLADE_WAVE_RANGE;
            default -> SWORD_REACH;
        };
    }

    /** Half-angle of the melee cone, in degrees. Spear sweeps are wider than sword cuts. */
    public static double coneDegrees(int move) {
        return switch (move) {
            case SPEAR_SWEEP, CYCLONE -> 150.0D;
            case OVERHEAD -> 55.0D;
            case SPEAR_THRUST -> 30.0D;
            case BLADE_WAVE -> 45.0D;
            default -> 90.0D;
        };
    }

    /**
     * True during the ticks a move connects. Multi-hit moves (wave, cyclone) open
     * several short windows, exactly like the ink crane's sweep windows.
     */
    public static boolean connects(int move, int age) {
        return switch (move) {
            case SLASH_LEFT, SLASH_RIGHT -> age == SLASH_HIT;
            case OVERHEAD -> age == OVERHEAD_HIT;
            case LUNGE -> age == LUNGE_HIT;
            case SPEAR_THRUST -> age == THRUST_HIT;
            case SPEAR_SWEEP -> age == SWEEP_HIT;
            case BLADE_WAVE -> waveAt(age);
            case CYCLONE -> cycloneAt(age);
            case SPEAR_THROW -> age == THROW_RELEASE;
            default -> false;
        };
    }

    /** The blade-wave volley fires once every {@link #BLADE_WAVE_INTERVAL} ticks. */
    public static boolean waveAt(int age) {
        int offset = age - BLADE_WAVE_FIRST;
        return offset >= 0 && offset < BLADE_WAVE_COUNT * BLADE_WAVE_INTERVAL
                && offset % BLADE_WAVE_INTERVAL == 0;
    }

    public static boolean cycloneAt(int age) {
        int offset = age - CYCLONE_HIT_FIRST;
        return offset >= 0 && offset % CYCLONE_HIT_INTERVAL == 0
                && offset / CYCLONE_HIT_INTERVAL < CYCLONE_HITS;
    }

    /** Damage multiplier for one connecting tick. */
    public static float damageFactor(int move) {
        return switch (move) {
            case SLASH_LEFT, SLASH_RIGHT -> SLASH_DAMAGE;
            case OVERHEAD -> OVERHEAD_DAMAGE;
            case BLADE_WAVE -> WAVE_DAMAGE;
            case LUNGE -> LUNGE_DAMAGE;
            case SPEAR_THRUST -> THRUST_DAMAGE;
            case SPEAR_SWEEP -> SWEEP_DAMAGE;
            case SPEAR_THROW -> THROW_DAMAGE;
            case CYCLONE -> CYCLONE_DAMAGE;
            default -> 0.0F;
        };
    }

    public static int recovery(int phase) {
        return phase == PHASE_SPEAR ? SPEAR_COOLDOWN : SWORD_COOLDOWN;
    }

    /**
     * Pick the next move. Distance decides whether the boss closes, pokes or
     * commits to a heavy swing, so it never stands still spamming one attack.
     *
     * @param phase  current phase
     * @param cycle  monotonically increasing counter, so the pattern rotates
     * @param range  horizontal distance to the target, in blocks
     */
    public static int choose(int phase, int cycle, double range) {
        int step = Math.floorMod(cycle, 4);
        if (phase == PHASE_SWORD) {
            if (range > BLADE_WAVE_RANGE) return BLADE_WAVE;
            if (range > LUNGE_RANGE) return step % 2 == 0 ? BLADE_WAVE : LUNGE;
            return switch (step) {
                case 3 -> OVERHEAD;
                case 1 -> SLASH_RIGHT;
                default -> SLASH_LEFT;
            };
        }
        if (range > BLADE_WAVE_RANGE) return SPEAR_THROW;
        if (range > LUNGE_RANGE) return step % 2 == 0 ? SPEAR_THROW : SPEAR_THRUST;
        return switch (step) {
            case 3 -> CYCLONE;
            case 1 -> SPEAR_SWEEP;
            default -> SPEAR_THRUST;
        };
    }

    /** True when the boss should swap its weapon and play the phase-shift clip. */
    public static boolean shouldShift(int phase, float health, float maxHealth) {
        return phase == PHASE_SWORD && health <= maxHealth * PHASE_TWO_THRESHOLD && health > 0.0F;
    }
}
