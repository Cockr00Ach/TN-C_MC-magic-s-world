package com.tnc.tnc.magic;

import com.tnc.tnc.TNMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.UUID;

/**
 * 燃烧线（火系第 3 条链）的<b>效果</b>与<b>机制</b>。
 *
 * <h2>5 个效果：只加火系伤害</h2>
 * 用户明确"加成只加火系"，所以只挂 {@code spell_power:fire} 这一个属性
 * （雷系那个"闪电登神"是加所有伤害，做法不同）。数值 +10% / +25% / +75% / +150% / +200%。
 *
 * <h2>4 套机制（引擎数据层都表达不了）</h2>
 * <ul>
 *   <li><b>燃血</b>：按最大生命百分比扣血，<b>永不致死</b>（保底留 1 点）。
 *       初级 1%/秒、中级 2%/秒、高级 3%/秒；火附着与完全燃烧<b>不扣</b>。</li>
 *   <li><b>原地复活</b>：中级/高级燃烧期间死亡 → 取消死亡、回满血、效果结束（一次）。</li>
 *   <li><b>完全燃烧</b>：血设 1 + 无敌，效果结束时回半血。期间不燃血。</li>
 *   <li><b>自爆</b>：施法瞬间扣自己最大生命 10%（<b>会致死</b>），范围伤害由法术 JSON 负责。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class TNFireMechanics {

    public static final DeferredRegister<MobEffect> FIRE_EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, TNMod.MODID);

    private static final int COLOR_FIRE = 0xE06010;

    /** 火系伤害加成的属性 id（spell_power 的学派属性）。 */
    private static final ResourceLocation FIRE_ATTRIBUTE =
            ResourceLocation.fromNamespaceAndPath("spell_power", "fire");

    // ---------------- 效果 ----------------

    /** 火附着 +10%。 */
    public static final RegistryObject<MobEffect> FIRE_ASPECT = fireBonus("fire_aspect", 0.10D);
    /** 初级燃烧 +25%，燃血 1%/秒。 */
    public static final RegistryObject<MobEffect> EMBER_BURN = fireBonus("ember_burn", 0.25D);
    /** 中级燃烧 +75%，燃血 2%/秒，15 秒内死亡可原地复活。 */
    public static final RegistryObject<MobEffect> BLAZE_BURN = fireBonus("blaze_burn", 0.75D);
    /** 高级燃烧 +150%，燃血 3%/秒，20 秒内死亡可原地复活。 */
    public static final RegistryObject<MobEffect> INFERNO_BURN = fireBonus("inferno_burn", 1.50D);
    /** 完全燃烧 +200%，血变 1 + 无敌，结束回半血，不燃血。 */
    public static final RegistryObject<MobEffect> TOTAL_BURN = fireBonus("total_burn", 2.00D);

    private static RegistryObject<MobEffect> fireBonus(String name, double amount) {
        return FIRE_EFFECTS.register(name, () -> new FireBonusEffect(amount));
    }

    private TNFireMechanics() {
    }

    /** 由 {@link TNEffects#register} 调用（模组构造期注册，不能晚）。 */
    public static void register(IEventBus modEventBus) {
        FIRE_EFFECTS.register(modEventBus);
    }

    /** 只加 spell_power:fire 的增益效果（属性在 spell_power 存在时才有）。 */
    private static final class FireBonusEffect extends MobEffect {
        FireBonusEffect(double amount) {
            super(MobEffectCategory.BENEFICIAL, COLOR_FIRE);
            Attribute fire = ForgeRegistries.ATTRIBUTES.getValue(FIRE_ATTRIBUTE);
            if (fire != null) {
                addAttributeModifier(fire,
                        UUID.nameUUIDFromBytes(("tnc:fire:" + amount).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),
                        amount, AttributeModifier.Operation.MULTIPLY_BASE);
            }
        }
    }

    // ---------------- 数值 ----------------

    /** 燃血：每秒扣最大生命的百分之几（0 = 不燃血）。 */
    private static double burnPercentPerSecond(ServerPlayer player) {
        if (has(player, TOTAL_BURN) || has(player, FIRE_ASPECT)) {
            return 0.0D;                    // 完全燃烧已经把血烧光了；火附着不燃血
        }
        if (has(player, INFERNO_BURN)) {
            return 0.03D;
        }
        if (has(player, BLAZE_BURN)) {
            return 0.02D;
        }
        if (has(player, EMBER_BURN)) {
            return 0.01D;
        }
        return 0.0D;
    }

    /** 自爆：扣自己最大生命的 10%（会致死）。 */
    private static final double SELF_DESTRUCT_COST = 0.10D;

    /** 完全燃烧：结束时回多少血。 */
    private static final double TOTAL_BURN_END_HEALTH_FRACTION = 0.5D;

    // ---------------- 机制 ----------------

    /** 施法瞬间的反应（由 SPELL_CAST 钩子转调）。 */
    public static void onSpellCast(ServerPlayer player, ResourceLocation spellId, MagicStoneData data) {
        if (!spellId.getPath().equals("self_destruct")) {
            return;
        }
        float cost = (float) (player.getMaxHealth() * SELF_DESTRUCT_COST);
        player.hurt(player.damageSources().magic(), cost);   // 会死，用户确认过
    }

    /** 每 tick：燃血 + 完全燃烧的血量/无敌维护。 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.level().isClientSide()) {
            return;
        }
        long time = player.level().getGameTime();

        // 完全燃烧：血锁 1 + 无敌；效果没了就回半血、解除无敌
        if (has(player, TOTAL_BURN)) {
            if (player.getHealth() > 1.0F) {
                player.setHealth(1.0F);
            }
            player.setInvulnerable(true);
            TOTAL_BURN_TRACKER.put(player.getUUID(), time);
            return;
        }
        Long wasBurning = TOTAL_BURN_TRACKER.remove(player.getUUID());
        if (wasBurning != null) {
            player.setInvulnerable(false);
            player.setHealth((float) (player.getMaxHealth() * TOTAL_BURN_END_HEALTH_FRACTION));
        }

        // 燃血：每秒一次，按最大生命百分比，永不致死
        double percent = burnPercentPerSecond(player);
        if (percent <= 0.0D || time % 20 != 0) {
            return;
        }
        float drain = (float) (player.getMaxHealth() * percent);
        float next = player.getHealth() - drain;
        player.setHealth(Math.max(1.0F, next));      // 保底 1 点：燃血不会致死
    }

    /** 完全燃烧期间被打上标记（用来判断"效果刚结束"）。 */
    private static final java.util.Map<UUID, Long> TOTAL_BURN_TRACKER =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 原地复活：中级/高级燃烧期间死亡 → 取消死亡、回满血、效果结束。
     *
     * <p>只在<b>还有那两种效果时</b>触发，所以"15 秒 / 20 秒内"这个窗口就是效果的持续时间。
     */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        RegistryObject<MobEffect> revive = has(player, INFERNO_BURN) ? INFERNO_BURN
                : has(player, BLAZE_BURN) ? BLAZE_BURN : null;
        if (revive == null) {
            return;
        }
        event.setCanceled(true);                     // 不死
        player.setHealth(player.getMaxHealth());     // 回满
        player.removeEffect(revive.get());           // 一次性的
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "§6[TN-C] §r燃烧未熄 —— 原地复活"), true);
    }

    private static boolean has(ServerPlayer player, RegistryObject<MobEffect> effect) {
        return effect.isPresent() && player.hasEffect(effect.get());
    }

    /** 给自检/命令看：这个玩家现在燃血每秒掉多少百分比。 */
    public static double burnRateFor(ServerPlayer player) {
        return burnPercentPerSecond(player);
    }

    /** 效果是否在身（命令/调试用）。 */
    public static boolean hasEffect(ServerPlayer player, String path) {
        for (RegistryObject<MobEffect> effect : FIRE_EFFECTS.getEntries()) {
            if (effect.getId().getPath().equals(path) && has(player, effect)) {
                return true;
            }
        }
        return false;
    }

    /** 供自检引用：5 个效果都注册上了吗。 */
    public static int registeredCount() {
        return FIRE_EFFECTS.getEntries().size();
    }
}
