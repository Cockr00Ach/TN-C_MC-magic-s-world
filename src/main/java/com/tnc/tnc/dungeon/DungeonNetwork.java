package com.tnc.tnc.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.*;

@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class DungeonNetwork {
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(DungeonContent.id("dungeons"),()->"1","1"::equals,"1"::equals);
    private record Session(UUID run,int floor,int room,String kind,BlockPos origin,long until,long token){}
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();private static long sequence;
    @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent e){e.enqueueWork(()->{
      CHANNEL.messageBuilder(State.class,0,NetworkDirection.PLAY_TO_CLIENT).encoder((m,b)->{b.writeNbt(m.data);b.writeBoolean(m.open);}).decoder(b->new State(b.readNbt(),b.readBoolean())).consumerMainThread((m,c)->{Client.accept(m);c.get().setPacketHandled(true);}).add();
      CHANNEL.messageBuilder(Action.class,1,NetworkDirection.PLAY_TO_SERVER).encoder((m,b)->{b.writeLong(m.token);b.writeUtf(m.action,24);b.writeVarInt(m.value);}).decoder(b->new Action(b.readLong(),b.readUtf(24),b.readVarInt())).consumerMainThread((m,c)->{var p=c.get().getSender();if(p!=null)act(p,m);c.get().setPacketHandled(true);}).add();
    });}
    public record State(CompoundTag data,boolean open){}
    private record Action(long token,String action,int value){}
    private static class Client {static void accept(State s){com.tnc.tnc.dungeon.client.DungeonScreen.accept(s);}}
    public static void send(long token,String action,int value){CHANNEL.sendToServer(new Action(token,action,value));}
    public static void open(ServerPlayer p,String kind){open(p,kind,p.blockPosition());}
    public static void open(ServerPlayer p,String kind,BlockPos point){var r=DungeonService.run(p);var room=DungeonService.room(p);long token=++sequence;SESSIONS.put(p.getUUID(),new Session(r==null?null:r.id,r==null?0:r.floor,room==null?-1:room.id(),kind,point,p.server.overworld().getGameTime()+1200,token));var data=state(p,r,room);data.putString("Kind",kind);data.putLong("Token",token);CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new State(data,true));}
    public static CompoundTag state(ServerPlayer p,DungeonData.Run r,DungeonGraph.Room room){var d=DungeonData.get(p.server);var profile=d.profile(p.getUUID());var t=new CompoundTag();t.putLong("Saved",profile.getLong("Saved"));t.putLong("Pending",profile.getLong("Pending"));t.putInt("Blessing",profile.getInt("Blessing"));if(r!=null){t.putString("Theme",r.theme.id);t.putInt("Floor",r.floor);t.putInt("Unlocked",DungeonService.unlocked(p,r.theme));t.putBoolean("Leader",r.leader.equals(p.getUUID()));t.putInt("Party",r.members.size());t.putBoolean("Multiplayer",r.multiplayer);t.putBoolean("SoloUsed",r.soloUsed.contains(p.getUUID()));t.putInt("Room",room==null?-1:room.id());if(r.floor>0){var f=r.state();t.putInt("Visited",f.visited);t.putInt("Cleared",f.cleared);var list=new ListTag();int reveal=f.events.getInt("Reveal");for(var a:r.graph().rooms)if(((f.visited|reveal)&(1<<a.id()))!=0){var v=new CompoundTag();v.putInt("Id",a.id());v.putInt("X",a.cell().x());v.putInt("Z",a.cell().z());v.putString("Type",((f.visited&(1<<a.id()))!=0)?a.kind().name():"UNKNOWN");v.putIntArray("Links",a.links().stream().filter(i->((f.visited|reveal)&(1<<i))!=0).mapToInt(Integer::intValue).toArray());list.add(v);}t.put("Map",list);if(room!=null){t.putString("RoomKind",room.kind().name());t.putInt("Event",event(r,room));}}}return t;}
    public static int event(DungeonData.Run r,DungeonGraph.Room room){return Math.floorMod((int)(r.seed+r.floor*7L+room.id()*13L),6);}
    public static void hud(ServerPlayer p,DungeonData.Run r,DungeonGraph.Room room){CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new State(state(p,r,room),false));}
    private static void act(ServerPlayer p,Action action){var session=SESSIONS.get(p.getUUID());var r=DungeonService.run(p);if(session==null||session.token!=action.token||session.until<p.server.overworld().getGameTime()||!p.isAlive()||DungeonService.recovering(p)||com.tnc.tnc.combat.DownedCombat.isDowned(p)||session.run!=null&&(r==null||!r.id.equals(session.run)||r.floor!=session.floor)||!session.kind.equals("guide")&&!DungeonService.near(p,session.origin,8))return;
      var room=DungeonService.room(p);if(session.room>=0&&(room==null||room.id()!=session.room))return;String a=action.action;
      if(a.equals("start")&&session.kind.equals("gate"))DungeonService.start(p,action.value);
      else if(a.equals("leave")&&!session.kind.equals("guide"))DungeonService.leave(p);
      else if(a.equals("abandon")&&session.kind.equals("gate"))DungeonService.abandon(p);
      else if(a.equals("claim")&&session.kind.equals("cache")&&room!=null)DungeonRewards.claim(p,r,room);
      else if(a.equals("bless")&&session.kind.equals("event")&&room!=null&&event(r,room)==1)DungeonRewards.bless(p,action.value);
      else if(a.equals("buy")&&session.kind.equals("event")&&room!=null&&event(r,room)==0)DungeonRewards.buy(p,action.value);
      else if(a.equals("event")&&session.kind.equals("event")&&room!=null)DungeonService.event(p,r,room,action.value);
      else if(a.equals("rest")&&session.kind.equals("rest")&&room!=null)DungeonService.rest(p,r,room);
    }
    public static void stop(){SESSIONS.clear();}
}
