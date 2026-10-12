package com.tnc.tnc.magic.water;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Prevalidates the complete Dragon Ruin corridor, then launches its front while bounded edits follow.
 * Each axial slice retains the original conservative voxel guard and every protection event,
 * including protected air. Slab enumeration avoids visiting the rest of the bounding cube.
 * The front advances 32 blocks per tick only inside validated slices. Terrain edits remain
 * capped at 128--192 blocks per tick; dense solid corridors may outlive this edit budget.
 */
public final class WaterTerrainBore {
    /** 每 tick 直接破坏方块的<b>基线</b>预算 ✓ —— 多条测试钉着这个数 ✗。 */
    public static final int BLOCK_BUDGET = 128;
    /**
     * 追赶用的<b>上限</b>预算 ✓：开火后按"剩余方块 ÷ 剩余 tick"自适应，最高到这里 ✗。
     *
     * <p>为什么需要它：半径 15 × 96 格的实心通道 ≈ 6.8 万方块 ✗，
     * 想要在 240 tick 内真的挖完就得 ≈ 283 方块/tick ✗。
     * <b>2026-10-10 第十批从 320 降到 192</b> ✗ —— 作者反馈"水法全部都不流畅、t 级越高越卡"✗，
     * 而 320/tick 意味着每 tick 320 次 `setBlock` ＋ 320 次 `Block.getDrops` ＋ 320 个掉落物实体 ✗
     * （掉落物洪水本身就是一笔大账 ✗）⇒ 取 192 做折中 ✓：
     * 常见的"半实心地形"（约 2 万方块 ✗）仍能在 100 tick 内追完 ✓，
     * 而每 tick 的服务端尖峰降了 40% ✓。<b>前端速度一行没变</b> ✓（FRONT_STEP_BLOCKS 管那个 ✗）。
     */
    public static final int MAX_BLOCK_BUDGET = 192;
    /** 水柱前端的保证速度（格/tick）：已验证的 96 格通道在 3 tick（0.15 秒）内射满。 */
    public static final int FRONT_STEP_BLOCKS = 32;
    /** 蓄力期每 tick 预扫描的切片数 ✓ —— 96 ÷ 4 = 24 tick，正好赶在 30 tick 蓄力结束前扫完 ✓。 */
    public static final int SCAN_SLICES_PER_TICK = 4;
    /** 预扫描被"未加载区块"挡住时最多等多少 tick 再放弃（不加载就不等了 ✗）。 */
    public static final int MAX_SCAN_STALL_TICKS = 20;
    private static final long[] NO_BLOCKS = new long[0];
    /** 体积防护壳半径 ✓ —— 校验"被这一层碰到的每个体素"，而不是只校验圆心 ✓。 */
    private static final double GUARD = Math.sqrt(3) / 2;
    private static final int ALLOWED = 0, DENIED = 1;

    private final Vec3 start, direction;
    private final double radius, range;
    private final int sliceCount;
    /** 计划：{@code plan.get(k)} = 第 k 格（轴向 [k,k+1)）要挖掉的方块（打包成 long ✗），已被消费的格子置 null ✓。 */
    private final List<long[]> plan = new ArrayList<>();
    private double front;            // 可见长度 = 伤害长度 ✓
    private int scannedSlices;       // 已经验证过几格 ✓
    private double stopLength;       // 第一个被拒绝的切片 ⇒ 前端硬上限 ✓
    private int nextSlice, cursor;   // 追挖进度 ✓
    private int remainingBlocks;     // 计划里还没处理的方块数 ✓
    private int remainingTicks;      // 技能还剩多少 tick（由实体每 tick 告知 ✓）
    private int stalls;              // 连续被"未加载区块"挡住的 tick 数 ✓
    private boolean stopped;
    private String stopReason = "";

    public WaterTerrainBore(Vec3 start, Vec3 direction, double radius, double range) {
        this.start = start;
        this.direction = direction.normalize();
        this.radius = Math.max(.1, Math.min(WaterSpellRules.radius(5), radius));
        this.range = Math.max(0, Math.min(96, range));
        this.sliceCount = (int) Math.ceil(this.range);
        this.stopLength = this.range;
    }

    public double length() { return front; }
    public boolean stopped() { return stopped; }
    public String stopReason() { return stopReason; }
    /** 已经验证过的通道长度（格）✓ —— 前端不可能超过它 ✗。 */
    public double scannedLength() { return stopped ? stopLength : scannedSlices; }
    /** 计划里还剩多少方块要挖 ✓（0 ⇒ 隧道挖完了 ✗）。 */
    public int remainingBlocks() { return remainingBlocks; }
    /** 预扫描是否已经结束（扫到底 / 撞上保护 / 等区块等超时）✓ —— 结束前不开火 ✗。 */
    public boolean prescanDone() { return stopped || scannedSlices >= sliceCount; }
    /** 由施法实体告知"技能还剩多少 tick"⇒ 自适应预算用 ✓（不设也能跑，只是恒定用基线预算 ✗）。 */
    public void setRemainingTicks(int ticks) { this.remainingTicks = Math.max(0, ticks); }

    public static boolean protectedState(BlockState state, ServerLevel level, BlockPos pos) {
        return state.is(net.minecraft.world.level.block.Blocks.BEDROCK);
    }
    static boolean fluidOnly(BlockState state) { return state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock; }

    /** 一个体素的裁决 ✓：允许 / 拒绝 ✗（"未加载区块"在 {@link #scanSlice} 里**按区块列**先查掉了 ✓）。 */
    private int verdict(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!level.getWorldBorder().isWithinBounds(pos) || level.isOutsideBuildHeight(pos)) {
            stopReason = "边界/未加载区块 " + pos.toShortString(); return DENIED;
        }
        BlockState state = level.getBlockState(pos);
        if (protectedState(state, level, pos)) { stopReason = "基岩 " + pos.toShortString(); return DENIED; }
        // mayInteract 已经把出生点保护与世界边界都算进去了 ⇒ 不再重复查一次 ✗（这是原来每体素最贵的两笔之一 ✗）。
        if (player.isSpectator() || player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE
                || !level.mayInteract(player, pos)) { stopReason = "交互/出生点保护 " + pos.toShortString(); return DENIED; }
        if (MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player))) {
            stopReason = "领地/事件保护 " + pos.toShortString(); return DENIED;
        }
        return ALLOWED;
    }

    /**
     * 预扫描（蓄力期每 tick 调用 ✓）：最多再验证 {@link #SCAN_SLICES_PER_TICK} 格 ✓。
     *
     * <p>没扫完就是没扫完 ✗ —— 前端会被 {@link #scannedLength()} 卡住 ✗，
     * 所以"没验证过的区域"永远不会被画出来、也不会被打到 ✓。
     */
    public void scan(ServerLevel level, ServerPlayer player) {
        if (prescanDone()) return;
        int before = scannedSlices;
        for (int i = 0; i < SCAN_SLICES_PER_TICK && !prescanDone(); i++) {
            if (!scanSlice(level, player, scannedSlices)) break;   // false ⇒ 这一格被未加载区块挡住 ✓
        }
        if (scannedSlices > before) stalls = 0;
        else if (++stalls > MAX_SCAN_STALL_TICKS) { stopped = true; stopLength = scannedSlices; }
    }

    /**
     * 验证一格（轴向 [k,k+1)）✓，通过后把"半径内、非空气"的方块记进计划 ✗。
     *
     * <p>顺序与老代码逐字一致 ✗：<b>整层先全部验证，全部通过之后才允许编辑</b> ✓ ——
     * 所以半个方块的受保护体积也不可能被跨过去 ✓。守卫盒故意保持**保守**（三轴都 ±radius+1 ✗），
     * 宁可多迭代几圈空气也不冒"漏掉一个体素"的风险 ✓。
     *
     * @return false ⇒ 这一格里有体素所在区块没加载：不判定、不推进、下一 tick 再试 ✓
     */
    private boolean scanSlice(ServerLevel level, ServerPlayer player, int k) {
        double from = k, to = k + 1;
        Vec3 a = start.add(direction.scale(from)), b = start.add(direction.scale(to));
        BlockPos min = BlockPos.containing(Math.min(a.x, b.x) - radius - 1, Math.min(a.y, b.y) - radius - 1, Math.min(a.z, b.z) - radius - 1);
        BlockPos max = BlockPos.containing(Math.max(a.x, b.x) + radius + 1, Math.max(a.y, b.y) + radius + 1, Math.max(a.z, b.z) + radius + 1);
        // 第十批：**区块先整片查一次** ✓ —— 原来每个体素都要 `hasChunkAt`（一次区块表查找 ✗），
        // 半径 15 一层约 1600 个体素 ⇒ 每片 1600 次查找 ✗、24 片/秒就是 3.8 万次 ✗。
        // 盒子只可能落在少数几个区块列里 ✓ ⇒ 一列一次就够 ✓（有一列没加载 ⇒ 整片挂起、下一 tick 再试 ✓）。
        for(int cx=min.getX()>>4;cx<=max.getX()>>4;cx++)
            for(int cz=min.getZ()>>4;cz<=max.getZ()>>4;cz++)
                if(!level.hasChunkAt(new BlockPos(cx<<4,min.getY(),cz<<4))) {
                    stopReason="边界/未加载区块 "+(cx<<4)+","+(cz<<4);
                    return false;
                }
        double axialExtent = .5 * (Math.abs(direction.x) + Math.abs(direction.y) + Math.abs(direction.z));
        long[] buffer = NO_BLOCKS;
        int count = 0;
        for (BlockPos mutable : BoreSliceGeometry.candidates(start,direction,min,max,from,to,axialExtent)) {
            double dx=mutable.getX()+.5-start.x, dy=mutable.getY()+.5-start.y, dz=mutable.getZ()+.5-start.z;
            double along=dx*direction.x+dy*direction.y+dz*direction.z;
            // 这一层碰到的每个体素体积都要查 ✓（不是只查圆心 ✗）：半格受保护空气不许进光束 ✓。
            if (along + axialExtent < from || along - axialExtent >= to) continue;
            double rx=dx-direction.x*along, ry=dy-direction.y*along, rz=dz-direction.z*along;
            double radialSquared=rx*rx+ry*ry+rz*rz;
            double guard = radius + GUARD;
            if (radialSquared > guard * guard) continue;
            BlockPos pos = mutable.immutable();
            int verdict = verdict(level, player, pos);
            if (verdict == DENIED) { stopped = true; stopLength = k; return true; }
            if (radialSquared > radius * radius) continue;   // 防护壳内的空气：验证过了，但没有东西要挖 ✓
            // 方块归属按**圆心**落在哪一层算 ✗（与老代码的 pending 条件逐字一致 ✓）——
            // 否则相邻两层会把同一个方块都记进去 ✗（计划翻倍、隧道提前一格 ✗）。
            if (along < from || along >= to) continue;
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || fluidOnly(state)) continue;
            if (count == buffer.length) buffer = Arrays.copyOf(buffer, Math.max(16, buffer.length * 2));
            buffer[count++] = pos.asLong();
        }
        plan.add(count == 0 ? NO_BLOCKS : Arrays.copyOf(buffer, count));
        remainingBlocks += count;
        scannedSlices = k + 1;
        return true;
    }

    /**
     * 开火后每 tick 一次 ✓：① 补预扫描 ② 前端按保证速度射出 ③ 隧道按预算追挖 ✗。
     *
     * @return 本 tick 真正挖掉的方块数 ✓（≤ {@link #budget()} ✗）
     */
    public int tick(ServerLevel level, ServerPlayer player) {
        scan(level, player);
        double limit = Math.min(range, scannedLength());
        // ① 前端：保证速度 ✓ —— 不管前面是空气、泥土还是实心岩 ✗。
        if (front < limit) front = Math.min(limit, front + FRONT_STEP_BLOCKS);
        // ② 隧道：逐格追 ✓，但绝不挖到"前端还没到"的地方 ✗（否则方块会在光束前面凭空消失 ✗）。
        int budget = budget(), changed = 0;
        while (changed < budget && nextSlice < plan.size() && nextSlice + 1 <= front) {
            long[] slice = plan.get(nextSlice);
            if (slice == null) { nextSlice++; cursor = 0; continue; }
            while (cursor < slice.length && changed < budget) {
                BlockPos pos = BlockPos.of(slice[cursor++]);
                remainingBlocks--;
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || fluidOnly(state)) continue;            // 别人先挖了 / 已经是水 ⇒ 跳过 ✓
                if (protectedState(state, level, pos)) continue;             // 中途冒出来的基岩：绝不挖 ✓（扫描时已判定 ✓）
                // 容器战利品必须在 onRemove 抹掉 NBT 之前抓 ✗（箱子/潜影盒都靠这一条 ✓）。
                var blockEntity = level.getBlockEntity(pos);
                var loot = blockEntity == null ? List.<net.minecraft.world.item.ItemStack>of()
                        : net.minecraft.world.level.block.Block.getDrops(state, level, pos, blockEntity, player, net.minecraft.world.item.ItemStack.EMPTY);
                if (!WaterBoreEditGuard.remove(level, pos)) {
                    stopReason = "方块移除失败/安全隔离不可用 " + pos.toShortString();
                    stopped = true; return changed;
                }
                for (var stack : loot) net.minecraft.world.level.block.Block.popResource(level, pos, stack);
                changed++;
            }
            if (cursor >= slice.length) { plan.set(nextSlice, null); nextSlice++; cursor = 0; }
        }
        return changed;
    }

    /** 本 tick 允许挖多少方块 ✓：默认基线 ✗；实体告知剩余 tick 后按"剩余方块 ÷ 剩余 tick"自适应（有上限 ✗）。 */
    private int budget() {
        if (remainingTicks <= 0 || remainingBlocks <= 0) return BLOCK_BUDGET;
        return Math.max(BLOCK_BUDGET, Math.min(MAX_BLOCK_BUDGET, (int) Math.ceil(remainingBlocks / (double) remainingTicks)));
    }
}
