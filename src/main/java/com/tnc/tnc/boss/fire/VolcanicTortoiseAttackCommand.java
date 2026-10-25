package com.tnc.tnc.boss.fire;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.tnc.tnc.TNMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.List;

/**
 * 火山巨龟的单技能调试入口。
 *
 * <p>只有带 {@code -Dtnc.tortoise.attack.debug=true} 启动时才注册，正式流程里默认不存在：
 * <pre>/tnc_bite</pre> 触发 32 格内最近一只火山巨龟对执行者咬一次；
 * <pre>/tnc_bite true</pre> 同时把本次咬合的扇区范围用粒子画出来。</p>
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class VolcanicTortoiseAttackCommand {

    private VolcanicTortoiseAttackCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        if (!VolcanicTortoiseEntity.ATTACK_DEBUG) {
            return;
        }
        event.getDispatcher().register(Commands.literal("tnc_bite")
                .requires(source -> source.hasPermission(2))
                .executes(context -> trigger(context.getSource(), false))
                .then(Commands.argument("range", BoolArgumentType.bool())
                        .executes(context -> trigger(context.getSource(),
                                BoolArgumentType.getBool(context, "range")))));
        event.getDispatcher().register(Commands.literal("tnc_slam")
                .requires(source -> source.hasPermission(2))
                .executes(context -> triggerSlam(context.getSource(), false))
                .then(Commands.argument("range", BoolArgumentType.bool())
                        .executes(context -> triggerSlam(context.getSource(),
                                BoolArgumentType.getBool(context, "range")))));
        // 技能 B：大地震波（独立施法，不产生火环）。
        event.getDispatcher().register(Commands.literal("tnc_quake")
                .requires(source -> source.hasPermission(2))
                .executes(context -> triggerQuake(context.getSource())));
        // 技能 C：嘴前蓄力 24–32 tick 后喷吐一发火球。
        event.getDispatcher().register(Commands.literal("tnc_spit")
                .requires(source -> source.hasPermission(2))
                .executes(context -> triggerSpit(context.getSource())));
        // 技能 E：二阶段火山喷发（火山口弹出 21 颗抛射火球）。
        event.getDispatcher().register(Commands.literal("tnc_erupt")
                .requires(source -> source.hasPermission(2))
                .executes(context -> triggerErupt(context.getSource())));

        // 技能 D：二阶段熔岩之柱（前方走廊预警 + 分批火柱）。
        event.getDispatcher().register(Commands.literal("tnc_lava_pillar")
                .requires(source -> source.hasPermission(2))
                .executes(context -> triggerPillar(context.getSource())));

        // 调试：狂暴 / 虚弱 / 清除状态。
        event.getDispatcher().register(Commands.literal("tnc_enrage")
                .requires(source -> source.hasPermission(2))
                .executes(context -> triggerEnrage(context.getSource(), "enrage"))
                .then(Commands.literal("weak")
                        .executes(context -> triggerEnrage(context.getSource(), "weak")))
                .then(Commands.literal("off")
                        .executes(context -> triggerEnrage(context.getSource(), "off"))));
    }

    /** 调试：enrage = 立刻狂暴，weak = 立刻进入虚弱易伤，off = 清掉状态。 */
    private static int triggerEnrage(CommandSourceStack source, String mode) {
        VolcanicTortoiseEntity boss = nearest(source);
        if (boss == null) {
            source.sendFailure(Component.literal("32 格内没有火山巨龟"));
            return 0;
        }
        switch (mode) {
            case "weak" -> boss.forceWeak();
            case "off" -> boss.clearEnrageState();
            default -> boss.forceEnrage();
        }
        source.sendSuccess(() -> Component.literal("当前状态：" + boss.enrageState()
                + "（0 正常 / 1 狂暴 / 2 虚弱），剩余 " + boss.enrageTicksLeft() + " tick"), false);
        return 1;
    }

    /** 调试：让最近一只巨龟对执行者开始一次喷吐（蓄力后自动发射）。 */
    private static int triggerSpit(CommandSourceStack source) {
        VolcanicTortoiseEntity boss = nearest(source);
        if (boss == null) {
            source.sendFailure(Component.literal("32 格内没有火山巨龟"));
            return 0;
        }
        boolean started = boss.forceSpit(source.getPlayer());
        source.sendSuccess(() -> Component.literal(started
                ? "已开始喷吐蓄力（约 1.2–1.6 秒后发射）"
                : "BOSS 正在攻击/转阶段/已死亡，本次未触发"), false);
        return started ? 1 : 0;
    }

    /** 调试：只触发技能 B（大地震波），不会同时放火环。 */
    private static int triggerQuake(CommandSourceStack source) {
        VolcanicTortoiseEntity boss = nearest(source);
        if (boss == null) {
            source.sendFailure(Component.literal("32 格内没有火山巨龟"));
            return 0;
        }
        boolean started = boss.forceQuake();
        source.sendSuccess(() -> Component.literal(started
                ? "已触发大地震波（只有地面起伏，没有火环）"
                : "BOSS 正在攻击/转阶段/已死亡，本次未触发"), false);
        return started ? 1 : 0;
    }

    /** 调试：立即放一次火山喷发（蓄势 30–40 tick，随后 3 批共 21 颗）。 */
    private static int triggerErupt(CommandSourceStack source) {
        VolcanicTortoiseEntity boss = nearest(source);
        if (boss == null) {
            source.sendFailure(Component.literal("32 格内没有火山巨龟"));
            return 0;
        }
        boolean started = boss.forceEruption();
        source.sendSuccess(() -> Component.literal(started
                ? "已触发火山喷发：蓄势后 3 批共 21 颗抛射火球"
                : "BOSS 正在攻击/转阶段/已死亡，本次未触发"), false);
        return started ? 1 : 0;
    }

    /** 调试：立即放一次熔岩之柱（走廊预警 36 tick 后分批起火柱）。 */
    private static int triggerPillar(CommandSourceStack source) {
        VolcanicTortoiseEntity boss = nearest(source);
        if (boss == null) {
            source.sendFailure(Component.literal("32 格内没有火山巨龟"));
            return 0;
        }
        boolean started = boss.forcePillar();
        source.sendSuccess(() -> Component.literal(started
                ? "已触发熔岩之柱：36 tick 预警，随后 5–7 根火柱分批冲起"
                : "BOSS 正在攻击/转阶段/已死亡，本次未触发"), false);
        return started ? 1 : 0;
    }

    private static VolcanicTortoiseEntity nearest(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (source.getPlayer() == null) {
            return null;
        }
        AABB box = source.getPlayer().getBoundingBox().inflate(32.0D);
        List<VolcanicTortoiseEntity> bosses = level.getEntitiesOfClass(VolcanicTortoiseEntity.class, box);
        return bosses.stream()
                .min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(source.getPlayer())))
                .orElse(null);
    }

    /** 调试：让 32 格内最近一只巨龟立刻重踏一次；range=true 时把 22 格最终半径画出来。 */
    private static int triggerSlam(CommandSourceStack source, boolean showRing) {
        ServerLevel level = source.getLevel();
        if (source.getPlayer() == null) {
            source.sendFailure(Component.literal("需要玩家执行这个调试命令"));
            return 0;
        }
        AABB box = source.getPlayer().getBoundingBox().inflate(32.0D);
        List<VolcanicTortoiseEntity> bosses = level.getEntitiesOfClass(VolcanicTortoiseEntity.class, box);
        VolcanicTortoiseEntity boss = bosses.stream()
                .min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(source.getPlayer())))
                .orElse(null);
        if (boss == null) {
            source.sendFailure(Component.literal("32 格内没有火山巨龟"));
            return 0;
        }
        boolean started = boss.forceSlam(showRing);
        source.sendSuccess(() -> Component.literal(started
                ? "已触发重踏" + (showRing ? "（并显示 22 格半径）" : "")
                : "BOSS 正在攻击/转阶段/已死亡，本次未触发"), false);
        return started ? 1 : 0;
    }

    private static int trigger(CommandSourceStack source, boolean showRange) {
        ServerLevel level = source.getLevel();
        if (source.getPlayer() == null) {
            source.sendFailure(Component.literal("需要玩家执行这个调试命令"));
            return 0;
        }
        AABB box = source.getPlayer().getBoundingBox().inflate(32.0D);
        List<VolcanicTortoiseEntity> bosses = level.getEntitiesOfClass(VolcanicTortoiseEntity.class, box);
        VolcanicTortoiseEntity boss = bosses.stream()
                .min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(source.getPlayer())))
                .orElse(null);
        if (boss == null) {
            source.sendFailure(Component.literal("32 格内没有火山巨龟"));
            return 0;
        }
        boolean started = boss.forceBite(source.getPlayer(), showRange);
        source.sendSuccess(() -> Component.literal(started
                ? "已触发撕咬" + (showRange ? "（并显示范围）" : "")
                : "BOSS 正在攻击/转阶段/已死亡，本次未触发"), false);
        return started ? 1 : 0;
    }
}
