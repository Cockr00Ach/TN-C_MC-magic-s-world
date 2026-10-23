package com.tnc.tnc.world.elementarena.client;
import com.tnc.tnc.world.elementarena.ElementArenas;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class ElementArenaVision {
 @SubscribeEvent public static void fog(ViewportEvent.RenderFog e){var mc=Minecraft.getInstance();if(mc.level!=null&&mc.level.dimension().equals(ElementArenas.WATER)&&e.getCamera().getFluidInCamera()==net.minecraft.world.level.material.FogType.WATER){e.setNearPlaneDistance(4);e.setFarPlaneDistance(112);e.setCanceled(true);}}
}
