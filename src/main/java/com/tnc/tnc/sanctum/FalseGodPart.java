package com.tnc.tnc.sanctum;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

/** A pickable living target on both sides. Only the shared boss body processes damage. */
@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class FalseGodPart extends Mob implements Enemy {
    private static final EntityDataAccessor<Integer> SIZE=SynchedEntityData.defineId(FalseGodPart.class,EntityDataSerializers.INT);
    public static EntityType<FalseGodPart> TYPE;
    private FalseGodFight owner;
    private int size;

    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.ENTITY_TYPE,registry->{
            TYPE=EntityType.Builder.<FalseGodPart>of(FalseGodPart::new,MobCategory.MISC)
                    .sized(1,1).clientTrackingRange(12).updateInterval(1).noSave().noSummon().build("tnc:false_god_part");
            registry.register(ResourceLocation.fromNamespaceAndPath("tnc","false_god_part"),TYPE);
        });
    }
    @SubscribeEvent public static void attributes(EntityAttributeCreationEvent e){e.put(TYPE,Mob.createMobAttributes().build());}

    public FalseGodPart(EntityType<? extends Mob> type,Level level) {
        super(type,level);
        noPhysics=true;setNoAi(true);setSilent(true);setNoGravity(true);setPersistenceRequired();
    }
    void bind(FalseGodFight fight,String role){
        owner=fight;addTag("tnc_false_god_part");addTag("tnc_false_god_part_"+role);
    }
    static int units(double width){return Math.max(1,Math.min(1023,(int)Math.ceil(width*16)));}
    void fit(AABB box) {
        int packed=units(box.getXsize()) | units(box.getYsize())<<10 | units(box.getZsize())<<20;
        if(size!=packed)entityData.set(SIZE,packed);
        setPos((box.minX+box.maxX)*.5,box.minY,(box.minZ+box.maxZ)*.5);
    }
    void hide(){if(size!=0)entityData.set(SIZE,0);}
    boolean active(){return size!=0;}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(SIZE,0);}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){
        super.onSyncedDataUpdated(key);
        if(SIZE.equals(key)){
            size=entityData.get(SIZE);
            refreshDimensions();
            setBoundingBox(makeBoundingBox());
        }
    }
    private double xSize(){return (size&1023)/16.;}
    private double ySize(){return (size>>>10&1023)/16.;}
    private double zSize(){return (size>>>20&1023)/16.;}
    @Override public EntityDimensions getDimensions(Pose pose){
        return EntityDimensions.fixed((float)Math.max(xSize(),zSize()),(float)ySize());
    }
    @Override protected AABB makeBoundingBox(){
        double x=getX(),y=getY(),z=getZ(),w=xSize()/2,d=zSize()/2;
        return new AABB(x-w,y,z-d,x+w,y+ySize(),z+d);
    }
    @Override public void lerpTo(double x,double y,double z,float yaw,float pitch,int steps,boolean teleport){
        setPos(x,y,z);setYRot(yaw);setXRot(pitch);
    }
    @Override public boolean isAttackable(){return active();}
    @Override public boolean isAlive(){return active() && super.isAlive();}
    @Override public boolean isPickable(){return active();}
    @Override public boolean canBeHitByProjectile(){return active();}
    @Override public boolean isPushable(){return false;}
    @Override public boolean shouldBeSaved(){return false;}
    @Override public void tick(){if(!level().isClientSide && owner==null)discard();}
    @Override public boolean hurt(DamageSource source,float amount){
        return active() && owner!=null && owner.body.isAlive() && owner.body.hurt(source,amount);
    }
}
