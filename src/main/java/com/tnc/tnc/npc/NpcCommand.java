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
 *   /tnc npc list                            列出登记（含锚点与偏移）
 *   /tnc npc here &lt;id&gt;                       ★ 以你脚下位置登记；**自动判断你离哪个天空岛锚点近**
 *   /tnc npc here &lt;id&gt; &lt;锚点&gt;              指定锚点登记（ARRIVAL/CENTER/ORIGIN/GROUND_PORTAL）
 *   /tnc npc place &lt;id&gt; &lt;锚点&gt; &lt;dx&gt; &lt;dy&gt; &lt;dz&gt;   按"锚点+偏移"登记（锚点写 ABSOLUTE 则为世界坐标）
 *   /tnc npc respawn &lt;id&gt;                   先清掉附近的、再按登记坐标重放一个
 *   /tnc npc purge &lt;id|all&gt;                 删掉已加载范围内所有该类型 NPC
 *   /tnc npc remove &lt;id&gt;                    取消登记（不删已存在的实体）
 * </pre>
 *
 * <p>权限等级 2。<b>放在独立类里</b>而不是塞进 {@code MagicStoneCommand}：
 * 那个文件已经 581 行、归魔法专题维护，NPC 的命令不该混进去。
 *
 * <h2>为什么位置要"锚点+偏移"</h2>
 * 天空岛每个存档位置都不同（生成时在出生点附近选点），所以写死世界坐标换存档就废了。
 * 存相对锚点的偏移，放置时按该存档的真实岛坐标算 —— NPC 就跟着岛走。
 */
public final class NpcCommand {

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tnc")
                .then(Commands.literal("npc")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("list")
                                .executes(ctx -> list(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("here")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> here(ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "id"), null))
                                        .then(Commands.argument("anchor", StringArgumentType.word())
                                                .executes(ctx -> here(ctx.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(ctx, "id"),
                                                        StringArgumentType.getString(ctx, "anchor"))))))
                        .then(Commands.literal("place")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.argument("anchor", StringArgumentType.word())
                                                .then(Commands.argument("dx", IntegerArgumentType.integer())
                                                        .then(Commands.argument("dy", IntegerArgumentType.integer())
                                                                .then(Commands.argument("dz", IntegerArgumentType.integer())
                                                                        .executes(ctx -> place(
                                                                                ctx.getSource().getPlayerOrException(),
                                                                                StringArgumentType.getString(ctx, "id"),
                                                                                StringArgumentType.getString(ctx, "anchor"),
                                                                                IntegerArgumentType.getInteger(ctx, "dx"),
                                                                                IntegerArgumentType.getInteger(ctx, "dy"),
                                                                                IntegerArgumentType.getInteger(ctx, "dz")))))))))
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
                                                StringArgumentType.getString(ctx, "id")))))
                        .then(Commands.literal("status")
                                .executes(ctx -> status(ctx.getSource().getPlayerOrException())))));
    }

    // ------------------------------------------------------------------ 登记

    /** 按"锚点+偏移"登记。锚点写 ABSOLUTE 时，dx/dy/dz 就是世界坐标。 */
    private static int place(ServerPlayer player, String npcId, String anchorArg,
                             int dx, int dy, int dz) {
        ServerLevel level = player.serverLevel();
        String anchor = normalizeAnchor(anchorArg);
        if (anchor == null) {
            player.displayClientMessage(Component.literal(
                    "\u00a7c[TN-C] \u672a\u77e5\u951a\u70b9\uff1a" + anchorArg
                            + "\uff08\u53ef\u7528\uff1aARRIVAL / CENTER / ORIGIN / GROUND_PORTAL / ABSOLUTE\uff09"), false);
            return 0;
        }
        NpcPlacementSavedData data = NpcPlacementSavedData.get(level);
        data.put(new NpcPlacementSavedData.Placement(npcId, anchor, dx, dy, dz));
        int spawned = data.respawn(level, npcId);
        BlockPos resolved = data.resolve(level, data.get(npcId));
        player.displayClientMessage(Component.literal(
                "\u00a7a[TN-C] \u5df2\u767b\u8bb0 \u00a7e" + npcId + "\u00a7a \u951a\u70b9=\u00a7e" + anchor
                        + "\u00a7a \u504f\u79fb=" + dx + "/" + dy + "/" + dz
                        + (resolved != null ? "\u00a7a \u2192 \u4e16\u754c\u5750\u6807 " + resolved.getX() + "/"
                        + resolved.getY() + "/" + resolved.getZ() : "\u00a7c\uff08\u951a\u70b9\u5c1a\u672a\u5c31\u7eea\uff09")
                        + "\u00a77 [\u653e\u7f6e " + spawned + "]"), false);
        return 1;
    }

    /**
     * 以玩家脚下那格登记，**自动判断离哪个天空岛锚点近**。
     *
     * <p>这样你只需要站到想让他站的位置敲一条命令 —— 偏移由我们算，
     * 换存档时他会跟着岛一起出现在同一个相对位置。
     */
    private static int here(ServerPlayer player, String npcId, String anchorArg) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();

        String anchor;
        if (anchorArg != null) {
            anchor = normalizeAnchor(anchorArg);
            if (anchor == null) {
                player.displayClientMessage(Component.literal(
                        "\u00a7c[TN-C] \u672a\u77e5\u951a\u70b9\uff1a" + anchorArg), false);
                return 0;
            }
        } else {
            anchor = nearestAnchor(level, pos);
            if (anchor == null) {
                player.displayClientMessage(Component.literal(
                        "\u00a7c[TN-C] \u5929\u7a7a\u5c9b\u5c1a\u672a\u5c31\u7eea\uff0c\u65e0\u6cd5\u81ea\u52a8\u5224\u65ad\u951a\u70b9\u3002"
                                + "\u53ef\u7528\uff1a/tnc npc here " + npcId + " ABSOLUTE"), false);
                return 0;
            }
        }

        BlockPos base = NpcPlacementSavedData.ABSOLUTE.equals(anchor)
                ? BlockPos.ZERO
                : SkyIslandAnchors.resolve(level, SkyIslandAnchors.Anchor.valueOf(anchor));
        if (base == null) {
            player.displayClientMessage(Component.literal(
                    "\u00a7c[TN-C] \u951a\u70b9 " + anchor + " \u7684\u5750\u6807\u8fd8\u6ca1\u5c31\u7eea"), false);
            return 0;
        }
        return place(player, npcId, anchor,
                pos.getX() - base.getX(), pos.getY() - base.getY(), pos.getZ() - base.getZ());
    }

    /** 找离玩家最近的天空岛锚点（只在岛已生成时有效）。 */
    private static String nearestAnchor(ServerLevel level, BlockPos pos) {
        if (!SkyIslandAnchors.isComplete(level)) {
            return null;
        }
        String best = null;
        double bestDist = Double.MAX_VALUE;
        for (SkyIslandAnchors.Anchor a : SkyIslandAnchors.Anchor.values()) {
            BlockPos p = SkyIslandAnchors.resolve(level, a);
            if (p == null) {
                continue;
            }
            double d = p.distSqr(pos);
            if (d < bestDist) {
                bestDist = d;
                best = a.name();
            }
        }
        return best;
    }

    private static String normalizeAnchor(String arg) {
        if (arg == null || arg.isEmpty()) {
            return null;
        }
        String up = arg.toUpperCase(java.util.Locale.ROOT);
        if (NpcPlacementSavedData.ABSOLUTE.equals(up)) {
            return NpcPlacementSavedData.ABSOLUTE;
        }
        // 允许省略 SKY_ 前缀、也允许直接写锚点名
        for (SkyIslandAnchors.Anchor a : SkyIslandAnchors.Anchor.values()) {
            if (a.name().equals(up)) {
                return a.name();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 其它

    private static int respawn(ServerPlayer player, String npcId) {
        ServerLevel level = player.serverLevel();
        NpcPlacementSavedData data = NpcPlacementSavedData.get(level);
        if (data.get(npcId) == null) {
            player.displayClientMessage(Component.literal(
                    "\u00a7c[TN-C] \u6ca1\u6709\u767b\u8bb0\u8fc7 " + npcId + "\uff0c\u5148\u7528 place/here"), false);
            return 0;
        }
        int n = data.respawn(level, npcId);
        BlockPos resolved = data.resolve(level, data.get(npcId));
        player.displayClientMessage(Component.literal(
                "\u00a7a[TN-C] \u91cd\u653e " + npcId + "\uff1a" + n + " \u4e2a"
                        + (resolved != null ? "\uff08\u5750\u6807 " + resolved.getX() + "/" + resolved.getY()
                        + "/" + resolved.getZ() + "\uff09" : "\u00a7c\uff08\u951a\u70b9\u672a\u5c31\u7eea\uff09")), false);
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
     * 把一个类型的 NPC <b>全部删掉</b>（已加载范围内），回报删了几个。
     * 传 {@code all} 表示清掉所有 TN-C 的 NPC。
     */
    private static int purge(ServerPlayer player, String npcId) {
        ServerLevel level = player.serverLevel();
        int removed = 0;
        String[] ids = ("@".equals(npcId) || "all".equalsIgnoreCase(npcId))
                ? new String[]{"self"}                      // 以后加了 NPC 就在这里补
                : new String[]{npcId};
        for (String id : ids) {
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

    /**
     * {@code /tnc npc status} —— 真实诊断：登记说"该在哪"，这里查"**实际上在不在**"。
     *
     * <p>为什么需要它：{@code list} 只报告记表内容，看不出实体到底有没有生成成功。
     * 用户遇到过"登记正确、命令回显正确，但看不见人" —— 这条命令一次就能分辨是
     * <b>没生成</b> 还是 <b>生成了但你看不见</b>（高度不对/客户端没渲染）。
     */
    private static int status(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        NpcPlacementSavedData data = NpcPlacementSavedData.get(level);
        if (data.all().isEmpty()) {
            player.displayClientMessage(Component.literal(
                    "\u00a77[TN-C] \u6ca1\u6709\u767b\u8bb0\u8fc7 NPC\u3002\u7528 /tnc npc here <id> \u767b\u8bb0"), false);
            return 0;
        }
        for (NpcPlacementSavedData.Placement p : data.all().values()) {
            net.minecraft.world.entity.EntityType<?> type =
                    net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                    com.tnc.tnc.TNMod.MODID, p.npcId()));
            if (type == null) {
                player.displayClientMessage(Component.literal("\u00a7c" + p.npcId() + " \u7c7b\u578b\u672a\u6ce8\u518c"), false);
                continue;
            }
            BlockPos want = data.resolve(level, p);
            java.util.List<? extends net.minecraft.world.entity.Entity> found = level.getEntities(
                    net.minecraft.world.level.entity.EntityTypeTest.forClass(net.minecraft.world.entity.Entity.class),
                    new net.minecraft.world.phys.AABB(player.blockPosition()).inflate(512.0D),
                    e -> e.getType() == type);

            player.displayClientMessage(Component.literal("\u00a7e--- " + p.npcId() + " ---"), false);
            player.displayClientMessage(Component.literal("\u00a77\u767b\u8bb0: \u00a7f" + p.anchor()
                    + " " + p.dx() + "/" + p.dy() + "/" + p.dz()
                    + (want != null ? " \u2192 \u00a7f" + want.getX() + "/" + want.getY() + "/" + want.getZ()
                                    : " \u00a7c(\u951a\u70b9\u672a\u5c31\u7eea)")), false);
            if (found.isEmpty()) {
                player.displayClientMessage(Component.literal(
                        "\u00a7c\u5b9e\u4f53: 512 \u683c\u5185\u4e00\u4e2a\u90fd\u6ca1\u6709\u3002"
                                + "\u53ef\u80fd\uff1a\u533a\u5757\u672a\u52a0\u8f7d / \u5806\u5728\u65b9\u5757\u91cc / \u653e\u7f6e\u5931\u8d25"), false);
            } else {
                for (net.minecraft.world.entity.Entity e : found) {
                    BlockPos ep = e.blockPosition();
                    player.displayClientMessage(Component.literal(
                            "\u00a7a\u5b9e\u4f53: \u00a7f" + ep.getX() + "/" + ep.getY() + "/" + ep.getZ()
                                    + "\u00a77 \u8ddd\u4f60 " + (int) Math.sqrt(e.distanceToSqr(player)) + " \u683c"
                                    + "  Y\u5dee " + (ep.getY() - player.blockPosition().getY())
                                    + "  \u52a0\u8f7d=" + e.isAlive()), false);
                }
            }
        }
        return 1;
    }

    private static int list(ServerPlayer player) {        NpcPlacementSavedData data = NpcPlacementSavedData.get(player.serverLevel());
        Map<String, NpcPlacementSavedData.Placement> all = data.all();
        if (all.isEmpty()) {
            player.displayClientMessage(Component.literal("\u00a77[TN-C] \u56fa\u5b9a NPC\uff1a\uff08\u7a7a\uff09"), false);
            return 0;
        }
        all.values().forEach(p -> {
            BlockPos resolved = data.resolve(player.serverLevel(), p);
            player.displayClientMessage(Component.literal(
                    "\u00a7e" + p.npcId() + "\u00a77 \u951a\u70b9 \u00a7f" + p.anchor()
                            + "\u00a77 \u504f\u79fb \u00a7f" + p.dx() + " " + p.dy() + " " + p.dz()
                            + (resolved != null ? "\u00a77 \u2192 \u00a7f" + resolved.getX() + " "
                            + resolved.getY() + " " + resolved.getZ() : "\u00a78 (\u951a\u70b9\u672a\u5c31\u7eea)")), false);
        });
        return all.size();
    }
}
