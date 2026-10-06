package com.tnc.tnc.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.TNEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

/**
 * 雷速链的<b>第一人称表现</b>：速度越快，视野越拉 —— 让"快"这件事在屏幕上看得见。
 *
 * <h2>为什么这个最划算</h2>
 * 自身增益法术（SELF）在引擎里没有任何模型/投射物，光靠粒子很难传达"我现在很快"。
 * 而原版本身就用 FOV 表达速度（冲刺时会轻微拉伸视野）✓ ——
 * 我们只要在 Forge 的 {@link ComputeFovModifierEvent} 里再乘一个系数，
 * 不写 mixin、不画 UI，几行代码就有明显手感 ✓。
 *
 * <h2>数值</h2>
 * 取<b>最高的那一档</b>而不是相乘 ✗（否则三样叠加会拉到头晕）：
 * 雷速 +3%、极速雷风 +8%、登神 +5%。基础值来自 {@code event.getFovModifier()}
 * （原版已经把冲刺/弓弩蓄力等算进去了 ✓）。
 *
 * <p>挂在 <b>FORGE 总线 + Dist.CLIENT</b>：这是游戏事件、且只在客户端存在 ✓
 * （挂错总线会静默不生效，项目里踩过 —— 见 MagicStoneKeys 的注释）。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TNSpellClientVisuals {

    /** 雷速（t1）：轻微 */
    private static final float FOV_LIGHTNING_HASTE = 1.03F;
    /** 极速雷风（t3）：明显 */
    private static final float FOV_LIGHTNING_WIND = 1.08F;
    /** 闪电登神（t5）：中等（它已经有满身电弧了，FOV 不用太夸张） */
    private static final float FOV_LIGHTNING_ASCENSION = 1.05F;

    private TNSpellClientVisuals() {
    }

    /**
     * ★ <b>客户端冷却看门狗</b>（2026-09-29 作者："我有一直按着" —— 他没冤枉谁，这是真 bug ✓）
     *
     * <h2>问题（反编译 SpellEngine 0.15.12 实锤）</h2>
     * 让冷却递减的 {@code SpellCooldownManager.update()} <b>只在服务端分支被调用</b>：
     * {@code PlayerEntityMixin.tick_TAIL_SpellEngine} 里是
     * {@code if (!player.level().isClientSide) { … cooldownManager.update() … }}，
     * 而客户端那份 manager 的 {@code tick} 计数器<b>永远不增长</b> ✗。
     *
     * <p>偏偏客户端的施法流程每一 tick 都要问它：
     * {@code ClientPlayerEntityMixin.updateSpellCast()} → {@code isCoolingDown(id)} 为真就
     * <b>立刻取消自己的读条</b> ✗✗。于是：一个法术只要放过一次，客户端那边就永远"冷却中"，
     * 玩家按住不放也没用 —— 而服务端其实早就放行了 ✓（日志里只有 {@code gate ALLOW}、
     * 没有 {@code SPELL_CAST}，两次 ALLOW 只隔 0.1 秒，正是"读条刚起就被自己取消" ✗）。
     *
     * <p>解除本来靠服务端发的"冷却清零包"（Fabric 网络 `Packets$SpellCooldown`，duration 0），
     * 但那套网络在 <b>Connector</b> 环境下没有送到客户端 ✗。所以这里替引擎把客户端那份
     * 每 tick {@code update()} 一次（和引擎本该做的一模一样 ✓）。
     *
     * <p>安全性：{@code cooldownSet/cooldownCleared} 只在 owner 是 {@code ServerPlayer} 时才发包
     * （反编译确认 ✓）⇒ 在客户端调用<b>不会</b>反过来动服务端的冷却 ✓。引擎不在时静默跳过 ✓。
     */
    private static void tickClientCooldowns(net.minecraft.client.Minecraft minecraft) {
        try {
            if (!(minecraft.player instanceof net.spell_engine.internals.casting.SpellCasterEntity caster)) {
                return;
            }
            net.spell_engine.internals.SpellCooldownManager manager = caster.getCooldownManager();
            manager.update();
            // ★ 实测日志（作者 2026-09-29："释放一遍之后没有第二遍了"）：每 2 秒把我们法术的
            //   客户端冷却进度打一行 —— "进度在下降" ⇒ 替引擎 tick 生效了 ✓；
            //   "一直 1.00" ⇒ 还没生效 ✗（那就继续往下查别的取消条件）。
            if (minecraft.level != null && minecraft.level.getGameTime() % 40L == 0L) {
                StringBuilder sb = new StringBuilder();
                for (com.tnc.tnc.magic.SpellCatalog.Entry entry : com.tnc.tnc.magic.SpellCatalog.all()) {
                    float progress = manager.getCooldownProgress(entry.id(), 0.0F);
                    if (progress > 0.0F) {
                        if (sb.length() > 0) {
                            sb.append(' ');
                        }
                        sb.append(entry.id().getPath()).append('=')
                                .append(String.format(java.util.Locale.ROOT, "%.2f", progress));
                    }
                }
                if (sb.length() > 0) {
                    LOGGER.info("TN-C/cd: client cooldowns {}", sb);
                }
            }
        } catch (Throwable error) {
            if (COOLDOWN_ERROR_LOGGED.compareAndSet(false, true)) {
                LOGGER.warn("TN-C/cd: client cooldown tick failed: {}", error.toString());
            }
        }
    }

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("TN-C/cd");
    private static final java.util.concurrent.atomic.AtomicBoolean COOLDOWN_ERROR_LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean();

    // ---- 爆炸震屏（2026-09-22）：附近出现冲击波实体时给镜头加抖动 ----
    // 为什么走这条路：冲击波实体是服务端在爆炸点生成的 ✓ 而客户端本来就能看到它 ✓，
    // 所以"震屏"不需要任何网络包 —— 客户端自己每 tick 就近侦测一次即可 ✓。
    private static float shake = 0.0F;
    /** 白闪强度（0~1，每 tick ×0.72）✓ */
    private static float flash = 0.0F;
    /** 冲击波震屏：贴脸时多大（度）＋ 影响半径（格）✓ 作者要求"参考核弹" → 拉到 14 / 40 ✓ */
    private static final double SHAKE_MAX = 9.0D;
    private static final double SHAKE_RANGE = 40.0D;
    private static final net.minecraft.util.RandomSource SHAKE_RANDOM = net.minecraft.util.RandomSource.create();

    @SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
            return;
        }
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            shake = 0.0F;
            return;
        }
        tickClientCooldowns(minecraft);
        // 20 格内有冲击波 => 抖一下（越近越猛）
        for (com.tnc.tnc.magic.TNShockwaveEntity wave : minecraft.level.getEntitiesOfClass(
                com.tnc.tnc.magic.TNShockwaveEntity.class, minecraft.player.getBoundingBox().inflate(20.0D))) {
            double distance = wave.position().distanceTo(minecraft.player.position());
            // 2026-09-27 作者："爆炸特效感觉好拉，我感受不到那种爆炸的感觉，多加一点镜头晃动吧，参考一下核弹"
            //   → 震幅 4.0→14.0、作用半径 20→40 格、衰减 0.80→0.88（摇得更久 ✓）
            float strength = (float) (wave.shakeStrength() * Math.max(0.0D, 1.0D - distance / SHAKE_RANGE));
            if (strength > shake) {
                shake = strength;
            }
            // 同一发冲击波顺手点一下白闪（越近越白 ✓）——"核弹感"主要靠它 ＋ 抖 ✓
            float f = (float) Math.max(0.0D, 1.0D - distance / SHAKE_RANGE) * wave.flashStrength();
            if (f > flash) {
                flash = f;
            }
        }
        // 落雷（主链雷场/雷暴/雷击）：劈下来时也有轻微晃动 ✓（作者指定）
        // ★ 2026-09-29 修正（作者："怎么刚释放就震动"）：只在**刚落地那几 tick** 晃 ✓ ——
        //   以前只要附近有这个实体就晃 ✗，而三尊神是"悬停不落地"的 ⇒ 它们一生成就开始晃、
        //   一直晃 10 秒 ✗（球的 12.0 更夸张）。现在由实体同步的"落地时刻"决定 ✓
        //   （神永远不落地 ⇒ 永远不晃 ✓；球/雷只在砸到地上那一下晃 ✓）。
        if (minecraft.level != null && minecraft.player != null) {
            for (com.tnc.tnc.magic.TNLightningStrikeEntity bolt : minecraft.level.getEntitiesOfClass(
                    com.tnc.tnc.magic.TNLightningStrikeEntity.class,
                    minecraft.player.getBoundingBox().inflate(SHAKE_RANGE))) {
                int age = bolt.tickCount;
                if (!bolt.isShaking(age)) {
                    continue;                   // 还在天上飞 / 已经砸完 ⇒ 不晃 ✓
                }
                double d = bolt.position().distanceTo(minecraft.player.position());
                float s = (float) (bolt.shake() * Math.max(0.0D, 1.0D - d / SHAKE_RANGE));
                if (s > shake) {
                    shake = s;
                }
            }
        }
        // ★ 光系「光线」链：光柱刚出现那 8 tick 抖镜头 ✓（作者 2026-10-01："并且加上画面震动"✓）
        //   和落雷同一个套路 ✓：服务端把震幅同步到实体上 ✓，客户端就近侦测 ✓，不需要网络包 ✓。
        //   天降那种 45 格宽的大柱子震幅给到 8.5 度 ✓；细光线只有 0.5 度 ✓。
        for (com.tnc.tnc.light.TNLightBeamEntity beam : minecraft.level.getEntitiesOfClass(
                com.tnc.tnc.light.TNLightBeamEntity.class,
                minecraft.player.getBoundingBox().inflate(SHAKE_RANGE))) {
            if (!beam.isShaking(beam.tickCount)) {
                continue;                       // 只有"刚砸下来"那一下 ✓（活 6 秒全程抖会晃吐人 ✗）
            }
            double d = beam.position().distanceTo(minecraft.player.position());
            float s = (float) (beam.shake() * Math.max(0.0D, 1.0D - d / SHAKE_RANGE));
            if (s > shake) {
                shake = s;
            }
            float f = (float) Math.max(0.0D, 1.0D - d / SHAKE_RANGE) * 0.5F;
            if (f > flash) {
                flash = f;                      // 顺手一点点白闪 ✓（大光柱砸下来该亮一下 ✓）
            }
        }
        shake *= 0.88F;         // 衰减
        if (shake < 0.02F) {
            shake = 0.0F;
        }
        flash *= 0.72F;
        if (flash < 0.01F) {
            flash = 0.0F;
        }
        // ★ 黑夜之手 t3+ 的"震动"（作者 2026-10-09："t3 开始增加震动"）✓
        if (handShakeTicks > 0) {
            handShakeTicks--;
            handShake *= 0.94F;
            if (handShake < 0.02F) {
                handShake = 0.0F;
                handShakeTicks = 0;
            }
        } else {
            handShake = 0.0F;
        }
        // ★ 黑雾的"屏幕变黑"（作者 2026-10-09："敌人包括其他玩家触碰到这个雾……
        //   屏幕得给我黑了，t 级越高屏幕越黑"）✓
        if (net.minecraftforge.fml.ModList.get().isLoaded("spell_engine")) if (net.minecraftforge.fml.ModList.get().isLoaded("spell_engine")) if (net.minecraftforge.fml.ModList.get().isLoaded("spell_engine")) tickFogDarkness(minecraft);
    }

    // ------------------------------------------------------------------
    //  ★★ 黑雾：站在雾里，屏幕按档位变黑
    //
    //  引擎给不了这个 ✗：
    //    * 原版 `darkness` 效果只有**一档**，而且它压的是"视野亮度"，做不到"t 级越高越黑" ✗；
    //    * 引擎的 `Impact$Action` 里也没有"压暗屏幕"这种动作 ✗。
    //  所以分两半做：
    //    服务端 —— `DarkFogCloudEntity.tick` 把 **档位 + 半径** 写进同步字段 ✓
    //    客户端 —— 这里就近找到雾，按"离雾心多远 / 半径"算一个 0..1 的深度，
    //              再乘以**该档位的最大压暗值**，在 `RenderGuiEvent.Post` 上盖一层黑 ✓。
    //
    //  为什么要"深度"而不是"在圈里就全黑"：从边缘走进去应该是**渐渐变黑** ✓，
    //  一圈突然全黑会像画面被切了一刀 ✗。
    //
    //  验收注意：致盲（服务端那个 1 秒结算的 `minecraft:blindness`）与这里压暗是**两件事** ✓
    //  —— 致盲是给"在雾里"的惩罚，压暗是让你**看见**自己在雾里 ✓；两个一起才成立 ✓。
    // ------------------------------------------------------------------

    /**
     * 每一档的**最大**压暗值（0..1，乘 250 得 alpha）✓。
     *
     * <p>t1 25% / t2 35% / t3 50% / t4 65% / t5 80% —— 单调递增，最高档也**不到全瞎** ✗
     * （连自己脚下都看不见会不好玩；想更黑就把最后一个数字往上加 ✓）。
     */
    private static final float[] FOG_DARKNESS_BY_TIER = {0.25F, 0.35F, 0.50F, 0.65F, 0.80F};

    /** 出圈之后再留多久才完全恢复（tick）—— 走出来的那一下不该"啪"地亮回来 ✓ */
    private static final float FOG_DARKNESS_RELEASE = 0.12F;

    /** 当前应该压多黑（0 = 不压）✓ */
    private static float fogDarkness = 0.0F;

    private static void tickFogDarkness(net.minecraft.client.Minecraft minecraft) {
        float target = 0.0F;
        if (minecraft.level != null && minecraft.player != null) {
            double px = minecraft.player.getX();
            double py = minecraft.player.getY();
            double pz = minecraft.player.getZ();
            for (com.tnc.tnc.magic.DarkFogCloudEntity fog : minecraft.level.getEntitiesOfClass(
                    com.tnc.tnc.magic.DarkFogCloudEntity.class,
                    minecraft.player.getBoundingBox().inflate(32.0D))) {
                double radius = fog.syncedRadius();
                int tier = fog.syncedTier();
                if (radius <= 0.5D || tier <= 0 || tier > FOG_DARKNESS_BY_TIER.length) {
                    continue;                       // 还没同步下来 / 不是我们的雾 ⇒ 跳过 ✓
                }
                double distance = Math.sqrt(
                        (fog.getX() - px) * (fog.getX() - px)
                                + (fog.getZ() - pz) * (fog.getZ() - pz));
                if (distance > radius) {
                    continue;                       // 在圈外 ⇒ 不压 ✓
                }
                // 圈心最黑、贴边最浅；再乘一个"高度也影响"的小修正（站高处略淡 ✓）
                double depth = 1.0D - (distance / radius);
                double vertical = 1.0D - Math.min(1.0D, Math.abs(fog.getY() - py) / 8.0D);
                float value = (float) (FOG_DARKNESS_BY_TIER[tier - 1] * depth * (0.55D + 0.45D * vertical));
                if (value > target) {
                    target = value;
                }
                // ★ 顺便把"雾里其他人被黑雾裹住"那层撒上（作者 2026-10-10）✓
                //   只在玩家自己在雾里时才做 ⇒ 离得远完全不为它花代价 ✓
                tickFogShroud(minecraft, fog.getX(), fog.getY(), fog.getZ(), radius);
            }
        }
        // 进去快、出来稍慢 ✓（免得站在边界上闪）
        fogDarkness = target > fogDarkness
                ? target
                : fogDarkness + (target - fogDarkness) * FOG_DARKNESS_RELEASE;
        if (fogDarkness < 0.01F) {
            fogDarkness = 0.0F;
        }
    }

    // ------------------------------------------------------------------
    //  ★ 黑夜之手链（暗系第 1 条）的"手砸出去"震动（作者 2026-10-09："t3 开始增加震动"）
    //
    //  为什么不用"侦测附近的手投射物"：引擎的投射物是**通用类型** ✗（`SpellProjectile`），
    //  客户端这边只看得到"附近有一个投射物"，认不出是不是这一条链 ✗
    //  ⇒ 改成**施法时记一笔**（客户端也收得到引擎的 SPELL_CAST ✓）：
    //    放 t3 起的手 → 记下"要抖多久、多猛"，之后每 tick 衰减 ✓。
    //  副作用：这是**屏幕**效果，所以旁边的队友也会一起晃 —— 对"这一记很猛"的传达反而更好 ✓。
    // ------------------------------------------------------------------

    /** 手链的档位 → (震动强度, 持续 tick)；t1/t2 是 0（作者：t3 起才有）✓。 */
    private static final double[] HAND_SHAKE_STRENGTH = {0.0D, 0.0D, 5.0D, 9.0D, 14.0D};
    private static final int[] HAND_SHAKE_TICKS = {0, 0, 12, 18, 26};

    /** 还在抖的剩余 tick 与当前强度 ✓（客户端内存，不进存档 ✓）。 */
    private static int handShakeTicks = 0;
    private static float handShake = 0.0F;

    static {
        // 挂在引擎自己的事件上（不是 Forge 事件 ✗ —— 见 DarkFogMechanics 类注释）
        try {
            net.spell_engine.api.event.CombatEvents.SPELL_CAST.register(
                    TNSpellClientVisuals::onSpellCastForShake);
        } catch (Throwable t) {
            org.apache.logging.log4j.LogManager.getLogger("TN-C/spellvisuals")
                    .warn("TN-C: hand shake listener registration failed ({})", t.toString());
        }
    }

    /**
     * 施法时记一笔震动（只看"黑夜之手"这一条链的 t3+）✓。
     *
     * <p>判据是<b>法术的 group</b>（{@code "dark_hand"}）＋ {@code learn.tier} ✓ ——
     * 不写死五个 id，以后加档/改名都不用动这里 ✓。
     */
    private static void onSpellCastForShake(
            net.spell_engine.api.event.CombatEvents.SpellCast.Args args) {
        if (args == null || args.spell() == null || args.spell().spell() == null) {
            return;
        }
        net.spell_engine.api.spell.Spell spell = args.spell().spell();
        if (!"dark_hand".equals(spell.group) || spell.learn == null) {
            return;
        }
        int tier = spell.learn.tier;
        if (tier < 3 || tier > HAND_SHAKE_STRENGTH.length) {
            return;
        }
        double strength = HAND_SHAKE_STRENGTH[tier - 1];
        int ticks = HAND_SHAKE_TICKS[tier - 1];
        if (strength > handShake) {
            handShake = (float) strength;
        }
        if (ticks > handShakeTicks) {
            handShakeTicks = ticks;
        }
    }

    /**
     * 爆炸白闪：整屏一层白光，随 tick 快速淡掉 ✓。
     *
     * <p>为什么要有它：只有镜头晃动的话，"爆炸"还是会显得单薄 ✗ ——
     * 一记盖住屏幕的白闪是"能量在脸上炸开"最直接的信号 ✓（核弹的观感就是这么来的 ✓）。
     */
    @SubscribeEvent
    public static void onRenderGui(net.minecraftforge.client.event.RenderGuiEvent.Post event) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        // ① 黑雾的"屏幕变黑"（作者 2026-10-09："屏幕得给我黑了，t 级越高屏幕越黑"）✓
        if (fogDarkness > 0.0F) {
            int fa = (int) Math.min(250.0F, fogDarkness * 250.0F);
            if (fa > 1) {
                event.getGuiGraphics().fill(0, 0, mc.getWindow().getGuiScaledWidth(),
                        mc.getWindow().getGuiScaledHeight(), fa << 24);
            }
        }
        // ② 爆炸白闪（原有的）✓
        if (flash <= 0.0F) {
            return;
        }
        int a = (int) Math.min(200.0F, flash * 200.0F);
        if (a <= 2) {
            return;
        }
        int argb = (a << 24) | 0xFFFFFF;
        event.getGuiGraphics().fill(0, 0, mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight(), argb);
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles event) {
        // 两种抖动叠加：爆炸/落雷那份（shake）＋ 黑夜之手 t3+ 那份（handShake）✓
        float total = Math.max(shake, handShake);
        if (total <= 0.0F) {
            return;
        }
        event.setYaw(event.getYaw() + (SHAKE_RANDOM.nextFloat() - 0.5F) * total * 1.6F);
        event.setPitch(event.getPitch() + (SHAKE_RANDOM.nextFloat() - 0.5F) * total * 1.2F);
        event.setRoll(event.getRoll() + (SHAKE_RANDOM.nextFloat() - 0.5F) * total * 2.4F);
    }

    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        Player player = event.getPlayer();
        float mult = 1.0F;
        mult = Math.max(mult, factor(player, TNEffects.LIGHTNING_HASTE, FOV_LIGHTNING_HASTE));
        mult = Math.max(mult, factor(player, TNEffects.LIGHTNING_WIND, FOV_LIGHTNING_WIND));
        mult = Math.max(mult, factor(player, TNEffects.LIGHTNING_ASCENSION, FOV_LIGHTNING_ASCENSION));
        if (mult != 1.0F) {
            event.setNewFovModifier(event.getFovModifier() * mult);
        }
    }

    /** 有该效果就返回它的系数，否则返回 1（不动原版数值）。 */
    private static float factor(Player player, RegistryObject<MobEffect> effect, float value) {
        return (effect.isPresent() && player.hasEffect(effect.get())) ? value : 1.0F;
    }

    // ------------------------------------------------------------------
    //  ★ 黑雾"笼罩"：沾上雾的人（**除了自己**）身上裹一层黑雾
    //
    //  作者 2026-10-10："黑雾的致盲效果，我希望沾上黑雾的敌人和玩家除了自己都都有黑雾
    //  笼罩的感觉"。
    //
    //  为什么是"粒子"而不是"给自己屏幕加黑"：
    //    * 自己那块已经由 {@link #tickFogDarkness} 的满屏黑幕负责了 ✓；
    //    * 而"别人被雾裹住"这件事是**看到**的，不是**感觉**的 ⇒ 必须在**他们身上**画东西 ✓。
    //  所以：每当玩家自己在雾里（说明这个领域正在生效），就把雾里**其他**活体的身上
    //  撒一层黑雾粒子（squid_ink + smoke，都验证过是真粒子 ✓）。自己跳过 ✗。
    //
    //  ⚠ 性能：每个受影响实体每 tick 约 6 颗。雾里挤一堆怪时会线性增长 ⇒
    //    这里硬性限制每 tick 最多给 {@link #SHROUD_MAX_ENTITIES} 个实体撒，
    //    并且只在"玩家自己也在雾里"时才做（离得远就完全不跑 ✓）。
    // ------------------------------------------------------------------

    /** 每 tick 最多给几个实体撒笼罩粒子（防止大场面上百怪）。 */
    private static final int SHROUD_MAX_ENTITIES = 12;

    /** 一个实体身上每 tick 撒几颗黑雾。 */
    private static final int SHROUD_PARTICLES = 6;

    private static void tickFogShroud(net.minecraft.client.Minecraft minecraft, double fogX,
                                      double fogY, double fogZ, double radius) {
        net.minecraft.client.multiplayer.ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(
                fogX - radius, fogY - 4.0D, fogZ - radius,
                fogX + radius, fogY + 8.0D, fogZ + radius);
        java.util.List<net.minecraft.world.entity.LivingEntity> inside =
                level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, area,
                        e -> e.isAlive() && e != minecraft.player
                                && e.hasEffect(com.tnc.tnc.magic.TNEffects.DARK_FOG.get()));
        int shown = 0;
        for (net.minecraft.world.entity.LivingEntity entity : inside) {
            if (shown++ >= SHROUD_MAX_ENTITIES) {
                break;
            }
            double h = entity.getBbHeight();
            double ex = entity.getX();
            double ey = entity.getY() + h * 0.5D;
            double ez = entity.getZ();
            // 贴身的黑雾：包住整个身高，慢慢散开
            for (int i = 0; i < SHROUD_PARTICLES; i++) {
                double ox = (level.random.nextDouble() - 0.5D) * 0.9D;
                double oy = (level.random.nextDouble() - 0.5D) * h;
                double oz = (level.random.nextDouble() - 0.5D) * 0.9D;
                level.addParticle(
                        (i % 3 == 0)
                                ? net.minecraft.core.particles.ParticleTypes.SMOKE
                                : net.minecraft.core.particles.ParticleTypes.SQUID_INK,
                        ex + ox, ey + oy, ez + oz,
                        0.0D, 0.012D, 0.0D);
            }
        }
    }
}
