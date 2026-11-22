package com.tnc.tnc.skyacademy.client;

import com.tnc.tnc.world.skyhaven.SkyhavenContent;
import com.tnc.tnc.world.skyhaven.SeaStormCoast.Layout;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT)
public final class SkyhavenLayoutClient {
    private static Layout layout = Layout.ORIGINAL;

    static void receive(boolean rebuilt, boolean highlands) {
        layout = !rebuilt ? Layout.ORIGINAL : highlands ? Layout.HIGHLANDS : Layout.HEADLANDS;
    }

    public static boolean rebuilt() {
        return layout() != Layout.ORIGINAL;
    }

    public static Layout layout() {
        var level = Minecraft.getInstance().level;
        return level != null && level.dimension().equals(SkyhavenContent.KEY) ? layout : Layout.ORIGINAL;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        layout = Layout.ORIGINAL;
    }

    private SkyhavenLayoutClient() {}
}
