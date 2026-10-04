package com.tnc.tnc.life.wonders;

import com.tnc.tnc.TNMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=TNMod.MODID)
public final class WonderEvents {
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void note(net.minecraftforge.event.level.NoteBlockEvent.Play e){if(e.isCanceled()||!(e.getLevel() instanceof net.minecraft.server.level.ServerLevel l))return;for(var p:net.minecraft.core.BlockPos.betweenClosed(e.getPos().offset(-6,-2,-6),e.getPos().offset(6,2,6)))if(l.hasChunkAt(p)&&l.getBlockEntity(p) instanceof SoundRelayEntity relay)relay.hear(l,e.getVanillaNoteId());}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e){if(e.phase!=TickEvent.Phase.END)return;var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(server!=null)for(var level:server.getAllLevels()){LightBloomData.get(level).tick(level);VineCleanupData.get(level).tick(level);}}
}
