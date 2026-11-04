package com.tnc.tnc.warrior;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/** Vanilla arrow physics and combat, with a synchronized visual identity. */
public final class WarriorArrowEntity extends Arrow {
    private static final EntityDataAccessor<Integer> STYLE=SynchedEntityData.defineId(WarriorArrowEntity.class,EntityDataSerializers.INT);
    public WarriorArrowEntity(EntityType<? extends Arrow> type,Level level){super(type,level);}
    public WarriorArrowEntity(Level level,LivingEntity owner,int style){
        this(WarriorEntities.ORNATE_ARROW.get(),level);setOwner(owner);setPos(owner.getX(),owner.getEyeY()-.1,owner.getZ());setStyle(style);
    }
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(STYLE,0);}
    public int style(){return entityData.get(STYLE);}
    public void setStyle(int style){entityData.set(STYLE,Math.max(0,Math.min(2,style)));}
    public static int bowStyle(ItemStack bow){
        var id=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(bow.getItem());
        return id!=null&&id.getPath().equals("ember_bow")?2:id!=null&&id.getPath().equals("moon_bow")?1:0;
    }
    public static WarriorArrowEntity create(Level level,LivingEntity owner,ItemStack ammo,ItemStack bow){
        int style=ammo.getItem() instanceof WarriorArrowItem item?item.style():bowStyle(bow);
        return new WarriorArrowEntity(level,owner,style);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putInt("TncArrowStyle",style());}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);setStyle(tag.getInt("TncArrowStyle"));}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    @Override protected ItemStack getPickupItem(){return new ItemStack(switch(style()){
        case 1->com.tnc.tnc.TNMod.MOON_ARROW.get();case 2->com.tnc.tnc.TNMod.EMBER_ARROW.get();default->com.tnc.tnc.TNMod.STORM_ARROW.get();});}
}
