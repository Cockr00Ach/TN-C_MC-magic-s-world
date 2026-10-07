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
    // 不会被攻击、不会寻路。所以五档的成长**目前只能靠"球数 + 单次伤害"**表达 ✓ ——
    // 一档一行写在 {@link #orbTier} 的那张表里 ✓。
    //
    // ⚠️ 2026-10-07 修：在这之前 4 级和 5 级跟 3 级共用同一个标记 ⇒
    //   4 级（"三个风灵"）实际只是 3 级换了个更短的时长 = **严格更弱** ✗，
    //   5 级则是 3 级的逐字复制 ✗。现在三级各有自己的标记，阶梯单调 ✓。

    /**
     * 风神降临（5 级）专属标记：只由那一个法术发出。
     *
     * <p>为什么需要它：5 级的"技能无冷却 / 蓝耗减半"只该属于这一级，
     * 而速度/风伤那几个效果是 4 级和 5 级共用的 —— 拿它们当判断依据会让
     * 4 级也吃到神级待遇（当前就是这样，等这个标记接上去就修正）。 *
     */
    public static final RegistryObject<MobEffect> WIND_GOD = WIND_EFFECTS.register(
            "wind_god", MarkerEffect::new);

    /**
     * 暗系召唤的标记：暗系的"召唤"链**曾经**复用绕身球机制，用一个标记让
     * {@code tickOrbs} 把粒子换成暗影感的（而不是复制一整套逻辑）。
     *
     * <p>⚠️ <b>2026-10-10 核实：这个标记现在没有任何施加方，那段分支是死代码</b> ✗ ——
     * 全项目（法术 JSON ＋ kubejs 脚本 ＋ Java）里搜不到任何一处写 {@code tnc:dark_orb}；
     * 暗系第 3 条链已经改成**真召唤实体**了（{@code dark/TNDarkSummonChain} ＋
     * {@code TNDarkSummonEntity}，五档五个实体），根本不再走绕身球 ✓。
     *
     * <p><b>故意保留</b>（而不是删掉）：它的注册是无害的（没有任何东西施加它 ⇒ 永远不生效 ✓），
     * 而它是"想让某条链借用绕身球但换粒子"的现成扩展点 ✓；删它要连带动暗系那边的文件，
     * 而暗系的边界属于别人（"别把暗系带崩" ✗）⇒ 留在这里 + 写清楚它现在是死的，成本最低 ✓。
     * 真要用它：让某条链的法术 JSON 挂上 {@code tnc:dark_orb} 即可，分支立刻活过来 ✓。
     */
    public static final RegistryObject<MobEffect> DARK_ORB = WIND_EFFECTS.register(
            "dark_orb", MarkerEffect::new);

    /** 三个风球。 */
    public static final RegistryObject<MobEffect> WIND_ORB_THREE = WIND_EFFECTS.register(            "wind_orb_three", MarkerEffect::new);
    /** 五个风球。 */
    public static final RegistryObject<MobEffect> WIND_ORB_FIVE = WIND_EFFECTS.register(
            "wind_orb_five", MarkerEffect::new);
    /** 风灵（更强的一档：球更多、打得更疼）。 */
    public static final RegistryObject<MobEffect> WIND_SPIRIT = WIND_EFFECTS.register(
            "wind_spirit", MarkerEffect::new);

    /**
     * 三个风灵（4 级，<b>2026-10-07 补</b>）。
     *
     * <p>为什么必须给它一个独立标记：4 级原来和 3 级**共用** {@link #WIND_SPIRIT} ✗ ——
     * 而 {@code orbCount()} 对那个标记一律返回 5 ⇒ 4 级「召唤三个风灵」在实装里
     * **和 3 级是同一个东西**，而且时长 15s 比 3 级的 30s 更短、冷却还更长（35 对 25）⇒
     * **4 级在每一个维度上都严格劣于 3 级** ✗（这是真 bug，不是手感问题）。
     * 拆出独立标记后，档位表才表达得出"4 级比 3 级强" ✓。
     */
    public static final RegistryObject<MobEffect> WIND_SPIRIT_II = WIND_EFFECTS.register(
            "wind_spirit_ii", MarkerEffect::new);

    /**
     * 风灵之主（5 级，<b>2026-10-07 补</b>）。
     *
     * <p>原来 5 级是 3 级的**逐字复制**（同一个 {@code wind_spirit}、同样 30s）✗ ——
     * {@code SpellCatalog} 的注释自己就写着"5 用户还没定，先占位" ✓。
     * 这里先给它**自己的档位**（见 {@link #orbTier}），把阶梯修成单调递增的 ✓；
     * 真正的「风灵之约 / 合体」设计仍待落地（见 docs/spells 下的风系提案）✗。
     */
    public static final RegistryObject<MobEffect> WIND_SPIRIT_LORD = WIND_EFFECTS.register(
            "wind_spirit_lord", MarkerEffect::new);

    // ---------------- 档位表（唯一来源） ----------------

    /**
     * 一个档位：<b>档号</b> ＋ <b>几只风灵</b> ＋ <b>单次伤害倍率</b> ＋ <b>渲染缩放</b> ✓。
     *
     * <p>为什么是一张显式的表：原来档位散在几条 if 里 ✗，而 4/5 级跟 3 级共用同一个标记
     * ⇒ 那几条 if **根本表达不出"4 级更强"**，于是 4 级反而更弱（见 {@link #WIND_SPIRIT_II}）✗。
     * 现在一档一行 ✓ —— 而且**实体、渲染器、伤害**三边都读这一张表 ✓
     * （{@code magic/TNWindSpiritEntity} 与 {@code client/TNWindSpiritRenderer} 都调这里的静态取值 ✓）。
     */
    public record OrbTier(int tier, int orbs, float damageMultiplier, double scale) {
    }

    /**
     * 五档的数值表 ✓（改手感只改这里）。
     *
     * <p>★ 2026-10-10 作者："风灵链怎么五个全是放一个围绕着玩家的小风球啊，**风灵呢**" ✗
     * ⇒ 数量轴按**名字**重新对齐：以前 t3/t4/t5 一律 5 只，名字里的"一只风灵 / 三个风灵 /
     * 风灵之主"根本没体现 ✗，现在就是 1 / 3 / 5 ✓。
     *
     * <table border="1">
     *   <tr><th>档</th><th>法术</th><th>标记</th><th>风灵</th><th>倍率</th><th>单次伤害</th><th>缩放</th></tr>
     *   <tr><td>t1</td><td>召唤风球</td><td>{@code wind_orb_three}</td><td>3</td><td>×1.0</td><td>4.0</td><td>0.62</td></tr>
     *   <tr><td>t2</td><td>召唤五个风球</td><td>{@code wind_orb_five}</td><td>5</td><td>×1.0</td><td>4.0</td><td>0.72</td></tr>
     *   <tr><td>t3</td><td>召唤风灵</td><td>{@code wind_spirit}</td><td><b>1</b></td><td>×2.2</td><td>8.8</td><td>1.05</td></tr>
     *   <tr><td>t4</td><td>召唤三个风灵</td><td>{@code wind_spirit_ii}</td><td><b>3</b></td><td>×2.2</td><td>8.8</td><td>1.05</td></tr>
     *   <tr><td>t5</td><td>风灵之主</td><td>{@code wind_spirit_lord}</td><td>5</td><td>×2.8</td><td>11.2</td><td>1.30</td></tr>
     * </table>
     *
     * <p>★ 每只风灵**各打各的** ✓（{@code TNWindSpiritEntity#zap} 每只各自每 10 tick 打一次
     * 自己最近的敌人）⇒ "只数多"终于是真的"伤害更高" ✓
     * —— 旧版是"一轮只打一个然后 break" ✗，球数纯粹是装饰 ✗。
     */
    private static final OrbTier[] TIERS = {
            new OrbTier(1, 3, 1.0F, 0.62D),
            new OrbTier(2, 5, 1.0F, 0.72D),
            new OrbTier(3, 1, 2.2F, 1.05D),
            new OrbTier(4, 3, 2.2F, 1.05D),
            new OrbTier(5, 5, 2.8F, 1.30D),
    };

    /**
     * 当前生效的档位（从最高档往下认，认到第一个就返回）✓。
     *
     * <p>⚠️ 从最高档往下认是有意的：法术用 {@code apply_mode: SET} 各自设自己的标记，
     * 低级那次可能还没到期 ⇒ 只按"有没有某个标记"逐级判断会认错档 ✗。
     *
     * @return 一只都没有时返回 {@code null} ✓
     */
    private static OrbTier orbTier(ServerPlayer player) {
        if (has(player, WIND_SPIRIT_LORD)) {
            return TIERS[4];
        }
        if (has(player, WIND_SPIRIT_II)) {
            return TIERS[3];
        }
        if (has(player, WIND_SPIRIT)) {
            return TIERS[2];
        }
        if (has(player, WIND_ORB_FIVE)) {
            return TIERS[1];
        }
        if (has(player, WIND_ORB_THREE)) {
            return TIERS[0];
        }
        return null;
    }

    /** 现在有没有风灵在 ✓（实体每 tick 用它决定要不要退场）。 */
    public static boolean hasSpirits(ServerPlayer player) {
        return orbTier(player) != null;
    }

    /** 当前档号 1..5（0 = 没有）✓ —— 实体用它判断"我这一档还算不算数" ✓。 */
    public static int currentTier(ServerPlayer player) {
        OrbTier tier = orbTier(player);
        return tier == null ? 0 : tier.tier();
    }

    /** 某个档位该有几只风灵 ✓（维护方补数量用它）。 */
    public static int orbsForTier(int tier) {
        return TIERS[Math.max(1, Math.min(5, tier)) - 1].orbs();
    }

    /** 某个档位的伤害倍率 ✓（实体算伤害用它）。 */
    public static float damageMultiplierForTier(int tier) {
        return TIERS[Math.max(1, Math.min(5, tier)) - 1].damageMultiplier();
    }

    /** 某个档位的渲染缩放 ✓（客户端渲染器用它）。 */
    public static double scaleForTier(int tier) {
        return TIERS[Math.max(1, Math.min(5, tier)) - 1].scale();
    }

    private static boolean has(ServerPlayer player, RegistryObject<MobEffect> effect) {
        return effect.isPresent() && player.hasEffect(effect.get());
    }

    /**
     * ★ <b>维护场上的风灵实体</b>（"多退少补"）✓ —— 取代了原来那个"每 tick 画云粒子"的写法 ✗。
     *
     * <p>为什么逻辑这么短：<b>行为全在实体自己身上</b> ✓（绕圈 / 打人 / 退场都归
     * {@link TNWindSpiritEntity}）。这里只保证"数量对得上"：
     * <ol>
     *   <li>档位没了 ⇒ 把剩下的全收掉 ✓；</li>
     *   <li>档位变了 ⇒ 旧档的实体<b>自己会退场</b>（它在 {@code tick()} 里比对档号 ✓），这里不用管；</li>
     *   <li>数一数"当前档号 + 槽位"，缺哪个槽就补一只 ✓。</li>
     * </ol>
     *
     * <p>⚠️ 别把这段并回 {@code onPlayerTick} 的早退之后 ✗（那里状态没变就 return，会一 tick 都不执行）。
     */
    private static void tickSpirits(ServerPlayer player) {
        OrbTier tier = orbTier(player);
        java.util.List<TNWindSpiritEntity> mine = player.serverLevel().getEntitiesOfClass(
                TNWindSpiritEntity.class, player.getBoundingBox().inflate(24.0D),
                e -> e.isOwnedBy(player));
        if (tier == null) {
            for (TNWindSpiritEntity spirit : mine) {
                spirit.discard();
            }
            return;
        }
        int want = tier.orbs();
        // 只有"当前档号"的实体才算数（旧档的正在自己退场 ✓）
        java.util.List<TNWindSpiritEntity> current = new java.util.ArrayList<>();
        for (TNWindSpiritEntity spirit : mine) {
            if (spirit.tier() == tier.tier()) {
                current.add(spirit);
            }
        }
        if (current.size() > want) {
            for (int i = want; i < current.size(); i++) {
                current.get(i).discard();
            }
            current = current.subList(0, want);
        }
        if (TNOrbEntities.WIND_SPIRIT == null || !TNOrbEntities.WIND_SPIRIT.isPresent()) {
            return;                                   // 理论上不会；真发生了也别每 tick 刷空指针 ✗
        }
        for (int slot = 0; slot < want; slot++) {
            boolean present = false;
            for (TNWindSpiritEntity spirit : current) {
                if (spirit.slot() == slot) {
                    present = true;
                    break;
                }
            }
            if (present) {
                continue;
            }
            TNWindSpiritEntity spirit = TNOrbEntities.WIND_SPIRIT.get().create(player.serverLevel());
            if (spirit == null) {
                return;
            }
            spirit.bind(player, slot, tier.tier(), slot * 0.7D);
            player.serverLevel().addFreshEntity(spirit);
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
     * <p>判断顺序：永久飞行（链进度 ≥ 4）→ 风速链标记效果 → <b>光翼链的任一 buff</b> → 都没有就收回。
     * 收回只针对生存模式，创造/旁观不碰。
     *
     * <h2>★ 2026-10-01 事故：这一条把光翼链的飞行<b>每 tick 收走一次</b> ✗</h2>
     * 光翼链（{@code light/TNLightChainMechanics}）给玩家开的 {@code mayfly} 在这里被**无条件收掉** ✗ ——
     * 因为这里只认风速链的 {@code WIND_FLIGHT} ✗。日志实锤：作者放完「飞行」后
     * {@code TN-C/light: 光翼飞行 给上} **每 tick 打一次**（768 次/47 秒 ✗）——
     * 那就是"我刚给上、下一 tick 又被收走"的指纹 ✓，作者看到的就是"翅膀有了，但双击空格飞不起来" ✗。
     *
     * <p>所以这里必须把光翼 buff 也算进 {@code shouldFly} ✓ ——
     * <b>"谁能飞"这件事有且只有这一个裁决点</b> ✓（两个系统各自 set/clear 必然互相打架 ✗）。
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        // ★ 脚下的风环（作者 2026-10-10："释放风速之后玩家的脚下一直有风在绕，感觉踩在风上"）——
        //   ⚠️ 必须放在下面那几处**早退之前** ✗：状态没变时这里会 `return`，
        //   把每 tick 的视觉一起吃掉（这正是"加在末尾看不到效果"的经典坑 ✓）。
        tickFootWind(player, player.level().getGameTime());
        boolean creativeLike = player.isCreative() || player.isSpectator();
        boolean shouldFly = permanentFlight(player)
                || player.hasEffect(WIND_FLIGHT.get())
                || lightWings(player);                   // ★ 光翼链：任一档 buff 在身就允许飞 ✓
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

    // ---------------- 脚下的风环（"踩在风上"） ----------------

    /** 站在地上时的环：粒子数 / 半径（格）。 */
    private static final int FOOT_WIND_RING = 6;
    private static final double FOOT_WIND_RADIUS = 0.48D;
    /** 真飞起来时更密更宽（"风在托着你"要看得出来 ✓）。 */
    private static final int FOOT_WIND_RING_FLYING = 10;
    private static final double FOOT_WIND_RADIUS_FLYING = 0.62D;
    /** 每隔几 tick 补一道风痕（sweep）—— 只靠 cloud 会像烟，加它才像"风" ✓。 */
    private static final int FOOT_WIND_SWEEP_INTERVAL = 4;
    /** 旋转速度：每 tick 转多少弧度（小一点才像"绕"，大了像闪 ✓）。 */
    private static final double FOOT_WIND_SPIN = 0.18D;

    /**
     * ★ <b>风速链生效期间，脚下一直有一圈风在绕</b> ✓（作者 2026-10-10）：
     * 「风速不是后面可以飞吗，我希望释放风速之后玩家的脚下一直有风在绕，感觉踩在风上」。
     *
     * <h2>什么时候吹</h2>
     * 只要"风给了你飞行"就吹 ✓ —— 也就是 {@link #permanentFlight}（4 级起解锁即永久）
     * 或 {@code tnc:wind_flight} 标记在身（1~3 级的时限飞行）✓。
     * ⚠️ <b>光翼链的飞行不吹</b> ✗ —— 那是光耀链的翅膀，脚下起风会串味
     * （两者共用一个 {@code mayfly} 裁决点，见 {@link #onPlayerTick} 的事故注释 ✓）。
     *
     * <h2>为什么不放在客户端</h2>
     * 服务端每 tick {@code sendParticles} 是本项目已有的写法（绕身球 {@code tickOrbs} 就是这么做的 ✓），
     * 而客户端要判"永久飞行"得读魔法石进度（同步链更长 ✗）⇒ 跟现成的走 ✓。
     * 开销：站着 6 颗/tick、飞着 10 颗/tick，和绕身球同量级 ✓。
     */
    private static void tickFootWind(ServerPlayer player, long time) {
        if (!(permanentFlight(player) || has(player, WIND_FLIGHT))) {
            return;
        }
        boolean flying = player.getAbilities().flying;
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        int ring = flying ? FOOT_WIND_RING_FLYING : FOOT_WIND_RING;
        double radius = flying ? FOOT_WIND_RADIUS_FLYING : FOOT_WIND_RADIUS;
        double phase = time * FOOT_WIND_SPIN;
        for (int i = 0; i < ring; i++) {
            double a = phase + i * (Math.PI * 2.0D / ring);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                    player.getX() + Math.cos(a) * radius, player.getY() + 0.05D,
                    player.getZ() + Math.sin(a) * radius,
                    1, 0.0D, 0.01D, 0.0D, 0.01D);
        }
        if (time % FOOT_WIND_SWEEP_INTERVAL == 0) {
            // 横着的一道风痕（原版 sweep 粒子就是"挥砍的那道弧" ✓ 风刃同款）
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
                    player.getX(), player.getY() + 0.15D, player.getZ(),
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        if (flying) {
            // 飞着的时候再补一点向上的白点：风往上托 ✓
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    player.getX(), player.getY() - 0.05D, player.getZ(),
                    2, 0.26D, 0.02D, 0.26D, 0.02D);
        }
    }

    /**
     * 光耀链给的飞行在不在身 ✓（作者 2026-10-02：飞行并进光耀链了，光翼链整条删掉 ✓）。
     * 判据就是那个**光翼标记** ✓ —— 光耀 buff 在身时由 {@code TNLightChainMechanics.tickGraceFlight} 挂着 ✓。
     */
    private static boolean lightWings(ServerPlayer player) {
        return hasLightEffect(player, TNEffects.LIGHT_WINGS);
    }

    private static boolean hasLightEffect(ServerPlayer player,
                                          net.minecraftforge.registries.RegistryObject<
                                                  net.minecraft.world.effect.MobEffect> effect) {
        return effect.isPresent() && player.hasEffect(effect.get());
    }

    /** 链2：风灵实体的维护（另开一个 tick 处理，逻辑上互不影响）。 */
    @SubscribeEvent
    public static void onPlayerTickOrbs(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.level().isClientSide()) {
            return;
        }
        tickSpirits(player);
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
