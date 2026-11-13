package com.tnc.tnc.world.yan;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The story uses only black and the current paragraph. */
@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT)
public final class YanPrologueScreen extends Screen {
    private static YanPrologueNetwork.Frame pending;
    private static long lastToken;
    private final YanPrologueNetwork.Frame frame;
    private int page, age;
    private int blank;
    private boolean sent;

    private YanPrologueScreen(YanPrologueNetwork.Frame frame) {
        super(Component.literal("序幕"));
        this.frame = frame;
        if (frame.stage() == YanPrologueNetwork.Stage.REWIND) blank = YanPrologueScript.BLACK_HOLD;
    }

    static void receive(YanPrologueNetwork.Frame frame) {
        var client = Minecraft.getInstance();
        if (frame.stage() == YanPrologueNetwork.Stage.DEFEAT) {
            if (frame.token() == lastToken) YanPrologueDefeatClient.begin(frame.defeat());
            return;
        }
        if (frame.stage() == YanPrologueNetwork.Stage.END) {
            if (frame.token() != lastToken) return;
            YanPrologueDefeatClient.end();
            pending = null;
            if (client.screen instanceof YanPrologueScreen) client.setScreen(null);
            com.tnc.tnc.journey.client.JourneyClient.restartWelcome();
            return;
        }
        if (frame.stage() == YanPrologueNetwork.Stage.REWIND) YanPrologueDefeatClient.end();
        lastToken = frame.token();
        pending = frame;
    }

    @SubscribeEvent
    public static void tickClient(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pending == null) return;
        var client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.getOverlay() != null) return;
        if (pending.stage() == YanPrologueNetwork.Stage.FIGHT) {
            if (!client.level.dimension().location().equals(YanPrologueScript.DIMENSION)
                    || !client.levelRenderer.isChunkCompiled(client.player.blockPosition())
                    || client.screen instanceof ReceivingLevelScreen) return;
            if (client.screen instanceof YanPrologueScreen) client.setScreen(null);
            YanPrologueNetwork.advance(pending.token());
            pending = null;
            return;
        }
        if (client.screen instanceof ReceivingLevelScreen) return;
        if (!(client.screen instanceof YanPrologueScreen active) || !active.frame.equals(pending)) {
            client.setScreen(new YanPrologueScreen(pending));
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        YanPrologueDefeatClient.end();
        pending = null;
        lastToken = 0;
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public void onClose() {}

    @Override
    public void tick() {
        if (sent) return;
        if (blank > 0) {
            blank--;
            return;
        }
        age++;
        var cards = cards();
        if (age >= YanPrologueScript.ticks(cards.get(page))) {
            if (page == cards.size() - 1) advance();
            else { page++; age = 0; }
        }
    }

    private java.util.List<String> cards() {
        return frame.stage() == YanPrologueNetwork.Stage.REWIND ? YanPrologueScript.REWIND_CARDS : YanPrologueScript.CARDS;
    }

    private void advance() {
        sent = true;
        YanPrologueNetwork.advance(frame.token());
    }

    @Override
    public void render(GuiGraphics graphics, int mx, int my, float partial) {
        graphics.fill(0, 0, width, height, 0xff000000);
        if (sent || blank > 0) return;
        float time = age + partial;
        var cards = cards();
        String card = cards.get(page);
        float fade = envelope(time, YanPrologueScript.ticks(card), YanPrologueScript.FADE);
        Component paragraph = Component.literal(card);
        if (cards == YanPrologueScript.CARDS && page == cards.size() - 1) {
            paragraph = paragraph.copy().withStyle(net.minecraft.ChatFormatting.BOLD);
        }
        drawCentered(graphics, paragraph, fade);
    }

    private void drawCentered(GuiGraphics graphics, Component text, float alpha) {
        if (alpha <= .022F) return;
        float scale = width >= 480 ? 1.4F : 1.05F;
        int lineWidth = Math.max(80, Math.min(254, (int) ((width - 48) / scale)));
        var lines = font.split(text, lineWidth);
        int lineHeight = font.lineHeight + 5;
        int color = (Math.round(alpha * 255) << 24) | 0xf3eee5;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(width / 2F, height / 2F - lines.size() * lineHeight * scale / 2, 0);
        pose.scale(scale, scale, 1);
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            graphics.drawString(font, line, -font.width(line) / 2, i * lineHeight, color, false);
        }
        pose.popPose();
    }

    private static float envelope(float age, int total, int fade) {
        float t = Mth.clamp(Math.min(age, total - age) / fade, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
