package com.tnc.tnc.dungeon.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.dungeon.DungeonMobRegistration;
import com.tnc.tnc.dungeon.DungeonSpecies;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

/**
 * 12 只小怪 + 弹体的客户端接线。
 *
 * <p>GeckoLib 的实体**不需要** {@code RegisterLayerDefinitions}（模型从 geo.json 读），
 * 所以这里只有 {@code RegisterRenderers} 一件事。少这一件事的后果是**实体隐形**
 * （不是崩溃，是更难受的"能打它、看不见它"）✗。
 *
 * <p>弹体直接借原版 {@link ThrownItemRenderer}：飞过来的是"一支箭 / 一罐火"，
 * 一眼能认出来是什么，也省掉一套模型与贴图 ✓。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DungeonMobClient {

    private DungeonMobClient() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (DungeonSpecies species : DungeonSpecies.values()) {
            event.registerEntityRenderer(DungeonMobRegistration.type(species).get(),
                    DungeonMobGeoRenderer::new);
        }
        event.registerEntityRenderer(DungeonMobRegistration.BOLT.get(), ThrownItemRenderer::new);
    }
}
