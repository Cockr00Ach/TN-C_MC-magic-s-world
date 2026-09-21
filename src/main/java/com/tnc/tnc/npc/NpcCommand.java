package com.tnc.tnc.npc;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;

/**
 * {@code /tnc npc ...} —— 固定 NPC 的调试与管理命令。
 *
 * <pre>
 *   /tnc npc list                      列出已登记的固定 NPC 与坐标
 *   /tnc npc place &lt;id&gt; &lt;x&gt; &lt;y&gt; &lt;z&gt;   登记/改坐标并立刻放置（id 如 self）
 *   /tnc npc here &lt;id&gt;                把你脚下这格登记为该 NPC 的位置
 *   /tnc npc respawn &lt;id&gt;             先清掉附近的、再按登记坐标重放一个
 *   /tnc npc remove &lt;id&gt;              取消登记（不删已存在的实体）
 * </pre>
 *
 * <p>权限等级 2（和 {@code /tnc} 的其它子命令一致）。
 * <b>放在独立类里</b>而不是塞进 {@code MagicStoneCommand}：那个文件已经 581 行、
 * 归魔法专题维护，NPC 的命令不该混进去。
 */
public final class NpcCommand {

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tnc")
                .then(Commands.literal("npc")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("list")
                                .executes(ctx -> list(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("place")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                                .executes(ctx -> place(
                                                                        ctx.getSource().getPlayerOrException(),
                                                                        StringArgumentType.getString(ctx, "id"),
                                                                        new BlockPos(
                                                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                                                IntegerArgumentType.getInteger(ctx, "y"),
                                                                                IntegerArgumentType.getInteger(ctx, "z")))))))))
                        .then(Commands.literal("here")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            return place(player, StringArgumentType.getString(ctx, "id"),
                                                    player.blockPosition());
                                        })))
                        .then(Commands.literal("respawn")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> respawn(ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "id")))))
                        .then(Commands.literal("purge")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> purge(ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "id")))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> remove(ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "id")))))));
    }

    private static int place(ServerPlayer player, String npcId, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        NpcPlacementSavedData data = NpcPlacementSavedData.get(level);
        data.put(npcId, pos);
        int spawned = data.respawn(level, npcId);
        player.displayClientMessage(Component.literal(
                "\u00a7a[TN-C] \u5df2\u767b\u8bb0\u56fa\u5b9a NPC \u00a7e" + npcId
                        + "\u00a7a @ " + pos.getX() + "/" + pos.getY() + "/" + pos.getZ()
                        + "\uff08\u672c\u6b21\u653e\u7f6e " + spawned + " \u4e2a\uff09"), false);
        return 1;
    }

    private static int respawn(ServerPlayer player, String npcId) {
        ServerLevel level = player.serverLevel();
        NpcPlacementSavedData data = NpcPlacementSavedData.get(level);
        if (data.get(npcId) == null) {
            player.displayClientMessage(Component.literal(
                    "\u00a7c[TN-C] \u6ca1\u6709\u767b\u8bb0\u8fc7 " + npcId + "\uff0c\u5148\u7528 place/here"), false);
            return 0;
        }
        int n = data.respawn(level, npcId);
        player.displayClientMessage(Component.literal(
                "\u00a7a[TN-C] \u91cd\u653e " + npcId + "\uff1a" + n + " \u4e2a"), false);
        return n;
    }

    private static int remove(ServerPlayer player, String npcId) {
        NpcPlacementSavedData data = NpcPlacementSavedData.get(player.serverLevel());
        boolean ok = data.remove(npcId);
        player.displayClientMessage(Component.literal(
                ok ? "\u00a7a[TN-C] \u5df2\u53d6\u6d88\u767b\u8bb0 " + npcId
                   : "\u00a7c[TN-C] \u6ca1\u6709\u767b\u8bb0\u8fc7 " + npcId), false);
        return ok ? 1 : 0;
    }

    /**
     * 把一个类型的 NPC <b>全部删掉</b>（已加载范围内），并返回删了几个。
     *
     * <p>用途：试验期间刷多了、要"世界里只留一个"。传 {@code all} 表示清掉所有 TN-C 的 NPC。
     */
    private static int purge(ServerPlayer player, String npcId) {
        ServerLevel level = player.serverLevel();
        int removed = 0;
        for (String id : "@".equals(npcId) || "all".equalsIgnoreCase(npcId)
                ? new String[]{"self"}                      // 以后加了 NPC 就在这里补
                : new String[]{npcId}) {
        net.minecraft.world.entity.EntityType<?> type =
                net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                com.tnc.tnc.TNMod.MODID, id));
            if (type == null) {
                player.displayClientMessage(Component.literal(
                        "\u00a7c[TN-C] \u672a\u77e5 NPC id\uff1a" + id), false);
                continue;
            }
            // 查已加载范围内的所有该类型实体。
            // 边界自己拼：Level 上**没有** getWorldBounds()（这个版本没有，读签名确认），
            // 所以用 build 高度 + 一个很大的水平范围。原版内部按 chunk 分桶，只遍历已加载的。
            net.minecraft.world.phys.AABB everywhere = new net.minecraft.world.phys.AABB(
                    -3.0E7D, level.getMinBuildHeight(), -3.0E7D,
                     3.0E7D, level.getMaxBuildHeight(),  3.0E7D);
            java.util.List<net.minecraft.world.entity.Entity> hits = level.getEntities(
                    net.minecraft.world.level.entity.EntityTypeTest.forClass(net.minecraft.world.entity.Entity.class),
                    everywhere,
                    e -> e.getType() == type);
            for (net.minecraft.world.entity.Entity e : hits) {
                e.discard();
                removed++;
            }
        }
        player.displayClientMessage(Component.literal(
                "\u00a7a[TN-C] \u5df2\u6e05\u7406 " + removed + " \u4e2a " + npcId), false);
        return removed;
    }

    private static int list(ServerPlayer player) {
        NpcPlacementSavedData data = NpcPlacementSavedData.get(player.serverLevel());
        Map<String, BlockPos> all = data.all();
        if (all.isEmpty()) {
            player.displayClientMessage(Component.literal("\u00a77[TN-C] \u56fa\u5b9a NPC\uff1a\uff08\u7a7a\uff09"), false);
            return 0;
        }
        all.forEach((id, pos) -> player.displayClientMessage(Component.literal(
                "\u00a7e" + id + " \u00a77@ \u00a7f" + pos.getX() + " " + pos.getY() + " " + pos.getZ()), false));
        return all.size();
    }
}
