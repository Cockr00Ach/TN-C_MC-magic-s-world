package com.tnc.tnc.journey;
import com.tnc.tnc.dungeon.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import java.util.*;
public final class Preparation {
    public static final class Storage extends SimpleContainer {
        private final ServerPlayer owner;private boolean loaded;
        public Storage(ServerPlayer p){super(31);owner=p;var items=NonNullList.withSize(31,ItemStack.EMPTY);ContainerHelper.loadAllItems(JourneyData.get(p.server).player(p.getUUID()).getCompound("Preparation"),items);for(int i=0;i<31;i++)setItem(i,items.get(i));loaded=true;}
        @Override public void setChanged(){super.setChanged();if(!loaded)return;var data=JourneyData.get(owner.server);var record=data.player(owner.getUUID());var list=NonNullList.withSize(31,ItemStack.EMPTY);for(int i=0;i<31;i++)list.set(i,getItem(i));var t=new CompoundTag();ContainerHelper.saveAllItems(t,list);record.put("Preparation",t);record.remove("ReadyRun");data.setDirty();}
    }
    public static int selectedFloor(ServerPlayer p){var r=DungeonService.run(p);var t=JourneyData.get(p.server).player(p.getUUID());return r!=null&&t.hasUUID("WantedRun")&&t.getUUID("WantedRun").equals(r.id)?Math.max(1,t.getInt("WantedFloor")):1;}
    public static boolean nearLobby(ServerPlayer p){var r=DungeonService.run(p);return r!=null&&r.floor==0&&!r.building&&DungeonRealm.inside(p.level())&&DungeonService.near(p,DungeonRealm.lobby(r),12)&&p.isAlive()&&!p.isSpectator()&&!com.tnc.tnc.combat.DownedCombat.isDowned(p);}
    public static void open(ServerPlayer p,int floor){if(!nearLobby(p))return;var r=DungeonService.run(p);if(r.leader.equals(p.getUUID())&&floor>=1&&floor<=26&&(floor-1)%5==0&&floor<=DungeonService.unlocked(p,r.theme)){
        for(var id:r.members){var t=JourneyData.get(p.server).player(id);if(!t.hasUUID("WantedRun")||!t.getUUID("WantedRun").equals(r.id)||t.getInt("WantedFloor")!=floor)t.remove("ReadyRun");t.putUUID("WantedRun",r.id);t.putInt("WantedFloor",floor);}JourneyData.get(p.server).setDirty();
    }int selected=selectedFloor(p);net.minecraftforge.network.NetworkHooks.openScreen(p,new SimpleMenuProvider((id,inv,q)->new PreparationMenu(id,inv,new Storage(p),selected),Component.literal("远征备战")),b->b.writeVarInt(selected));}
    private static String members(DungeonData.Run r){return r.members.stream().map(UUID::toString).sorted().collect(java.util.stream.Collectors.joining(","));}
    public static void clearReady(ServerPlayer p){var d=JourneyData.get(p.server);var t=d.player(p.getUUID());if(t.contains("ReadyRun")){t.remove("ReadyRun");d.setDirty();}}
    public static void ready(ServerPlayer p,int shownFloor){if(!nearLobby(p)||shownFloor!=selectedFloor(p))return;var d=JourneyData.get(p.server);var t=d.player(p.getUUID());var r=DungeonService.run(p);t.putUUID("ReadyRun",r.id);t.putInt("ReadyFloor",shownFloor);t.putString("ReadyMembers",members(r));t.putLong("ReadyAt",p.server.overworld().getGameTime());d.setDirty();p.closeContainer();DungeonNetwork.open(p,"gate");CommissionService.message(p,"备战已确认。队长可再次选择本段，点击启程。");}
    public static boolean ready(ServerPlayer p,DungeonData.Run r,int floor){var t=JourneyData.get(p.server).player(p.getUUID());return t.hasUUID("ReadyRun")&&t.getUUID("ReadyRun").equals(r.id)&&t.getInt("ReadyFloor")==floor&&t.getString("ReadyMembers").equals(members(r))&&p.server.overworld().getGameTime()-t.getLong("ReadyAt")<12000;}
    public static void choose(ServerPlayer p,int floor){if(!nearLobby(p))return;var r=DungeonService.run(p);if(!r.leader.equals(p.getUUID())){open(p,selectedFloor(p));return;}if(!ready(p,r,floor)){open(p,floor);return;}var party=DungeonService.online(p.server,r);var missing=party.stream().filter(q->!ready(q,r,floor)).map(q->q.getName().getString()).toList();if(party.size()!=r.members.size()||!missing.isEmpty()){CommissionService.message(p,"等待全队到齐并确认备战："+String.join("、",missing));return;}DungeonService.start(p,floor);if(r.floor>0)for(var id:r.members)JourneyData.get(p.server).player(id).remove("ReadyRun");JourneyData.get(p.server).setDirty();}
    public static void sip(ServerPlayer p){if(p.containerMenu!=p.inventoryMenu)return;var storage=new Storage(p);for(int i=27;i<31;i++){var stack=storage.getItem(i);if(stack.getItem() instanceof com.tnc.tnc.alchemy.ElixirItem&&com.tnc.tnc.alchemy.ElixirItem.drink(p,stack)){if(stack.isEmpty()&&!p.isCreative()&&!p.getInventory().add(new ItemStack(Items.GLASS_BOTTLE)))p.drop(new ItemStack(Items.GLASS_BOTTLE),false);storage.setChanged();return;}}CommissionService.message(p,"携行栏没有可用魔药，或仍在8秒饮药冷却中。");}
}
