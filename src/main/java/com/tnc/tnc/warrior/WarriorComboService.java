package com.tnc.tnc.warrior;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.combat.DownedCombat;
import com.tnc.tnc.network.MagicStoneNetwork;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * 长剑连招的服务端结算。
 *
 * <p>流程：客户端只发"我按了哪个键"（{@link WarriorComboInputPacket}），这里负责
 * ①校验（是否加入磨砺、是否手持长剑、输入间隔）；②把输入喂给 {@link ComboTracker}；
 * ③命中后查图鉴门槛、冷却，放终结技；④回一份权威快照给 HUD。
 *
 * <p><b>武器绑定</b>：和"拿法杖才能施法"同一条规矩 —— 手上不是剑就不吃输入，
 * 换回剑立刻能接上（因为缓冲是按玩家记的，不跟着物品走）。
 *
 * <p><b>为什么 Q/E 的伤害和左键分开算</b>：左键是原版攻击，已经吃原版冷却与附魔；
 * Q/E 若也走原版攻击强度，六段连招会被 0.6 秒的挥速卡成四秒以上。所以 Q/E 只做
 * 一次轻击（0.55 倍攻击力，10 tick 内置冷却），连招节奏交给输入窗口。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class WarriorComboService {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** 两次输入之间的最小间隔（tick）：防连点脚本，人手正常速度远达不到。 */
    static final int MIN_INPUT_GAP = 2;
    /** Q/E 轻击的伤害冷却（tick）。 */
    static final int SLASH_COOLDOWN = 10;
    /** Q/E 各自的基础攻击冷却：比 10 tick 短，左右交替按能连着出刀。 */
    static final int BASIC_ATTACK_COOLDOWN = 6;
    /** Q/E 轻击的伤害系数。 */
    static final float SLASH_DAMAGE = 0.55f;
    /** Q/E 轻击的够到距离（格）。 */
    static final double SLASH_RANGE = 2.7;
    /** Q/E 轻击的扇形角度。 */
    static final int SLASH_ARC = 100;
    /** 提示最短间隔（tick），防"没拿剑"刷屏。 */
    private static final int HINT_INTERVAL = 20;
    /**
     * Shift 短按的前冲距离（格）：用户要的是"冲一大段"。
     * 实际速度是 {@code 0.62 × blocks}，1.9 起步约 1.18 格/tick，够贴脸也能拉开。
     */
    static final double SHIFT_STEP_TAP_BLOCKS = 1.9;

    private static final Map<UUID, State> STATES = new HashMap<>();
    /** 最近一次连招挥砍的 tick：挡住风元素武器在下一 tick 再画一遍挥砍轨迹。 */
    private static final Map<UUID, Long> LAST_SWING = new HashMap<>();

    private WarriorComboService() {
    }

    /** 每名玩家的临时状态：重登/换维度即清，不写存档。 */
    static final class State {
        final ComboTracker tracker = new ComboTracker();
        final Map<String, Long> cooldowns = new HashMap<>();
        long lastInput = Long.MIN_VALUE;
        long lastSlash = Long.MIN_VALUE;
        /** 上一条广播出去的基础动作，用来去重（见 basicAttack）。 */
        WarriorAnimation lastAnimation;
        long lastAnimationAt = Long.MIN_VALUE;
        /** 左斩/右斩各自的上一次时间：两条基础攻击互不抢冷却。 */
        final Map<WarriorMove, Long> lastSlashByMove = new java.util.EnumMap<>(WarriorMove.class);
        long lastHint = Long.MIN_VALUE;
        /** 振刀架势持续到这个 tick（含）：这段时间里被攻击链打到，出手的人会被架住。 */
        long stanceUntil = Long.MIN_VALUE;
        long counterAt = Long.MIN_VALUE;
        long counterReady = Long.MIN_VALUE;
        /** 正在“按着不放”的架势招（振刀）；松手时据此收势。 */
        WarriorSkill heldSkill;
        /** 上一次收到“还按着”包的 tick：久不续就自动收架。 */
        long holdKeepAliveAt;
        /** 被架住/被破架后的僵直结束 tick：这之前连招发不出去。 */
        long interruptedUntil = Long.MIN_VALUE;
        /**
         * 终结技动作演完的 tick（含）。
         *
         * <p>这之前短按 Q/E/左键<b>不再广播基础挥砍动作</b> —— 否则你刚放出的破锋式/振刀
         * 会被自己下一刀的基础动作顶掉，看起来像"连招被打断"。伤害照常结算，只是不给
         * 基础动作再套一层姿势；想接下一套连招不受影响（新连招会直接接管动作）。
         */
        long finisherUntil = Long.MIN_VALUE;
        /** 最近一次连招伤害落地的 tick，以及那一下的对拼定位（谁先造成伤害的判定依据）。 */
        long comboAt = Long.MIN_VALUE;
        WarriorComboRole comboRole = WarriorComboRole.NEUTRAL;
    }

    // ------------------------------------------------------------------
    //  输入
    // ------------------------------------------------------------------

    /** 轻点输入（旧调用）。 */
    public static void input(ServerPlayer player, WarriorMove move) {
        input(player, move, false);
    }

    /**
     * 客户端输入的唯一入口。
     *
     * <p>{@code held} = 这一下是"按住超过半秒"的长按。长按<b>不再</b>追加一次挥砍/特效：
     * 按下那一下已经出过刀了，长按只是把缓冲里的那一段从"轻点"改写成"长按"，
     * 用来接振刀这类需要按住的招式。
     */
    public static void input(ServerPlayer player, WarriorMove move, boolean held) {
        input(player, move, held ? WarriorComboInputPacket.HOLD : WarriorComboInputPacket.TAP);
    }

    /**
     * 四个阶段：轻点 / 长按 / 松手 / 还按着。
     *
     * <p>松手与还按着都不产生新输入，只用来维护“按住就一直摆架势”
     * （振刀）：按着时架势窗口不断续，松手才算收架。
     */
    public static void input(ServerPlayer player, WarriorMove move, int mode) {
        if (player == null || move == null || !eligible(player)) {
            return;
        }
        try {
            if (WarriorBowCharge.input(player,move,mode)) return;
            if(mode==WarriorComboInputPacket.CHARGE_START)return;
            if (mode == WarriorComboInputPacket.RELEASE) {
                releaseHold(player);
                return;
            }
            if (mode == WarriorComboInputPacket.KEEPALIVE) {
                keepHold(player);
                return;
            }
            handleInput(player, move, mode == WarriorComboInputPacket.HOLD);
        } catch (RuntimeException error) {
            LOGGER.error("[tnc] 连招引擎处理 {} 时出错（已吞掉，不踢人）", move, error);
        }
    }

    /** 松手：把按着不放的架势收掉（摆姿由客户端软落地）。 */
    private static void releaseHold(ServerPlayer player) {
        State state = state(player.getUUID());
        WarriorSkill skill = state.heldSkill;
        if (skill == null) {
            return;
        }
        state.heldSkill = null;
        state.stanceUntil = gameTime(player);
        WarriorAnimationPacket.release(player, skill.animation(), skill.animation().durationTicks());
    }

    /** 还按着：架势窗口续上，抗性也续上（每 10 tick 一次）。 */
    private static void keepHold(ServerPlayer player) {
        State state = state(player.getUUID());
        WarriorSkill skill = state.heldSkill;
        if (skill == null || skill.guardTicks() <= 0) {
            return;
        }
        long now = gameTime(player);
        if (now < state.interruptedUntil) { releaseHold(player); return; }
        state.stanceUntil = now + skill.guardTicks();
        state.holdKeepAliveAt = now;
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, skill.guardTicks() + 10, 2, false, false));
    }

    private static void handleInput(ServerPlayer player, WarriorMove move, boolean held) {
        WarriorWeaponFamily family = WarriorWeapons.family(player.getMainHandItem());
        if (family == null) {
            hint(player, "§7需要手持武器才能施展连招");
            return;
        }
        long now = gameTime(player);
        State state = state(player.getUUID());
        // 排查用：一按 Q/E 就掉线时，这一行是 debug.log 里的最后足迹
        LOGGER.debug("[tnc] input move={} held={} family={} gap={}", move, held, family, elapsed(now, state.lastInput));
        if (elapsed(now, state.lastInput) < MIN_INPUT_GAP) {
            return;
        }
        state.lastInput = now;

        if (!held) {
            // Z（左键）的原版伤害已经由游戏结算，这里只记输入，避免一次点击打两下。
            // 弓不是近战武器：Z 只记输入，不画近战横斩轨迹。
            // 终结技还在演（performing）时不给基础动作：否则刚放出的招会被自己下一刀顶掉。
            // Q/E/Z 即使不组连招，也各有一次独立的基础攻击（左斩 / 右斩 / 横斩）。
            // 以前只有 Z 无条件播横斩、Q/E 还要等 10 tick 冷却，按快一点时手里那把剑纹丝不动，
            // 看起来像"没有普攻"（用户 2026-11-02 反馈）。
            if (family.melee() && now >= state.finisherUntil) {
                basicAttack(player, state, move, now);
            }
        }

        WarriorInput input = held ? WarriorInput.hold(move) : WarriorInput.tap(move);
        WarriorSkill matched = state.tracker.push(input, now, WarriorCombos.forFamily(family));
        int flashed = -1;
        if (matched != null) {
            flashed = resolve(player, state, matched, now, held);
        }
        sync(player, state, flashed);
    }

    /** 命中一条连招后：查解锁、查冷却、放终结技。返回在目录里的下标（HUD 闪实用）。 */
    private static int resolve(ServerPlayer player, State state, WarriorSkill skill, long now, boolean held) {
        var progress = progress(player);
        int index = WarriorCombos.all().indexOf(skill);
        if (!WarriorCombos.unlocked(skill, progress)) {
            int need = WarriorCombos.temperRequirement(skill);
            hint(player, "§e连招已成 · " + skill.name() + " 尚未解锁"
                    + (need > 0 ? "（需磨砺 " + need + "，当前 " + progress.points() + "）" : ""));
            return -1;
        }
        long ready = state.cooldowns.getOrDefault(skill.id(), 0L);
        if (now < ready) {
            hint(player, "§7" + skill.name() + " 冷却 " + seconds(ready - now));
            return -1;
        }
        state.cooldowns.put(skill.id(), now + skill.cooldownTicks());
        if (!finisher(player, skill, state, now, held)) {
            // 条件不足（例如弓弦空了）：撤掉刚记的冷却，玩家补完箭可以立刻再试
            state.cooldowns.remove(skill.id());
            return -1;
        }
        player.displayClientMessage(Component.literal("§6连招 · " + skill.name() + "！"), true);
        return index;
    }

    // ------------------------------------------------------------------
    //  伤害与特效
    // ------------------------------------------------------------------

    /**
     * Q/E 的一次轻击：只打面前扇形里的少量目标，不打断原版挥砍节奏。
     *
     * @param animate 要不要广播基础挥砍动作。终结技正在演的时候传 {@code false}：
     *                伤害和声音照旧，但不再往客户端丢 SLASH_* —— 那一层姿势会把刚放出的
     *                终结技动作顶掉（"短按把自己连招打断了"就是这么来的）。
     */
    /**
     * 一条独立的基础攻击：动作、伤害、声音都按 Q/E/Z 分开。
     *
     * <p>动作<b>每次都广播</b>（除了终结技正在演的时候），伤害另外按 6 tick 冷却结算：
     * 这样"左右交替点"是连着的两刀，而"狂点同一个键"不会把伤害翻倍。
     */
    private static void basicAttack(ServerPlayer player, State state, WarriorMove move, long now) {
        WarriorAnimation animation = switch (move) {
            case LEFT -> WarriorAnimation.SLASH_LEFT;
            case RIGHT -> WarriorAnimation.SLASH_RIGHT;
            case HORIZONTAL -> WarriorAnimation.SLASH_HORIZONTAL;
        };
        // 同一招还没演完就不重复发：不然狂点 Q 每 2 tick 一包，
        // 客户端还会逐包写一条"没有关键帧"的日志，直接把主线程卡成影片
        if (animation == state.lastAnimation && now - state.lastAnimationAt < animation.durationTicks()) {
            return;
        }
        state.lastAnimation = animation;
        state.lastAnimationAt = now;
        LOGGER.debug("[tnc] basic animation={} at={}", animation, now);
        WarriorAnimationPacket.broadcast(player, animation, animation.durationTicks());
        if (move == WarriorMove.HORIZONTAL) {
            // Z（左键）的伤害由原版结算，这里只补动作，避免一次点击打两下
            state.lastSlash = now;
            return;
        }
        long last = state.lastSlashByMove.getOrDefault(move, Long.MIN_VALUE);
        if (elapsed(now, last) < BASIC_ATTACK_COOLDOWN) {
            return;
        }
        state.lastSlashByMove.put(move, now);
        state.lastSlash = now;
        slash(player, move, false);
    }

    static void slash(ServerPlayer player, WarriorMove move, boolean animate) {
        LOGGER.debug("[tnc] slash move={} animate={}", move, animate);
        player.swing(InteractionHand.MAIN_HAND, true);
        LAST_SWING.put(player.getUUID(), gameTime(player));
        if (animate) {
            WarriorAnimation animation = switch (move) {
                case LEFT -> WarriorAnimation.SLASH_LEFT;
                case RIGHT -> WarriorAnimation.SLASH_RIGHT;
                case HORIZONTAL -> WarriorAnimation.SLASH_HORIZONTAL;
            };
            WarriorAnimationPacket.broadcast(player, animation, animation.durationTicks());
        }
        float base = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        // 左斩偏左、右斩偏右、横斩最宽：三种基础攻击的"手感形状"不一样
        int arc = move == WarriorMove.HORIZONTAL ? SLASH_ARC : 80;
        float scale = move == WarriorMove.HORIZONTAL ? SLASH_DAMAGE : 0.50f;
        for (LivingEntity target : targets(player, SLASH_RANGE)) {
            if (!inArc(player, target, arc)) {
                continue;
            }
            if (target.hurt(player.damageSources().playerAttack(player), base * scale)) {
                knockback(player, target, move == WarriorMove.RIGHT ? .28 : .22);

            }
        }
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, .7f, switch (move) {
                    case LEFT -> 1.15f;
                    case RIGHT -> .95f;
                    case HORIZONTAL -> .85f;
                });
    }

    /** 终结技：扇形/环形范围伤害 + 击退 + 位移 + 打断/僵直 + 闪避 + 武器耐久。 */
    private static boolean finisher(ServerPlayer player, WarriorSkill skill, State state, long now, boolean held) {
        skill = WarriorPerformance.forWeapon(skill, player.getMainHandItem());
        // 刚被振刀架住 / 被破架：僵直期间连招发不出来（冷却也不占，恢复后能立刻再试）
        if (now < state.interruptedUntil) {
            hint(player, "§c僵直中 · 连招接不上");
            return false;
        }
        // 弓的每一条链都要真的射出去，所以先确认箭够不够：不够就整段不发。
        // 放在位移/无敌/扣耐久之前，避免"人已经后跃出去了，箭却没射出来"。
        if (skill.ranged() && !hasAmmo(player)) {
            hint(player, "§7弓弦空了 · 没有箭，补一捆再放「" + skill.name() + "」");
            return false;
        }
        // 按着不放的架势招（振刀）：动作演到最后一帧就停住，直到松手
        // （客户端看到 HOLD 就不再过期，收到收势包才软落地）
        boolean sustain = held && skill.guardTicks() > 0;
        state.heldSkill = sustain ? skill : null;
        state.holdKeepAliveAt = now;
        if (sustain) {
            WarriorAnimationPacket.hold(player, skill.animation(), skill.animation().durationTicks());
        } else {
            WarriorAnimationPacket.broadcast(player, skill.animation(), skill.animation().durationTicks());
        }
        // 剑气 / 剑波（长剑、重剑、战锤、长枪都有自己的一道）
        WarriorWaves.onFinisher(player, skill, WarriorPhantom.scale(player));
        // 龙之剑：每一记终结技都带一声龙吼；龙本体在客户端渲染（WarriorDragonRenderer），
        // 服务端不广播任何粒子 —— 那条"粒子的龙"2026-11-03 已整段删除。
        if (player.getMainHandItem().is(TNMod.DRAGON_SWORD.get())) {
            WarriorCombatEffects.dragonRoar(player);
        }
        // 这条动作演完之前，短按 Q/E/左键不再广播基础挥砍（见 State#finisherUntil）
        state.finisherUntil = now + skill.animation().durationTicks();
        if (skill.displaces()) {
            if (skill.family()==WarriorWeaponFamily.BOW && skill.slot()==3) {
                Vec3 side = player.getLookAngle().cross(new Vec3(0,1,0)).normalize();
                player.setDeltaMovement(side.scale(.62 * skill.dash()).add(0,.12,0)); player.hurtMarked=true;
            } else dash(player, skill.dash());
        }
        if (skill.backsteps()) {
            backstep(player, skill.backstep());
        }
        if (skill.evades()) {
            // 闪避链：短暂无敌帧，配合后撤真的躲开这一下
            player.invulnerableTime = Math.max(player.invulnerableTime, skill.evadeTicks());
        }
        if (skill.ranged()) {
            shoot(player, skill);
        } else if (skill.slot()!=1) {
            melee(player, skill, state, now);
        }
        WarriorCombatEffects.finisherSound(player, skill);
        if (skill.guardTicks() > 0) {
            // 架势：短时间高抗性，配合打断让振刀真的"架得住"
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, skill.guardTicks(), 2, false, false));
        }
        if (skill.role() == WarriorComboRole.STANCE) {
            // 振刀架势窗口：这之后一段时间里，攻击链打到你会被架住（见 melee 里的 parried）
            state.stanceUntil = now + skill.guardTicks();
        }
        player.getMainHandItem().hurtAndBreak(skill.durabilityCost(), player,
                broken -> broken.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        return true;
    }

    /**
     * 中断自己当前的动作（Shift <b>按下那一瞬间</b>就发，不等松手）。
     *
     * <p>做三件事：清空连招缓冲、撤掉"终结技正在演"的锁、让客户端把这一帧的姿势扔掉。
     * 于是"按 Shift 就能立刻停下自己的动作"是无延迟的，后面的冲刺只是附加的位移。
     */
    public static void interrupt(ServerPlayer player) {
        WarriorBowCharge.cancel(player);
        if (player == null || !eligible(player)) {
            return;
        }
        State state = state(player.getUUID());
        if (!state.tracker.isEmpty()) {
            state.tracker.clear();
            sync(player, state, -1);
        }
        state.finisherUntil = 0;
        state.heldSkill = null;
        WarriorAnimationPacket.cancel(player);
    }

    /**
     * 战斗步法（用户口述规格，按键默认左 Alt；Shift 保持原版潜行）：
     *
     * <ul>
     *   <li><b>短按</b>：向前冲<b>一大段</b>（纯位移，不给加速）；</li>
     *   <li><b>长按</b>：只挪<b>一小段</b>，但获得一段加速跑；</li>
     *   <li>不论长短，按下就<b>中断自己当前的动作</b>（见 {@link #interrupt}）。</li>
     * </ul>
     *
     * <p>只在"已加入磨砺 + 手持武器"时生效；空手/拿法杖时这个键什么都不做，
     * 潜行（Shift）从头到尾都是原版行为，本 mod 不再吞它。
     */
    public static void step(ServerPlayer player, boolean sprint) {
        if (player == null || !eligible(player)) {
            return;
        }
        if (WarriorWeapons.family(player.getMainHandItem()) == null) {
            return;
        }
        interrupt(player);
        if (sprint) {
            // 长按：小步 + 加速跑，用来贴着打、绕后
            dash(player, SHIFT_STEP_TAP_BLOCKS * 0.3);
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 120, 1, false, false));
            // 蹲着的时候别强行起跑：原版潜行和疾跑互斥，强行 sprint 会把潜行顶掉，
            // 玩家按着蹲键却发现蹲不下去。蹲着就蹲着滑一步，速度加成照样生效。
            if (!player.isCrouching()) {
                player.setSprinting(true);
            }
        } else {
            // 短按：一大段前冲，用来贴脸或拉开
            dash(player, SHIFT_STEP_TAP_BLOCKS);
        }
        player.serverLevel().playSound(null, player.blockPosition(),
                sprint ? SoundEvents.PLAYER_ATTACK_SWEEP : SoundEvents.PLAYER_ATTACK_NODAMAGE,
                SoundSource.PLAYERS, .5f, sprint ? 1.4f : 1.7f);
    }

    /** 近战终结技：每条链共用这段，打断/僵直由数据字段决定。 */
    static void melee(ServerPlayer player, WarriorSkill skill, State state, long now) {
        float base = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        WarriorComboRole role = skill.role();
        // 虚影倍率：磨砺越高手越长（和客户端的虚影大小是同一个数）
        double reach = WarriorPhantom.scale(player);
        boolean landed = false;
        for (LivingEntity target : targets(player, skill.radius() * reach)) {
            if (!skill.fullCircle() && !inArc(player, target, skill.arcDegrees())) {
                continue;
            }
            // 振刀架势：这一下被架住，不打伤害，自己整段中断
            if (target instanceof ServerPlayer other && parried(player, state, other, skill, now)) {
                return;
            }
            if (!target.hurt(player.damageSources().playerAttack(player), base * skill.damageMultiplier())) {
                continue;
            }
            landed = true;
            knockback(player, target, skill.knockback());
            if (skill.interrupt() || skill.staggerTicks() > 0) {
                interrupt(player, target, skill.staggerTicks());
            }

            if (target instanceof ServerPlayer other) {
                afterHit(player, state, other, role, now);
            }
        }
        if (landed && role != WarriorComboRole.NEUTRAL) {
            // 记下"我这一招什么时候真的打到了人"：对拼规则靠它判断谁先造成伤害
            state.comboAt = now;
            state.comboRole = role;
        }
    }

    /**
     * 振刀架住攻击：攻击链/突击链打到正在振刀的人，<b>这一下不掉血</b>，
     * 出手的人整段连招作废并僵直 1 秒。
     *
     * <p>这就是振刀存在的意义 —— 站桩换血的人敢在振刀窗口里抬手，就要吃这一秒。
     */
    private static boolean parried(ServerPlayer player, State state, ServerPlayer target,
                                   WarriorSkill skill, long now) {
        boolean inStance = now < state(target.getUUID()).stanceUntil;
        if (WarriorDuel.beforeHit(skill.role(), inStance) != WarriorDuel.Outcome.PARRIED) {
            return false;
        }
        stagger(player, WarriorDuel.PARRY_STAGGER);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK,
                SoundSource.PLAYERS, 1.0f, 1.25f);
        WarriorImpactPacket.send(target,target.getEyePosition().add(target.getLookAngle().scale(.65)).add(0,-.35,0),1);
        hint(player, "§b振刀架住了「" + skill.name() + "」· 你的连招被打断，僵直 1 秒");
        hint(target, "§b振刀震开一击 · 对方僵直 1 秒");
        return true;
    }

    /**
     * 连招命中玩家之后的克制结算：破架打散振刀、进攻 ↔ 破架 的先后手。
     *
     * <p>判定本身在 {@link WarriorDuel}（纯规则、可单测），这里只负责施加效果和播反馈。
     */
    private static void afterHit(ServerPlayer player, State state, ServerPlayer victim,
                                 WarriorComboRole role, long now) {
        State other = state(victim.getUUID());
        boolean inStance = now < other.stanceUntil;
        WarriorDuel.Outcome outcome = WarriorDuel.afterHit(role, inStance, other.comboRole,
                elapsed(now, other.comboAt));
        if (outcome == WarriorDuel.Outcome.STANCE_BROKEN) {
            // 破振刀：打散架势、撤掉抗性，压 2 秒僵直
            other.stanceUntil = Long.MIN_VALUE;
            victim.removeEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE);
            other.heldSkill=null;
            WarriorAnimationPacket.cancel(victim);
            WarriorImpactPacket.send(player,victim.getBoundingBox().getCenter(),4);
            stagger(victim, WarriorDuel.BREAK_STAGGER);
            player.serverLevel().playSound(null, victim.blockPosition(), SoundEvents.SHIELD_BREAK,
                    SoundSource.PLAYERS, 1.0f, 0.85f);
            hint(player, "§a破振刀 · 打散了对方的架势，僵直 2 秒");
            hint(victim, "§c振刀被「破架」破了 · 僵直 2 秒");
        } else if (outcome == WarriorDuel.Outcome.CLASH_LOST) {
            // 对拼：对方那一下先落地 → 自己这一下算"慢了半拍"
            stagger(player, WarriorDuel.CLASH_STAGGER);
            hint(player, "§e对拼慢了半拍 · 连招被打断，僵直 0.25 秒");
            hint(victim, "§e你的连招先落地 · 架开了对方");
        }
    }

    /**
     * 让某个玩家僵直：减速 + 虚弱，清掉正在接的连招缓冲，
     * 并在这段时间里锁住连招（{@link State#interruptedUntil}）。
     */
    static void stagger(ServerPlayer victim, int ticks) {
        WarriorBowCharge.cancel(victim);
        if (ticks <= 0) {
            return;
        }
        victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, ticks, 2, false, false));
        victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.WEAKNESS, ticks, 1, false, false));
        victim.setDeltaMovement(0, Math.min(0, victim.getDeltaMovement().y), 0);
        victim.hurtMarked = true;
        clearCombo(victim);
        State state = state(victim.getUUID());
        state.heldSkill=null;state.stanceUntil=Long.MIN_VALUE;state.counterAt=Long.MIN_VALUE;
        WarriorAnimationPacket.cancel(victim);
        state.interruptedUntil = Math.max(state.interruptedUntil, gameTime(victim) + ticks);
    }

    /** 背包（含副手）里能当箭用的那一摞；没有返回空栈。 */
    static ItemStack ammo(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof ArrowItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * 远程链前置条件：创造模式或弓上有无限附魔算"箭源无限"，其余情况必须背包里真的有箭。
     *
     * <p>无限附魔仍要求至少一支箭（和原版一致），所以这里先看背包再看附魔。
     */
    static boolean hasAmmo(ServerPlayer player) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        return !ammo(player).isEmpty();
    }

    /** 远程终结技（弓）：消耗一支箭再射；创造/无限附魔不消耗，射出的箭也不能捡。 */
    private static void shoot(ServerPlayer player, WarriorSkill skill) {
        ItemStack bow = player.getMainHandItem();
        ItemStack ammunition = ammo(player).copy();
        boolean infinite = player.getAbilities().instabuild
                || EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS, bow) > 0;
        if (!infinite) {
            ItemStack arrowStack = ammo(player);
            if (!arrowStack.isEmpty()) {
                arrowStack.shrink(1);
            }
        }
        net.minecraft.world.entity.projectile.Arrow arrow =
                WarriorArrowEntity.create(player.level(),player,ammunition,bow);
        arrow.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F,
                2.4F, Math.max(0.4F, 1.2F - (float) Math.min(1, skill.length() / 8.0)));
        arrow.setBaseDamage(5.2 * skill.damageMultiplier());
        // 连招的箭是"特殊箭"：更重（穿一支）、必定暴击、带发光轮廓。
        // 原来还有一条"元素光尾"（每 tick 撒两颗粒子），2026-11-03 起不再广播：
        // 发光轮廓 + 暴击星已经是可读的反馈，再糊一条粒子尾巴纯属挡视线。
        arrow.setCritArrow(true);
        arrow.setPierceLevel((byte) 1);
        arrow.setGlowingTag(true);
        arrow.pickup = infinite
                ? net.minecraft.world.entity.projectile.AbstractArrow.Pickup.CREATIVE_ONLY
                : net.minecraft.world.entity.projectile.AbstractArrow.Pickup.ALLOWED;
        player.level().addFreshEntity(arrow);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ARROW_SHOOT,
                SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    /**
     * 防御链的打断：停掉目标正在用的物品（施法/拉弓/举盾都走这条），清掉对方的连招缓冲，
     * 再按 {@code staggerTicks} 压一段僵直。
     *
     * <p>"打断其他玩家的连招"靠的是清空他服务端的输入缓冲 —— 不清的话，对方元素栏里
     * 已经接了一半的连招会在僵直结束后继续成立。
     */
    private static void interrupt(ServerPlayer player, LivingEntity target, int staggerTicks) {
        target.stopUsingItem();
        if (target instanceof ServerPlayer other) {
            clearCombo(other);
        }
        if (staggerTicks > 0) {
            target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, staggerTicks, 2, false, false));
            target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.WEAKNESS, staggerTicks, 1, false, false));
            target.setDeltaMovement(0, Math.min(0, target.getDeltaMovement().y), 0);
            target.hurtMarked = true;
        }
    }

    /**
     * 打断某个玩家正在接的连招（防御链命中、被打断时都走这里）。
     *
     * <p>抽出来是为了能单独验证：假玩家不在实体表里，走不到完整的 hurt 结算，
     * 但"清缓冲"这件事本身可以直接测。
     */
    static void clearCombo(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        if (state != null && !state.tracker.isEmpty()) {
            state.tracker.clear();
            sync(player, state, -1);
        }
    }

    /** 突击链的位移：沿视线水平前冲，带一点抬升，velocity 同步给客户端。 */
    private static void dash(ServerPlayer player, double blocks) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1e-6) {
            return;
        }
        flat = flat.normalize();
        player.setDeltaMovement(flat.x * 0.62 * blocks,
                Math.max(0.08, player.getDeltaMovement().y), flat.z * 0.62 * blocks);
        player.hurtMarked = true;
    }

    /** 闪避链的后撤：沿视线反方向弹开。 */
    private static void backstep(ServerPlayer player, double blocks) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(-look.x, 0, -look.z);
        if (flat.lengthSqr() < 1e-6) {
            return;
        }
        flat = flat.normalize();
        player.setDeltaMovement(flat.x * 0.7 * blocks,
                Math.max(0.05, player.getDeltaMovement().y), flat.z * 0.7 * blocks);
        player.hurtMarked = true;
    }

    private static List<LivingEntity> targets(ServerPlayer player, double radius) {
        double reach = radius * radius;
        return player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius),
                // 方块 AABB 会外扩到角落（最远 1.73 倍），所以再补一次真实距离和视线判定，
                // 免得隔一堵墙、隔一层地板也能被 360 度旋斩扫到
                target -> player.distanceToSqr(target) <= reach
                        && player.hasLineOfSight(target)
                        && validTarget(player, target));
    }

    static boolean validTarget(ServerPlayer player, LivingEntity target) {
        if (target == player || !target.isAlive() || target.isSpectator()) {
            return false;
        }
        if (player.isAlliedTo(target) || !player.canAttack(target)) {
            return false;
        }
        if (target instanceof Player other && !player.canHarmPlayer(other)) {
            return false;
        }
        // 自己的宠物/坐骑、别人的盔甲架都不该被范围技顺手带走
        if (target instanceof OwnableEntity ownable && player.getUUID().equals(ownable.getOwnerUUID())) {
            return false;
        }
        if (target instanceof ArmorStand) {
            return false;
        }
        // 演武场的教官只在训练课里吃"正面一击"，不被连招顺手打
        return !TrainingGround.trainer(target);
    }

    /** 目标是否在玩家面前 {@code degrees} 度的扇形里。360 度恒真。 */
    static boolean inArc(ServerPlayer player, LivingEntity target, int degrees) {
        if (degrees >= 360) {
            return true;
        }
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1e-6) {
            // 仰头/低头时水平朝向退化成 0：按 yaw 兜底，而不是当成"全方向"
            double yaw = Math.toRadians(player.getYRot());
            flat = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        }
        Vec3 to = new Vec3(target.getX() - player.getX(), 0, target.getZ() - player.getZ());
        if (to.lengthSqr() < 1e-6) {
            return true;
        }
        double cos = Math.cos(Math.toRadians(Math.max(1, Math.min(180, degrees)) / 2.0));
        return flat.normalize().dot(to.normalize()) >= cos;
    }

    private static void knockback(ServerPlayer player, LivingEntity target, double strength) {
        if (strength <= 0) {
            return;
        }
        target.knockback((float) strength, target.getX() - player.getX(), target.getZ() - player.getZ());
    }

    // ------------------------------------------------------------------
    //  状态 / 同步
    // ------------------------------------------------------------------

    static boolean eligible(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !DownedCombat.isDowned(player)
                && !(player.isUsingItem() && player.getUseItem().getItem() instanceof com.tnc.tnc.bronze.BronzeWeaponItem)
                && TemperingData.get(player.server).enrolled(player.getUUID());
    }

    static State state(UUID id) {
        return STATES.computeIfAbsent(id, key -> new State());
    }

    /**
     * 这个玩家现在正在演某个终结技的动作吗？
     *
     * <p>这段时间里短按 Q/E/左键<b>不会</b>广播基础挥砍姿势（伤害照常），所以自己的连招
     * 不会被打断；想接下一套连招随时可以按，新连招会直接接管动作。
     */
    static boolean performing(ServerPlayer player, long now) {
        return player != null && now < state(player.getUUID()).finisherUntil;
    }

    /** 与服务端面板同一口径的进度快照（图鉴/连招解锁共用）。 */
    public static WarriorCodex.Progress progress(ServerPlayer player) {
        var data = TemperingData.get(player.server);
        var record = TrainingGroundData.get(player.server).record(player.getUUID());
        return new WarriorCodex.Progress(data.talent(player.getUUID()), data.points(player.getUUID()),
                record.completions(), record.score(), data.guardGraduate(player.getUUID()));
    }

    /** 把权威缓冲与冷却推给该玩家的 HUD。 */
    static void sync(ServerPlayer player, State state, int flashed) {
        long now = gameTime(player);
        List<WarriorSkill> all = WarriorCombos.all();
        int[] cooldowns = new int[all.size()];
        for (int i = 0; i < all.size(); i++) {
            long ready = state.cooldowns.getOrDefault(all.get(i).id(), 0L);
            cooldowns[i] = (int) Math.max(0, Math.min(Integer.MAX_VALUE, ready - now));
        }
        List<WarriorInput> inputs = state.tracker.inputs();
        byte[] buffer = new byte[inputs.size()];
        for (int i = 0; i < buffer.length; i++) {
            // 最高位 = 长按标记；低 2 位是斩击方向
            buffer[i] = (byte) (inputs.get(i).move().ordinal() | (inputs.get(i).held() ? 0x80 : 0));
        }
        MagicStoneNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new WarriorComboSyncPacket(buffer, cooldowns, flashed));
    }

    private static void hint(ServerPlayer player, String text) {
        long now = gameTime(player);
        State state = state(player.getUUID());
        if (elapsed(now, state.lastHint) < HINT_INTERVAL) {
            return;
        }
        state.lastHint = now;
        player.displayClientMessage(Component.literal(text), true);
    }

    private static String seconds(long ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0) + " 秒";
    }

    static long gameTime(ServerPlayer player) {
        return player.server.overworld().getGameTime();
    }

    /** 这一两 tick 内刚有过连招挥砍？风元素武器的自动挥砍轨迹据此让位，避免一次挥砍画两条。 */
    static boolean swungRecently(ServerPlayer player, long now) {
        Long at = LAST_SWING.get(player.getUUID());
        return at != null && now - at <= 2;
    }

    /** {@code then == Long.MIN_VALUE} 时返回一个大数，避免相减溢出成负数。 */
    static long elapsed(long now, long then) {
        return then == Long.MIN_VALUE ? Long.MAX_VALUE / 2 : now - then;
    }

    // ------------------------------------------------------------------
    //  生命周期
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        State state = STATES.get(player.getUUID());
        if (state == null) {
            return;
        }
        long now = gameTime(player);
        if (state.counterAt != Long.MIN_VALUE && now >= state.counterAt) {
            state.counterAt = Long.MIN_VALUE;
            mobCounterSweep(player);
        }
        int before = state.tracker.size();
        state.tracker.prune(now);
        if (state.tracker.size() != before) {
            // 缓冲过期了要主动推一次，否则 HUD 还挂着已经不存在的进度
            sync(player, state, -1);
        }
        if (state.heldSkill != null && now - state.holdKeepAliveAt > 40) {
            // 玩家按着架势时客户端会每 10 tick 续一次；突然不续了（关界面、切线、
            // 按键被卡住），不能让他一直摆着架势站在那里
            state.heldSkill = null;
            state.stanceUntil = now;
        }
        state.cooldowns.values().removeIf(ready -> ready <= now);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
        LAST_SWING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        STATES.remove(event.getEntity().getUUID());
        LAST_SWING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        STATES.remove(event.getEntity().getUUID());
        LAST_SWING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        STATES.clear();
        LAST_SWING.clear();
    }

    /**
     * 连招被打断：连招还没接完（缓冲里还有输入）时挨了别人的伤害，整段作废。
     *
     * <p>这条是防御链"能打断别人"的另一面 —— 你在连招前摇里还没架起振刀，
     * 敌人一刀就能把你的连招打断；只有按满长按、振刀真正放出来，才有抗性和打断。
     *
     * <p><b>首领不打断</b>（2026-11-02 用户指定）：{@code /boss} 级敌人的攻击面板伤害高、
     * 攻击频率也高，如果每一巴掌都把玩家的连招清零，打首领就变成"永远接不完一套"。
     * 判定见 {@link #boss}：实体类型标签 {@code tnc:bosses}，或最大生命 ≥ 80（兜住没打标签的模组首领）。
     */
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGH)
    public static void mobParry(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getAmount()<=0 || !eligible(p)) return;
        if (!(event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.Mob mob)
                || event.getSource().getEntity()!=mob || !smallCounterTarget(p,mob)) return;
        var state=STATES.get(p.getUUID());long now=gameTime(p);
        if(state==null || now>=state.stanceUntil || now<state.counterReady)return;
        event.setCanceled(true);
        state.stanceUntil=Long.MIN_VALUE;state.heldSkill=null;state.counterReady=now+18;state.counterAt=now+2;
        p.removeEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE);
        WarriorAnimationPacket.cancel(p);
        WarriorImpactPacket.send(p,p.getEyePosition().add(p.getLookAngle().scale(.6)).add(0,-.35,0),1);
        p.serverLevel().playSound(null,p.blockPosition(),SoundEvents.SHIELD_BLOCK,SoundSource.PLAYERS,1.0f,1.35f);
    }
    static boolean smallCounterTarget(ServerPlayer p,LivingEntity target){
        return target instanceof net.minecraft.world.entity.Mob && target.getBbHeight()<4
                && p.distanceToSqr(target)<=16 && p.hasLineOfSight(target) && validTarget(p,target);
    }
    static void mobCounterSweep(ServerPlayer p){
        if(!eligible(p)||WarriorWeapons.family(p.getMainHandItem())==null)return;
        WarriorAnimationPacket.broadcast(p,WarriorAnimation.SPIN_SLASH,10);
        WarriorImpactPacket.send(p,p.position().add(0,.85,0),2);
        p.serverLevel().playSound(null,p.blockPosition(),SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,1.1f,.75f);
        for(var target:targets(p,4)){
            if(!smallCounterTarget(p,target))continue;
            target.hurt(p.damageSources().playerAttack(p),(float)p.getAttributeValue(Attributes.ATTACK_DAMAGE)*.8f);
            var away=target.position().subtract(p.position()).multiply(1,0,1);
            if(away.lengthSqr()<.001)away=new Vec3(1,0,0);
            away=away.normalize().scale(1.6);
            target.setDeltaMovement(away.x,.75,away.z);target.hurtMarked=true;
        }
    }

    @SubscribeEvent
    public static void damaged(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        if (event.getAmount() <= 0 || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        var source = event.getSource().getEntity();
        if (source == null || source == player) {
            return;
        }
        if (source instanceof LivingEntity attacker && boss(attacker)) {
            return;
        }
        State state = STATES.get(player.getUUID());
        if (state == null || state.tracker.isEmpty()) {
            return;
        }
        state.tracker.clear();
        sync(player, state, -1);
        hint(player, "§c连招被打断");
    }

    /** 首领判定：显式标签优先，再用"血量很高"兜底（模组首领一般没打我们的标签）。 */
    static boolean boss(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        if (entity.getType().is(BOSSES)) {
            return true;
        }
        return entity.getMaxHealth() >= BOSS_HEALTH;
    }

    /** 数据包标签：{@code data/tnc/tags/entity_types/bosses.json}，服主可以往里面塞模组首领。 */
    public static final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> BOSSES =
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc", "bosses"));

    /** 没打标签时，最大生命到这个数就当首领（铁傀儡 100、劫掠兽 100 也在此列，它们本来就打不断连招）。 */
    static final double BOSS_HEALTH = 80.0;

    // ------------------------------------------------------------------
    //  命令（/tempering combo）
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tempering")
                .then(Commands.literal("combo")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            context.getSource().sendSuccess(() -> Component.literal(describe(player)), false);
                            return 1;
                        })
                        .then(Commands.literal("reset").executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            State state = state(player.getUUID());
                            state.tracker.clear();
                            state.cooldowns.clear();
                            sync(player, state, -1);
                            context.getSource().sendSuccess(() -> Component.literal("连招缓冲与冷却已清空。"), false);
                            return 1;
                        }))
                        .then(Commands.argument("move", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (WarriorMove move : WarriorMove.values()) {
                                        builder.suggest(String.valueOf(move.key()));
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    String raw = StringArgumentType.getString(context, "move");
                                    WarriorMove move = raw.isEmpty() ? null : WarriorMove.byKey(raw.charAt(0));
                                    if (move == null) {
                                        context.getSource().sendFailure(Component.literal("斩击只有 q（左斩）/ e（右斩）/ z（横斩）。"));
                                        return 0;
                                    }
                                    input(player, move);
                                    return 1;
                                }))));
    }

    /** 命令行的可读状态，顺带说明下一步该按什么。 */
    static String describe(ServerPlayer player) {
        var progress = progress(player);
        State state = state(player.getUUID());
        WarriorWeaponFamily family = WarriorWeapons.family(player.getMainHandItem());
        List<WarriorSkill> catalog = family == null ? WarriorCombos.all() : WarriorCombos.forFamily(family);
        StringBuilder out = new StringBuilder(family == null ? "未持武器" : family.cn() + "连招");
        out.append(" · 缓冲 ");
        var inputs = state.tracker.inputs();
        if (inputs.isEmpty()) {
            out.append("空");
        } else {
            for (int i = 0; i < inputs.size(); i++) {
                if (i > 0) {
                    out.append(' ');
                }
                out.append(inputs.get(i).label());
            }
        }
        ComboTracker.Progress next = state.tracker.progress(catalog);
        if (next != null && next.next() != null) {
            out.append(" · 下一步 ").append(next.next().label()).append("（").append(next.skill().name()).append("）");
        }
        for (WarriorSkill skill : catalog) {
            out.append("\n").append(skill.chain().cn()).append(" · ").append(skill.name())
                    .append(" ").append(skill.sequenceLabel())
                    .append(" · 槽 ").append(skill.slot() + 1)
                    .append(" · ").append(WarriorCombos.unlocked(skill, progress) ? "已解锁" : "未解锁");
            if (skill.displaces()) {
                out.append(" · 位移 ").append(String.format(Locale.ROOT, "%.1f", skill.dash())).append(" 格");
            }
            if (skill.interrupt() || skill.staggerTicks() > 0) {
                out.append(" · 打断/僵直");
            }
        }
        return out.toString();
    }
}
