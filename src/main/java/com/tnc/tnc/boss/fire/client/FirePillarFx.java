package com.tnc.tnc.boss.fire.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.tnc.tnc.boss.fire.TortoiseParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 火柱（任务 6）的纯客户端粒子表现，附带独立预览命令。
 *
 * <p>一个实例管理一整根柱子的发射：底部蓄亮 → 向上冲起 → 停留 → 减弱消散。
 * 粒子只在客户端生成，不建实体、不产生伤害、不点燃方块、不改地形。</p>
 *
 * <p>预算按“多根同时存在”设计：单根存活上限 {@link #PER_PILLAR_ALIVE}，
 * 全局上限 {@link #GLOBAL_ALIVE}，7 根同屏时自动降密度而不是失控。</p>
 */
@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT)
public final class FirePillarFx {

    /** 单根最大存活粒子数（含上升中的全部火舌）。 */
    private static final int PER_PILLAR_ALIVE = 320;
    /** 同屏所有火柱的粒子上限，任务 7 的 5–7 根按这个预算设计。 */
    private static final int GLOBAL_ALIVE = 1900;
    /** 柱子总时长（tick）：蓄亮 + 冲起 + 停留 + 消散。 */
    private static final int TOTAL_TICKS = 74;
    /** 根部蓄亮时长。 */
    private static final int IGNITE_TICKS = 8;
    /** 向上冲起结束时刻。 */
    private static final int SURGE_END = 24;
    /** 喷流开始减弱的时刻。 */
    private static final int HOLD_END = 50;
    /** 单根高度（格）。 */
    private static final double HEIGHT_MIN = 5.0D;
    private static final double HEIGHT_MAX = 7.0D;
    /** 主体宽度（格）。 */
    private static final double WIDTH_MIN = 1.5D;
    private static final double WIDTH_MAX = 2.0D;
    /** 稳定流动通道数量：少量通道 + 每颗随机相位，能看出翻卷但不散成乱流。 */
    private static final int CHANNELS = 7;

    private static final List<Pillar> PILLARS = new ArrayList<>();

    private FirePillarFx() {
    }

    private static final class Pillar {
        final Vec3 base;
        final long start;
        final double height;
        final double width;
        final long seed;
        /** 最近 32 tick 每 tick 的生成数，用于估算存活量。 */
        final int[] history = new int[32];
        int cursor;
        int spawnedTotal;

        Pillar(Vec3 base, long start, double height, double width, long seed) {
            this.base = base;
            this.start = start;
            this.height = height;
            this.width = width;
            this.seed = seed;
        }

        int aliveEstimate() {
            int sum = 0;
            for (int v : history) {
                sum += v;
            }
            return sum;
        }

        void record(int spawned) {
            history[cursor] = spawned;
            cursor = (cursor + 1) % history.length;
            spawnedTotal += spawned;
        }
    }

    // ---------------------------------------------------------------- tick

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            PILLARS.clear();
            return;
        }
        long now = level.getGameTime();

        int alive = 0;
        for (Pillar p : PILLARS) {
            alive += p.aliveEstimate();
        }

        Iterator<Pillar> it = PILLARS.iterator();
        while (it.hasNext()) {
            Pillar p = it.next();
            long age = now - p.start;
            if (age > TOTAL_TICKS + 34L) {
                it.remove();
                continue;
            }
            if (age < 0) {
                continue;
            }
            int want = spawnCountFor(age);
            if (want <= 0) {
                continue;
            }
            int roomPerPillar = PER_PILLAR_ALIVE - p.aliveEstimate();
            int roomGlobal = GLOBAL_ALIVE - alive;
            int count = Math.min(want, Math.min(roomPerPillar, roomGlobal));
            if (count <= 0) {
                continue;
            }
            for (int i = 0; i < count; i++) {
                spawnParticle(level, p, age);
            }
            p.record(count);
            alive += count;
        }
    }

    /** 按阶段给出每 tick 生成数：蓄亮少、冲起多、停留稳、尾段线性减小。 */
    private static int spawnCountFor(long age) {
        if (age < IGNITE_TICKS) {
            return 5;
        }
        if (age < SURGE_END) {
            return 13;
        }
        if (age < HOLD_END) {
            return 9;
        }
        if (age >= TOTAL_TICKS) {
            return 0;
        }
        double k = 1.0D - (age - HOLD_END) / (double) (TOTAL_TICKS - HOLD_END);
        return Math.max(0, (int) Math.round(7.0D * k));
    }

    private static void spawnParticle(ClientLevel level, Pillar p, long age) {
        var random = level.random;
        int channel = random.nextInt(CHANNELS);
        double channelAngle = (Math.PI * 2.0D / CHANNELS) * channel;

        // 生成半径：主体宽度的 0.2–1.0，中心更密
        double radius = p.width * 0.5D * (0.20D + 0.80D * Math.sqrt(random.nextDouble()));
        double angle = channelAngle + (random.nextDouble() - 0.5D) * 0.55D;
        double x = p.base.x + Math.cos(angle) * radius;
        double z = p.base.z + Math.sin(angle) * radius;
        double y = p.base.y + 0.10D + random.nextDouble() * 0.12D;

        // 中心快、边缘慢：柱顶自然收束，而不是靠缩小生成半径
        double radial = radius / Math.max(0.001D, p.width * 0.5D);
        double speed = (0.34D - 0.10D * radial) * (0.85D + 0.30D * random.nextDouble());
        if (age < IGNITE_TICKS) {
            speed *= 0.55D;      // 蓄亮阶段先压住，形成底部堆积的亮核
        } else if (age > HOLD_END) {
            speed *= 0.85D;
        }

        // 径向一点点外扩 + 切向旋转，形成翻卷；幅度很小，不会越升越散
        double outward = 0.006D + random.nextDouble() * 0.010D;
        double tangent = (random.nextDouble() - 0.5D) * 0.020D;
        double vx = Math.cos(angle) * outward - Math.sin(angle) * tangent;
        double vz = Math.sin(angle) * outward + Math.cos(angle) * tangent;

        // addAlwaysVisibleParticle：粒子设「最少」时也不把柱体削成零星火星
        level.addAlwaysVisibleParticle(TortoiseParticles.FIRE_PILLAR.get(), true,
                x, y, z, vx, speed, vz);
    }

    // ------------------------------------------------------------- 预览命令

    @SubscribeEvent
    public static void onRegisterCommands(RegisterClientCommandsEvent event) {
        if (!Boolean.getBoolean("tnc.tortoise.attack.debug")) {
            return;
        }
        event.getDispatcher().register(Commands.literal("tnc_pillar")
                .executes(ctx -> trigger(ctx, 1))
                .then(Commands.literal("clear").executes(ctx -> {
                    PILLARS.clear();
                    ctx.getSource().sendSuccess(() -> Component.literal("[TN-C] 火柱已清空"), false);
                    return 1;
                }))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 12))
                        .executes(ctx -> trigger(ctx, IntegerArgumentType.getInteger(ctx, "count")))));
    }

    private static int trigger(CommandContext<CommandSourceStack> ctx, int count) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return 0;
        }
        Vec3 base = groundAhead(mc);
        long now = level.getGameTime();
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0D / Math.max(1, count)) * i;
            double spread = count == 1 ? 0.0D : 3.2D;
            Vec3 at = base.add(Math.cos(angle) * spread, 0.0D, Math.sin(angle) * spread);
            double h = HEIGHT_MIN + level.random.nextDouble() * (HEIGHT_MAX - HEIGHT_MIN);
            double w = WIDTH_MIN + level.random.nextDouble() * (WIDTH_MAX - WIDTH_MIN);
            PILLARS.add(new Pillar(at, now, h, w, level.random.nextLong()));
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[TN-C] 触发 " + count + " 根火柱，位置 " + fmt(base)), false);
        return count;
    }

    private static String fmt(Vec3 v) {
        return String.format("(%.1f, %.1f, %.1f)", v.x, v.y, v.z);
    }

    /** 视线前方的地面落点；没打中方块时退回脚前 8 格。 */
    private static Vec3 groundAhead(Minecraft mc) {
        HitResult hit = mc.player.pick(24.0D, 0.0F, false);
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = block.getBlockPos();
            return new Vec3(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D);
        }
        Vec3 look = mc.player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        if (flat.lengthSqr() < 1.0E-4D) {
            flat = new Vec3(0.0D, 0.0D, 1.0D);
        }
        flat = flat.normalize().scale(8.0D);
        return new Vec3(mc.player.getX() + flat.x, mc.player.getY() + 0.1D, mc.player.getZ() + flat.z);
    }

    /** 供任务 7 调用：在指定位置直接起一根柱子。 */
    public static void spawnAt(Vec3 base, double height, double width) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        PILLARS.add(new Pillar(base, level.getGameTime(), height, width, level.random.nextLong()));
    }

    /** 当前同屏估算存活量，调试用。 */
    public static int aliveEstimate() {
        int sum = 0;
        for (Pillar p : PILLARS) {
            sum += p.aliveEstimate();
        }
        return sum;
    }
}
