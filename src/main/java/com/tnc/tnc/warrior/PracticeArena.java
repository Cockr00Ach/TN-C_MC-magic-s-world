package com.tnc.tnc.warrior;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.world.SkyIslandSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 天空岛上的共用演武场。
 *
 * <p>它不是独立维度了：就是岛上的一块石台，走过去就能用。台的四个角各有一个
 * 演武位，正中一台「演武终端」——右键终端选一位教官，教官当场显形，练习立刻开始。
 *
 * <p>台子是「按需建造」的：等天空岛生成完，第一次有人要用时由代码一次性砌出来
 * （连脚下地形一起找平），位置固定，不依赖任何存档预置。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class PracticeArena {
    /** 演武场在天空岛本地坐标系里的中心 X。 */
    public static final int LOCAL_X = 84;
    /** 演武场在天空岛本地坐标系里的中心 Z。 */
    public static final int LOCAL_Z = 256;

    private PracticeArena() {
    }

    /**
     * 演武场台心（世界坐标）。天空岛还没生成完时返回 {@code null}。
     *
     * <p>返回的 Y 是岛的原点高度，真正的台面高度在建台时按地形算。
     */
    public static BlockPos site(MinecraftServer server) {
        var island = SkyIslandSavedData.get(server.overworld());
        if (!island.isSkyIslandComplete()) return null;
        var origin = island.anchorPos("ORIGIN");
        if (origin == null) return null;
        return new BlockPos(origin.getX() + LOCAL_X, origin.getY(), origin.getZ() + LOCAL_Z);
    }

    /** 台子建好没有；没建就试着建（岛没生成完就什么都不做）。 */
    public static boolean ensure(MinecraftServer server) {
        var data = TrainingGroundData.get(server);
        if (data.arenaBuilt()) return true;
        var site = site(server);
        if (site == null) return false;
        build(server.overworld(), site.getX(), site.getZ());
        return data.arenaBuilt();
    }

    /** 一次性砌台：找平地形、铺台面、四角演武位、中央终端、南侧台阶。 */
    public static void build(ServerLevel level, int cx, int cz) {
        int half = TrainingGroundRules.HALF;
        int size = half * 2 + 1;
        // 先把台子占的区块加载出来：没加载的区块 heightmap 全是空值，
        // 直接读会把台面砌到世界底部去。
        for (int dx = -half; dx <= half; dx += 16) {
            for (int dz = -half; dz <= half; dz += 16) {
                level.getChunk((cx + dx) >> 4, (cz + dz) >> 4);
            }
        }
        int[][] tops = new int[size][size];
        int maxTop = Integer.MIN_VALUE;
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx + dx, cz + dz) - 1;
                tops[dx + half][dz + half] = top;
                if (top > maxTop) maxTop = top;
            }
        }
        if (maxTop < level.getMinBuildHeight() + 2) return;
        int deck = maxTop + 1;
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                int x = cx + dx, z = cz + dz;
                for (int y = deck + 1; y <= deck + 12; y++) {
                    var pos = new BlockPos(x, y, z);
                    if (!level.isEmptyBlock(pos)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
                level.setBlock(new BlockPos(x, deck, z), deckState(dx, dz, half), 3);
                int ground = tops[dx + half][dz + half];
                int bottom = Math.max(ground - 1, deck - 20);
                for (int y = deck - 1; y >= bottom; y--) {
                    var pos = new BlockPos(x, y, z);
                    if (level.isEmptyBlock(pos)) level.setBlock(pos, Blocks.STONE_BRICKS.defaultBlockState(), 2);
                }
            }
        }
        // 四周矮墙，南侧留一个五格宽的入口。
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                boolean rim = Math.abs(dx) == half || Math.abs(dz) == half;
                boolean gate = dz == half && Math.abs(dx) <= 2;
                if (rim && !gate) level.setBlock(new BlockPos(cx + dx, deck + 1, cz + dz),
                    Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState(), 3);
            }
        }
        // 四角灯柱。
        for (int sx : new int[]{-half + 3, half - 3}) {
            for (int sz : new int[]{-half + 3, half - 3}) {
                for (int i = 1; i <= 3; i++) {
                    level.setBlock(new BlockPos(cx + sx, deck + i, cz + sz),
                        Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState(), 3);
                }
                level.setBlock(new BlockPos(cx + sx, deck + 4, cz + sz),
                    Blocks.LANTERN.defaultBlockState(), 3);
            }
        }
        // 中央终端：一块 3×3 的台基，终端立在上面。
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(new BlockPos(cx + dx, deck, cz + dz),
                    Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 3);
            }
        }
        level.setBlock(new BlockPos(cx, deck + 1, cz),
            TNMod.PRACTICE_TERMINAL.get().defaultBlockState(), 3);
        // 南侧台阶：从台面下到岛面。
        for (int i = 0; i < 5; i++) {
            int z = cz + half + 1 + i;
            int y = deck - i;
            for (int dx = -2; dx <= 2; dx++) {
                level.setBlock(new BlockPos(cx + dx, y, z), Blocks.STONE_BRICKS.defaultBlockState(), 3);
                level.setBlock(new BlockPos(cx + dx, y + 1, z), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(new BlockPos(cx + dx, y + 2, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        TrainingGroundData.get(level.getServer()).markArena(level.dimension().location().toString(), cx, deck, cz);
        level.getServer().getPlayerList().broadcastSystemMessage(
            Component.literal("天空岛演武场已落成：岛西侧的青石台，中央终端可以召出教官。"), false);
    }

    /** 台面花纹：外圈黑石、四角演武位、中央台基、两条石环。 */
    private static BlockState deckState(int dx, int dz, int half) {
        int ax = Math.abs(dx), az = Math.abs(dz);
        if (ax == half || az == half) return Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        for (int pad = 0; pad < TrainingGroundRules.PAD_COUNT; pad++) {
            int padDx = ((pad & 1) == 0) ? -TrainingGroundRules.PAD_OFFSET : TrainingGroundRules.PAD_OFFSET;
            int padDz = (pad < 2) ? -TrainingGroundRules.PAD_OFFSET : TrainingGroundRules.PAD_OFFSET;
            if (Math.abs(dx - padDx) <= TrainingGroundRules.PAD_HALF
                && Math.abs(dz - padDz) <= TrainingGroundRules.PAD_HALF) {
                return padState(pad, dx - padDx, dz - padDz);
            }
        }
        if (ax <= 3 && az <= 3) return Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
        if (ax == 9 || az == 9) return Blocks.SMOOTH_STONE.defaultBlockState();
        return ((ax + az) % 7 == 0)
            ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
            : Blocks.POLISHED_ANDESITE.defaultBlockState();
    }

    /** 演武位花纹：底色 + 中央 5×5 徽记，四种角色四个色。 */
    private static BlockState padState(int pad, int dx, int dz) {
        boolean emblem = Math.abs(dx) <= 2 && Math.abs(dz) <= 2;
        return switch (pad) {
            case 1 -> emblem ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.YELLOW_TERRACOTTA.defaultBlockState();
            case 2 -> emblem ? Blocks.LAPIS_BLOCK.defaultBlockState() : Blocks.BLUE_TERRACOTTA.defaultBlockState();
            case 3 -> emblem ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.CYAN_TERRACOTTA.defaultBlockState();
            default -> emblem ? Blocks.IRON_BLOCK.defaultBlockState() : Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState();
        };
    }

    /** 玩家是否站在演武场里。 */
    public static boolean inside(ServerPlayer p) {
        return arenaOf(p) != null;
    }

    /** 玩家脚下这座台；不在台上返回 null。 */
    public static TrainingGroundData.Arena arenaOf(ServerPlayer p) {
        return TrainingGroundData.get(p.server)
            .arenaAt(p.level().dimension().location().toString(), p.getX(), p.getY(), p.getZ());
    }

    public static BlockPos center(MinecraftServer server) {
        var data = TrainingGroundData.get(server);
        return data.arenaBuilt() ? data.arenaCenter() : null;
    }

    /** 终端右键：只负责把面板推给客户端，选谁由面板发回来。 */
    public static void openTerminal(ServerPlayer p) {
        if (!ensure(p.server)) {
            p.sendSystemMessage(Component.literal("天空岛还没生成完，演武场暂时无法使用。"));
            return;
        }
        TemperingEvents.sync(p, true);
    }

    /**
     * 召出一位教官并开始练习。
     *
     * @param role {@code counter} / {@code guard} / {@code blade} / {@code wind}
     */
    public static boolean summon(ServerPlayer p, String role) {
        if (!TrainingGround.qualified(p)) {
            p.sendSystemMessage(Component.literal("需要战士天赋、生存模式且未倒地，才能开始演武。"));
            return false;
        }
        var data = TrainingGroundData.get(p.server);
        if (!data.arenaBuilt() && !ensure(p.server)) {
            p.sendSystemMessage(Component.literal("天空岛还没生成完，演武场暂时无法使用。"));
            return false;
        }
        if (!inside(p)) {
            p.sendSystemMessage(Component.literal("先走到天空岛西侧的演武场，再用中央终端召唤教官。"));
            return false;
        }
        String normalized = normalizeRole(role);
        TrainingGround.dismissAll(p);
        var arena = arenaOf(p);
        if (arena == null) {
            p.sendSystemMessage(Component.literal("先走到天空岛西侧的演武场，再用中央终端召唤教官。"));
            return false;
        }
        int pad = data.claim(p.getUUID(), arena.key());
        if (pad < 0) {
            p.sendSystemMessage(Component.literal("四个演武位都有人在用，先等一会儿或请对方结束。"));
            return false;
        }
        var c = arena.center();
        int px = TrainingGroundRules.padX(pad, c.getX());
        int pz = TrainingGroundRules.padZ(pad, c.getZ());
        double feet = c.getY() + 1;
        p.teleportTo(p.serverLevel(), px + 0.5, feet, pz + 0.5, 0, 0);
        settle(p);
        TrainingGround.spawnTutor(p, arena, pad, normalized);
        if ("counter".equals(normalized)) {
            TemperingTraining.start(p);
        } else {
            var tutor = SpecialistTrainers.trainers(p);
            if (!tutor.isEmpty()) SpecialistTrainers.begin(p, tutor.get(0));
        }
        TemperingEvents.sync(p);
        return true;
    }

    /** 结束练习：收起教官、释放演武位。 */
    public static boolean dismiss(ServerPlayer p) {
        if (!inside(p)) return false;
        TrainingGround.dismissAll(p);
        TrainingGroundData.get(p.server).release(p.getUUID());
        TemperingEvents.sync(p);
        p.sendSystemMessage(Component.literal("已结束演武，教官退场，演武位空出。"));
        return true;
    }

    /** 传送到演武场（给命令用；正常玩法靠走）。 */
    public static boolean teleportIn(ServerPlayer p) {
        if (!ensure(p.server)) {
            p.sendSystemMessage(Component.literal("天空岛还没生成完，演武场暂时无法使用。"));
            return false;
        }
        var c = TrainingGroundData.get(p.server).arenaCenter();
        if (c == null) {
            p.sendSystemMessage(Component.literal("天空岛还没生成完，演武场暂时无法使用。"));
            return false;
        }
        double x = c.getX() + 0.5;
        double z = c.getZ() + TrainingGroundRules.HALF - 2.5;
        p.teleportTo(p.serverLevel(), x, c.getY() + 1, z, 180f, 0f);
        settle(p);
        TemperingEvents.sync(p);
        return true;
    }

    static String normalizeRole(String role) {
        if (role != null) {
            for (String name : InstructorEntity.VARIANTS) {
                if (name.equals(role)) return name;
            }
        }
        return "counter";
    }

    static void settle(ServerPlayer p) {
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
        p.clearFire();
    }

    /** 老存档里还留在旧训练场维度的玩家：登录时送回演武场，别把人锁在虚空里。 */
    static void rescueLegacy(ServerPlayer p) {
        if (!p.level().dimension().location().toString().equals("tnc:training_ground")) return;
        if (!ensure(p.server)) return;
        teleportIn(p);
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = event.getServer();
        if (server == null || server.getTickCount() % 40 != 0) return;
        if (TrainingGroundData.get(server).arenaBuilt()) return;
        ensure(server);
    }
}
