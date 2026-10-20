package com.tnc.tnc.world.wind;

/**
 * Timings, reaches and budgets for 苍岚战将 / Canglan Warlord, the wind school's
 * heavy boss.
 *
 * <p>Same contract as the rest of this project's bosses: every number that
 * decides <em>when</em> a hit lands is a pure constant or a pure function, so the
 * whole fight can be unit-tested without a level, an entity or a client.
 *
 * <h2>The body</h2>
 * The rig is authored in model units (1 unit = 1/16 block) and stands 108 units
 * = 6.75 blocks tall including the helm horns and the cloth crest, with a 60-unit
 * (3.8 block) shoulder span across the pauldron spikes. The hitbox is narrower
 * than the spikes on purpose: those are decoration, the body is what the player
 * can hit.
 *
 * <h2>The weapon is the arsenal's</h2>
 * The boss models no weapon at all. It carries an empty {@code weapon_1} anchor
 * on its right fist, and the renderer draws the same wind arsenal blade the
 * player's wind spells conjure: the sword ({@link #FORM_SWORD}) in phase one and
 * the long spear ({@link #FORM_SPEAR}) after the half-health shift. That is why
 * the two phases are called {@link #PHASE_SWORD} and {@link #PHASE_SPEAR}.
 *
 * <p>Every {@code *_LENGTH} here is exactly the matching GeckoLib clip length in
 * {@code animations/entity/canglan_warlord.animation.json} multiplied by 20, and
 * {@code CanglanWarlordModelTest} reads the JSON to keep it that way.
 */
public final class CanglanWarlordAttackRules {

    // ---- attack ids (also the values synced to the client) ----
    public static final int IDLE = 0;
    public static final int CLEAVE_LEFT = 1;
    public static final int CLEAVE_RIGHT = 2;
    public static final int OVERHEAD = 3;
    public static final int THRUST = 4;
    public static final int SWEEP = 5;
    public static final int SHIELD_BASH = 6;
    public static final int GALE_ROAR = 7;
    public static final int SPEAR_THROW = 8;

    // ---- the two arsenal forms the renderer can draw at weapon_1 ----
    public static final int FORM_SWORD = 1;
    public static final int FORM_SPEAR = 2;

    // ---- phases ----
    public static final int PHASE_SWORD = 1;
    public static final int PHASE_SPEAR = 2;
    /** Half health, the same threshold the wind warden and the leviathan use. */
    public static final float PHASE_TWO_THRESHOLD = 0.5F;
    /** Below this the boss will spend one move on a gale roar if it has not roared yet. */
    public static final float GALE_ROAR_THRESHOLD = 0.35F;

    // ---- body ----
    /** The rig is already authored at 6.75 blocks, so the renderer adds no scale. */
    public static final float BODY_SCALE = 1.0F;
    public static final float HITBOX_WIDTH = 2.8F;
    public static final float HITBOX_HEIGHT = 6.4F;

    /**
     * Melee reach in blocks, matched to the drawn blade: the sword is 2.5 blocks
     * from the fist, the spear 3.1, and the arm pushes both further out. The
     * offline rig audit measures the same blade tip against these numbers.
     */
    public static final double CLEAVE_REACH = 4.2D;
    public static final double OVERHEAD_REACH = 4.8D;
    public static final double THRUST_REACH = 5.6D;
    public static final double SWEEP_REACH = 4.8D;
    public static final double BASH_REACH = 3.2D;
    public static final double GALE_ROAR_REACH = 6.4D;
    /** The thrown spear keeps flying; the boss only bothers throwing this far. */
    public static final double SPEAR_THROW_RANGE = 26.0D;

    // ---- durations (ticks) ----
    public static final int CLEAVE_LENGTH = 20;
    public static final int OVERHEAD_LENGTH = 28;
    public static final int THRUST_LENGTH = 24;
    public static final int SWEEP_LENGTH = 34;
    public static final int SPEAR_THROW_LENGTH = 30;
    public static final int BASH_LENGTH = 22;
    public static final int GALE_ROAR_LENGTH = 44;
    public static final int PHASE_SHIFT_LENGTH = 52;
    public static final int SPAWN_LENGTH = 52;
    public static final int DEATH_LENGTH = 48;

    // ---- the tick inside each move at which damage is actually dealt ----
    public static final int CLEAVE_HIT = 10;
    public static final int OVERHEAD_HIT = 16;
    public static final int THRUST_HIT = 12;
    public static final int SWEEP_HIT_FIRST = 12;
    public static final int SWEEP_HIT_SECOND = 20;
    public static final int BASH_HIT = 10;
    public static final int SPEAR_THROW_HIT = 12;
    /** The roar peaks late: players get half the clip to back out of the shockwave. */
    public static final int GALE_ROAR_HIT = 24;

    // ---- damage, as a multiplier of ATTACK_DAMAGE ----
    public static final float CLEAVE_DAMAGE = 0.80F;
    public static final float OVERHEAD_DAMAGE = 1.30F;
    public static final float THRUST_DAMAGE = 1.15F;
    public static final float SWEEP_DAMAGE = 0.60F;
    public static final float SPEAR_THROW_DAMAGE = 1.00F;
    public static final float BASH_DAMAGE = 0.55F;
    public static final float GALE_ROAR_DAMAGE = 0.30F;
    /** The spear phase hits every move a little harder. */
    public static final float SPEAR_PHASE_DAMAGE_BONUS = 1.10F;

    // ---- knockback (blocks of push); wind shoves harder than it crushes ----
    public static final double CLEAVE_KNOCKBACK = 0.40D;
    public static final double OVERHEAD_KNOCKBACK = 0.30D;
    public static final double THRUST_KNOCKBACK = 0.60D;
    public static final double SWEEP_KNOCKBACK = 0.55D;
    public static final double SPEAR_THROW_KNOCKBACK = 0.25D;
    public static final double BASH_KNOCKBACK = 1.15D;
    public static final double GALE_ROAR_KNOCKBACK = 1.25D;

    // ---- the thrown spear, a real dodgeable projectile ----
    public static final double SPEAR_THROW_SPEED = 2.0D;
    public static final double SPEAR_THROW_SIZE = 4.4D;

    // ---- recovery between moves (ticks) ----
    public static final int SWORD_COOLDOWN = 15;
    public static final int SPEAR_COOLDOWN = 11;
    public static final int ENGAGE_RANGE = 40;
    /** Inside this the boss commits to a swing instead of walking in. */
    public static final double SWORD_ENGAGE = CLEAVE_REACH + 1.2D;
    public static final double SPEAR_ENGAGE = THRUST_REACH + 1.4D;
    /** How far the thrust lunges forward, in blocks. */
    public static final double THRUST_DASH = 4.5D;
    /** A swing never touches anything this far above or below the boss's own chest. */
    public static final double VERTICAL_REACH = 4.5D;
    /** After a gale roar the boss cannot roar again for this long. */
    public static final int GALE_ROAR_COOLDOWN = 240;
    /** Spear-phase buff the roar grants itself, in ticks. */
    public static final int GALE_ROAR_BUFF_LENGTH = 160;

    private CanglanWarlordAttackRules() {}

    public static int phaseFor(float health, float maxHealth) {
        return health <= maxHealth * PHASE_TWO_THRESHOLD ? PHASE_SPEAR : PHASE_SWORD;
    }

    /** The arsenal form drawn in the hand this phase. */
    public static int formFor(int phase) {
        return phase == PHASE_SPEAR ? FORM_SPEAR : FORM_SWORD;
    }

    /** True when the boss should swap sword for spear and play the shift clip. */
    public static boolean shouldShift(int phase, float health, float maxHealth) {
        return phase == PHASE_SWORD && health <= maxHealth * PHASE_TWO_THRESHOLD
                && health > 0.0F;
    }

    /**
     * True when the boss has been hurt enough to spend one move on a gale roar.
     *
     * @param roarCooldown ticks still left before another roar is allowed
     */
    public static boolean shouldRoar(int phase, float health, float maxHealth, int roarCooldown) {
        return phase == PHASE_SPEAR && roarCooldown <= 0
                && health <= maxHealth * GALE_ROAR_THRESHOLD && health > 0.0F;
    }

    /** Which moves the current phase owns. The buckler works in both. */
    public static boolean available(int phase, int move) {
        return switch (move) {
            case CLEAVE_LEFT, CLEAVE_RIGHT, OVERHEAD -> phase == PHASE_SWORD;
            case THRUST, SWEEP, SPEAR_THROW -> phase == PHASE_SPEAR;
            case SHIELD_BASH, GALE_ROAR -> true;
            default -> false;
        };
    }

    /** Total length of one move, used by both the state machine and the tests. */
    public static int duration(int move) {
        return switch (move) {
            case CLEAVE_LEFT, CLEAVE_RIGHT -> CLEAVE_LENGTH;
            case OVERHEAD -> OVERHEAD_LENGTH;
            case THRUST -> THRUST_LENGTH;
            case SWEEP -> SWEEP_LENGTH;
            case SPEAR_THROW -> SPEAR_THROW_LENGTH;
            case SHIELD_BASH -> BASH_LENGTH;
            case GALE_ROAR -> GALE_ROAR_LENGTH;
            default -> throw new IllegalArgumentException("Unknown warlord move: " + move);
        };
    }

    /** Clip name played for one move; also what the client sees through {@code move()}. */
    public static String animationFor(int move) {
        return switch (move) {
            case CLEAVE_LEFT -> "cleave_left";
            case CLEAVE_RIGHT -> "cleave_right";
            case OVERHEAD -> "overhead";
            case THRUST -> "thrust";
            case SWEEP -> "sweep";
            case SPEAR_THROW -> "spear_throw";
            case SHIELD_BASH -> "shield_bash";
            case GALE_ROAR -> "war_cry";
            default -> "idle";
        };
    }

    /** Reach of the damaging window, in blocks. */
    public static double reach(int move) {
        return switch (move) {
            case OVERHEAD -> OVERHEAD_REACH;
            case THRUST -> THRUST_REACH;
            case SWEEP -> SWEEP_REACH;
            case SHIELD_BASH -> BASH_REACH;
            case GALE_ROAR -> GALE_ROAR_REACH;
            case SPEAR_THROW -> 0.0D;
            default -> CLEAVE_REACH;
        };
    }

    /** Half-angle of the melee cone, in degrees. A sweep covers most of a circle. */
    public static double coneDegrees(int move) {
        return switch (move) {
            case SWEEP -> 165.0D;
            case GALE_ROAR -> 180.0D;
            case OVERHEAD -> 55.0D;
            case THRUST -> 35.0D;
            case SHIELD_BASH -> 75.0D;
            default -> 105.0D;
        };
    }

    /**
     * True during the ticks a move connects. The sweep opens two windows, so a
     * single target can be caught by both halves of the same swing.
     */
    public static boolean connects(int move, int age) {
        return switch (move) {
            case CLEAVE_LEFT, CLEAVE_RIGHT -> age == CLEAVE_HIT;
            case OVERHEAD -> age == OVERHEAD_HIT;
            case THRUST -> age == THRUST_HIT;
            case SWEEP -> age == SWEEP_HIT_FIRST || age == SWEEP_HIT_SECOND;
            case SPEAR_THROW -> age == SPEAR_THROW_HIT;
            case SHIELD_BASH -> age == BASH_HIT;
            case GALE_ROAR -> age == GALE_ROAR_HIT;
            default -> false;
        };
    }

    /** Damage multiplier for one connecting tick, before the phase bonus. */
    public static float damageFactor(int move) {
        return switch (move) {
            case CLEAVE_LEFT, CLEAVE_RIGHT -> CLEAVE_DAMAGE;
            case OVERHEAD -> OVERHEAD_DAMAGE;
            case THRUST -> THRUST_DAMAGE;
            case SWEEP -> SWEEP_DAMAGE;
            case SPEAR_THROW -> SPEAR_THROW_DAMAGE;
            case SHIELD_BASH -> BASH_DAMAGE;
            case GALE_ROAR -> GALE_ROAR_DAMAGE;
            default -> 0.0F;
        };
    }

    /** Final damage multiplier for the current phase. */
    public static float damageFactor(int move, int phase) {
        float base = damageFactor(move);
        return phase == PHASE_SPEAR ? base * SPEAR_PHASE_DAMAGE_BONUS : base;
    }

    public static double knockback(int move) {
        return switch (move) {
            case THRUST -> THRUST_KNOCKBACK;
            case SWEEP -> SWEEP_KNOCKBACK;
            case SPEAR_THROW -> SPEAR_THROW_KNOCKBACK;
            case SHIELD_BASH -> BASH_KNOCKBACK;
            case GALE_ROAR -> GALE_ROAR_KNOCKBACK;
            case OVERHEAD -> OVERHEAD_KNOCKBACK;
            default -> CLEAVE_KNOCKBACK;
        };
    }

    /** Vertical launch of a hit; the bash, the overhead and the roar pop targets up. */
    public static double launch(int move) {
        return switch (move) {
            case OVERHEAD -> 0.30D;
            case SHIELD_BASH, GALE_ROAR -> 0.45D;
            case SWEEP -> 0.22D;
            default -> 0.10D;
        };
    }

    public static int recovery(int phase) {
        return phase == PHASE_SPEAR ? SPEAR_COOLDOWN : SWORD_COOLDOWN;
    }

    /**
     * Pick the next move.
     *
     * <p>The sword phase closes and cuts; the spear phase pokes, throws and spins.
     * Distance decides what is worth committing to, so the boss never stands in
     * one spot repeating a swing it cannot land. The cycle rotates the pattern so
     * two fights in a row do not look identical.
     *
     * @param phase current phase
     * @param cycle monotonically increasing counter
     * @param range horizontal distance to the target, in blocks
     * @return a move id, or {@link #IDLE} when the boss should just walk in
     */
    public static int choose(int phase, int cycle, double range) {
        int step = Math.floorMod(cycle, 4);
        if (phase == PHASE_SPEAR) {
            if (range > SPEAR_THROW_RANGE) {
                return IDLE;
            }
            if (range > SPEAR_ENGAGE) {
                return step % 2 == 0 ? SPEAR_THROW : IDLE;
            }
            return switch (step) {
                case 1 -> SWEEP;
                case 2 -> SPEAR_THROW;
                case 3 -> SHIELD_BASH;
                default -> THRUST;
            };
        }
        if (range > SWORD_ENGAGE) {
            return step % 2 == 0 ? OVERHEAD : IDLE;
        }
        return switch (step) {
            case 1 -> CLEAVE_RIGHT;
            case 2 -> SHIELD_BASH;
            case 3 -> OVERHEAD;
            default -> CLEAVE_LEFT;
        };
    }
}
