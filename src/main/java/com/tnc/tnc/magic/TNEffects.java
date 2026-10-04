package com.tnc.tnc.magic;

import com.tnc.tnc.TNMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.UUID;

/**
 * TN-C 自己的状态效果（雷速 / 极速雷风 / 环绕雷球 / 闪电登神）。
 *
 * <h2>为什么要自己注册效果</h2>
 * 原版速度效果每级固定 +20%，拿不到我们要的 +25% / +70%。自定义效果可以在构造时
 * {@code addAttributeModifier} 精确指定数值；而且 {@code MobEffectInstance} 会把
 * 修饰符数值再乘 {@code (amplifier + 1)}，所以<b>一个效果就能覆盖同一类 buff 的多档强度</b>
 * （例如雷速 amp0 = +25%、amp1 = +50%）。
 *
 * <p>法术 JSON 里通过 {@code impact[].action.status_effect.effect_id = "tnc:xxx"} 引用它们，
 * 所以数据层不需要知道任何数值。
 *
 * <h2>不归这里管的部分</h2>
 * "环绕雷球的持续电击""极速雷风的雷电拖尾""闪电登神的无冷却""闪电降低冷却的回蓝"
 * 都是每 tick 或施法瞬间的<b>行为</b>，放在 {@link TnSpellMechanics} 里；
 * 这里只负责"挂一个带数值的效果"。
 */
public final class TNEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, TNMod.MODID);

    public static final RegistryObject<MobEffect> WATER_CAST = EFFECTS.register("water_cast",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x54CEF7) {});
    public static final RegistryObject<MobEffect> CHAOS_SILENCE = EFFECTS.register("chaos_silence",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0x992BDD) {});

    /**
     * 雷速线统一用的颜色 —— <b>2026-09-22 从紫改成雷的青色</b>。
     *
     * <p>这个颜色同时决定两件事：原版 HUD 里效果图标的底色 ✓，以及玩家身上那圈
     * 效果粒子的颜色 ✓（原版 {@code MobEffectInstance} 的可见粒子用它）。
     * 以前是 {@code 0x7C5CE0}（偏紫，和魔力条一套），作者要"雷的体现"后改成青白。
     */
    private static final int COLOR_LIGHTNING = 0x9FE8FF;

    /** 雷速：+25% 移速（amp1 = +50%，被"闪电降低冷却"复用）。 */
    public static final RegistryObject<MobEffect> LIGHTNING_HASTE =
            EFFECTS.register("lightning_haste", () -> new AttributeBuff(
                    COLOR_LIGHTNING, Attributes.MOVEMENT_SPEED, 0.25D,
                    AttributeModifier.Operation.MULTIPLY_BASE));

    /** 极速雷风：+70% 移速（拖尾伤害由机制层处理）。 */
    public static final RegistryObject<MobEffect> LIGHTNING_WIND =
            EFFECTS.register("lightning_wind", () -> new AttributeBuff(
                    0x7FE8FF, Attributes.MOVEMENT_SPEED, 0.70D,
                    AttributeModifier.Operation.MULTIPLY_BASE));

    /**
     * ★ 光翼标记（客户端拿它画玩家背后的光翼 ✓，逻辑在 {@code light/TNLightWingsEvents} ✓）。
     *
     * <p>★ 2026-10-02：原来那条"光翼链"（light_flight / light_swift_flight / light_wingspan）
     * 被作者要求**删掉、并进光耀链** ✗ ⇒ 现在这个标记由**光耀 buff**驱动：
     * 身上有光耀任一档 buff 就挂着它 + 给 {@code mayfly} ✓（见
     * {@code TNLightChainMechanics.tickGraceFlight} ✓）。**这个标记本身保留** ✓
     * —— 翅膀模型/贴图/渲染器都还在用 ✓。
     */
    public static final RegistryObject<MobEffect> LIGHT_WINGS =
            EFFECTS.register("light_wings", () -> new MobEffect(
                    MobEffectCategory.BENEFICIAL, 0xFFE9A8) {
            });

    /**
     * ★★ 光系<b>第二条链</b>（治疗 + 减伤 + <b>飞行</b>，作者 2026-10-01 定、2026-10-02 并入飞行 ✓）
     * 的五个 buff ✓
     *
     * <p>减伤数值**不在效果里**做 ✗ —— 一律由 {@code armor/TNIronArmorEvents} 在
     * {@code LivingHurtEvent} 里按"取最高档、不相乘"统一结算 ✓（和铁甲 99% / 光龙鳞甲 50% 同一处 ✓）。
     * 这里只管"标记 + 时长"：t1 25% / t2 50% / t3 50% / t4 70% / t5 70% ✓。
     * 颜色统一走光系的暖白（{@code 0xFFF6DC}）✓。
     *
     * <p><b>飞行也挂在这五个 buff 上</b> ✓ —— 作者 2026-10-02："释放光耀法术就获得飞行" ✓：
     * 时长就是这里的 {@code buffSeconds}（12 / 14 / 16 / 18 / 20 秒 ✓）。
     */
    public static final RegistryObject<MobEffect> LIGHT_RADIANCE =
            EFFECTS.register("light_radiance", () -> new MobEffect(
                    MobEffectCategory.BENEFICIAL, 0xFFF6DC) {
            });
    public static final RegistryObject<MobEffect> LIGHT_HOLY =
            EFFECTS.register("light_holy", () -> new MobEffect(
                    MobEffectCategory.BENEFICIAL, 0xFFF6DC) {
            });
    public static final RegistryObject<MobEffect> LIGHT_DIVINE =
            EFFECTS.register("light_divine", () -> new MobEffect(
                    MobEffectCategory.BENEFICIAL, 0xFFF6DC) {
            });
    public static final RegistryObject<MobEffect> LIGHT_DESCENT =
            EFFECTS.register("light_descent", () -> new MobEffect(
                    MobEffectCategory.BENEFICIAL, 0xFFF6DC) {
            });
    public static final RegistryObject<MobEffect> LIGHT_MERCY =
            EFFECTS.register("light_mercy", () -> new MobEffect(
                    MobEffectCategory.BENEFICIAL, 0xFFF6DC) {
            });

    /**
     * 天使的悲悯 t5：范围内的怪物**停止攻击 5 秒** ✓（作者 2026-10-01："范围内的怪物停止攻击五秒钟"）。
     *
     * <p>只挂在怪物身上 ✓（HARMFUL，因为对它们是负面 ✓）；具体"停手"由
     * {@code light/TNLightChainMechanics} 每 tick 清目标 + 在 {@code LivingHurtEvent} 里取消伤害 ✓。
     */
    public static final RegistryObject<MobEffect> LIGHT_CALM =
            EFFECTS.register("light_calm", () -> new MobEffect(
                    MobEffectCategory.HARMFUL, 0xFFF6DC) {
            });

    /**
     * ★ 光龙链 t2「光龙鳞甲」：移动速度 <b>+20%</b> ✓（减伤 50% 在受伤事件里 ✓）
     * ＋ 光属性伤害 <b>+30%</b> ✓（照 {@link #DARK_POWER} 的写法，没装 spell_power 时只加速度、不崩 ✓）。
     *
     * <p>光龙链 = 作者 2026-10-02 新增的第五条光链 ✓（用的是那条东方光明龙 ✓）。
     */
    public static final RegistryObject<MobEffect> LIGHT_DRAGON_SCALES =
            EFFECTS.register("light_dragon_scales", () -> new MobEffect(
                    MobEffectCategory.BENEFICIAL, 0xFFE08A) {
                {
                    addAttributeModifier(Attributes.MOVEMENT_SPEED,
                            uuidFor("tnc:light_dragon_scales_speed"), 0.20D,
                            AttributeModifier.Operation.MULTIPLY_BASE);
                    Attribute healing = ForgeRegistries.ATTRIBUTES.getValue(
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                    "spell_power", "healing"));
                    if (healing != null) {
                        addAttributeModifier(healing, uuidFor("tnc:light_dragon_scales_heal"),
                                0.30D, AttributeModifier.Operation.MULTIPLY_BASE);
                    }
                }
            });
    /** 环绕雷球：本身不加属性，只是个"光环开着"的标记（电击逻辑在机制层）。 */
    public static final RegistryObject<MobEffect> ORBITING_THUNDER_ORB =
            EFFECTS.register("orbiting_thunder_orb", () -> new MobEffect(
                    MobEffectCategory.BENEFICIAL, COLOR_LIGHTNING) {
            });

    /**
     * 雷场（t2）的"场开着"标记 —— 劈人的逻辑在 {@code TnSpellMechanics} 里 ✓。
     *
     * <p>作者 2026-09-27："雷场就是有敌人，**每个敌人劈两下**" ✓ ——
     * 原来这个法术是引擎的 {@code CLOUD}（每 10 tick 自动打一次范围内所有人 ✗），
     * 现在改成 SELF ＋ 这个标记，由我们自己在第 20 / 60 tick 各劈一轮：出现在场里的敌人正好挨两下 ✓。
     */
    public static final RegistryObject<MobEffect> LIGHTNING_FIELD =
            EFFECTS.register("lightning_field", () -> new MobEffect(
                    MobEffectCategory.BENEFICIAL, COLOR_LIGHTNING) {
            });

    /**
     * 雷暴（t4）的"暴开着"标记 —— 作者："雷暴就是**一直劈到时间结束**" ✓。
     *
     * <p>机制层每 15 tick 挑范围内几个敌人各劈一道闪电，持续整个 buff 时间 ✓。
     */
    public static final RegistryObject<MobEffect> LIGHTNING_STORM =
            EFFECTS.register("lightning_storm", () -> new MobEffect(
                    MobEffectCategory.BENEFICIAL, COLOR_LIGHTNING) {
            });

    /**
     * 闪电登神：所有伤害提升（+30% 攻击力）+ 期间无冷却（无冷却在机制层）。
     *
     * <p>另外把 spell_power 的 12 个学派属性也一起加（如果装了 spell_power）——
     * 法术伤害走的是那些属性，只加攻击力的话"所有伤害"就只算近战了。
     */
    public static final RegistryObject<MobEffect> LIGHTNING_ASCENSION =
            EFFECTS.register("lightning_ascension", () -> AscensionEffect.create());

    /**
     * 暗系专属增伤：只加 {@code spell_power:soul}，每级 +50%。
     *
     * <p>为什么需要它：暗系的"以伤换伤 / 燃血 / 献祭 / 夺舍"要的是"烧血换暗属性强度"，
     * 而之前临时借用了火系那 5 个效果 —— 那些加的是 {@code spell_power:fire} ✗，
     * 暗系法术根本吃不到。有了这个，暗系那条线才算真的成立。
     *
     * <p>用原版那套"amplifier 递增"就够：MobEffectInstance 会按 (amplifier+1) 放大，
     * 所以一个效果就能覆盖 +50% / +100% / +150% / +200%（amp 0~3），
     * 不必像火系那样写 5 个效果类。
     */
    public static final RegistryObject<MobEffect> DARK_POWER = EFFECTS.register("dark_power",
            () -> {
                Attribute soul = ForgeRegistries.ATTRIBUTES.getValue(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("spell_power", "soul"));
                // 没装 spell_power 时退化成一个空标记，至少不崩
                if (soul == null) {
                    return new AttributeBuff(0x6A3FA0, Attributes.ATTACK_DAMAGE, 0.0D,
                            AttributeModifier.Operation.MULTIPLY_BASE);
                }
                return new AttributeBuff(0x6A3FA0, soul, 0.50D, AttributeModifier.Operation.MULTIPLY_BASE);
            });

    private TNEffects() {
    }

    /** 带一个属性修饰符的增益效果。 */
    private static class AttributeBuff extends MobEffect {

        AttributeBuff(int color, Attribute attribute, double amount, AttributeModifier.Operation op) {
            super(MobEffectCategory.BENEFICIAL, color);
            // 1.20.1 的 addAttributeModifier 收的是"UUID 字符串"
            // 固定 UUID：同一个效果重复上不该叠加出多个修饰符
            addAttributeModifier(attribute, uuidFor("tnc:" + attribute.getDescriptionId()), amount, op);
        }
    }

    /** 由名字派生一个稳定的 UUID 字符串。 */
    private static String uuidFor(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    /** 闪电登神：攻击力 + 法术强度一起加。 */
    private static final class AscensionEffect {

        static MobEffect create() {
            MobEffect effect = new AttributeBuff(0xCFF4FF, Attributes.ATTACK_DAMAGE, 0.30D,
                    AttributeModifier.Operation.MULTIPLY_BASE);
            // spell_power 的学派属性（软依赖：没有那个 mod 就跳过）
            for (String school : new String[]{"arcane", "fire", "frost", "healing", "lightning", "soul",
                    "air", "earth", "water", "physical_melee", "physical_ranged", "berserker_melee"}) {
                Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("spell_power", school));
                if (attribute != null) {
                    effect.addAttributeModifier(attribute, uuidFor("tnc:ascension:" + school),
                            0.30D, AttributeModifier.Operation.MULTIPLY_BASE);
                }
            }
            return effect;
        }
    }

    /** MOD 总线上的注册（由 {@link TNMod} 的构造器调用一次）。 */
    public static void register(net.minecraftforge.eventbus.api.IEventBus modEventBus) {
        EFFECTS.register(modEventBus);
        // 火系燃烧线那 5 个效果在自己的类里（TNFireMechanics），一并挂上：
        // DeferredRegister 必须在模组构造期挂到总线上，晚一步就注册不进去了
        TNFireMechanics.register(modEventBus);
        // 风系的效果与飞行机制同理
        TNWindMechanics.register(modEventBus);
    }
}
