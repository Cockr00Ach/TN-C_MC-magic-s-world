package com.tnc.tnc.magic;

import com.tnc.tnc.TNMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 风系：效果 + 飞行机制。
 *
 * <h2>为什么风系的加速要单独做效果</h2>
 * 原版速度效果每级固定 +20%，拿不到用户要的 +100% / +150% / +300%，
 * 所以和雷/火一样自己注册。
 *
 * <h2>风系伤害加成挂哪个属性</h2>
 * {@link Element#WIND} 对应引擎属性 {@code spell_power:air}（见 Element 枚举），
 * 所以风伤加成挂它 —— 不是 "wind"。
 *
 * <h2>飞行（链1 的核心）</h2>
 * <ul>
 *   <li>1~3 级：法术给一个 {@code wind_flight} 标记效果（5s / 15s），
 *       效果在身就允许飞（{@code mayfly}），效果结束收回。</li>
 *   <li>4~5 级：**只要学会就永久允许飞**（不看效果）—— 判断依据是
 *       {@code MagicStoneData} 里风速链的进度 ≥ 4。</li>
 *   <li>收回时**绝对不动创造/旁观玩家**：那两种模式本来就会飞，
 *       被我们收掉是严重 bug。</li>
 * </ul>
 *
 * <p>还没做（等确认）：风神降临的"蓝耗减半"（要改扣蓝那一步）、
 * "技能无冷却"（雷系已有清冷却的机制，可以复用）。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class TNWindMechanics {

    public static final DeferredRegister<MobEffect> WIND_EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, TNMod.MODID);

    private static final int COLOR_WIND = 0xC8E8F0;

    private static final ResourceLocation WIND_POWER_ATTRIBUTE =
            ResourceLocation.fromNamespaceAndPath("spell_power", "air");

    // ---------------- 效果 ----------------

    /** 起飞标记：有它就能飞（持续时间 = 法术给的 5s / 15s）。 */
    public static final RegistryObject<MobEffect> WIND_FLIGHT = WIND_EFFECTS.register(
            "wind_flight", MarkerEffect::new);

    /** 风速 +100%（2 级）。 */
    public static final RegistryObject<MobEffect> WIND_SPEED_I = speed("wind_speed_i", 1.00D);
    /** 顶级风速 +150%（3 级）。 */
    public static final RegistryObject<MobEffect> WIND_SPEED_II = speed("wind_speed_ii", 1.50D);
    /** 超级风速 / 风神降临 +300%（4、5 级）。 */
    public static final RegistryObject<MobEffect> WIND_SPEED_III = speed("wind_speed_iii", 3.00D);

    /** 风伤 +50%（3 级）。 */
    public static final RegistryObject<MobEffect> WIND_POWER_I = windPower("wind_power_i", 0.50D);
    /** 风伤 +100%（4、5 级）。 */
    public static final RegistryObject<MobEffect> WIND_POWER_II = windPower("wind_power_ii", 1.00D);

    // ---- 链3 范围风：给敌人减速、给自己加速 ----

    /** 范围风：周围怪物 -25% 速度（有害）。 */
    public static final RegistryObject<MobEffect> GALE_SLOW = WIND_EFFECTS.register(
            "gale_slow", () -> new GaleSlowEffect());

    /** 范围风：自己 +25% 速度（"队友加速"目前只作用于施法者，见类注释）。 */
    public static final RegistryObject<MobEffect> GALE_HASTE = WIND_EFFECTS.register(
            "gale_haste", () -> new GaleHasteEffect());

    /** 减速 25%（有害效果，颜色用暗一点的青）。 */
    private static final class GaleSlowEffect extends MobEffect {
        GaleSlowEffect() {
            super(MobEffectCategory.HARMFUL, 0x4A7A8A);
            Attribute speed = net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED;
            addAttributeModifier(speed, uuidFor("gale_slow"), -0.25D,
                    AttributeModifier.Operation.MULTIPLY_BASE);
        }
    }

    /** 加速 25%。 */
    private static final class GaleHasteEffect extends MobEffect {
        GaleHasteEffect() {
            super(MobEffectCategory.BENEFICIAL, COLOR_WIND);
            Attribute speed = net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED;
            addAttributeModifier(speed, uuidFor("gale_haste"), 0.25D,
                    AttributeModifier.Operation.MULTIPLY_BASE);
        }
    }

    private static String uuidFor(String key) {
        return UUID.nameUUIDFromBytes(("tnc:wind:" + key).getBytes(StandardCharsets.UTF_8)).toString();
    }

    // ---------------- 链2 风球/风灵（绕身球版） ----------------
    //
    // 用户说"风灵最好是独立的，不过可以先做绕身球" —— 所以这一版是：
    // 法术给自己一个标记效果，Java 每 tick 在玩家周围画 N 个球，
    // 每 ORB_ZAP_INTERVAL tick 打一下最近的敌人（并把它往你这边拽一点 = 牵引）。
    //
    // 简化掉的部分（都是"独立实体"才有的东西）：球不会自己飞出去、
    // 不会被攻击、不会寻路。风灵 3/4 级的"自主召唤"这里表现为球更多、
    // 打得更疼（同一个机制，不同参数）。

    /** 三个风球。 */
    public static final RegistryObject<MobEffect> WIND_ORB_THREE = WIND_EFFECTS.register(
            "wind_orb_three", MarkerEffect::new);
    /** 五个风球。 */
    public static final RegistryObject<MobEffect> WIND_ORB_FIVE = WIND_EFFECTS.register(
            "wind_orb_five", MarkerEffect::new);
    /** 风灵（更强的一档：球更多、打得更疼）。 */
    public static final RegistryObject<MobEffect> WIND_SPIRIT = WIND_EFFECTS.register(
            "wind_spirit", MarkerEffect::new);

    /** 球离玩家多远。 */
    private static final double ORB_RADIUS = 1.6D;
    /** 每隔几 tick 打一次。 */
    private static final int ORB_ZAP_INTERVAL = 10;
    /** 打多远内的敌人。 */
    private static final double ORB_RANGE = 5.0D;
    /** 每次伤害。 */
    private static final float ORB_DAMAGE = 4.0F;
    /** 风灵档的额外倍率。 */
    private static final float SPIRIT_DAMAGE_MULTIPLIER = 2.0F;
    /** 牵引：把敌人往玩家这边拽多少（每 tick）。 */
    private static final double ORB_PULL = 0.12D;

    /** 玩家现在有几个球（0 = 没有）。 */
    private static int orbCount(ServerPlayer player) {
        if (has(player, WIND_SPIRIT)) {
            return 5;
        }
        if (has(player, WIND_ORB_FIVE)) {
            return 5;
        }
        if (has(player, WIND_ORB_THREE)) {
            return 3;
        }
        return 0;
    }

    private static boolean has(ServerPlayer player, RegistryObject<MobEffect> effect) {
        return effect.isPresent() && player.hasEffect(effect.get());
    }

    /** 绕身球：画球 + 定期打最近的敌人（含牵引）。 */
    private static void tickOrbs(ServerPlayer player, long time) {
        int count = orbCount(player);
        if (count == 0) {
            return;
        }
        boolean spirit = has(player, WIND_SPIRIT);
        for (int i = 0; i < count; i++) {
            double angle = (time % 80) / 80.0 * Math.PI * 2.0 + i * (Math.PI * 2.0 / count);
            double x = player.getX() + Math.cos(angle) * ORB_RADIUS;
            double z = player.getZ() + Math.sin(angle) * ORB_RADIUS;
            player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                    x, player.getY() + 1.0D, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        if (time % ORB_ZAP_INTERVAL != 0) {
            return;
        }
        net.minecraft.world.phys.AABB box = player.getBoundingBox().inflate(ORB_RANGE);
        java.util.List<net.minecraft.world.entity.LivingEntity> targets =
                player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box,
                        e -> e != player && !(e instanceof net.minecraft.world.entity.player.Player));
        for (net.minecraft.world.entity.LivingEntity target : targets) {
            float damage = spirit ? ORB_DAMAGE * SPIRIT_DAMAGE_MULTIPLIER : ORB_DAMAGE;
            target.hurt(player.damageSources().indirectMagic(player, player), damage);
            // 牵引：往玩家方向拽一点（"风球的攻击有牵引效果"）
            net.minecraft.world.phys.Vec3 pull = player.position().subtract(target.position()).normalize().scale(ORB_PULL);
            target.push(pull.x, 0.0D, pull.z);
            target.hurtMarked = true;
            player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
                    target.getX(), target.getY() + 1.0D, target.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            break;                                  // 一次只打一个，和"球在打人"的感觉一致
        }
    }

    // ---------------- 机制常量 ----------------

    /** 从几级开始"解锁即永久飞行"。 */
    private static final int PERMANENT_FLIGHT_TIER = 4;

    private TNWindMechanics() {
    }

    public static void register(IEventBus modEventBus) {
        WIND_EFFECTS.register(modEventBus);
    }

    private static RegistryObject<MobEffect> speed(String name, double amount) {
        return WIND_EFFECTS.register(name, () -> new SimpleAttributeEffect(amount, false));
    }

    private static RegistryObject<MobEffect> windPower(String name, double amount) {
        return WIND_EFFECTS.register(name, () -> new SimpleAttributeEffect(amount, true));
    }

    /** 纯标记效果（不带属性修饰符，只用来判断"在不在飞"）。 */
    private static final class MarkerEffect extends MobEffect {
        MarkerEffect() {
            super(MobEffectCategory.BENEFICIAL, COLOR_WIND);
        }
    }

    /** 只挂一个属性的增益/减益效果。 */
    private static final class SimpleAttributeEffect extends MobEffect {        SimpleAttributeEffect(double amount, boolean windPower) {
            super(MobEffectCategory.BENEFICIAL, COLOR_WIND);
            Attribute attribute = windPower
                    ? ForgeRegistries.ATTRIBUTES.getValue(WIND_POWER_ATTRIBUTE)
                    : net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED;
            if (attribute != null) {
                addAttributeModifier(attribute,
                        UUID.nameUUIDFromBytes(("tnc:wind:" + amount + ":" + windPower).getBytes(StandardCharsets.UTF_8)).toString(),
                        amount, AttributeModifier.Operation.MULTIPLY_BASE);
            }
        }
    }

    // ---------------- 飞行 ----------------

    /**
     * 每 tick 维护"能不能飞"。
     *
     * <p>判断顺序：永久飞行（链进度 ≥ 4）→ 标记效果 → 都没有就收回。
     * 收回只针对生存模式，创造/旁观不碰。
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        boolean creativeLike = player.isCreative() || player.isSpectator();
        boolean shouldFly = permanentFlight(player) || player.hasEffect(WIND_FLIGHT.get());
        if (shouldFly == player.getAbilities().mayfly) {
            return;                                  // 已经是想要的状态，别每 tick 刷同步包
        }
        if (!shouldFly && creativeLike) {
            return;                                  // 创造/旁观的飞行不是我们给的，不许收
        }
        player.getAbilities().mayfly = shouldFly;
        if (!shouldFly) {
            player.getAbilities().flying = false;
        }
        player.onUpdateAbilities();
    }

    /** 链2：绕身球（另开一个 tick 处理，逻辑上互不影响）。 */
    @SubscribeEvent
    public static void onPlayerTickOrbs(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.level().isClientSide()) {
            return;
        }
        tickOrbs(player, player.level().getGameTime());
    }

    /** 是否已经学会"解锁即永久飞行"（风速链进度 ≥ 4）。 */
    private static boolean permanentFlight(ServerPlayer player) {
        // MagicStone.get 返回 Optional<MagicStoneData>（和网络同步那边一个用法）
        return MagicStone.get(player)
                .map(data -> data.getProgress(Element.WIND, SpellCatalog.Chain.FLIGHT) >= PERMANENT_FLIGHT_TIER)
                .orElse(false);
    }

    /** 供自检/命令用。 */
    public static int registeredCount() {
        return WIND_EFFECTS.getEntries().size();
    }

    public static boolean permanentFlightFor(ServerPlayer player) {
        return permanentFlight(player);
    }
}
