package com.tnc.tnc.world.ink;

import com.tnc.tnc.TNMod;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Applies a full-scene monochrome post effect while the local player is in the ink realm. */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT)
public final class InkRealmClient {
    private static final ResourceLocation MONOCHROME =
            ResourceLocation.fromNamespaceAndPath("minecraft", "shaders/post/desaturate.json");
    private static boolean applied;

    private InkRealmClient() {}

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        boolean inInkRealm = minecraft.level != null
                && minecraft.level.dimension().equals(InkRealmTravel.INK_REALM);

        if (inInkRealm) {
            if (!applied || minecraft.gameRenderer.currentEffect() == null) {
                minecraft.gameRenderer.loadEffect(MONOCHROME);
                applied = true;
            }
        } else if (applied) {
            minecraft.gameRenderer.shutdownEffect();
            applied = false;
        }
    }
}
