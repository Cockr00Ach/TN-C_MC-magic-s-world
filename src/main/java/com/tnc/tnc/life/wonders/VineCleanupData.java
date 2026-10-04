package com.tnc.tnc.life.wonders;

import java.util.*;
import com.tnc.tnc.home.TownProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;

/** Retired roots clean only recorded vine blocks,32 edits per tick, across saves. */
public final class VineCleanupData extends SavedData {
    private record Node(BlockPos pos,boolean stem,UUID owner){}
    private final Deque<Node> pending=new ArrayDeque<>();
    public static VineCleanupData get(ServerLevel l){return l.getDataStorage().computeIfAbsent(VineCleanupData::load,VineCleanupData::new,"tnc_retired_vines");}
    public void retire(Collection<BlockPos> stems,Collection<BlockPos> leaves,UUID owner){if(owner==null)return;for(var p:stems)pending.addLast(new Node(p,true,owner));for(var p:leaves)pending.addLast(new Node(p,false,owner));setDirty();}
    public void tick(ServerLevel l){int edits=0;for(int scan=Math.min(128,pending.size());scan>0&&edits<32;scan--){Node n=pending.removeFirst();if(!l.hasChunkAt(n.pos)){pending.addLast(n);continue;}var actor=net.minecraftforge.common.util.FakePlayerFactory.get(l,new com.mojang.authlib.GameProfile(n.owner,"retired_vine"));if(TownProtection.denied(actor,n.pos)||!l.mayInteract(actor,n.pos)){pending.addLast(n);continue;}if(l.getBlockState(n.pos).is(n.stem?WonderContent.VINE_STEM:WonderContent.VINE_LEAF)){l.setBlock(n.pos,Blocks.AIR.defaultBlockState(),3);edits++;}setDirty();}}
    @Override public CompoundTag save(CompoundTag t){var list=new ListTag();for(Node n:pending){var tag=new CompoundTag();tag.putLong("Position",n.pos.asLong());tag.putBoolean("Stem",n.stem);tag.putUUID("Owner",n.owner);list.add(tag);}t.put("Nodes",list);return t;}
    public static VineCleanupData load(CompoundTag t){var d=new VineCleanupData();for(Tag raw:t.getList("Nodes",Tag.TAG_COMPOUND)){var n=(CompoundTag)raw;if(n.hasUUID("Owner"))d.pending.addLast(new Node(BlockPos.of(n.getLong("Position")),n.getBoolean("Stem"),n.getUUID("Owner")));}return d;}
}
