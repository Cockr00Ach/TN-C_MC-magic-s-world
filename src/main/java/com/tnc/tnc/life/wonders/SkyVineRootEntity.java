package com.tnc.tnc.life.wonders;

import java.util.*;
import com.tnc.tnc.home.TownProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Four real world days, a resumable growth queue, and never a block replacement. */
public final class SkyVineRootEntity extends BlockEntity {
    private UUID owner;
    private long growth,lastTick,nextHarvest;
    private int height,queueTarget;
    private int cursor;
    private final List<BlockPos> queue=new ArrayList<>();
    private final Set<BlockPos> ownStem=new HashSet<>(),ownLeaf=new HashSet<>();
    private String pause="";
    private boolean knownClock;
    private boolean validSample;
    private long nextSpaceCheck;
    public SkyVineRootEntity(BlockPos p,BlockState s){super(WonderContent.VINE_ENTITY,p,s);}
    public void setOwner(UUID id){owner=id;setChanged();}
    public UUID owner(){return owner;}
    public void retire(){if(level instanceof ServerLevel l)VineCleanupData.get(l).retire(ownStem,ownLeaf,owner);}
    static int targetHeight(long growth){return growth>=96000?64:growth>=72000?40:growth>=48000?24:growth>=24000?12:0;}
    /** A non-solid braided trunk, stepped branches and alternating leaf crowns. */
    static List<BlockPos> plan(BlockPos root,int from,int target){
        var points=new LinkedHashSet<BlockPos>();
        for(int y=from+1;y<=target;y++){
            double angle=y*Math.PI/8;
            int x=(int)Math.round(Math.cos(angle)),z=(int)Math.round(Math.sin(angle));
            points.add(root.offset(0,y,0));points.add(root.offset(x,y,z));
            if(y%8==0){
                int branch=y/8%4;
                for(int r=1;r<=3;r++)points.add(root.offset(branch==0?r:branch==2?-r:0,y,branch==1?r:branch==3?-r:0));
                for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++)if(Math.abs(dx)+Math.abs(dz)<=5){if(y+1<=target)points.add(root.offset(dx,y+1,dz));if(y+2<=target&&Math.abs(dx)+Math.abs(dz)<=2)points.add(root.offset(dx,y+2,dz));}
            }
        }
        return new ArrayList<>(points);
    }
    static boolean stem(BlockPos relative){return relative.getX()==0&&relative.getZ()==0||Math.abs(relative.getX())<=1&&Math.abs(relative.getZ())<=1||relative.getY()%8==0;}
    private boolean permitted(ServerLevel l,BlockPos p,ServerPlayer player){
        if(!l.isInWorldBounds(p)||!l.hasChunkAt(p)||TownProtection.hazard(l,p))return false;
        if(player!=null&&(TownProtection.denied(player,p)||!l.mayInteract(player,p)||net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.level.BlockEvent.BreakEvent(l,p,l.getBlockState(p),player))))return false;
        return true;
    }
    private boolean space(ServerLevel l,List<BlockPos> proposed,ServerPlayer player){
        for(var p:proposed){
            if(!permitted(l,p,player)){pause="等待区块加载或避开受保护地界";return false;}
            if(!l.getBlockState(p).isAir()&&!ours(l,p)){pause="生长空间被方块占据，可移走障碍后继续";return false;}
        }
        pause="";return true;
    }
    private boolean ours(ServerLevel l,BlockPos p){return ownStem.contains(p)&&l.getBlockState(p).is(WonderContent.VINE_STEM)||ownLeaf.contains(p)&&l.getBlockState(p).is(WonderContent.VINE_LEAF);}
    private boolean envelopeClear(ServerLevel l){
        if(worldPosition.getY()+64>=l.getMaxBuildHeight()){pause="上方不足64格生长空间";return false;}
        for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++)for(int y=1;y<=64;y++){
            BlockPos at=worldPosition.offset(dx,y,dz);
            if(!l.hasChunkAt(at)||TownProtection.hazard(l,at)){pause="9×9生长范围跨入未加载或受保护地界";return false;}
            if(!l.getBlockState(at).isAir()&&!ours(l,at)){pause="请清出上方9×9、64格高的空间；不会替换已有建筑";return false;}
        }
        pause="";return true;
    }
    public static void tick(ServerLevel l,BlockPos p,BlockState state,SkyVineRootEntity be){
        long now=l.getGameTime();
        if(!be.knownClock){be.lastTick=now;be.knownClock=true;}
        if(be.owner==null)return;
        long dt=Math.min(20,Math.max(0,now-be.lastTick));be.lastTick=now;
        if(now>=be.nextSpaceCheck){be.validSample=be.envelopeClear(l);be.nextSpaceCheck=now+100;}
        if(!be.validSample){be.setChanged();return;}
        be.growth=Math.min(96000,be.growth+dt);
        int target=targetHeight(be.growth);
        ServerPlayer player=l.getServer().getPlayerList().getPlayer(be.owner);
        // Automation carries the planter's real UUID through protection hooks.
        // Neither an absent planter nor this queue loads additional chunks.
        if(player==null||player.serverLevel()!=l)player=net.minecraftforge.common.util.FakePlayerFactory.get(l,new com.mojang.authlib.GameProfile(be.owner,"sky_vine"));
        if(be.queue.isEmpty()&&target>be.height&&now%20==0){
            List<BlockPos> proposed=plan(p,be.height,target);
            if(!be.space(l,proposed,player)){be.setChanged();return;}
            be.queue.addAll(proposed);be.cursor=0;be.queueTarget=target;
        }
        if(!be.queue.isEmpty()){
            int placed=0;
            while(be.cursor<be.queue.size()&&placed<32){
                BlockPos at=be.queue.get(be.cursor);
                if(!be.permitted(l,at,player)||!l.getBlockState(at).isAir()&&!be.ours(l,at)){be.pause="施工段有障碍，移走后继续";break;}
                boolean isStem=stem(at.subtract(p));
                if(!be.ours(l,at)){
                    l.setBlock(at,(isStem?WonderContent.VINE_STEM:WonderContent.VINE_LEAF).defaultBlockState(),3);
                    (isStem?be.ownStem:be.ownLeaf).add(at.immutable());
                }
                be.cursor++;placed++;
            }
            if(be.cursor>=be.queue.size()){
                be.height=be.queueTarget;be.queue.clear();be.cursor=0;be.pause="";
                l.setBlock(p,l.getBlockState(p).setValue(SkyVineRootBlock.AGE,be.height>=64?4:be.height>=40?3:be.height>=24?2:1),3);
                l.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,p.getX()+.5,p.getY()+be.height,p.getZ()+.5,15,2,1,2,.03);
            }
        }
        if(now%20==0)be.setChanged();
    }
    public boolean harvest(ServerPlayer player){
        if(owner==null||!owner.equals(player.getUUID())&&!player.isCreative()||height<64||level==null||level.getGameTime()<nextHarvest)return false;
        nextHarvest=level.getGameTime()+24000;setChanged();
        Block.popResource(level,worldPosition,new ItemStack(WonderContent.SKY_FIBER,4));Block.popResource(level,worldPosition,new ItemStack(WonderContent.SKY_VINE_SEED));return true;
    }
    public String status(){return "望天蔓："+(growth*100/96000)+"% · 已长"+height+"格。"+(pause.isEmpty()?"四游戏日长到64格；成熟后用剪刀取天幕纤维。":pause);}
    @Override public void onLoad(){super.onLoad();if(level instanceof ServerLevel l){long now=l.getGameTime();boolean clear=envelopeClear(l);if(knownClock&&validSample&&clear)growth=Math.min(96000,growth+Math.max(0,now-lastTick)/2);validSample=clear;nextSpaceCheck=now+100;lastTick=now;knownClock=true;setChanged();}}
    private boolean inEnvelope(BlockPos p){BlockPos r=p.subtract(worldPosition);return Math.abs(r.getX())<=4&&Math.abs(r.getZ())<=4&&r.getY()>0&&r.getY()<=64;}
    @Override protected void saveAdditional(CompoundTag t){super.saveAdditional(t);if(owner!=null)t.putUUID("Owner",owner);t.putLong("Growth",growth);t.putLong("LastTick",lastTick);t.putBoolean("KnownClock",knownClock);t.putBoolean("ValidSample",validSample);t.putLong("NextHarvest",nextHarvest);t.putInt("Height",height);t.putInt("QueueTarget",queueTarget);t.putInt("Cursor",cursor);t.putLongArray("Queue",queue.stream().mapToLong(BlockPos::asLong).toArray());t.putLongArray("Stem",ownStem.stream().mapToLong(BlockPos::asLong).toArray());t.putLongArray("Leaf",ownLeaf.stream().mapToLong(BlockPos::asLong).toArray());}
    @Override public void load(CompoundTag t){super.load(t);owner=t.hasUUID("Owner")?t.getUUID("Owner"):null;growth=Math.max(0,Math.min(96000,t.getLong("Growth")));lastTick=t.getLong("LastTick");knownClock=t.getBoolean("KnownClock");validSample=t.getBoolean("ValidSample");nextHarvest=t.getLong("NextHarvest");height=Math.max(0,Math.min(64,t.getInt("Height")));queueTarget=Math.max(height,Math.min(64,t.getInt("QueueTarget")));queue.clear();ownStem.clear();ownLeaf.clear();for(long v:t.getLongArray("Queue"))if(queue.size()<2500&&inEnvelope(BlockPos.of(v)))queue.add(BlockPos.of(v));cursor=Math.max(0,Math.min(queue.size(),t.getInt("Cursor")));for(long v:t.getLongArray("Stem"))if(ownStem.size()<2500&&inEnvelope(BlockPos.of(v)))ownStem.add(BlockPos.of(v));for(long v:t.getLongArray("Leaf"))if(ownLeaf.size()<2500&&inEnvelope(BlockPos.of(v)))ownLeaf.add(BlockPos.of(v));}
}
