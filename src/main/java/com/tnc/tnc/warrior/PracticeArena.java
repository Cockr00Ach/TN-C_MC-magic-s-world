package com.tnc.tnc.warrior;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.world.SkyIslandSavedData;
import com.tnc.tnc.world.SkyIslandWorlds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
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
    /** 台面在岛原点上方 86 格，中央终端在它上方一格。 */
    private static final BlockPos DECK_OFFSET = new BlockPos(466, 86, 236);

    private PracticeArena() {
    }

    /**
     * 演武场台心（世界坐标）。天空岛还没生成完时返回 {@code null}。
     *
     * <p>台心和终端随存档的天空岛原点平移。
     */
    public static BlockPos site(MinecraftServer server) {
        var origin = SkyIslandSavedData.get(server.overworld()).anchorPos("ORIGIN");
        return origin == null ? null : origin.offset(DECK_OFFSET);
    }

    /** 台子建好没有；没建就试着建（岛没生成完就什么都不做）。 */
    public static boolean ensure(MinecraftServer server) {
        var site = site(server);
        if (site == null) return false;
        var level = SkyIslandWorlds.island(server);
        if (islandArena(server) == null) {
            build(level, site.getX(), site.getZ(), site.getY());
            if (islandArena(server) == null) return false;
        }
        retirePreviousArenas(server, level, site.subtract(DECK_OFFSET));
        return true;
    }

    private static boolean previousCenter(BlockPos origin, int x, int z) {
        int dx = x - origin.getX(), dz = z - origin.getZ();
        return dx == 285 && dz == 423 || dx == 84 && dz == 256;
    }

    /** Retire only the two former island sites, once the new arena can receive players. */
    private static void retirePreviousArenas(MinecraftServer server, ServerLevel island, BlockPos origin) {
        var data = TrainingGroundData.get(server);
        for (var arena : data.arenas()) {
            if (!previousCenter(origin, arena.x(), arena.z())) continue;
            ServerLevel oldLevel;
            if (arena.dimension().equals(island.dimension().location().toString())) oldLevel = island;
            else if (arena.dimension().equals(server.overworld().dimension().location().toString())) oldLevel = server.overworld();
            else continue;
            var terminal = arena.center().above();
            if (oldLevel.getBlockState(terminal).is(TNMod.PRACTICE_TERMINAL.get())) {
                oldLevel.setBlockAndUpdate(terminal, Blocks.AIR.defaultBlockState());
            }
            var bounds = new AABB(arena.center()).inflate(31, 8, 31);
            oldLevel.getEntitiesOfClass(InstructorEntity.class, bounds,
                    tutor -> tutor.getPersistentData().getBoolean(WarriorTutorial.MENTOR)
                            || tutor.getPersistentData().hasUUID(InstructorEntity.OWNER)).forEach(InstructorEntity::discard);
            oldLevel.getEntitiesOfClass(TestDummyEntity.class, bounds,
                    dummy -> dummy.getPersistentData().hasUUID("DummyOwner")).forEach(TestDummyEntity::discard);
            data.retireArena(arena.dimension(), arena.x(), arena.z());
        }
    }

    /** The former permanent mentor may be read from disk after the arena has moved. */
    @SubscribeEvent
    public static void removeFormerMentor(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof InstructorEntity tutor)
                || !tutor.getPersistentData().getBoolean(WarriorTutorial.MENTOR)) return;
        var server = level.getServer();
        var origin = SkyIslandSavedData.get(level).anchorPos("ORIGIN");
        if (origin == null || !previousCenter(origin, tutor.getBlockX(), tutor.getBlockZ() - 26)) return;
        if (level != server.overworld() && level != SkyIslandWorlds.island(server)) return;
        if (islandArena(server) != null) event.setCanceled(true);
    }

    static TrainingGroundData.Arena islandArena(MinecraftServer server) {
        var site = site(server);
        if (site == null) return null;
        return TrainingGroundData.get(server).arenas().stream()
            .filter(a -> a.dimension().equals(SkyIslandWorlds.island(server).dimension().location().toString()) && a.center().equals(site))
            .findFirst().orElse(null);
    }

    /** 一次性砌台：找平地形、铺台面、四角演武位、中央终端、南侧台阶。 */
    public static void build(ServerLevel level, int cx, int cz) {
        build(level, cx, cz, null);
    }

    static void build(ServerLevel level, int cx, int cz, Integer fixedDeck) {
        int half = TrainingGroundRules.HALF;
        if (level == SkyIslandWorlds.island(level.getServer())) {
            var origin = SkyIslandSavedData.get(level).anchorPos("ORIGIN");
            if (origin != null) {
                var min = origin.offset(com.tnc.tnc.casino.machine.SkyCasinoUpgrade.MIN);
                var max = origin.offset(com.tnc.tnc.casino.machine.SkyCasinoUpgrade.MAX);
                if (cx + half >= min.getX() && cx - half <= max.getX()
                        && cz + half + 5 >= min.getZ() && cz - half <= max.getZ()) return;
            }
        }
        int size = half * 2 + 1;
        // 先把台子占的区块加载出来：没加载的区块 heightmap 全是空值，
        // 直接读会把台面砌到世界底部去。
        for (int chunkX = (cx - half) >> 4; chunkX <= (cx + half) >> 4; chunkX++) {
            for (int chunkZ = (cz - half) >> 4; chunkZ <= (cz + half + 6) >> 4; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
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
        int deck = fixedDeck == null ? maxTop + 1 : fixedDeck;
        int stairsStart = cz + half + 1;
        int approachHeight = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, stairsStart + 5) - 1;
        int rise = net.minecraft.util.Mth.clamp(approachHeight - deck, -5, 5);
        int[] stairHeights = new int[5];
        for (int i = 0; i < stairHeights.length; i++) {
            stairHeights[i] = fixedDeck == null ? deck - i
                    : deck + Integer.signum(rise) * Math.min(i + 1, Math.abs(rise));
        }
        if (fixedDeck != null && hasBlockEntitiesInBuildArea(level, cx, deck, cz, half, stairHeights)) return;
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
        // 南侧台阶按岛面高度衔接，林地高于台面时向外逐级抬升。
        for (int i = 0; i < stairHeights.length; i++) {
            int z = stairsStart + i;
            int y = stairHeights[i];
            for (int dx = -2; dx <= 2; dx++) {
                level.setBlock(new BlockPos(cx + dx, y, z), Blocks.STONE_BRICKS.defaultBlockState(), 3);
                level.setBlock(new BlockPos(cx + dx, y + 1, z), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(new BlockPos(cx + dx, y + 2, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        TrainingGroundData.get(level.getServer()).markArena(level.dimension().location().toString(), cx, deck, cz);
        level.getServer().getPlayerList().broadcastSystemMessage(
            Component.literal("天空岛演武场已落成，中央终端可以召出教官。"), false);
    }

    private static boolean hasBlockEntitiesInBuildArea(ServerLevel level, int x, int deck, int z, int half, int[] stairHeights) {
        for (var pos : BlockPos.betweenClosed(x - half, deck, z - half, x + half, deck + 12, z + half)) {
            if (level.getBlockState(pos).hasBlockEntity()) return true;
        }
        for (int i = 0; i < stairHeights.length; i++) {
            int stairZ = z + half + 1 + i;
            int y = stairHeights[i];
            for (var pos : BlockPos.betweenClosed(x - 2, y, stairZ, x + 2, y + 2, stairZ)) {
                if (level.getBlockState(pos).hasBlockEntity()) return true;
            }
        }
        return false;
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
        if (!ensure(p.server) && !inside(p)) {
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
            p.sendSystemMessage(Component.literal("先到天空岛演武场，再用中央终端召唤教官。"));
            return false;
        }
        String normalized = normalizeRole(role);
        TrainingGround.dismissAll(p);
        var arena = arenaOf(p);
        if (arena == null) {
            p.sendSystemMessage(Component.literal("先到天空岛演武场，再用中央终端召唤教官。"));
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
        var arena = islandArena(p.server);
        var c = arena == null ? null : arena.center();
        if (c == null) {
            p.sendSystemMessage(Component.literal("天空岛还没生成完，演武场暂时无法使用。"));
            return false;
        }
        double x = c.getX() + 0.5;
        double z = c.getZ() + TrainingGroundRules.HALF - 2.5;
        p.teleportTo(SkyIslandWorlds.island(p.server), x, c.getY() + 1, z, 180f, 0f);
        settle(p);
        TemperingEvents.sync(p, true);
        p.sendSystemMessage(Component.literal("已到天空岛演武场：在面板选择教官，再点开始练习即可召出。"));
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
        ensure(server);
    }
}
