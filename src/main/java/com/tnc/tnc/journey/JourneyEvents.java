package com.tnc.tnc.journey;
import net.minecraftforge.eventbus.api.SubscribeEvent;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc")
public final class JourneyEvents {
    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p)Preparation.clearReady(p);}
    @SubscribeEvent public static void dimension(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p)Preparation.clearReady(p);}
    @SubscribeEvent public static void commands(net.minecraftforge.event.RegisterCommandsEvent e){e.getDispatcher().register(net.minecraft.commands.Commands.literal("character").executes(c->{FamilyLife.openProfile(c.getSource().getPlayerOrException());return 1;}));e.getDispatcher().register(net.minecraft.commands.Commands.literal("family").executes(c->{FamilyLife.family(c.getSource().getPlayerOrException());return 1;}));e.getDispatcher().register(net.minecraft.commands.Commands.literal("prepare").executes(c->{var p=c.getSource().getPlayerOrException();Preparation.open(p,Preparation.selectedFloor(p));return 1;}));}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ServerTickEvent e){if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END||e.getServer().getTickCount()%20!=0)return;for(var p:e.getServer().getPlayerList().getPlayers()){if(!Preparation.nearLobby(p))Preparation.clearReady(p);var t=JourneyData.get(p.server).player(p.getUUID());if(t.getLong("ElixirManaUntil")>p.server.overworld().getGameTime()){var m=com.tnc.tnc.magic.MagicStone.getOrNull(p);if(m!=null){m.addMana(Math.max(0,Math.min(2,t.getInt("ElixirManaRate"))));com.tnc.tnc.network.MagicStoneNetwork.syncTo(p);}}}}
    @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppedEvent e){JourneyNetwork.clear();}
}
