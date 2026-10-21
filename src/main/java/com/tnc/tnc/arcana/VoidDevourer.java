package com.tnc.tnc.arcana;
import com.tnc.tnc.life.routes.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.*;
import java.util.UUID;
/** Non-destructive disaster predator. Real machine mana is its food and healing budget. */
public final class VoidDevourer extends Monster {
    public UUID owner;private net.minecraft.core.BlockPos food;private final net.minecraft.server.level.ServerBossEvent bar=new net.minecraft.server.level.ServerBossEvent(net.minecraft.network.chat.Component.literal("魔力吞噬者 · 虚空巨兽"),net.minecraft.world.BossEvent.BossBarColor.PURPLE,net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS);
    public VoidDevourer(EntityType<? extends Monster> t,Level l){super(t,l);setNoGravity(true);setPersistenceRequired();xpReward=100;}
    public static AttributeSupplier.Builder attributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,800).add(Attributes.ARMOR,12).add(Attributes.MOVEMENT_SPEED,.3).add(Attributes.ATTACK_DAMAGE,18).add(Attributes.FOLLOW_RANGE,48).add(Attributes.KNOCKBACK_RESISTANCE,.8);}
    @Override public void tick(){super.tick();if(!(level() instanceof ServerLevel l))return;bar.setProgress(getHealth()/getMaxHealth());
        if(tickCount%20==0){double best=-1;food=null;int scanned=0;for(var pos:RouteNetwork.positions(l)){if(++scanned>1024)break;if(!l.hasChunkAt(pos)||pos.distToCenterSqr(position())>2304||!(l.getBlockEntity(pos) instanceof RouteNodeEntity n)||n.mana==0||owner!=null&&!owner.equals(n.owner))continue;double score=n.mana/(1+pos.distToCenterSqr(position()));if(score>best){best=score;food=pos;}}
            if(food!=null&&food.distToCenterSqr(position())<16&&l.getBlockEntity(food) instanceof RouteNodeEntity n){int eaten=n.extract(Math.min(640,n.mana));heal(eaten/32f);com.tnc.tnc.mana.sight.ManaSightServer.record(l,Vec3.atCenterOf(food),getEyePosition(),eaten,0xf22b7a,4);}
            if(food==null){var p=l.getNearestPlayer(this,32);if(p!=null&&!p.isCreative()&&!p.isSpectator())setTarget(p);}
            for(var p:l.players())if(distanceToSqr(p)<25&&!p.isCreative()&&!p.isSpectator()){p.hurt(damageSources().mobAttack(this),12);p.push((p.getX()-getX())*.15,.35,(p.getZ()-getZ())*.15);p.hurtMarked=true;}
        }
        Vec3 target=food!=null?Vec3.atCenterOf(food).add(0,2.5,0):getTarget()!=null?getTarget().position().add(0,1,0):position().add(0,Math.sin(tickCount*.04)*.1,0);var delta=target.subtract(position());if(horizontalCollision)setDeltaMovement(getDeltaMovement().add(0,.1,0));if(delta.length()>1)setDeltaMovement(getDeltaMovement().scale(.8).add(delta.normalize().scale(.045)));setYRot((float)(Math.atan2(-delta.x,delta.z)*180/Math.PI));
    }
    @Override public void remove(RemovalReason reason){if(reason==RemovalReason.KILLED&&level() instanceof ServerLevel l)ArcanaDisasters.get(l).mark(getUUID());super.remove(reason);}
    @Override public void startSeenByPlayer(ServerPlayer p){super.startSeenByPlayer(p);bar.addPlayer(p);}
    @Override public void stopSeenByPlayer(ServerPlayer p){super.stopSeenByPlayer(p);bar.removePlayer(p);}
    @Override protected void dropCustomDeathLoot(net.minecraft.world.damagesource.DamageSource source,int looting,boolean recent){super.dropCustomDeathLoot(source,looting,recent);if(level() instanceof ServerLevel l)for(var pos:RouteNetwork.positions(l))if(l.hasChunkAt(pos)&&l.getBlockEntity(pos) instanceof RouteNodeEntity n&&n.kind()==RouteKind.ABYSS_TOWER&&n.getPersistentData().hasUUID("Devourer")&&getUUID().equals(n.getPersistentData().getUUID("Devourer"))){n.getPersistentData().remove("Devourer");n.getPersistentData().putDouble("overload_level",40);n.setChanged();}spawnAtLocation(ArcanaContent.item("collapsed_mana_singularity"));}
    @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);if(owner!=null)t.putUUID("TowerOwner",owner);}
    @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);owner=t.hasUUID("TowerOwner")?t.getUUID("TowerOwner"):null;}
}
