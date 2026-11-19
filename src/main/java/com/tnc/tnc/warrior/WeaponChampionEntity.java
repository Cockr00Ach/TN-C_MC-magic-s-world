package com.tnc.tnc.warrior;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import com.tnc.tnc.combat.DownedCombat;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class WeaponChampionEntity extends Monster {
    static final String COMBO_ARROW = "TncChampionCombo";
    private static final EntityDataAccessor<Byte> POSE = SynchedEntityData.defineId(
        WeaponChampionEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<CompoundTag> WARNING = SynchedEntityData.defineId(
        WeaponChampionEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<CompoundTag> IMPACT = SynchedEntityData.defineId(
        WeaponChampionEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private enum Phase { APPROACH, WINDUP, RECOVER, OPEN }

    private final WeaponChampion style;
    private final ServerBossEvent bar;
    private WeaponChampionCue warning;
    private WeaponChampionCue impactCue;
    private ServerPlayer owner;
    private Vec3 home = Vec3.ZERO;
    private Vec3 origin = Vec3.ZERO;
    private Vec3 forward = new Vec3(0, 0, 1);
    private Phase phase = Phase.APPROACH;
    private WeaponChampion.Strike strike;
    private int phaseTicks = 26;
    private int waves;
    private int attacks;
    private int styleRounds;
    private int attacksSinceUltimate;
    private int openings;
    private java.util.List<WarriorSkill> weaponChains = java.util.List.of();
    private WarriorSkill chain;
    private int nextChain;
    private boolean landedThisCombo;
    private int furnaceHeat;
    private int brokenPlates;
    private int sealTicks;
    private long startedAt;

    WeaponChampionEntity(EntityType<? extends Monster> type, net.minecraft.world.level.Level level, WeaponChampion style) {
        super(type, level);
        this.style = style;
        this.bar = new ServerBossEvent(style.displayName(), style.barColor, BossEvent.BossBarOverlay.NOTCHED_10);
        setCanPickUpLoot(false);
    }

    static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 115)
            .add(Attributes.MOVEMENT_SPEED, .26).add(Attributes.KNOCKBACK_RESISTANCE, 1)
            .add(Attributes.ATTACK_DAMAGE, 8).add(Attributes.FOLLOW_RANGE, 20);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(POSE, (byte) 0);
        entityData.define(WARNING, new CompoundTag());
        entityData.define(IMPACT, new CompoundTag());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (!level().isClientSide) return;
        if (WARNING.equals(key)) warning = WeaponChampionCue.load(entityData.get(WARNING));
        if (IMPACT.equals(key)) impactCue = WeaponChampionCue.load(entityData.get(IMPACT));
    }

    public WeaponChampionCue warning() {
        return warning;
    }

    public WeaponChampionCue impactCue() {
        return impactCue;
    }

    public WeaponChampion champion() {
        return style;
    }

    public byte weaponPose() {
        return entityData.get(POSE);
    }

    void begin(ServerPlayer player, Vec3 pad, ItemStack weapon) {
        owner = player;
        home = pad;
        startedAt = level().getGameTime();
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(style.health);
        setHealth(getMaxHealth());
        setItemSlot(EquipmentSlot.MAINHAND, weapon);
        weaponChains = WarriorCombos.forRank(WarriorWeapons.family(weapon), style.rank()).stream()
            .filter(skill -> skill.slot() != 1)
            .map(skill -> WarriorPerformance.forWeapon(skill, weapon)).toList();
        setItemSlot(EquipmentSlot.CHEST, new ItemStack(style.armor));
        setDropChance(EquipmentSlot.MAINHAND, 0);
        setDropChance(EquipmentSlot.CHEST, 0);
        bar.addPlayer(player);
        if (style == WeaponChampion.SPEAR_ELITE) {
            setNoGravity(true);
            entityData.set(POSE, (byte) 4);
            status("展翼升空");
        } else status("准备接招");
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean causeFallDamage(float fall, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (!isAlive()) return;
        if (!WeaponChampionDuel.active(this, owner)) {
            discard();
            return;
        }
        if (!owner.isAlive() || DownedCombat.isDowned(owner)) {
            WeaponChampionDuel.finish(owner, this, "约战落败 · " + style.title);
            return;
        }
        if (owner.isSpectator() || distanceToSqr(owner) > 48 * 48 || TrainingGround.inside(owner)) {
            WeaponChampionDuel.finish(owner, this, "已脱离战斗，首领离去。");
            return;
        }
        if (style == WeaponChampion.SPEAR_ELITE) flyCrane();
        else home = position();
        if (phase == Phase.APPROACH) {
            face(owner.position());
            double reach = (style.branch() == WarriorWeaponRanks.Branch.BOW ? 13 : 5) * style.attackScale(style.strike(false));
            boolean close = owner.position().subtract(position()).horizontalDistance() <= reach && hasLineOfSight(owner);
            if (style != WeaponChampion.SPEAR_ELITE && !close) {
                if (getNavigation().isDone() || tickCount % 10 == 0) getNavigation().moveTo(owner, 1.15);
            } else {
                getNavigation().stop();
                maneuver();
            }
            if (--phaseTicks <= 0 && close) windup();
        } else if (phase == Phase.WINDUP) {
            if (counterDuringWindup(level)) return;
            if (--phaseTicks <= 0) impact(level);
        } else if (--phaseTicks <= 0) {
            phase = Phase.APPROACH;
            entityData.set(POSE, (byte) (style == WeaponChampion.SPEAR_ELITE ? 4 : 0));
            phaseTicks = style.approachTicks(secondHalf());
            status(style == WeaponChampion.SPEAR_ELITE ? "盘空 · 等它收翼" : "重新起势");
        }
    }

    private boolean secondHalf() {
        return getHealth() <= getMaxHealth() / 2;
    }

    private void face(Vec3 at) {
        Vec3 direction = at.subtract(position()).multiply(1, 0, 1).normalize();
        if (direction.lengthSqr() == 0) return;
        forward = direction;
        float yaw = (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
        setYRot(yaw);
        yBodyRot = yaw;
        setYHeadRot(yaw);
    }

    private void maneuver() {
        double distance = owner.position().subtract(position()).horizontalDistance();
        Vec3 step;
        Vec3 side = new Vec3(-forward.z, 0, forward.x).scale(attacks % 2 == 0 ? 1 : -1);
        if (style.branch() == WarriorWeaponRanks.Branch.BOW) {
            step = side.scale(style == WeaponChampion.BOW_MASTER ? .24 : .15);
            if (distance < 5) step = step.subtract(forward.scale(style == WeaponChampion.BOW ? .34 : .16));
        } else {
            double preferred = (style.branch() == WarriorWeaponRanks.Branch.SPEAR ? 3.3 : 2.2)
                + Math.max(0, getBbWidth() - .72f) * .5;
            step = forward.scale(distance > preferred ? .18 : 0);
            step = switch (style) {
                case SPEAR, SCYTHE_ELITE, LONGSWORD_MASTER -> step.add(side.scale(.17));
                case SPEAR_ELITE -> forward.scale(Math.min(.40, (distance - 2.1) * .12)).add(side.scale(.16));
                case SCYTHE -> forward.scale(distance < 3 ? -.15 : distance > 4 ? .18 : 0);
                case HAMMER_MASTER -> forward.scale(distance > 1.9 ? .27 : 0);
                case HAMMER_LEGEND -> step.scale(.42);
                default -> step;
            };
        }
        takeStep(step);
    }

    private void takeStep(Vec3 step) {
        if (level().getWorldBorder().isWithinBounds(getBoundingBox().move(step))) {
            move(MoverType.SELF, step);
            calculateEntityAnimation(false);
        }
    }

    private void flyCrane() {
        var below = level().clip(new ClipContext(position().add(0, .1, 0), position().add(0, -16, 0),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (below.getType() == HitResult.Type.BLOCK) home = new Vec3(getX(), below.getLocation().y, getZ());
        boolean circling = phase == Phase.APPROACH || phase == Phase.RECOVER;
        double height = circling ? 5.6 + .28 * Math.sin(tickCount * .13) : 0;
        double dy = net.minecraft.util.Mth.clamp(home.y + height - getY(), -.47, .33);
        setDeltaMovement(Vec3.ZERO);
        move(MoverType.SELF, new Vec3(0, dy, 0));
    }

    private void windup() {
        chain = null;
        if (style.ultimateReady(attacksSinceUltimate, secondHalf())) {
            strike = style.ultimate();
            attacksSinceUltimate = 0;
            playSound(SoundEvents.BEACON_ACTIVATE, 1.5f, .72f);
        } else {
            if ((attacks++ & 1) == 1 && !weaponChains.isEmpty()) {
                chain = weaponChains.get(nextChain++ % weaponChains.size());
                strike = style.strike(false);
            } else {
                strike = style.strikeForRound(styleRounds++, secondHalf());
            }
            attacksSinceUltimate++;
        }
        phase = Phase.WINDUP;
        entityData.set(POSE, (byte) (strike == WeaponChampion.Strike.SWORD_GUARD ? 2 : 1));
        waves = chain == null ? strike.waves() : chain.length() + 1;
        phaseTicks = style.windupTicks(strike);
        landedThisCombo = false;
        furnaceHeat = 0;
        sealTicks = 0;
        face(owner.position());
        if (chain != null) prepareChainBeat();
        else locateStrike();
        warn();
    }

    private void prepareChainBeat() {
        boolean bow = style.branch() == WarriorWeaponRanks.Branch.BOW;
        face(owner.position());
        if (waves == 1) {
            strike = WeaponChampion.Strike.COMBO_FINISH;
            takeStep(forward.scale(chain.backsteps() ? -Math.min(2.4, chain.backstep()) : Math.min(2.4, chain.dash())));
            phaseTicks = style.windupTicks(strike);
        } else {
            int index = chain.length() + 1 - waves;
            var move = chain.sequence().get(index);
            strike = bow ? WeaponChampion.Strike.QUICK_VOLLEY : switch (move) {
                case LEFT, RIGHT -> switch (style.branch()) {
                    case SPEAR -> WeaponChampion.Strike.THRUST;
                    case HALBERD -> WeaponChampion.Strike.RETURN_THRUST;
                    case GREATSWORD -> WeaponChampion.Strike.CLEAVE;
                    case HAMMER -> WeaponChampion.Strike.INNER_QUAKE;
                    case SCYTHE -> WeaponChampion.Strike.REAP;
                    default -> WeaponChampion.Strike.SWORD;
                };
                case HORIZONTAL -> WeaponChampion.Strike.HALBERD_SWEEP;
            };
            if (!bow && move != WarriorMove.HORIZONTAL)
                forward = forward.yRot((float)Math.toRadians(move == WarriorMove.LEFT ? -18 : 18));
            phaseTicks = Math.max(10, (index == 0 ? 18 : 15) - style.rank()) + (chain.holdAt(index) ? 5 : 0);
        }
        int pose = waves == 1 ? 9 : switch (chain.sequence().get(chain.length() + 1 - waves)) {
            case LEFT -> 6;
            case RIGHT -> 7;
            case HORIZONTAL -> 8;
        };
        entityData.set(POSE, (byte) pose);
        origin = bow ? owner.position() : new Vec3(getX(), home.y, getZ());
    }

    private void locateStrike() {
        origin = switch (strike) {
            case VOLLEY, QUICK_VOLLEY, ARROW_CURTAIN, HORN_CAGE, CINDER_PLUMES, STAR_GATE, SKY_LATTICE, METEOR_VOLLEY -> new Vec3(owner.getX(), owner.getY(), owner.getZ());
            default -> new Vec3(getX(), home.y, getZ()).add(forward.scale(style.rank() == 5 ? getBbWidth() * .36 : 0));
        };
    }

    private void aimNextWave() {
        if (strike.isUltimate()) return; // Keep all waves tied to the first mark, even if the challenger dashes.
        if (strike == WeaponChampion.Strike.FOX_TRAIL || strike == WeaponChampion.Strike.CLOUD_EDGES) {
            takeStep(new Vec3(-forward.z, 0, forward.x).scale(waves == 2 ? 1.6 : -1.6));
            face(owner.position());
        }
        switch (strike) {
            case TURNING_THRUST -> face(owner.position());
            case TWIN_CUT, SPADE_FORK, PIVOT_CROSS, ARROW_CURTAIN, CLOSING_REAP,
                 CLOUD_EDGES, MOON_DIVIDE, SILVER_GATE, IRON_GRID, BONE_ORBIT -> {
                int degrees = switch (strike) {
                    case TWIN_CUT -> -38;
                    case SPADE_FORK -> 58;
                    case PIVOT_CROSS -> 45;
                    case ARROW_CURTAIN -> waves == 2 ? 42 : -84;
                    case CLOUD_EDGES -> waves == 2 ? 35 : -70;
                    case MOON_DIVIDE -> 25;
                    case SILVER_GATE -> waves == 2 ? 22 : -44;
                    case IRON_GRID -> 18;
                    case BONE_ORBIT -> -92;
                    default -> 108;
                };
                forward = forward.yRot((float) Math.toRadians(degrees));
                float yaw = (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
                setYRot(yaw);
                yBodyRot = yaw;
                setYHeadRot(yaw);
            }
            default -> {}
        }
        // These volleys pivot about the first marked tile. Moving the marker would erase their safe lane.
        if (strike != WeaponChampion.Strike.ARROW_CURTAIN && strike != WeaponChampion.Strike.STAR_GATE
            && strike != WeaponChampion.Strike.CINDER_PLUMES) locateStrike();
    }

    private void warn() {
        boolean finisher = strike == WeaponChampion.Strike.COMBO_FINISH;
        double scale = style.attackScale(strike) * (finisher ? chain.radius() / 4 : 1);
        double arc = finisher && !chain.ranged() ? chain.arcDegrees() : 360;
        WarriorAnimation animation = null;
        if (chain != null) {
            animation = finisher ? chain.animation() : switch (chain.sequence().get(chain.length() + 1 - waves)) {
                case LEFT -> WarriorAnimation.SLASH_LEFT;
                case RIGHT -> WarriorAnimation.SLASH_RIGHT;
                case HORIZONTAL -> WarriorAnimation.SLASH_HORIZONTAL;
            };
        }
        warning = new WeaponChampionCue(strike, origin, origin, forward, level().getGameTime(), phaseTicks, waves, scale, arc, animation);
        entityData.set(WARNING, warning.save());
        playSound(SoundEvents.NOTE_BLOCK_BELL.value(), .85f, waves > 1 ? .8f : 1.25f);
        String attack = attackTitle();
        status(attack);
        String hint = strike.isUltimate() ? strike.ultimateHint : style.hint;
        if (strike.isUltimate() && waves == strike.waves()) hint = "蓄力可断起手。" + hint;
        owner.displayClientMessage(Component.literal("「" + attack + "」 " + hint), true);
    }

    private String attackTitle() {
        if (chain != null) return chain.name() + (waves == 1 ? " · 收式" : " · " + (chain.length() + 2 - waves) + "/" + chain.length());
        if (strike.isUltimate()) return "绝式 · " + strike.title + "（" + (strike.waves() - waves + 1) + "/" + strike.waves() + "）";
        if (strike == WeaponChampion.Strike.FURNACE_CUT && waves == 1) return "炉火纵劈 · " + furnaceHeat + " 层炉火";
        if (style == WeaponChampion.GREATSWORD_MASTER && waves == 1) return "力劈华山";
        if (style == WeaponChampion.SPEAR_ELITE) return "收翼落枪 · " + strike.title;
        if (strike != WeaponChampion.Strike.QUAKE) return strike.title;
        return strike.title + switch (waves) {
            case 3 -> " · 外环";
            case 2 -> " · 圈心";
            default -> " · 中环";
        };
    }

    private boolean counterDuringWindup(ServerLevel level) {
        if (style == WeaponChampion.BOW && horizontalReachSquared(owner) <= 2.5 * 2.5 && hasLineOfSight(owner)) {
            open("迫弓弃射", 46);
            return true;
        }
        if (strike == WeaponChampion.Strike.STAR_GATE && waves == 1) {
            // The same ground point stays marked while the axes change. It must be occupied continuously.
            boolean onSeal = owner.position().distanceToSqr(origin) <= 1.35 * 1.35 && hasLineOfSight(owner);
            sealTicks = onSeal ? sealTicks + 1 : 0;
            level.sendParticles(ParticleTypes.END_ROD, origin.x, origin.y + .12, origin.z, 2, .24, .06, .24, .01);
            if (sealTicks == 6) {
                open("阵眼被踏破", 84);
                return true;
            }
        }
        return false;
    }

    private void impact(ServerLevel level) {
        WeaponChampionCue hit = warning;
        clearWarning();
        impactCue = new WeaponChampionCue(hit.strike(), hit.home(), hit.origin(), hit.forward(),
            level.getGameTime(), 13, hit.wavesLeft(), hit.scale(), hit.arc(), hit.animation());
        entityData.set(IMPACT, impactCue.save());
        swing(InteractionHand.MAIN_HAND, true);
        level.sendParticles(ParticleTypes.CRIT, origin.x, origin.y + .3, origin.z, 8, .4, .18, .4, .08);
        boolean ranged = style.branch() == WarriorWeaponRanks.Branch.BOW;
        level.playSound(null, blockPosition(), ranged ? SoundEvents.ARROW_SHOOT
            : SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.3f, .75f);
        switch (strike) {
            case CLEAVE, QUAKE, INNER_QUAKE, CROSS, SPADE_FORK, ROLLING_QUAKE, PIVOT_CROSS,
                 MOON_DIVIDE, BELL_STEPS, IRON_GRID, FURNACE_CUT, SHELL_RESONANCE, STAR_GATE ->
                WarriorUltimateSound.impact(owner, origin);
            case THRUST, RETURN_THRUST, TURNING_THRUST, SILVER_GATE, DRAGON_WEAVE -> level.playSound(null, blockPosition(), SoundEvents.TRIDENT_THROW,
                SoundSource.HOSTILE, 1.1f, .85f);
            case REAP, SCYTHE_CORE, CLOSING_REAP, BONE_ORBIT, MANTIS_HOOK -> level.playSound(null, blockPosition(), SoundEvents.ENDER_DRAGON_FLAP,
                SoundSource.HOSTILE, .95f, .65f);
            default -> {}
        }
        double height = ranged ? 7 : 2.1;
        if (chain == null && style == WeaponChampion.HAMMER) height = .42; // Bear's shock travels along the ground.
        boolean inFootprint = hasLineOfSight(owner) && Math.abs(owner.getY() - hit.origin().y) < height && hit.hits(owner.getX(), owner.getZ());
        if (strike == WeaponChampion.Strike.CLOSING_REAP && waves == 1 && inFootprint) {
            var towardOwl = position().subtract(owner.position()).multiply(1, 0, 1).normalize();
            if (owner.getLookAngle().multiply(1, 0, 1).normalize().dot(towardOwl) > .80) {
                open("回望识伏 · 枭镰失手", 68);
                return;
            }
        }
        if (strike.isUltimate()) WarriorUltimateSound.impact(owner, origin);
        if (inFootprint) {
            float damage = style.heavyBlow(strike, waves) ? 16 : 8;
            if (chain != null) damage = waves == 1 ? (float)Math.min(16, 5 * chain.damageMultiplier()) : 4;
            if (strike.isUltimate()) damage = waves == 1 ? 16 : 10;
            if (strike == WeaponChampion.Strike.FURNACE_CUT && waves == 1) damage += furnaceHeat * 3;
            if (owner.hurt(damageSources().mobAttack(this), damage) && phase == Phase.WINDUP) {
                landedThisCombo = true;
                onLandedStrike(level);
            }
        }
        // The standard warrior parry can put the champion directly into OPEN during hurt().
        if (phase != Phase.WINDUP || !WeaponChampionDuel.active(this, owner)) return;
        if (chain == null && style == WeaponChampion.SPEAR) takeStep(forward.scale(strike == WeaponChampion.Strike.RETURN_THRUST ? 3.6 : 2));
        if (waves > 1) {
            waves--;
            phaseTicks = style.waveInterval(strike, waves);
            if (chain != null) prepareChainBeat();
            else aimNextWave();
            warn();
            return;
        }
        boolean missed = style == WeaponChampion.BOW_LEGEND ? !landedThisCombo : !inFootprint;
        if (strike.isUltimate() && !inFootprint) {
            open("绝式落空 · 趁势反击", 74);
            return;
        }
        if (missed && style.exposedOnMiss(strike)) {
            open("末招落空 · 追击", 66);
            return;
        }
        phase = Phase.RECOVER;
        phaseTicks = style.recoveryTicks();
        if (style == WeaponChampion.SPEAR_ELITE) {
            entityData.set(POSE, (byte) 4);
            status("振翅回空");
        } else if (style == WeaponChampion.GREATSWORD_MASTER) {
            entityData.set(POSE, (byte) 5);
            status("伏身拔刀 · 长破绽");
        } else {
            entityData.set(POSE, (byte) 0);
            status("收招 · 反击时机");
        }
    }

    private void clearWarning() {
        warning = null;
        entityData.set(WARNING, new CompoundTag());
    }

    private void onLandedStrike(ServerLevel level) {
        Vec3 away = owner.position().subtract(position()).multiply(1, 0, 1).normalize();
        switch (strike) {
            case COMBO_FINISH -> {
                if (chain.knockback() > 0) shiftOwner(away.scale(Math.min(1.2, chain.knockback())), .12);
            }
            case REAP -> {
                if (style == WeaponChampion.SCYTHE) shiftOwner(away.scale(-.85), .10);
            }
            case SPADE_FORK -> {
                if (waves == 2) shiftOwner(away.scale(.82), .35);
            }
            case BELL_STEPS -> {
                if (waves == 4 || waves == 2) shiftOwner(away.scale(.95), .18);
            }
            case SILVER_GATE -> {
                if (waves > 1) {
                    Vec3 across = new Vec3(-forward.z, 0, forward.x);
                    double lane = across.dot(owner.position().subtract(origin));
                    shiftOwner(across.scale(-Math.signum(lane) * .88), .12);
                }
            }
            case BONE_ORBIT -> {
                heal(5);
                bar.setProgress(getHealth() / getMaxHealth());
                level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1.2, getZ(), 7, .35, .3, .35, .03);
            }
            case HORN_CAGE -> {
                if (waves > 1) owner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 28, 0));
            }
            case FURNACE_CUT -> {
                if (waves > 1) {
                    furnaceHeat++;
                    level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 1.3, getZ(), 12, .5, .35, .5, .04);
                }
            }
            case DRAGON_WEAVE -> {
                if (waves > 1) shiftOwner(new Vec3(-forward.z, 0, forward.x).scale(waves == 3 ? .72 : -.72), .10);
            }
            case CINDER_PLUMES -> owner.setSecondsOnFire(3);
            default -> {}
        }
    }

    private void shiftOwner(Vec3 motion, double lift) {
        owner.push(motion.x, lift, motion.z);
        owner.hurtMarked = true;
    }

    boolean belongsTo(ServerPlayer player) {
        return player != null && owner == player && player.level() == level();
    }

    double horizontalReachSquared(ServerPlayer player) {
        if (style.rank() == 2) return distanceToSqr(player);
        var box = getBoundingBox();
        double x = net.minecraft.util.Mth.clamp(player.getX(), box.minX, box.maxX);
        double y = net.minecraft.util.Mth.clamp(player.getY(), box.minY, box.maxY);
        double z = net.minecraft.util.Mth.clamp(player.getZ(), box.minZ, box.maxZ);
        return player.distanceToSqr(x, y, z);
    }

    void onParried(ServerPlayer player) {
        if (!belongsTo(player) || phase != Phase.WINDUP) return;
        if (strike.isUltimate()) {
            if (waves == 1 && style.branch() != WarriorWeaponRanks.Branch.BOW) open("振碎绝式", 90);
            return;
        }
        if (!style.parryOpens(strike, waves)) return;
        if (style == WeaponChampion.SPEAR_ELITE) {
            // The counter catches the crane as it tries to lift off at the end of its thrust.
            move(MoverType.SELF, new Vec3(0, .95, 0));
            open("折翼坠地 · 眩晕", 100);
        } else if (style == WeaponChampion.SCYTHE_LEGEND) open("双钩交锁 · 失衡", 96);
        else open();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        WarriorSkill skill = null;
        if (source.getDirectEntity() instanceof AbstractArrow arrow && arrow.getPersistentData().contains(COMBO_ARROW)) {
            skill = WarriorCombos.bySlot(WarriorWeaponFamily.BOW, arrow.getPersistentData().getInt(COMBO_ARROW));
        }
        return hurtBySkill(source, amount, skill, false);
    }

    boolean hurtBySkill(DamageSource source, float amount, WarriorSkill skill, boolean charged) {
        if (!(source.getEntity() instanceof ServerPlayer player) || !belongsTo(player) || !isAlive()
            || !WeaponChampionDuel.active(this, player) || !Float.isFinite(amount) || amount <= 0) return false;
        Vec3 direction = player.position().subtract(position()).multiply(1, 0, 1);
        if (style == WeaponChampion.SPEAR_ELITE && getY() - home.y > 2) return false;
        boolean casting = phase == Phase.WINDUP && style.canInterrupt(strike, waves);
        double facing = direction.normalize().dot(forward);
        if (phase == Phase.WINDUP && strike == WeaponChampion.Strike.TWIN_CUT && waves == 2
                && skill == null && source.getDirectEntity() == player && facing > .45) {
            strike = WeaponChampion.Strike.SWORD_GUARD;
            waves = 1;
            phaseTicks = 18;
            entityData.set(POSE, (byte) 2);
            warn();
            status("折枝变架 · 破振刀");
            return false;
        }
        if (style == WeaponChampion.LONGSWORD && casting && skill == null && facing > .45) {
            if (phaseTicks > 12) {
                phaseTicks = 12;
                warn();
                status("架剑反斩 · 振刀");
                playSound(SoundEvents.SHIELD_BLOCK, .8f, 1.3f);
            }
            return false;
        }
        boolean recovering = phase == Phase.RECOVER;
        boolean armor = style.armored(facing) && brokenPlates < 2;
        boolean broken = style.opens(skill, casting, recovering, charged,
            style.branch() == WarriorWeaponRanks.Branch.BOW ? direction.length() : Math.sqrt(horizontalReachSquared(player)),
            facing);
        String counter = null;
        if (phase == Phase.WINDUP && warning != null) {
            counter = strike.isUltimate() && charged && waves == strike.waves() ? "蓄力打断绝式" : switch (strike) {
                case PIVOT_CROSS -> waves == 1 && charged ? "转轴被震开" : null;
                case ARROW_CURTAIN -> source.getDirectEntity() instanceof AbstractArrow
                    && !warning.hits(player.getX(), player.getZ()) ? "弓弦被截断" : null;
                case CLOUD_EDGES -> waves < 3 && Math.abs(facing) < .45 ? "侧锋失足" : null;
                case FOX_TRAIL -> facing < -.5 && waves < 3 ? "识破雪踪" : null;
                default -> null;
            };
        }
        if (style == WeaponChampion.HALBERD_MASTER && armor && phase != Phase.OPEN && charged && invulnerableTime <= 10) {
            brokenPlates++;
            playSound(SoundEvents.SHIELD_BREAK, 1.1f, 1.2f);
            if (brokenPlates == 2) counter = "两片胸甲已碎";
            else owner.displayClientMessage(Component.literal("玄铁胸甲裂开一片 · 再蓄力一次"), true);
        }
        if (counter != null) {
            open(counter, 74);
            invulnerableTime = 0;
        } else if (broken) {
            open();
            invulnerableTime = 0;
        }
        float damage = amount * (phase == Phase.OPEN ? 1.9f : phase == Phase.RECOVER ? 1.0f : .75f);
        if (phase != Phase.OPEN && armor) {
            damage = Math.min(.35f, damage);
            if (invulnerableTime <= 10) playSound(SoundEvents.SHIELD_BLOCK, .8f, .64f);
        }
        boolean hit = super.hurt(source, damage);
        bar.setProgress(getHealth() / getMaxHealth());
        return hit;
    }

    private void open() {
        open("破势 · 趁现在追击", secondHalf() ? 68 : 86);
    }

    private void open(String reason, int ticks) {
        clearWarning();
        openings++;
        phase = Phase.OPEN;
        entityData.set(POSE, (byte) 3);
        phaseTicks = ticks;
        playSound(SoundEvents.SHIELD_BREAK, 1.1f, .85f);
        WarriorImpactPacket.send(owner, position().add(0, Math.min(2.4, getBbHeight() * .52), 0), 1);
        owner.displayClientMessage(Component.literal(reason + " · 接主攻或长连！"), true);
        status(reason);
    }

    private void status(String text) {
        bar.setName(Component.literal(style.title + " · " + text));
    }

    int openings() {
        return openings;
    }

    long elapsedTicks() {
        return Math.max(1, level().getGameTime() - startedAt);
    }

    @Override
    public void die(DamageSource source) {
        bar.removeAllPlayers();
        super.die(source);
        if (owner != null) WeaponChampionDuel.victory(owner, this, style);
    }

    @Override
    public void remove(RemovalReason reason) {
        bar.removeAllPlayers();
        super.remove(reason);
    }
}
