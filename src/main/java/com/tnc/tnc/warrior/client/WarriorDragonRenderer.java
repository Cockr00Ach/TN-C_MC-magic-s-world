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

/** Short-lived carved block dragon, with bounded instances and a private geometry buffer.
 * Production meshes are shared with the offline preview. Quad submission and ordinary
 * alpha blending keep the scales, antlers and jaws legible instead of washing them white.
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
        var vertices = buffers.getBuffer(WarriorGeometryRenderType.TYPE);
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
            buffers.endBatch(WarriorGeometryRenderType.TYPE);
        }
    }

    private static void draw(VertexConsumer out, Matrix4f matrix, Dragon dragon, double head, float fade) {
        for (var tri : WarriorDragonGeometry.build(dragon.origin(), dragon.dir(), dragon.side(), head, fade)) {
            vertex(out, matrix, tri.a(), tri.color(), tri.alpha());
            vertex(out, matrix, tri.b(), tri.color(), tri.alpha());
            vertex(out, matrix, tri.c(), tri.color(), tri.alpha());
            // The renderer consumes QUADS: duplicate the third corner to submit
            // a degenerate fourth corner rather than joining unrelated triangles.
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
