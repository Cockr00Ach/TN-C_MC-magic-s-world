package com.tnc.tnc.npc;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tnc.tnc.magic.PlayerCastAnimations;
import com.tnc.tnc.npc.compat.SelfCastActorSupport;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /tnc anim ...} —— 玩家施法动作的<b>演示/预览命令</b>（2026-10-30）。
 *
 * <p>为什么要有它：用户在 Blockbench 里录了三条施法动作，要"在游戏里看得方便"。
 * 直接在玩家本人身上换模型改动太大（盔甲层 / 手持物 / 第一人称全要重做），
 * 所以先做<b>演示角色</b>：在玩家正前方放一个<b>持杖版 Self</b>
 * （{@code self_wand.geo.json} + {@code self_wand_bedrock.png} + 施法动作文件），
 * 敲命令切动作、循环看。想反复对比就 {@code loop}，想回站姿就 {@code stop}。
 *
 * <pre>
 *   /tnc anim                      帮助
 *   /tnc anim spawn [距离]         放一个持杖演示角色（默认 2.2 格，面朝你）
 *   /tnc anim play &lt;动作&gt;          播一遍（没角色会先自动放一个）
 *   /tnc anim loop &lt;动作&gt;          循环播（观察用）
 *   /tnc anim kind &lt;种类&gt;         按"法术种类"播：summon / standard / powerful
 *   /tnc anim stop                 停下、回站姿
 *   /tnc anim list                 动作表 ＋ 种类映射
 *   /tnc anim remove               删掉附近的演示角色
 * </pre>
 *
 * <p>动作名与映射的唯一来源是 {@link PlayerCastAnimations}：
 * 召唤 = {@code magicsummon}、常规 = {@code wavingstaff}、强力 = {@code playmagic_1}。
 *
 * <p>权限等级 2（与 {@code /tnc npc}、{@code /tnc canglan} 一致；演示角色会写进世界，不是纯客户端玩具）。
 * 没装 GeckoLib 时所有子命令只回一句"需要 GeckoLib"，不崩 ✓。
 */
@Mod.EventBusSubscriber(modid = "tnc")
public final class CastPreviewCommand {

    private CastPreviewCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> anim = Commands.literal("anim")
                .requires(source -> source.hasPermission(2))
                .executes(CastPreviewCommand::help)
                .then(Commands.literal("spawn")
                        .executes(ctx -> spawn(ctx, SelfCastActorSupport.DEFAULT_DISTANCE))
                        .then(Commands.argument("distance", DoubleArgumentType.doubleArg(0.5D, 8.0D))
                                .executes(ctx -> spawn(ctx, DoubleArgumentType.getDouble(ctx, "distance")))))
                .then(Commands.literal("play")
                        .then(castArgument().executes(ctx -> play(ctx, false))))
                .then(Commands.literal("loop")
                        .then(castArgument().executes(ctx -> play(ctx, true))))
                .then(Commands.literal("kind")
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((ctx, builder) ->
                                        SharedSuggestionProvider.suggest(kindKeys(), builder))
                                .executes(CastPreviewCommand::kind)))
                .then(Commands.literal("stop").executes(CastPreviewCommand::stop))
                .then(Commands.literal("list").executes(CastPreviewCommand::list))
                .then(Commands.literal("remove").executes(CastPreviewCommand::remove));

        event.getDispatcher().register(Commands.literal("tnc").then(anim));
    }

    /** 动作名参数（带补全）。 */
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> castArgument() {
        return Commands.argument("animation", StringArgumentType.word())
                .suggests((ctx, builder) ->
                        SharedSuggestionProvider.suggest(PlayerCastAnimations.animationNames(), builder));
    }

    private static String[] kindKeys() {
        PlayerCastAnimations[] all = PlayerCastAnimations.values();
        String[] keys = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            keys[i] = all[i].name().toLowerCase(java.util.Locale.ROOT);
        }
        return keys;
    }

    // ------------------------------------------------------------------ 子命令

    private static int help(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!SelfCastActorSupport.available()) {
            source.sendFailure(Component.literal("[TN-C] 施法动作需要 GeckoLib（整合包里自带）。"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "\u00a7e[TN-C] 施法动作预览\u00a77（演示角色 = 持杖版 Self）"), false);
        source.sendSuccess(() -> Component.literal(
                "\u00a7e/tnc anim \u00a7fplay <动作> \u00a77播一遍\u00a7f / \u00a7floop <动作> \u00a77循环\u00a7f"
                        + " / \u00a7fstop \u00a77回站姿\u00a7f / \u00a7fspawn [距离]\u00a77 放演示角色"), false);
        source.sendSuccess(() -> Component.literal(
                "\u00a77用 \u00a7flist \u00a77看动作表；\u00a7fkind summon|standard|powerful \u00a77按法术种类试"
                        + "\u00a77；按 \u00a7fF5 \u00a77切第三人称看自己"), false);
        return 1;
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx, double distance) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Entity actor = SelfCastActorSupport.spawnDemo(player, distance);
        if (actor == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "[TN-C] 演示角色放不出来（需要 GeckoLib，且 tnc:self 必须是 Bedrock 版本）。"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "\u00a7a[TN-C] 演示角色就位。\u00a77用 \u00a7f/tnc anim play wavingstaff\u00a77 开始看动作。"), false);
        return 1;
    }

    private static int play(CommandContext<CommandSourceStack> ctx, boolean loop) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        String animation = StringArgumentType.getString(ctx, "animation");

        PlayerCastAnimations kind = PlayerCastAnimations.byAnimation(animation);
        if (kind == null) {
            source.sendFailure(Component.literal("[TN-C] 未知施法动作：" + animation + "（可用："
                    + String.join(" / ", PlayerCastAnimations.animationNames()) + "）"));
            return 0;
        }

        Entity actor = SelfCastActorSupport.nearestDemo(player);
        boolean spawned = false;
        if (actor == null) {
            actor = SelfCastActorSupport.spawnDemo(player, SelfCastActorSupport.DEFAULT_DISTANCE);
            spawned = actor != null;
        }
        if (actor == null) {
            source.sendFailure(Component.literal("[TN-C] 没有演示角色，也放不出来（需要 GeckoLib）。"));
            return 0;
        }
        if (!SelfCastActorSupport.play(actor, animation, loop)) {
            source.sendFailure(Component.literal("[TN-C] 动作没播出来（这个演示角色不是持杖外观？）"));
            return 0;
        }
        boolean wasSpawned = spawned;
        source.sendSuccess(() -> Component.literal(
                (loop ? "\u00a7a[TN-C] 循环播放 " : "\u00a7a[TN-C] 播放 ")
                        + "\u00a7e" + animation + "\u00a77（" + kind.label() + "）"
                        + (wasSpawned ? "\u00a77 先放了一个演示角色" : "")), false);
        return 1;
    }

    private static int kind(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        String key = StringArgumentType.getString(ctx, "kind");
        PlayerCastAnimations kind = PlayerCastAnimations.byKey(key);
        if (kind == null) {
            source.sendFailure(Component.literal("[TN-C] 未知种类：" + key + "（可用："
                    + String.join(" / ", kindKeys()) + "）"));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        Entity actor = SelfCastActorSupport.nearestDemo(player);
        if (actor == null) {
            actor = SelfCastActorSupport.spawnDemo(player, SelfCastActorSupport.DEFAULT_DISTANCE);
        }
        if (actor == null || !kind.play(actor, false)) {
            source.sendFailure(Component.literal("[TN-C] 播放失败（需要 GeckoLib ＋ 持杖演示角色）。"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "\u00a7a[TN-C] " + kind.label() + " 法术 \u2192 \u00a7e" + kind.animation()), false);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Entity actor = SelfCastActorSupport.nearestDemo(player);
        if (actor == null) {
            ctx.getSource().sendFailure(Component.literal("[TN-C] 附近没有演示角色。"));
            return 0;
        }
        SelfCastActorSupport.stop(actor);
        ctx.getSource().sendSuccess(() -> Component.literal("\u00a7a[TN-C] 已停下，回到站姿。"), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("\u00a7e--- 玩家施法动作 ---"), false);
        for (PlayerCastAnimations kind : PlayerCastAnimations.values()) {
            source.sendSuccess(() -> Component.literal(
                    "\u00a7f" + kind.animation() + "\u00a77 \u2190 \u00a7f" + kind.label()
                            + " \u00a77(" + kind.name().toLowerCase(java.util.Locale.ROOT) + ")"), false);
        }
        source.sendSuccess(() -> Component.literal(
                "\u00a77文件：\u00a7fanimations/entity/player_magic-animation.json"), false);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int removed = SelfCastActorSupport.removeAll(player);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "\u00a7a[TN-C] 清掉 " + removed + " 个演示角色。"), false);
        return removed > 0 ? 1 : 0;
    }
}
