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

    /**
     * ★ 暗龙链 t2「暗龙鳞甲」：移动速度 <b>+20%</b> ✓（减伤 50% 在受伤事件里 ✓）
     * ＋ 暗属性伤害 <b>+30%</b> ✓（暗在引擎里映射 {@code spell_power:soul} ✓，
     * 照 {@link #DARK_POWER} 的写法，没装时只加速度、不崩 ✓）。
     *
     * <p>它就是上面那个光龙鳞甲的**暗属性镜像** ✓ ——
     * 作者 2026-10-02："复制一下光龙，生成一个暗龙" ✓。
     */
    public static final RegistryObject<MobEffect> DARK_DRAGON_SCALES =
            EFFECTS.register("dark_dragon_scales", () -> new MobEffect(
                    MobEffectCategory.BENEFICIAL, 0x6A2AB0) {
                {
                    addAttributeModifier(Attributes.MOVEMENT_SPEED,
                            uuidFor("tnc:dark_dragon_scales_speed"), 0.20D,
                            AttributeModifier.Operation.MULTIPLY_BASE);
                    Attribute soul = ForgeRegistries.ATTRIBUTES.getValue(
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                    "spell_power", "soul"));
                    if (soul != null) {
                        addAttributeModifier(soul, uuidFor("tnc:dark_dragon_scales_soul"),
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

    /**
     * <b>黑雾 · 侵蚀</b> —— 暗系第四条链（黑雾 / 领域）挂在敌人身上的减益 ✓
     * （作者 2026-10-09："你全做吧"）。
     *
     * <h2>为什么不再借 {@code tnc:gale_slow}</h2>
     * 那五个黑雾法术原来挂的是<b>风系</b>的 {@code gale_slow} ✗ —— 暗雾用风的效果，
     * 图标/颜色/名字全是风的味道，而且它只是"减速"，和"黑雾侵蚀"没关系。
     * 现在换成自己的：<b>减速 + 虚弱</b>（移动速度与攻击力一起降）✓
     * 走原版那套"amplifier 递增"就够（amp 0/1/2 = 缓 0/I/II 阶）✓，不必写五个效果类。
     *
     * <p>颜色用暗紫（{@code 0x4A2C6E}）⇒ 它同时决定 <b>HUD 图标底色</b>和身上那圈粒子颜色 ✓ ——
     * 一眼能认出"这是暗的"，和雷系的青色、风系的浅绿区分开 ✓。
     */
    public static final RegistryObject<MobEffect> DARK_VEIL = EFFECTS.register("dark_veil",
            () -> {
                // 1.20.1 的 MobEffect 构造器是 protected、也没有 setCategory ✗
                // ⇒ 走我们自己的子类，把 category 从构造器喂进去 ✓
                AttributeBuff effect = new AttributeBuff(MobEffectCategory.HARMFUL, 0x4A2C6E,
                        Attributes.MOVEMENT_SPEED, -0.15D, AttributeModifier.Operation.MULTIPLY_TOTAL);
                effect.addAttributeModifier(Attributes.ATTACK_DAMAGE,
                        uuidFor("tnc:dark_veil_weak"), -0.10D, AttributeModifier.Operation.MULTIPLY_TOTAL);
                return effect;
            });

    // ------------------------------------------------------------------
    //  暗系第二条链「以伤换伤 · 献祭」（作者 2026-10-09："你还可以再优化一下以伤换伤"）
    //
    //  为什么这五个效果必须存在（链条的核心一直缺一半 ✗）：
    //  这条链原来是**借火系燃烧线的五个效果**当"代价"（`tnc:fire_aspect` … `tnc:total_burn`），
    //  而那五个效果加的是 `spell_power:fire` ✗ —— 暗系法术吃不到，
    //  于是"烧血"只剩纯亏：扣自己的血、换一个对暗系毫无用处的火系加成 ✗✗。
    //  ⇒ 现在有暗系自己的五个效果（加 `spell_power:soul` ✓），
    //    "燃血"这套代价由 `magic/TNDarkSacrificeMechanics` 按同一条规则结算 ✓。
    //
    //  加成走原版"amplifier 递增"：MobEffectInstance 按 (amplifier + 1) 放大，
    //  所以一个效果覆盖 +10% / +20% / +30% …（JSON 里写 amplifier 0..3）✓。
    // ------------------------------------------------------------------

    /** 火系燃烧线用的颜色（和 `TNFireMechanics.COLOR_FIRE` 一致）。 */
    private static final int COLOR_FIRE = 0xE06010;
    /** 暗系献祭线用的颜色（暗红紫：血 + 暗 ✓）。 */
    private static final int COLOR_BLOOD = 0x8E1E3C;

    /** 暗 · 血之烙印：+10% 暗属性强度（不燃血 → 安全档）。 */
    public static final RegistryObject<MobEffect> BLOOD_MARK =
            darkScales("blood_mark", 0.10D, false);
    /** 暗 · 燃血：+25% 暗属性强度，燃血 1%/秒。 */
    public static final RegistryObject<MobEffect> BLOOD_BURN =
            darkScales("blood_burn", 0.25D, true);
    /** 暗 · 献祭：+50% 暗属性强度，燃血 2%/秒，死亡可原地复活。 */
    public static final RegistryObject<MobEffect> BLOOD_SACRIFICE =
            darkScales("blood_sacrifice", 0.50D, true);
    /** 暗 · 夺舍：+100% 暗属性强度，燃血 3%/秒，死亡可原地复活。 */
    public static final RegistryObject<MobEffect> BLOOD_POSSESS =
            darkScales("blood_possess", 1.00D, true);
    /** 暗 · 我为神：+200% 暗属性强度，血锁 1 ＋ 无敌，结束回半血。 */
    public static final RegistryObject<MobEffect> BLOOD_GOD =
            darkScales("blood_god", 2.00D, false);

    /**
     * 暗系献祭效果工厂：挂一个 {@code spell_power:soul} 修饰符 ✓。
     *
     * <p>没装 spell_power 时不挂任何属性 ⇒ 退化成"纯标记"，至少不崩 ✓。
     * 颜色决定 HUD 图标底色与身上粒子色（暗红紫 = 血与暗 ✓）。
     */
    private static RegistryObject<MobEffect> darkScales(String name, double amount, boolean burn) {
        return EFFECTS.register(name, () -> new DarkScalesEffect(amount, burn));
    }

    /** 暗系献祭标记 + {@code spell_power:soul} 加成。 */
    public static final class DarkScalesEffect extends MobEffect {

        private final boolean burning;

        DarkScalesEffect(double amount, boolean burning) {
            super(MobEffectCategory.BENEFICIAL, COLOR_BLOOD);
            this.burning = burning;
            Attribute soul = ForgeRegistries.ATTRIBUTES.getValue(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("spell_power", "soul"));
            if (soul != null) {
                addAttributeModifier(soul, uuidFor("tnc:soul:" + amount), amount,
                        AttributeModifier.Operation.MULTIPLY_BASE);
            }
        }

        /** 这个档位是不是"持续燃血"的那种（我为神不是 —— 它把血锁在 1 ✓）。 */
        public boolean burning() {
            return burning;
        }
    }

    /**
     * 火系燃烧线的五个效果工厂 —— <b>给 {@link TNFireMechanics} 用</b> ✓。
     *
     * <p>原来那个私有嵌套类只加 {@code spell_power:fire}；现在把"加哪个学派"参数化，
     * 暗系那五个（{@link #BLOOD_MARK} 等）走同一个实现，只是换成 {@code soul} ✓。
     * （两条链因此**不会互相影响**：燃血判定看的是各自那组效果 ✓。）
     */
    static RegistryObject<MobEffect> fireScales(String name, double amount) {
        return EFFECTS.register(name, () -> new ScalesEffect(COLOR_FIRE, "fire", amount));
    }

    /** 加某一个学派法术强度的通用实现（`spell_power:<school>` 必须存在）。 */
    public static final class ScalesEffect extends MobEffect {

        ScalesEffect(int color, String school, double amount) {
            super(MobEffectCategory.BENEFICIAL, color);
            Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("spell_power", school));
            if (attribute != null) {
                addAttributeModifier(attribute, uuidFor("tnc:" + school + ":" + amount), amount,
                        AttributeModifier.Operation.MULTIPLY_BASE);
            }
        }
    }

    /** 给自检/命令看：暗系献祭那五个效果都注册上了吗。 */
    public static int darkScalesCount() {
        return 5;
    }

    private TNEffects() {
    }

    /** 带一个属性修饰符的效果（默认增益；减益走下面那个带 category 的构造器 ✓）。 */
    private static class AttributeBuff extends MobEffect {

        AttributeBuff(int color, Attribute attribute, double amount, AttributeModifier.Operation op) {
            this(MobEffectCategory.BENEFICIAL, color, attribute, amount, op);
        }

        /**
         * ★ 带 category 的版本 —— 1.20.1 的 {@code MobEffect(MobEffectCategory, int)} 是
         * <b>protected</b> ✗、也没有 {@code setCategory}，所以"减益"只能从构造器传进来 ✓
         * （黑雾的 {@code tnc:dark_veil} 要用它；HUD 上减益是红框、增益是蓝框 ✓）。
         */
        AttributeBuff(MobEffectCategory category, int color, Attribute attribute,
                      double amount, AttributeModifier.Operation op) {
            super(category, color);
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
        // ★ 黑雾链的事件订阅挂在**引擎自己的**事件上（不是 Forge 总线 ✗），
        //   在 DarkFogMechanics 的静态块里注册 ⇒ 这里显式碰一下它的静态字段，
        //   保证类在"任何施法之前"就被加载 ✓（否则第一次放黑雾会没反应 ✗）。
        DarkFogMechanics.ensureLoaded();
        // 火系火球链的「焚身」（命中灼伤）—— 自有实体那一套，见 magic/fire/TNScorch
        com.tnc.tnc.magic.fire.TNScorch.register(modEventBus);
    }
}
