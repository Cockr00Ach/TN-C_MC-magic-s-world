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
