package com.tnc.tnc.magic.fire;

import com.mojang.brigadier.context.CommandContext;
import com.tnc.tnc.TNMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 火系「<b>焚身</b>」：命中敌人后挂上的持续灼伤。
 *
 * <h2>规则（作者 2026-10-05 定稿）</h2>
 * <ul>
 *   <li>火系法术<b>命中敌人</b>时挂上，持续 5 秒，每秒跳一次</li>
 *   <li>每秒伤害 = <b>那一下的实际命中伤害</b> × 10%（所以大火球术的灼烧天然更强，不用另立数值表）</li>
 *   <li><b>不刷新</b>：身上已有焚身时，新的命中<b>整个跳过</b> —— 不重置时间、也不改数值，
 *       必须等它结束才能重新挂</li>
 *   <li><b>唯一的例外</b>：<b>II 级（重度）可以顶掉 I 级</b> —— 升级时<b>会</b>把时间刷新成
 *       II 级的 10 秒（作者 2026-10-05 确认）；反过来 I 级顶不掉 II 级</li>
 * </ul>
 *
 * <h2>为什么不用「挂在效果上的数值」</h2>
 * 原版 {@code MobEffectInstance} 只带 amplifier / duration，<b>放不下"这一发打了多少"</b>。
 * 而每个敌人身上的焚身数值都可能不同（同一发法术打不同目标、或不同法术打同一目标），
 * 所以数值存在服务端的 {@link #STATES} 里（和 {@code TNFireMechanics.TOTAL_BURN_TRACKER} 同一写法）。
 * 原版效果本体只负责<b>图标与被动的时长显示</b>。
 *
 * <h2>谁调用 {@link #apply}</h2>
 * 火系法术自己的实体会在命中时调它（火球链是自有实体，所以命中时 100% 知道
 * "是哪个法术、打了多少"，不存在靠时间窗猜法术的问题）。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class TNScorch {

    public static final DeferredRegister<MobEffect> SCORCH_EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, TNMod.MODID);

    /** 火系通用的橙红色（沿用 {@code TNFireMechanics} 的 COLOR_FIRE，保持一致）。 */
    private static final int COLOR_FIRE = 0xE06010;

    /** 焚身：有害效果，图标/粒子用火系橙红。 */
    public static final RegistryObject<MobEffect> BURNING_BODY =
            SCORCH_EFFECTS.register("burning_body",
                    () -> new MobEffect(MobEffectCategory.HARMFUL, COLOR_FIRE) {});

    // ---------------- 数值 ----------------

    /** I 级：每秒 = 命中伤害的 10%。 */
    public static final double NORMAL_PERCENT = 0.10D;
    /** II 级（重度）：每秒 = 命中伤害的 20%。 */
    public static final double HEAVY_PERCENT = 0.20D;
    /** I 级持续 5 秒。 */
    public static final int NORMAL_DURATION_TICKS = 100;
    /** II 级持续 10 秒（炎葬给的就是这一档）。 */
    public static final int HEAVY_DURATION_TICKS = 200;
    /** 每秒跳一次。 */
    public static final int TICK_INTERVAL = 20;

    // ---------------- 纯规则（可被自检直接断言，不需要实体） ----------------

    /** 来料与现状比对之后该怎么办。 */
    public enum Decision {
        /** 身上没有（或已结束）→ 正常挂上 */
        APPLY,
        /** 身上是 I 级、来料是 II 级 → 升级并刷新时间 */
        UPGRADE,
        /** 其余一律跳过：同级不刷新、低级别想顶高级也不行 */
        SKIP
    }

    /**
     * <b>焚身的唯一判定口径</b>（纯函数，便于自检）。
     *
     * @param active       身上是否还有未结束的焚身
     * @param existingHeavy 身上的那一层是不是 II 级
     * @param incomingHeavy 这一发来的是不是 II 级
     */
    public static Decision decide(boolean active, boolean existingHeavy, boolean incomingHeavy) {
        if (!active) {
            return Decision.APPLY;
        }
        if (incomingHeavy && !existingHeavy) {
            return Decision.UPGRADE;
        }
        return Decision.SKIP;
    }

    /** 这一层该每秒掉多少（绝对值，不是百分比）。 */
    public static float perSecond(float hitDamage, boolean heavy) {
        if (!(hitDamage > 0.0F)) {
            return 0.0F;
        }
        return (float) (hitDamage * (heavy ? HEAVY_PERCENT : NORMAL_PERCENT));
    }

    /** 这一层该持续多久。 */
    public static int durationTicks(boolean heavy) {
        return heavy ? HEAVY_DURATION_TICKS : NORMAL_DURATION_TICKS;
    }

    // ---------------- 每个敌人各自的状态 ----------------

    /**
     * 一个敌人身上的焚身状态。
     *
     * @param caster    挂上它的玩家（可能已下线/换维度，取不到就退回无主伤害）
     * @param perSecond 每秒伤害（绝对值）
     * @param heavy     是不是 II 级
     * @param endsAt    结束时刻（{@code level.getGameTime()} 口径）
     */
    private record State(UUID caster, float perSecond, boolean heavy, long endsAt) {
    }

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    // ---------------- 对外入口 ----------------

    /**
     * 命中时挂焚身。<b>不刷新</b>：已有同级或更高时直接返回 false，不动任何数据。
     *
     * @param victim    被打中的生物
     * @param caster    施法者（可为 null，只影响伤害归属/击杀提示）
     * @param hitDamage 这一下的<b>实际命中伤害</b>（焚身的基数）
     * @param heavy     是不是 II 级（重度）
     * @return 数据真的被改了才返回 true（挂上 或 升级）
     */
    public static boolean apply(LivingEntity victim, ServerPlayer caster, float hitDamage, boolean heavy) {
        if (victim == null || victim.level().isClientSide() || !victim.isAlive()) {
            return false;
        }
        float dps = perSecond(hitDamage, heavy);
        if (dps <= 0.0F) {
            return false;
        }
        long now = victim.level().getGameTime();
        State old = STATES.get(victim.getUUID());
        boolean active = old != null && old.endsAt() > now && victim.hasEffect(BURNING_BODY.get());
        boolean existingHeavy = active && old.heavy();

        Decision decision = decide(active, existingHeavy, heavy);
        if (decision == Decision.SKIP) {
            return false;                       // 不刷新：时间、数值、caster 全都不动
        }

        int duration = durationTicks(heavy);
        // amplifier 0 = I 级、1 = II 级：原版 HUD 会显示成「焚身」「焚身 II」，
        // 也是"II 级能顶掉 I 级"能生效的原因（原版 addEffect 只允许更高 amplifier 覆盖）
        victim.addEffect(new MobEffectInstance(BURNING_BODY.get(), duration, heavy ? 1 : 0,
                false, true, true));
        // 升级时要**换掉**旧的状态（数值变大、时间刷新），不能沿用旧的
        STATES.put(victim.getUUID(), new State(caster == null ? null : caster.getUUID(),
                dps, heavy, now + duration));
        return true;
    }

    // ---------------- 每秒结算 ----------------

    @SubscribeEvent
    public static void onLivingTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        State state = STATES.get(victim.getUUID());
        if (state == null) {
            return;
        }
        long now = victim.level().getGameTime();
        // 到点 / 死了 / 效果被强行清掉（喝牛奶、死亡清除）→ 收摊
        if (now >= state.endsAt() || !victim.isAlive() || !victim.hasEffect(BURNING_BODY.get())) {
            STATES.remove(victim.getUUID());
            return;
        }
        if (now % TICK_INTERVAL != 0) {
            return;
        }
        // ⚠️ 必须先清无敌帧：原版受伤后 invulnerableTime = 20，正好等于我们的跳动间隔，
        //    不清理的话第二跳起就可能被吞掉（DOT 的老坑）
        victim.invulnerableTime = 0;
        victim.hurt(damageSourceFor(victim, state), state.perSecond());
    }

    /** 尽量把伤害算在施法者头上（击杀提示/统计），取不到就用无主魔法伤害。 */
    private static DamageSource damageSourceFor(LivingEntity victim, State state) {
        if (state.caster() != null) {
            MinecraftServer server = victim.getServer();
            if (server != null) {
                ServerPlayer caster = server.getPlayerList().getPlayer(state.caster());
                if (caster != null) {
                    return victim.damageSources().indirectMagic(caster, caster);
                }
            }
        }
        return victim.damageSources().magic();
    }

    /** 死了就把状态清掉，免得 UUID 复用或内存积压。 */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    /** 玩家退出时顺手清掉他挂过的状态（伤害归属已经没意义了）。 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        long now = player.level().getGameTime();
        if (now % 200 != 0) {
            return;
        }
        STATES.entrySet().removeIf(e -> e.getValue().endsAt() <= now);
    }

    // ---------------- 调试/自检 ----------------

    /** 现在有几个敌人身上带着焚身（命令/自检用）。 */
    public static int activeCount() {
        return STATES.size();
    }

    /** 某个人身上焚身每秒掉多少（命令/自检用；没有就是 0）。 */
    public static float perSecondOf(LivingEntity victim) {
        State state = victim == null ? null : STATES.get(victim.getUUID());
        return state == null ? 0.0F : state.perSecond();
    }

    /**
     * 纯规则自检 —— 四条规则的边界一次全测掉，不需要造实体。
     * 返回失败说明的列表，空列表 = 全过。
     */
    public static List<String> selfCheck() {
        List<String> bad = new ArrayList<>();
        if (decide(false, false, false) != Decision.APPLY) bad.add("空身 + I 级 应挂上");
        if (decide(false, false, true) != Decision.APPLY) bad.add("空身 + II 级 应挂上");
        if (decide(true, false, false) != Decision.SKIP) bad.add("I 级在身上 + I 级 应跳过(不刷新)");
        if (decide(true, false, true) != Decision.UPGRADE) bad.add("I 级在身上 + II 级 应升级");
        if (decide(true, true, false) != Decision.SKIP) bad.add("II 级在身上 + I 级 应跳过(顶不掉)");
        if (decide(true, true, true) != Decision.SKIP) bad.add("II 级在身上 + II 级 应跳过(不刷新)");
        if (perSecond(12.0F, false) != 1.2F) bad.add("I 级 12 伤害应每秒 1.2");
        if (perSecond(12.0F, true) != 2.4F) bad.add("II 级 12 伤害应每秒 2.4");
        if (perSecond(0.0F, true) != 0.0F) bad.add("0 伤害不该挂");
        if (durationTicks(false) != 100 || durationTicks(true) != 200) bad.add("持续时间应为 5s / 10s");
        return bad;
    }

    // ---------------- 游戏内测试入口（仅 OP） ----------------

    /** 测试用的固定「命中伤害」，让每秒掉血是个好算的数（I 级 1.0/s、II 级 2.0/s）。 */
    private static final float TEST_HIT_DAMAGE = 10.0F;

    /**
     * {@code /tnc fire scorch [heavy]} —— 把焚身挂到最近的生物身上；
     * {@code /tnc fire rules} —— 跑纯规则自检。
     *
     * <p>为什么必须有这个入口：火球链改成自有实体之前，<b>没有任何法术会调 {@link #apply}</b>，
     * 所以不留手动入口的话，这条机制在游戏里根本没法验收三件最该验的事 ——
     * 「每秒真的在掉血」「同级不刷新」「II 级能顶掉 I 级」。
     * （和 {@code WaterTestCommand} 的 {@code /tnc water cast} 同一个用途。）
     */
    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tnc")
                .then(Commands.literal("fire")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.literal("scorch")
                                .executes(c -> scorchNearest(c, false))
                                .then(Commands.literal("heavy").executes(c -> scorchNearest(c, true))))
                        .then(Commands.literal("rules").executes(TNScorch::printRules))));
    }

    private static int scorchNearest(CommandContext<CommandSourceStack> ctx, boolean heavy) {
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (Exception notAPlayer) {
            return 0;
        }
        LivingEntity target = player.level()
                .getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(16.0D),
                        e -> e != player && e.isAlive())
                .stream()
                .min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(player)))
                .orElse(null);
        if (target == null) {
            ctx.getSource().sendFailure(Component.literal("§c[TN-C] 16 格内没有可以挂焚身的生物"));
            return 0;
        }
        String name = target.getName().getString();
        if (apply(target, player, TEST_HIT_DAMAGE, heavy)) {
            ctx.getSource().sendSuccess(() -> Component.literal("§6[TN-C] §r已给 §e" + name + "§r 挂上焚身"
                    + (heavy ? " §cII" : "")
                    + "：每秒 §e" + perSecond(TEST_HIT_DAMAGE, heavy) + "§r 点，持续 "
                    + (durationTicks(heavy) / 20) + " 秒（此刻身上共 " + activeCount() + " 个目标）"), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("§7[TN-C] " + name
                    + " 身上已有一层焚身 → 跳过：§7没刷新时间、也没改数值"), false);
        }
        return 1;
    }

    private static int printRules(CommandContext<CommandSourceStack> ctx) {
        List<String> bad = selfCheck();
        if (bad.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§a[TN-C] §r焚身规则自检：§e10/10 §r通过（挂上 / 同级不刷新 / II 顶 I / 数值 / 时长）"), false);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("§c[TN-C] 焚身规则自检失败 §e" + bad.size() + "§c 条："
                + String.join("；", bad)));
        return 0;
    }

    /** 由 {@link com.tnc.tnc.magic.TNEffects#register} 调用（模组构造期注册，不能晚）。 */
    public static void register(IEventBus modEventBus) {
        SCORCH_EFFECTS.register(modEventBus);
    }

    private TNScorch() {
    }
}
