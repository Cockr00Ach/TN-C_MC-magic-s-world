package com.tnc.tnc.building.client;

import com.tnc.tnc.building.ElementBossBlocks;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in GPU/model audit; omitted from the scoped installed update. */
@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT)
public final class ElementBossBlockClientAudit {
    private static boolean started, saving;
    private static volatile boolean saved;
    private static int frames;
    private static final List<String> failures = new ArrayList<>();

    @Mod.EventBusSubscriber(modid = "tnc", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Setup {
        @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent event) {
            if (Boolean.getBoolean("tnc.elementBlocksAudit")) event.enqueueWork(() -> {
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.magic.TNDarkAimMechanics.class);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.magic.TNDarkHandCord.class);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.client.TNSpellClientVisuals.class);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.client.DarkGiantSiphonInput.class);
            });
        }
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) throws Exception {
        if (!Boolean.getBoolean("tnc.elementBlocksAudit") || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        // Static block/item models can be checked directly after resource loading.
        if (mc.getOverlay() != null || mc.getModelManager().getMissingModel() == null) return;
        if (!started) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.guiScale().set(1);
            mc.resizeDisplay();
            for (String theme : ElementBossBlocks.THEMES) for (int shade = 1; shade <= 8; shade++) {
                var item = ElementBossBlocks.block(theme, shade).asItem();
                var model = mc.getItemRenderer().getItemModelShaper().getItemModel(item);
                if (model == mc.getModelManager().getMissingModel() || model.getParticleIcon().contents().name().getPath().equals("missingno")) failures.add("Missing model/texture: " + ElementBossBlocks.name(theme, shade));
            }
            mc.setScreen(new Gallery());
        }
        if (++frames < 40) return;
        if (!saving) {
            saving = true;
            Screenshot.grab(mc.gameDirectory, "element_boss_blocks.png", mc.getMainRenderTarget(), message -> saved = true);
            return;
        }
        if (!saved) return;
        Files.writeString(Path.of(System.getProperty("tnc.elementBlocksAuditReport")), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("failures", failures, "models", 48, "scope", "Real dev client item/block model render")));
        mc.stop();
    }

    private static final class Gallery extends Screen {
        Gallery() { super(Component.literal("Element boss blocks")); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void render(GuiGraphics graphics, int mx, int my, float partial) {
            graphics.fill(0, 0, width, height, 0xFF12171E);
            graphics.drawString(font, "ELEMENT BOSS RUNESTONES / LIGHT TO DARK / 48 BLOCKS", 30, 22, 0xFFE5C58E, false);
            int left = 220, cell = (width - left - 20) / 8, rowHeight = (height - 90) / 6;
            String[] labels = {"EARTH / CORE ARMOR", "WATER / ABYSSAL SCALES", "DARK / VOID ECLIPSE", "YAN / VIOLET THUNDER", "YAN / GOLD THUNDER", "LIGHT / SACRED HALO"};
            for (int row = 0; row < 6; row++) {
                int y = 60 + row * rowHeight;
                graphics.drawString(font, labels[row], 25, y + rowHeight / 2, 0xD2DCE8, false);
                for (int shade = 1; shade <= 8; shade++) {
                    int x = left + (shade - 1) * cell;
                    graphics.fill(x + 4, y, x + cell - 4, y + rowHeight - 8, 0xFF242B34);
                    float scale = Math.min(cell - 30, rowHeight - 34) / 16F;
                    graphics.pose().pushPose();
                    graphics.pose().translate(x + (cell - 16 * scale) / 2, y + 2, 0);
                    graphics.pose().scale(scale, scale, 1);
                    graphics.renderItem(new ItemStack(ElementBossBlocks.block(ElementBossBlocks.THEMES.get(row), shade)), 0, 0);
                    graphics.pose().popPose();
                    graphics.drawCenteredString(font, "0" + shade, x + cell / 2, y + rowHeight - 24, 0xD8C9AA);
                }
            }
        }
    }
}
