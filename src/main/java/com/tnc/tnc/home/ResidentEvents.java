package com.tnc.tnc.home;

import com.tnc.tnc.TNMod;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import static net.minecraft.commands.Commands.literal;

@Mod.EventBusSubscriber(modid=TNMod.MODID)
public final class ResidentEvents {
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e){if(e.phase==TickEvent.Phase.END){var s=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(s!=null&&s.getTickCount()%200==0)ResidentService.tick(s.overworld());}}
    @SubscribeEvent public static void stop(ServerStoppedEvent e){ResidentService.clear();}
    @SubscribeEvent public static void commands(RegisterCommandsEvent e){
        var root=literal("neighbor");for(String action:java.util.List.of("gift","dine","live","farewell","home"))root.then(literal(action).executes(c->{var p=c.getSource().getPlayerOrException();p.sendSystemMessage(Component.literal(ResidentService.act(p,action)));return 1;}));e.getDispatcher().register(root);
    }
}
