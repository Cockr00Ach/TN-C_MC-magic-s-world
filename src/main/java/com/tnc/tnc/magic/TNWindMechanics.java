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
