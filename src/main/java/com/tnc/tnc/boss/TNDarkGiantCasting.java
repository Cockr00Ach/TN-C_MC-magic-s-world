package com.tnc.tnc.boss;

import com.tnc.tnc.dark.TNDarkSummonEntity;
import com.tnc.tnc.magic.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.SpellRegistry;
import net.spell_power.api.SpellPower;
import java.util.Comparator;

/** Spell Engine integration is isolated so the arsenal remains usable without the optional engine. */
final class TNDarkGiantCasting {
    public static boolean cast(LivingEntity caster,TNDarkGiantSpells.Move move){
        if(!(caster.level() instanceof ServerLevel level))return false;
        var target=caster instanceof Mob mob?mob.getTarget():null;
        if(target==null||!target.isAlive()||target.distanceToSqr(caster)>TNDarkGiantSpells.CAST_RANGE*TNDarkGiantSpells.CAST_RANGE)return false;
        return switch(move.chain()){
            case 0 -> summon(level,caster,target,move.tier());
            case 2 -> fog(level,caster,move.path());
            case 3 -> siphon(level,caster);
            default -> target!=null&&target.isAlive()&&hands(level,caster,target,move.tier());
        };
    }
    private static boolean hands(ServerLevel level,LivingEntity caster,LivingEntity target,int tier){
        if(TNOrbEntities.NIGHT_GRIP==null)return false;
        int count=NightHandReleaseRules.count(tier);
        for(int i=0;i<count;i++){
            var hand=TNOrbEntities.NIGHT_GRIP.get().create(level);if(hand==null)return false;
            if(tier>=5){
                double angle=i*Math.PI*2/count;
                Vec3 landing=i==0?target.position():target.position().add(Math.cos(angle)*4.5,0,Math.sin(angle)*4.5);
                hand.launchSkyfall(caster,i==0?target.getId():-1,tier,i,count,landing);
            }else hand.launch(caster,target.getId(),tier,i,count);
            level.addFreshEntity(hand);
        }
        return true;
    }
    private static boolean siphon(ServerLevel level,LivingEntity caster){
        if(TNOrbEntities.DRAIN==null)return false;
        double range=TNDarkGiantSpells.CAST_RANGE;
        level.getEntitiesOfClass(TNDarkDrainEntity.class,caster.getBoundingBox().inflate(range),d->d.casterId()==caster.getId())
                .forEach(net.minecraft.world.entity.Entity::discard);
        var victims=level.getEntitiesOfClass(LivingEntity.class,caster.getBoundingBox().inflate(range),
                e->e!=caster&&e.isAlive()&&!e.isSpectator()&&!e.isAlliedTo(caster)
                        &&!(e instanceof TNDarkSummonEntity)&&!(e instanceof net.minecraft.world.entity.player.Player p&&p.isCreative())
                        &&e.distanceToSqr(caster)<=range*range&&caster.hasLineOfSight(e));
        var primary=caster instanceof Mob mob?mob.getTarget():null;
        victims.sort(Comparator.comparingDouble(e->e==primary?-1:e.distanceToSqr(caster)));
        int spawned=0;
        for(var victim:victims.subList(0,Math.min(victims.size(),DarkSiphonRules.targets(3)))){
            var drain=TNOrbEntities.DRAIN.get().create(level);if(drain==null)continue;
            drain.configureSiphon(caster,victim,3,range);level.addFreshEntity(drain);spawned++;
        }
        return spawned>0;
    }
    private static boolean fog(ServerLevel level,LivingEntity caster,String path){
        var id=ResourceLocation.fromNamespaceAndPath("tnc",path);
        var spell=SpellRegistry.getSpell(id);
        if(spell==null||spell.release==null||spell.release.target==null||spell.release.target.cloud==null||TNOrbEntities.FOG==null)return false;
        // Refresh rather than accumulating damaging domains on every cast.
        level.getEntitiesOfClass(DarkFogCloudEntity.class,caster.getBoundingBox().inflate(32),f->f.isOwnedBy(caster))
                .forEach(net.minecraft.world.entity.Entity::discard);
        var cloud=TNOrbEntities.FOG.get().create(level);if(cloud==null)return false;
        var original=SpellPower.getSpellPower(spell.school,caster);
        var power=new SpellPower.Result(spell.school,Math.max(10,original.baseValue()),original.criticalChance(),original.criticalDamage());
        cloud.setOwner(caster);cloud.setPos(caster.position());
        cloud.onCreatedFromSpell(id,spell.release.target.cloud,new SpellHelper.ImpactContext().position(caster.position()).power(power));
        level.addFreshEntity(cloud);return true;
    }
    private static boolean summon(ServerLevel level,LivingEntity caster,LivingEntity target,int tier){
        var mine=level.getEntitiesOfClass(TNDarkSummonEntity.class,caster.getBoundingBox().inflate(128),e->caster.getUUID().equals(e.owner()));
        mine.sort(Comparator.comparingInt(net.minecraft.world.entity.Entity::getId));
        for(int i=0;i<Math.max(0,mine.size()+3-6);i++)mine.get(i).discard();
        double radius=caster.getBbWidth()+4;
        int spawned=0;
        for(int i=0;i<3;i++){
            var type=i==0?(tier==5?TNOrbEntities.EVIL_GOD.get():TNOrbEntities.DARK_KING.get())
                    :(tier==5?TNOrbEntities.DARK_KING.get():TNOrbEntities.DARK_LORD.get());
            var summon=type.create(level);if(summon==null)continue;
            double angle=Math.toRadians(caster.getYRot())+i*Math.PI*2/3;
            double x=caster.getX()+Math.cos(angle)*radius,z=caster.getZ()+Math.sin(angle)*radius;
            var pos=net.minecraft.core.BlockPos.containing(x,caster.getY()+3,z);
            for(int drop=0;drop<12&&level.isEmptyBlock(pos.below());drop++)pos=pos.below();
            summon.setOwner(caster.getUUID());summon.setScale(1);summon.setLifetime((tier==5?70:50)*20);
            summon.moveTo(x,pos.getY(),z,caster.getYRot(),0);summon.setTarget(target);
            level.addFreshEntity(summon);spawned++;
        }
        return spawned>0;
    }
}
