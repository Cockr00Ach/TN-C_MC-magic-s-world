package com.tnc.tnc.dungeon.mirror;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.RegisterEvent;

/** A vanilla fishing hook requires a Player owner. This projectile keeps vanilla-style flight,
 * swept collision and a physical line while allowing a dungeon mob to hold the rod. */
@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class MirrorHook extends ThrowableProjectile {
    static EntityType<MirrorHook> TYPE;
    public static EntityType<MirrorHook> type(){return TYPE;}
    private static final EntityDataAccessor<Integer> CASTER=SynchedEntityData.defineId(MirrorHook.class, EntityDataSerializers.INT);
    @SubscribeEvent public static void register(RegisterEvent event){
        event.register(Registries.ENTITY_TYPE,helper->{
            var id=ResourceLocation.fromNamespaceAndPath("tnc","echo_rod_hook");
            TYPE=EntityType.Builder.<MirrorHook>of(MirrorHook::new,MobCategory.MISC).sized(.18f,.18f)
                    .clientTrackingRange(8).updateInterval(1).noSave().build(id.toString());
            helper.register(id,TYPE);
        });
    }
    public MirrorHook(EntityType<? extends MirrorHook> type,Level level){super(type,level);}
    @Override protected void defineSynchedData(){entityData.define(CASTER,-1);}
    public EchoDouble caster(){return level().getEntity(entityData.get(CASTER)) instanceof EchoDouble d?d:null;}
    public static Vec3 tip(EchoDouble owner,Vec3 location,float yaw){
        double angle=Math.toRadians(yaw),side=owner.getMainArm()==HumanoidArm.RIGHT?1:-1;
        return location.add(-Math.sin(angle)*.55-Math.cos(angle)*side*.31,owner.getBbHeight()*.71,
                Math.cos(angle)*.55-Math.sin(angle)*side*.31);
    }
    static void cast(EchoDouble owner,Vec3 aim){
        var hook=new MirrorHook(TYPE,owner.level());
        hook.setOwner(owner);hook.entityData.set(CASTER,owner.getId());
        var origin=tip(owner,owner.position(),owner.yBodyRot);hook.setPos(origin);
        var delta=aim.subtract(origin);hook.shoot(delta.x,delta.y+delta.horizontalDistance()*.075,delta.z,1.25f,0);
        owner.level().addFreshEntity(hook);
        owner.playSound(SoundEvents.FISHING_BOBBER_THROW,.7f,1f);
    }
    @Override protected boolean canHitEntity(Entity e){
        return super.canHitEntity(e)&&getOwner() instanceof EchoDouble m&&e instanceof ServerPlayer p
                &&MirrorFight.silent(p)&&m.runId().equals(MirrorFight.activeRun(p))&&!p.isSpectator()
                &&!com.tnc.tnc.combat.DownedCombat.isDowned(p)&&!com.tnc.tnc.dungeon.DungeonService.recovering(p);
    }
    @Override protected void onHit(HitResult hit){
        if(!(level() instanceof ServerLevel level))return;
        if(hit instanceof EntityHitResult e&&e.getEntity() instanceof ServerPlayer p&&getOwner() instanceof EchoDouble m)
            MirrorMelee.hooked(m,p);
        level.sendParticles(ParticleTypes.SPLASH,hit.getLocation().x,hit.getLocation().y,hit.getLocation().z,5,.08,.1,.08,.03);
        discard();
    }
    @Override public void tick(){
        if(!level().isClientSide&&(!(getOwner() instanceof EchoDouble m)||!m.isAlive()||!m.melee()||tickCount>=16||distanceToSqr(m)>15*15)){discard();return;}
        super.tick();
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
}
