package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.warrior.WarriorDragonGeometry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * 龙之剑的龙：**真实几何体**，不是粒子。
 *
 * <p>2026-11-03 之前这条龙是服务端每 tick 撒几十颗尘埃粒子拼出来的（龙骨 END_ROD、
 * 龙身暗红尘埃、两侧斜线当龙翼、龙头炸一发 FLASH）。问题是：粒子没有体积、没有朝向，
 * 远看是一团糊、近看是一串点，而且粒子上限一吃就只剩一半。现在改成客户端画一条
 * 带厚度的刃形长龙：沿玩家朝向冲出 7.5 格，蛇形摆动、两侧张翼、头部收成菱形，
 * 三层嵌套（暗红外壳 / 赤红龙身 / 金色脊线）叠加发光。
 *
 * <p>形状本身在 {@link WarriorDragonGeometry}（纯数学，可单测、可离线出预览图）；
 * 这里只负责"把三角形塞进 buffer"，所以画错了只有两种可能：数据错（单测能抓到）
 * 或者顶点顺序/混合错。
 *
 * <p>触发方式<b>不新增网络包</b>：服务端只广播动作（{@code WarriorAnimationPacket}），
 * 客户端在 {@link WarriorAnimationClient#accept} 里看到"龙之剑 + 终结技动作"就自己起一条龙。
 * 这样别人的屏幕上也能看到，而且协议号不用动（协议号 20 由美术侧负责，别去碰）。
 *
 * <p>渲染方式与 {@code WarriorWaveRenderer} 一致：{@code RenderType.lightning()} 加算、
 * 不写深度，所以龙不会把地形或别的实体遮黑，多条叠在一起也只是更亮。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT)
public final class WarriorDragonRenderer {

    /** 同时最多画几条，防止刷屏时叠成一团白。 */
    private static final int MAX_DRAGONS = 6;

    private record Dragon(Vec3 origin, Vec3 dir, Vec3 side, long startedAt) {
    }

    private static final List<Dragon> DRAGONS = new ArrayList<>();
    /** 待出生队列：网络包可能在工作线程上到，统一回到客户端 tick 再解析实体。 */
    private static final List<Integer> PENDING = new ArrayList<>();
    private static ClientLevel world;
    private static final MultiBufferSource.BufferSource BUFFERS =
            MultiBufferSource.immediate(new BufferBuilder(65536));

    private WarriorDragonRenderer() {
    }

    /**
     * 请求给某个玩家起一条龙（线程安全：只入队，真正的实体解析放到客户端 tick）。
     *
     * <p>{@link WarriorAnimationClient#accept} 是在网络包回调里调的，那里不保证在主线程；
     * 所以这里只记实体 id，回到 tick 再查 {@code ClientLevel#getEntity}。
     */
    static void request(int entityId) {
        if (!PENDING.contains(entityId)) {
            PENDING.add(entityId);
        }
    }

    /** 起一条龙：只由 {@link #tick} 在主线程调用。 */
    private static void spawn(Player player) {
        var level = Minecraft.getInstance().level;
        if (level == null || player == null) {
            return;
        }
        // 只有龙之剑才唤龙：别的武器放终结技不该看到一条龙冲出去
        if (!player.getMainHandItem().is(TNMod.DRAGON_SWORD.get())) {
            return;
        }
        if (world != level) {
            DRAGONS.clear();
            world = level;
        }
        Vec3 look = player.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z);
        if (dir.lengthSqr() < 1.0E-4) {
            return;
        }
        dir = dir.normalize();
        var side = new Vec3(-dir.z, 0, dir.x);
        var origin = player.position().add(0, player.getEyeHeight() * 0.55, 0).add(dir.scale(0.8));
        if (DRAGONS.size() >= MAX_DRAGONS) {
            DRAGONS.remove(0);
        }
        DRAGONS.add(new Dragon(origin, dir, side, level.getGameTime()));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        var level = Minecraft.getInstance().level;
        if (world != level) {
            DRAGONS.clear();
            world = level;
        }
        if (level == null) {
            PENDING.clear();
            return;
        }
        DRAGONS.removeIf(dragon -> level.getGameTime() - dragon.startedAt() > WarriorDragonGeometry.LIFE + 2);
        if (!PENDING.isEmpty()) {
            // 每 tick 最多处理 8 个，避免一次进太多包卡一帧
            int budget = Math.min(8, PENDING.size());
            for (int i = 0; i < budget; i++) {
                int entityId = PENDING.remove(0);
                if (level.getEntity(entityId) instanceof Player player) {
                    spawn(player);
                }
            }
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || DRAGONS.isEmpty()) {
            return;
        }
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != world) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        var buffers = BUFFERS;
        var vertices = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = new Matrix4f().translation((float) -camera.x, (float) -camera.y, (float) -camera.z);
        float partial = event.getPartialTick();
        boolean drew = false;
        for (var dragon : DRAGONS) {
            double age = mc.level.getGameTime() - dragon.startedAt() + partial;
            if (age < 0 || age > WarriorDragonGeometry.LIFE) {
                continue;
            }
            drew = true;
            draw(vertices, matrix, dragon, WarriorDragonGeometry.head(age), WarriorDragonGeometry.fade(age));
        }
        if (drew) {
            buffers.endBatch(RenderType.lightning());
        }
    }

    private static void draw(VertexConsumer out, Matrix4f matrix, Dragon dragon, double head, float fade) {
        for (var tri : WarriorDragonGeometry.build(dragon.origin(), dragon.dir(), dragon.side(), head, fade)) {
            vertex(out, matrix, tri.a(), tri.color(), tri.alpha());
            vertex(out, matrix, tri.b(), tri.color(), tri.alpha());
            vertex(out, matrix, tri.c(), tri.color(), tri.alpha());
        }
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 point, int color, float alpha) {
        out.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .color(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f,
                        (color & 255) / 255f, Mth.clamp(alpha, 0, 1))
                .endVertex();
    }
}
