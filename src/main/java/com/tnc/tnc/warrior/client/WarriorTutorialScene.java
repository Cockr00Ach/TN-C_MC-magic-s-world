package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.tnc.tnc.client.ui.ArcaneGui;
import com.tnc.tnc.skyacademy.client.AcademyClient;
import com.tnc.tnc.warrior.TestDummyEntity;
import com.tnc.tnc.warrior.WarriorTutorialNetwork;
import com.tnc.tnc.warrior.WarriorTutorialScript;
import java.util.Comparator;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Client presentation only: the server owns every lesson transition and completion. */
@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT)
public final class WarriorTutorialScene {
    public static final KeyMapping REPEAT = new KeyMapping("key.tnc.warrior_tutorial_repeat",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.tnc");
    private static ClientLevel world;
    private static int stage = -1, instructor = -1, missingTicks, cameraLine = -1;
    private static long token, started, lastRequest = Long.MIN_VALUE;
    private static BlockPos terminal = BlockPos.ZERO;
    private static String playerName = "";
    private static boolean skipped, invitationOpened, replayRequested;
    private static Vec3 sceneAnchor, sceneFace;
    private static ArmorStand camera;
    private static CameraType previousCamera;
    private static float health;
    private static int hurtTime;

    private WarriorTutorialScene() {}

    /** Includes free practice; cinematic() identifies only the interval that owns the camera. */
    public static boolean active() { return stage >= 0 && world != null; }
    public static boolean cinematic() { return camera != null; }

    public static void receive(CompoundTag data) {
        var mc = Minecraft.getInstance();
        int next = data.getInt("Stage");
        if (next < 0 || next >= 7) { close(); return; }
        if (mc.player == null || mc.level == null) return;
        long nextToken = data.getLong("Token");
        boolean changed = world != mc.level || token != nextToken || stage != next;
        if (changed) {
            releaseCamera();
            closeInvitation();
            skipped = false;
            replayRequested = false;
            sceneAnchor = sceneFace = null;
            invitationOpened = false;
            missingTicks = 0;
            lastRequest = Long.MIN_VALUE;
        }
        world = mc.level;
        stage = next;
        token = nextToken;
        instructor = data.getInt("Instructor");
        terminal = BlockPos.of(data.getLong("Terminal"));
        long nextStarted = data.getLong("Started");
        if (!changed && replayRequested && nextStarted != started) {
            skipped = false;
            replayRequested = false;
        }
        started = nextStarted;
        playerName = data.getString("PlayerName");
        health = mc.player.getHealth();
        hurtTime = mc.player.hurtTime;
        // Updating Started for "next" never resets an explicit local skip.
        if (!wantsCamera()) releaseCamera();
    }

    private static int elapsed() {
        return world == null ? 0 : (int) Math.min(Integer.MAX_VALUE,
                Math.max(0L, world.getGameTime() - started));
    }

    private static boolean wantsCamera() {
        return stage > 0 && !skipped && WarriorTutorialScript.cinematic(stage, elapsed());
    }

    private static boolean otherScene(Minecraft mc) {
        return AcademyClient.cinematic()
                || com.tnc.tnc.world.skyhaven.SkyhavenArrivalClient.active()
                || com.tnc.tnc.world.ink.client.InkEncounterCamera.active()
                || com.tnc.tnc.world.wind.client.CanglanCinematicCamera.active()
                || mc.getCameraEntity() != mc.player;
    }

    private static Entity mentor() { return world == null ? null : world.getEntity(instructor); }

    private static void request(String action) {
        var mc = Minecraft.getInstance();
        if (!active() || mc.getConnection() == null) return;
        long now = world.getGameTime();
        if (!action.equals("skip") && lastRequest != Long.MIN_VALUE && now - lastRequest < 5) return;
        lastRequest = now;
        if (action.equals("repeat")) replayRequested = true;
        WarriorTutorialNetwork.action(token, action);
    }

    private static void skip() {
        if (!cinematic()) return;
        request("skip");
        skipped = true;
        releaseCamera();
    }

    private static void cancel() {
        var mc = Minecraft.getInstance();
        if (active() && mc.getConnection() != null) WarriorTutorialNetwork.action(token, "cancel");
        close();
    }

    private static void closeInvitation() {
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof InvitationScreen) mc.setScreen(null);
    }

    private static void releaseCamera() {
        var mc = Minecraft.getInstance();
        ArmorStand owned = camera;
        camera = null;
        cameraLine = -1;
        if (owned != null && mc.getCameraEntity() == owned) {
            mc.setCameraEntity(mc.player);
            if (previousCamera != null) mc.options.setCameraType(previousCamera);
        }
        previousCamera = null;
        if (mc.screen instanceof FilmScreen) mc.setScreen(null);
    }

    public static void close() {
        releaseCamera();
        closeInvitation();
        stage = -1;
        world = null;
        token = 0;
        instructor = -1;
        skipped = invitationOpened = false;
        replayRequested = false;
        sceneAnchor = sceneFace = null;
        missingTicks = 0;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        boolean replay = false;
        while (REPEAT.consumeClick()) replay = true;
        if (!active()) return;
        if (mc.player == null || mc.level != world || !mc.player.isAlive() || mc.player.isSpectator()) {
            cancel(); return;
        }
        if (mc.player.getHealth() < health || cinematic() && mc.player.hurtTime > hurtTime) {
            cancel(); return;
        }
        health = mc.player.getHealth(); hurtTime = mc.player.hurtTime;
        var tutor = mentor();
        if (stage > 0 && tutor != null && (!tutor.isAlive() || mc.player.distanceToSqr(tutor) > 48 * 48)) {
            cancel(); return;
        }
        if (mc.isPaused()) return;
        if (stage == 0) {
            if (!invitationOpened && mc.screen == null && mc.getOverlay() == null && !otherScene(mc)) {
                invitationOpened = true;
                mc.setScreen(new InvitationScreen(token));
            }
            return;
        }
        if (camera != null && (mc.getCameraEntity() != camera || !(mc.screen instanceof FilmScreen)
                || !mc.isWindowActive() || mc.options.getCameraType() != CameraType.FIRST_PERSON)) {
            // A newly opened menu or another camera keeps ownership; never force it closed.
            request("skip"); skipped = true; releaseCamera();
        }
        if (!wantsCamera()) {
            releaseCamera();
            if (replay && stage >= 3 && stage <= 5 && mc.screen == null && !otherScene(mc)) request("repeat");
            return;
        }
        if (camera == null) {
            if (mc.screen != null || mc.getOverlay() != null || otherScene(mc)) return;
            if (tutor == null) {
                if (++missingTicks >= 100) {
                    mc.player.displayClientMessage(Component.literal("教官尚未抵达视野，已恢复操作。请稍后再与教官交谈。"), false);
                    cancel();
                }
                return;
            }
            previousCamera = mc.options.getCameraType();
            if (sceneAnchor == null) {
                sceneAnchor = tutor.position();
                sceneFace = Vec3.directionFromRotation(0, tutor.getYRot());
            }
            camera = new ArmorStand(world, mc.player.getX(), mc.player.getY(), mc.player.getZ());
            camera.setInvisible(true); camera.setNoGravity(true);
            if (!updateCamera(true)) { skip(); return; }
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.setCameraEntity(camera);
            mc.setScreen(new FilmScreen());
        } else if (!updateCamera(false)) skip();
    }

    private static boolean updateCamera(boolean initial) {
        var mc = Minecraft.getInstance();
        var tutor = mentor();
        if (camera == null || tutor == null || mc.player == null) return false;
        int age = elapsed();
        var lines = WarriorTutorialScript.lines(stage);
        if (lines.isEmpty()) return false;
        int line = Mth.clamp(WarriorTutorialScript.lineAt(stage, age), 0, lines.size() - 1);
        int lineStart = 0;
        for (int i = 0; i < line; i++) lineStart += lines.get(i).ticks();
        double t = Mth.clamp((age - lineStart) / (double) Math.max(1, lines.get(line).ticks()), 0, 1);
        double drift = Math.sin(t * Math.PI) * .22;
        Vec3 face = Vec3.directionFromRotation(0, tutor.getYRot());
        Vec3 side = new Vec3(face.z, 0, -face.x);
        Vec3 head = tutor.getEyePosition().add(face.scale(.12));
        Vec3 feet = tutor.position();
        Vec3 device = Vec3.atBottomCenterOf(terminal).add(0, 1.25, 0);
        Vec3 eye, focus;
        switch (lines.get(line).shot()) {
            case MENTOR -> {
                focus = head.add(0, -.04, 0);
                eye = focus.add(face.scale(1.65 - drift)).add(side.scale(.43 + drift)).add(0, .09, 0);
            }
            case HANDS -> {
                focus = feet.add(0, tutor.getBbHeight() * .58, 0).add(face.scale(.45));
                eye = focus.add(face.scale(1.8)).add(side.scale(1.4 + drift)).add(0, .3, 0);
            }
            case TERMINAL -> {
                focus = device;
                eye = focus.add(side.scale(3.3 - drift)).add(face.scale(4.2)).add(0, 2.1, 0);
            }
            case PLAYER -> {
                // Over the learner's shoulder: their face remains out of the foreground.
                focus = head.lerp(mc.player.getEyePosition(), .15);
                Vec3 toward = focus.subtract(mc.player.getEyePosition()).normalize();
                Vec3 shoulder = new Vec3(toward.z, 0, -toward.x).normalize();
                eye = mc.player.getEyePosition().subtract(toward.scale(1.5)).add(shoulder.scale(.9)).add(0, .35, 0);
            }
            case DUMMY -> {
                var dummy = world.getEntitiesOfClass(TestDummyEntity.class, tutor.getBoundingBox().inflate(20), Entity::isAlive)
                        .stream().min(Comparator.comparingDouble(tutor::distanceToSqr)).orElse(null);
                focus = dummy == null ? feet.add(face.scale(4)).add(0, 1.05, 0) : dummy.getBoundingBox().getCenter();
                eye = focus.add(side.scale(4.5)).add(face.scale(3.5 - drift)).add(0, 2, 0);
            }
            default -> {
                // Hold the stage, so the nine fast dashes never drag the whole camera along.
                Vec3 wideFace = sceneFace == null ? face : sceneFace;
                Vec3 wideSide = new Vec3(wideFace.z, 0, -wideFace.x);
                focus = (sceneAnchor == null ? feet : sceneAnchor).add(0, 1.05, 0).add(wideFace.scale(1.5));
                eye = focus.add(wideSide.scale(8 - drift)).add(wideFace.scale(8)).add(0, 4.5, 0);
            }
        }
        if (!world.hasChunkAt(BlockPos.containing(focus))) return false;
        var hit = world.clip(new ClipContext(focus, eye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() == HitResult.Type.BLOCK) eye = hit.getLocation().lerp(focus, .12);
        if (eye.distanceToSqr(focus) < .16 || !world.hasChunkAt(BlockPos.containing(eye))) return false;
        Vec3 direction = focus.subtract(eye);
        camera.xo = camera.getX(); camera.yo = camera.getY(); camera.zo = camera.getZ();
        camera.yRotO = camera.getYRot(); camera.xRotO = camera.getXRot();
        camera.setPos(eye.x, eye.y - camera.getEyeHeight(), eye.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        camera.setYRot(camera.yRotO + Mth.wrapDegrees(yaw - camera.yRotO));
        camera.setXRot((float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance())));
        // Cuts land on the authored shot immediately; motion within each shot stays interpolated.
        if (initial || cameraLine != line) {
            camera.xo = camera.getX(); camera.yo = camera.getY(); camera.zo = camera.getZ();
            camera.yRotO = camera.getYRot(); camera.xRotO = camera.getXRot();
        }
        cameraLine = line;
        return true;
    }

    @SubscribeEvent public static void input(MovementInputUpdateEvent event) {
        if (cinematic()) {
            event.getInput().forwardImpulse = 0; event.getInput().leftImpulse = 0;
            event.getInput().jumping = false; event.getInput().shiftKeyDown = false;
        }
    }
    @SubscribeEvent public static void interact(InputEvent.InteractionKeyMappingTriggered event) {
        if (cinematic()) { event.setCanceled(true); event.setSwingHand(false); }
    }
    @SubscribeEvent public static void hand(RenderHandEvent event) { if (cinematic()) event.setCanceled(true); }
    @SubscribeEvent public static void overlays(RenderGuiOverlayEvent.Pre event) { if (cinematic()) event.setCanceled(true); }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void angles(ViewportEvent.ComputeCameraAngles event) {
        if (camera == null || Minecraft.getInstance().getCameraEntity() != camera) return;
        float partial = (float) event.getPartialTick();
        event.setYaw(Mth.rotLerp(partial, camera.yRotO, camera.getYRot()));
        event.setPitch(Mth.lerp(partial, camera.xRotO, camera.getXRot())); event.setRoll(0);
    }
    @SubscribeEvent public static void fov(ViewportEvent.ComputeFov event) { if (cinematic()) event.setFOV(52); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { close(); }
    @SubscribeEvent public static void clone(ClientPlayerNetworkEvent.Clone event) { cancel(); }

    private static String keys(String text) {
        var mc = Minecraft.getInstance();
        return text.replace("{Q}", WarriorComboKeys.LEFT.getTranslatedKeyMessage().getString())
                .replace("{E}", WarriorComboKeys.RIGHT.getTranslatedKeyMessage().getString())
                .replace("{Z}", WarriorComboKeys.BOW_HORIZONTAL.getTranslatedKeyMessage().getString())
                .replace("{K}", WarriorPracticeKeys.OPEN.getTranslatedKeyMessage().getString())
                .replace("{USE}", mc.options.keyUse.getTranslatedKeyMessage().getString())
                .replace("{PLAYER}", playerName);
    }

    private static String objective() {
        String q = WarriorComboKeys.LEFT.getTranslatedKeyMessage().getString();
        String e = WarriorComboKeys.RIGHT.getTranslatedKeyMessage().getString();
        String z = WarriorComboKeys.BOW_HORIZONTAL.getTranslatedKeyMessage().getString();
        return switch (stage) {
            case 2 -> "走到演武终端旁，用 " + Minecraft.getInstance().options.keyUse.getTranslatedKeyMessage().getString() + " 打开它";
            case 3 -> "主攻  " + z + " → " + z + " → " + z + "   ·   连按三次，也可连续左键";
            case 4 -> "振刀  " + e + " → " + q + " → " + e + "（末段按住） · 面向教官架住攻击";
            case 5 -> "破振刀  " + e + " → " + q + " → " + z + "   ·   破开架势，接回进攻";
            default -> "教官正在演示，请观察动作与刀光";
        };
    }

    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (!active() || cinematic() || stage == 0 || mc.player == null || mc.screen != null || mc.options.hideGui) return;
        var g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        int panel = Math.min(390, w - 16), x = (w - panel) / 2;
        var text = mc.font.split(Component.literal(objective()), panel - 20);
        boolean practice = stage >= 3 && stage <= 5;
        int y = h - 102 - text.size() * 11;
        g.fill(x, y, x + panel, y + 28 + text.size() * 11, 0xdc101821);
        g.fill(x, y, x + 2, y + 28 + text.size() * 11, ArcaneGui.GOLD);
        for (int i = 0; i < text.size(); i++) g.drawString(mc.font, text.get(i), x + 10, y + 7 + i * 11, ArcaneGui.TEXT, false);
        String hint = practice ? REPEAT.getTranslatedKeyMessage().getString() + " 重看本阶段 · "
                + WarriorPracticeKeys.OPEN.getTranslatedKeyMessage().getString() + " 查看武器图鉴"
                : stage == 2 ? "台中央的石台上 · " + WarriorPracticeKeys.OPEN.getTranslatedKeyMessage().getString() + " 查看武器图鉴"
                : "示范仍在继续，实操由教官确认";
        g.drawString(mc.font, hint, x + 10, y + 14 + text.size() * 11, ArcaneGui.DIM, false);
    }

    private static final class FilmScreen extends Screen {
        FilmScreen() { super(Component.literal("战士现场教学")); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public boolean shouldCloseOnEsc() { return false; }
        @Override public void onClose() { skip(); }
        @Override public boolean keyPressed(int key, int scan, int modifiers) {
            if (key == GLFW.GLFW_KEY_ESCAPE || Minecraft.getInstance().options.keyShift.matches(key, scan)) { skip(); return true; }
            if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { request("next"); return true; }
            return true;
        }
        @Override public boolean mouseClicked(double x, double y, int button) {
            if (button == 0) request("next");
            return true;
        }
        @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
            int band = Math.max(22, Math.min(36, height / 12));
            g.fill(0, 0, width, band, 0xe6091018);
            var lines = WarriorTutorialScript.lines(stage);
            String text = lines.isEmpty() ? "" : keys(lines.get(Mth.clamp(WarriorTutorialScript.lineAt(stage, elapsed()), 0, lines.size() - 1)).text());
            var wrapped = font.split(Component.literal(text), Math.max(80, Math.min(560, width - 48)));
            int bottom = Math.max(74, wrapped.size() * 12 + 43);
            g.fill(0, height - bottom, width, height, 0xea091018);
            g.drawCenteredString(font, "演武初见 · " + (stage == 6 ? "记住你的第一套剑招" : "先看清，再出手"), width / 2, (band - 8) / 2, ArcaneGui.GOLD);
            Entity tutor = mentor();
            g.drawCenteredString(font, tutor == null ? "剑术教官" : tutor.getName().getString(), width / 2, height - bottom + 9, ArcaneGui.GOLD);
            for (int i = 0; i < wrapped.size(); i++) g.drawCenteredString(font, wrapped.get(i), width / 2, height - bottom + 24 + i * 12, ArcaneGui.TEXT);
            g.drawCenteredString(font, "空格 / 左键 下一句 · " + Minecraft.getInstance().options.keyShift.getTranslatedKeyMessage().getString()
                    + " / ESC 跳过镜头", width / 2, height - 15, ArcaneGui.DIM);
        }
    }

    private static final class InvitationScreen extends Screen {
        private final long offeredToken;
        private int left, top, panelWidth, panelHeight;
        private boolean replied;
        InvitationScreen(long offeredToken) { super(Component.literal("演武场的邀请")); this.offeredToken = offeredToken; }
        @Override public boolean isPauseScreen() { return false; }
        @Override protected void init() {
            panelWidth = Math.min(390, width - 20); panelHeight = Math.min(204, height - 16);
            left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
            int buttonWidth = (panelWidth - 52) / 2;
            addRenderableWidget(Button.builder(Component.literal("开始 · 请教官演示"), b -> reply("begin"))
                    .bounds(left + 20, top + panelHeight - 47, buttonWidth, 24).build());
            addRenderableWidget(Button.builder(Component.literal("稍后再来"), b -> reply("cancel"))
                    .bounds(left + 32 + buttonWidth, top + panelHeight - 47, buttonWidth, 24).build());
        }
        private void reply(String action) {
            if (replied) return; replied = true;
            if (token == offeredToken && active()) WarriorTutorialNetwork.action(offeredToken, action);
            Minecraft.getInstance().setScreen(null);
            if (action.equals("cancel")) close();
        }
        @Override public void onClose() { reply("cancel"); }
        @Override public void render(GuiGraphics g, int mx, int my, float partial) {
            renderBackground(g);
            ArcaneGui.proportionalFrame(g, left, top, panelWidth, panelHeight);
            g.drawString(font, "演武初见", left + 22, top + 20, ArcaneGui.GOLD, false);
            String name = playerName.isBlank() ? "旅人" : playerName;
            String text = name + "，先看长剑绝技，再亲手练主攻、振刀和破振刀。"
                    + "我会介绍演武终端、武器等级与大招条件。练习进度都会替你记住。";
            int y = top + 43;
            for (var line : font.split(Component.literal(text), panelWidth - 44)) {
                g.drawString(font, line, left + 22, y, ArcaneGui.TEXT, false); y += 13;
            }
            super.render(g, mx, my, partial);
        }
    }

    @Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) { event.register(REPEAT); }
    }
}
