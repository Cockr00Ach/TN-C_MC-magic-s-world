package com.tnc.tnc.dungeon;

import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Independent ledger; bank balances and authored quest data never enter this file. */
public final class DungeonData extends SavedData {
    public long nextSlot;
    public final Map<UUID,Run> runs=new LinkedHashMap<>();
    public final Map<UUID,CompoundTag> players=new LinkedHashMap<>();
    public static DungeonData get(MinecraftServer s){return s.overworld().getDataStorage().computeIfAbsent(DungeonData::load,DungeonData::new,"tnc_dungeons_v1");}
    public CompoundTag profile(UUID player){return players.computeIfAbsent(player,k->new CompoundTag());}
    public static final class Floor {
        public int visited,cleared,started,paid,waves;public boolean built;
        public final CompoundTag claims=new CompoundTag(),enemies=new CompoundTag(),participants=new CompoundTag(),events=new CompoundTag();
        public boolean clear(int room){return (cleared&(1<<room))!=0;}
        public CompoundTag save(){var t=new CompoundTag();t.putInt("Visited",visited);t.putInt("Cleared",cleared);t.putInt("Started",started);t.putInt("Paid",paid);t.putInt("Waves",waves);t.putBoolean("Built",built);t.put("Claims",claims.copy());t.put("Enemies",enemies.copy());t.put("Participants",participants.copy());t.put("Events",events.copy());return t;}
        public static Floor load(CompoundTag t){var f=new Floor();f.visited=t.getInt("Visited");f.cleared=t.getInt("Cleared");f.started=t.getInt("Started");f.paid=t.getInt("Paid");f.waves=t.getInt("Waves");f.built=t.getBoolean("Built");for(var pair:List.of(new CompoundTag[]{f.claims,t.getCompound("Claims")},new CompoundTag[]{f.enemies,t.getCompound("Enemies")},new CompoundTag[]{f.participants,t.getCompound("Participants")},new CompoundTag[]{f.events,t.getCompound("Events")}))pair[0].merge(pair[1]);return f;}
    }
    public static final class Run {
        public UUID id,leader;public long seed,slot;public DungeonTheme theme;public int floor,segment=1;public boolean closed,building,multiplayer;public final Set<UUID> soloUsed=new HashSet<>();
        public final Set<UUID> members=new LinkedHashSet<>();public final Map<Integer,Floor> floors=new LinkedHashMap<>();
        public Run(UUID id,UUID leader,long seed,long slot,DungeonTheme theme){this.id=id;this.leader=leader;this.seed=seed;this.slot=slot;this.theme=theme;members.add(leader);}
        public Floor state(){return floors.computeIfAbsent(floor,k->new Floor());}public DungeonGraph graph(){return DungeonGraph.generate(seed,floor);}
        public CompoundTag save(){var t=new CompoundTag();t.putUUID("Id",id);t.putUUID("Leader",leader);t.putLong("Seed",seed);t.putLong("Slot",slot);t.putString("Theme",theme.id);t.putInt("Floor",floor);t.putInt("Segment",segment);t.putBoolean("Closed",closed);t.putBoolean("Multiplayer",multiplayer);var used=new ListTag();soloUsed.forEach(u->used.add(StringTag.valueOf(u.toString())));t.put("SoloUsed",used);var m=new ListTag();members.forEach(u->m.add(StringTag.valueOf(u.toString())));t.put("Members",m);var fs=new CompoundTag();floors.forEach((i,f)->fs.put(i.toString(),f.save()));t.put("Floors",fs);return t;}
        public static Run load(CompoundTag t){var r=new Run(t.getUUID("Id"),t.getUUID("Leader"),t.getLong("Seed"),t.getLong("Slot"),DungeonTheme.find(t.getString("Theme")));r.floor=Math.max(0,Math.min(30,t.getInt("Floor")));r.segment=Math.max(1,Math.min(26,t.getInt("Segment")));r.closed=t.getBoolean("Closed");r.multiplayer=t.getBoolean("Multiplayer");for(Tag v:t.getList("SoloUsed",8))try{r.soloUsed.add(UUID.fromString(v.getAsString()));}catch(IllegalArgumentException ignored){}r.members.clear();for(Tag v:t.getList("Members",8))try{r.members.add(UUID.fromString(v.getAsString()));}catch(IllegalArgumentException ignored){}var fs=t.getCompound("Floors");for(String key:fs.getAllKeys())try{int f=Integer.parseInt(key);if(f>=0&&f<=30)r.floors.put(f,Floor.load(fs.getCompound(key)));}catch(NumberFormatException ignored){}return r;}
    }
    public static DungeonData load(CompoundTag root){var d=new DungeonData();d.nextSlot=Math.max(0,root.getLong("NextSlot"));for(Tag v:root.getList("Runs",10))try{var r=Run.load((CompoundTag)v);d.runs.put(r.id,r);}catch(RuntimeException ignored){}for(Tag v:root.getList("Players",10)){var t=(CompoundTag)v;if(t.hasUUID("Id"))d.players.put(t.getUUID("Id"),t.getCompound("Data").copy());}return d;}
    @Override public CompoundTag save(CompoundTag root){root.putLong("NextSlot",nextSlot);var rs=new ListTag();runs.values().forEach(r->rs.add(r.save()));root.put("Runs",rs);var ps=new ListTag();players.forEach((id,data)->{var t=new CompoundTag();t.putUUID("Id",id);t.put("Data",data.copy());ps.add(t);});root.put("Players",ps);return root;}
    public static long failureLoss(long pending){return Math.max(0,pending)/5;}
}
