package com.tnc.tnc.world.yan;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tnc.tnc.boss.client.YanExecutionCamera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT)
public final class YanPrologueDefeatClient {
    private static final int CAMERA_HANDOFF = 16;
    private static final int[] ELEMENT_COLORS = {
            0xb298ff, 0xff7540, 0x70f5bb, 0x61bfff, 0xe9b465, 0xfff1b1, 0xa057de
    };
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new BufferBuilder(16384));
    private static ClientLevel world;
    private static RemotePlayer body;
    private static ArmorStand camera;
    private static CameraType previous;
    private static Vec3 feet, chest;
    private static float yaw;
    private static int age;

    private YanPrologueDefeatClient() {}

    static void begin(YanPrologueNetwork.Defeat defeat) {
        end();
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.level.dimension().location().equals(YanPrologueScript.DIMENSION)) return;
        world = mc.level;
        feet = defeat.position();
        yaw = defeat.yaw();
        chest = feet.add(Vec3.directionFromRotation(0, yaw - 90).scale(.63)).add(0, .28, 0);
        body = new RemotePlayer(world, mc.player.getGameProfile());
        body.moveTo(feet.x, feet.y, feet.z, yaw, 0);
        body.yBodyRot = body.yBodyRotO = yaw;
        body.yHeadRot = body.yHeadRotO = yaw;
        for (var slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.ARMOR) body.setItemSlot(slot, mc.player.getItemBySlot(slot).copy());
        }
        if (mc.screen != null) mc.setScreen(null);
    }

    static void end() {
        if (body == null) return;
        var mc = Minecraft.getInstance();
        if (camera != null && mc.getCameraEntity() == camera) {
            mc.setCameraEntity(mc.player);
            mc.options.setCameraType(previous);
        }
        body = null;
        world = null;
        camera = null;
        previous = null;
        age = 0;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || body == null) return;
        var mc = Minecraft.getInstance();
        if (mc.level != world || mc.player == null) {
            end();
            return;
        }
        if (mc.isPaused()) return;
        age++;
        body.deathTime = Math.min(20, age + 3);
        if (camera == null && age >= CAMERA_HANDOFF) {
            YanExecutionCamera.releaseForPrologue();
            previous = mc.options.getCameraType();
            camera = new ArmorStand(world, feet.x, feet.y, feet.z);
            camera.setInvisible(true);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.setCameraEntity(camera);
        }
        if (camera != null) {
            float rise = smooth((age - CAMERA_HANDOFF) / 80F);
            var side = Vec3.directionFromRotation(0, yaw + 90 + rise * 26);
            var eye = chest.add(side.scale(4.4 + rise * 2.6)).add(0, 2.1 + rise * 4.5, 0);
            var toward = chest.add(0, .25 + rise, 0).subtract(eye);
            camera.xo = camera.getX();
            camera.yo = camera.getY();
            camera.zo = camera.getZ();
            camera.xRotO = camera.getXRot();
            camera.yRotO = camera.getYRot();
            camera.setPos(eye.x, eye.y - camera.getEyeHeight(), eye.z);
            camera.setYRot((float) Math.toDegrees(Math.atan2(-toward.x, toward.z)));
            camera.setXRot((float) -Math.toDegrees(Math.atan2(toward.y, toward.horizontalDistance())));
            if (age == CAMERA_HANDOFF) {
                camera.setOldPosAndRot();
            }
        }
    }

    @SubscribeEvent
    public static void movement(MovementInputUpdateEvent event) {
        if (body == null) return;
        var input = event.getInput();
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void interact(InputEvent.InteractionKeyMappingTriggered event) {
        if (body != null) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void hidePlayer(RenderPlayerEvent.Pre event) {
        if (body != null && event.getEntity() == Minecraft.getInstance().player) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void hideBodyName(RenderNameTagEvent event) {
        if (body != null && event.getEntity() == body) event.setResult(Event.Result.DENY);
    }

    @SubscribeEvent
    public static void hand(RenderHandEvent event) {
        if (body != null) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (body == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        var view = event.getCamera().getPosition();
        var offset = feet.subtract(view);
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.render(body, offset.x, offset.y, offset.z, yaw, event.getPartialTick(),
                event.getPoseStack(), BUFFERS, dispatcher.getPackedLightCoords(body, event.getPartialTick()));
        BUFFERS.endBatch();

        float time = age + event.getPartialTick();
        if (time < YanPrologueScript.CORPSE_HOLD || time > YanPrologueScript.CLOCK_START + 7) return;
        var matrix = new Matrix4f(event.getPoseStack().last().pose());
        var consumer = BUFFERS.getBuffer(RenderType.lightning());
        var center = chest.subtract(view);
        for (int i = 0; i < ELEMENT_COLORS.length; i++) {
            float travel = Mth.clamp((time - YanPrologueScript.CORPSE_HOLD - i * 1.8F) / 39F, 0, 1);
            float alpha = smooth(travel * 10) * (1 - smooth((time - YanPrologueScript.CLOCK_START) / 7F));
            if (alpha <= 0) continue;
            double angle = i * Math.PI * 2 / ELEMENT_COLORS.length + .45;
            double head = 1 - travel;
            double tail = Math.min(1, head + .54 - travel * .24);
            Vec3 last = streamPoint(center, angle, i, head, time);
            for (int j = 1; j <= 16; j++) {
                float along = j / 16F;
                Vec3 next = streamPoint(center, angle, i, Mth.lerp(along, head, tail), time);
                Vec3 across = next.subtract(last).cross(next.scale(-1)).normalize();
                double width = .17 * (1 - along * .84) * (1 + .22 * Math.sin(time * .36 + j * .54));
                float opacity = alpha * (1 - along * .9F);
                ribbon(consumer, matrix, last, next, across.scale(width * 2.6), ELEMENT_COLORS[i], opacity * .21F);
                ribbon(consumer, matrix, last, next, across.scale(width), ELEMENT_COLORS[i], opacity * .83F);
                ribbon(consumer, matrix, last, next, across.scale(width * .28), 0xfff5dc, opacity * .92F);
                last = next;
            }
        }
        BUFFERS.endBatch(RenderType.lightning());
    }

    private static Vec3 streamPoint(Vec3 center, double angle, int ray, double progress, float time) {
        double turn = angle + (1 - progress) * .8 + Math.sin(progress * 7 - time * .12 + ray) * .23;
        double radius = progress * (8 + ray % 3 * 1.4);
        double y = progress * (5 + ray % 4 * 1.6) + Math.sin(progress * Math.PI) * 1.6;
        return center.add(Math.cos(turn) * radius, y, Math.sin(turn) * radius);
    }

    private static void ribbon(VertexConsumer out, Matrix4f matrix, Vec3 from, Vec3 to, Vec3 width, int rgb, float alpha) {
        vertex(out, matrix, from.subtract(width), rgb, alpha);
        vertex(out, matrix, from.add(width), rgb, alpha);
        vertex(out, matrix, to.add(width), rgb, alpha);
        vertex(out, matrix, to.subtract(width), rgb, alpha);
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 p, int rgb, float alpha) {
        out.vertex(matrix, (float) p.x, (float) p.y, (float) p.z)
                .color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, (int) (alpha * 255)).endVertex();
    }

    @SubscribeEvent
    public static void clock(RenderGuiEvent.Pre event) {
        if (body == null) return;
        event.setCanceled(true);
        var gui = event.getGuiGraphics();
        int width = gui.guiWidth();
        int height = gui.guiHeight();
        float time = age + event.getPartialTick();
        float timepiece = time - YanPrologueScript.CLOCK_START;
        if (timepiece >= 0) {
            float fade = smooth(timepiece / YanPrologueClockRules.FADE_IN);
            // 只把画面压暗（不再垫白盘 ✗ —— 作者第二版："不用这个白色圆盘"）
            gui.fill(0, 0, width, height, color(0x07050b, fade * .58F));
            var pose = gui.pose();
            pose.pushPose();
            pose.translate(width / 2F, height / 2F, 0);
            float scale = Math.min(width, height) / 190F * (.93F + .07F * fade);
            pose.scale(scale, scale, 1);
            // ★ 2026-11-15 第二版：**亮墨**漩涡阵（墨自己发光 ✓）＋ 毛笔指针 ✓
            //   （第一版垫了张宣纸、指针是 gui.fill 的矩形 ⇒ 作者："时针分针一点水墨感都没有"✗）
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            float r = YanPrologueClockRules.DIAL_RADIUS;
            // ① 外层水墨阵：慢慢转 ✓
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(YanPrologueClockRules.outerTurn(timepiece)));
            pose.scale(YanPrologueClockRules.OUTER_SCALE, YanPrologueClockRules.OUTER_SCALE, 1.0F);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, fade * YanPrologueClockRules.OUTER_ALPHA);
            blitSeal(gui, -r, -r, r * 2);
            pose.popPose();
            // ② 内层：反向、更小、更淡 ⇒ 墨在水里化开 ✓
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(YanPrologueClockRules.innerTurn(timepiece)));
            pose.scale(YanPrologueClockRules.INNER_SCALE, YanPrologueClockRules.INNER_SCALE, 1.0F);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, fade * YanPrologueClockRules.INNER_ALPHA);
            blitSeal(gui, -r, -r, r * 2);
            pose.popPose();
            // ③ 两支毛笔指针（倒着转 ⇒ "时间倒流"✓）
            drawBrush(gui, YanPrologueClockRules.HAND_HOUR, YanPrologueClockRules.HAND_HOUR_W,
                    YanPrologueClockRules.HAND_HOUR_H, YanPrologueClockRules.hourAngle(timepiece),
                    YanPrologueClockRules.HOUR_LENGTH, fade);
            drawBrush(gui, YanPrologueClockRules.HAND_MINUTE, YanPrologueClockRules.HAND_MINUTE_W,
                    YanPrologueClockRules.HAND_MINUTE_H, YanPrologueClockRules.minuteAngle(timepiece),
                    YanPrologueClockRules.MINUTE_LENGTH, fade);
            // ④ 轴心：一点朱红（像盖在画上的印 ✓）
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            gui.fill(-3, -3, 4, 4, color(0x8a2f2b, fade));
            gui.fill(-1, -1, 2, 2, color(0xf0dca8, fade * .9F));
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            pose.popPose();
        }
        float flash = time - (YanPrologueScript.DEFEAT_TICKS - 8);
        if (flash >= 0) {
            gui.fill(0, 0, width, height, color(0xffeed1, .85F * (1 - Math.abs(Mth.clamp(flash / 5, 0, 2) - 1))));
            gui.fill(0, 0, width, height, color(0, smooth((flash - 4) / 4)));
        }
    }

    /** 贴一张亮墨阵（固定 512×512 的贴图 ✓）。 */
    private static void blitSeal(GuiGraphics gui, float x, float y, float size) {
        gui.blit(YanPrologueClockRules.SEAL, Math.round(x), Math.round(y),
                Math.round(size), Math.round(size), 0, 0, 512, 512, 512, 512);
    }

    /**
     * 一支<b>毛笔指针</b> ✓ —— 图的下端是转轴、上端是笔锋，绕着转轴转 ✓。
     *
     * <p>为什么要贴图而不是 {@code gui.fill}：矩形直边、方头、粗细不变 ✗ ——
     * 作者 2026-11-15 第二版："时针分针一点水墨感都没有"✗。
     * 笔锋图自带"起笔粗 → 收笔尖、边缘不齐、几道飞白"✓，贴上去就有毛笔味 ✓。
     *
     * @param length 画多长（GUI 单位 ✓ 从轴心到笔尖）
     */
    private static void drawBrush(GuiGraphics gui, net.minecraft.resources.ResourceLocation texture,
                                  int textureWidth, int textureHeight, float angle, float length,
                                  float alpha) {
        float aspect = textureWidth / (float)textureHeight;
        int w = Math.max(3, Math.round(length * aspect));
        var pose = gui.pose();
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(angle));
        // ① 湿笔的**淡边**：同一支笔放宽一点、原色压淡 ⇒ 墨在纸上洇开的那圈 ✓
        //    （这一层同时也是"暗底上的轮廓"✓ —— 否则浅墨笔锋出了阵面就看不见了 ✗）
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha * 0.45F);
        gui.blit(texture, -(w * 5) / 4, Math.round(-length * 1.04F), (w * 5) / 2,
                Math.round(length * 1.04F), 0, 0, textureWidth, textureHeight,
                textureWidth, textureHeight);
        // ② 笔锋本体：**浓墨**（贴图是月白的，这里用乘色压暗 ✓）——
        //    不然白笔锋压在亮墨阵上就糊成一片了 ✗
        RenderSystem.setShaderColor(0.24F, 0.20F, 0.18F, alpha);
        gui.blit(texture, -w / 2, Math.round(-length), w, Math.round(length), 0, 0,
                textureWidth, textureHeight, textureWidth, textureHeight);
        pose.popPose();
    }

    private static int color(int rgb, float alpha) { return ((int) (255 * alpha) << 24) | rgb; }

    private static float smooth(float time) {
        float t = Mth.clamp(time, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
