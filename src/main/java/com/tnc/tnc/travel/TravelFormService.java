package com.tnc.tnc.travel;

import com.tnc.tnc.Config;
import com.tnc.tnc.bosshud.ServerBossHud;
import com.tnc.tnc.magic.Element;
import com.tnc.tnc.magic.MagicStone;
import com.tnc.tnc.magic.MagicStoneData;
import com.tnc.tnc.magic.SpellCatalog;
import com.tnc.tnc.magic.compat.CombatCasting;
import com.tnc.tnc.network.MagicStoneNetwork;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 元素附身的服务端状态机。
 *
 * <p>客户端只发 {@code ENTER/SKILL/KEEPALIVE/EXIT}；这里负责：
 * 主手是不是元素法杖、阶位/亲和/学习门槛、魔力、冷却、战斗锁定、区域拦截、
 * 落点安全、飞行速度与退出恢复。
 *
 * <p><b>不直接和风/光抢 {@code mayfly}</b>：附身只把 {@code mayfly} 抬起来，
 * 最终裁决仍然在 {@code TNWindMechanics.onPlayerTick}；退出时恢复进入前保存的值，
 * 不硬清风光。
 */
public final class TravelFormService {

    private static final Map<UUID, FormState> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> LAST_COMBAT = new HashMap<>();

    /** 客户端 2 秒没有 KeepAlive 就认为掉线/卡死，自动退出。 */
    private static final int KEEPALIVE_TIMEOUT_TICKS = 40;

    private TravelFormService() {
    }

    public static boolean active(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    /** 只有空中形态才需要飞行；土/光不自动起飞。 */
    public static boolean allowsFlight(ServerPlayer player) {
        FormState form = ACTIVE.get(player.getUUID());
        return form != null && TravelForms.airForm(form.element);
    }

    public static void input(ServerPlayer player, int action) {
        switch (action) {
            case TravelInputPacket.ENTER -> enter(player);
            case TravelInputPacket.SKILL -> skill(player);
            case TravelInputPacket.KEEPALIVE -> keepAlive(player);
            case TravelInputPacket.EXIT -> exit(player, true);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------
    //  进入 / 退出
    // ------------------------------------------------------------------

    public static void enter(ServerPlayer player) {
        if (!Config.travelEnabled || active(player)) {
            return;
        }
        TravelStaff staff = TravelStaff.of(player.getMainHandItem());
        if (staff == null) {
            message(player, "需要主手拿元素法杖");
            return;
        }
        Element element = staff.travelElement();
        if (!TravelForms.supported(element)) {
            message(player, TravelForms.elementName(element) + "系附身尚未开放");
            return;
        }
        MagicStoneData data = MagicStone.getOrNull(player);
        if (data == null) {
            message(player, "战士无法附身（需要魔法石）");
            return;
        }
        int tier = player.isCreative()
                ? Math.min(staff.travelTier(), TravelForms.MAX_TIER)
                : TravelForms.effectiveTier(staff.travelTier(), data.getAffinity(element));
        if (tier <= 0) {
            message(player, "没有" + TravelForms.elementName(element) + "系亲和，无法附身");
            return;
        }
        if (!player.isCreative() && Config.travelRequireStudiedSpell && !hasStudied(data, element)) {
            message(player, "先学会一个" + TravelForms.elementName(element) + "系法术");
            return;
        }
        // 环境特征：下界禁附身、火遇水/雨进不去、幽行只能夜里或地下。
        boolean inWater = player.isInWater();
        boolean wet = player.isInWaterOrRain();
        boolean night = player.level().isNight();
        boolean underground = !player.level().canSeeSky(player.blockPosition());
        boolean nether = player.level().dimension() == net.minecraft.world.level.Level.NETHER;
        String blocked = TravelForms.entryBlocked(element, inWater, wet, night, underground, nether);
        if (blocked != null) {
            message(player, blocked);
            return;
        }
        Component denied = TravelBlockers.enterDenied(player);
        if (denied != null) {
            message(player, denied.getString());
            return;
        }
        long now = gameTime(player);
        long lastCombat = LAST_COMBAT.getOrDefault(player.getUUID(), Long.MIN_VALUE);
        if (lastCombat != Long.MIN_VALUE && now - lastCombat < Config.travelCombatLockTicks) {
            long seconds = Math.max(1L, (Config.travelCombatLockTicks - (now - lastCombat) + 19L) / 20L);
            message(player, "战斗中无法附身（" + seconds + " 秒后可再试）");
            return;
        }
        int cooldown = player.isCreative() ? 0 : cooldownRemaining(player);
        if (cooldown > 0) {
            message(player, "附身冷却中：" + seconds(cooldown) + " 秒");
            return;
        }
        int entryCost = TravelForms.entryMana(data.getMaxMana());
        if (!player.isCreative() && (data.getMana() < entryCost || !data.spendMana(entryCost))) {
            message(player, "魔力不足：需要 " + entryCost + "，当前 " + data.getMana());
            return;
        }

        FormState form = new FormState();
        form.element = element;
        form.tier = tier;
        form.totalTicks = TravelForms.durationTicks(element, tier);
        form.ticksLeft = form.totalTicks;
        form.cooldownOnExit = TravelForms.cooldownTicks(tier);
        form.entry = player.position();
        form.lastInputTick = player.tickCount;
        form.savedMayfly = player.getAbilities().mayfly;
        form.savedFlying = player.getAbilities().flying;
        form.savedFlyingSpeed = player.getAbilities().getFlyingSpeed();

        ACTIVE.put(player.getUUID(), form);
        TravelNetwork.sendAppearance(player, true);
        CombatCasting.cancel(player);
        player.stopUsingItem();
        player.fallDistance = 0.0F;
        applyFlight(player, form);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        if (element == Element.DARK) {
            TravelBlockers.clearMobTargets(player);
        }
        MagicStoneNetwork.syncTo(player);
        TravelNetwork.sendState(player);
        message(player, TravelForms.elementName(element) + "附身 · " + TravelForms.formName(element)
                + "（松手退出）");
        sound(player, element, true);
    }

    public static void exit(ServerPlayer player, boolean cooldown) {
        FormState form = ACTIVE.remove(player.getUUID());
        if (form == null) {
            return;
        }
        TravelNetwork.sendAppearance(player, false);
        restoreFlight(player, form);
        if (cooldown && !player.isCreative()) {
            MagicStoneData data = MagicStone.getOrNull(player);
            if (data != null) {
                data.setTravelCooldownUntil(gameTime(player) + form.cooldownOnExit);
                MagicStone.markCast(player);
                MagicStoneNetwork.syncTo(player);
            }
        }
        if (TravelForms.airForm(form.element) && !player.onGround()) {
            player.fallDistance = 0.0F;
            if (Config.travelSlowFallingTicks > 0) {
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,
                        Config.travelSlowFallingTicks, 0, false, false, true));
            }
        }
        TravelNetwork.sendState(player);
        message(player, "附身结束");
        sound(player, form.element, false);
    }

    // ------------------------------------------------------------------
    //  形态技
    // ------------------------------------------------------------------

    public static void skill(ServerPlayer player) {
        FormState form = ACTIVE.get(player.getUUID());
        if (form == null) {
            return;
        }
        if (form.skillCooldown > 0) {
            message(player, "形态技冷却中：" + seconds(form.skillCooldown) + " 秒");
            return;
        }
        MagicStoneData data = MagicStone.getOrNull(player);
        if (data == null) {
            return;
        }
        int cost = TravelForms.skillMana(form.element, data.getMaxMana());
        if (form.element == Element.LIGHTNING) {
            if (!paySkill(player, data, cost)) {
                return;
            }
            boostSkill(player, form);
            particles(player, form.element);
            message(player, "雷冲");
            sync(player);
            return;
        }
        if (form.element == Element.DARK) {
            if (form.returnUsed) {
                message(player, "影返本次附身已经用过");
                return;
            }
            if (!TravelSafety.safe(player, form.entry)) {
                message(player, "影返落点不再安全");
                return;
            }
            if (!paySkill(player, data, cost)) {
                return;
            }
            form.returnUsed = true;
            TravelSafety.teleport(player, form.entry);
            message(player, "影返");
            exit(player, true);
            return;
        }
        if (form.element == Element.FIRE) {
            if (!paySkill(player, data, cost)) {
                return;
            }
            boostSkill(player, form);
            particles(player, form.element);
            message(player, "爆燃");
            sync(player);
            return;
        }
        if (form.element == Element.WATER) {
            if (!paySkill(player, data, cost)) {
                return;
            }
            boostSkill(player, form);
            player.clearFire();       // 顺流：水形态自带灭火
            particles(player, form.element);
            message(player, "顺流");
            sync(player);
            return;
        }
        if (form.element == Element.WIND) {
            if (!paySkill(player, data, cost)) {
                return;
            }
            boostSkill(player, form);
            player.push(0.0D, 0.85D, 0.0D);   // 乘风：向上抬一段，再靠加速段往前冲
            player.hurtMarked = true;
            particles(player, form.element);
            message(player, "乘风");
            sync(player);
            return;
        }
        if (form.element == Element.EARTH) {
            var target = TravelSafety.burrowTarget(player, TravelForms.earthDistance(form.tier));
            if (target.isEmpty()) {
                message(player, "前方没有能钻出的地表");
                return;
            }
            if (!paySkill(player, data, cost)) {
                return;
            }
            form.skillCooldown = TravelForms.skillCooldownTicks(form.element);
            TravelSafety.teleport(player, target.get());
            particles(player, form.element);
            message(player, "潜地");
            sync(player);
            return;
        }
        if (form.element == Element.LIGHT) {
            var target = TravelSafety.blinkTarget(player, TravelForms.lightDistance(form.tier));
            if (target.isEmpty()) {
                message(player, "前方没有能落脚的位置");
                return;
            }
            if (!paySkill(player, data, cost)) {
                return;
            }
            form.skillCooldown = TravelForms.skillCooldownTicks(form.element);
            TravelSafety.teleport(player, target.get());
            particles(player, form.element);
            message(player, "光跃");
            sync(player);
        }
    }

    /** 扣形态技消耗；创造模式直接放行。 */
    private static boolean paySkill(ServerPlayer player, MagicStoneData data, int cost) {
        if (player.isCreative()) {
            return true;
        }
        if (!data.spendMana(cost)) {
            message(player, "魔力不足：需要 " + cost);
            return false;
        }
        return true;
    }

    /** 开加速段 + 写冷却，并立刻把新的 flyingSpeed 推给客户端。 */
    private static void boostSkill(ServerPlayer player, FormState form) {
        form.boostTicks = TravelForms.skillBoostTicks(form.element);
        form.boostMultiplier = TravelForms.skillSpeedMultiplier(form.element);
        form.skillCooldown = TravelForms.skillCooldownTicks(form.element);
        applyFlight(player, form);
        player.onUpdateAbilities();
    }

    private static void sync(ServerPlayer player) {
        MagicStoneNetwork.syncTo(player);
        TravelNetwork.sendState(player);
    }

    public static void keepAlive(ServerPlayer player) {
        FormState form = ACTIVE.get(player.getUUID());
        if (form != null) {
            form.lastInputTick = player.tickCount;
        }
    }

    // ------------------------------------------------------------------
    //  每 tick
    // ------------------------------------------------------------------

    public static void tick(ServerPlayer player) {
        FormState form = ACTIVE.get(player.getUUID());
        if (form == null) {
            return;
        }
        TravelStaff staff = TravelStaff.of(player.getMainHandItem());
        if (staff == null || staff.travelElement() != form.element) {
            exit(player, true);
            return;
        }
        if (player.isDeadOrDying() || player.isRemoved() || player.isPassenger()
                || player.isSleeping() || player.containerMenu != player.inventoryMenu) {
            exit(player, true);
            return;
        }
        if (player.tickCount - form.lastInputTick > KEEPALIVE_TIMEOUT_TICKS) {
            exit(player, true);
            return;
        }
        if (form.ticksLeft-- <= 0) {
            message(player, "附身时长结束");
            exit(player, true);
            return;
        }
        if (player.tickCount % 10 == 0) {
            Component denied = TravelBlockers.enterDenied(player);
            if (denied != null) {
                message(player, denied.getString());
                exit(player, true);
                return;
            }
        }

        MagicStoneData data = MagicStone.getOrNull(player);
        if (data == null) {
            exit(player, true);
            return;
        }
        double drain = player.isCreative() ? 0.0D : data.getMaxMana()
                * TravelForms.drainPercentPerSecond(form.element, form.tier) / 100.0D / 20.0D;
        form.drainCarry += Math.max(0.0D, drain);
        int whole = (int) form.drainCarry;
        if (whole > 0) {
            if (!player.isCreative() && !data.spendMana(whole)) {
                message(player, "魔力耗尽，附身结束");
                MagicStoneNetwork.syncTo(player);
                exit(player, true);
                return;
            }
            form.drainCarry -= whole;
        }

        if (form.skillCooldown > 0) {
            form.skillCooldown--;
        }
        if (form.boostTicks > 0) {
            form.boostTicks--;
        }
        applyFlight(player, form);
        if (player.tickCount % 2 == 0) {
            trail(player, form.element);
        }
        // 风球牵引：把附近的怪往玩家身上吸（Boss 不吸，避免把 Boss 拉进安全区）。
        if (form.element == Element.WIND && player.tickCount % 4 == 0) {
            pullMobs(player);
        }
        // 环境特征：火遇水即灭；风泡在水里会逐渐被打散。
        if (form.element == Element.FIRE && (player.isInWater() || player.isInWaterOrRain())) {
            message(player, "火被水浇灭，附身结束");
            exit(player, true);
            return;
        }
        if (form.element == Element.WIND) {
            if (player.isInWater()) {
                if (++form.waterTicks > TravelForms.WIND_WATER_DISSOLVE_TICKS) {
                    message(player, "风被水打散，附身结束");
                    exit(player, true);
                    return;
                }
            } else if (form.waterTicks > 0) {
                form.waterTicks--;
            }
        }
        // 穿墙：幽行（幽灵）和御风（风能钻缝）都能穿，雷/火/水/土/光不能。
        if ((form.element == Element.DARK || form.element == Element.WIND)
                && player.horizontalCollision
                && Math.abs(player.getLookAngle().y) < 0.8D
                && player.tickCount - form.lastPhase >= 12) {
            var target = TravelSafety.phaseTarget(player, TravelForms.phaseBlocks(form.tier));
            if (target.isPresent()) {
                int cost = Math.max(1, (int) Math.ceil(data.getMaxMana() * 0.02D));
                if (player.isCreative() || data.spendMana(cost)) {
                    TravelSafety.teleport(player, target.get());
                    form.lastPhase = player.tickCount;
                    particles(player, form.element);
                    MagicStoneNetwork.syncTo(player);
                }
            }
        }

        if (player.tickCount % 10 == 0) {
            player.onUpdateAbilities();
            TravelNetwork.sendState(player);
            MagicStoneNetwork.syncTo(player);
        }
    }

    // ------------------------------------------------------------------
    //  战斗锁定 / 目标抑制
    // ------------------------------------------------------------------

    public static void onHurt(LivingEntity victim, net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (amount <= 0.0F) {
            return;
        }
        long now = victim.level().getGameTime();
        if (victim instanceof ServerPlayer player) {
            if (source.getEntity() != null || source.getDirectEntity() != null) {
                LAST_COMBAT.put(player.getUUID(), now);
            }
            if (active(player)) {
                message(player, "受伤，附身中断");
                exit(player, true);
            }
        }
        if (source.getEntity() instanceof ServerPlayer attacker) {
            LAST_COMBAT.put(attacker.getUUID(), now);
        }
    }

    public static void noteCombat(ServerPlayer player) {
        LAST_COMBAT.put(player.getUUID(), gameTime(player));
    }

    /** 暗形态期间，怪物不能把玩家设为目标。 */
    public static boolean suppressesTarget(ServerPlayer player) {
        FormState form = ACTIVE.get(player.getUUID());
        return form != null && form.element == Element.DARK;
    }

    public static TravelAppearancePacket appearancePacket(ServerPlayer player, boolean active) {
        FormState form = ACTIVE.get(player.getUUID());
        if (!active || form == null) {
            return TravelAppearancePacket.inactive(player.getId());
        }
        return new TravelAppearancePacket(player.getId(), form.element.ordinal(), form.tier, true);
    }

    public static TravelStatePacket statePacket(ServerPlayer player) {
        FormState form = ACTIVE.get(player.getUUID());
        if (form == null) {
            return TravelStatePacket.inactive(cooldownRemaining(player));
        }
        return new TravelStatePacket(form.element.ordinal(), form.tier, form.ticksLeft,
                form.totalTicks, 0, form.skillCooldown);
    }

    public static int cooldownRemaining(ServerPlayer player) {
        if (player.isCreative()) {
            return 0;
        }
        MagicStoneData data = MagicStone.getOrNull(player);
        if (data == null) {
            return 0;
        }
        long remaining = data.getTravelCooldownUntil() - gameTime(player);
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, remaining));
    }

    public static void clear(UUID id) {
        ACTIVE.remove(id);
        LAST_COMBAT.remove(id);
    }

    /**
     * 登出时收尾：和 {@link #exit} 一样恢复能力/写冷却，但不给已经断线的玩家发包。
     * 不写冷却的话，玩家可以在附身中直接退游戏，重登后冷却为 0。
     */
    public static void logout(ServerPlayer player) {
        FormState form = ACTIVE.remove(player.getUUID());
        if (form != null) {
            restoreFlight(player, form);
            MagicStoneData data = MagicStone.getOrNull(player);
            if (data != null && !player.isCreative()) {
                data.setTravelCooldownUntil(gameTime(player) + form.cooldownOnExit);
                MagicStone.markCast(player);
                // 连接可能已经关闭，不发包；冷却随玩家存档保存。
            }
        }
        clear(player.getUUID());
    }

    // ------------------------------------------------------------------
    //  内部
    // ------------------------------------------------------------------

    private static boolean hasStudied(MagicStoneData data, Element element) {
        for (ResourceLocation id : data.getLearned()) {
            SpellCatalog.Entry entry = SpellCatalog.byId(id);
            if (entry != null && !entry.independent() && entry.element() == element) {
                return true;
            }
        }
        return false;
    }

    private static void applyFlight(ServerPlayer player, FormState form) {
        double speed = TravelForms.speed(form.element, form.tier);
        speed *= TravelForms.environmentSpeed(form.element, player.isInWater(), player.isInWaterOrRain());
        if (form.boostTicks > 0) {
            speed *= form.boostMultiplier;
        }
        player.getAbilities().mayfly = true;
        player.getAbilities().setFlyingSpeed(TravelForms.flyingSpeed(speed));
    }

    private static void restoreFlight(ServerPlayer player, FormState form) {
        player.getAbilities().mayfly = form.savedMayfly;
        player.getAbilities().flying = form.savedFlying;
        player.getAbilities().setFlyingSpeed(form.savedFlyingSpeed);
        player.onUpdateAbilities();
    }

    private static void particles(ServerPlayer player, Element element) {
        var level = player.serverLevel();
        double x = player.getX();
        double y = player.getY() + 1.0D;
        double z = player.getZ();
        switch (element) {
            case LIGHTNING -> level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    x, y, z, 18, 0.35D, 0.5D, 0.35D, 0.02D);
            case FIRE -> {
                level.sendParticles(ParticleTypes.FLAME, x, y, z, 14, 0.35D, 0.45D, 0.35D, 0.05D);
                level.sendParticles(ParticleTypes.LAVA, x, y, z, 4, 0.30D, 0.35D, 0.30D, 0.0D);
            }
            case WATER -> level.sendParticles(ParticleTypes.SPLASH,
                    x, y, z, 20, 0.40D, 0.40D, 0.40D, 0.12D);
            case WIND -> level.sendParticles(ParticleTypes.CLOUD,
                    x, y, z, 16, 0.45D, 0.45D, 0.45D, 0.06D);
            case EARTH -> {
                level.sendParticles(ParticleTypes.CRIT, x, y - 0.6D, z, 18, 0.5D, 0.25D, 0.5D, 0.10D);
                level.sendParticles(ParticleTypes.SMOKE, x, y - 0.8D, z, 8, 0.45D, 0.20D, 0.45D, 0.02D);
            }
            case LIGHT -> level.sendParticles(ParticleTypes.END_ROD,
                    x, y, z, 14, 0.35D, 0.45D, 0.35D, 0.02D);
            case DARK -> {
                level.sendParticles(ParticleTypes.SOUL, x, y, z, 14, 0.35D, 0.45D, 0.35D, 0.02D);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 6, 0.4D, 0.4D, 0.4D, 0.01D);
            }
        }
    }

    /**
     * 持续拖尾：七系各自的粒子。不用客户端粒子 ⇒ 附近所有玩家看到的拖尾一致，
     * 也让"火球在烧 / 激流在溅 / 风球在卷"这三件事在别人屏幕上也成立。
     */
    private static void trail(ServerPlayer player, Element element) {
        var level = player.serverLevel();
        double x = player.getX();
        double y = player.getY() + 1.0D;
        double z = player.getZ();
        switch (element) {
            case LIGHTNING -> level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    x, y, z, 3, 0.22D, 0.30D, 0.22D, 0.02D);
            case FIRE -> {
                level.sendParticles(ParticleTypes.FLAME, x, y, z, 3, 0.22D, 0.32D, 0.22D, 0.03D);
                level.sendParticles(ParticleTypes.SMOKE, x, y + 0.3D, z, 1, 0.18D, 0.22D, 0.18D, 0.01D);
            }
            case WATER -> {
                level.sendParticles(ParticleTypes.SPLASH, x, y, z, 2, 0.25D, 0.30D, 0.25D, 0.06D);
                level.sendParticles(ParticleTypes.BUBBLE, x, y, z, 1, 0.20D, 0.25D, 0.20D, 0.02D);
            }
            case WIND -> {
                level.sendParticles(ParticleTypes.CLOUD, x, y, z, 2, 0.30D, 0.32D, 0.30D, 0.03D);
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, x, y, z, 1, 0.30D, 0.25D, 0.30D, 0.0D);
            }
            case EARTH -> {
                level.sendParticles(ParticleTypes.CRIT, x, y - 0.5D, z, 2, 0.30D, 0.25D, 0.30D, 0.05D);
                level.sendParticles(ParticleTypes.SMOKE, x, y - 0.7D, z, 1, 0.25D, 0.20D, 0.25D, 0.01D);
            }
            case LIGHT -> {
                level.sendParticles(ParticleTypes.END_ROD, x, y, z, 2, 0.30D, 0.35D, 0.30D, 0.02D);
                level.sendParticles(ParticleTypes.FIREWORK, x, y, z, 1, 0.30D, 0.30D, 0.30D, 0.02D);
            }
            case DARK -> {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 2, 0.28D, 0.35D, 0.28D, 0.01D);
                level.sendParticles(ParticleTypes.SQUID_INK, x, y, z, 1, 0.25D, 0.30D, 0.25D, 0.01D);
            }
        }
    }

    /**
     * 风球牵引：6 格内的怪被吸向玩家。
     *
     * <p>不吸 Boss（{@link ServerBossHud#theme} 认得的就是 Boss）：把 Boss 拖进安全区
     * 或者拖出它自己的场地，都是玩家会记一辈子的 Bug ✗。
     */
    private static void pullMobs(ServerPlayer player) {
        var box = player.getBoundingBox().inflate(6.0D);
        for (Monster mob : player.serverLevel().getEntitiesOfClass(Monster.class, box)) {
            if (!mob.isAlive() || mob.isPassenger() || ServerBossHud.theme(mob) != null) {
                continue;
            }
            Vec3 pull = player.position().subtract(mob.position());
            if (pull.lengthSqr() < 1.0E-4D) {
                continue;
            }
            pull = pull.normalize().scale(0.16D);
            mob.setDeltaMovement(mob.getDeltaMovement().add(pull.x, 0.06D, pull.z));
            mob.hurtMarked = true;
            mob.fallDistance = 0.0F;
        }
    }

    private static void message(ServerPlayer player, String text) {
        player.displayClientMessage(Component.literal(text), true);
    }

    /**
     * 进入/退出的音效：全部用原版 {@link SoundEvents}，七系各一个，
     * 不新增 ogg（新音频资源一旦没好，静音比有声音更像 Bug ✗）。
     */
    private static void sound(ServerPlayer player, Element element, boolean entering) {
        SoundEvent event = switch (element) {
            case LIGHTNING -> entering
                    ? SoundEvents.LIGHTNING_BOLT_IMPACT : SoundEvents.LIGHTNING_BOLT_THUNDER;
            case DARK -> SoundEvents.SOUL_ESCAPE;
            case FIRE -> entering ? SoundEvents.FIRECHARGE_USE : SoundEvents.FIRE_EXTINGUISH;
            case WATER -> SoundEvents.GENERIC_SPLASH;
            case WIND -> entering ? SoundEvents.PHANTOM_FLAP : SoundEvents.ELYTRA_FLYING;
            case EARTH -> entering ? SoundEvents.STONE_BREAK : SoundEvents.GRAVEL_BREAK;
            case LIGHT -> entering
                    ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.AMETHYST_BLOCK_RESONATE;
        };
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                event, SoundSource.PLAYERS, entering ? 0.75F : 0.55F, entering ? 1.0F : 0.85F);
    }

    private static int seconds(int ticks) {
        return Math.max(0, (ticks + 19) / 20);
    }

    private static long gameTime(ServerPlayer player) {
        return player.server.overworld().getGameTime();
    }

    private static final class FormState {
        Element element;
        int tier;
        int ticksLeft;
        int totalTicks;
        int cooldownOnExit;
        Vec3 entry;
        boolean savedMayfly;
        boolean savedFlying;
        float savedFlyingSpeed;
        double drainCarry;
        int skillCooldown;
        int boostTicks;
        double boostMultiplier = 1.0D;
        /** 风在水里已经泡了多少 tick（到 {@code WIND_WATER_DISSOLVE_TICKS} 强制退出）。 */
        int waterTicks;
        boolean returnUsed;
        int lastPhase;
        int lastInputTick;
    }
}
