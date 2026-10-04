package com.tnc.tnc.life.ecology;

import com.tnc.tnc.production.energy.OwnedManaPlant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;
import javax.annotation.Nullable;

/** Storage is debited before any receiver sees it; no offline FE or event synthesis. */
public final class VerdantVeinBlockEntity extends BlockEntity implements OwnedManaPlant {
    private UUID owner;
    private int mana,growth,prunes;
    private long lastDrawSecond=-1;
    private long lastActiveTick;
    private boolean knownClock,wasGrowing;
    public VerdantVeinBlockEntity(BlockPos p,BlockState s){super(EcologyContent.VERDANT_ENTITY,p,s);}
    @Nullable @Override public UUID manaOwner(){return owner;}
    public int storedMana(){return mana;}
    public void setOwner(UUID id){if(owner==null){owner=id;setChanged();}}
    static boolean wet(ServerLevel l,BlockPos p){for(Direction d:Direction.Plane.HORIZONTAL)if(l.getFluidState(p.below().relative(d)).is(FluidTags.WATER))return true;return l.isRainingAt(p);}
    static boolean valid(ServerLevel l,BlockPos p){return l.isDay()&&l.getBrightness(LightLayer.SKY,p)>=12&&wet(l,p);}
    public static void tick(ServerLevel l,BlockPos p,BlockState s,VerdantVeinBlockEntity be){
        if(l.getGameTime()%20!=0)return;
        int age=s.getValue(VerdantVeinBlock.AGE);
        boolean growing=valid(l,p);
        be.lastActiveTick=l.getGameTime();be.knownClock=true;be.wasGrowing=growing;
        be.setChanged();
        if(growing){
            if(age<3){be.growth=Math.min(24000,be.growth+20);int next=Math.min(3,be.growth/8000);if(next!=age)l.setBlock(p,s.setValue(VerdantVeinBlock.AGE,next),3);}
            else be.mana=Math.min(40,be.mana+1);
            be.setChanged();
        }
        var current=l.getBlockState(p);int glow=be.mana==0?0:be.mana<20?1:2;
        if(current.is(EcologyContent.VERDANT)&&current.getValue(VerdantVeinBlock.GLOW)!=glow)l.setBlock(p,current.setValue(VerdantVeinBlock.GLOW,glow),3);
    }
    public int drawMana(int max){
        if(max<1||!(level instanceof ServerLevel l)||getBlockState().getValue(VerdantVeinBlock.AGE)<3||mana==0)return 0;
        long second=l.getGameTime()/20;if(lastDrawSecond==second)return 0;
        // Stored charge remains available in darkness, but it cannot regenerate.
        mana--;lastDrawSecond=second;setChanged();return 1;
    }
    public boolean prune(){mana=0;growth=12000;prunes++;lastDrawSecond=-1;setChanged();if(level!=null)level.setBlock(worldPosition,getBlockState().setValue(VerdantVeinBlock.AGE,1).setValue(VerdantVeinBlock.GLOW,0),3);return prunes%3==0;}
    public String status(){
        if(getBlockState().getValue(VerdantVeinBlock.AGE)<3)return "绿脉枝成长 "+(growth*100/24000)+"%；需要白天、日照和根旁水。";
        String reason=level instanceof ServerLevel l?(valid(l,worldPosition)?"正在蓄魔":"缺光或缺水，停止补充"):"";
        return "绿脉储魔 "+mana+"/40 · "+reason+"；每秒最多送1。用剪刀取枝会暂停供能。";
    }
    @Override public void onLoad(){
        super.onLoad();
        if(!(level instanceof ServerLevel l)||!getBlockState().is(EcologyContent.VERDANT))return;
        long now=l.getGameTime();
        // Only growth can catch up, once and at half speed. Generation and
        // transfer never happen while unloaded. Both endpoint habitats must fit.
        if(knownClock&&wasGrowing&&valid(l,worldPosition)&&getBlockState().getValue(VerdantVeinBlock.AGE)<3){
            long elapsed=Math.max(0,now-lastActiveTick);
            growth+=(int)Math.min(24000-growth,elapsed/2);
            var state=l.getBlockState(worldPosition);
            if(state.is(EcologyContent.VERDANT))l.setBlock(worldPosition,state.setValue(VerdantVeinBlock.AGE,Math.min(3,growth/8000)),3);
        }
        lastActiveTick=now;knownClock=true;wasGrowing=valid(l,worldPosition);setChanged();
    }
    @Override protected void saveAdditional(CompoundTag t){super.saveAdditional(t);if(owner!=null)t.putUUID("Owner",owner);t.putInt("Mana",mana);t.putInt("Growth",growth);t.putInt("Prunes",prunes);t.putLong("LastDrawSecond",lastDrawSecond);if(knownClock)t.putLong("LastActiveTick",lastActiveTick);t.putBoolean("WasGrowing",wasGrowing);}
    @Override public void load(CompoundTag t){super.load(t);owner=t.hasUUID("Owner")?t.getUUID("Owner"):null;mana=Math.max(0,Math.min(40,t.getInt("Mana")));growth=Math.max(0,Math.min(24000,t.getInt("Growth")));prunes=Math.max(0,t.getInt("Prunes"));lastDrawSecond=t.contains("LastDrawSecond")?t.getLong("LastDrawSecond"):-1;knownClock=t.contains("LastActiveTick");lastActiveTick=t.getLong("LastActiveTick");wasGrowing=t.getBoolean("WasGrowing");}
    @Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
