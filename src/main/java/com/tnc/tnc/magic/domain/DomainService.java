package com.tnc.tnc.magic.domain;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.combat.DownedCombat;
import com.tnc.tnc.magic.Element;
import com.tnc.tnc.magic.TNEffects;
import com.tnc.tnc.magic.TNMagicCircleEntity;
import com.tnc.tnc.magic.TNOrbEntities;
import com.tnc.tnc.magic.TNNightGripEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 领域法术的<b>服务端本体</b>（作者 2026-11-08；第二轮补齐七系）。
 *
 * <h2>七系各一句（作者原话 + 备注）</h2>
 * <pre>
 *   雷  展开雷领域 → 加雷伤 → 战士附雷伤、刀上带小闪电
 *       周围时不时遭雷劈（间隔很久），主要劈「玩家和队友正在打的目标」，劈之前地面有明显落点
 *   暗  展开暗领域 → 加暗伤 → 战士附暗伤、刀光剑影变黑
 *       目标脚下起小团黑雾 → 伸出手抓住 → 一直控制，间隔也久
 *   火  加火伤 → 战士附火伤、刀光转橙红；目标脚下预警后砸下火柱，点燃
 *   风  加风伤 → 战士附风伤、刀光转青绿；目标被风刃割开并推出去
 *   水  加水伤 → 战士附水伤、刀光转青蓝；目标脚下涌起水浪，泡住减速
 *   土  加土伤 → 战士附土伤、刀光转土黄；目标脚下岩刺顶起，定在原地
 *   光  加光伤 → 战士附光伤、刀光转白金；圣光灼烧敌人，同时给队友回血
 * </pre>
 *
 * <h2>怎么落到代码</h2>
 * <ol>
 *   <li><b>状态</b>：{@link TNEffects#domainEffect(Element)} —— 效果自动同步、自带倒计时 ✓。</li>
 *   <li><b>谁是"正在打的目标"</b>：{@link #HIT} —— 任何玩家（= 队友）直接打到谁就记谁 + 记时间；
 *       领域每隔 {@link DomainSpells.Domain#interval()} 从这份名单里挑<b>最近挨打</b>的那个 ✓。</li>
 *   <li><b>预警</b>：先在落点铺一圈 {@link TNMagicCircleEntity}（样式取
 *       {@link DomainSpells.Domain#style()}），过 {@link DomainSpells.Domain#telegraph()} tick 才真的出手
 *       ⇒ 作者要的「劈之前地面有明显落点」✓。</li>
 *   <li><b>出手</b>：{@link #resolve} 按 {@link DomainSpells.Domain#kind()} 分派到七个方法，
 *       伤害/半径都取自数值表 ✓。</li>
 *   <li><b>战士附伤</b>：{@link #onHurt} —— 领域开着时，玩家用刀/斧直接砍中的那一下
 *       额外加 {@link DomainSpells#MELEE_BONUS} 的元素伤害（走 {@code indirectMagic}，
 *       伤害归属还是这个玩家 ✓）。</li>
 * </ol>
 *
 * <p>⚠️ 未实机验收：时序、伤害归属、名单裁剪这些静态能核的都核了 ✓；
 * 七个领域的手感（雷 6 秒 / 暗 8 秒 / 风 5 秒…、1.1 秒预警、暗 3 秒控制）请作者进游戏看，
 * 参数全在 {@link DomainSpells} 一张表里 ✓。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class DomainService {

    /** 目标 → 最后一次被玩家直接打到的 tick（"玩家和队友攻击的目标"就靠它 ✓）。 */
    private static final Map<UUID, Long> HIT = new HashMap<>();

    /** 玩家 → 已经预警、还没落地的那些打击（一次可以同时点好几个落点 ✓）。 */
    private static final Map<UUID, List<Pending>> PENDING = new HashMap<>();

    /** 玩家 → 下一次可以出手的 tick ✓。 */
    private static final Map<UUID, Long> NEXT = new HashMap<>();

    /** 一次预警中的打击。 */
    private record Pending(ServerLevel level, Vec3 at, int targetId, long due,
                           DomainSpells.Domain domain) {
    }

    private DomainService() {
    }

    // ------------------------------------------------------------------
    //  施放：把领域展开
    // ------------------------------------------------------------------

    /**
     * 施法瞬间（{@code TnSpellMechanics.onSpellCast} 调）—— 不是领域法术就返回 false ✓。
     *
     * <p>同一时间只能开一个领域（作者：「只能选择一个领域法术」）：
     * 开新领域会把其它六个全部关掉 ✓（正常玩法下学不到第二个，这条是兜底）。
     */
    public static boolean cast(ServerPlayer player, ResourceLocation spellId) {
        if (!DomainSpells.isDomain(spellId)) {
            return false;
        }
        DomainSpells.Domain domain = DomainSpells.byPath(spellId.getPath());
        if (domain == null || !player.isAlive() || player.isSpectator() || DownedCombat.isDowned(player)
                || player.level().isClientSide()) {
            return false;
        }
        MobEffect wanted = TNEffects.domainEffect(domain.element());
        if (wanted == null) {
            return false;
        }
        for (DomainSpells.Domain other : DomainSpells.domains()) {   // 只留一个领域 ✓
            if (other == domain) {
                continue;
            }
            MobEffect stale = TNEffects.domainEffect(other.element());
            if (stale != null && player.hasEffect(stale)) {
                player.removeEffect(stale);
            }
        }
        player.addEffect(new MobEffectInstance(wanted, DomainSpells.DURATION, 0, false, true, true));

        ServerLevel level = player.serverLevel();
        NEXT.put(player.getUUID(), level.getGameTime() + DomainSpells.FIRST_DELAY);
        // 开张：脚下一圈大阵 + 一记地球人都听得出"开了"的音效 ✓
        circle(level, player.position(), 6.0D, 40, grandStyle(domain));
        level.playSound(null, player.blockPosition(), castSound(domain), SoundSource.PLAYERS,
                0.7F, castPitch(domain));
        player.displayClientMessage(Component.literal("§d[TN-C] §r" + domain.name()
                + " 展开 §7（" + (DomainSpells.DURATION / 20) + " 秒）"), true);
        return true;
    }

    // ------------------------------------------------------------------
    //  每 tick：预警到期 → 出手
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.level().isClientSide()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        prune(now);

        // ① 预警到点时先结算（就算领域刚好结束，这一击也认 ✓）
        List<Pending> falling = PENDING.remove(player.getUUID());
        if (falling != null) {
            List<Pending> keep = new ArrayList<>(falling.size());
            for (Pending pending : falling) {
                if (now >= pending.due()) {
                    resolve(player, pending);
                } else {
                    keep.add(pending);                       // 还没到，放回去
                }
            }
            if (!keep.isEmpty()) {
                PENDING.put(player.getUUID(), keep);
            }
        }

        // ② 没开领域 ⇒ 什么都不做
        DomainSpells.Domain domain = DomainSpells.active(player);
        if (domain == null) {
            NEXT.remove(player.getUUID());
            return;
        }
        if (player.isSpectator() || !player.isAlive()) {
            return;
        }

        // ③ 常驻光环：身边一圈一直在掉血 / 被染上本系状态（作者：「来点范围的那种效果」）✓
        if (now % DomainSpells.AURA_INTERVAL == 0L) {
            aura(player, domain);
        }

        // ④ 到点了就找「玩家和队友正在打的目标」—— 一次点最多三个落点 ✓
        long due = NEXT.getOrDefault(player.getUUID(), now + DomainSpells.FIRST_DELAY);
        if (now < due) {
            return;
        }
        List<LivingEntity> victims = pickVictims(player);
        if (victims.isEmpty()) {
            // 作者：「周围会时不时遭雷劈」—— 没人在打的时候，天上也不能闲着 ✓
            // 只有雷霆领域这么做（其它领域是"索敌"，不该往空地放东西）
            if (DomainSpells.KIND_BOLT.equals(domain.kind())) {
                NEXT.put(player.getUUID(), now + domain.interval());
                sceneryBolt(player);
            }
            return;                                          // 有人可打就按名单来 ✓
        }
        NEXT.put(player.getUUID(), now + domain.interval());
        List<Pending> list = new ArrayList<>(victims.size());
        for (LivingEntity victim : victims) {
            list.add(telegraph(player, victim, domain, now));
        }
        PENDING.put(player.getUUID(), list);
    }

    /**
     * 没目标的"氛围雷"：在玩家周围 6~14 格随机落一根<b>纯视觉</b>的雷 ✓。
     *
     * <p>作者：「周围会时不时遭雷劈」。它<b>不造成伤害</b>（不抢玩家打怪的手感，
     * 也不会莫名其妙劈死路过的动物），只是让雷领域"听着像在用" ✓。
     */
    private static void sceneryBolt(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        double angle = level.random.nextDouble() * Math.PI * 2.0D;
        double distance = 6.0D + level.random.nextDouble() * 8.0D;
        Vec3 at = ground(level, player.position().add(Math.cos(angle) * distance, 0.0D,
                Math.sin(angle) * distance));
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(at);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.5D, at.z, 16,
                0.6D, 0.8D, 0.6D, 0.3D);
    }

    /**
     * 常驻光环：每 {@link DomainSpells#AURA_INTERVAL} tick 在玩家身边 {@link DomainSpells#AURA_RADIUS} 格内
     * 撒一圈本系粒子、所有敌人掉 {@link DomainSpells#AURA_DAMAGE} 点血并染上本系状态 ✓。
     *
     * <p>作者 2026-11-08：「领域我希望来点范围的那种效果」——
     * 之前七系都只是"点名 + 一个小圈"，站在领域里没有任何"被领域罩住"的感觉 ✗。
     * 现在领域 = <b>身边一圈持续生效的光环</b> + 隔几秒一次的多点打击 ✓。
     *
     * <p>⚠️ 光环<b>不碰玩家</b>（{@code hittable} 排除 Player）：PVP 时不会莫名其妙掉血 ✓；
     * 光领域例外 —— 它给范围内的玩家（含自己）回血 ✓。
     */
    private static void aura(ServerPlayer player, DomainSpells.Domain domain) {
        ServerLevel level = player.serverLevel();
        Element element = domain.element();
        Vec3 center = player.position();
        double radius = DomainSpells.AURA_RADIUS;

        // ① 地面上滚一圈本系粒子（16 颗，不用实体，便宜 ✓）
        for (int i = 0; i < 16; i++) {
            double angle = i * (Math.PI * 2.0D / 16.0D) + level.getGameTime() * 0.02D;
            level.sendParticles(auraParticle(element),
                    center.x + Math.cos(angle) * radius, center.y + 0.15D,
                    center.z + Math.sin(angle) * radius, 1, 0.0D, 0.02D, 0.0D, 0.0D);
        }

        // ② 敌人：掉血 + 每两秒染一次本系状态 ✓
        boolean second = level.getGameTime() % (DomainSpells.AURA_INTERVAL * 2L) == 0L;
        for (LivingEntity target : around(level, center, radius)) {
            applyMagic(player, target, DomainSpells.AURA_DAMAGE);
            auraFlavour(player, domain, target, second);
        }

        // ③ 光领域：站在领域里的玩家（含自己）持续回血 ✓
        if (element == Element.LIGHT) {
            AABB box = new AABB(center.x - radius, center.y - 2.0D, center.z - radius,
                    center.x + radius, center.y + 3.0D, center.z + radius);
            for (Player ally : level.getEntitiesOfClass(Player.class, box, Player::isAlive)) {
                if (!ally.isSpectator()) {
                    ally.heal(DomainSpells.AURA_HEAL);
                }
            }
        }
    }

    /** 光环每跳给目标染上的本系状态（很轻，主要是"被领域罩着"的体感）✓。 */
    private static void auraFlavour(ServerPlayer owner, DomainSpells.Domain domain,
                                    LivingEntity target, boolean second) {
        switch (domain.element()) {
            case LIGHTNING -> {
                if (second) {                                    // 麻一下 ✓
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                            12, 0, false, true, true));
                }
            }
            case FIRE -> target.setSecondsOnFire(1);             // 一直烧着 ✓
            case WATER -> {
                if (second) {                                    // 潮湿 ✓
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                            24, 0, false, true, true));
                }
            }
            case EARTH -> {
                if (second) {                                    // 沉重 ✓
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                            12, 1, false, true, true));
                }
            }
            case WIND -> {                                       // 被风轻轻往外推 ✓
                Vec3 push = target.position().subtract(owner.position());
                if (push.lengthSqr() > 1.0E-4D) {
                    push = push.normalize().scale(0.06D);
                    target.setDeltaMovement(target.getDeltaMovement().add(push.x, 0.0D, push.z));
                    target.hurtMarked = true;
                }
            }
            case DARK -> {
                if (second) {                                    // 视线被黑雾吃掉 ✓
                    target.addEffect(new MobEffectInstance(MobEffects.DARKNESS,
                            40, 0, false, true, true));
                }
            }
            case LIGHT -> {
                if (second) {                                    // 被圣光标记 ✓
                    target.addEffect(new MobEffectInstance(MobEffects.GLOWING,
                            40, 0, false, false, false));
                }
            }
        }
    }

    /** 光环那一圈的粒子（每系一种，玩家一眼知道自己在哪个领域里 ✓）。 */
    private static net.minecraft.core.particles.SimpleParticleType auraParticle(Element element) {
        return switch (element) {
            case LIGHTNING -> ParticleTypes.ELECTRIC_SPARK;
            case DARK -> ParticleTypes.SCULK_SOUL;
            case FIRE -> ParticleTypes.FLAME;
            case WIND -> ParticleTypes.CLOUD;
            case WATER -> ParticleTypes.SPLASH;
            case EARTH -> ParticleTypes.CRIT;
            case LIGHT -> ParticleTypes.END_ROD;
        };
    }

    /** 预警：地面那一圈（作者：「劈之前地面有明显落点」✓）。 */
    private static Pending telegraph(ServerPlayer player, LivingEntity victim,
                                     DomainSpells.Domain domain, long now) {
        ServerLevel level = player.serverLevel();
        Vec3 at = ground(level, victim.position());
        circle(level, at, 1.6D, domain.telegraph() + 12, domain.style());
        level.playSound(null, BlockPos.containing(at), telegraphSound(domain), SoundSource.PLAYERS,
                0.35F, telegraphPitch(domain));
        return new Pending(level, at, victim.getId(), now + domain.telegraph(), domain);
    }

    /** 结算：按出手方式分派 ✓。 */
    private static void resolve(ServerPlayer owner, Pending pending) {
        ServerLevel level = pending.level();
        Vec3 at = pending.at();
        Entity found = level.getEntity(pending.targetId());
        LivingEntity victim = found instanceof LivingEntity living && living.isAlive() ? living : null;
        if (victim != null) {
            at = ground(level, victim.position());            // 人动了：以它现在脚下为准 ✓
        }
        DomainSpells.Domain domain = pending.domain();
        switch (domain.kind()) {
            case DomainSpells.KIND_GRIP -> darkGrip(owner, level, at, victim);
            case DomainSpells.KIND_BURN -> firePillar(owner, level, at, domain);
            case DomainSpells.KIND_CUT -> windCut(owner, level, at, domain);
            case DomainSpells.KIND_WAVE -> waterWave(owner, level, at, domain);
            case DomainSpells.KIND_QUAKE -> earthSpikes(owner, level, at, domain);
            case DomainSpells.KIND_SMITE -> lightSmite(owner, level, at, domain);
            default -> lightningStrike(owner, level, at, domain);
        }
    }

    // ------------------------------------------------------------------
    //  七种出手
    // ------------------------------------------------------------------

    /** 雷：一记真雷砸那一小块地 ✓。 */
    private static void lightningStrike(ServerPlayer owner, ServerLevel level, Vec3 at,
                                        DomainSpells.Domain domain) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(at);
            bolt.setVisualOnly(true);                        // 伤害我们自己结算，免得双重计算 ✗
            level.addFreshEntity(bolt);
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.4D, at.z, 30,
                0.7D, 1.0D, 0.7D, 0.35D);
        level.playSound(null, BlockPos.containing(at), SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.WEATHER, 0.6F, 1.7F);
        damageAround(owner, level, at, domain, domain.damage());
    }

    /** 暗：脚下黑雾 → 手从地里伸出来 → 按住 ✓。 */
    private static void darkGrip(ServerPlayer owner, ServerLevel level, Vec3 at, LivingEntity victim) {
        // ① 一小团黑雾在脚下散开 ✓
        level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 0.15D, at.z, 18,
                0.55D, 0.25D, 0.55D, 0.02D);
        level.sendParticles(ParticleTypes.SCULK_SOUL, at.x, at.y + 0.35D, at.z, 12,
                0.45D, 0.35D, 0.45D, 0.015D);
        level.playSound(null, BlockPos.containing(at), SoundEvents.SCULK_SHRIEKER_SHRIEK,
                SoundSource.PLAYERS, 0.55F, 0.8F);

        // ② 手从雾里伸出来（起点就在脚下、比地面低一点 ⇒ 读作"从地里伸出来"✓）
        if (TNOrbEntities.NIGHT_GRIP != null) {
            TNNightGripEntity hand = TNOrbEntities.NIGHT_GRIP.get().create(level);
            if (hand != null) {
                hand.launchFrom(at.add(0.0D, -0.9D, 0.0D), owner,
                        victim == null ? -1 : victim.getId(), 3);
                level.addFreshEntity(hand);
            }
        }

        // ③ 一直控制：动不了、看不见、还挂着标记 ✓
        for (LivingEntity target : around(level, at, 2.0D)) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    DomainSpells.GRIP_TICKS, 6, false, true, true));
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS,
                    DomainSpells.GRIP_TICKS, 0, false, true, true));
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING,
                    DomainSpells.GRIP_TICKS, 0, false, false, false));
            target.setDeltaMovement(Vec3.ZERO);
            target.hurtMarked = true;
        }
    }

    /** 火：落点火柱 + 点燃 ✓。 */
    private static void firePillar(ServerPlayer owner, ServerLevel level, Vec3 at,
                                   DomainSpells.Domain domain) {
        for (int i = 0; i < 40; i++) {
            double y = at.y + i * 0.12D;
            level.sendParticles(ParticleTypes.FLAME, at.x + (level.random.nextDouble() - 0.5D) * 0.8D,
                    y, at.z + (level.random.nextDouble() - 0.5D) * 0.8D, 1, 0.0D, 0.06D, 0.0D, 0.02D);
            level.sendParticles(ParticleTypes.LAVA, at.x + (level.random.nextDouble() - 0.5D) * 0.9D,
                    y + 0.2D, at.z + (level.random.nextDouble() - 0.5D) * 0.9D, 1, 0.0D, 0.02D, 0.0D, 0.0D);
        }
        level.sendParticles(ParticleTypes.SMOKE, at.x, at.y + 0.4D, at.z, 24, 0.6D, 0.5D, 0.6D, 0.04D);
        level.playSound(null, BlockPos.containing(at), SoundEvents.BLAZE_SHOOT,
                SoundSource.PLAYERS, 0.8F, 1.1F);
        for (LivingEntity target : damageAround(owner, level, at, domain, domain.damage())) {
            target.setSecondsOnFire(DomainSpells.BURN_SECONDS);
        }
    }

    /** 风：一圈风刃割开，并把它们往外推 ✓。 */
    private static void windCut(ServerPlayer owner, ServerLevel level, Vec3 at,
                                DomainSpells.Domain domain) {
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y + 0.9D, at.z, 6, 0.5D, 0.3D, 0.5D, 0.0D);
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.3D, at.z, 20, 1.2D, 0.25D, 1.2D, 0.08D);
        level.playSound(null, BlockPos.containing(at), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 0.9F, 0.8F);
        for (LivingEntity target : damageAround(owner, level, at, domain, domain.damage())) {
            Vec3 push = target.position().subtract(at);
            if (push.lengthSqr() < 1.0E-4D) {
                push = new Vec3(0.0D, 0.0D, 1.0D);
            }
            push = push.normalize().scale(0.85D);
            target.setDeltaMovement(target.getDeltaMovement().add(push.x, 0.32D, push.z));
            target.hurtMarked = true;
        }
    }

    /** 水：涌起水浪，泡住减速 ✓。 */
    private static void waterWave(ServerPlayer owner, ServerLevel level, Vec3 at,
                                  DomainSpells.Domain domain) {
        level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.35D, at.z, 40, 1.1D, 0.35D, 1.1D, 0.12D);
        level.sendParticles(ParticleTypes.BUBBLE, at.x, at.y + 0.5D, at.z, 18, 0.9D, 0.4D, 0.9D, 0.02D);
        level.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_SPLASH,
                SoundSource.PLAYERS, 0.8F, 0.7F);
        for (LivingEntity target : damageAround(owner, level, at, domain, domain.damage())) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    DomainSpells.SOAK_TICKS, 2, false, true, true));
        }
    }

    /** 土：岩刺从脚下顶起来，把人定在原地 ✓。 */
    private static void earthSpikes(ServerPlayer owner, ServerLevel level, Vec3 at,
                                    DomainSpells.Domain domain) {
        for (int i = 0; i < 14; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double r = 0.4D + level.random.nextDouble() * 1.2D;
            level.sendParticles(ParticleTypes.CRIT, at.x + Math.cos(angle) * r, at.y + 0.15D,
                    at.z + Math.sin(angle) * r, 2, 0.05D, 0.35D, 0.05D, 0.06D);
        }
        level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.2D, at.z, 22, 1.0D, 0.25D, 1.0D, 0.05D);
        level.playSound(null, BlockPos.containing(at), SoundEvents.STONE_BREAK,
                SoundSource.PLAYERS, 0.9F, 0.6F);
        for (LivingEntity target : damageAround(owner, level, at, domain, domain.damage())) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    DomainSpells.ROOT_TICKS, 6, false, true, true));
            target.setDeltaMovement(Vec3.ZERO);
            target.hurtMarked = true;
        }
    }

    /** 光：烧敌人、同时给范围内的玩家/队友回血 ✓。 */
    private static void lightSmite(ServerPlayer owner, ServerLevel level, Vec3 at,
                                   DomainSpells.Domain domain) {
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.6D, at.z, 30, 0.7D, 1.4D, 0.7D, 0.04D);
        level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y + 1.0D, at.z, 20, 1.0D, 1.0D, 1.0D, 0.1D);
        level.playSound(null, BlockPos.containing(at), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.9F, 1.3F);
        damageAround(owner, level, at, domain, domain.damage());
        // 队友（含自己）回血：作者要的是"领域"，站在里面就该有好处 ✓
        AABB box = new AABB(at.x - domain.radius(), at.y - 2.0D, at.z - domain.radius(),
                at.x + domain.radius(), at.y + 3.0D, at.z + domain.radius());
        for (Player ally : level.getEntitiesOfClass(Player.class, box, Player::isAlive)) {
            if (ally.isSpectator()) {
                continue;
            }
            ally.heal(DomainSpells.LIGHT_HEAL);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, ally.getX(), ally.getY() + 1.2D,
                    ally.getZ(), 6, 0.35D, 0.4D, 0.35D, 0.01D);
        }
    }

    /**
     * 范围内所有可打的目标吃一次伤害（伤害归属仍是领域主人，走间接魔法）✓。
     *
     * @return 命中的目标（调用方可以再补状态，比如点燃/减速）✓
     */
    private static List<LivingEntity> damageAround(ServerPlayer owner, ServerLevel level, Vec3 at,
                                                   DomainSpells.Domain domain, float damage) {
        List<LivingEntity> hit = new ArrayList<>();
        for (LivingEntity target : around(level, at, domain.radius())) {
            applyMagic(owner, target, damage);
            hit.add(target);
        }
        return hit;
    }

    /**
     * 领域伤害统一走这里：<b>先把无敌帧清零</b>再打 ✓。
     *
     * <p>为什么必须清：原版受击后有 20 tick 无敌帧（`invulnerableTime`），
     * 而领域光环是<b>每秒 1 点</b>的持续小伤害 —— 不清的话，玩家砍出的一刀会落在
     * 光环刚打出来的无敌帧里被吞掉（伤害归零，还查不出原因 ✗）。
     * 先清零再 hurt，等于"领域伤害不占玩家的受击窗口" ✓。
     */
    private static void applyMagic(ServerPlayer owner, LivingEntity target, float amount) {
        if (amount <= 0.0F || !target.isAlive()) {
            return;
        }
        target.invulnerableTime = 0;
        target.hurt(owner.damageSources().indirectMagic(owner, owner), amount);
    }

    /** 落点周围的可打目标（排除自己/友军/玩家）✓。 */
    private static List<LivingEntity> around(ServerLevel level, Vec3 at, double radius) {
        AABB box = new AABB(at.x - radius, at.y - 1.6D, at.z - radius,
                at.x + radius, at.y + 3.0D, at.z + radius);
        return level.getEntitiesOfClass(LivingEntity.class, box, DomainService::hittable);
    }

    // ------------------------------------------------------------------
    //  战士附伤 ＋ 记住"谁在被玩家/队友打"
    // ------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        if (event.isCanceled() || !(event.getSource().getDirectEntity() instanceof ServerPlayer player)
                || event.getEntity().level().isClientSide()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        long now = victim.level().getGameTime();
        HIT.put(victim.getUUID(), now);                       // 队友的也算 ✓

        DomainSpells.Domain domain = DomainSpells.active(player);
        if (domain == null) {
            return;
        }
        // 战士附伤：这一刀额外 30%（封顶 12 点），走间接魔法 ⇒ 伤害还是记在这个玩家头上 ✓
        float bonus = Math.min(DomainSpells.MELEE_BONUS_CAP, event.getAmount() * DomainSpells.MELEE_BONUS);
        if (bonus <= 0.0F) {
            return;
        }
        event.setAmount(event.getAmount() + bonus);
        ServerLevel level = (ServerLevel) victim.level();
        meleeFx(level, domain.element(), victim);
        // 雷领域额外给一根小雷：作者「战士的攻击会携带小闪电」✓
        if (domain.element() == Element.LIGHTNING && player.getRandom().nextFloat() < 0.25F) {
            LightningBolt small = EntityType.LIGHTNING_BOLT.create(level);
            if (small != null) {
                small.moveTo(victim.getX(), victim.getY(), victim.getZ());
                small.setVisualOnly(true);
                level.addFreshEntity(small);
            }
        }
    }

    /** 战士附伤的命中特效（跟着元素走；刀光颜色在客户端由 {@code WarriorTrail} 换 ✓）。 */
    private static void meleeFx(ServerLevel level, Element element, LivingEntity victim) {
        double x = victim.getX();
        double y = victim.getY() + 1.0D;
        double z = victim.getZ();
        switch (element) {
            case LIGHTNING -> level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 10,
                    0.35D, 0.45D, 0.35D, 0.25D);
            case DARK -> {
                level.sendParticles(ParticleTypes.SCULK_SOUL, x, y, z, 8, 0.3D, 0.4D, 0.3D, 0.02D);
                level.sendParticles(ParticleTypes.SMOKE, x, y - 0.1D, z, 6, 0.25D, 0.3D, 0.25D, 0.01D);
            }
            case FIRE -> {
                level.sendParticles(ParticleTypes.FLAME, x, y, z, 10, 0.3D, 0.4D, 0.3D, 0.03D);
                level.sendParticles(ParticleTypes.LAVA, x, y, z, 2, 0.2D, 0.2D, 0.2D, 0.0D);
            }
            case WIND -> level.sendParticles(ParticleTypes.SWEEP_ATTACK, x, y, z, 2,
                    0.3D, 0.2D, 0.3D, 0.0D);
            case WATER -> level.sendParticles(ParticleTypes.SPLASH, x, y, z, 12,
                    0.35D, 0.4D, 0.35D, 0.08D);
            case EARTH -> level.sendParticles(ParticleTypes.CRIT, x, y, z, 10,
                    0.3D, 0.4D, 0.3D, 0.06D);
            case LIGHT -> level.sendParticles(ParticleTypes.END_ROD, x, y, z, 10,
                    0.3D, 0.5D, 0.3D, 0.03D);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------
    //  小工具
    // ------------------------------------------------------------------

    /** 开领域时那圈"大阵"的样式：雷/暗有专属，其余用该系的阵放大 ✓。 */
    private static int grandStyle(DomainSpells.Domain domain) {
        return switch (domain.element()) {
            case LIGHTNING -> TNMagicCircleEntity.STYLE_LIGHTNING_GRAND;
            case DARK -> TNMagicCircleEntity.STYLE_DARK_GRAND;
            default -> domain.style();
        };
    }

    /** 开领域的音效 ✓。 */
    private static net.minecraft.sounds.SoundEvent castSound(DomainSpells.Domain domain) {
        return switch (domain.element()) {
            case LIGHTNING -> SoundEvents.LIGHTNING_BOLT_THUNDER;
            case DARK -> SoundEvents.SOUL_ESCAPE;
            case FIRE -> SoundEvents.FIRECHARGE_USE;
            case WIND -> SoundEvents.PHANTOM_FLAP;
            case WATER -> SoundEvents.GENERIC_SPLASH;
            case EARTH -> SoundEvents.STONE_BREAK;
            case LIGHT -> SoundEvents.AMETHYST_BLOCK_CHIME;
        };
    }

    private static float castPitch(DomainSpells.Domain domain) {
        return switch (domain.element()) {
            case DARK -> 0.6F;
            case LIGHTNING -> 1.5F;
            case FIRE -> 1.1F;
            case WIND -> 1.3F;
            case WATER -> 0.7F;
            case EARTH -> 0.6F;
            case LIGHT -> 1.3F;
        };
    }

    /** 预警那一下的音效（要轻，不然每 5~8 秒砸一次会很吵）✓。 */
    private static net.minecraft.sounds.SoundEvent telegraphSound(DomainSpells.Domain domain) {
        return switch (domain.element()) {
            case LIGHTNING -> SoundEvents.LIGHTNING_BOLT_IMPACT;
            case DARK -> SoundEvents.SCULK_CATALYST_BLOOM;
            case FIRE -> SoundEvents.FIRECHARGE_USE;
            case WIND -> SoundEvents.PLAYER_ATTACK_SWEEP;
            case WATER -> SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT;
            case EARTH -> SoundEvents.STONE_PLACE;
            case LIGHT -> SoundEvents.AMETHYST_BLOCK_CHIME;
        };
    }

    private static float telegraphPitch(DomainSpells.Domain domain) {
        return switch (domain.element()) {
            case LIGHTNING -> 1.9F;
            case DARK -> 1.4F;
            case FIRE -> 1.4F;
            case WIND -> 1.6F;
            case WATER -> 1.2F;
            case EARTH -> 0.8F;
            case LIGHT -> 1.6F;
        };
    }

    /** 能不能打：活着、不是观察者、不是玩家 ✓。 */
    private static boolean hittable(LivingEntity entity) {
        return entity.isAlive() && !entity.isSpectator() && !(entity instanceof Player);
    }

    /**
     * 从"最近被玩家/队友打过的目标"里挑最多 {@link DomainSpells#MAX_TARGETS} 个（间隔 ≥
     * {@link DomainSpells#TARGET_SPACING} 格，免得三个圈叠成一个）✓。
     *
     * <p>够不着任何"刚被打过"的就退而求其次挑最近的敌人 —— 一片打击总比空放好看 ✓。
     */
    private static List<LivingEntity> pickVictims(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        List<LivingEntity> candidates = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(DomainSpells.RADIUS), DomainService::hittable));
        candidates.removeIf(e -> e.isAlliedTo(player) || e == player);
        List<LivingEntity> beaten = new ArrayList<>();
        for (LivingEntity candidate : candidates) {
            Long at = HIT.get(candidate.getUUID());
            if (at != null && now - at <= DomainSpells.TARGET_MEMORY) {
                beaten.add(candidate);
            }
        }
        List<LivingEntity> pool = beaten.isEmpty() ? candidates : beaten;
        pool.sort(Comparator.comparingDouble(e -> {
            Long at = HIT.get(e.getUUID());
            return at == null ? Double.MAX_VALUE : -at;       // 最近挨打的排前面 ✓
        }));
        List<LivingEntity> picked = new ArrayList<>(DomainSpells.MAX_TARGETS);
        for (LivingEntity candidate : pool) {
            boolean tooClose = false;
            for (LivingEntity chosen : picked) {
                if (chosen.distanceToSqr(candidate) < DomainSpells.TARGET_SPACING * DomainSpells.TARGET_SPACING) {
                    tooClose = true;
                    break;
                }
            }
            if (tooClose) {
                continue;
            }
            picked.add(candidate);
            if (picked.size() >= DomainSpells.MAX_TARGETS) {
                break;
            }
        }
        return picked;
    }

    /** 把点按到地面上（往下找实心方块，最多找 8 格）✓。 */
    private static Vec3 ground(ServerLevel level, Vec3 at) {
        if (!level.hasChunkAt(BlockPos.containing(at))) {
            return at;
        }
        HitResult hit = level.clip(new ClipContext(at.add(0.0D, 6.0D, 0.0D), at.add(0.0D, -8.0D, 0.0D),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
        return hit.getType() == HitResult.Type.MISS ? at : hit.getLocation();
    }

    /** 铺一圈法阵（复用 {@code TNMagicCircleEntity}，样式由调用方给 ✓）。 */
    private static void circle(ServerLevel level, Vec3 at, double radius, int life, int style) {
        if (TNOrbEntities.MAGIC_CIRCLE == null) {
            return;
        }
        TNMagicCircleEntity seal = TNOrbEntities.MAGIC_CIRCLE.get().create(level);
        if (seal == null) {
            return;
        }
        seal.configure(radius, life, style);
        seal.setPos(at.x, at.y + 0.06D, at.z);
        level.addFreshEntity(seal);
    }

    /** 名单太长了就剪一次（每 5 秒一次，开销可以忽略）✓。 */
    private static void prune(long now) {
        if (now % 100L != 0L) {
            return;
        }
        HIT.entrySet().removeIf(entry -> now - entry.getValue() > DomainSpells.TARGET_MEMORY * 4L);
        NEXT.entrySet().removeIf(entry -> entry.getValue() < now - 24000L);
    }

    /** 玩家掉线时清干净，免得记着一个不存在的预警 ✓。 */
    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            forget(player.getUUID());
        }
    }

    /** 玩家掉线/换维度时清干净 ✓。 */
    public static void forget(UUID uuid) {
        PENDING.remove(uuid);
        NEXT.remove(uuid);
    }

    /** 调试用：现在记着多少个"刚被打过"的目标。 */
    public static int trackedTargets() {
        return HIT.size();
    }

    /** 调试用：某个玩家是不是还挂着待结算的预警。 */
    public static boolean hasPending(UUID uuid) {
        List<Pending> list = PENDING.get(uuid);
        return list != null && !list.isEmpty();
    }

    /** 调试用：第一个预警的落点（没有就 {@code null}）。 */
    public static Vec3 pendingAt(UUID uuid) {
        List<Pending> list = PENDING.get(uuid);
        return list == null || list.isEmpty() ? null : list.get(0).at();
    }
}
