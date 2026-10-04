package com.tnc.tnc.life.pasture;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.saveddata.SavedData;

/** A consumable soil treatment keeps existing farmland wet; it never creates water. */
public final class PastureMoistureData extends SavedData {
    private record Treatment(UUID owner,long until){}
    private final Map<BlockPos,Treatment> soils=new LinkedHashMap<>();
    public static PastureMoistureData get(ServerLevel l){return l.getDataStorage().computeIfAbsent(PastureMoistureData::load,PastureMoistureData::new,"tnc_soil_treatments");}
    public void apply(BlockPos p,UUID owner,long until){if(soils.size()>=4096&&!soils.containsKey(p))return;soils.put(p.immutable(),new Treatment(owner,until));setDirty();}
    public void tick(ServerLevel l){var iterator=soils.entrySet().iterator();while(iterator.hasNext()){var e=iterator.next();if(l.getGameTime()>=e.getValue().until){iterator.remove();setDirty();continue;}if(!l.hasChunkAt(e.getKey()))continue;var actor=net.minecraftforge.common.util.FakePlayerFactory.get(l,new com.mojang.authlib.GameProfile(e.getValue().owner,"soil_care"));if(com.tnc.tnc.home.TownProtection.denied(actor,e.getKey())||!l.mayInteract(actor,e.getKey())){iterator.remove();setDirty();continue;}var s=l.getBlockState(e.getKey());if(s.is(Blocks.FARMLAND)&&s.getValue(FarmBlock.MOISTURE)<7)l.setBlock(e.getKey(),s.setValue(FarmBlock.MOISTURE,7),2);}}
    public static PastureMoistureData load(CompoundTag t){var data=new PastureMoistureData();for(Tag raw:t.getList("Soils",10)){var s=(CompoundTag)raw;if(s.hasUUID("Owner")&&data.soils.size()<4096)data.soils.put(BlockPos.of(s.getLong("Position")),new Treatment(s.getUUID("Owner"),s.getLong("Until")));}return data;}
    @Override public CompoundTag save(CompoundTag t){var list=new ListTag();soils.forEach((p,s)->{var tag=new CompoundTag();tag.putLong("Position",p.asLong());tag.putUUID("Owner",s.owner);tag.putLong("Until",s.until);list.add(tag);});t.put("Soils",list);return t;}
}
