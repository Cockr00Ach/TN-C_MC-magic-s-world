package com.tnc.tnc.boss.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.boss.YanDarkBossEntity;
import com.tnc.tnc.boss.YanExecutionRules;
import com.tnc.tnc.client.lightning.LightningBoltGeometry;
import com.tnc.tnc.client.lightning.LightningRenderTypes;
import com.tnc.tnc.client.lightning.LightningTheme;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.Comparator;

/**
 * ★ <b>衍的处决演出（客户端）</b>—— 运镜 ＋ 落雷 ＋ 雷网（2026-11-14 作者分镜）✓
 *
 * <p>服务端只管"什么时候开始 / 冻结谁 / 打多少"（{@link YanDarkBossEntity}），
 * 这里负责<b>看得见的那一半</b>：跟拍、抬镜头、跟着雷末端往下坠 3 秒、命中炸开 ✓。
 *
 * <h2>镜头（按 {@link YanExecutionRules#stage(int)} 切）</h2>
 * <ol>
 *   <li>衍特写：贴在衍的脸前面往回看 ✓</li>
 *   <li>玩家特写：站到玩家侧前方，看着被定住的玩家 ✓</li>
 *   <li>抬头看天：镜头在玩家头顶往上仰（雷在云里聚）✓</li>
 *   <li>跟着雷末端：镜头挂在雷的<b>末端</b>上一起往下坠（60 tick ＝ 3 秒）✓</li>
 *   <li>命中：拉回玩家，拍那一下爆开 ✓</li>
 * </ol>
 *
 * <h2>为什么用隐身 ArmorStand 当镜头</h2>
 * 和 {@code WarriorUltimateCamera} / {@code CanglanCinematicCamera} 同一套：
 * {@code mc.setCameraEntity(stand)} ＋ 每 tick 挪它 ＋ {@link ViewportEvent.ComputeCameraAngles} 修正朝向。
 * 这套在本仓已经验证过能跑 ✓（自己写 mixin 去改 Camera 反而要维护映射 ✗）。
 *
 * <p>⚠️ 任何"玩家可能想脱身"的情况都立刻把镜头还回去：按潜行、开界面、死亡、Boss 没了 ✓。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT)
public final class YanExecutionCamera {

    private static ArmorStand camera;
    private static CameraType previous;
    private static ClientLevel world;
    private static int bossId = -1;
    private static int victimId = -1;
    private static int tick;
    private static Vec3 cameraFocus;

    private static final MultiBufferSource.BufferSource BUFFERS =
            MultiBufferSource.immediate(new BufferBuilder(65536));

    private YanExecutionCamera() {
    }

    // ------------------------------------------------------------------
    //  找 Boss / 起停
    // ------------------------------------------------------------------

    private static YanDarkBossEntity findBoss(ClientLevel level) {
        return level.getEntitiesOfClass(YanDarkBossEntity.class, Minecraft.getInstance().player.getBoundingBox().inflate(96.0D))
                .stream()
                .filter(boss -> boss.execTick() >= 0 && boss.execTargetId() == Minecraft.getInstance().player.getId())
                .min(Comparator.comparingDouble(boss -> boss.distanceToSqr(Minecraft.getInstance().player)))
                .orElse(null);
    }

    private static void start(YanDarkBossEntity boss) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null || mc.getCameraEntity() != mc.player) {
            return;
        }
        stopCamera();
        previous = mc.options.getCameraType();
        world = mc.level;
        bossId = boss.getId();
        victimId = boss.execTargetId();
        tick = 0;
        camera = new ArmorStand(mc.level, mc.player.getX(), mc.player.getY(), mc.player.getZ());
        camera.setInvisible(true);
        camera.setNoGravity(true);
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.setCameraEntity(camera);
    }

    private static void stopCamera() {
        Minecraft mc = Minecraft.getInstance();
        if (camera != null && mc.getCameraEntity() == camera) {
            mc.setCameraEntity(mc.player);
            if (previous != null) {
                mc.options.setCameraType(previous);
            }
        }
        camera = null;
        previous = null;
        world = null;
        bossId = -1;
        victimId = -1;
        tick = 0;
        cameraFocus = null;
    }

    public static void releaseForPrologue() { stopCamera(); }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (camera != null) {
                stopCamera();
            }
            return;
        }
        YanDarkBossEntity boss = camera == null ? findBoss(mc.level) : null;
        if (camera == null) {
            // 只有**被处决的那个人**才进特写；旁观者只看世界里的雷 ✓（多人时不会抢别人的镜头）
            if (boss != null && boss.execTargetId() == mc.player.getId()) {
                start(boss);
            }
            return;
        }
        // 演出中：任何异常都立刻退出
        if (mc.level != world || !mc.player.isAlive() || mc.player.isSpectator() || mc.screen != null
                || !mc.isWindowActive() || mc.options.keyShift.isDown()
                || mc.getCameraEntity() != camera
                || mc.options.getCameraType() != CameraType.FIRST_PERSON) {
            stopCamera();
            return;
        }
        var active = world.getEntity(bossId);
        if (!(active instanceof YanDarkBossEntity yan) || yan.execTick() < 0) {
            stopCamera();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        tick = yan.execTick();
        updateCamera(yan);
    }

    // ------------------------------------------------------------------
    //  运镜
    // ------------------------------------------------------------------

    private static LivingEntity victim() {
        return world != null && victimId >= 0 && world.getEntity(victimId) instanceof LivingEntity living
                ? living : null;
    }

    private static void updateCamera(YanDarkBossEntity boss) {
        Minecraft mc = Minecraft.getInstance();
        if (camera == null || mc.player == null) {
            return;
        }
        LivingEntity victim = victim();
        if (victim == null) {
            return;
        }
        Vec3 eye;
        Vec3 focus;
        int stage = YanExecutionRules.stage(tick);
        Vec3 side = Vec3.directionFromRotation(0.0F, victim.getYRot() + 90.0F);
        switch (stage) {
            case YanExecutionRules.STAGE_BOSS -> {
                // 衍特写：贴脸（正面略偏，别正好挡住眼睛）
                Vec3 bossCentre = boss.position().add(0.0D, boss.getBbHeight() * 0.72D, 0.0D);
                Vec3 face = Vec3.directionFromRotation(0.0F, boss.getYRot());
                eye = bossCentre.add(face.scale(4.2D)).add(side.scale(1.1D)).add(0.0D, 0.35D, 0.0D);
                focus = bossCentre;
            }
            case YanExecutionRules.STAGE_SKY -> {
                // 抬头看天
                Vec3 base = victim.position().add(0.0D, 1.4D, 0.0D);
                eye = base.add(side.scale(0.9D));
                focus = base.add(0.0D, 26.0D, 0.0D);
            }
            case YanExecutionRules.STAGE_FALL -> {
                // 跟着雷的末端一起往下坠 ✓
                double groundY = victim.getY();
                double skyY = groundY + YanExecutionRules.SKY_HEIGHT;
                double tipY = YanExecutionRules.boltTipY(skyY, groundY, tick);
                Vec3 tip = new Vec3(victim.getX(), tipY, victim.getZ());
                eye = tip.add(side.scale(3.4D)).add(0.0D, 1.1D, 0.0D);
                focus = tip;
            }
            case YanExecutionRules.STAGE_IMPACT -> {
                // 命中：拉远一点拍那一下
                Vec3 chest = victim.getPosition(1.0F).add(0.0D, victim.getBbHeight() * 0.55D, 0.0D);
                eye = chest.add(side.scale(4.6D)).add(0.0D, 1.4D, 0.0D);
                focus = chest;
            }
            default -> {
                // 玩家特写
                Vec3 chest = victim.getPosition(1.0F).add(0.0D, victim.getBbHeight() * 0.62D, 0.0D);
                eye = chest.add(side.scale(3.2D)).add(0.0D, 0.55D, 0.0D);
                focus = chest;
            }
        }
        camera.xo = camera.getX();
        camera.yo = camera.getY();
        camera.zo = camera.getZ();
        camera.yRotO = camera.getYRot();
        camera.xRotO = camera.getXRot();
        camera.setPos(eye.x, eye.y - camera.getEyeHeight(), eye.z);
        Vec3 d = focus.subtract(eye);
        camera.setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
        camera.setXRot((float) -Math.toDegrees(Math.atan2(d.y, d.horizontalDistance())));
        cameraFocus = focus;
        if (tick <= YanExecutionRules.AT_PLAYER) {
            camera.xo = camera.getX();
            camera.yo = camera.getY();
            camera.zo = camera.getZ();
            camera.yRotO = camera.getYRot();
            camera.xRotO = camera.getXRot();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void aim(ViewportEvent.ComputeCameraAngles event) {
        if (camera == null || camera != Minecraft.getInstance().getCameraEntity()) {
            return;
        }
        if (cameraFocus == null) return;
        Vec3 d = cameraFocus.subtract(event.getCamera().getPosition());
        event.setYaw((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
        event.setPitch((float) -Math.toDegrees(Math.atan2(d.y, d.horizontalDistance())));
        event.setRoll(0.0F);
    }

    @SubscribeEvent
    public static void hand(RenderHandEvent event) {
        if (camera != null) {
            event.setCanceled(true);       // 特写时不要第一人称手臂 ✓
        }
    }

    @SubscribeEvent
    public static void movement(net.minecraftforge.client.event.MovementInputUpdateEvent event) {
        if (camera == null || tick < YanExecutionRules.AT_PLAYER) return;
        var input = event.getInput();
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.jumping = false;
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov event) {
        if (camera != null) {
            event.setFOV(52.0F);           // 略长焦，脸更近 ✓
        }
    }

    // ------------------------------------------------------------------
    //  画面：雷网 / 落雷 / 爆开
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        if (world == null || tick < 0) {
            return;
        }
        LivingEntity victim = victim();
        if (victim == null) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        float partial = event.getPartialTick();
        Vec3 feet = victim.getPosition(partial);
        double camX = cam.x - feet.x;
        double camY = cam.y - feet.y;
        double camZ = cam.z - feet.z;
        Matrix4f m = new Matrix4f().translation(
                (float) (feet.x - cam.x), (float) (feet.y - cam.y), (float) (feet.z - cam.z));
        VertexConsumer vc = BUFFERS.getBuffer(LightningRenderTypes.BOLT);
        int theme = LightningTheme.VIOLET;
        boolean any = false;
        int stage = YanExecutionRules.stage(tick);

        // ① 玩家脚下：雷网（禁锢）✓
        if (stage >= YanExecutionRules.STAGE_PLAYER && stage <= YanExecutionRules.STAGE_IMPACT) {
            float fade = stage == YanExecutionRules.STAGE_IMPACT ? 0.9F : 0.75F;
            LightningBoltGeometry.groundWeb(vc, m, 0.0D, 0.03D, 0.0D, 2.6F,
                    victimId * 31L + 7L, theme, fade, tick * 0.08F, camX, camY, camZ, true);
            LightningBoltGeometry.groundFlash(vc, m, 0.0D, 0.03D, 0.0D, 1.9F, theme, fade * 0.8F);
            any = true;
        }

        // ② 天空聚雷（暗示）＋ 慢慢下坠的雷 ✓
        if (stage == YanExecutionRules.STAGE_SKY || stage == YanExecutionRules.STAGE_FALL) {
            double groundY = 0.0D;
            double skyY = YanExecutionRules.SKY_HEIGHT;
            double tipY = YanExecutionRules.boltTipY(skyY, groundY, tick);
            double x = 0.0D;
            double z = 0.0D;
            // 云里的预兆
            if (stage == YanExecutionRules.STAGE_SKY) {
                float gather = 0.35F + 0.45F * (tick - YanExecutionRules.AT_SKY) / (float) YanExecutionRules.SKY_LOOK;
                LightningBoltGeometry.billboardFlash(vc, m, x, skyY, z, 7.0F, theme, gather, camX, camY, camZ);
            }
            // 从云到"末端"这一整条雷（末端在下降 ⇒ 镜头跟着它）✓
            LightningBoltGeometry.bolt(vc, m, x, tipY, z, x, skyY - tipY, z,
                    victimId * 17L + tick / 2, 0.22F, 0.95F, theme, 3, camX, camY, camZ);
            LightningBoltGeometry.point(vc, m, x, tipY, z, 1.1F, 0.95F, theme, camX, camY, camZ);
            any = true;
        }

        // ③ 命中：无数雷电从身上爆开 ✓
        if (stage == YanExecutionRules.STAGE_IMPACT) {
            float k = 1.0F - Math.min(1.0F, (tick - YanExecutionRules.AT_IMPACT)
                    / (float) YanExecutionRules.IMPACT);
            for (int i = 0; i < 14; i++) {
                double a0 = i * 0.4488D + tick * 0.07D;
                double a1 = a0 + 0.55D;
                double r0 = 0.4D;
                double r1 = 2.2D + 1.6D * k;
                LightningBoltGeometry.arc(vc, m,
                        Math.cos(a0) * r0, 0.7D + Math.sin(a0 * 2.0D) * 0.5D, Math.sin(a0) * r0,
                        Math.cos(a1) * r1, 0.6D + Math.sin(a1 * 2.0D) * 1.1D, Math.sin(a1) * r1,
                        victimId * 53L + i * 97L, 0.12F, 0.95F * k, theme, camX, camY, camZ);
            }
            LightningBoltGeometry.billboardFlash(vc, m, 0.0D, 1.0D, 0.0D, 5.0F * (0.6F + k),
                    theme, 0.85F * k, camX, camY, camZ);
            any = true;
        }
        if (any) {
            BUFFERS.endBatch(LightningRenderTypes.BOLT);
        }
    }
}
